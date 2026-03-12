package com.bonitasoft.connectors.gdrive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bonitasoft.engine.bpm.bar.BarResource;
import org.bonitasoft.engine.bpm.bar.BusinessArchive;
import org.bonitasoft.engine.bpm.bar.BusinessArchiveBuilder;
import org.bonitasoft.engine.bpm.bar.BusinessArchiveFactory;
import org.bonitasoft.engine.bpm.bar.actorMapping.Actor;
import org.bonitasoft.engine.bpm.bar.actorMapping.ActorMapping;
import org.bonitasoft.engine.bpm.connector.ConnectorEvent;
import org.bonitasoft.engine.bpm.process.DesignProcessDefinition;
import org.bonitasoft.engine.bpm.process.impl.ProcessDefinitionBuilder;
import org.bonitasoft.engine.expression.ExpressionBuilder;
import org.bonitasoft.engine.expression.InvalidExpressionException;
import org.bonitasoft.engine.operation.OperationBuilder;
import org.bonitasoft.web.client.BonitaClient;
import org.bonitasoft.web.client.api.ProcessInstanceVariableApi;
import org.bonitasoft.web.client.model.ProcessInstantiationResponse;
import org.bonitasoft.web.client.services.policies.ProcessImportPolicy;

/**
 * Helper for testing connectors in a Docker Bonita instance.
 * Adapted from bonita-connector-rest's ConnectorTestToolkit.
 */
public class ConnectorTestToolkit {

    private static final String ARTIFACT_ID = "bonita-connector-gdrive";

    /**
     * Build a connector and install it into a dummy process with input and output process variables.
     */
    public static BusinessArchive buildConnectorToTest(String connectorId, String versionId,
            Map<String, String> inputs, Map<String, Output> outputs) throws Exception {
        var process = buildConnectorInProcess(connectorId, versionId, inputs, outputs);
        return buildBusinessArchive(process, connectorId, ARTIFACT_ID);
    }

    private static BusinessArchive buildBusinessArchive(DesignProcessDefinition process, String connectorId,
            String artifactId) throws Exception {
        var barBuilder = new BusinessArchiveBuilder();
        barBuilder.createNewBusinessArchive();
        barBuilder.setProcessDefinition(process);

        var foundFiles = new File("").getAbsoluteFile().toPath()
                .resolve("target")
                .toFile()
                .listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return Pattern.matches(artifactId + "-.*.jar", name)
                                && !name.endsWith("-sources.jar")
                                && !name.endsWith("-javadoc.jar");
                    }
                });

        assertThat(foundFiles).hasSize(1);
        var connectorJar = foundFiles[0];
        assertThat(connectorJar).exists();

        List<JarEntry> jarEntries = findJarEntries(connectorJar,
                entry -> entry.getName().equals(connectorId + ".impl"));
        assertThat(jarEntries).hasSize(1);
        var implEntry = jarEntries.get(0);

        byte[] content;
        try (JarFile jarFile = new JarFile(connectorJar)) {
            InputStream inputStream = jarFile.getInputStream(implEntry);
            content = inputStream.readAllBytes();
        }

        barBuilder.addConnectorImplementation(
                new BarResource(connectorId + ".impl", content));
        barBuilder.addClasspathResource(
                new BarResource(connectorJar.getName(), Files.readAllBytes(connectorJar.toPath())));

        ActorMapping actorMapping = new ActorMapping();
        var systemActor = new Actor("system");
        systemActor.addRole("member");
        actorMapping.addActor(systemActor);
        barBuilder.setActorMapping(actorMapping);

        return barBuilder.done();
    }

    private static DesignProcessDefinition buildConnectorInProcess(String connectorId, String versionId,
            Map<String, String> inputs, Map<String, Output> outputs) throws Exception {
        var processBuilder = new ProcessDefinitionBuilder();
        var expBuilder = new ExpressionBuilder();
        processBuilder.createNewInstance("PROCESS_UNDER_TEST", "1.0");
        processBuilder.addActor("system");
        var connectorBuilder = processBuilder.addConnector("connector-under-test", connectorId, versionId,
                ConnectorEvent.ON_ENTER);

        inputs.forEach((name, value) -> {
            try {
                connectorBuilder.addInput(name, expBuilder.createConstantStringExpression(value));
            } catch (InvalidExpressionException e) {
                throw new RuntimeException(e);
            }
        });

        if (outputs != null) {
            outputs.forEach((name, output) -> {
                try {
                    processBuilder.addData(name, output.getType(), null);
                    connectorBuilder.addOutput(new OperationBuilder().createSetDataOperation(name,
                            new ExpressionBuilder().createDataExpression(output.getName(), output.getType())));
                } catch (InvalidExpressionException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        // Add a user task to avoid the process completing immediately
        processBuilder.addUserTask("waiting task", "system");

        return processBuilder.done();
    }

    /**
     * Import the BusinessArchive and launch the process containing the connector.
     */
    public static ProcessInstantiationResponse importAndLaunchProcess(BusinessArchive barArchive, BonitaClient client)
            throws IOException {
        var process = barArchive.getProcessDefinition();
        File processFile = null;
        try {
            processFile = Files.createTempFile("process", ".bar").toFile();
            processFile.delete();
            BusinessArchiveFactory.writeBusinessArchiveToFile(barArchive, processFile);
            client.login("install", "install");
            client.processes().importProcess(processFile, ProcessImportPolicy.REPLACE_DUPLICATES);
        } finally {
            if (processFile != null) {
                processFile.delete();
            }
        }

        var processId = client.processes().getProcess(process.getName(), process.getVersion()).getId();
        return client.processes().startProcess(processId, Map.of());
    }

    /**
     * Get the value of a process variable.
     */
    public static String getProcessVariableValue(BonitaClient client, String caseId, String variableProcessName) {
        return client.get(ProcessInstanceVariableApi.class)
                .getVariableByProcessInstanceId(caseId, variableProcessName)
                .getValue();
    }

    private static List<JarEntry> findJarEntries(File file, Predicate<? super JarEntry> entryPredicate)
            throws IOException {
        try (JarFile jarFile = new JarFile(file)) {
            return jarFile.stream()
                    .filter(entryPredicate)
                    .collect(Collectors.toList());
        }
    }

    static class Output {
        private final String name;
        private final String type;

        public static Output create(String name, String type) {
            return new Output(name, type);
        }

        private Output(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }
}

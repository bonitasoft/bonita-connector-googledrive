package com.bonitasoft.connectors.gdrive.btt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Programmatically builds a Bonita .bar (Business Archive) as a ZIP file
 * containing a process with all 8 GDrive connector operations wired as automatic tasks.
 *
 * <p>Generates the .bar directly as a ZIP (no dependency on Bonita's ProcessDefinitionBuilder),
 * using the exact XML formats expected by Bonita runtime.</p>
 *
 * <p>The process flow is linear (10 tasks exercising 8 connector types):
 * Start -> CreateFolder -> Upload -> Search -> Download -> Copy
 * -> CreateArchiveFolder -> Move -> UploadConvert -> Export -> Delete -> End</p>
 */
public final class GDriveProcessBarBuilder {

    public static final String PROCESS_NAME = "GDriveConnectorTestProcess";
    public static final String PROCESS_VERSION = "1.0";
    private static final String CONNECTOR_VERSION = "1.0.0";
    private static final String DEFAULT_BONITA_JAR_NAME = "bonita-connector-gdrive-all-1.0.0-SNAPSHOT-bonita.jar";

    private static final AtomicLong ID_COUNTER = new AtomicLong(1000000);

    private GDriveProcessBarBuilder() {
        // utility class
    }

    /**
     * Builds the .bar and writes it to the specified file using the default JAR name.
     *
     * @param outputFile the target .bar file
     */
    public static void writeToFile(File outputFile) throws IOException {
        writeToFile(outputFile, DEFAULT_BONITA_JAR_NAME);
    }

    /**
     * Builds the .bar and writes it to the specified file using a custom connector JAR name.
     *
     * @param outputFile       the target .bar file
     * @param connectorJarName the name of the fat JAR in target/
     */
    public static void writeToFile(File outputFile, String connectorJarName) throws IOException {
        outputFile.getParentFile().mkdirs();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
            // 1. process-design.xml
            zos.putNextEntry(new ZipEntry("process-design.xml"));
            zos.write(buildProcessDesignXml().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. actorMapping.xml
            zos.putNextEntry(new ZipEntry("actorMapping.xml"));
            zos.write(buildActorMappingXml().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 3. form-mapping.xml
            zos.putNextEntry(new ZipEntry("form-mapping.xml"));
            zos.write(buildFormMappingXml().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 4. Extract .impl files from the bonita JAR and add under connector/
            File bonitaJar = findBonitaJar(connectorJarName);
            extractImplFiles(bonitaJar, zos);

            // 5. Add the shaded bonita JAR itself under classpath/
            zos.putNextEntry(new ZipEntry("classpath/" + bonitaJar.getName()));
            zos.write(java.nio.file.Files.readAllBytes(bonitaJar.toPath()));
            zos.closeEntry();
        }
    }

    // =========================================================================
    // XML Generators
    // =========================================================================

    private static String buildProcessDesignXml() {
        ID_COUNTER.set(1000000);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<tns:processDefinition id=\"_").append(nextId())
                .append("\" name=\"").append(PROCESS_NAME)
                .append("\" version=\"").append(PROCESS_VERSION)
                .append("\" xmlns:tns=\"http://www.bonitasoft.org/ns/process/client/7.4\">\n");

        // Actor
        String actorId = "_" + nextId();
        xml.append("    <actors>\n");
        xml.append("        <actor id=\"").append(actorId).append("\" name=\"System\" initiator=\"true\">\n");
        xml.append("            <description></description>\n");
        xml.append("        </actor>\n");
        xml.append("    </actors>\n");
        xml.append("    <actorInitiator>").append(actorId).append("</actorInitiator>\n");

        // Flow elements container
        String flowId = "_" + nextId();
        xml.append("    <flowElements id=\"").append(flowId).append("\">\n");

        // Build tasks with connectors
        List<ConnectorDef> connectors = buildConnectorDefs();
        String[] taskNames = {
                "CreateFolder", "Upload", "Search", "Download", "Copy",
                "CreateArchiveFolder", "Move", "UploadConvert", "Export", "Delete"
        };

        int taskCount = taskNames.length; // 10

        // Generate task IDs and transition IDs
        String startId = "_" + nextId();
        String endId = "_" + nextId();
        String[] taskIds = new String[taskCount];
        for (int i = 0; i < taskCount; i++) {
            taskIds[i] = "_" + nextId();
        }

        // Transition IDs: Start->task0, task0->task1, ..., taskN->End = taskCount+1 transitions
        String[] transitionIds = new String[taskCount + 1];
        for (int i = 0; i < taskCount + 1; i++) {
            transitionIds[i] = "_" + nextId();
        }

        // Automatic tasks with connectors
        for (int i = 0; i < taskCount; i++) {
            xml.append("        <automaticTask id=\"").append(taskIds[i])
                    .append("\" name=\"").append(taskNames[i]).append("\">\n");
            xml.append("            <incomingTransition>").append(transitionIds[i]).append("</incomingTransition>\n");
            xml.append("            <outgoingTransition>").append(transitionIds[i + 1]).append("</outgoingTransition>\n");

            // Connector
            ConnectorDef conn = connectors.get(i);
            String connElemId = "_" + nextId();
            xml.append("            <connector id=\"").append(connElemId)
                    .append("\" name=\"").append(conn.definitionId).append("-connector")
                    .append("\" connectorId=\"").append(conn.definitionId)
                    .append("\" activationEvent=\"ON_ENTER\" version=\"").append(CONNECTOR_VERSION)
                    .append("\" failAction=\"FAIL\">\n");

            // Inputs
            xml.append("                <inputs>\n");
            for (InputMapping input : conn.inputs) {
                String exprId = "_" + nextId();
                xml.append("                    <input name=\"").append(input.connectorInputName).append("\">\n");
                if (input.expressionType.equals("TYPE_VARIABLE")) {
                    xml.append("                        <expression id=\"").append(exprId)
                            .append("\" name=\"").append(input.expressionContent)
                            .append("\" expressionType=\"TYPE_VARIABLE\" returnType=\"").append(input.returnType)
                            .append("\" interpreter=\"\">\n");
                    xml.append("                            <content>").append(input.expressionContent).append("</content>\n");
                    xml.append("                        </expression>\n");
                } else {
                    // TYPE_CONSTANT
                    xml.append("                        <expression id=\"").append(exprId)
                            .append("\" name=\"").append(escapeXml(input.expressionContent))
                            .append("\" expressionType=\"TYPE_CONSTANT\" returnType=\"").append(input.returnType)
                            .append("\" interpreter=\"\">\n");
                    xml.append("                            <content>").append(escapeXml(input.expressionContent)).append("</content>\n");
                    xml.append("                        </expression>\n");
                }
                xml.append("                    </input>\n");
            }
            xml.append("                </inputs>\n");

            // Outputs
            xml.append("                <outputs>\n");
            for (OutputMapping output : conn.outputs) {
                String rightId = "_" + nextId();
                xml.append("                    <operation operatorType=\"ASSIGNMENT\">\n");
                xml.append("                        <leftOperand name=\"").append(output.processVariableName)
                        .append("\" type=\"DATA\"/>\n");
                xml.append("                        <rightOperand id=\"").append(rightId)
                        .append("\" name=\"").append(output.connectorOutputName)
                        .append("\" expressionType=\"TYPE_INPUT\" returnType=\"").append(output.returnType)
                        .append("\" interpreter=\"\">\n");
                xml.append("                            <content>").append(output.connectorOutputName).append("</content>\n");
                xml.append("                        </rightOperand>\n");
                xml.append("                    </operation>\n");
            }
            xml.append("                </outputs>\n");

            xml.append("            </connector>\n");
            xml.append("            <dataDefinitions/>\n");
            xml.append("            <businessDataDefinitions/>\n");
            xml.append("            <operations/>\n");
            xml.append("            <boundaryEvents/>\n");
            xml.append("        </automaticTask>\n");
        }

        // Transitions
        xml.append("        <transitions>\n");
        // Start -> task0
        xml.append("            <transition id=\"").append(transitionIds[0])
                .append("\" name=\"Start-&gt;").append(taskNames[0])
                .append("\" source=\"").append(startId)
                .append("\" target=\"").append(taskIds[0]).append("\"/>\n");
        // task[i] -> task[i+1]
        for (int i = 0; i < taskCount - 1; i++) {
            xml.append("            <transition id=\"").append(transitionIds[i + 1])
                    .append("\" name=\"").append(taskNames[i]).append("-&gt;").append(taskNames[i + 1])
                    .append("\" source=\"").append(taskIds[i])
                    .append("\" target=\"").append(taskIds[i + 1]).append("\"/>\n");
        }
        // lastTask -> End
        xml.append("            <transition id=\"").append(transitionIds[taskCount])
                .append("\" name=\"").append(taskNames[taskCount - 1]).append("-&gt;End")
                .append("\" source=\"").append(taskIds[taskCount - 1])
                .append("\" target=\"").append(endId).append("\"/>\n");
        xml.append("        </transitions>\n");

        // Start event
        xml.append("        <startEvent id=\"").append(startId).append("\" name=\"Start\" interrupting=\"true\">\n");
        xml.append("            <outgoingTransition>").append(transitionIds[0]).append("</outgoingTransition>\n");
        xml.append("        </startEvent>\n");

        // End event
        xml.append("        <endEvent id=\"").append(endId).append("\" name=\"End\">\n");
        xml.append("            <incomingTransition>").append(transitionIds[taskCount]).append("</incomingTransition>\n");
        xml.append("        </endEvent>\n");

        // Process-level data definitions
        xml.append("        <dataDefinitions>\n");

        // String variables (with optional default values for serviceAccountJson and parentFolderId)
        for (String varName : getStringVariables()) {
            String dataId = "_" + nextId();
            String defaultValue = getDefaultValue(varName);
            if (defaultValue != null && !defaultValue.isEmpty()) {
                xml.append("            <textDataDefinition longText=\"true\" id=\"").append(dataId)
                        .append("\" name=\"").append(varName)
                        .append("\" transient=\"false\" className=\"java.lang.String\">\n");
                String defaultExprId = "_" + nextId();
                xml.append("                <defaultValue id=\"").append(defaultExprId)
                        .append("\" name=\"default_").append(varName)
                        .append("\" expressionType=\"TYPE_CONSTANT\" returnType=\"java.lang.String\" interpreter=\"\">\n");
                xml.append("                    <content>").append(escapeXml(defaultValue)).append("</content>\n");
                xml.append("                </defaultValue>\n");
                xml.append("            </textDataDefinition>\n");
            } else {
                xml.append("            <textDataDefinition longText=\"false\" id=\"").append(dataId)
                        .append("\" name=\"").append(varName)
                        .append("\" transient=\"false\" className=\"java.lang.String\"/>\n");
            }
        }

        // Integer variable: gdriveTotalResults
        String totalResultsId = "_" + nextId();
        xml.append("            <dataDefinition id=\"").append(totalResultsId)
                .append("\" name=\"gdriveTotalResults\" transient=\"false\" className=\"java.lang.Integer\"/>\n");

        // Boolean variable: gdriveSuccess
        String successId = "_" + nextId();
        xml.append("            <dataDefinition id=\"").append(successId)
                .append("\" name=\"gdriveSuccess\" transient=\"false\" className=\"java.lang.Boolean\"/>\n");

        xml.append("        </dataDefinitions>\n");

        xml.append("        <businessDataDefinitions/>\n");
        xml.append("        <documentDefinitions/>\n");
        xml.append("        <documentListDefinitions/>\n");
        xml.append("        <connectors/>\n");
        xml.append("        <elementFinder/>\n");
        xml.append("    </flowElements>\n");

        // String indexes
        xml.append("    <stringIndexes>\n");
        for (int i = 1; i <= 5; i++) {
            xml.append("        <stringIndex index=\"").append(i).append("\"/>\n");
        }
        xml.append("    </stringIndexes>\n");
        xml.append("    <context/>\n");
        xml.append("</tns:processDefinition>\n");

        return xml.toString();
    }

    /**
     * Returns the default value for a process variable, read from system properties or environment.
     */
    private static String getDefaultValue(String varName) {
        if ("serviceAccountJson".equals(varName)) {
            return System.getProperty("GDRIVE_SA_JSON_CONTENT",
                    System.getenv().getOrDefault("GDRIVE_SA_JSON_CONTENT", ""));
        }
        if ("parentFolderId".equals(varName)) {
            return System.getProperty("GDRIVE_SHARED_FOLDER_ID",
                    System.getenv().getOrDefault("GDRIVE_SHARED_FOLDER_ID", ""));
        }
        return null;
    }

    private static String buildActorMappingXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <actorMappings:actorMappings xmlns:actorMappings="http://www.bonitasoft.org/ns/actormapping/6.0">
                    <actorMapping name="System">
                        <users/>
                        <groups/>
                        <roles>
                            <role>member</role>
                        </roles>
                        <memberships/>
                    </actorMapping>
                </actorMappings:actorMappings>
                """;
    }

    private static String buildFormMappingXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <form:formMappingModel xmlns:form="http://www.bonitasoft.org/ns/form/6.0">
                    <form-mappings/>
                </form:formMappingModel>
                """;
    }

    // =========================================================================
    // Connector Definitions
    // =========================================================================

    private static List<ConnectorDef> buildConnectorDefs() {
        List<ConnectorDef> defs = new ArrayList<>();

        // Common auth inputs (empty TYPE_CONSTANT for unused fields)
        // serviceAccountJson is always TYPE_VARIABLE

        // 1. CreateFolder
        defs.add(new ConnectorDef("gdrive-create-folder",
                authInputs(
                        constInput("folderName", "Bonita-BTT-Test"),
                        varInput("parentFolderId", "parentFolderId"),
                        constInput("description", "BTT test folder")),
                List.of(
                        boolOutput("success", "gdriveSuccess"),
                        strOutput("errorMessage", "gdriveErrorMessage"),
                        strOutput("folderId", "gdriveFolderId"),
                        strOutput("folderName", "gdriveFileName"))));

        // 2. Upload
        defs.add(new ConnectorDef("gdrive-upload",
                authInputs(
                        constInput("fileName", "btt-test.txt"),
                        constInput("fileContent", Base64.getEncoder().encodeToString("Hello from BTT".getBytes(StandardCharsets.UTF_8))),
                        constInput("mimeType", "text/plain"),
                        varInput("parentFolderId", "gdriveFolderId"),
                        boolConstInput("convertToGoogleFormat", "false")),
                List.of(
                        boolOutput("success", "gdriveSuccess"),
                        strOutput("errorMessage", "gdriveErrorMessage"),
                        strOutput("fileId", "gdriveFileId"),
                        strOutput("fileName", "gdriveFileName"))));

        // 3. Search
        defs.add(new ConnectorDef("gdrive-search",
                authInputs(
                        constInput("nameContains", "btt-test"),
                        varInput("parentFolderId", "gdriveFolderId"),
                        boolConstInput("includeTrashed", "false")),
                List.of(
                        boolOutput("success", "gdriveSuccess"),
                        strOutput("errorMessage", "gdriveErrorMessage"),
                        intOutput("totalResults", "gdriveTotalResults"))));

        // 4. Download
        defs.add(new ConnectorDef("gdrive-download",
                authInputs(
                        varInput("fileId", "gdriveFileId")),
                List.of(
                        boolOutput("success", "gdriveSuccess"),
                        strOutput("errorMessage", "gdriveErrorMessage"),
                        strOutput("fileContent", "gdriveFileContent"),
                        strOutput("fileName", "gdriveFileName"))));

        // 5. Copy
        defs.add(new ConnectorDef("gdrive-copy",
                authInputs(
                        varInput("sourceFileId", "gdriveFileId"),
                        constInput("newName", "btt-test-copy.txt")),
                List.of(
                        boolOutput("success", "gdriveSuccess"),
                        strOutput("errorMessage", "gdriveErrorMessage"),
                        strOutput("newFileId", "gdriveCopiedFileId"))));

        // 6. CreateArchiveFolder (reuses gdrive-create-folder)
        defs.add(new ConnectorDef("gdrive-create-folder",
                authInputs(
                        constInput("folderName", "Archive"),
                        varInput("parentFolderId", "gdriveFolderId")),
                List.of(
                        boolOutput("success", "gdriveSuccess"),
                        strOutput("errorMessage", "gdriveErrorMessage"),
                        strOutput("folderId", "gdriveSubFolderId"))));

        // 7. Move
        defs.add(new ConnectorDef("gdrive-move",
                authInputs(
                        varInput("fileId", "gdriveCopiedFileId"),
                        varInput("destinationFolderId", "gdriveSubFolderId"),
                        boolConstInput("removeFromCurrentParent", "true")),
                List.of(
                        boolOutput("success", "gdriveSuccess"),
                        strOutput("errorMessage", "gdriveErrorMessage"))));

        // 8. UploadConvert
        defs.add(new ConnectorDef("gdrive-upload",
                authInputs(
                        constInput("fileName", "ConvertedDoc.txt"),
                        constInput("fileContent", Base64.getEncoder().encodeToString("Document to convert to Google Doc".getBytes(StandardCharsets.UTF_8))),
                        constInput("mimeType", "text/plain"),
                        varInput("parentFolderId", "gdriveFolderId"),
                        boolConstInput("convertToGoogleFormat", "true")),
                List.of(
                        boolOutput("success", "gdriveSuccess"),
                        strOutput("errorMessage", "gdriveErrorMessage"),
                        strOutput("fileId", "gdriveGoogleDocId"))));

        // 9. Export
        defs.add(new ConnectorDef("gdrive-export",
                authInputs(
                        varInput("fileId", "gdriveGoogleDocId"),
                        constInput("exportMimeType", "application/pdf")),
                List.of(
                        boolOutput("success", "gdriveSuccess"),
                        strOutput("errorMessage", "gdriveErrorMessage"),
                        strOutput("fileContent", "gdriveExportedContent"))));

        // 10. Delete
        defs.add(new ConnectorDef("gdrive-delete",
                authInputs(
                        varInput("fileId", "gdriveCopiedFileId"),
                        boolConstInput("permanent", "false")),
                List.of(
                        boolOutput("success", "gdriveSuccess"),
                        strOutput("errorMessage", "gdriveErrorMessage"))));

        return defs;
    }

    /**
     * Creates the full input list with auth inputs prepended, followed by operation-specific inputs.
     */
    private static List<InputMapping> authInputs(InputMapping... operationInputs) {
        List<InputMapping> inputs = new ArrayList<>();
        // serviceAccountJson: TYPE_VARIABLE from process variable
        inputs.add(varInput("serviceAccountJson", "serviceAccountJson"));
        // Optional auth params: empty TYPE_CONSTANT
        inputs.add(emptyConstInput("impersonateUser"));
        inputs.add(emptyConstInput("clientId"));
        inputs.add(emptyConstInput("clientSecret"));
        inputs.add(emptyConstInput("refreshToken"));
        inputs.add(emptyConstInput("applicationName"));
        inputs.add(emptyIntConstInput("connectTimeout"));
        inputs.add(emptyIntConstInput("readTimeout"));
        // Operation-specific inputs
        inputs.addAll(List.of(operationInputs));
        return inputs;
    }

    // =========================================================================
    // Variable Lists
    // =========================================================================

    private static List<String> getStringVariables() {
        return List.of(
                "serviceAccountJson", "parentFolderId",
                "gdriveFolderId", "gdriveSubFolderId",
                "gdriveFileId", "gdriveCopiedFileId", "gdriveGoogleDocId",
                "gdriveFileName", "gdriveFileContent", "gdriveExportedContent",
                "gdriveErrorMessage");
    }

    // =========================================================================
    // JAR / .impl Extraction
    // =========================================================================

    private static File findBonitaJar(String jarName) {
        Path targetDir = Path.of("target");
        File jarFile = targetDir.resolve(jarName).toFile();
        if (!jarFile.exists()) {
            // Try from module directory
            jarFile = Path.of("bonita-connector-gdrive-all", "target", jarName).toFile();
        }
        if (!jarFile.exists()) {
            throw new IllegalStateException(
                    "Bonita JAR not found. Run 'mvn clean install -DskipTests' first. " +
                            "Expected: target/" + jarName);
        }
        return jarFile;
    }

    /**
     * Extracts root-level .impl files from the bonita JAR and adds them
     * under the {@code connector/} directory in the .bar ZIP.
     * The entry name uses the {@code <implementationId>} from the .impl XML.
     */
    private static List<String> extractImplFiles(File bonitaJar, ZipOutputStream zos) throws IOException {
        List<String> implNames = new ArrayList<>();

        try (ZipFile zip = new ZipFile(bonitaJar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                // Root-level .impl files only (not in subdirectories)
                if (name.endsWith(".impl") && !name.contains("/")) {
                    // Read the .impl content to extract implementationId
                    byte[] implContent;
                    try (InputStream is = zip.getInputStream(entry)) {
                        implContent = is.readAllBytes();
                    }
                    String implXml = new String(implContent, StandardCharsets.UTF_8);
                    String implId = extractXmlTagContent(implXml, "implementationId");
                    String entryName = (implId != null ? implId : name.replace(".impl", "")) + ".impl";

                    zos.putNextEntry(new ZipEntry("connector/" + entryName));
                    zos.write(implContent);
                    zos.closeEntry();
                    implNames.add(entryName);
                }
            }
        }

        if (implNames.isEmpty()) {
            throw new IllegalStateException(
                    "No .impl files found at root level in " + bonitaJar.getName() +
                            ". Verify the connector modules are built correctly.");
        }

        return implNames;
    }

    /**
     * Simple XML tag content extractor (no dependency on XML parser).
     */
    private static String extractXmlTagContent(String xml, String tagName) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int start = xml.indexOf(openTag);
        if (start < 0) return null;
        start += openTag.length();
        int end = xml.indexOf(closeTag, start);
        if (end < 0) return null;
        return xml.substring(start, end).trim();
    }

    // =========================================================================
    // Internal Model
    // =========================================================================

    private record ConnectorDef(String definitionId, List<InputMapping> inputs, List<OutputMapping> outputs) {}

    private record InputMapping(String connectorInputName, String expressionContent,
                                String expressionType, String returnType) {}

    private record OutputMapping(String connectorOutputName, String processVariableName, String returnType) {}

    /** Creates a TYPE_VARIABLE input referencing a process variable. */
    private static InputMapping varInput(String connectorInput, String processVar) {
        return new InputMapping(connectorInput, processVar, "TYPE_VARIABLE", "java.lang.String");
    }

    /** Creates a TYPE_CONSTANT String input with a literal value. */
    private static InputMapping constInput(String connectorInput, String value) {
        return new InputMapping(connectorInput, value, "TYPE_CONSTANT", "java.lang.String");
    }

    /** Creates a TYPE_CONSTANT Boolean input with a literal value ("true"/"false"). */
    private static InputMapping boolConstInput(String connectorInput, String value) {
        return new InputMapping(connectorInput, value, "TYPE_CONSTANT", "java.lang.Boolean");
    }

    /** Creates an empty TYPE_CONSTANT String input. */
    private static InputMapping emptyConstInput(String connectorInput) {
        return new InputMapping(connectorInput, "", "TYPE_CONSTANT", "java.lang.String");
    }

    /** Creates an empty TYPE_CONSTANT Integer input. */
    private static InputMapping emptyIntConstInput(String connectorInput) {
        return new InputMapping(connectorInput, "", "TYPE_CONSTANT", "java.lang.Integer");
    }

    private static OutputMapping strOutput(String connectorOutput, String processVar) {
        return new OutputMapping(connectorOutput, processVar, "java.lang.String");
    }

    private static OutputMapping boolOutput(String connectorOutput, String processVar) {
        return new OutputMapping(connectorOutput, processVar, "java.lang.Boolean");
    }

    private static OutputMapping intOutput(String connectorOutput, String processVar) {
        return new OutputMapping(connectorOutput, processVar, "java.lang.Integer");
    }

    private static long nextId() {
        return ID_COUNTER.getAndIncrement();
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

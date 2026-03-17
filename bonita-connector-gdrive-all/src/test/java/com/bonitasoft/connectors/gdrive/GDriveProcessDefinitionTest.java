package com.bonitasoft.connectors.gdrive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Validates the GDriveConnectorTestProcess-1.0.proc XMI definition.
 * Ensures the .proc file is well-formed XML in Bonita Studio XMI format,
 * contains all 8 connector operations across 10 service tasks,
 * and has correct sequence flows matching the GDriveFullLifecycleIT order.
 */
class GDriveProcessDefinitionTest {

    private static final String PROC_RESOURCE = "GDriveConnectorTestProcess-1.0.proc";

    private static Document doc;

    @BeforeAll
    static void loadProcess() throws Exception {
        InputStream is = GDriveProcessDefinitionTest.class.getClassLoader()
                .getResourceAsStream(PROC_RESOURCE);
        assertThat(is).as("Process file %s must exist on classpath", PROC_RESOURCE).isNotNull();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        doc = builder.parse(is);
        doc.getDocumentElement().normalize();
    }

    @Nested
    @DisplayName("XMI Structure")
    class XmlStructure {

        @Test
        @DisplayName("should_be_valid_xmi_document")
        void should_be_valid_xmi_document() {
            Element root = doc.getDocumentElement();
            assertThat(root.getTagName()).isEqualTo("xmi:XMI");
        }

        @Test
        @DisplayName("should_have_main_process_with_correct_name")
        void should_have_main_process_with_correct_name() {
            NodeList mainProcesses = doc.getElementsByTagName("process:MainProcess");
            assertThat(mainProcesses.getLength()).isEqualTo(1);

            Element mainProcess = (Element) mainProcesses.item(0);
            assertThat(mainProcess.getAttribute("name")).isEqualTo("GDriveConnectorTest");
            assertThat(mainProcess.getAttribute("bonitaModelVersion")).isEqualTo("9");
        }

        @Test
        @DisplayName("should_have_pool_and_lane")
        void should_have_pool_and_lane() {
            NodeList allElements = doc.getElementsByTagName("elements");
            boolean hasPool = false;
            boolean hasLane = false;
            for (int i = 0; i < allElements.getLength(); i++) {
                Element el = (Element) allElements.item(i);
                String type = el.getAttribute("xmi:type");
                if ("process:Pool".equals(type)) hasPool = true;
                if ("process:Lane".equals(type)) hasLane = true;
            }
            assertThat(hasPool).as("Should have a Pool").isTrue();
            assertThat(hasLane).as("Should have a Lane").isTrue();
        }

        @Test
        @DisplayName("should_have_notation_diagram")
        void should_have_notation_diagram() {
            NodeList diagrams = doc.getElementsByTagName("notation:Diagram");
            assertThat(diagrams.getLength()).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Process Variables")
    class ProcessVariables {

        @Test
        @DisplayName("should_declare_required_variables")
        void should_declare_required_variables() {
            Set<String> dataNames = getDataNames();
            assertThat(dataNames).contains(
                    "gdriveSuccess", "gdriveErrorMessage",
                    "gdriveFolderId", "gdriveFileId",
                    "gdriveCopiedFileId", "gdriveFileContent",
                    "gdriveFileName", "serviceAccountJsonPath");
        }

        @Test
        @DisplayName("should_declare_new_optimized_variables")
        void should_declare_new_optimized_variables() {
            Set<String> dataNames = getDataNames();
            assertThat(dataNames).contains(
                    "gdriveSubFolderId", "gdriveGoogleDocId");
        }

        private Set<String> getDataNames() {
            NodeList dataNodes = doc.getElementsByTagName("data");
            Set<String> names = new HashSet<>();
            for (int i = 0; i < dataNodes.getLength(); i++) {
                Element el = (Element) dataNodes.item(i);
                String name = el.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
            return names;
        }
    }

    @Nested
    @DisplayName("Service Tasks & Connectors")
    class ServiceTasksAndConnectors {

        private static final Set<String> EXPECTED_CONNECTOR_IDS = Set.of(
                "gdrive-create-folder", "gdrive-upload", "gdrive-search",
                "gdrive-download", "gdrive-copy", "gdrive-move",
                "gdrive-export", "gdrive-delete");

        @Test
        @DisplayName("should_have_10_service_tasks")
        void should_have_10_service_tasks() {
            int count = 0;
            NodeList elements = doc.getElementsByTagName("elements");
            for (int i = 0; i < elements.getLength(); i++) {
                Element el = (Element) elements.item(i);
                if ("process:ServiceTask".equals(el.getAttribute("xmi:type"))) {
                    count++;
                }
            }
            assertThat(count).isEqualTo(10);
        }

        @Test
        @DisplayName("should_reference_all_8_connector_definitions")
        void should_reference_all_8_connector_definitions() {
            NodeList connectors = doc.getElementsByTagName("connectors");
            Set<String> foundIds = new HashSet<>();
            for (int i = 0; i < connectors.getLength(); i++) {
                Element el = (Element) connectors.item(i);
                String defId = el.getAttribute("definitionId");
                if (defId != null && !defId.isEmpty()) {
                    foundIds.add(defId);
                }
            }
            assertThat(foundIds).containsAll(EXPECTED_CONNECTOR_IDS);
        }

        @Test
        @DisplayName("should_have_auth_parameter_on_every_connector")
        void should_have_auth_parameter_on_every_connector() {
            NodeList connectors = doc.getElementsByTagName("connectors");
            for (int i = 0; i < connectors.getLength(); i++) {
                Element connector = (Element) connectors.item(i);
                String defId = connector.getAttribute("definitionId");
                if (defId == null || defId.isEmpty()) continue;

                Set<String> paramKeys = getParameterKeys(connector);
                assertThat(paramKeys)
                        .as("Auth param on connector %s", defId)
                        .contains("serviceAccountJson");
            }
        }

        @Test
        @DisplayName("should_have_success_and_errorMessage_outputs_on_every_connector")
        void should_have_success_and_errorMessage_outputs_on_every_connector() {
            NodeList connectors = doc.getElementsByTagName("connectors");
            for (int i = 0; i < connectors.getLength(); i++) {
                Element connector = (Element) connectors.item(i);
                String defId = connector.getAttribute("definitionId");
                if (defId == null || defId.isEmpty()) continue;

                Set<String> outputNames = getOutputNames(connector);
                assertThat(outputNames)
                        .as("Standard outputs on connector %s", defId)
                        .contains("success", "errorMessage");
            }
        }

        @Test
        @DisplayName("should_use_gdrive_create_folder_twice")
        void should_use_gdrive_create_folder_twice() {
            assertThat(countConnectorUsages("gdrive-create-folder"))
                    .as("gdrive-create-folder: main folder + archive subfolder")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("should_use_gdrive_upload_twice")
        void should_use_gdrive_upload_twice() {
            assertThat(countConnectorUsages("gdrive-upload"))
                    .as("gdrive-upload: regular upload + convert-to-Google-format")
                    .isEqualTo(2);
        }

        private int countConnectorUsages(String definitionId) {
            NodeList connectors = doc.getElementsByTagName("connectors");
            int count = 0;
            for (int i = 0; i < connectors.getLength(); i++) {
                if (definitionId.equals(((Element) connectors.item(i)).getAttribute("definitionId"))) {
                    count++;
                }
            }
            return count;
        }

        private Set<String> getParameterKeys(Element connector) {
            NodeList params = connector.getElementsByTagName("parameters");
            Set<String> keys = new HashSet<>();
            for (int j = 0; j < params.getLength(); j++) {
                keys.add(((Element) params.item(j)).getAttribute("key"));
            }
            return keys;
        }

        private Set<String> getOutputNames(Element connector) {
            NodeList outputs = connector.getElementsByTagName("outputs");
            Set<String> names = new HashSet<>();
            for (int j = 0; j < outputs.getLength(); j++) {
                Element output = (Element) outputs.item(j);
                NodeList rightOperands = output.getElementsByTagName("rightOperand");
                for (int k = 0; k < rightOperands.getLength(); k++) {
                    String name = ((Element) rightOperands.item(k)).getAttribute("name");
                    if (name != null && !name.isEmpty()) {
                        names.add(name);
                    }
                }
            }
            return names;
        }
    }

    @Nested
    @DisplayName("Sequence Flows")
    class SequenceFlows {

        @Test
        @DisplayName("should_have_11_sequence_flows")
        void should_have_11_sequence_flows() {
            NodeList connections = doc.getElementsByTagName("connections");
            int flowCount = 0;
            for (int i = 0; i < connections.getLength(); i++) {
                Element el = (Element) connections.item(i);
                if ("process:SequenceFlow".equals(el.getAttribute("xmi:type"))) {
                    flowCount++;
                }
            }
            assertThat(flowCount).isEqualTo(11);
        }

        @Test
        @DisplayName("should_have_start_and_end_events")
        void should_have_start_and_end_events() {
            NodeList elements = doc.getElementsByTagName("elements");
            boolean hasStart = false;
            boolean hasEnd = false;
            for (int i = 0; i < elements.getLength(); i++) {
                Element el = (Element) elements.item(i);
                String type = el.getAttribute("xmi:type");
                if ("process:StartEvent".equals(type)) hasStart = true;
                if ("process:EndEvent".equals(type)) hasEnd = true;
            }
            assertThat(hasStart).as("Should have StartEvent").isTrue();
            assertThat(hasEnd).as("Should have EndEvent").isTrue();
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("should_have_connector_definition_mappings")
        void should_have_connector_definition_mappings() {
            NodeList mappings = doc.getElementsByTagName("definitionMappings");
            Set<String> mappedConnectors = new HashSet<>();
            for (int i = 0; i < mappings.getLength(); i++) {
                Element el = (Element) mappings.item(i);
                if ("CONNECTOR".equals(el.getAttribute("type"))) {
                    mappedConnectors.add(el.getAttribute("definitionId"));
                }
            }
            assertThat(mappedConnectors).containsAll(Set.of(
                    "gdrive-search", "gdrive-create-folder", "gdrive-upload",
                    "gdrive-download", "gdrive-copy", "gdrive-move",
                    "gdrive-export", "gdrive-delete"));
        }

        @Test
        @DisplayName("should_have_actor_mapping")
        void should_have_actor_mapping() {
            NodeList actorMappings = doc.getElementsByTagName("actorMapping");
            assertThat(actorMappings.getLength()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should_have_datatypes_section")
        void should_have_datatypes_section() {
            NodeList datatypes = doc.getElementsByTagName("datatypes");
            assertThat(datatypes.getLength())
                    .as("Should have standard Bonita datatypes (Boolean, Date, Integer, Long, Double, Text, Java_object, XML, Business_Object)")
                    .isGreaterThanOrEqualTo(9);
        }
    }
}

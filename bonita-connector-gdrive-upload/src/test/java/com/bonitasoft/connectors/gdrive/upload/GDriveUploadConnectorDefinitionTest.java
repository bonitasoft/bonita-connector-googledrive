package com.bonitasoft.connectors.gdrive.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Tests that the connector definition XML (.def) is valid and consistent.
 * <p>
 * Note: Tests for .impl file are excluded because it requires Maven resource filtering
 * which is not available during unit test phase.
 */
@DisplayName("GDrive Upload Connector Definition Tests")
class GDriveUploadConnectorDefinitionTest {

    private static Document defDocument;
    private static List<String> defInputNames;
    private static List<String> defOutputNames;
    private static List<String> defMandatoryInputs;

    @BeforeAll
    static void loadDefinition() throws Exception {
        // Load the connector definition XML
        String defPath = "gdrive-upload.def";
        InputStream defIs = GDriveUploadConnectorDefinitionTest.class.getClassLoader()
                .getResourceAsStream(defPath);
        assertThat(defIs).as("Definition file '%s' should exist", defPath).isNotNull();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        defDocument = builder.parse(defIs);

        // Extract input parameter names
        defInputNames = new ArrayList<>();
        defMandatoryInputs = new ArrayList<>();
        NodeList inputs = defDocument.getElementsByTagNameNS("*", "input");
        for (int i = 0; i < inputs.getLength(); i++) {
            Element input = (Element) inputs.item(i);
            String name = input.getAttribute("name");
            defInputNames.add(name);
            if ("true".equals(input.getAttribute("mandatory"))) {
                defMandatoryInputs.add(name);
            }
        }

        // Extract output parameter names
        defOutputNames = new ArrayList<>();
        NodeList outputs = defDocument.getElementsByTagNameNS("*", "output");
        for (int i = 0; i < outputs.getLength(); i++) {
            Element output = (Element) outputs.item(i);
            defOutputNames.add(output.getAttribute("name"));
        }
    }

    @Test
    @DisplayName("Definition should have a valid connector ID")
    void shouldHaveValidConnectorId() {
        NodeList ids = defDocument.getElementsByTagNameNS("*", "id");
        assertThat(ids.getLength()).isGreaterThan(0);
        
        String connectorId = ids.item(0).getTextContent();
        assertThat(connectorId)
                .as("Connector ID should follow {service}-{operation} convention")
                .isEqualTo("gdrive-upload");
    }

    @Test
    @DisplayName("Definition should have standard output parameters")
    void shouldHaveStandardOutputs() {
        assertThat(defOutputNames)
                .as("Must include standard 'success' output")
                .contains("success");
        assertThat(defOutputNames)
                .as("Must include standard 'errorMessage' output")
                .contains("errorMessage");
    }

    @Test
    @DisplayName("Definition should have required input parameters")
    void shouldHaveRequiredInputs() {
        assertThat(defInputNames)
                .as("Must include fileName input")
                .contains("fileName");
        assertThat(defInputNames)
                .as("Must include fileContent input")
                .contains("fileContent");
    }

    @Test
    @DisplayName("Mandatory inputs should be marked correctly")
    void shouldHaveCorrectMandatoryInputs() {
        assertThat(defMandatoryInputs)
                .as("fileName should be mandatory")
                .contains("fileName");
        assertThat(defMandatoryInputs)
                .as("fileContent should be mandatory")
                .contains("fileContent");
    }

    @Test
    @DisplayName("Definition should have timeout parameters")
    void shouldHaveTimeoutParameters() {
        assertThat(defInputNames).contains("connectTimeout", "readTimeout");
    }

    @Test
    @DisplayName("Definition should have at least 2 UI pages")
    void shouldHaveUIPages() {
        NodeList pages = defDocument.getElementsByTagNameNS("*", "page");
        assertThat(pages.getLength())
                .as("Should have at least auth page and operation page")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("All inputs should be assigned to a UI widget")
    void allInputsShouldBeInUIWidgets() {
        // In namespace 6.1, inputName is an ATTRIBUTE on <widget>, not a child element
        Set<String> widgetInputNames = new HashSet<>();
        NodeList widgets = defDocument.getElementsByTagNameNS("*", "widget");
        for (int i = 0; i < widgets.getLength(); i++) {
            Element widget = (Element) widgets.item(i);
            String inputName = widget.getAttribute("inputName");
            if (inputName != null && !inputName.isBlank()) {
                widgetInputNames.add(inputName);
            }
        }

        for (String inputName : defInputNames) {
            assertThat(widgetInputNames)
                    .as("Input '%s' should be assigned to a UI widget", inputName)
                    .contains(inputName);
        }
    }

    @Test
    @DisplayName("Widgets should have labels in properties file")
    void widgetsShouldHaveLabels() {
        // Labels are defined in .properties file, not as XML child elements
        // (EMF namespace 6.1 uses .properties for i18n labels)
        InputStream propsIs = getClass().getClassLoader()
                .getResourceAsStream("gdrive-upload.properties");
        assertThat(propsIs).as("Properties file should exist").isNotNull();

        java.util.Properties props = new java.util.Properties();
        try {
            props.load(propsIs);
        } catch (Exception e) {
            assertThat(false).as("Properties file should be loadable: " + e.getMessage()).isTrue();
        }

        // Each widget ID should have a corresponding .label key in properties
        NodeList widgets = defDocument.getElementsByTagNameNS("*", "widget");
        int labelCount = 0;
        for (int i = 0; i < widgets.getLength(); i++) {
            Element widget = (Element) widgets.item(i);
            String widgetId = widget.getAttribute("id");
            if (widgetId != null && !widgetId.isBlank()) {
                String labelKey = widgetId + ".label";
                if (props.containsKey(labelKey)) {
                    labelCount++;
                }
            }
        }
        assertThat(labelCount)
                .as("Widgets should have descriptive labels in .properties file")
                .isGreaterThan(5);
    }

    @Test
    @DisplayName("Definition should have operation-specific outputs")
    void shouldHaveOperationOutputs() {
        assertThat(defOutputNames)
                .as("Upload should output fileId")
                .contains("fileId");
        assertThat(defOutputNames)
                .as("Upload should output webViewLink")
                .contains("webViewLink");
    }

    @Test
    @DisplayName("Connector class should exist and be loadable")
    void connectorClassShouldExist() {
        String expectedClass = "com.bonitasoft.connectors.gdrive.upload.GDriveUploadConnector";
        try {
            Class<?> clazz = Class.forName(expectedClass);
            assertThat(clazz).isNotNull();
            assertThat(clazz.getSuperclass().getSimpleName()).isEqualTo("AbstractGDriveConnector");
        } catch (ClassNotFoundException e) {
            assertThat(false)
                    .as("Connector class '%s' should exist and be loadable", expectedClass)
                    .isTrue();
        }
    }
}

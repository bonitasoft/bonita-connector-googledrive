package com.bonitasoft.connectors.gdrive;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadFileConnectorPropertyTest {

    private static final String VALID_KEY_JSON = "{\"type\":\"service_account\",\"project_id\":\"test\"}";

    private UploadFileConnector createConnector(Map<String, Object> inputs) {
        UploadFileConnector connector = new UploadFileConnector();
        connector.setInputParameters(inputs);
        return connector;
    }

    private Map<String, Object> buildValidInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(UploadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        inputs.put(UploadFileConnector.INPUT_FILE_NAME, "test.txt");
        inputs.put(UploadFileConnector.INPUT_FILE_CONTENT_BASE64,
                Base64.getEncoder().encodeToString("content".getBytes()));
        inputs.put(UploadFileConnector.INPUT_MIME_TYPE, "text/plain");
        return inputs;
    }

    @Property
    @Label("Any non-blank fileName passes validation")
    void anyNonBlankFileNamePassesValidation(
            @ForAll @StringLength(min = 1, max = 200) @Chars({'a', 'b', 'c', '.', '-', '_', '1', '2', '3'}) String fileName
    ) throws ConnectorValidationException {
        Map<String, Object> inputs = buildValidInputs();
        inputs.put(UploadFileConnector.INPUT_FILE_NAME, fileName);
        UploadFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();
    }

    @Property
    @Label("Any valid base64 string passes validation")
    void anyValidBase64StringPassesValidation(
            @ForAll @StringLength(min = 1, max = 100) @Chars({'a', 'b', 'c', 'd', 'e', 'A', 'B', 'C', '0', '1'}) String rawContent
    ) throws ConnectorValidationException {
        Map<String, Object> inputs = buildValidInputs();
        inputs.put(UploadFileConnector.INPUT_FILE_CONTENT_BASE64,
                Base64.getEncoder().encodeToString(rawContent.getBytes()));
        UploadFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();
    }

    @Property
    @Label("Blank service account key always fails validation")
    void blankServiceAccountKeyFailsValidation(
            @ForAll("blankStrings") String blankKey
    ) {
        Map<String, Object> inputs = buildValidInputs();
        inputs.put(UploadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, blankKey);
        UploadFileConnector connector = createConnector(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    @Property
    @Label("Blank fileName always fails validation")
    void blankFileNameFailsValidation(
            @ForAll("blankStrings") String blankName
    ) {
        Map<String, Object> inputs = buildValidInputs();
        inputs.put(UploadFileConnector.INPUT_FILE_NAME, blankName);
        UploadFileConnector connector = createConnector(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileName");
    }

    @Property
    @Label("Blank fileContentBase64 always fails validation")
    void blankFileContentBase64FailsValidation(
            @ForAll("blankStrings") String blankContent
    ) {
        Map<String, Object> inputs = buildValidInputs();
        inputs.put(UploadFileConnector.INPUT_FILE_CONTENT_BASE64, blankContent);
        UploadFileConnector connector = createConnector(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileContentBase64");
    }

    @Property
    @Label("Application name defaults when null or blank")
    void applicationNameDefaultsWhenMissing(
            @ForAll("nullOrBlankStrings") String appName
    ) throws ConnectorValidationException, NoSuchFieldException, IllegalAccessException {
        Map<String, Object> inputs = buildValidInputs();
        if (appName != null) {
            inputs.put(UploadFileConnector.INPUT_APPLICATION_NAME, appName);
        }
        UploadFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        Field configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getApplicationName()).isEqualTo("Bonita-GoogleDrive-Connector");
    }

    @Property
    @Label("Custom application name is preserved")
    void customApplicationNameIsPreserved(
            @ForAll @StringLength(min = 1, max = 100) @Chars({'a', 'b', 'c', 'A', 'B', 'C', '-', '_'}) String appName
    ) throws ConnectorValidationException, NoSuchFieldException, IllegalAccessException {
        Map<String, Object> inputs = buildValidInputs();
        inputs.put(UploadFileConnector.INPUT_APPLICATION_NAME, appName);
        UploadFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        Field configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getApplicationName()).isEqualTo(appName);
    }

    @Property
    @Label("Connect timeout defaults to 30000 when not provided")
    void connectTimeoutDefaultsWhenNotProvided(
            @ForAll @IntRange(min = 1, max = 300000) int timeout
    ) throws ConnectorValidationException, NoSuchFieldException, IllegalAccessException {
        Map<String, Object> inputs = buildValidInputs();
        inputs.put(UploadFileConnector.INPUT_CONNECT_TIMEOUT, timeout);
        UploadFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        Field configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getConnectTimeout()).isEqualTo(timeout);
    }

    @Property
    @Label("Read timeout defaults to 60000 when not provided")
    void readTimeoutDefaultsWhenNotProvided(
            @ForAll @IntRange(min = 1, max = 600000) int timeout
    ) throws ConnectorValidationException, NoSuchFieldException, IllegalAccessException {
        Map<String, Object> inputs = buildValidInputs();
        inputs.put(UploadFileConnector.INPUT_READ_TIMEOUT, timeout);
        UploadFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        Field configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getReadTimeout()).isEqualTo(timeout);
    }

    @Property
    @Label("Upload strategy defaults to AUTO when not provided")
    void uploadStrategyDefaultsToAuto() throws ConnectorValidationException, NoSuchFieldException, IllegalAccessException {
        Map<String, Object> inputs = buildValidInputs();
        // Do not set uploadStrategy
        UploadFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        Field configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getUploadStrategy()).isEqualTo("AUTO");
    }

    @Property
    @Label("MIME type defaults to application/octet-stream when not provided")
    void mimeTypeDefaultsWhenNotProvided() throws ConnectorValidationException, NoSuchFieldException, IllegalAccessException {
        Map<String, Object> inputs = buildValidInputs();
        inputs.remove(UploadFileConnector.INPUT_MIME_TYPE);
        UploadFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        Field configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getMimeType()).isEqualTo("application/octet-stream");
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", "   ", "\t", "\n", " \t\n ");
    }

    @Provide
    Arbitrary<String> nullOrBlankStrings() {
        return Arbitraries.of(null, "", "   ", "\t");
    }
}

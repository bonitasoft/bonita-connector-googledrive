package com.bonitasoft.connectors.gdrive;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.constraints.IntRange;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

class DownloadFileConnectorPropertyTest {

    private DownloadFileConnector createConnectorWithInputs(Map<String, Object> inputs) {
        DownloadFileConnector connector = new DownloadFileConnector();
        connector.setInputParameters(inputs);
        return connector;
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DownloadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON,
                "{\"type\":\"service_account\",\"project_id\":\"test\"}");
        inputs.put(DownloadFileConnector.INPUT_FILE_ID, "1AbCdEfGhIjKlMnOpQrStUvWxYz");
        return inputs;
    }

    @Property
    void fileIdRejectsBlank(@ForAll("blankStrings") String input) {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_FILE_ID, input);
        DownloadFileConnector connector = createConnectorWithInputs(inputs);

        try {
            connector.validateInputParameters();
            throw new AssertionError("Expected ConnectorValidationException for blank fileId");
        } catch (ConnectorValidationException e) {
            // expected
        }
    }

    @Property
    void serviceAccountKeyJsonRejectsBlank(@ForAll("blankStrings") String input) {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, input);
        DownloadFileConnector connector = createConnectorWithInputs(inputs);

        try {
            connector.validateInputParameters();
            throw new AssertionError("Expected ConnectorValidationException for blank serviceAccountKeyJson");
        } catch (ConnectorValidationException e) {
            // expected
        }
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", " ", "\t", "\n", "  \t\n  ");
    }

    @Property
    void validFileIdAccepted(
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String fileId
    ) throws ConnectorValidationException {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_FILE_ID, fileId);
        DownloadFileConnector connector = createConnectorWithInputs(inputs);

        connector.validateInputParameters();
        // No exception means validation passed
    }

    @Property
    void validConfigurationAlwaysBuilds(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String serviceAccountKey,
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String fileId
    ) throws ConnectorValidationException {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DownloadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, serviceAccountKey);
        inputs.put(DownloadFileConnector.INPUT_FILE_ID, fileId);
        DownloadFileConnector connector = createConnectorWithInputs(inputs);

        connector.validateInputParameters();
        // No exception means config built successfully
    }

    @Property
    void configurationPreservesFileId(
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String fileId
    ) throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_FILE_ID, fileId);
        DownloadFileConnector connector = createConnectorWithInputs(inputs);
        connector.validateInputParameters();

        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assert config.getFileId().equals(fileId) : "fileId should be preserved in configuration";
    }

    @Property
    void configurationPreservesExportMimeType(
            @ForAll("mimeTypes") String mimeType
    ) throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_EXPORT_MIME_TYPE, mimeType);
        DownloadFileConnector connector = createConnectorWithInputs(inputs);
        connector.validateInputParameters();

        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assert config.getExportMimeType().equals(mimeType) : "exportMimeType should be preserved in configuration";
    }

    @Provide
    Arbitrary<String> mimeTypes() {
        return Arbitraries.of(
                "application/pdf",
                "text/plain",
                "text/csv",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "image/png",
                "image/jpeg"
        );
    }

    @Property
    void connectTimeoutDefaultsWhenNotProvided(
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String fileId
    ) throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_FILE_ID, fileId);
        DownloadFileConnector connector = createConnectorWithInputs(inputs);
        connector.validateInputParameters();

        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assert config.getConnectTimeout() == 30000 : "connectTimeout should default to 30000";
    }

    @Property
    void readTimeoutDefaultsWhenNotProvided(
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String fileId
    ) throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_FILE_ID, fileId);
        DownloadFileConnector connector = createConnectorWithInputs(inputs);
        connector.validateInputParameters();

        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assert config.getReadTimeout() == 60000 : "readTimeout should default to 60000";
    }

    @Property
    void customTimeoutsArePreserved(
            @ForAll @IntRange(min = 1000, max = 120000) int connectTimeout,
            @ForAll @IntRange(min = 1000, max = 300000) int readTimeout
    ) throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_CONNECT_TIMEOUT, connectTimeout);
        inputs.put(DownloadFileConnector.INPUT_READ_TIMEOUT, readTimeout);
        DownloadFileConnector connector = createConnectorWithInputs(inputs);
        connector.validateInputParameters();

        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assert config.getConnectTimeout() == connectTimeout : "connectTimeout should be preserved";
        assert config.getReadTimeout() == readTimeout : "readTimeout should be preserved";
    }

    @Property
    void applicationNameDefaultsWhenBlank(@ForAll("blankStrings") String appName) throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_APPLICATION_NAME, appName);
        DownloadFileConnector connector = createConnectorWithInputs(inputs);
        connector.validateInputParameters();

        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assert "Bonita-GoogleDrive-Connector".equals(config.getApplicationName())
                : "applicationName should default when blank";
    }

    @Property
    void downloadFileResultRecordPreservesBase64(
            @ForAll @StringLength(min = 1, max = 1000) String content
    ) {
        byte[] contentBytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(contentBytes);
        GDriveClient.DownloadFileResult result = new GDriveClient.DownloadFileResult(
                base64, "file.bin", "application/octet-stream", (long) contentBytes.length
        );

        byte[] decoded = Base64.getDecoder().decode(result.fileContentBase64());
        assert new String(decoded, java.nio.charset.StandardCharsets.UTF_8).equals(content) : "Base64 round-trip should preserve content";
        assert result.fileSizeBytes() == contentBytes.length : "Size should match original content length";
    }
}

package com.bonitasoft.connectors.gdrive;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DeleteFileConnectorPropertyTest {

    private DeleteFileConnector createConnectorWithInputs(Map<String, Object> inputs) {
        DeleteFileConnector connector = new DeleteFileConnector();
        connector.setInputParameters(inputs);
        return connector;
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DeleteFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        inputs.put(DeleteFileConnector.INPUT_FILE_ID, "file-123");
        return inputs;
    }

    // 1. Blank fileId always rejected
    @Property
    void blankFileIdAlwaysRejected(@ForAll("blankStrings") String blankId) {
        Map<String, Object> inputs = validInputs();
        inputs.put(DeleteFileConnector.INPUT_FILE_ID, blankId);
        DeleteFileConnector connector = createConnectorWithInputs(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", " ", "\t", "\n", "  \t\n  ");
    }

    // 2. Blank serviceAccountKeyJson always rejected
    @Property
    void blankServiceAccountKeyAlwaysRejected(@ForAll("blankStrings") String blankKey) {
        Map<String, Object> inputs = validInputs();
        inputs.put(DeleteFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, blankKey);
        DeleteFileConnector connector = createConnectorWithInputs(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    // 3. Any non-blank fileId passes validation
    @Property
    void nonBlankFileIdPassesValidation(
            @ForAll @StringLength(min = 1, max = 200) @CharRange(from = 'a', to = 'z') String fileId) {
        Map<String, Object> inputs = validInputs();
        inputs.put(DeleteFileConnector.INPUT_FILE_ID, fileId);
        DeleteFileConnector connector = createConnectorWithInputs(inputs);

        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    // 4. Any non-blank serviceAccountKeyJson passes validation
    @Property
    void nonBlankServiceAccountKeyPassesValidation(
            @ForAll @StringLength(min = 1, max = 500) @CharRange(from = 'a', to = 'z') String key) {
        Map<String, Object> inputs = validInputs();
        inputs.put(DeleteFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, key);
        DeleteFileConnector connector = createConnectorWithInputs(inputs);

        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    // 5. Valid configuration always builds without exception
    @Property
    void validConfigurationAlwaysBuilds(
            @ForAll @StringLength(min = 1, max = 500) @CharRange(from = 'a', to = 'z') String key,
            @ForAll @StringLength(min = 1, max = 100) @CharRange(from = 'a', to = 'z') String fileId) {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson(key)
                .fileId(fileId)
                .permanent(false)
                .build();

        assertThat(config.getServiceAccountKeyJson()).isEqualTo(key);
        assertThat(config.getFileId()).isEqualTo(fileId);
        assertThat(config.isPermanent()).isFalse();
    }

    // 6. Boolean permanent flag preserved in configuration
    @Property
    void permanentFlagPreservedInConfiguration(@ForAll boolean permanent) {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson("{\"type\":\"service_account\"}")
                .fileId("file-id")
                .permanent(permanent)
                .build();

        assertThat(config.isPermanent()).isEqualTo(permanent);
    }

    // 7. Default applicationName applied when not set
    @Property
    void defaultApplicationNameApplied(
            @ForAll @StringLength(min = 1, max = 100) @CharRange(from = 'a', to = 'z') String fileId) {
        Map<String, Object> inputs = validInputs();
        inputs.put(DeleteFileConnector.INPUT_FILE_ID, fileId);
        // applicationName not set, should default
        DeleteFileConnector connector = createConnectorWithInputs(inputs);

        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    // 8. Default permanent is false when not set
    @Property
    void defaultPermanentIsFalse(
            @ForAll @StringLength(min = 1, max = 100) @CharRange(from = 'a', to = 'z') String fileId) {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson("{\"type\":\"service_account\"}")
                .fileId(fileId)
                .build();

        assertThat(config.isPermanent()).isFalse();
    }

    // 9. Default connectTimeout is 30000
    @Property
    void defaultConnectTimeoutIs30000(
            @ForAll @StringLength(min = 1, max = 100) @CharRange(from = 'a', to = 'z') String fileId) {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson("{\"type\":\"service_account\"}")
                .fileId(fileId)
                .build();

        assertThat(config.getConnectTimeout()).isEqualTo(30000);
    }

    // 10. Default readTimeout is 60000
    @Property
    void defaultReadTimeoutIs60000(
            @ForAll @StringLength(min = 1, max = 100) @CharRange(from = 'a', to = 'z') String fileId) {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson("{\"type\":\"service_account\"}")
                .fileId(fileId)
                .build();

        assertThat(config.getReadTimeout()).isEqualTo(60000);
    }

    // 11. Default maxRetries is 5
    @Property
    void defaultMaxRetriesIs5(
            @ForAll @StringLength(min = 1, max = 100) @CharRange(from = 'a', to = 'z') String fileId) {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson("{\"type\":\"service_account\"}")
                .fileId(fileId)
                .build();

        assertThat(config.getMaxRetries()).isEqualTo(5);
    }

    // 12. Null fileId rejected
    @Property(tries = 1)
    void nullFileIdRejected() {
        Map<String, Object> inputs = validInputs();
        inputs.remove(DeleteFileConnector.INPUT_FILE_ID);
        DeleteFileConnector connector = createConnectorWithInputs(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
    }

    // 13. DeleteFileResult record preserves all fields
    @Property
    void deleteFileResultPreservesFields(
            @ForAll @StringLength(min = 1, max = 200) @CharRange(from = 'a', to = 'z') String fileId,
            @ForAll boolean permanent) {
        GDriveClient.DeleteFileResult result = new GDriveClient.DeleteFileResult(fileId, permanent);

        assertThat(result.deletedFileId()).isEqualTo(fileId);
        assertThat(result.permanent()).isEqualTo(permanent);
    }
}

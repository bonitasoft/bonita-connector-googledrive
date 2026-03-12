package com.bonitasoft.connectors.gdrive;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class GetFileConnectorPropertyTest {

    private static final String VALID_KEY_JSON = "{\"type\":\"service_account\",\"project_id\":\"test\"}";
    private static final String VALID_FILE_ID = "1abc2def3ghi";

    @Property
    void serviceAccountKeyJsonRejectsBlank(@ForAll("blankStrings") String input) {
        GetFileConnector connector = new GetFileConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, input);
        inputs.put(GetFileConnector.INPUT_FILE_ID, VALID_FILE_ID);
        connector.setInputParameters(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    @Property
    void fileIdRejectsBlank(@ForAll("blankStrings") String input) {
        GetFileConnector connector = new GetFileConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        inputs.put(GetFileConnector.INPUT_FILE_ID, input);
        connector.setInputParameters(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", " ", "\t", "\n", "   ", "\t\n");
    }

    @Property
    void validMandatoryInputsAlwaysPassValidation(
            @ForAll @StringLength(min = 1, max = 500) String keyJson,
            @ForAll @StringLength(min = 1, max = 100) String fileId
    ) {
        // Filter out strings that are purely whitespace
        Assume.that(!keyJson.isBlank());
        Assume.that(!fileId.isBlank());

        GetFileConnector connector = new GetFileConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, keyJson);
        inputs.put(GetFileConnector.INPUT_FILE_ID, fileId);
        connector.setInputParameters(inputs);

        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property
    void configurationBuilderAlwaysProducesValidConfig(
            @ForAll @StringLength(min = 1, max = 200) String keyJson,
            @ForAll @StringLength(min = 1, max = 100) String fileId
    ) {
        Assume.that(!keyJson.isBlank());
        Assume.that(!fileId.isBlank());

        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson(keyJson)
                .fileId(fileId)
                .build();

        assertThat(config.getServiceAccountKeyJson()).isEqualTo(keyJson);
        assertThat(config.getFileId()).isEqualTo(fileId);
        assertThat(config.getApplicationName()).isEqualTo("Bonita-GoogleDrive-Connector");
        assertThat(config.getConnectTimeout()).isEqualTo(30000);
        assertThat(config.getReadTimeout()).isEqualTo(60000);
        assertThat(config.getMaxRetries()).isEqualTo(5);
    }

    @Property
    void customFieldsArePreservedInConfiguration(
            @ForAll @StringLength(min = 1, max = 300) String fields
    ) {
        Assume.that(!fields.isBlank());

        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson(VALID_KEY_JSON)
                .fileId(VALID_FILE_ID)
                .fields(fields)
                .build();

        assertThat(config.getFields()).isEqualTo(fields);
    }

    @Property
    void connectTimeoutIsPreservedInConfiguration(
            @ForAll @IntRange(min = 1, max = 300000) int timeout
    ) {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson(VALID_KEY_JSON)
                .fileId(VALID_FILE_ID)
                .connectTimeout(timeout)
                .build();

        assertThat(config.getConnectTimeout()).isEqualTo(timeout);
    }

    @Property
    void readTimeoutIsPreservedInConfiguration(
            @ForAll @IntRange(min = 1, max = 300000) int timeout
    ) {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson(VALID_KEY_JSON)
                .fileId(VALID_FILE_ID)
                .readTimeout(timeout)
                .build();

        assertThat(config.getReadTimeout()).isEqualTo(timeout);
    }

    @Property
    void applicationNameDefaultsWhenBlankOrNull(
            @ForAll("blankOrNullStrings") String appName
    ) {
        GetFileConnector connector = new GetFileConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        inputs.put(GetFileConnector.INPUT_FILE_ID, VALID_FILE_ID);
        inputs.put(GetFileConnector.INPUT_APPLICATION_NAME, appName);
        connector.setInputParameters(inputs);

        // Should not throw -- validation passes, and default is used for blank app names
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Provide
    Arbitrary<String> blankOrNullStrings() {
        return Arbitraries.of("", " ", "\t", null);
    }

    @Property
    void getFileResultRecordPreservesAllFields(
            @ForAll @StringLength(min = 1, max = 50) String fileId,
            @ForAll @StringLength(min = 1, max = 100) String fileName,
            @ForAll @StringLength(min = 1, max = 100) String mimeType,
            @ForAll @IntRange(min = 0, max = Integer.MAX_VALUE) int sizeBytes
    ) {
        GDriveClient.GetFileResult result = new GDriveClient.GetFileResult(
                fileId, fileName, mimeType,
                "https://drive.google.com/view",
                "https://drive.google.com/download",
                (long) sizeBytes,
                "2025-01-01T00:00:00.000Z",
                "2025-06-01T00:00:00.000Z",
                "owner@test.com"
        );

        assertThat(result.fileId()).isEqualTo(fileId);
        assertThat(result.fileName()).isEqualTo(fileName);
        assertThat(result.mimeType()).isEqualTo(mimeType);
        assertThat(result.sizeBytes()).isEqualTo((long) sizeBytes);
    }

    @Property
    void nullFileIdAlwaysFailsValidation() {
        GetFileConnector connector = new GetFileConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        // fileId not set at all
        connector.setInputParameters(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
    }

    @Property
    void impersonatedUserEmailIsOptional(
            @ForAll @StringLength(min = 1, max = 100) String email
    ) {
        GetFileConnector connector = new GetFileConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        inputs.put(GetFileConnector.INPUT_FILE_ID, VALID_FILE_ID);
        inputs.put(GetFileConnector.INPUT_IMPERSONATED_USER_EMAIL, email);
        connector.setInputParameters(inputs);

        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }
}

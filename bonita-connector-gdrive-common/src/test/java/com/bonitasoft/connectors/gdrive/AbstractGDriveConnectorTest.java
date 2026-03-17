package com.bonitasoft.connectors.gdrive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.api.services.drive.Drive;

/**
 * Unit tests for {@link AbstractGDriveConnector}.
 * Uses a concrete test subclass to exercise all base-class logic.
 */
@DisplayName("AbstractGDriveConnector")
@ExtendWith(MockitoExtension.class)
class AbstractGDriveConnectorTest {

    @Mock
    private GDriveClient mockClient;

    @Mock
    private Drive mockDriveService;

    private TestableConnector connector;

    /**
     * Concrete subclass for testing the abstract base class.
     */
    static class TestableConnector extends AbstractGDriveConnector {

        private boolean executeOperationCalled = false;
        private GDriveException executeOperationException;
        private RuntimeException executeOperationRuntimeException;
        private final List<String> operationValidationErrors;

        TestableConnector() {
            this(List.of());
        }

        TestableConnector(List<String> operationValidationErrors) {
            this.operationValidationErrors = operationValidationErrors;
        }

        @Override
        protected String getConnectorName() {
            return "TestConnector";
        }

        @Override
        protected void validateOperationInputs(List<String> errors) {
            errors.addAll(operationValidationErrors);
        }

        @Override
        protected void executeOperation(Drive driveService) throws GDriveException {
            executeOperationCalled = true;
            if (executeOperationException != null) {
                throw executeOperationException;
            }
            if (executeOperationRuntimeException != null) {
                throw executeOperationRuntimeException;
            }
        }

        /**
         * Expose executeBusinessLogic for direct testing.
         */
        public void callExecuteBusinessLogic() throws ConnectorException {
            executeBusinessLogic();
        }
    }

    @BeforeEach
    void setUp() {
        connector = new TestableConnector();
    }

    // -----------------------------------------------------------------------
    // Helper: set inputs on connector
    // -----------------------------------------------------------------------

    private void setInputs(Map<String, Object> inputs) {
        connector.setInputParameters(inputs);
    }

    private Map<String, Object> validServiceAccountInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(AbstractGDriveConnector.INPUT_SERVICE_ACCOUNT_JSON, "{ \"type\": \"service_account\" }");
        inputs.put(AbstractGDriveConnector.INPUT_APPLICATION_NAME, "TestApp");
        return inputs;
    }

    private Map<String, Object> validOAuthInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(AbstractGDriveConnector.INPUT_CLIENT_ID, "client-id-123");
        inputs.put(AbstractGDriveConnector.INPUT_CLIENT_SECRET, "client-secret-456");
        inputs.put(AbstractGDriveConnector.INPUT_REFRESH_TOKEN, "refresh-token-789");
        inputs.put(AbstractGDriveConnector.INPUT_APPLICATION_NAME, "TestApp");
        return inputs;
    }

    // =======================================================================
    // Phase 1: VALIDATE
    // =======================================================================

    @Nested
    @DisplayName("validateInputParameters")
    class ValidateInputParameters {

        @Test
        @DisplayName("should pass with valid service account JSON")
        void should_pass_when_valid_service_account() throws ConnectorValidationException {
            setInputs(validServiceAccountInputs());

            connector.validateInputParameters();
            // No exception = pass
        }

        @Test
        @DisplayName("should pass with valid OAuth credentials")
        void should_pass_when_valid_oauth() throws ConnectorValidationException {
            setInputs(validOAuthInputs());

            connector.validateInputParameters();
        }

        @Test
        @DisplayName("should fail when no auth provided")
        void should_fail_when_no_auth() {
            setInputs(new HashMap<>());

            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should fail when service account JSON is a file path")
        void should_fail_when_service_account_is_file_path() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(AbstractGDriveConnector.INPUT_SERVICE_ACCOUNT_JSON, "/path/to/key.json");
            setInputs(inputs);

            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("must contain the actual JSON content");
        }

        @Test
        @DisplayName("should fail when only partial OAuth provided")
        void should_fail_when_partial_oauth() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(AbstractGDriveConnector.INPUT_CLIENT_ID, "client-id");
            // missing clientSecret and refreshToken
            setInputs(inputs);

            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should fail when connectTimeout is negative")
        void should_fail_when_negative_connect_timeout() {
            Map<String, Object> inputs = validServiceAccountInputs();
            inputs.put(AbstractGDriveConnector.INPUT_CONNECT_TIMEOUT, -1);
            setInputs(inputs);

            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("connectTimeout must be a positive number");
        }

        @Test
        @DisplayName("should fail when readTimeout is negative")
        void should_fail_when_negative_read_timeout() {
            Map<String, Object> inputs = validServiceAccountInputs();
            inputs.put(AbstractGDriveConnector.INPUT_READ_TIMEOUT, -1);
            setInputs(inputs);

            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("readTimeout must be a positive number");
        }

        @Test
        @DisplayName("should pass when timeouts are positive")
        void should_pass_when_timeouts_positive() throws ConnectorValidationException {
            Map<String, Object> inputs = validServiceAccountInputs();
            inputs.put(AbstractGDriveConnector.INPUT_CONNECT_TIMEOUT, 5000);
            inputs.put(AbstractGDriveConnector.INPUT_READ_TIMEOUT, 10000);
            setInputs(inputs);

            connector.validateInputParameters();
        }

        @Test
        @DisplayName("should pass when timeouts are null")
        void should_pass_when_timeouts_null() throws ConnectorValidationException {
            Map<String, Object> inputs = validServiceAccountInputs();
            // No timeout entries -> null
            setInputs(inputs);

            connector.validateInputParameters();
        }

        @Test
        @DisplayName("should collect operation validation errors")
        void should_collect_operation_validation_errors() {
            connector = new TestableConnector(List.of("operationField is required"));
            Map<String, Object> inputs = validServiceAccountInputs();
            setInputs(inputs);

            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("operationField is required");
        }

        @Test
        @DisplayName("should collect multiple errors at once")
        void should_collect_multiple_errors() {
            connector = new TestableConnector(List.of("error1", "error2"));
            // No auth + operation errors
            setInputs(new HashMap<>());

            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class);
        }
    }

    @Nested
    @DisplayName("validateAuthenticationInputs")
    class ValidateAuthenticationInputs {

        @Test
        @DisplayName("should accept service account JSON starting with brace")
        void should_accept_json_starting_with_brace() throws ConnectorValidationException {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(AbstractGDriveConnector.INPUT_SERVICE_ACCOUNT_JSON,
                    "  { \"type\": \"service_account\" }"); // leading whitespace OK
            setInputs(inputs);

            connector.validateInputParameters();
        }

        @Test
        @DisplayName("should reject blank service account JSON")
        void should_reject_blank_service_account_json() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(AbstractGDriveConnector.INPUT_SERVICE_ACCOUNT_JSON, "   ");
            setInputs(inputs);

            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should accept OAuth when all three fields present")
        void should_accept_oauth_when_all_fields_present() throws ConnectorValidationException {
            setInputs(validOAuthInputs());

            connector.validateInputParameters();
        }

        @Test
        @DisplayName("should reject OAuth when clientSecret blank")
        void should_reject_oauth_when_client_secret_blank() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(AbstractGDriveConnector.INPUT_CLIENT_ID, "id");
            inputs.put(AbstractGDriveConnector.INPUT_CLIENT_SECRET, "  ");
            inputs.put(AbstractGDriveConnector.INPUT_REFRESH_TOKEN, "token");
            setInputs(inputs);

            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should reject OAuth when refreshToken blank")
        void should_reject_oauth_when_refresh_token_blank() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(AbstractGDriveConnector.INPUT_CLIENT_ID, "id");
            inputs.put(AbstractGDriveConnector.INPUT_CLIENT_SECRET, "secret");
            inputs.put(AbstractGDriveConnector.INPUT_REFRESH_TOKEN, "");
            setInputs(inputs);

            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should reject service account that looks like Windows file path")
        void should_reject_windows_file_path() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(AbstractGDriveConnector.INPUT_SERVICE_ACCOUNT_JSON, "C:\\keys\\sa.json");
            setInputs(inputs);

            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("must contain the actual JSON content");
        }
    }

    // =======================================================================
    // Phase 2: CONNECT
    // =======================================================================

    @Nested
    @DisplayName("connect")
    class Connect {

        // Note: connect() creates a real GDriveClient which tries to connect
        // to Google. We test the error-wrapping path by using a subclass that
        // overrides buildConfiguration to throw.

        @Test
        @DisplayName("should wrap GDriveException in ConnectorException")
        void should_wrap_gdrive_exception_in_connector_exception() {
            // Use a connector that throws during buildConfiguration
            var failingConnector = new TestableConnector() {
                @Override
                protected GDriveConfiguration buildConfiguration() {
                    throw new GDriveException.ValidationException("Bad auth");
                }
            };

            assertThatThrownBy(failingConnector::connect)
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("Failed to connect to Google Drive")
                    .hasCauseInstanceOf(GDriveException.class);
        }
    }

    // =======================================================================
    // Phase 3: EXECUTE
    // =======================================================================

    @Nested
    @DisplayName("executeBusinessLogic")
    class ExecuteBusinessLogic {

        @BeforeEach
        void setUpClient() {
            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            connector.setClient(mockClient);
        }

        @Test
        @DisplayName("should call executeOperation and set success outputs")
        void should_succeed_when_operation_succeeds() throws ConnectorException {
            setInputs(validServiceAccountInputs());

            connector.callExecuteBusinessLogic();

            assertThat(connector.executeOperationCalled).isTrue();
            assertThat(connector.getOutputs()).containsEntry(AbstractGDriveConnector.OUTPUT_SUCCESS, true);
            assertThat(connector.getOutputs()).containsEntry(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE, "");
        }

        @Test
        @DisplayName("should wrap GDriveException and set error outputs")
        void should_wrap_gdrive_exception_when_operation_fails() {
            connector.executeOperationException = new GDriveException("API error");
            setInputs(validServiceAccountInputs());

            assertThatThrownBy(() -> connector.callExecuteBusinessLogic())
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("API error")
                    .hasCauseInstanceOf(GDriveException.class);

            assertThat(connector.getOutputs()).containsEntry(AbstractGDriveConnector.OUTPUT_SUCCESS, false);
            assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                    .asString().contains("API error");
        }

        @Test
        @DisplayName("should wrap unexpected RuntimeException and set error outputs")
        void should_wrap_unexpected_exception_when_operation_fails() {
            connector.executeOperationRuntimeException = new IllegalStateException("Unexpected boom");
            setInputs(validServiceAccountInputs());

            assertThatThrownBy(() -> connector.callExecuteBusinessLogic())
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("Unexpected error")
                    .hasCauseInstanceOf(IllegalStateException.class);

            assertThat(connector.getOutputs()).containsEntry(AbstractGDriveConnector.OUTPUT_SUCCESS, false);
            assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                    .asString().contains("Unexpected error");
        }
    }

    // =======================================================================
    // Phase 4: DISCONNECT
    // =======================================================================

    @Nested
    @DisplayName("disconnect")
    class Disconnect {

        @Test
        @DisplayName("should close client when present")
        void should_close_client_when_present() throws ConnectorException {
            connector.setClient(mockClient);

            connector.disconnect();

            verify(mockClient).close();
        }

        @Test
        @DisplayName("should not throw when client is null")
        void should_not_throw_when_client_null() throws ConnectorException {
            // client is null by default
            connector.disconnect();
            // No exception = pass
        }

        @Test
        @DisplayName("should swallow exception from client close")
        void should_swallow_close_exception() throws ConnectorException {
            doThrow(new RuntimeException("close error")).when(mockClient).close();
            connector.setClient(mockClient);

            connector.disconnect(); // should not throw

            verify(mockClient).close();
        }
    }

    // =======================================================================
    // Output helpers
    // =======================================================================

    @Nested
    @DisplayName("setSuccessOutputs / setErrorOutputs")
    class OutputHelpers {

        @Test
        @DisplayName("should set success=true and empty error message")
        void should_set_success_outputs() {
            connector.setSuccessOutputs();

            assertThat(connector.getOutputs())
                    .containsEntry(AbstractGDriveConnector.OUTPUT_SUCCESS, true)
                    .containsEntry(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE, "");
        }

        @Test
        @DisplayName("should set success=false and error message")
        void should_set_error_outputs() {
            connector.setErrorOutputs("Something went wrong");

            assertThat(connector.getOutputs())
                    .containsEntry(AbstractGDriveConnector.OUTPUT_SUCCESS, false)
                    .containsEntry(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE, "Something went wrong");
        }

        @Test
        @DisplayName("should truncate long error messages")
        void should_truncate_long_error_message() {
            String longMessage = "x".repeat(2000);

            connector.setErrorOutputs(longMessage);

            String result = (String) connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE);
            assertThat(result.length()).isLessThanOrEqualTo(GDriveException.MAX_ERROR_MESSAGE_LENGTH);
            assertThat(result).endsWith("...");
        }
    }

    // =======================================================================
    // Input helpers
    // =======================================================================

    @Nested
    @DisplayName("getStringInput")
    class GetStringInput {

        @Test
        @DisplayName("should return string value")
        void should_return_string_when_value_is_string() {
            setInputs(Map.of("key", "hello"));
            assertThat(connector.getStringInput("key")).isEqualTo("hello");
        }

        @Test
        @DisplayName("should return null when missing")
        void should_return_null_when_missing() {
            setInputs(Map.of());
            assertThat(connector.getStringInput("missing")).isNull();
        }

        @Test
        @DisplayName("should convert non-string to string via toString")
        void should_convert_non_string_to_string() {
            setInputs(Map.of("key", 42));
            assertThat(connector.getStringInput("key")).isEqualTo("42");
        }
    }

    @Nested
    @DisplayName("getStringInputOrDefault")
    class GetStringInputOrDefault {

        @Test
        @DisplayName("should return value when present and not blank")
        void should_return_value_when_present() {
            setInputs(Map.of("key", "value"));
            assertThat(connector.getStringInputOrDefault("key", "default")).isEqualTo("value");
        }

        @Test
        @DisplayName("should return default when missing")
        void should_return_default_when_missing() {
            setInputs(Map.of());
            assertThat(connector.getStringInputOrDefault("key", "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("should return default when blank")
        void should_return_default_when_blank() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("key", "   ");
            setInputs(inputs);
            assertThat(connector.getStringInputOrDefault("key", "default")).isEqualTo("default");
        }
    }

    @Nested
    @DisplayName("getIntInput")
    class GetIntInput {

        @Test
        @DisplayName("should return null when missing")
        void should_return_null_when_missing() {
            setInputs(Map.of());
            assertThat(connector.getIntInput("missing")).isNull();
        }

        @Test
        @DisplayName("should return Integer directly")
        void should_return_integer_directly() {
            setInputs(Map.of("key", 42));
            assertThat(connector.getIntInput("key")).isEqualTo(42);
        }

        @Test
        @DisplayName("should convert Number to int")
        void should_convert_number_to_int() {
            setInputs(Map.of("key", 42L));
            assertThat(connector.getIntInput("key")).isEqualTo(42);
        }

        @Test
        @DisplayName("should convert Double to int")
        void should_convert_double_to_int() {
            setInputs(Map.of("key", 42.7));
            assertThat(connector.getIntInput("key")).isEqualTo(42);
        }

        @Test
        @DisplayName("should parse string to int")
        void should_parse_string_to_int() {
            setInputs(Map.of("key", "123"));
            assertThat(connector.getIntInput("key")).isEqualTo(123);
        }

        @Test
        @DisplayName("should return null for unparseable string")
        void should_return_null_for_unparseable_string() {
            setInputs(Map.of("key", "not-a-number"));
            assertThat(connector.getIntInput("key")).isNull();
        }
    }

    @Nested
    @DisplayName("getIntInputOrDefault")
    class GetIntInputOrDefault {

        @Test
        @DisplayName("should return value when present")
        void should_return_value_when_present() {
            setInputs(Map.of("key", 10));
            assertThat(connector.getIntInputOrDefault("key", 99)).isEqualTo(10);
        }

        @Test
        @DisplayName("should return default when missing")
        void should_return_default_when_missing() {
            setInputs(Map.of());
            assertThat(connector.getIntInputOrDefault("key", 99)).isEqualTo(99);
        }

        @Test
        @DisplayName("should return default for unparseable value")
        void should_return_default_for_unparseable() {
            setInputs(Map.of("key", "abc"));
            assertThat(connector.getIntInputOrDefault("key", 99)).isEqualTo(99);
        }
    }

    @Nested
    @DisplayName("getLongInput")
    class GetLongInput {

        @Test
        @DisplayName("should return null when missing")
        void should_return_null_when_missing() {
            setInputs(Map.of());
            assertThat(connector.getLongInput("missing")).isNull();
        }

        @Test
        @DisplayName("should return Long directly")
        void should_return_long_directly() {
            setInputs(Map.of("key", 100L));
            assertThat(connector.getLongInput("key")).isEqualTo(100L);
        }

        @Test
        @DisplayName("should convert Number to long")
        void should_convert_number_to_long() {
            setInputs(Map.of("key", 42));
            assertThat(connector.getLongInput("key")).isEqualTo(42L);
        }

        @Test
        @DisplayName("should parse string to long")
        void should_parse_string_to_long() {
            setInputs(Map.of("key", "9999999999"));
            assertThat(connector.getLongInput("key")).isEqualTo(9_999_999_999L);
        }

        @Test
        @DisplayName("should return null for unparseable string")
        void should_return_null_for_unparseable_string() {
            setInputs(Map.of("key", "not-a-long"));
            assertThat(connector.getLongInput("key")).isNull();
        }
    }

    @Nested
    @DisplayName("getBooleanInput")
    class GetBooleanInput {

        @Test
        @DisplayName("should return null when missing")
        void should_return_null_when_missing() {
            setInputs(Map.of());
            assertThat(connector.getBooleanInput("missing")).isNull();
        }

        @Test
        @DisplayName("should return Boolean directly")
        void should_return_boolean_directly() {
            setInputs(Map.of("key", true));
            assertThat(connector.getBooleanInput("key")).isTrue();
        }

        @Test
        @DisplayName("should parse string true")
        void should_parse_string_true() {
            setInputs(Map.of("key", "true"));
            assertThat(connector.getBooleanInput("key")).isTrue();
        }

        @Test
        @DisplayName("should parse string false")
        void should_parse_string_false() {
            setInputs(Map.of("key", "false"));
            assertThat(connector.getBooleanInput("key")).isFalse();
        }

        @Test
        @DisplayName("should return false for non-boolean string")
        void should_return_false_for_non_boolean_string() {
            setInputs(Map.of("key", "yes"));
            assertThat(connector.getBooleanInput("key")).isFalse();
        }
    }

    @Nested
    @DisplayName("getBooleanInputOrDefault")
    class GetBooleanInputOrDefault {

        @Test
        @DisplayName("should return value when present")
        void should_return_value_when_present() {
            setInputs(Map.of("key", false));
            assertThat(connector.getBooleanInputOrDefault("key", true)).isFalse();
        }

        @Test
        @DisplayName("should return default when missing")
        void should_return_default_when_missing() {
            setInputs(Map.of());
            assertThat(connector.getBooleanInputOrDefault("key", true)).isTrue();
        }
    }

    @Nested
    @DisplayName("getListInput")
    class GetListInput {

        @Test
        @DisplayName("should return null when missing")
        void should_return_null_when_missing() {
            setInputs(Map.of());
            assertThat(connector.getListInput("missing")).isNull();
        }

        @Test
        @DisplayName("should return list directly")
        void should_return_list_directly() {
            setInputs(Map.of("key", List.of("a", "b", "c")));
            assertThat(connector.getListInput("key")).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("should return null for non-list value")
        void should_return_null_for_non_list() {
            setInputs(Map.of("key", "not-a-list"));
            assertThat(connector.getListInput("key")).isNull();
        }
    }

    @Nested
    @DisplayName("getMapInput")
    class GetMapInput {

        @Test
        @DisplayName("should return null when missing")
        void should_return_null_when_missing() {
            setInputs(Map.of());
            assertThat(connector.getMapInput("missing")).isNull();
        }

        @Test
        @DisplayName("should return map directly")
        void should_return_map_directly() {
            setInputs(Map.of("key", Map.of("a", "1", "b", "2")));
            assertThat(connector.getMapInput("key")).containsEntry("a", "1").containsEntry("b", "2");
        }

        @Test
        @DisplayName("should return null for non-map value")
        void should_return_null_for_non_map() {
            setInputs(Map.of("key", "not-a-map"));
            assertThat(connector.getMapInput("key")).isNull();
        }
    }

    // =======================================================================
    // buildConfiguration
    // =======================================================================

    @Nested
    @DisplayName("buildConfiguration")
    class BuildConfiguration {

        @Test
        @DisplayName("should build config from inputs with defaults")
        void should_build_config_with_defaults() {
            Map<String, Object> inputs = validServiceAccountInputs();
            setInputs(inputs);

            GDriveConfiguration config = connector.buildConfiguration();

            assertThat(config.serviceAccountJson()).isEqualTo("{ \"type\": \"service_account\" }");
            assertThat(config.applicationName()).isEqualTo("TestApp");
            assertThat(config.connectTimeout()).isEqualTo(30_000);
            assertThat(config.readTimeout()).isEqualTo(60_000);
        }

        @Test
        @DisplayName("should use custom timeouts from inputs")
        void should_use_custom_timeouts() {
            Map<String, Object> inputs = validServiceAccountInputs();
            inputs.put(AbstractGDriveConnector.INPUT_CONNECT_TIMEOUT, 5000);
            inputs.put(AbstractGDriveConnector.INPUT_READ_TIMEOUT, 10000);
            setInputs(inputs);

            GDriveConfiguration config = connector.buildConfiguration();

            assertThat(config.connectTimeout()).isEqualTo(5000);
            assertThat(config.readTimeout()).isEqualTo(10000);
        }

        @Test
        @DisplayName("should pass OAuth fields to config")
        void should_pass_oauth_fields() {
            Map<String, Object> inputs = validOAuthInputs();
            setInputs(inputs);

            GDriveConfiguration config = connector.buildConfiguration();

            assertThat(config.clientId()).isEqualTo("client-id-123");
            assertThat(config.clientSecret()).isEqualTo("client-secret-456");
            assertThat(config.refreshToken()).isEqualTo("refresh-token-789");
        }

        @Test
        @DisplayName("should pass impersonateUser to config")
        void should_pass_impersonate_user() {
            Map<String, Object> inputs = validServiceAccountInputs();
            inputs.put(AbstractGDriveConnector.INPUT_IMPERSONATE_USER, "user@example.com");
            setInputs(inputs);

            GDriveConfiguration config = connector.buildConfiguration();

            assertThat(config.impersonateUser()).isEqualTo("user@example.com");
        }
    }

    // =======================================================================
    // setClient (testing hook)
    // =======================================================================

    @Nested
    @DisplayName("setClient")
    class SetClient {

        @Test
        @DisplayName("should inject client for testing")
        void should_inject_client() {
            connector.setClient(mockClient);

            // Verify via disconnect that client was set
            assertThat(connector.client).isSameAs(mockClient);
        }
    }

    // =======================================================================
    // getOutputs
    // =======================================================================

    @Nested
    @DisplayName("getOutputs")
    class GetOutputs {

        @Test
        @DisplayName("should expose output parameters map")
        void should_expose_output_parameters() {
            connector.setSuccessOutputs();

            Map<String, Object> outputs = connector.getOutputs();

            assertThat(outputs).containsEntry(AbstractGDriveConnector.OUTPUT_SUCCESS, true);
        }
    }
}

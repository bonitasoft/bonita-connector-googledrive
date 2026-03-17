package com.bonitasoft.connectors.gdrive;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.services.drive.Drive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.objenesis.ObjenesisStd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GDriveClient}.
 * <p>
 * Uses Objenesis (bundled with Mockito) to create instances without calling the
 * constructor (which requires network access to Google APIs). Private methods
 * are tested via reflection.
 */
@DisplayName("GDriveClient")
@ExtendWith(MockitoExtension.class)
class GDriveClientTest {

    // ---------------------------------------------------------------
    // Test infrastructure
    // ---------------------------------------------------------------

    /**
     * Creates a GDriveClient instance bypassing the real constructor
     * (which calls GoogleNetHttpTransport). Fields are set via reflection.
     */
    private static GDriveClient createTestableClient(GDriveConfiguration config) throws Exception {
        var objenesis = new ObjenesisStd();
        GDriveClient client = objenesis.newInstance(GDriveClient.class);
        setField(client, "configuration", config);
        setField(client, "driveService", mock(Drive.class));
        setField(client, "httpTransport", mock(HttpTransport.class));
        // Lombok @Slf4j generates a static field; Objenesis skips <clinit> so it's already set.
        return client;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        // Remove final modifier for static fields
        field.set(null, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokePrivate(Object target, String methodName, Class<?>[] paramTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        try {
            return (T) method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException(cause);
        }
    }

    // ---------------------------------------------------------------
    // Config factories
    // ---------------------------------------------------------------

    private static GDriveConfiguration serviceAccountConfig() {
        return new GDriveConfiguration(
                "{\"type\": \"service_account\", \"project_id\": \"test\"}",
                null, null, null, null,
                null, null, 0, 0);
    }

    private static GDriveConfiguration oauthConfig() {
        return new GDriveConfiguration(
                null, null,
                "client-id", "client-secret", "refresh-token",
                null, null, 0, 0);
    }

    private static GDriveConfiguration noAuthConfig() {
        return new GDriveConfiguration(
                null, null, null, null, null,
                "TestApp", List.of("https://www.googleapis.com/auth/drive"),
                5000, 10000);
    }

    // ---------------------------------------------------------------
    // Helper: build GoogleJsonResponseException for wrapException tests
    // ---------------------------------------------------------------

    private static GoogleJsonResponseException createGoogleJsonResponseException(
            int statusCode, String reason) {
        GoogleJsonError jsonError = new GoogleJsonError();
        jsonError.setCode(statusCode);
        jsonError.setMessage("Test error");

        GoogleJsonError.ErrorInfo errorInfo = new GoogleJsonError.ErrorInfo();
        errorInfo.setReason(reason);
        jsonError.setErrors(List.of(errorInfo));

        HttpHeaders headers = new HttpHeaders();
        var builder = new HttpResponseException.Builder(statusCode, "Test", headers);
        return new GoogleJsonResponseException(builder, jsonError);
    }

    // ===============================================================
    // Nested test groups
    // ===============================================================

    @Nested
    @DisplayName("executeWithRetry")
    class ExecuteWithRetry {

        private GDriveClient client;

        @BeforeEach
        void setUp() throws Exception {
            client = createTestableClient(serviceAccountConfig());
        }

        @Test
        @DisplayName("should return result when operation succeeds on first attempt")
        void should_return_result_when_operation_succeeds_first_attempt() {
            String result = client.executeWithRetry(() -> "success", "test operation");
            assertThat(result).isEqualTo("success");
        }

        @Test
        @Timeout(10)
        @DisplayName("should retry and succeed when retryable error then success")
        void should_retry_and_succeed_when_retryable_error_then_success() {
            AtomicInteger attempts = new AtomicInteger(0);
            Callable<String> operation = () -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new GDriveException.ExecutionException("Rate limited", 429);
                }
                return "success after retry";
            };

            String result = client.executeWithRetry(operation, "retry test");
            assertThat(result).isEqualTo("success after retry");
            assertThat(attempts.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw immediately when non-retryable error occurs")
        void should_throw_immediately_when_non_retryable_error() {
            AtomicInteger attempts = new AtomicInteger(0);
            Callable<String> operation = () -> {
                attempts.incrementAndGet();
                throw new GDriveException.ExecutionException("Not found", 404);
            };

            assertThatThrownBy(() -> client.executeWithRetry(operation, "non-retryable test"))
                    .isInstanceOf(GDriveException.class)
                    .hasMessageContaining("Not found");
            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        @Timeout(10)
        @DisplayName("should retry on 5xx server errors")
        void should_retry_when_5xx_server_error() {
            AtomicInteger attempts = new AtomicInteger(0);
            Callable<String> operation = () -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new GDriveException.ExecutionException("Internal error", 503);
                }
                return "recovered";
            };

            String result = client.executeWithRetry(operation, "5xx test");
            assertThat(result).isEqualTo("recovered");
            assertThat(attempts.get()).isEqualTo(2);
        }

        @Test
        @Timeout(10)
        @DisplayName("should retry on 429 rate limit errors")
        void should_retry_when_429_rate_limit() {
            AtomicInteger attempts = new AtomicInteger(0);
            Callable<String> operation = () -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new GDriveException.ExecutionException("Too many requests", 429);
                }
                return "recovered";
            };

            String result = client.executeWithRetry(operation, "429 test");
            assertThat(result).isEqualTo("recovered");
        }

        @Test
        @DisplayName("should wrap unexpected exceptions as GDriveException")
        void should_wrap_unexpected_exceptions_when_non_gdrive_exception() {
            Callable<String> operation = () -> {
                throw new IllegalStateException("unexpected");
            };

            // IllegalStateException is not retryable, so it should throw immediately
            assertThatThrownBy(() -> client.executeWithRetry(operation, "wrap test"))
                    .isInstanceOf(GDriveException.class)
                    .hasMessageContaining("Unexpected error")
                    .hasCauseInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should pass through GDriveException subclass without re-wrapping")
        void should_passthrough_when_gdrive_exception_thrown() {
            Callable<String> operation = () -> {
                throw new GDriveException.ValidationException("bad input");
            };

            assertThatThrownBy(() -> client.executeWithRetry(operation, "passthrough test"))
                    .isInstanceOf(GDriveException.ValidationException.class)
                    .hasMessage("bad input");
        }

        @Test
        @Timeout(60)
        @DisplayName("should throw after max retries exceeded when retryable errors persist")
        void should_throw_after_max_retries_when_all_attempts_fail() {
            AtomicInteger attempts = new AtomicInteger(0);
            // Succeed on attempt 7+ (MAX_RETRIES is 5, so initial + 5 retries = 6 total)
            // This means all 6 attempts fail, triggering the "max retries" path
            Callable<String> operation = () -> {
                attempts.incrementAndGet();
                throw new GDriveException.ExecutionException("Server error", 500);
            };

            assertThatThrownBy(() -> client.executeWithRetry(operation, "max retry test"))
                    .isInstanceOf(GDriveException.class)
                    .hasMessageContaining("Max retries")
                    .hasMessageContaining("max retry test")
                    .hasCauseInstanceOf(GDriveException.ExecutionException.class);
            // initial attempt (0) + 5 retries = 6 total calls
            assertThat(attempts.get()).isEqualTo(6);
        }

        @Test
        @Timeout(10)
        @DisplayName("should succeed after multiple retries before max")
        void should_succeed_when_retries_before_max() {
            AtomicInteger attempts = new AtomicInteger(0);
            Callable<String> operation = () -> {
                if (attempts.incrementAndGet() <= 3) {
                    throw new GDriveException.ExecutionException("Server error", 500);
                }
                return "finally";
            };

            String result = client.executeWithRetry(operation, "multi-retry test");
            assertThat(result).isEqualTo("finally");
            assertThat(attempts.get()).isEqualTo(4);
        }

    }

    @Nested
    @DisplayName("buildCredentials")
    class BuildCredentials {

        @Test
        @DisplayName("should throw ValidationException when no auth configured")
        void should_throw_validation_when_no_auth_configured() throws Exception {
            GDriveClient client = createTestableClient(noAuthConfig());

            assertThatThrownBy(() -> invokePrivate(client, "buildCredentials", new Class[0]))
                    .isInstanceOf(GDriveException.ValidationException.class)
                    .hasMessageContaining("No valid authentication configured");
        }

        @Test
        @DisplayName("should build OAuth credentials when OAuth config provided")
        void should_build_oauth_credentials_when_oauth_config() throws Exception {
            GDriveClient client = createTestableClient(oauthConfig());

            Object credentials = invokePrivate(client, "buildCredentials", new Class[0]);
            assertThat(credentials).isNotNull();
            assertThat(credentials.getClass().getSimpleName()).contains("UserCredentials");
        }

        @Test
        @DisplayName("should attempt service account path when SA config provided")
        void should_attempt_service_account_when_sa_config() throws Exception {
            // SA JSON is syntactically valid JSON but not a real service account key,
            // so Google's library will throw IOException (not ValidationException).
            GDriveClient client = createTestableClient(serviceAccountConfig());

            assertThatThrownBy(() -> invokePrivate(client, "buildCredentials", new Class[0]))
                    .isInstanceOf(Exception.class)
                    // Must NOT be ValidationException — that would mean it took the wrong branch
                    .isNotInstanceOf(GDriveException.ValidationException.class);
        }

        @Test
        @DisplayName("should return SA credentials through buildCredentials when mocked")
        void should_return_sa_credentials_through_build_credentials() throws Exception {
            GDriveClient client = createTestableClient(serviceAccountConfig());

            com.google.auth.oauth2.GoogleCredentials mockCreds =
                    mock(com.google.auth.oauth2.GoogleCredentials.class);
            com.google.auth.oauth2.GoogleCredentials scopedCreds =
                    mock(com.google.auth.oauth2.GoogleCredentials.class);
            when(mockCreds.createScoped(serviceAccountConfig().scopes())).thenReturn(scopedCreds);

            try (var mockedStatic = org.mockito.Mockito.mockStatic(
                    com.google.auth.oauth2.GoogleCredentials.class)) {
                mockedStatic.when(() -> com.google.auth.oauth2.GoogleCredentials.fromStream(
                        org.mockito.ArgumentMatchers.any(java.io.InputStream.class)))
                        .thenReturn(mockCreds);

                Object result = invokePrivate(client, "buildCredentials", new Class[0]);
                assertThat(result).isSameAs(scopedCreds);
            }
        }
    }

    @Nested
    @DisplayName("resolveServiceAccountJson")
    class ResolveServiceAccountJson {

        @Test
        @DisplayName("should return JSON from configuration when provided directly")
        void should_return_json_when_provided_in_config() throws Exception {
            String expectedJson = "{\"type\": \"service_account\"}";
            var config = new GDriveConfiguration(
                    expectedJson, null, null, null, null, null, null, 0, 0);
            GDriveClient client = createTestableClient(config);

            String result = invokePrivate(client, "resolveServiceAccountJson", new Class[0]);
            assertThat(result).isEqualTo(expectedJson);
        }

        @Test
        @DisplayName("should throw ValidationException when no JSON and no fallbacks")
        void should_throw_when_no_json_and_no_fallbacks() throws Exception {
            GDriveClient client = createTestableClient(noAuthConfig());

            String previousProp = System.getProperty("google.application.credentials");
            try {
                System.clearProperty("google.application.credentials");
                assertThatThrownBy(() -> invokePrivate(client, "resolveServiceAccountJson", new Class[0]))
                        .isInstanceOf(GDriveException.ValidationException.class)
                        .hasMessageContaining("Service Account JSON not provided");
            } finally {
                if (previousProp != null) {
                    System.setProperty("google.application.credentials", previousProp);
                }
            }
        }

        @Test
        @DisplayName("should throw ConnectionException when sys property file does not exist")
        void should_throw_connection_when_sys_property_file_missing() throws Exception {
            GDriveClient client = createTestableClient(noAuthConfig());

            String previousProp = System.getProperty("google.application.credentials");
            try {
                System.setProperty("google.application.credentials", "/nonexistent/path/credentials.json");
                assertThatThrownBy(() -> invokePrivate(client, "resolveServiceAccountJson", new Class[0]))
                        .isInstanceOf(GDriveException.ConnectionException.class)
                        .hasMessageContaining("google.application.credentials");
            } finally {
                System.clearProperty("google.application.credentials");
                if (previousProp != null) {
                    System.setProperty("google.application.credentials", previousProp);
                }
            }
        }

        @Test
        @DisplayName("should prioritize config JSON over environment fallbacks")
        void should_prioritize_config_over_fallbacks() throws Exception {
            String configJson = "{\"type\": \"service_account\", \"source\": \"config\"}";
            var config = new GDriveConfiguration(
                    configJson, null, null, null, null, null, null, 0, 0);
            GDriveClient client = createTestableClient(config);

            String previousProp = System.getProperty("google.application.credentials");
            try {
                System.setProperty("google.application.credentials", "/some/path");
                String result = invokePrivate(client, "resolveServiceAccountJson", new Class[0]);
                assertThat(result).isEqualTo(configJson);
            } finally {
                System.clearProperty("google.application.credentials");
                if (previousProp != null) {
                    System.setProperty("google.application.credentials", previousProp);
                }
            }
        }

        @Test
        @DisplayName("should treat blank string as missing")
        void should_treat_blank_as_missing() throws Exception {
            var config = new GDriveConfiguration(
                    "   ", null, null, null, null,
                    "TestApp", List.of("https://www.googleapis.com/auth/drive"), 5000, 10000);
            GDriveClient client = createTestableClient(config);

            String previousProp = System.getProperty("google.application.credentials");
            try {
                System.clearProperty("google.application.credentials");
                assertThatThrownBy(() -> invokePrivate(client, "resolveServiceAccountJson", new Class[0]))
                        .isInstanceOf(GDriveException.ValidationException.class);
            } finally {
                if (previousProp != null) {
                    System.setProperty("google.application.credentials", previousProp);
                }
            }
        }

        @Test
        @DisplayName("should read JSON from sys property file when config is null")
        void should_read_from_sys_property_file_when_config_null() throws Exception {
            GDriveClient client = createTestableClient(noAuthConfig());
            String expectedJson = "{\"type\": \"service_account\", \"from\": \"sysprop\"}";

            // Create a temp file with JSON content
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("sa-test-", ".json");
            java.nio.file.Files.writeString(tempFile, expectedJson);

            String previousProp = System.getProperty("google.application.credentials");
            try {
                System.setProperty("google.application.credentials", tempFile.toString());
                String result = invokePrivate(client, "resolveServiceAccountJson", new Class[0]);
                assertThat(result).isEqualTo(expectedJson);
            } finally {
                System.clearProperty("google.application.credentials");
                if (previousProp != null) {
                    System.setProperty("google.application.credentials", previousProp);
                }
                java.nio.file.Files.deleteIfExists(tempFile);
            }
        }

        @Test
        @DisplayName("should treat null as missing")
        void should_treat_null_as_missing() throws Exception {
            GDriveClient client = createTestableClient(noAuthConfig());

            String previousProp = System.getProperty("google.application.credentials");
            try {
                System.clearProperty("google.application.credentials");
                assertThatThrownBy(() -> invokePrivate(client, "resolveServiceAccountJson", new Class[0]))
                        .isInstanceOf(GDriveException.ValidationException.class);
            } finally {
                if (previousProp != null) {
                    System.setProperty("google.application.credentials", previousProp);
                }
            }
        }
    }

    @Nested
    @DisplayName("wrapException")
    class WrapException {

        private GDriveClient client;

        @BeforeEach
        void setUp() throws Exception {
            client = createTestableClient(serviceAccountConfig());
        }

        @Test
        @DisplayName("should return same instance when already GDriveException")
        void should_return_same_when_already_gdrive_exception() throws Exception {
            GDriveException original = new GDriveException("test error");

            GDriveException result = invokePrivate(client, "wrapException",
                    new Class[]{Exception.class}, original);

            assertThat(result).isSameAs(original);
        }

        @Test
        @DisplayName("should return same instance when GDriveException subclass")
        void should_return_same_when_gdrive_subclass() throws Exception {
            var original = new GDriveException.ValidationException("bad");

            GDriveException result = invokePrivate(client, "wrapException",
                    new Class[]{Exception.class}, original);

            assertThat(result).isSameAs(original);
            assertThat(result).isInstanceOf(GDriveException.ValidationException.class);
        }

        @Test
        @DisplayName("should convert GoogleJsonResponseException with retryable status")
        void should_convert_retryable_google_exception() throws Exception {
            GoogleJsonResponseException googleEx =
                    createGoogleJsonResponseException(429, "rateLimitExceeded");

            GDriveException result = invokePrivate(client, "wrapException",
                    new Class[]{Exception.class}, googleEx);

            assertThat(result.getStatusCode()).isEqualTo(429);
            assertThat(result.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should convert GoogleJsonResponseException with non-retryable status")
        void should_convert_non_retryable_google_exception() throws Exception {
            GoogleJsonResponseException googleEx =
                    createGoogleJsonResponseException(404, "notFound");

            GDriveException result = invokePrivate(client, "wrapException",
                    new Class[]{Exception.class}, googleEx);

            assertThat(result.getStatusCode()).isEqualTo(404);
            assertThat(result.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should wrap unknown exception with Unexpected error message")
        void should_wrap_unknown_exception() throws Exception {
            var original = new RuntimeException("something broke");

            GDriveException result = invokePrivate(client, "wrapException",
                    new Class[]{Exception.class}, original);

            assertThat(result.getMessage()).contains("Unexpected error");
            assertThat(result.getMessage()).contains("something broke");
            assertThat(result.getCause()).isSameAs(original);
            assertThat(result.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should wrap IOException as non-retryable")
        void should_wrap_ioexception_as_non_retryable() throws Exception {
            var original = new java.io.IOException("connection reset");

            GDriveException result = invokePrivate(client, "wrapException",
                    new Class[]{Exception.class}, original);

            assertThat(result.getMessage()).contains("Unexpected error");
            assertThat(result.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should convert 500 GoogleJsonResponseException as retryable")
        void should_convert_500_google_exception_as_retryable() throws Exception {
            GoogleJsonResponseException googleEx =
                    createGoogleJsonResponseException(500, "backendError");

            GDriveException result = invokePrivate(client, "wrapException",
                    new Class[]{Exception.class}, googleEx);

            assertThat(result.getStatusCode()).isEqualTo(500);
            assertThat(result.isRetryable()).isTrue();
        }
    }

    @Nested
    @DisplayName("calculateBackoffDelay")
    class CalculateBackoffDelay {

        private GDriveClient client;

        @BeforeEach
        void setUp() throws Exception {
            client = createTestableClient(serviceAccountConfig());
        }

        @Test
        @DisplayName("should return delay around 1000ms for first attempt")
        void should_return_around_1000ms_when_first_attempt() throws Exception {
            long delay = invokePrivate(client, "calculateBackoffDelay",
                    new Class[]{int.class}, 1);
            // Base=1000, jitter up to 20% → [1000, 1200)
            assertThat(delay).isBetween(1000L, 1200L);
        }

        @Test
        @DisplayName("should return delay around 2000ms for second attempt")
        void should_return_around_2000ms_when_second_attempt() throws Exception {
            long delay = invokePrivate(client, "calculateBackoffDelay",
                    new Class[]{int.class}, 2);
            assertThat(delay).isBetween(2000L, 2400L);
        }

        @Test
        @DisplayName("should return delay around 4000ms for third attempt")
        void should_return_around_4000ms_when_third_attempt() throws Exception {
            long delay = invokePrivate(client, "calculateBackoffDelay",
                    new Class[]{int.class}, 3);
            assertThat(delay).isBetween(4000L, 4800L);
        }

        @Test
        @DisplayName("should return delay around 8000ms for fourth attempt")
        void should_return_around_8000ms_when_fourth_attempt() throws Exception {
            long delay = invokePrivate(client, "calculateBackoffDelay",
                    new Class[]{int.class}, 4);
            assertThat(delay).isBetween(8000L, 9600L);
        }

        @Test
        @DisplayName("should cap delay at MAX_DELAY_MS for high attempt numbers")
        void should_cap_delay_when_high_attempt() throws Exception {
            long delay = invokePrivate(client, "calculateBackoffDelay",
                    new Class[]{int.class}, 10);
            // MAX_DELAY_MS=64000, jitter adds up to 20% → max 76800
            assertThat(delay).isBetween(64000L, 76800L);
        }

        @Test
        @DisplayName("should show exponential growth across attempts")
        void should_show_exponential_growth() throws Exception {
            long d1 = invokePrivate(client, "calculateBackoffDelay", new Class[]{int.class}, 1);
            long d2 = invokePrivate(client, "calculateBackoffDelay", new Class[]{int.class}, 2);
            long d3 = invokePrivate(client, "calculateBackoffDelay", new Class[]{int.class}, 3);

            assertThat(d2).isGreaterThan(d1);
            assertThat(d3).isGreaterThan(d2);
        }

        @Test
        @DisplayName("should always return positive delay")
        void should_always_return_positive() throws Exception {
            for (int attempt = 1; attempt <= 10; attempt++) {
                long delay = invokePrivate(client, "calculateBackoffDelay",
                        new Class[]{int.class}, attempt);
                assertThat(delay).isPositive();
            }
        }
    }

    @Nested
    @DisplayName("close")
    class Close {

        @Test
        @DisplayName("should not throw when closing client")
        void should_not_throw_when_closing() throws Exception {
            GDriveClient client = createTestableClient(serviceAccountConfig());
            assertThatCode(client::close).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should be safe to call close multiple times")
        void should_be_safe_when_closed_multiple_times() throws Exception {
            GDriveClient client = createTestableClient(serviceAccountConfig());
            assertThatCode(() -> {
                client.close();
                client.close();
                client.close();
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should throw ValidationException when no auth configured")
        void should_throw_validation_when_no_auth() {
            var config = noAuthConfig();
            assertThatThrownBy(() -> new GDriveClient(config))
                    .isInstanceOf(GDriveException.ValidationException.class)
                    .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should throw ValidationException when SA JSON is a file path")
        void should_throw_validation_when_sa_json_is_filepath() {
            var config = new GDriveConfiguration(
                    "/path/to/credentials.json",
                    null, null, null, null, null, null, 0, 0);

            assertThatThrownBy(() -> new GDriveClient(config))
                    .isInstanceOf(GDriveException.ValidationException.class)
                    .hasMessageContaining("must contain the JSON content");
        }

        @Test
        @DisplayName("should throw ConnectionException when invalid SA JSON provided")
        void should_throw_connection_when_invalid_sa_json() {
            var config = new GDriveConfiguration(
                    "{\"type\": \"service_account\", \"invalid\": true}",
                    null, null, null, null, null, null, 0, 0);

            assertThatThrownBy(() -> new GDriveClient(config))
                    .isInstanceOf(GDriveException.ConnectionException.class)
                    .hasMessageContaining("Failed to connect");
        }

        @Test
        @DisplayName("should succeed with valid OAuth configuration")
        void should_succeed_with_valid_oauth_config() {
            assertThatCode(() -> new GDriveClient(oauthConfig()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should build Drive service with custom application name")
        void should_build_with_custom_app_name() {
            var config = new GDriveConfiguration(
                    null, null,
                    "client-id", "client-secret", "refresh-token",
                    "My-Custom-App", null, 0, 0);

            GDriveClient client = new GDriveClient(config);
            assertThat(client.getDriveService()).isNotNull();
            client.close();
        }
    }

    @Nested
    @DisplayName("getDriveService")
    class GetDriveService {

        @Test
        @DisplayName("should return non-null Drive service after construction")
        void should_return_drive_service() throws Exception {
            GDriveClient client = createTestableClient(serviceAccountConfig());
            assertThat(client.getDriveService()).isNotNull();
        }
    }

    @Nested
    @DisplayName("sleep (private)")
    class SleepMethod {

        @Test
        @DisplayName("should throw GDriveException when thread interrupted during sleep")
        void should_throw_when_interrupted() throws Exception {
            GDriveClient client = createTestableClient(serviceAccountConfig());
            Thread testThread = Thread.currentThread();

            Thread interrupter = new Thread(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) { }
                testThread.interrupt();
            });
            interrupter.start();

            assertThatThrownBy(() -> invokePrivate(client, "sleep",
                    new Class[]{long.class}, 5000L))
                    .isInstanceOf(GDriveException.class)
                    .hasMessageContaining("interrupted");

            // Clear interrupted status
            Thread.interrupted();
            interrupter.join(1000);
        }

        @Test
        @DisplayName("should complete without error for short sleep")
        void should_complete_for_short_sleep() throws Exception {
            GDriveClient client = createTestableClient(serviceAccountConfig());
            assertThatCode(() -> invokePrivate(client, "sleep",
                    new Class[]{long.class}, 1L))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("buildOAuthCredentials (private)")
    class BuildOAuthCredentials {

        @Test
        @DisplayName("should return UserCredentials when OAuth config valid")
        void should_return_user_credentials_when_oauth() throws Exception {
            GDriveClient client = createTestableClient(oauthConfig());

            Object credentials = invokePrivate(client, "buildOAuthCredentials", new Class[0]);
            assertThat(credentials).isNotNull();
            assertThat(credentials.getClass().getName()).contains("UserCredentials");
        }
    }

    @Nested
    @DisplayName("buildRequestInitializer (private)")
    class BuildRequestInitializer {

        @Test
        @DisplayName("should return initializer that sets timeouts on HttpRequest")
        void should_set_timeouts_on_request() throws Exception {
            GDriveClient client = createTestableClient(oauthConfig());

            // Use a mock GoogleCredentials to avoid real token fetching
            com.google.auth.oauth2.GoogleCredentials mockCredentials =
                    mock(com.google.auth.oauth2.GoogleCredentials.class);

            com.google.api.client.http.HttpRequestInitializer initializer =
                    invokePrivate(client, "buildRequestInitializer",
                            new Class[]{com.google.auth.oauth2.GoogleCredentials.class}, mockCredentials);

            assertThat(initializer).isNotNull();

            // Create a mock HttpRequest and verify the initializer configures it
            HttpRequest mockRequest = mock(HttpRequest.class);
            initializer.initialize(mockRequest);

            // Verify timeouts were set (OAuth config uses defaults: 30000 / 60000)
            org.mockito.Mockito.verify(mockRequest).setConnectTimeout(30_000);
            org.mockito.Mockito.verify(mockRequest).setReadTimeout(60_000);
        }

        @Test
        @DisplayName("should set custom timeouts from configuration")
        void should_set_custom_timeouts() throws Exception {
            var config = new GDriveConfiguration(
                    null, null,
                    "client-id", "client-secret", "refresh-token",
                    null, null, 5000, 10000);
            GDriveClient client = createTestableClient(config);

            com.google.auth.oauth2.GoogleCredentials mockCredentials =
                    mock(com.google.auth.oauth2.GoogleCredentials.class);

            com.google.api.client.http.HttpRequestInitializer initializer =
                    invokePrivate(client, "buildRequestInitializer",
                            new Class[]{com.google.auth.oauth2.GoogleCredentials.class}, mockCredentials);

            HttpRequest mockRequest = mock(HttpRequest.class);
            initializer.initialize(mockRequest);

            org.mockito.Mockito.verify(mockRequest).setConnectTimeout(5000);
            org.mockito.Mockito.verify(mockRequest).setReadTimeout(10000);
        }
    }

    @Nested
    @DisplayName("buildServiceAccountCredentials (private)")
    class BuildServiceAccountCredentials {

        @Test
        @DisplayName("should throw IOException when SA JSON is not valid service account format")
        void should_throw_when_sa_json_invalid_format() throws Exception {
            var config = new GDriveConfiguration(
                    "{\"type\": \"service_account\", \"project_id\": \"test\"}",
                    null, null, null, null, null, null, 0, 0);
            GDriveClient client = createTestableClient(config);

            assertThatThrownBy(() -> invokePrivate(client, "buildServiceAccountCredentials", new Class[0]))
                    .isInstanceOf(java.io.IOException.class);
        }

        @Test
        @DisplayName("should throw when SA JSON has impersonation but invalid key")
        void should_throw_when_sa_json_with_impersonation_invalid() throws Exception {
            var config = new GDriveConfiguration(
                    "{\"type\": \"service_account\", \"project_id\": \"test\"}",
                    "user@domain.com", null, null, null, null, null, 0, 0);
            GDriveClient client = createTestableClient(config);

            assertThatThrownBy(() -> invokePrivate(client, "buildServiceAccountCredentials", new Class[0]))
                    .isInstanceOf(java.io.IOException.class);
        }

        @Test
        @DisplayName("should create scoped credentials without impersonation when user is null")
        void should_create_scoped_credentials_when_no_impersonation() throws Exception {
            var config = new GDriveConfiguration(
                    "{\"type\": \"service_account\", \"project_id\": \"test\"}",
                    null, null, null, null, null, null, 0, 0);
            GDriveClient client = createTestableClient(config);

            // Mock GoogleCredentials.fromStream to return a mock credential
            com.google.auth.oauth2.GoogleCredentials mockCredentials =
                    mock(com.google.auth.oauth2.GoogleCredentials.class);
            com.google.auth.oauth2.GoogleCredentials scopedCredentials =
                    mock(com.google.auth.oauth2.GoogleCredentials.class);
            when(mockCredentials.createScoped(config.scopes())).thenReturn(scopedCredentials);

            try (var mockedStatic = org.mockito.Mockito.mockStatic(
                    com.google.auth.oauth2.GoogleCredentials.class)) {
                mockedStatic.when(() -> com.google.auth.oauth2.GoogleCredentials.fromStream(
                        org.mockito.ArgumentMatchers.any(java.io.InputStream.class)))
                        .thenReturn(mockCredentials);

                Object result = invokePrivate(client, "buildServiceAccountCredentials", new Class[0]);
                assertThat(result).isSameAs(scopedCredentials);
            }
        }

        @Test
        @DisplayName("should create delegated+scoped credentials when impersonation user set")
        void should_create_delegated_credentials_when_impersonation() throws Exception {
            var config = new GDriveConfiguration(
                    "{\"type\": \"service_account\", \"project_id\": \"test\"}",
                    "admin@domain.com", null, null, null, null, null, 0, 0);
            GDriveClient client = createTestableClient(config);

            // ServiceAccountCredentials mock
            com.google.auth.oauth2.ServiceAccountCredentials mockSaCreds =
                    mock(com.google.auth.oauth2.ServiceAccountCredentials.class);
            com.google.auth.oauth2.ServiceAccountCredentials delegatedCreds =
                    mock(com.google.auth.oauth2.ServiceAccountCredentials.class);
            com.google.auth.oauth2.GoogleCredentials scopedCreds =
                    mock(com.google.auth.oauth2.GoogleCredentials.class);

            when(mockSaCreds.createDelegated("admin@domain.com")).thenReturn(delegatedCreds);
            when(delegatedCreds.createScoped(config.scopes())).thenReturn(scopedCreds);

            try (var mockedStatic = org.mockito.Mockito.mockStatic(
                    com.google.auth.oauth2.GoogleCredentials.class)) {
                mockedStatic.when(() -> com.google.auth.oauth2.GoogleCredentials.fromStream(
                        org.mockito.ArgumentMatchers.any(java.io.InputStream.class)))
                        .thenReturn(mockSaCreds);

                Object result = invokePrivate(client, "buildServiceAccountCredentials", new Class[0]);
                assertThat(result).isSameAs(scopedCreds);
            }
        }

        @Test
        @DisplayName("should skip impersonation when impersonate user is blank")
        void should_skip_impersonation_when_blank() throws Exception {
            var config = new GDriveConfiguration(
                    "{\"type\": \"service_account\", \"project_id\": \"test\"}",
                    "   ", null, null, null, null, null, 0, 0);
            GDriveClient client = createTestableClient(config);

            com.google.auth.oauth2.GoogleCredentials mockCredentials =
                    mock(com.google.auth.oauth2.GoogleCredentials.class);
            com.google.auth.oauth2.GoogleCredentials scopedCredentials =
                    mock(com.google.auth.oauth2.GoogleCredentials.class);
            when(mockCredentials.createScoped(config.scopes())).thenReturn(scopedCredentials);

            try (var mockedStatic = org.mockito.Mockito.mockStatic(
                    com.google.auth.oauth2.GoogleCredentials.class)) {
                mockedStatic.when(() -> com.google.auth.oauth2.GoogleCredentials.fromStream(
                        org.mockito.ArgumentMatchers.any(java.io.InputStream.class)))
                        .thenReturn(mockCredentials);

                Object result = invokePrivate(client, "buildServiceAccountCredentials", new Class[0]);
                assertThat(result).isSameAs(scopedCredentials);
            }
        }

        @Test
        @DisplayName("should apply scopes without delegation when credentials are not ServiceAccountCredentials")
        void should_scope_without_delegation_when_not_sa_creds() throws Exception {
            var config = new GDriveConfiguration(
                    "{\"type\": \"service_account\", \"project_id\": \"test\"}",
                    "user@domain.com", null, null, null, null, null, 0, 0);
            GDriveClient client = createTestableClient(config);

            // Return a plain GoogleCredentials (not ServiceAccountCredentials)
            com.google.auth.oauth2.GoogleCredentials mockCredentials =
                    mock(com.google.auth.oauth2.GoogleCredentials.class);
            com.google.auth.oauth2.GoogleCredentials scopedCredentials =
                    mock(com.google.auth.oauth2.GoogleCredentials.class);
            when(mockCredentials.createScoped(config.scopes())).thenReturn(scopedCredentials);

            try (var mockedStatic = org.mockito.Mockito.mockStatic(
                    com.google.auth.oauth2.GoogleCredentials.class)) {
                mockedStatic.when(() -> com.google.auth.oauth2.GoogleCredentials.fromStream(
                        org.mockito.ArgumentMatchers.any(java.io.InputStream.class)))
                        .thenReturn(mockCredentials);

                Object result = invokePrivate(client, "buildServiceAccountCredentials", new Class[0]);
                // Since mockCredentials is NOT a ServiceAccountCredentials instance,
                // the code skips delegation and goes to createScoped
                assertThat(result).isSameAs(scopedCredentials);
            }
        }
    }

    @Nested
    @DisplayName("Retry logic (exception classification)")
    class RetryLogic {

        @Test
        @DisplayName("should not retry 400 Bad Request")
        void should_not_retry_400() {
            var ex = new GDriveException.ExecutionException("Bad request", 400);
            assertThat(ex.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should retry 429 Too Many Requests")
        void should_retry_429() {
            var ex = new GDriveException.ExecutionException("Rate limited", 429);
            assertThat(ex.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should retry 500 Internal Server Error")
        void should_retry_500() {
            var ex = new GDriveException.ExecutionException("Server error", 500);
            assertThat(ex.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should retry 503 Service Unavailable")
        void should_retry_503() {
            var ex = new GDriveException.ExecutionException("Unavailable", 503);
            assertThat(ex.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should not retry 404 Not Found")
        void should_not_retry_404() {
            var ex = new GDriveException.ExecutionException("Not found", 404);
            assertThat(ex.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should retry 502 Bad Gateway")
        void should_retry_502() {
            var ex = new GDriveException.ExecutionException("Bad gateway", 502);
            assertThat(ex.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should retry 504 Gateway Timeout")
        void should_retry_504() {
            var ex = new GDriveException.ExecutionException("Timeout", 504);
            assertThat(ex.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should not retry 401 Unauthorized")
        void should_not_retry_401() {
            var ex = new GDriveException.ExecutionException("Unauthorized", 401);
            assertThat(ex.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should not retry 403 Forbidden")
        void should_not_retry_403() {
            var ex = new GDriveException.ExecutionException("Forbidden", 403);
            assertThat(ex.isRetryable()).isFalse();
        }
    }

    @Nested
    @DisplayName("Configuration validation")
    class ConfigValidation {

        @Test
        @DisplayName("should apply defaults for missing optional fields")
        void should_apply_defaults() {
            var config = new GDriveConfiguration(
                    "{\"type\": \"service_account\"}",
                    null, null, null, null, null, null, 0, 0);

            assertThat(config.applicationName()).isEqualTo("Bonita-GDrive-Connector");
            assertThat(config.connectTimeout()).isEqualTo(30_000);
            assertThat(config.readTimeout()).isEqualTo(60_000);
            assertThat(config.scopes()).isNotEmpty();
        }

        @Test
        @DisplayName("should preserve custom values")
        void should_preserve_custom() {
            var config = new GDriveConfiguration(
                    "{\"type\": \"service_account\"}",
                    "user@domain.com", null, null, null,
                    "Custom-App",
                    List.of("https://www.googleapis.com/auth/drive"),
                    5000, 10000);

            assertThat(config.applicationName()).isEqualTo("Custom-App");
            assertThat(config.impersonateUser()).isEqualTo("user@domain.com");
            assertThat(config.connectTimeout()).isEqualTo(5000);
            assertThat(config.readTimeout()).isEqualTo(10000);
        }
    }
}

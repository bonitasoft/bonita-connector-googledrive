package com.bonitasoft.connectors.gdrive;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GDriveException")
class GDriveExceptionTest {

    @Nested
    @DisplayName("Basic construction")
    class BasicConstruction {

        @Test
        @DisplayName("should create with message only")
        void should_create_with_message_only() {
            var ex = new GDriveException("Test error");

            assertThat(ex.getMessage()).isEqualTo("Test error");
            assertThat(ex.getStatusCode()).isEqualTo(0);
            assertThat(ex.getReason()).isEqualTo("unknown");
            assertThat(ex.isRetryable()).isFalse();
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("should create with message and cause")
        void should_create_with_message_and_cause() {
            var cause = new RuntimeException("root cause");
            var ex = new GDriveException("Wrapper", cause);

            assertThat(ex.getMessage()).isEqualTo("Wrapper");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getStatusCode()).isEqualTo(0);
            assertThat(ex.getReason()).isEqualTo("unknown");
            assertThat(ex.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should create with all parameters")
        void should_create_with_all_parameters() {
            var cause = new RuntimeException("root");
            var ex = new GDriveException("msg", 429, "rateLimitExceeded", true, cause);

            assertThat(ex.getMessage()).isEqualTo("msg");
            assertThat(ex.getStatusCode()).isEqualTo(429);
            assertThat(ex.getReason()).isEqualTo("rateLimitExceeded");
            assertThat(ex.isRetryable()).isTrue();
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("should default reason to unknown when null")
        void should_default_reason_to_unknown_when_null() {
            var ex = new GDriveException("msg", 500, null, true, null);

            assertThat(ex.getReason()).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("Subclasses")
    class Subclasses {

        @Test
        @DisplayName("should create ValidationException with message")
        void should_create_validation_exception_with_message() {
            var ex = new GDriveException.ValidationException("Invalid input");

            assertThat(ex).isInstanceOf(GDriveException.class);
            assertThat(ex.getMessage()).isEqualTo("Invalid input");
            assertThat(ex.getStatusCode()).isEqualTo(0);
            assertThat(ex.getReason()).isEqualTo("unknown");
            assertThat(ex.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should create ConnectionException with message and cause")
        void should_create_connection_exception_with_message_and_cause() {
            var cause = new java.io.IOException("Network down");
            var ex = new GDriveException.ConnectionException("Connection failed", cause);

            assertThat(ex).isInstanceOf(GDriveException.class);
            assertThat(ex.getMessage()).isEqualTo("Connection failed");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getStatusCode()).isEqualTo(0);
            assertThat(ex.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should create ExecutionException with message and status code")
        void should_create_execution_exception_with_message_and_status_code() {
            var ex = new GDriveException.ExecutionException("Not found", 404);

            assertThat(ex).isInstanceOf(GDriveException.class);
            assertThat(ex.getMessage()).isEqualTo("Not found");
            assertThat(ex.getStatusCode()).isEqualTo(404);
            assertThat(ex.getReason()).isEqualTo("execution_error");
            assertThat(ex.isRetryable()).isFalse();
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("should create ExecutionException with message, status code and cause")
        void should_create_execution_exception_with_cause() {
            var cause = new RuntimeException("API error");
            var ex = new GDriveException.ExecutionException("Server error", 500, cause);

            assertThat(ex).isInstanceOf(GDriveException.class);
            assertThat(ex.getMessage()).isEqualTo("Server error");
            assertThat(ex.getStatusCode()).isEqualTo(500);
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.isRetryable()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {429, 500, 501, 502, 503, 504})
        @DisplayName("should mark retryable status codes in ExecutionException")
        void should_mark_retryable_status_in_execution_exception(int code) {
            var ex = new GDriveException.ExecutionException("Error", code);
            assertThat(ex.isRetryable()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 401, 403, 404, 409})
        @DisplayName("should mark non-retryable status codes in ExecutionException")
        void should_mark_non_retryable_status_in_execution_exception(int code) {
            var ex = new GDriveException.ExecutionException("Error", code);
            assertThat(ex.isRetryable()).isFalse();
        }
    }

    @Nested
    @DisplayName("From Google exception")
    class FromGoogleException {

        @Test
        @DisplayName("should identify rateLimitExceeded as retryable")
        void should_identify_rate_limit_as_retryable() {
            var googleEx = createGoogleException(403, "rateLimitExceeded", "Rate limit");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isTrue();
            assertThat(ex.getStatusCode()).isEqualTo(403);
            assertThat(ex.getReason()).isEqualTo("rateLimitExceeded");
            assertThat(ex.getCause()).isSameAs(googleEx);
        }

        @Test
        @DisplayName("should identify userRateLimitExceeded as retryable")
        void should_identify_user_rate_limit_as_retryable() {
            var googleEx = createGoogleException(403, "userRateLimitExceeded", "User rate limit");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should identify sharingRateLimitExceeded as retryable")
        void should_identify_sharing_rate_limit_as_retryable() {
            var googleEx = createGoogleException(403, "sharingRateLimitExceeded", "Sharing rate limit");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should identify 429 as retryable")
        void should_identify_429_as_retryable() {
            var googleEx = createGoogleException(429, "rateLimitExceeded", "Too many requests");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {500, 502, 503, 504})
        @DisplayName("should identify server errors as retryable")
        void should_identify_server_errors_as_retryable(int statusCode) {
            var googleEx = createGoogleException(statusCode, "serverError", "Server error");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should identify backendError reason as retryable")
        void should_identify_backend_error_as_retryable() {
            var googleEx = createGoogleException(403, "backendError", "Backend error");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should identify internalError reason as retryable")
        void should_identify_internal_error_as_retryable() {
            var googleEx = createGoogleException(403, "internalError", "Internal error");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should not identify 400 as retryable")
        void should_not_identify_400_as_retryable() {
            var googleEx = createGoogleException(400, "badRequest", "Bad request");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should not identify 403 with non-rate-limit reason as retryable")
        void should_not_identify_403_permission_denied_as_retryable() {
            var googleEx = createGoogleException(403, "forbidden", "Access denied");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("should handle exception with no error details")
        void should_handle_exception_with_no_details() {
            var builder = new HttpResponseException.Builder(500, "Server Error", new HttpHeaders());
            var googleEx = new GoogleJsonResponseException(builder, null);

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getReason()).isEqualTo("unknown");
            assertThat(ex.isRetryable()).isTrue(); // 500 is retryable regardless
        }

        @Test
        @DisplayName("should handle exception with empty error list")
        void should_handle_exception_with_empty_error_list() {
            var error = new GoogleJsonError();
            error.setCode(404);
            error.setMessage("Not found");
            error.setErrors(List.of());

            var builder = new HttpResponseException.Builder(404, "Not found", new HttpHeaders());
            var googleEx = new GoogleJsonResponseException(builder, error);

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getReason()).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("User-friendly messages")
    class UserFriendlyMessages {

        @Test
        @DisplayName("should produce user-friendly message for 400")
        void should_produce_message_for_400() {
            var googleEx = createGoogleException(400, "badRequest", "Invalid field");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getMessage()).startsWith("Invalid request:");
        }

        @Test
        @DisplayName("should produce user-friendly message for 401")
        void should_produce_message_for_401() {
            var googleEx = createGoogleException(401, "unauthorized", "Token expired");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getMessage()).contains("Authentication failed");
        }

        @Test
        @DisplayName("should produce user-friendly message for 403 rate limit")
        void should_produce_message_for_403_rate_limit() {
            var googleEx = createGoogleException(403, "rateLimitExceeded", "Rate limit");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getMessage()).contains("Rate limit exceeded");
        }

        @Test
        @DisplayName("should produce user-friendly message for 403 userRateLimitExceeded")
        void should_produce_message_for_403_user_rate_limit() {
            var googleEx = createGoogleException(403, "userRateLimitExceeded", "User rate limit");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getMessage()).contains("Rate limit exceeded");
        }

        @Test
        @DisplayName("should produce user-friendly message for 403 dailyLimitExceeded")
        void should_produce_message_for_403_daily_limit() {
            var googleEx = createGoogleException(403, "dailyLimitExceeded", "Daily limit");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getMessage()).contains("Daily quota exceeded");
        }

        @Test
        @DisplayName("should produce user-friendly message for 403 quotaExceeded")
        void should_produce_message_for_403_quota_exceeded() {
            var googleEx = createGoogleException(403, "quotaExceeded", "Quota exceeded");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getMessage()).contains("Daily quota exceeded");
        }

        @Test
        @DisplayName("should produce user-friendly message for 403 permission denied")
        void should_produce_message_for_403_permission_denied() {
            var googleEx = createGoogleException(403, "forbidden", "No access");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getMessage()).startsWith("Permission denied:");
        }

        @Test
        @DisplayName("should produce user-friendly message for 404")
        void should_produce_message_for_404() {
            var googleEx = createGoogleException(404, "notFound", "Not found");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getMessage()).contains("File or folder not found");
        }

        @Test
        @DisplayName("should produce user-friendly message for 429")
        void should_produce_message_for_429() {
            var googleEx = createGoogleException(429, "rateLimitExceeded", "Too many requests");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getMessage()).contains("Too many requests");
        }

        @ParameterizedTest
        @ValueSource(ints = {500, 502, 503, 504})
        @DisplayName("should produce user-friendly message for server errors")
        void should_produce_message_for_server_errors(int code) {
            var googleEx = createGoogleException(code, "serverError", "Server error");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.getMessage()).contains("temporarily unavailable");
        }

        @Test
        @DisplayName("should use original message for unknown status codes")
        void should_use_original_message_for_unknown_codes() {
            var googleEx = createGoogleException(416, "rangeNotSatisfiable", "Range not satisfiable");

            var ex = GDriveException.fromGoogleException(googleEx);

            // The default case returns the original message from the GoogleJsonResponseException
            assertThat(ex.getStatusCode()).isEqualTo(416);
            assertThat(ex.isRetryable()).isFalse();
        }
    }

    @Nested
    @DisplayName("Output message")
    class OutputMessage {

        @Test
        @DisplayName("should format with status code and reason")
        void should_format_with_status_code() {
            var ex = new GDriveException("Error details", 404, "notFound", false, null);

            String output = ex.toOutputMessage();

            assertThat(output).contains("[HTTP 404");
            assertThat(output).contains("notFound");
            assertThat(output).contains("Error details");
        }

        @Test
        @DisplayName("should return plain message when no status code")
        void should_return_plain_message_when_no_status_code() {
            var ex = new GDriveException("Simple error");

            assertThat(ex.toOutputMessage()).isEqualTo("Simple error");
        }
    }

    @Nested
    @DisplayName("Message truncation")
    class MessageTruncation {

        @Test
        @DisplayName("should not truncate short message")
        void should_not_truncate_short_message() {
            String shortMessage = "Short error";
            assertThat(GDriveException.truncateMessage(shortMessage)).isEqualTo(shortMessage);
        }

        @Test
        @DisplayName("should not truncate message at exact limit")
        void should_not_truncate_message_at_exact_limit() {
            String exactMessage = "x".repeat(GDriveException.MAX_ERROR_MESSAGE_LENGTH);
            assertThat(GDriveException.truncateMessage(exactMessage)).isEqualTo(exactMessage);
        }

        @Test
        @DisplayName("should truncate long message")
        void should_truncate_long_message() {
            String longMessage = "x".repeat(1500);
            String truncated = GDriveException.truncateMessage(longMessage);

            assertThat(truncated).hasSize(GDriveException.MAX_ERROR_MESSAGE_LENGTH);
            assertThat(truncated).endsWith("...");
        }

        @Test
        @DisplayName("should handle null message")
        void should_handle_null_message() {
            assertThat(GDriveException.truncateMessage(null)).isNull();
        }

        @Test
        @DisplayName("should truncate toOutputMessage with long content")
        void should_truncate_toOutputMessage_with_long_content() {
            String longMessage = "x".repeat(1500);
            var ex = new GDriveException(longMessage, 500, "serverError", true, null);

            String output = ex.toOutputMessage();

            assertThat(output.length()).isLessThanOrEqualTo(GDriveException.MAX_ERROR_MESSAGE_LENGTH);
            assertThat(output).startsWith("[HTTP 500");
            assertThat(output).endsWith("...");
        }
    }

    private GoogleJsonResponseException createGoogleException(int code, String reason, String message) {
        var errorInfo = new GoogleJsonError.ErrorInfo();
        errorInfo.setReason(reason);
        errorInfo.setMessage(message);

        var error = new GoogleJsonError();
        error.setCode(code);
        error.setMessage(message);
        error.setErrors(List.of(errorInfo));

        var builder = new HttpResponseException.Builder(code, message, new HttpHeaders());
        return new GoogleJsonResponseException(builder, error);
    }
}

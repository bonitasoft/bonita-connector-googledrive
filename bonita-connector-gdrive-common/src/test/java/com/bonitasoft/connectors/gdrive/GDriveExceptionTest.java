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
        void shouldCreateWithMessage() {
            var ex = new GDriveException("Test error");

            assertThat(ex.getMessage()).isEqualTo("Test error");
            assertThat(ex.getStatusCode()).isEqualTo(0);
            assertThat(ex.isRetryable()).isFalse();
        }
    }

    @Nested
    @DisplayName("From Google exception")
    class FromGoogleException {

        @Test
        @DisplayName("should identify rate limit as retryable")
        void shouldIdentifyRateLimitAsRetryable() {
            var googleEx = createGoogleException(403, "rateLimitExceeded", "Rate limit");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isTrue();
            assertThat(ex.getStatusCode()).isEqualTo(403);
        }

        @ParameterizedTest
        @ValueSource(ints = {500, 502, 503, 504})
        @DisplayName("should identify server errors as retryable")
        void shouldIdentifyServerErrorsAsRetryable(int statusCode) {
            var googleEx = createGoogleException(statusCode, "serverError", "Server error");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("should not identify 400 as retryable")
        void shouldNotIdentify400AsRetryable() {
            var googleEx = createGoogleException(400, "badRequest", "Bad request");

            var ex = GDriveException.fromGoogleException(googleEx);

            assertThat(ex.isRetryable()).isFalse();
        }
    }

    @Nested
    @DisplayName("Output message")
    class OutputMessage {

        @Test
        @DisplayName("should format with status code")
        void shouldFormatWithStatusCode() {
            var ex = new GDriveException("Error", 404, "notFound", false, null);

            assertThat(ex.toOutputMessage()).contains("[HTTP 404");
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

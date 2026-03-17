package com.bonitasoft.connectors.gdrive;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;

/**
 * Exception hierarchy for Google Drive connector.
 * <p>
 * Hierarchy:
 * <pre>
 * GDriveException (base — extends RuntimeException)
 * ├── ValidationException  (Phase 1: bad inputs)
 * ├── ConnectionException  (Phase 2: connection failures)
 * └── ExecutionException   (Phase 3: API errors)
 * </pre>
 */
public class GDriveException extends RuntimeException {

    /**
     * Maximum error message length for Bonita's bpm_failure table.
     * H2 column ERRORMESSAGE is VARCHAR_IGNORECASE(1024).
     */
    static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final int statusCode;
    private final String reason;
    private final boolean retryable;

    public GDriveException(String message) {
        super(message);
        this.statusCode = 0;
        this.reason = "unknown";
        this.retryable = false;
    }

    public GDriveException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.reason = "unknown";
        this.retryable = false;
    }

    public GDriveException(String message, int statusCode, String reason, boolean retryable, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.reason = reason != null ? reason : "unknown";
        this.retryable = retryable;
    }

    /**
     * HTTP status code (0 if not from HTTP response).
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Error reason code from Google API.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Whether this error can be retried (rate-limit, transient server error).
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Truncate a message to fit Bonita's ERRORMESSAGE column (VARCHAR 1024).
     * Leaves a 24-char margin for toOutputMessage() prefix formatting.
     *
     * @param message the message to truncate
     * @return the truncated message, or null if input was null
     */
    static String truncateMessage(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "...";
    }

    /**
     * Format error message for Bonita output parameter.
     */
    public String toOutputMessage() {
        String msg = truncateMessage(getMessage());
        if (statusCode > 0) {
            String formatted = String.format("[HTTP %d - %s] %s", statusCode, reason, msg);
            return truncateMessage(formatted);
        }
        return msg;
    }

    /**
     * Create GDriveException from Google API exception with user-friendly messages.
     */
    public static GDriveException fromGoogleException(GoogleJsonResponseException ex) {
        int code = ex.getStatusCode();
        String reason = extractReason(ex);
        boolean retryable = isRetryableError(code, reason);
        String message = createUserFriendlyMessage(code, reason, ex.getMessage());
        
        return new GDriveException(message, code, reason, retryable, ex);
    }

    private static String extractReason(GoogleJsonResponseException ex) {
        GoogleJsonError error = ex.getDetails();
        if (error != null && error.getErrors() != null && !error.getErrors().isEmpty()) {
            return error.getErrors().get(0).getReason();
        }
        return "unknown";
    }

    private static boolean isRetryableError(int code, String reason) {
        // Rate limit errors
        if (code == 429) return true;
        if (code == 403 && ("rateLimitExceeded".equals(reason) || 
                           "userRateLimitExceeded".equals(reason) ||
                           "sharingRateLimitExceeded".equals(reason))) {
            return true;
        }
        // Server errors
        if (code >= 500 && code <= 504) return true;
        if ("backendError".equals(reason) || "internalError".equals(reason)) return true;
        
        return false;
    }

    private static String createUserFriendlyMessage(int code, String reason, String originalMessage) {
        return switch (code) {
            case 400 -> "Invalid request: " + originalMessage;
            case 401 -> "Authentication failed. Check your Service Account JSON or OAuth credentials.";
            case 403 -> {
                if ("rateLimitExceeded".equals(reason) || "userRateLimitExceeded".equals(reason)) {
                    yield "Rate limit exceeded. The request will be retried automatically.";
                }
                if ("dailyLimitExceeded".equals(reason) || "quotaExceeded".equals(reason)) {
                    yield "Daily quota exceeded. Try again tomorrow or request higher quota.";
                }
                yield "Permission denied: " + originalMessage;
            }
            case 404 -> "File or folder not found. Verify the ID is correct and you have access.";
            case 429 -> "Too many requests. The request will be retried automatically.";
            case 500, 502, 503, 504 -> "Google Drive service temporarily unavailable. Retrying...";
            default -> originalMessage;
        };
    }

    // --- Specialized subclasses ---

    /**
     * Phase 1 error: invalid input parameters.
     */
    public static class ValidationException extends GDriveException {
        public ValidationException(String message) {
            super(message);
        }
    }

    /**
     * Phase 2 error: cannot establish connection.
     */
    public static class ConnectionException extends GDriveException {
        public ConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Phase 3 error: API returned an error during execution.
     */
    public static class ExecutionException extends GDriveException {
        public ExecutionException(String message, int httpStatusCode) {
            super(message, httpStatusCode, "execution_error", isRetryableStatus(httpStatusCode), null);
        }

        public ExecutionException(String message, int httpStatusCode, Throwable cause) {
            super(message, httpStatusCode, "execution_error", isRetryableStatus(httpStatusCode), cause);
        }

        private static boolean isRetryableStatus(int code) {
            return code == 429 || (code >= 500 && code <= 504);
        }
    }
}

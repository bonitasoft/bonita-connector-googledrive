package com.bonitasoft.connectors.gdrive;

public class GDriveException extends Exception {

    private final int statusCode;
    private final boolean retryable;

    public GDriveException(String message) {
        super(message);
        this.statusCode = -1;
        this.retryable = false;
    }

    public GDriveException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.retryable = false;
    }

    public GDriveException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public GDriveException(String message, int statusCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}

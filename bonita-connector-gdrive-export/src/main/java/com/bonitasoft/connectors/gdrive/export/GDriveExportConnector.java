package com.bonitasoft.connectors.gdrive.export;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.bonitasoft.connectors.gdrive.AbstractGDriveConnector;
import com.bonitasoft.connectors.gdrive.GDriveException;
import com.bonitasoft.connectors.gdrive.model.FileMetadata;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import lombok.extern.slf4j.Slf4j;

/**
 * Google Drive Export connector.
 * <p>
 * Exports Google Workspace documents (Docs, Sheets, Slides) to standard formats.
 */
@Slf4j
public class GDriveExportConnector extends AbstractGDriveConnector {

    public static final String INPUT_FILE_ID = "fileId";
    public static final String INPUT_EXPORT_MIME_TYPE = "exportMimeType";

    public static final String OUTPUT_FILE_CONTENT = "fileContent";
    public static final String OUTPUT_FILE_NAME = "fileName";
    public static final String OUTPUT_EXPORTED_MIME_TYPE = "exportedMimeType";
    public static final String OUTPUT_SIZE = "size";

    private static final Map<String, String> MIME_TO_EXTENSION = Map.ofEntries(
            Map.entry("application/pdf", ".pdf"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx"),
            Map.entry("text/plain", ".txt"),
            Map.entry("text/csv", ".csv"),
            Map.entry("text/html", ".html"),
            Map.entry("image/png", ".png"),
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/svg+xml", ".svg")
    );

    private static final long MAX_EXPORT_SIZE = 10 * 1024 * 1024; // 10MB

    @Override
    protected String getConnectorName() {
        return "GDrive-Export";
    }

    @Override
    protected void validateOperationInputs(List<String> errors) {
        String fileId = getStringInput(INPUT_FILE_ID);
        if (fileId == null || fileId.isBlank()) {
            errors.add("fileId is required");
        }

        String exportMimeType = getStringInput(INPUT_EXPORT_MIME_TYPE);
        if (exportMimeType == null || exportMimeType.isBlank()) {
            errors.add("exportMimeType is required (e.g., application/pdf)");
        }
    }

    @Override
    protected void executeOperation(Drive driveService) throws GDriveException {
        String fileId = getStringInput(INPUT_FILE_ID);
        String exportMimeType = getStringInput(INPUT_EXPORT_MIME_TYPE);

        log.info("Exporting file {} to {}", fileId, exportMimeType);

        // Get file metadata
        File fileMetadata = client.executeWithRetry(() -> {
            return driveService.files().get(fileId)
                    .setSupportsAllDrives(true)
                    .setFields("id,name,mimeType")
                    .execute();
        }, "get file metadata for " + fileId);

        FileMetadata metadata = FileMetadata.fromDriveFile(fileMetadata);

        // Verify it's a Google Workspace document
        if (!metadata.isGoogleWorkspaceDocument()) {
            throw new GDriveException.ExecutionException(
                    "Export only works with Google Workspace documents. For regular files, use Download. " +
                    "File type: " + fileMetadata.getMimeType(), 400);
        }

        // Export file
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        client.executeWithRetry(() -> {
            driveService.files().export(fileId, exportMimeType)
                    .executeMediaAndDownloadTo(outputStream);
            return null;
        }, "export file " + fileId);

        if (outputStream.size() > MAX_EXPORT_SIZE) {
            log.warn("Exported file exceeds recommended size: {} bytes", outputStream.size());
        }

        // Generate output filename
        String baseName = fileMetadata.getName();
        String extension = MIME_TO_EXTENSION.getOrDefault(exportMimeType, "");
        String outputFileName = baseName + extension;

        // Encode to Base64
        String base64Content = Base64.getEncoder().encodeToString(outputStream.toByteArray());

        // Set outputs
        setOutputParameter(OUTPUT_FILE_CONTENT, base64Content);
        setOutputParameter(OUTPUT_FILE_NAME, outputFileName);
        setOutputParameter(OUTPUT_EXPORTED_MIME_TYPE, exportMimeType);
        setOutputParameter(OUTPUT_SIZE, (long) outputStream.size());

        log.info("File exported successfully: {} ({} bytes)", outputFileName, outputStream.size());
    }
}

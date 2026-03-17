package com.bonitasoft.connectors.gdrive.download;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

import com.bonitasoft.connectors.gdrive.AbstractGDriveConnector;
import com.bonitasoft.connectors.gdrive.GDriveException;
import com.bonitasoft.connectors.gdrive.model.FileMetadata;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import lombok.extern.slf4j.Slf4j;

/**
 * Google Drive Download connector.
 * <p>
 * Downloads files from Google Drive and returns content as Base64.
 * For Google Workspace documents (Docs, Sheets, Slides), use the Export connector instead.
 */
@Slf4j
public class GDriveDownloadConnector extends AbstractGDriveConnector {

    public static final String INPUT_FILE_ID = "fileId";
    public static final String INPUT_ACKNOWLEDGE_ABUSE = "acknowledgeAbuse";

    public static final String OUTPUT_FILE_CONTENT = "fileContent";
    public static final String OUTPUT_FILE_NAME = "fileName";
    public static final String OUTPUT_MIME_TYPE = "mimeType";
    public static final String OUTPUT_SIZE = "size";
    public static final String OUTPUT_MD5_CHECKSUM = "md5Checksum";

    @Override
    protected String getConnectorName() {
        return "GDrive-Download";
    }

    @Override
    protected void validateOperationInputs(List<String> errors) {
        String fileId = getStringInput(INPUT_FILE_ID);
        if (fileId == null || fileId.isBlank()) {
            errors.add("fileId is required");
        }
    }

    @Override
    protected void executeOperation(Drive driveService) throws GDriveException {
        String fileId = getStringInput(INPUT_FILE_ID);
        boolean acknowledgeAbuse = getBooleanInputOrDefault(INPUT_ACKNOWLEDGE_ABUSE, false);

        log.info("Downloading file: {}", fileId);

        // Get file metadata first
        File fileMetadata = client.executeWithRetry(() -> {
            return driveService.files().get(fileId)
                    .setSupportsAllDrives(true)
                    .setFields("id,name,mimeType,size,md5Checksum")
                    .execute();
        }, "get file metadata for " + fileId);

        FileMetadata metadata = FileMetadata.fromDriveFile(fileMetadata);

        // Check if it's a Google Workspace document
        if (metadata.isGoogleWorkspaceDocument()) {
            throw new GDriveException.ExecutionException(
                    "Cannot download Google Workspace documents directly. Use the Export connector instead. " +
                    "File type: " + fileMetadata.getMimeType(), 400);
        }

        log.debug("File: {} ({}, {} bytes)", fileMetadata.getName(), fileMetadata.getMimeType(), fileMetadata.getSize());

        // Download file content
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        client.executeWithRetry(() -> {
            Drive.Files.Get request = driveService.files().get(fileId);
            if (acknowledgeAbuse) {
                request.setAcknowledgeAbuse(true);
            }
            request.executeMediaAndDownloadTo(outputStream);
            return null;
        }, "download file " + fileId);

        // Encode to Base64
        String base64Content = Base64.getEncoder().encodeToString(outputStream.toByteArray());

        // Set outputs
        setOutputParameter(OUTPUT_FILE_CONTENT, base64Content);
        setOutputParameter(OUTPUT_FILE_NAME, fileMetadata.getName());
        setOutputParameter(OUTPUT_MIME_TYPE, fileMetadata.getMimeType());
        setOutputParameter(OUTPUT_SIZE, fileMetadata.getSize());
        setOutputParameter(OUTPUT_MD5_CHECKSUM, fileMetadata.getMd5Checksum());

        log.info("File downloaded successfully: {} ({} bytes)", fileMetadata.getName(), outputStream.size());
    }
}

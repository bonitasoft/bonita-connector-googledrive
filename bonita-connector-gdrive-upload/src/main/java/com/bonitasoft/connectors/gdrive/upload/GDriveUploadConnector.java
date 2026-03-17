package com.bonitasoft.connectors.gdrive.upload;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bonitasoft.connectors.gdrive.AbstractGDriveConnector;
import com.bonitasoft.connectors.gdrive.GDriveException;
import com.bonitasoft.connectors.gdrive.model.FileMetadata;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import lombok.extern.slf4j.Slf4j;

/**
 * Google Drive Upload connector.
 * <p>
 * Uploads files to Google Drive with support for:
 * <ul>
 *   <li>Binary files (Base64 encoded)</li>
 *   <li>Custom metadata and properties</li>
 *   <li>Conversion to Google Workspace formats</li>
 *   <li>Folder targeting</li>
 * </ul>
 */
@Slf4j
public class GDriveUploadConnector extends AbstractGDriveConnector {

    // Input parameters
    public static final String INPUT_FILE_NAME = "fileName";
    public static final String INPUT_FILE_CONTENT = "fileContent";
    public static final String INPUT_MIME_TYPE = "mimeType";
    public static final String INPUT_PARENT_FOLDER_ID = "parentFolderId";
    public static final String INPUT_DESCRIPTION = "description";
    public static final String INPUT_CUSTOM_PROPERTIES = "customProperties";
    public static final String INPUT_CONVERT_TO_GOOGLE_FORMAT = "convertToGoogleFormat";

    // Output parameters
    public static final String OUTPUT_FILE_ID = "fileId";
    public static final String OUTPUT_FILE_NAME = "fileName";
    public static final String OUTPUT_WEB_VIEW_LINK = "webViewLink";
    public static final String OUTPUT_WEB_CONTENT_LINK = "webContentLink";
    public static final String OUTPUT_MIME_TYPE = "mimeType";
    public static final String OUTPUT_SIZE = "size";
    public static final String OUTPUT_MD5_CHECKSUM = "md5Checksum";

    // MIME type mappings for Google format conversion
    private static final Map<String, String> GOOGLE_MIME_TYPES = Map.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.google-apps.document",
            "application/msword", "application/vnd.google-apps.document",
            "text/plain", "application/vnd.google-apps.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.google-apps.spreadsheet",
            "application/vnd.ms-excel", "application/vnd.google-apps.spreadsheet",
            "text/csv", "application/vnd.google-apps.spreadsheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/vnd.google-apps.presentation",
            "application/vnd.ms-powerpoint", "application/vnd.google-apps.presentation"
    );

    @Override
    protected String getConnectorName() {
        return "GDrive-Upload";
    }

    @Override
    protected void validateOperationInputs(List<String> errors) {
        String fileName = getStringInput(INPUT_FILE_NAME);
        if (fileName == null || fileName.isBlank()) {
            errors.add("fileName is required");
        } else if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            errors.add("fileName exceeds maximum length (" + MAX_FILE_NAME_LENGTH + " chars)");
        }

        String fileContent = getStringInput(INPUT_FILE_CONTENT);
        if (fileContent == null || fileContent.isBlank()) {
            errors.add("fileContent is required (Base64 encoded)");
        } else {
            // Check length limit first (before attempting decode)
            if (fileContent.length() > MAX_FILE_CONTENT_LENGTH) {
                errors.add("fileContent exceeds maximum length (~37MB decoded). Consider using streaming for large files.");
            } else {
                // Validate Base64 encoding
                try {
                    Base64.getDecoder().decode(fileContent);
                } catch (IllegalArgumentException e) {
                    errors.add("fileContent must be valid Base64 encoded data");
                }
            }
        }

        // Validate optional description length
        String description = getStringInput(INPUT_DESCRIPTION);
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add("description exceeds maximum length (" + MAX_DESCRIPTION_LENGTH + " chars)");
        }
    }

    @Override
    protected void executeOperation(Drive driveService) throws GDriveException {
        String fileName = getStringInput(INPUT_FILE_NAME);
        String fileContent = getStringInput(INPUT_FILE_CONTENT);
        String mimeType = getStringInputOrDefault(INPUT_MIME_TYPE, "application/octet-stream");
        String parentFolderId = getStringInput(INPUT_PARENT_FOLDER_ID);
        String description = getStringInput(INPUT_DESCRIPTION);
        Map<String, String> customProperties = getMapInput(INPUT_CUSTOM_PROPERTIES);
        boolean convertToGoogle = getBooleanInputOrDefault(INPUT_CONVERT_TO_GOOGLE_FORMAT, false);

        log.info("Uploading file '{}' to Google Drive", fileName);

        // Decode Base64 content
        byte[] contentBytes = Base64.getDecoder().decode(fileContent);
        log.debug("File size: {} bytes", contentBytes.length);

        // Build file metadata
        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setDescription(description);
        
        if (parentFolderId != null && !parentFolderId.isBlank()) {
            fileMetadata.setParents(List.of(parentFolderId));
            log.debug("Target folder: {}", parentFolderId);
        }

        if (customProperties != null && !customProperties.isEmpty()) {
            fileMetadata.setAppProperties(customProperties);
            log.debug("Custom properties: {}", customProperties.keySet());
        }

        // Handle Google format conversion
        String uploadMimeType = mimeType;
        if (convertToGoogle && GOOGLE_MIME_TYPES.containsKey(mimeType)) {
            fileMetadata.setMimeType(GOOGLE_MIME_TYPES.get(mimeType));
            log.debug("Converting to Google format: {}", fileMetadata.getMimeType());
        }

        // Create media content
        ByteArrayContent mediaContent = new ByteArrayContent(uploadMimeType, contentBytes);

        // Upload file with retry (supportsAllDrives for Shared Drive compatibility)
        File uploadedFile = client.executeWithRetry(() -> {
            return driveService.files().create(fileMetadata, mediaContent)
                    .setSupportsAllDrives(true)
                    .setFields("id,name,mimeType,size,md5Checksum,webViewLink,webContentLink,parents,createdTime,modifiedTime")
                    .execute();
        }, "upload file '" + fileName + "'");

        // Set outputs
        FileMetadata metadata = FileMetadata.fromDriveFile(uploadedFile);
        setOutputParameter(OUTPUT_FILE_ID, uploadedFile.getId());
        setOutputParameter(OUTPUT_FILE_NAME, uploadedFile.getName());
        setOutputParameter(OUTPUT_WEB_VIEW_LINK, uploadedFile.getWebViewLink());
        setOutputParameter(OUTPUT_WEB_CONTENT_LINK, uploadedFile.getWebContentLink());
        setOutputParameter(OUTPUT_MIME_TYPE, uploadedFile.getMimeType());
        setOutputParameter(OUTPUT_SIZE, uploadedFile.getSize());
        setOutputParameter(OUTPUT_MD5_CHECKSUM, uploadedFile.getMd5Checksum());

        log.info("File uploaded successfully: {} (ID: {})", uploadedFile.getName(), uploadedFile.getId());
    }
}

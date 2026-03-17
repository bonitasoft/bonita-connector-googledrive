package com.bonitasoft.connectors.gdrive.copy;

import java.util.List;

import com.bonitasoft.connectors.gdrive.AbstractGDriveConnector;
import com.bonitasoft.connectors.gdrive.GDriveException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import lombok.extern.slf4j.Slf4j;

/**
 * Google Drive Copy connector.
 */
@Slf4j
public class GDriveCopyConnector extends AbstractGDriveConnector {

    public static final String INPUT_SOURCE_FILE_ID = "sourceFileId";
    public static final String INPUT_NEW_NAME = "newName";
    public static final String INPUT_DESTINATION_FOLDER_ID = "destinationFolderId";

    public static final String OUTPUT_NEW_FILE_ID = "newFileId";
    public static final String OUTPUT_FILE_NAME = "fileName";
    public static final String OUTPUT_WEB_VIEW_LINK = "webViewLink";

    @Override
    protected String getConnectorName() {
        return "GDrive-Copy";
    }

    @Override
    protected void validateOperationInputs(List<String> errors) {
        if (getStringInput(INPUT_SOURCE_FILE_ID) == null || getStringInput(INPUT_SOURCE_FILE_ID).isBlank()) {
            errors.add("sourceFileId is required");
        }
    }

    @Override
    protected void executeOperation(Drive driveService) throws GDriveException {
        String sourceFileId = getStringInput(INPUT_SOURCE_FILE_ID);
        String newName = getStringInput(INPUT_NEW_NAME);
        String destinationFolderId = getStringInput(INPUT_DESTINATION_FOLDER_ID);

        log.info("Copying file: {}", sourceFileId);

        File copyMetadata = new File();
        if (newName != null && !newName.isBlank()) {
            copyMetadata.setName(newName);
        }
        if (destinationFolderId != null && !destinationFolderId.isBlank()) {
            copyMetadata.setParents(List.of(destinationFolderId));
        }

        File copiedFile = client.executeWithRetry(() -> {
            return driveService.files().copy(sourceFileId, copyMetadata)
                    .setSupportsAllDrives(true)
                    .setFields("id,name,webViewLink")
                    .execute();
        }, "copy file " + sourceFileId);

        setOutputParameter(OUTPUT_NEW_FILE_ID, copiedFile.getId());
        setOutputParameter(OUTPUT_FILE_NAME, copiedFile.getName());
        setOutputParameter(OUTPUT_WEB_VIEW_LINK, copiedFile.getWebViewLink());

        log.info("File copied: {} -> {} (ID: {})", sourceFileId, copiedFile.getName(), copiedFile.getId());
    }
}

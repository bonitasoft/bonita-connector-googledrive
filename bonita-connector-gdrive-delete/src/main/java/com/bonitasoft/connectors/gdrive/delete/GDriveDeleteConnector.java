package com.bonitasoft.connectors.gdrive.delete;

import java.util.List;

import com.bonitasoft.connectors.gdrive.AbstractGDriveConnector;
import com.bonitasoft.connectors.gdrive.GDriveException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import lombok.extern.slf4j.Slf4j;

/**
 * Google Drive Delete connector.
 * <p>
 * Supports both permanent deletion and moving to trash.
 */
@Slf4j
public class GDriveDeleteConnector extends AbstractGDriveConnector {

    public static final String INPUT_FILE_ID = "fileId";
    public static final String INPUT_PERMANENT = "permanent";

    @Override
    protected String getConnectorName() {
        return "GDrive-Delete";
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
        boolean permanent = getBooleanInputOrDefault(INPUT_PERMANENT, false);

        if (permanent) {
            log.info("Permanently deleting file: {}", fileId);
            client.executeWithRetry(() -> {
                driveService.files().delete(fileId).setSupportsAllDrives(true).execute();
                return null;
            }, "delete file " + fileId);
            log.info("File permanently deleted: {}", fileId);
        } else {
            log.info("Moving file to trash: {}", fileId);
            File trashedFile = new File();
            trashedFile.setTrashed(true);
            client.executeWithRetry(() -> {
                return driveService.files().update(fileId, trashedFile)
                        .setSupportsAllDrives(true).execute();
            }, "trash file " + fileId);
            log.info("File moved to trash: {}", fileId);
        }
    }
}

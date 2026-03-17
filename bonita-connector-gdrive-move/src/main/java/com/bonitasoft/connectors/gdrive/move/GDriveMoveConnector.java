package com.bonitasoft.connectors.gdrive.move;

import java.util.List;
import java.util.stream.Collectors;

import com.bonitasoft.connectors.gdrive.AbstractGDriveConnector;
import com.bonitasoft.connectors.gdrive.GDriveException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import lombok.extern.slf4j.Slf4j;

/**
 * Google Drive Move connector.
 */
@Slf4j
public class GDriveMoveConnector extends AbstractGDriveConnector {

    public static final String INPUT_FILE_ID = "fileId";
    public static final String INPUT_DESTINATION_FOLDER_ID = "destinationFolderId";
    public static final String INPUT_REMOVE_FROM_CURRENT_PARENT = "removeFromCurrentParent";

    public static final String OUTPUT_FILE_ID = "fileId";
    public static final String OUTPUT_NEW_PARENTS = "newParents";

    @Override
    protected String getConnectorName() {
        return "GDrive-Move";
    }

    @Override
    protected void validateOperationInputs(List<String> errors) {
        if (getStringInput(INPUT_FILE_ID) == null || getStringInput(INPUT_FILE_ID).isBlank()) {
            errors.add("fileId is required");
        }
        if (getStringInput(INPUT_DESTINATION_FOLDER_ID) == null || getStringInput(INPUT_DESTINATION_FOLDER_ID).isBlank()) {
            errors.add("destinationFolderId is required");
        }
    }

    @Override
    protected void executeOperation(Drive driveService) throws GDriveException {
        String fileId = getStringInput(INPUT_FILE_ID);
        String destinationFolderId = getStringInput(INPUT_DESTINATION_FOLDER_ID);
        boolean removeFromCurrent = getBooleanInputOrDefault(INPUT_REMOVE_FROM_CURRENT_PARENT, true);

        log.info("Moving file {} to folder {}", fileId, destinationFolderId);

        // Get current parents
        File currentFile = client.executeWithRetry(() -> {
            return driveService.files().get(fileId).setSupportsAllDrives(true).setFields("parents").execute();
        }, "get current parents for " + fileId);

        String previousParents = "";
        if (removeFromCurrent && currentFile.getParents() != null) {
            previousParents = String.join(",", currentFile.getParents());
        }

        // Move file
        final String parentsToRemove = previousParents;
        File movedFile = client.executeWithRetry(() -> {
            Drive.Files.Update request = driveService.files().update(fileId, null)
                    .setSupportsAllDrives(true)
                    .setAddParents(destinationFolderId)
                    .setFields("id,parents");
            if (!parentsToRemove.isEmpty()) {
                request.setRemoveParents(parentsToRemove);
            }
            return request.execute();
        }, "move file " + fileId);

        setOutputParameter(OUTPUT_FILE_ID, movedFile.getId());
        setOutputParameter(OUTPUT_NEW_PARENTS, movedFile.getParents() != null ? 
                String.join(",", movedFile.getParents()) : destinationFolderId);

        log.info("File moved successfully: {}", fileId);
    }
}

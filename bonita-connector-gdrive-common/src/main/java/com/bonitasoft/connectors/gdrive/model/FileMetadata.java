package com.bonitasoft.connectors.gdrive.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.services.drive.model.File;

import lombok.Builder;
import lombok.Data;

/**
 * Simplified file metadata model for Bonita process variables.
 * <p>
 * Maps Google Drive File objects to a flat structure suitable
 * for use in BPM processes.
 */
@Data
@Builder
public class FileMetadata {

    private String fileId;
    private String fileName;
    private String mimeType;
    private String description;
    private Long size;
    private String md5Checksum;
    private String webViewLink;
    private String webContentLink;
    private List<String> parents;
    private String createdTime;
    private String modifiedTime;
    private Boolean trashed;
    private Map<String, String> customProperties;

    /**
     * Create FileMetadata from Google Drive File object.
     *
     * @param file Google Drive file
     * @return FileMetadata or null if file is null
     */
    public static FileMetadata fromDriveFile(File file) {
        if (file == null) {
            return null;
        }

        return FileMetadata.builder()
                .fileId(file.getId())
                .fileName(file.getName())
                .mimeType(file.getMimeType())
                .description(file.getDescription())
                .size(file.getSize())
                .md5Checksum(file.getMd5Checksum())
                .webViewLink(file.getWebViewLink())
                .webContentLink(file.getWebContentLink())
                .parents(file.getParents())
                .createdTime(file.getCreatedTime() != null ? file.getCreatedTime().toStringRfc3339() : null)
                .modifiedTime(file.getModifiedTime() != null ? file.getModifiedTime().toStringRfc3339() : null)
                .trashed(file.getTrashed())
                .customProperties(file.getAppProperties())
                .build();
    }

    /**
     * Convert to Map for Bonita process variable assignment.
     *
     * @return Map representation of this metadata
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("fileId", fileId != null ? fileId : "");
        map.put("fileName", fileName != null ? fileName : "");
        map.put("mimeType", mimeType != null ? mimeType : "");
        map.put("description", description != null ? description : "");
        map.put("size", size != null ? size : 0L);
        map.put("md5Checksum", md5Checksum != null ? md5Checksum : "");
        map.put("webViewLink", webViewLink != null ? webViewLink : "");
        map.put("webContentLink", webContentLink != null ? webContentLink : "");
        map.put("createdTime", createdTime != null ? createdTime : "");
        map.put("modifiedTime", modifiedTime != null ? modifiedTime : "");
        map.put("trashed", trashed != null ? trashed : false);
        if (parents != null && !parents.isEmpty()) {
            map.put("parentId", parents.get(0));
        } else {
            map.put("parentId", "");
        }
        return map;
    }

    /**
     * Check if this file is a Google Workspace document (Docs, Sheets, Slides, etc.).
     * <p>
     * Google Workspace documents cannot be downloaded directly and must be exported.
     */
    public boolean isGoogleWorkspaceDocument() {
        return mimeType != null && mimeType.startsWith("application/vnd.google-apps.");
    }

    /**
     * Check if this file is a folder.
     */
    public boolean isFolder() {
        return "application/vnd.google-apps.folder".equals(mimeType);
    }

    /**
     * Get the appropriate export MIME types for this Google Workspace document.
     *
     * @return List of available export formats, or empty list if not a Google Workspace doc
     */
    public List<String> getAvailableExportFormats() {
        if (!isGoogleWorkspaceDocument()) {
            return List.of();
        }

        return switch (mimeType) {
            case "application/vnd.google-apps.document" -> List.of(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain",
                    "application/rtf",
                    "text/html",
                    "application/epub+zip"
            );
            case "application/vnd.google-apps.spreadsheet" -> List.of(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "text/csv",
                    "text/tab-separated-values"
            );
            case "application/vnd.google-apps.presentation" -> List.of(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "text/plain"
            );
            case "application/vnd.google-apps.drawing" -> List.of(
                    "application/pdf",
                    "image/png",
                    "image/jpeg",
                    "image/svg+xml"
            );
            default -> List.of("application/pdf");
        };
    }
}

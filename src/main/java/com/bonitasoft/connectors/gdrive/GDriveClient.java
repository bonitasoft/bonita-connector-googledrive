package com.bonitasoft.connectors.gdrive;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

@Slf4j
public class GDriveClient {

    private static final String GOOGLE_APPS_MIME_PREFIX = "application/vnd.google-apps.";
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final long SIMPLE_UPLOAD_LIMIT = 5L * 1024 * 1024; // 5 MB
    private static final long EXPORT_SIZE_LIMIT = 10L * 1024 * 1024; // 10 MB
    private static final List<String> DRIVE_SCOPES = List.of("https://www.googleapis.com/auth/drive");

    private final Drive driveService;
    private final RetryPolicy retryPolicy;

    public GDriveClient(GDriveConfiguration configuration) throws GDriveException {
        this.retryPolicy = new RetryPolicy(configuration.getMaxRetries());
        try {
            GoogleCredentials credentials = resolveCredentials(configuration);
            NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

            this.driveService = new Drive.Builder(transport, GsonFactory.getDefaultInstance(), requestInitializer)
                    .setApplicationName(configuration.getApplicationName())
                    .build();
            log.debug("GDriveClient initialized with application name: {}", configuration.getApplicationName());
        } catch (GeneralSecurityException | IOException e) {
            throw new GDriveException("Failed to initialize Google Drive client: " + e.getMessage(), e);
        }
    }

    // Visible for testing
    GDriveClient(Drive driveService, RetryPolicy retryPolicy) {
        this.driveService = driveService;
        this.retryPolicy = retryPolicy;
    }

    private GoogleCredentials resolveCredentials(GDriveConfiguration config) throws IOException {
        GoogleCredentials credentials;

        if (config.getServiceAccountKeyJson() != null && !config.getServiceAccountKeyJson().isBlank()) {
            credentials = ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(config.getServiceAccountKeyJson().getBytes(StandardCharsets.UTF_8))
            ).createScoped(DRIVE_SCOPES);
        } else {
            String credPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credPath != null && !credPath.isBlank()) {
                credentials = GoogleCredentials.getApplicationDefault().createScoped(DRIVE_SCOPES);
            } else {
                credentials = GoogleCredentials.getApplicationDefault().createScoped(DRIVE_SCOPES);
            }
        }

        if (config.getImpersonatedUserEmail() != null && !config.getImpersonatedUserEmail().isBlank()) {
            credentials = ImpersonatedCredentials.create(
                    credentials,
                    config.getImpersonatedUserEmail(),
                    null,
                    DRIVE_SCOPES,
                    300
            );
        }

        return credentials;
    }

    public UploadFileResult uploadFile(String fileName, byte[] content, String mimeType,
                                       String parentFolderId, String uploadStrategy,
                                       String description) throws GDriveException {
        return retryPolicy.execute(() -> {
            try {
                File fileMetadata = new File();
                fileMetadata.setName(fileName);
                if (description != null && !description.isBlank()) {
                    fileMetadata.setDescription(description);
                }
                if (parentFolderId != null && !parentFolderId.isBlank()) {
                    fileMetadata.setParents(List.of(parentFolderId));
                }

                AbstractInputStreamContent mediaContent = new ByteArrayContent(mimeType, content);

                File created = driveService.files().create(fileMetadata, mediaContent)
                        .setFields("id,webViewLink,webContentLink")
                        .execute();

                return new UploadFileResult(
                        created.getId(),
                        created.getWebViewLink(),
                        created.getWebContentLink()
                );
            } catch (GoogleJsonResponseException e) {
                throw translateException(e);
            } catch (IOException e) {
                throw new GDriveException("Upload failed: " + e.getMessage(), e);
            }
        });
    }

    public DownloadFileResult downloadFile(String fileId, String exportMimeType) throws GDriveException {
        return retryPolicy.execute(() -> {
            try {
                File fileMeta = driveService.files().get(fileId)
                        .setFields("id,name,mimeType,size")
                        .execute();

                String actualMimeType = fileMeta.getMimeType();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                if (actualMimeType != null && actualMimeType.startsWith(GOOGLE_APPS_MIME_PREFIX)) {
                    if (exportMimeType == null || exportMimeType.isBlank()) {
                        throw new GDriveException("exportMimeType is required for Google Workspace files");
                    }
                    driveService.files().export(fileId, exportMimeType)
                            .executeMediaAndDownloadTo(outputStream);
                    actualMimeType = exportMimeType;
                } else {
                    driveService.files().get(fileId)
                            .executeMediaAndDownloadTo(outputStream);
                }

                byte[] bytes = outputStream.toByteArray();
                if (bytes.length > EXPORT_SIZE_LIMIT && fileMeta.getMimeType() != null
                        && fileMeta.getMimeType().startsWith(GOOGLE_APPS_MIME_PREFIX)) {
                    throw new GDriveException("Export exceeds 10 MB limit", 413, false);
                }

                return new DownloadFileResult(
                        Base64.getEncoder().encodeToString(bytes),
                        fileMeta.getName(),
                        actualMimeType,
                        (long) bytes.length
                );
            } catch (GDriveException e) {
                throw e;
            } catch (GoogleJsonResponseException e) {
                throw translateException(e);
            } catch (IOException e) {
                throw new GDriveException("Download failed: " + e.getMessage(), e);
            }
        });
    }

    public CreateFolderResult createFolder(String folderName, String parentFolderId,
                                            String description) throws GDriveException {
        return retryPolicy.execute(() -> {
            try {
                File folderMetadata = new File();
                folderMetadata.setName(folderName);
                folderMetadata.setMimeType(FOLDER_MIME_TYPE);
                if (description != null && !description.isBlank()) {
                    folderMetadata.setDescription(description);
                }
                if (parentFolderId != null && !parentFolderId.isBlank()) {
                    folderMetadata.setParents(List.of(parentFolderId));
                }

                File folder = driveService.files().create(folderMetadata)
                        .setFields("id,webViewLink")
                        .execute();

                return new CreateFolderResult(
                        folder.getId(),
                        folder.getWebViewLink()
                );
            } catch (GoogleJsonResponseException e) {
                throw translateException(e);
            } catch (IOException e) {
                throw new GDriveException("Create folder failed: " + e.getMessage(), e);
            }
        });
    }

    public GetFileResult getFile(String fileId, String fields) throws GDriveException {
        return retryPolicy.execute(() -> {
            try {
                String requestFields = (fields != null && !fields.isBlank()) ? fields
                        : "id,name,mimeType,webViewLink,webContentLink,size,createdTime,modifiedTime,owners,parents";

                File file = driveService.files().get(fileId)
                        .setFields(requestFields)
                        .execute();

                String ownerEmail = null;
                if (file.getOwners() != null && !file.getOwners().isEmpty()) {
                    ownerEmail = file.getOwners().get(0).getEmailAddress();
                }

                Long sizeBytes = file.getSize() != null ? file.getSize() : 0L;

                return new GetFileResult(
                        file.getId(),
                        file.getName(),
                        file.getMimeType(),
                        file.getWebViewLink(),
                        file.getWebContentLink(),
                        sizeBytes,
                        file.getCreatedTime() != null ? file.getCreatedTime().toStringRfc3339() : null,
                        file.getModifiedTime() != null ? file.getModifiedTime().toStringRfc3339() : null,
                        ownerEmail
                );
            } catch (GoogleJsonResponseException e) {
                throw translateException(e);
            } catch (IOException e) {
                throw new GDriveException("Get file failed: " + e.getMessage(), e);
            }
        });
    }

    public ListFilesResult listFiles(String parentFolderId, String searchQuery, String mimeTypeFilter,
                                      int maxResults, String orderBy,
                                      boolean includeFiles, boolean includeFolders) throws GDriveException {
        return retryPolicy.execute(() -> {
            try {
                List<String> queryParts = new ArrayList<>();
                queryParts.add("trashed = false");

                if (parentFolderId != null && !parentFolderId.isBlank()) {
                    queryParts.add("'" + parentFolderId + "' in parents");
                }
                if (searchQuery != null && !searchQuery.isBlank()) {
                    queryParts.add("(" + searchQuery + ")");
                }
                if (mimeTypeFilter != null && !mimeTypeFilter.isBlank()) {
                    queryParts.add("mimeType = '" + mimeTypeFilter + "'");
                }
                if (!includeFiles && includeFolders) {
                    queryParts.add("mimeType = '" + FOLDER_MIME_TYPE + "'");
                } else if (includeFiles && !includeFolders) {
                    queryParts.add("mimeType != '" + FOLDER_MIME_TYPE + "'");
                }

                String query = String.join(" and ", queryParts);

                Drive.Files.List request = driveService.files().list()
                        .setQ(query)
                        .setPageSize(maxResults)
                        .setFields("nextPageToken,files(id,name,mimeType,webViewLink,modifiedTime)")
                        .setOrderBy(orderBy != null ? orderBy : "modifiedTime desc");

                FileList result = request.execute();

                List<Map<String, String>> files = new ArrayList<>();
                if (result.getFiles() != null) {
                    for (File f : result.getFiles()) {
                        Map<String, String> fileMap = new LinkedHashMap<>();
                        fileMap.put("id", f.getId());
                        fileMap.put("name", f.getName());
                        fileMap.put("mimeType", f.getMimeType());
                        fileMap.put("webViewLink", f.getWebViewLink());
                        fileMap.put("modifiedTime", f.getModifiedTime() != null ? f.getModifiedTime().toStringRfc3339() : null);
                        files.add(fileMap);
                    }
                }

                return new ListFilesResult(
                        files,
                        files.size(),
                        result.getNextPageToken()
                );
            } catch (GoogleJsonResponseException e) {
                throw translateException(e);
            } catch (IOException e) {
                throw new GDriveException("List files failed: " + e.getMessage(), e);
            }
        });
    }

    public MoveFileResult moveFile(String fileId, String newParentFolderId, String newName,
                                    boolean removeFromCurrentParents) throws GDriveException {
        return retryPolicy.execute(() -> {
            try {
                File fileMetadata = new File();
                if (newName != null && !newName.isBlank()) {
                    fileMetadata.setName(newName);
                }

                Drive.Files.Update updateRequest = driveService.files().update(fileId, fileMetadata)
                        .setFields("id,name,parents,webViewLink");

                if (newParentFolderId != null && !newParentFolderId.isBlank()) {
                    updateRequest.setAddParents(newParentFolderId);
                    if (removeFromCurrentParents) {
                        File current = driveService.files().get(fileId).setFields("parents").execute();
                        if (current.getParents() != null) {
                            updateRequest.setRemoveParents(String.join(",", current.getParents()));
                        }
                    }
                }

                File updated = updateRequest.execute();

                String parentId = (updated.getParents() != null && !updated.getParents().isEmpty())
                        ? updated.getParents().get(0) : null;

                return new MoveFileResult(
                        updated.getId(),
                        updated.getName(),
                        parentId,
                        updated.getWebViewLink()
                );
            } catch (GoogleJsonResponseException e) {
                throw translateException(e);
            } catch (IOException e) {
                throw new GDriveException("Move file failed: " + e.getMessage(), e);
            }
        });
    }

    public DeleteFileResult deleteFile(String fileId, boolean permanent) throws GDriveException {
        return retryPolicy.execute(() -> {
            try {
                if (permanent) {
                    log.warn("Permanently deleting file: {}", fileId);
                    driveService.files().delete(fileId).execute();
                } else {
                    File trashMetadata = new File();
                    trashMetadata.setTrashed(true);
                    driveService.files().update(fileId, trashMetadata).execute();
                }
                return new DeleteFileResult(fileId, permanent);
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 404) {
                    log.info("File {} already deleted (404), returning success", fileId);
                    return new DeleteFileResult(fileId, permanent);
                }
                throw translateException(e);
            } catch (IOException e) {
                throw new GDriveException("Delete failed: " + e.getMessage(), e);
            }
        });
    }

    private GDriveException translateException(GoogleJsonResponseException e) {
        int code = e.getStatusCode();
        String reason = e.getDetails() != null && e.getDetails().getErrors() != null
                && !e.getDetails().getErrors().isEmpty()
                ? e.getDetails().getErrors().get(0).getReason() : "unknown";
        String message = e.getDetails() != null ? e.getDetails().getMessage() : e.getMessage();

        boolean retryable = RetryPolicy.isRetryableStatusCode(code);

        // 403 with rateLimitExceeded or userRateLimitExceeded is retryable
        if (code == 403 && ("rateLimitExceeded".equals(reason) || "userRateLimitExceeded".equals(reason))) {
            retryable = true;
        }

        return new GDriveException(
                String.format("Google Drive API error %d (%s): %s", code, reason, message),
                code, retryable, e);
    }

    // === Result records ===

    public record UploadFileResult(String fileId, String webViewLink, String webContentLink) {}
    public record DownloadFileResult(String fileContentBase64, String fileName, String mimeType, long fileSizeBytes) {}
    public record CreateFolderResult(String folderId, String folderWebViewLink) {}
    public record GetFileResult(String fileId, String fileName, String mimeType,
                                String webViewLink, String webContentLink, long sizeBytes,
                                String createdTime, String modifiedTime, String ownerEmail) {}
    public record ListFilesResult(List<Map<String, String>> files, int totalCount, String nextPageToken) {}
    public record MoveFileResult(String fileId, String fileName, String newParentFolderId, String webViewLink) {}
    public record DeleteFileResult(String deletedFileId, boolean permanent) {}
}

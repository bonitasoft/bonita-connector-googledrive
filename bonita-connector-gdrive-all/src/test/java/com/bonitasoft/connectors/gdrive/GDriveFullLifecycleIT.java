package com.bonitasoft.connectors.gdrive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.bonitasoft.connectors.gdrive.ConnectorTestToolkit.ConnectorResult;
import com.bonitasoft.connectors.gdrive.copy.GDriveCopyConnector;
import com.bonitasoft.connectors.gdrive.createfolder.GDriveCreateFolderConnector;
import com.bonitasoft.connectors.gdrive.delete.GDriveDeleteConnector;
import com.bonitasoft.connectors.gdrive.download.GDriveDownloadConnector;
import com.bonitasoft.connectors.gdrive.export.GDriveExportConnector;
import com.bonitasoft.connectors.gdrive.move.GDriveMoveConnector;
import com.bonitasoft.connectors.gdrive.search.GDriveSearchConnector;
import com.bonitasoft.connectors.gdrive.upload.GDriveUploadConnector;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Full lifecycle integration test for ALL 8 Google Drive connectors.
 * <p>
 * Executes a realistic workflow against real Google Drive API:
 * <ol>
 *   <li>CreateFolder — main test folder in Shared Drive</li>
 *   <li>Upload — text file into the folder</li>
 *   <li>Search — find the uploaded file</li>
 *   <li>Download — download and verify content roundtrip</li>
 *   <li>Copy — copy file within the folder</li>
 *   <li>CreateFolder — subfolder for Move target</li>
 *   <li>Move — move copied file to subfolder</li>
 *   <li>Upload (convert) — upload text as Google Doc</li>
 *   <li>Export — export Google Doc to PDF</li>
 *   <li>Delete — explicit delete of copied file</li>
 * </ol>
 * Cleanup removes all created resources automatically.
 * <p>
 * Requires:
 * - Service Account JSON: -DGDRIVE_SA_JSON_PATH=/path/to/sa.json
 * - Shared Drive folder: -DGDRIVE_SHARED_FOLDER_ID=folderId
 * <p>
 * Run with: mvn verify -pl bonita-connector-gdrive-all -am
 */
@DisplayName("GDrive Full Lifecycle - Integration Test (8 connectors)")
@Tag("provider-api")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GDriveFullLifecycleIT {

    // Shared Drive folder where the SA has write access (SA has no own storage quota)
    private static final String SHARED_FOLDER_ID = System.getProperty("GDRIVE_SHARED_FOLDER_ID",
            System.getenv().getOrDefault("GDRIVE_SHARED_FOLDER_ID",
                    "1E7UnzYxYzWtRNVJGIFw0YAcX4a-ARVvM")); // "Test Conector Drive" folder

    private static final String TEST_FOLDER_NAME = "Bonita-IT-Lifecycle-" + System.currentTimeMillis();
    private static final String TEST_FILE_NAME = "bonita-lifecycle-test.txt";
    private static final String TEST_FILE_CONTENT = "Hello from Bonita GDrive Full Lifecycle IT!";

    // Shared state between ordered tests
    private static String folderId;
    private static String subFolderId;
    private static String uploadedFileId;
    private static String copiedFileId;
    private static String googleDocId;

    // Track all created resources for cleanup (LIFO: files first, folders last)
    private static final java.util.Deque<String> createdResourceIds = new java.util.ArrayDeque<>();

    @BeforeAll
    static void checkPrerequisites() {
        assumeTrue(ConnectorTestToolkit.isIntegrationTestAvailable(),
                "Skipping ITs: Service Account JSON not found. Set GDRIVE_SA_JSON_PATH to enable.");
        System.out.println("=== GDrive Full Lifecycle IT (8 connectors) ===");
        System.out.println("Shared folder: " + SHARED_FOLDER_ID);
        System.out.println("Test folder: " + TEST_FOLDER_NAME);
    }

    @AfterAll
    static void cleanup() {
        System.out.println("\n=== Cleanup ===");
        while (!createdResourceIds.isEmpty()) {
            String resourceId = createdResourceIds.pop();
            try {
                var inputs = ConnectorTestToolkit.authInputs();
                inputs.put("fileId", resourceId);
                inputs.put("permanent", true);
                ConnectorTestToolkit.execute(new GDriveDeleteConnector(), inputs);
                System.out.println("  Deleted: " + resourceId);
            } catch (Exception e) {
                System.err.println("  Failed to delete " + resourceId + ": " + e.getMessage());
            }
        }
        System.out.println("=== Cleanup complete ===");
    }

    // ---- 1. CreateFolder ----

    @Test
    @Order(1)
    @DisplayName("should_create_folder_in_shared_drive [CreateFolder]")
    void should_create_folder_in_shared_drive() throws Exception {
        var inputs = ConnectorTestToolkit.authInputs();
        inputs.put("folderName", TEST_FOLDER_NAME);
        inputs.put("parentFolderId", SHARED_FOLDER_ID);
        inputs.put("description", "Integration test folder - auto-created by Bonita IT");

        ConnectorResult result = ConnectorTestToolkit.executeOrSkipOnInfraError(new GDriveCreateFolderConnector(), inputs);
        result.printTiming("CreateFolder");

        assertThat(result.isSuccess()).isTrue();
        folderId = result.stringOutput("folderId");
        assertThat(folderId).isNotBlank();
        createdResourceIds.push(folderId);

        System.out.println("Created folder: " + TEST_FOLDER_NAME + " (ID: " + folderId + ")");
    }

    // ---- 2. Upload ----

    @Test
    @Order(2)
    @DisplayName("should_upload_file_to_folder [Upload]")
    void should_upload_file_to_folder() throws Exception {
        assumeTrue(folderId != null, "Folder must be created first");

        String base64Content = Base64.getEncoder().encodeToString(TEST_FILE_CONTENT.getBytes());

        var inputs = ConnectorTestToolkit.authInputs();
        inputs.put("fileName", TEST_FILE_NAME);
        inputs.put("fileContent", base64Content);
        inputs.put("mimeType", "text/plain");
        inputs.put("parentFolderId", folderId);
        inputs.put("description", "Integration test file");

        ConnectorResult result = ConnectorTestToolkit.executeOrSkipOnInfraError(new GDriveUploadConnector(), inputs);
        result.printTiming("Upload");

        assertThat(result.isSuccess()).isTrue();
        uploadedFileId = result.stringOutput("fileId");
        assertThat(uploadedFileId).isNotBlank();
        createdResourceIds.push(uploadedFileId);

        assertThat(result.stringOutput("fileName")).isEqualTo(TEST_FILE_NAME);
        assertThat(result.stringOutput("mimeType")).isEqualTo("text/plain");

        System.out.println("Uploaded: " + TEST_FILE_NAME + " (ID: " + uploadedFileId + ")");
    }

    // ---- 3. Search ----

    @Test
    @Order(3)
    @DisplayName("should_search_and_find_uploaded_file [Search]")
    void should_search_and_find_uploaded_file() throws Exception {
        assumeTrue(uploadedFileId != null, "File must be uploaded first");

        var inputs = ConnectorTestToolkit.authInputs();
        inputs.put("nameContains", TEST_FILE_NAME);
        inputs.put("parentFolderId", folderId);
        inputs.put("includeTrashed", false);

        ConnectorResult result = ConnectorTestToolkit.executeOrSkipOnInfraError(new GDriveSearchConnector(), inputs);
        result.printTiming("Search");

        assertThat(result.isSuccess()).isTrue();
        int totalResults = result.output("totalResults");
        assertThat(totalResults).isGreaterThanOrEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = result.output("files");
        assertThat(files)
                .extracting(f -> f.get("fileId"))
                .contains(uploadedFileId);

        System.out.println("Search found " + totalResults + " file(s)");
    }

    // ---- 4. Download ----

    @Test
    @Order(4)
    @DisplayName("should_download_file_and_verify_content [Download]")
    void should_download_file_and_verify_content() throws Exception {
        assumeTrue(uploadedFileId != null, "File must be uploaded first");

        var inputs = ConnectorTestToolkit.authInputs();
        inputs.put("fileId", uploadedFileId);

        ConnectorResult result = ConnectorTestToolkit.executeOrSkipOnInfraError(new GDriveDownloadConnector(), inputs);
        result.printTiming("Download");

        assertThat(result.isSuccess()).isTrue();

        String base64Content = result.stringOutput("fileContent");
        assertThat(base64Content).isNotBlank();

        String decodedContent = new String(Base64.getDecoder().decode(base64Content));
        assertThat(decodedContent).isEqualTo(TEST_FILE_CONTENT);

        System.out.println("Downloaded and verified: \"" + decodedContent + "\"");
    }

    // ---- 5. Copy ----

    @Test
    @Order(5)
    @DisplayName("should_copy_file [Copy]")
    void should_copy_file() throws Exception {
        assumeTrue(uploadedFileId != null, "File must be uploaded first");

        var inputs = ConnectorTestToolkit.authInputs();
        inputs.put("sourceFileId", uploadedFileId);
        inputs.put("newName", "copy-of-" + TEST_FILE_NAME);
        inputs.put("destinationFolderId", folderId);

        ConnectorResult result = ConnectorTestToolkit.executeOrSkipOnInfraError(new GDriveCopyConnector(), inputs);
        result.printTiming("Copy");

        assertThat(result.isSuccess()).isTrue();
        copiedFileId = result.stringOutput("newFileId");
        assertThat(copiedFileId).isNotBlank();
        createdResourceIds.push(copiedFileId);

        assertThat(result.stringOutput("fileName")).isEqualTo("copy-of-" + TEST_FILE_NAME);
        System.out.println("Copied to: " + result.stringOutput("fileName") + " (ID: " + copiedFileId + ")");
    }

    // ---- 6. CreateFolder (subfolder for Move) ----

    @Test
    @Order(6)
    @DisplayName("should_create_subfolder_for_move [CreateFolder]")
    void should_create_subfolder_for_move() throws Exception {
        assumeTrue(folderId != null, "Main folder must exist");

        var inputs = ConnectorTestToolkit.authInputs();
        inputs.put("folderName", "move-target");
        inputs.put("parentFolderId", folderId);

        ConnectorResult result = ConnectorTestToolkit.executeOrSkipOnInfraError(new GDriveCreateFolderConnector(), inputs);
        result.printTiming("CreateFolder (subfolder)");

        assertThat(result.isSuccess()).isTrue();
        subFolderId = result.stringOutput("folderId");
        assertThat(subFolderId).isNotBlank();
        createdResourceIds.push(subFolderId);

        System.out.println("Created subfolder: move-target (ID: " + subFolderId + ")");
    }

    // ---- 7. Move ----

    @Test
    @Order(7)
    @DisplayName("should_move_copied_file_to_subfolder [Move]")
    void should_move_copied_file_to_subfolder() throws Exception {
        assumeTrue(copiedFileId != null, "File must be copied first");
        assumeTrue(subFolderId != null, "Subfolder must be created first");

        var inputs = ConnectorTestToolkit.authInputs();
        inputs.put("fileId", copiedFileId);
        inputs.put("destinationFolderId", subFolderId);
        inputs.put("removeFromCurrentParent", true);

        ConnectorResult result = ConnectorTestToolkit.executeOrSkipOnInfraError(new GDriveMoveConnector(), inputs);
        result.printTiming("Move");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stringOutput("fileId")).isEqualTo(copiedFileId);
        assertThat(result.stringOutput("newParents")).contains(subFolderId);

        System.out.println("Moved " + copiedFileId + " to subfolder " + subFolderId);
    }

    // ---- 8. Upload with conversion (for Export test) ----

    @Test
    @Order(8)
    @DisplayName("should_upload_and_convert_to_google_doc [Upload + convertToGoogleFormat]")
    void should_upload_and_convert_to_google_doc() throws Exception {
        assumeTrue(folderId != null, "Folder must exist");

        String docContent = "This is a test document for Bonita GDrive Export IT.\n\n"
                + "It contains text that will be converted to a Google Doc,\n"
                + "then exported back to PDF to verify the Export connector.";
        String base64Content = Base64.getEncoder().encodeToString(docContent.getBytes());

        var inputs = ConnectorTestToolkit.authInputs();
        inputs.put("fileName", "bonita-export-test.txt");
        inputs.put("fileContent", base64Content);
        inputs.put("mimeType", "text/plain");
        inputs.put("parentFolderId", folderId);
        inputs.put("convertToGoogleFormat", true);

        ConnectorResult result = ConnectorTestToolkit.executeOrSkipOnInfraError(new GDriveUploadConnector(), inputs);
        result.printTiming("Upload (convert to Google Doc)");

        assertThat(result.isSuccess()).isTrue();
        googleDocId = result.stringOutput("fileId");
        assertThat(googleDocId).isNotBlank();
        createdResourceIds.push(googleDocId);

        // When convertToGoogleFormat=true and mimeType=text/plain, the file becomes a Google Doc
        assertThat(result.stringOutput("mimeType")).isEqualTo("application/vnd.google-apps.document");

        System.out.println("Uploaded as Google Doc (ID: " + googleDocId + ")");
    }

    // ---- 9. Export ----

    @Test
    @Order(9)
    @DisplayName("should_export_google_doc_to_pdf [Export]")
    void should_export_google_doc_to_pdf() throws Exception {
        assumeTrue(googleDocId != null, "Google Doc must be uploaded first");

        var inputs = ConnectorTestToolkit.authInputs();
        inputs.put("fileId", googleDocId);
        inputs.put("exportMimeType", "application/pdf");

        ConnectorResult result = ConnectorTestToolkit.executeOrSkipOnInfraError(new GDriveExportConnector(), inputs);
        result.printTiming("Export");

        assertThat(result.isSuccess()).isTrue();

        String base64Content = result.stringOutput("fileContent");
        assertThat(base64Content).isNotBlank();

        // Verify it's a valid PDF (starts with %PDF after Base64 decode)
        byte[] pdfBytes = Base64.getDecoder().decode(base64Content);
        assertThat(pdfBytes.length).isGreaterThan(0);
        String pdfHeader = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
        assertThat(pdfHeader).startsWith("%PDF");

        assertThat(result.stringOutput("exportedMimeType")).isEqualTo("application/pdf");
        assertThat(result.stringOutput("fileName")).endsWith(".pdf");

        Long size = result.output("size");
        assertThat(size).isGreaterThan(0L);

        System.out.println("Exported to PDF: " + result.stringOutput("fileName") + " (" + size + " bytes)");
    }

    // ---- 10. Delete ----

    @Test
    @Order(10)
    @DisplayName("should_delete_copied_file_permanently [Delete]")
    void should_delete_copied_file_permanently() throws Exception {
        assumeTrue(copiedFileId != null, "File must be copied first");

        var inputs = ConnectorTestToolkit.authInputs();
        inputs.put("fileId", copiedFileId);
        inputs.put("permanent", true);

        ConnectorResult result = ConnectorTestToolkit.executeOrSkipOnInfraError(new GDriveDeleteConnector(), inputs);
        result.printTiming("Delete");

        assertThat(result.isSuccess()).isTrue();
        createdResourceIds.remove(copiedFileId);

        System.out.println("Deleted copied file: " + copiedFileId);
    }
}

package com.bonitasoft.connectors.gdrive.model;

import com.google.api.client.util.DateTime;
import com.google.api.services.drive.model.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileMetadata")
class FileMetadataTest {

    @Nested
    @DisplayName("From Drive File")
    class FromDriveFile {

        @Test
        @DisplayName("should convert Drive File with all fields to FileMetadata")
        void should_convert_drive_file_with_all_fields() {
            File driveFile = new File();
            driveFile.setId("file-123");
            driveFile.setName("document.pdf");
            driveFile.setMimeType("application/pdf");
            driveFile.setDescription("A test document");
            driveFile.setSize(2048L);
            driveFile.setMd5Checksum("abc123md5");
            driveFile.setWebViewLink("https://drive.google.com/view");
            driveFile.setWebContentLink("https://drive.google.com/download");
            driveFile.setParents(List.of("parent-1"));
            driveFile.setCreatedTime(new DateTime("2024-01-15T10:30:00.000Z"));
            driveFile.setModifiedTime(new DateTime("2024-06-20T14:00:00.000Z"));
            driveFile.setTrashed(false);
            driveFile.setAppProperties(Map.of("customKey", "customValue"));

            FileMetadata metadata = FileMetadata.fromDriveFile(driveFile);

            assertThat(metadata.getFileId()).isEqualTo("file-123");
            assertThat(metadata.getFileName()).isEqualTo("document.pdf");
            assertThat(metadata.getMimeType()).isEqualTo("application/pdf");
            assertThat(metadata.getDescription()).isEqualTo("A test document");
            assertThat(metadata.getSize()).isEqualTo(2048L);
            assertThat(metadata.getMd5Checksum()).isEqualTo("abc123md5");
            assertThat(metadata.getWebViewLink()).isEqualTo("https://drive.google.com/view");
            assertThat(metadata.getWebContentLink()).isEqualTo("https://drive.google.com/download");
            assertThat(metadata.getParents()).containsExactly("parent-1");
            assertThat(metadata.getCreatedTime()).isNotNull();
            assertThat(metadata.getModifiedTime()).isNotNull();
            assertThat(metadata.getTrashed()).isFalse();
            assertThat(metadata.getCustomProperties()).containsEntry("customKey", "customValue");
        }

        @Test
        @DisplayName("should handle Drive File with null timestamps")
        void should_handle_null_timestamps() {
            File driveFile = new File();
            driveFile.setId("file-456");
            driveFile.setName("no-dates.txt");

            FileMetadata metadata = FileMetadata.fromDriveFile(driveFile);

            assertThat(metadata.getCreatedTime()).isNull();
            assertThat(metadata.getModifiedTime()).isNull();
        }

        @Test
        @DisplayName("should return null for null input")
        void should_return_null_for_null_input() {
            assertThat(FileMetadata.fromDriveFile(null)).isNull();
        }

        @Test
        @DisplayName("should handle Drive File with minimal fields")
        void should_handle_minimal_fields() {
            File driveFile = new File();
            driveFile.setId("file-min");

            FileMetadata metadata = FileMetadata.fromDriveFile(driveFile);

            assertThat(metadata.getFileId()).isEqualTo("file-min");
            assertThat(metadata.getFileName()).isNull();
            assertThat(metadata.getMimeType()).isNull();
            assertThat(metadata.getSize()).isNull();
            assertThat(metadata.getParents()).isNull();
        }
    }

    @Nested
    @DisplayName("To Map")
    class ToMap {

        @Test
        @DisplayName("should convert fully populated metadata to map")
        void should_convert_full_metadata_to_map() {
            var metadata = FileMetadata.builder()
                    .fileId("123")
                    .fileName("test.pdf")
                    .mimeType("application/pdf")
                    .description("A description")
                    .size(1024L)
                    .md5Checksum("md5hash")
                    .webViewLink("https://view")
                    .webContentLink("https://download")
                    .parents(List.of("parent-id"))
                    .createdTime("2024-01-01T00:00:00.000Z")
                    .modifiedTime("2024-06-01T00:00:00.000Z")
                    .trashed(true)
                    .build();

            Map<String, Object> map = metadata.toMap();

            assertThat(map).containsEntry("fileId", "123");
            assertThat(map).containsEntry("fileName", "test.pdf");
            assertThat(map).containsEntry("mimeType", "application/pdf");
            assertThat(map).containsEntry("description", "A description");
            assertThat(map).containsEntry("size", 1024L);
            assertThat(map).containsEntry("md5Checksum", "md5hash");
            assertThat(map).containsEntry("webViewLink", "https://view");
            assertThat(map).containsEntry("webContentLink", "https://download");
            assertThat(map).containsEntry("createdTime", "2024-01-01T00:00:00.000Z");
            assertThat(map).containsEntry("modifiedTime", "2024-06-01T00:00:00.000Z");
            assertThat(map).containsEntry("trashed", true);
            assertThat(map).containsEntry("parentId", "parent-id");
        }

        @Test
        @DisplayName("should use defaults for null fields in map")
        void should_use_defaults_for_null_fields() {
            var metadata = FileMetadata.builder().build();

            Map<String, Object> map = metadata.toMap();

            assertThat(map).containsEntry("fileId", "");
            assertThat(map).containsEntry("fileName", "");
            assertThat(map).containsEntry("mimeType", "");
            assertThat(map).containsEntry("description", "");
            assertThat(map).containsEntry("size", 0L);
            assertThat(map).containsEntry("md5Checksum", "");
            assertThat(map).containsEntry("webViewLink", "");
            assertThat(map).containsEntry("webContentLink", "");
            assertThat(map).containsEntry("createdTime", "");
            assertThat(map).containsEntry("modifiedTime", "");
            assertThat(map).containsEntry("trashed", false);
            assertThat(map).containsEntry("parentId", "");
        }

        @Test
        @DisplayName("should use empty parentId when parents list is empty")
        void should_use_empty_parent_id_when_parents_empty() {
            var metadata = FileMetadata.builder()
                    .parents(List.of())
                    .build();

            Map<String, Object> map = metadata.toMap();

            assertThat(map).containsEntry("parentId", "");
        }
    }

    @Nested
    @DisplayName("Type detection")
    class TypeDetection {

        @ParameterizedTest
        @ValueSource(strings = {
                "application/vnd.google-apps.document",
                "application/vnd.google-apps.spreadsheet",
                "application/vnd.google-apps.presentation",
                "application/vnd.google-apps.drawing",
                "application/vnd.google-apps.form",
                "application/vnd.google-apps.script"
        })
        @DisplayName("should detect Google Workspace MIME types as workspace documents")
        void should_detect_google_workspace_types(String mimeType) {
            var metadata = FileMetadata.builder()
                    .mimeType(mimeType)
                    .build();

            assertThat(metadata.isGoogleWorkspaceDocument()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "application/pdf",
                "text/plain",
                "image/png",
                "application/octet-stream"
        })
        @DisplayName("should not detect non-Google MIME types as workspace documents")
        void should_not_detect_non_google_types(String mimeType) {
            var metadata = FileMetadata.builder()
                    .mimeType(mimeType)
                    .build();

            assertThat(metadata.isGoogleWorkspaceDocument()).isFalse();
        }

        @Test
        @DisplayName("should not detect null MIME type as workspace document")
        void should_not_detect_null_mime_type() {
            var metadata = FileMetadata.builder().build();

            assertThat(metadata.isGoogleWorkspaceDocument()).isFalse();
        }

        @Test
        @DisplayName("should detect folder")
        void should_detect_folder() {
            var metadata = FileMetadata.builder()
                    .mimeType("application/vnd.google-apps.folder")
                    .build();

            assertThat(metadata.isFolder()).isTrue();
        }

        @Test
        @DisplayName("should not detect non-folder as folder")
        void should_not_detect_non_folder() {
            var metadata = FileMetadata.builder()
                    .mimeType("application/pdf")
                    .build();

            assertThat(metadata.isFolder()).isFalse();
        }
    }

    @Nested
    @DisplayName("Export formats")
    class ExportFormats {

        @Test
        @DisplayName("should return document export formats")
        void should_return_document_export_formats() {
            var metadata = FileMetadata.builder()
                    .mimeType("application/vnd.google-apps.document")
                    .build();

            List<String> formats = metadata.getAvailableExportFormats();

            assertThat(formats).containsExactly(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain",
                    "application/rtf",
                    "text/html",
                    "application/epub+zip"
            );
        }

        @Test
        @DisplayName("should return spreadsheet export formats")
        void should_return_spreadsheet_export_formats() {
            var metadata = FileMetadata.builder()
                    .mimeType("application/vnd.google-apps.spreadsheet")
                    .build();

            List<String> formats = metadata.getAvailableExportFormats();

            assertThat(formats).containsExactly(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "text/csv",
                    "text/tab-separated-values"
            );
        }

        @Test
        @DisplayName("should return presentation export formats")
        void should_return_presentation_export_formats() {
            var metadata = FileMetadata.builder()
                    .mimeType("application/vnd.google-apps.presentation")
                    .build();

            List<String> formats = metadata.getAvailableExportFormats();

            assertThat(formats).containsExactly(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "text/plain"
            );
        }

        @Test
        @DisplayName("should return drawing export formats")
        void should_return_drawing_export_formats() {
            var metadata = FileMetadata.builder()
                    .mimeType("application/vnd.google-apps.drawing")
                    .build();

            List<String> formats = metadata.getAvailableExportFormats();

            assertThat(formats).containsExactly(
                    "application/pdf",
                    "image/png",
                    "image/jpeg",
                    "image/svg+xml"
            );
        }

        @Test
        @DisplayName("should return PDF for unknown Google Workspace type")
        void should_return_pdf_for_unknown_workspace_type() {
            var metadata = FileMetadata.builder()
                    .mimeType("application/vnd.google-apps.form")
                    .build();

            List<String> formats = metadata.getAvailableExportFormats();

            assertThat(formats).containsExactly("application/pdf");
        }

        @Test
        @DisplayName("should return empty list for non-workspace file")
        void should_return_empty_for_non_workspace_file() {
            var metadata = FileMetadata.builder()
                    .mimeType("application/pdf")
                    .build();

            List<String> formats = metadata.getAvailableExportFormats();

            assertThat(formats).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when MIME type is null")
        void should_return_empty_when_mime_type_null() {
            var metadata = FileMetadata.builder().build();

            List<String> formats = metadata.getAvailableExportFormats();

            assertThat(formats).isEmpty();
        }
    }
}

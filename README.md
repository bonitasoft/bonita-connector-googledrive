# Bonita Google Drive Connector

Multi-module Bonita BPM connector for Google Drive integration.

## Table of Contents

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Authentication](#authentication)
- [Shared Drives](#shared-drives)
- [Operations Reference](#operations-reference)
- [Parameter Scopes](#parameter-scopes)
- [BPM Use Cases](#bpm-use-cases)
- [Error Handling](#error-handling)
- [Testing](#testing)
  - [Test Architecture](#test-architecture)
  - [Run Unit Tests](#run-unit-tests-levels-1-3)
  - [Run Integration Tests](#run-integration-tests-level-4)
  - [ConnectorTestToolkit](#connectortesttoolkit)
- [Troubleshooting](#troubleshooting)
- [Development](#development)

---

## Features

| Operation | Connector ID | Description |
|-----------|--------------|-------------|
| **Upload** | `gdrive-upload` | Upload files with metadata and Google format conversion |
| **Download** | `gdrive-download` | Download binary files as Base64 |
| **Export** | `gdrive-export` | Export Google Docs/Sheets/Slides to PDF, DOCX, XLSX, etc. |
| **Create Folder** | `gdrive-create-folder` | Create folders with custom metadata |
| **Delete** | `gdrive-delete` | Delete or move files to trash |
| **Move** | `gdrive-move` | Move files between folders |
| **Copy** | `gdrive-copy` | Copy files with optional rename |
| **Search** | `gdrive-search` | Search with query filters, full-text, and pagination |

---

## Prerequisites

- **Bonita Runtime:** 10.2.0+
- **Java:** 17+
- **Maven:** 3.8+
- **Google Cloud:** Project with Drive API enabled
- **Credentials:** Service Account (see [Authentication](#authentication))
- **Shared Drive:** Required for file uploads with Service Account (SA has no personal storage quota)

---

## Installation

### 1. Build from source

```bash
# Clone and build (produces a single fat JAR with all 8 operations)
git clone <repository-url>
cd bonita-connector-gdrive
mvn clean install -DskipTests

# The fat JAR is in the -all module
ls bonita-connector-gdrive-all/target/*-bonita.jar
```

### 2. Verify JAR contents

```bash
# Must contain: 8 .def, 8 .impl, 8 .properties, 1 gdrive.png
jar tf bonita-connector-gdrive-all/target/bonita-connector-gdrive-all-*-bonita.jar \
  | grep -E '\.(def|impl|properties|png)$' | grep -v META-INF
```

### 3. Deploy to Bonita Studio (2025.2+)

1. Open Bonita Studio
2. Go to **Extensions → Import extension**
3. Select `bonita-connector-gdrive-all-1.0.0-SNAPSHOT-bonita.jar`
4. All 8 connectors appear under the **"Google Drive"** category

### 4. Deploy to Bonita Runtime (Server)

```bash
# Copy the fat JAR to the connectors directory
cp bonita-connector-gdrive-all/target/*-bonita.jar \
   $BONITA_HOME/server/platform/tenant-template-data/connectors/

# Restart Bonita
```

---

## Authentication

### Service Account (Recommended for Server-to-Server)

This is the recommended authentication method for BPM processes where no user interaction is needed.

#### Configuration Parameters

| Parameter | Type | Required | Widget | Description |
|-----------|------|----------|--------|-------------|
| `serviceAccountJson` | String | Yes* | Password | Full JSON key content (not file path) |
| `impersonateUser` | String | No | Text | Email to impersonate (domain-wide delegation) |
| `applicationName` | String | No | Text | Application name (default: `Bonita-GDrive-Connector`) |
| `connectTimeout` | Integer | No | Text | Connection timeout in ms (default: 30000) |
| `readTimeout` | Integer | No | Text | Read timeout in ms (default: 60000) |

*Required unless OAuth 2.0 credentials are provided.

#### Credential Resolution Order

When credentials are not provided explicitly, the connector attempts resolution in this order:

```
1. Connector parameter: serviceAccountJson (highest priority)
2. Environment variable: GOOGLE_APPLICATION_CREDENTIALS (file path)
3. JVM system property: google.application.credentials (file path)
```

#### Example: Service Account with Impersonation

```groovy
// In Bonita connector configuration
serviceAccountJson: '''
{
  "type": "service_account",
  "project_id": "my-project",
  "private_key_id": "abc123...",
  "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
  "client_email": "bonita-connector@my-project.iam.gserviceaccount.com",
  "client_id": "123456789",
  ...
}
'''
impersonateUser: "admin@mydomain.com"
```

### OAuth 2.0 (For User-Delegated Access)

Use this when you need to act on behalf of a specific user who has already authorized the application.

| Parameter | Type | Required | Widget | Description |
|-----------|------|----------|--------|-------------|
| `clientId` | String | Yes* | Text | OAuth 2.0 Client ID |
| `clientSecret` | String | Yes* | Password | OAuth 2.0 Client Secret |
| `refreshToken` | String | Yes* | Password | Pre-obtained refresh token |

*Required if Service Account is not provided.

---

## Shared Drives

All connectors support Google Shared Drives out of the box (`supportsAllDrives=true` is set internally).

### Why Shared Drives matter

Service Accounts **do not have personal storage quota**. This means:

| Operation | SA on regular Drive | SA on Shared Drive | SA with Impersonation |
|-----------|--------------------|--------------------|----------------------|
| **Search** | Read shared files only | Full search | Full search |
| **Download** | If file is shared | Full access | Full access |
| **Upload** | **403 storageQuotaExceeded** | Works | Works |
| **Create Folder** | Creates in "orphan" space | Works | Works |
| **Delete** | If file is owned by SA | Full access | Full access |
| **Copy/Move** | Limited | Full access | Full access |

### Setup: Add Service Account to Shared Drive

1. Create a Shared Drive in Google Workspace Admin
2. Open the Shared Drive → click "Manage members"
3. Add the Service Account email (e.g., `bonita-connector@my-project.iam.gserviceaccount.com`)
4. Set role to **Content Manager** or **Manager**
5. Use the Shared Drive folder ID as `parentFolderId` in your connectors

### Alternative: Domain-Wide Delegation

If you don't have Shared Drives, configure impersonation:

1. Enable domain-wide delegation on the Service Account (Google Cloud Console)
2. Authorize the SA scopes in Google Workspace Admin (Security → API controls → Domain-wide delegation)
3. Set `impersonateUser` to a real user email in the connector configuration

---

## Operations Reference

### Upload (`gdrive-upload`)

Upload files to Google Drive with support for metadata and format conversion.

#### Input Parameters

| Parameter | Type | Required | Scope | Description |
|-----------|------|----------|-------|-------------|
| `fileName` | String | **Yes** | Connector | Name for the uploaded file |
| `fileContent` | String | **Yes** | Connector | File content (Base64 encoded) |
| `mimeType` | String | No | Connector | MIME type (default: `application/octet-stream`) |
| `parentFolderId` | String | No | Process | Target folder ID |
| `description` | String | No | Connector | File description |
| `customProperties` | Map | No | Connector | Custom app properties (key-value) |
| `convertToGoogleFormat` | Boolean | No | Connector | Convert Office files to Google format |

#### Output Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `success` | Boolean | `true` if upload completed |
| `errorMessage` | String | Error description (empty on success) |
| `fileId` | String | Google Drive file ID |
| `fileName` | String | Final file name |
| `webViewLink` | String | URL to view in browser |
| `webContentLink` | String | Direct download URL |
| `mimeType` | String | Final MIME type |
| `size` | Long | File size in bytes |
| `md5Checksum` | String | MD5 hash of content |

#### Example

```groovy
// Upload a PDF to a specific folder
fileName: "Invoice_${invoiceNumber}.pdf"
fileContent: ${documentBase64}
mimeType: "application/pdf"
parentFolderId: "1ABC123xyz"  // Invoices folder
description: "Invoice for order ${orderId}"
```

---

### Download (`gdrive-download`)

Download binary files from Google Drive. For Google Workspace documents (Docs, Sheets), use **Export** instead.

#### Input Parameters

| Parameter | Type | Required | Scope | Description |
|-----------|------|----------|-------|-------------|
| `fileId` | String | **Yes** | Connector | File ID to download |
| `acknowledgeAbuse` | Boolean | No | Connector | Download even if flagged as abuse |

#### Output Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `success` | Boolean | `true` if download completed |
| `errorMessage` | String | Error description (empty on success) |
| `fileContent` | String | File content (Base64 encoded) |
| `fileName` | String | Original file name |
| `mimeType` | String | File MIME type |
| `size` | Long | File size in bytes |
| `md5Checksum` | String | MD5 hash for verification |

---

### Export (`gdrive-export`)

Export Google Workspace documents (Docs, Sheets, Slides) to standard formats.

#### Input Parameters

| Parameter | Type | Required | Scope | Description |
|-----------|------|----------|-------|-------------|
| `fileId` | String | **Yes** | Connector | Google document ID |
| `exportMimeType` | String | **Yes** | Connector | Target format MIME type |

#### Supported Export Formats

| Source | Available Formats |
|--------|-------------------|
| Google Docs | `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (.docx), `text/plain`, `text/html`, `application/rtf` |
| Google Sheets | `application/pdf`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (.xlsx), `text/csv` |
| Google Slides | `application/pdf`, `application/vnd.openxmlformats-officedocument.presentationml.presentation` (.pptx) |
| Google Drawings | `application/pdf`, `image/png`, `image/svg+xml` |

#### Output Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `success` | Boolean | `true` if export completed |
| `errorMessage` | String | Error description (empty on success) |
| `fileContent` | String | Exported content (Base64 encoded) |
| `fileName` | String | Suggested filename with extension |
| `exportedMimeType` | String | Actual exported MIME type |
| `size` | Long | Exported file size |

---

### Create Folder (`gdrive-create-folder`)

Create folders in Google Drive.

#### Input Parameters

| Parameter | Type | Required | Scope | Description |
|-----------|------|----------|-------|-------------|
| `folderName` | String | **Yes** | Connector | Name for the new folder |
| `parentFolderId` | String | No | Process | Parent folder ID (root if omitted) |
| `description` | String | No | Connector | Folder description |
| `customProperties` | Map | No | Connector | Custom app properties |

#### Output Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `success` | Boolean | `true` if folder created |
| `errorMessage` | String | Error description |
| `folderId` | String | New folder ID |
| `folderName` | String | Folder name |
| `webViewLink` | String | URL to open folder |

---

### Delete (`gdrive-delete`)

Delete or move files to trash.

#### Input Parameters

| Parameter | Type | Required | Scope | Description |
|-----------|------|----------|-------|-------------|
| `fileId` | String | **Yes** | Connector | File or folder ID to delete |
| `permanent` | Boolean | No | Connector | `true` = permanent delete, `false` = move to trash (default) |

#### Output Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `success` | Boolean | `true` if deleted |
| `errorMessage` | String | Error description |

---

### Move (`gdrive-move`)

Move files between folders.

#### Input Parameters

| Parameter | Type | Required | Scope | Description |
|-----------|------|----------|-------|-------------|
| `fileId` | String | **Yes** | Connector | File ID to move |
| `destinationFolderId` | String | **Yes** | Connector | Target folder ID |
| `removeFromCurrentParent` | Boolean | No | Connector | Remove from current location (default: `true`) |

#### Output Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `success` | Boolean | `true` if moved |
| `errorMessage` | String | Error description |
| `fileId` | String | Moved file ID |
| `newParents` | String | Comma-separated parent IDs |

---

### Copy (`gdrive-copy`)

Copy files within Google Drive.

#### Input Parameters

| Parameter | Type | Required | Scope | Description |
|-----------|------|----------|-------|-------------|
| `sourceFileId` | String | **Yes** | Connector | Source file ID |
| `newName` | String | No | Connector | Name for the copy |
| `destinationFolderId` | String | No | Connector | Target folder (same location if omitted) |

#### Output Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `success` | Boolean | `true` if copied |
| `errorMessage` | String | Error description |
| `newFileId` | String | New file ID |
| `fileName` | String | Copy file name |
| `webViewLink` | String | URL to view copy |

---

### Search (`gdrive-search`)

Search for files with powerful query filters.

#### Input Parameters

| Parameter | Type | Required | Scope | Description |
|-----------|------|----------|-------|-------------|
| `query` | String | No* | Connector | Raw Google Drive query string |
| `nameContains` | String | No* | Connector | Filter by name contains |
| `mimeType` | String | No* | Connector | Filter by MIME type |
| `parentFolderId` | String | No* | Process | Filter by parent folder |
| `fullTextSearch` | String | No* | Connector | Full-text content search |
| `modifiedAfter` | String | No | Connector | Modified after (RFC 3339 date) |
| `modifiedBefore` | String | No | Connector | Modified before (RFC 3339 date) |
| `includeTrashed` | Boolean | No | Connector | Include trashed files (default: `false`) |
| `maxResults` | Integer | No | Connector | Maximum results (default: 100) |
| `orderBy` | String | No | Connector | Sort order (default: `modifiedTime desc`) |

*At least one search criterion is required.

#### Output Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `success` | Boolean | `true` if search completed |
| `errorMessage` | String | Error description |
| `files` | List<Map> | List of file metadata objects |
| `totalResults` | Integer | Number of results returned |
| `nextPageToken` | String | Token for pagination |

#### Query Examples

```groovy
// Find all PDFs in a folder
parentFolderId: "1ABC123"
mimeType: "application/pdf"

// Full-text search
fullTextSearch: "quarterly report 2024"

// Raw query (advanced)
query: "name contains 'invoice' and modifiedTime > '2024-01-01T00:00:00'"
```

---

## Parameter Scopes

Understanding parameter scopes helps you configure connectors efficiently.

| Scope | Level | Frequency | Examples |
|-------|-------|-----------|----------|
| **Project/Runtime** | Global | Rarely changes | `serviceAccountJson`, `connectTimeout`, `readTimeout` |
| **Process** | Per-process definition | Occasionally | `parentFolderId` (default folder for a process) |
| **Connector** | Per-execution | Every time | `fileId`, `fileName`, `fileContent`, `query` |

### Recommendation

- Configure **authentication** at Project/Runtime level (shared across all connectors)
- Configure **default folders** at Process level (per-process context)
- Configure **operation data** at Connector level (dynamic, per-task)

---

## Bonita Studio Configuration

### Step-by-Step: Adding the Upload Connector

1. **Open your process** in Bonita Studio
2. **Select a task** → Right-click → **Add** → **Connector in...**
3. **Choose** "Google Drive" category → "gdrive-upload"
4. **Page 1 - Service Account:**
   - Paste your Service Account JSON (the actual content, not a file path)
   - Optionally set the user to impersonate
5. **Page 2 - OAuth (skip if using Service Account)**
6. **Page 3 - Upload Details:**
   - File Name: Use an expression like `"Invoice_" + invoiceId + ".pdf"`
   - File Content: Click the pencil icon → Edit Expression:
     ```groovy
     import org.bonitasoft.engine.bpm.document.Document
     
     Document doc = apiAccessor.getProcessAPI().getLastDocument(processInstanceId, "myDocument")
     byte[] content = apiAccessor.getProcessAPI().getDocumentContent(doc.getContentStorageId())
     return content.encodeBase64().toString()
     ```
7. **Page 4 - Destination:**
   - Set the parent folder ID (find it in the Google Drive URL)
8. **Outputs tab:**
   - Map `fileId` to a process variable to use in subsequent connectors

### Converting Bonita Documents to Base64

```groovy
// Method 1: From a document variable
import org.bonitasoft.engine.bpm.document.Document

Document doc = apiAccessor.getProcessAPI().getLastDocument(processInstanceId, "documentName")
byte[] content = apiAccessor.getProcessAPI().getDocumentContent(doc.getContentStorageId())
return content.encodeBase64().toString()

// Method 2: From a document list (first document)
List<Document> docs = apiAccessor.getProcessAPI().getDocumentList(processInstanceId, "documentList", 0, 1)
if (!docs.isEmpty()) {
    byte[] content = apiAccessor.getProcessAPI().getDocumentContent(docs[0].getContentStorageId())
    return content.encodeBase64().toString()
}
return null
```

### Creating a Bonita Document from Downloaded Content

After downloading a file, create a Bonita document in a subsequent operation:

```groovy
// In an Operation on a task, set document variable
import org.bonitasoft.engine.bpm.document.DocumentValue

byte[] content = Base64.getDecoder().decode(fileContent) // from connector output
new DocumentValue(content, mimeType, fileName)
```

---

## Rate Limits

Google Drive API has quota limits that the connector handles automatically with retry logic.

### Default Quotas

| Quota | Limit | Scope |
|-------|-------|-------|
| Queries per day | 1,000,000,000 | Per project |
| Queries per 100 seconds per user | 1,000 | Per user |
| Queries per 100 seconds | 10,000 | Per project |
| Upload bandwidth | 750 GB/day | Per user |
| Download bandwidth | 10 GB/day | Per user |

### Connector Behavior

| Scenario | HTTP Code | Connector Response |
|----------|-----------|-------------------|
| User rate limit exceeded | 403 `userRateLimitExceeded` | Retry with backoff (max 5 attempts) |
| Project rate limit exceeded | 403 `rateLimitExceeded` | Retry with backoff (max 5 attempts) |
| Too many requests | 429 | Retry with backoff (max 5 attempts) |
| Daily quota exceeded | 403 `dailyLimitExceeded` | Fail (no retry - wait 24h) |

### Best Practices for High-Volume Processes

1. **Batch operations:** Group multiple files into fewer connector calls
2. **Use Search wisely:** Cache search results instead of repeated queries
3. **Stagger execution:** Avoid starting many process instances simultaneously
4. **Monitor quotas:** Check [Google Cloud Console → APIs → Drive API → Quotas](https://console.cloud.google.com/apis/api/drive.googleapis.com/quotas)
5. **Request increase:** For production workloads, request a quota increase from Google

### Retry Timing

The connector uses exponential backoff with jitter:

| Attempt | Base Delay | With Jitter (±20%) |
|---------|------------|-------------------|
| 1 | 1s | 0.8s - 1.2s |
| 2 | 2s | 1.6s - 2.4s |
| 3 | 4s | 3.2s - 4.8s |
| 4 | 8s | 6.4s - 9.6s |
| 5 | 16s | 12.8s - 19.2s |

Total maximum wait time: ~40 seconds before final failure.

---

## BPM Use Cases

### Document Management Workflow

```
[Start] → [Create case folder] → [Upload documents] → [Review] → [Archive approved]
                                                          ↓
                                              [Delete rejected]
```

1. **Create Folder:** Create a case-specific folder when process starts
2. **Upload:** Store incoming documents during data collection tasks
3. **Search:** Find related documents for review tasks
4. **Move:** Archive approved documents to final location
5. **Delete:** Clean up rejected/temporary files

### Contract Approval Process

```groovy
// Step 1: Upload draft contract
gdrive-upload:
  fileName: "Contract_${contractId}_DRAFT.docx"
  fileContent: ${contractDocument}
  parentFolderId: ${draftsFolderId}

// Step 2: After approval, move to approved folder
gdrive-move:
  fileId: ${uploadedFileId}
  destinationFolderId: ${approvedContractsFolderId}

// Step 3: Generate PDF for distribution
gdrive-export:
  fileId: ${uploadedFileId}
  exportMimeType: "application/pdf"
```

### Invoice Processing

```groovy
// Search for unprocessed invoices
gdrive-search:
  parentFolderId: ${inboxFolderId}
  mimeType: "application/pdf"
  nameContains: "invoice"

// After processing, archive
gdrive-move:
  fileId: ${processedInvoiceId}
  destinationFolderId: ${archiveFolderId}
```

---

## Error Handling

### HTTP Status Mapping

| Status | Category | Connector Behavior | BPM Impact |
|--------|----------|-------------------|------------|
| 400 | Bad Request | Fail immediately with validation error | Task stays active, user fixes input |
| 401 | Unauthorized | Fail with auth error | Check credentials configuration |
| 403 | Rate Limit | Retry with exponential backoff (max 5) | Transparent retry |
| 403 | Permission | Fail with permission error | Escalate to admin |
| 404 | Not Found | Fail with "not found" error | Process handles gracefully |
| 429 | Too Many Requests | Retry with exponential backoff (max 5) | Transparent retry |
| 5xx | Server Error | Retry with exponential backoff (max 5) | Transparent retry |

### Retry Policy

- **Max retries:** 5
- **Backoff:** Exponential (1s, 2s, 4s, 8s, 16s) with 20% jitter
- **Retryable errors:** Rate limits (403/429), server errors (5xx)
- **Non-retryable:** Validation errors (400), auth errors (401), not found (404)

### Output Pattern

Every connector sets these outputs:

```groovy
// Success
success: true
errorMessage: ""

// Failure
success: false
errorMessage: "[HTTP 403 - rateLimitExceeded] Rate limit exceeded. The request will be retried automatically."
```

---

## Testing

The project has a multi-level testing strategy: unit tests, property-based tests, definition tests, and integration tests against the real Google Drive API.

### Test Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Level 1: Unit Tests (*Test.java) — Surefire                   │
│  Validation, metadata, configuration, exception handling        │
│  No network, no credentials needed                              │
├─────────────────────────────────────────────────────────────────┤
│  Level 2: Property-Based Tests (*PropertyTest.java) — jqwik    │
│  Fuzzing with 100+ random inputs                                │
│  No network, no credentials needed                              │
├─────────────────────────────────────────────────────────────────┤
│  Level 3: Definition Tests (*DefinitionTest.java) — Surefire   │
│  XML .def schema validation, widget/input/output checks         │
│  No network, no credentials needed                              │
├─────────────────────────────────────────────────────────────────┤
│  Level 4: Integration Tests (*IT.java) — Failsafe              │
│  Full lifecycle against real Google Drive API                    │
│  Requires: Service Account JSON + Shared Drive folder           │
└─────────────────────────────────────────────────────────────────┘
```

### Run Unit Tests (Levels 1-3)

```bash
# All unit tests across all modules
mvn test

# Single module
mvn test -pl bonita-connector-gdrive-upload
```

### Run Integration Tests (Level 4)

Integration tests execute the **full Bonita connector lifecycle** (VALIDATE → CONNECT → EXECUTE → DISCONNECT) against the real Google Drive API.

#### Prerequisites

1. **Service Account JSON file** — A Google Cloud Service Account key with Drive API enabled
2. **Shared Drive folder** — Service Accounts have no personal storage quota, so files must be uploaded to a Shared Drive folder where the SA has Editor access

#### Configuration

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `GDRIVE_SA_JSON_PATH` | `GDRIVE_SA_JSON_PATH` | `C:/BonitaStudioSubscription-2025.2-u3/bonitasoft-conectores-*.json` | Path to Service Account JSON file |
| `GDRIVE_SHARED_FOLDER_ID` | `GDRIVE_SHARED_FOLDER_ID` | — | Shared Drive folder ID where the SA can write |

#### Run

```bash
# ITs run automatically during mvn install/verify.
# Without SA configured, they are SKIPPED (not failed).
mvn clean install

# To actually execute ITs against real Google Drive:
mvn clean verify \
  -DGDRIVE_SA_JSON_PATH=/path/to/service-account.json \
  -DGDRIVE_SHARED_FOLDER_ID=1ABC123xyz
```

> **Note:** Integration tests run as part of `mvn install` via Failsafe. If the Service Account JSON is not found, all ITs are **automatically skipped** (not failed) using JUnit 5 `assumeTrue`. This means `mvn install` always succeeds, but ITs execute when infrastructure is available.

#### What the Full Lifecycle IT Tests

The `GDriveFullLifecycleIT` runs 10 ordered tests covering all 8 connectors:

| Order | Test | Connector | Depends On |
|-------|------|-----------|------------|
| 1 | `should_create_folder_in_shared_drive` | CreateFolder | — |
| 2 | `should_upload_file_to_folder` | Upload | #1 |
| 3 | `should_search_and_find_uploaded_file` | Search | #2 |
| 4 | `should_download_file_and_verify_content` | Download | #2 |
| 5 | `should_copy_file` | Copy | #2 |
| 6 | `should_create_subfolder_for_move` | CreateFolder | #1 |
| 7 | `should_move_copied_file_to_subfolder` | Move | #5, #6 |
| 8 | `should_upload_and_convert_to_google_doc` | Upload (convert) | #1 |
| 9 | `should_export_google_doc_to_pdf` | Export | #8 |
| 10 | `should_delete_copied_file_permanently` | Delete | #5 |

The test **automatically cleans up** all created resources (files and folders) in `@AfterAll`, even if tests fail mid-way.

### ConnectorTestToolkit

The `ConnectorTestToolkit` class (in `bonita-connector-gdrive-common`, test scope) provides helpers for testing any GDrive connector through its full lifecycle:

```java
// Build auth inputs from Service Account JSON
var inputs = ConnectorTestToolkit.authInputs();
inputs.put("folderName", "my-test-folder");

// Execute: VALIDATE → CONNECT → EXECUTE → DISCONNECT
ConnectorResult result = ConnectorTestToolkit.execute(
    new GDriveCreateFolderConnector(), inputs);

// Check results
assertThat(result.isSuccess()).isTrue();
String folderId = result.stringOutput("folderId");

// Timing info available
result.printTiming("CreateFolder");
// --- CreateFolder timing ---
//   Validate:   0 ms
//   Connect:    462 ms
//   Execute:    2284 ms
//   Disconnect: 0 ms
//   Total:      2746 ms
```

To use `ConnectorTestToolkit` from another module, add this dependency:

```xml
<dependency>
    <groupId>com.bonitasoft.connectors</groupId>
    <artifactId>bonita-connector-gdrive-common</artifactId>
    <version>${project.version}</version>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
```

### Coverage Report

```bash
mvn jacoco:report
# Open target/site/jacoco/index.html
```

### Mutation Testing

```bash
mvn org.pitest:pitest-maven:mutationCoverage
# Open target/pit-reports/index.html
```

### Quality Targets

| Metric | Minimum | Target | Tool |
|--------|---------|--------|------|
| Line Coverage | 95% | 98% | JaCoCo |
| Branch Coverage | 90% | 95% | JaCoCo |
| Mutation Score | 95% | 98% | Pitest (STRONGER) |

---

## Troubleshooting

### "Authentication failed" / "401 Unauthorized"

1. **Verify JSON content:** Ensure `serviceAccountJson` contains the actual JSON content, not a file path
2. **Check service account status:** Verify the service account is not disabled in Google Cloud Console
3. **Verify API enabled:** Ensure Google Drive API is enabled for your project

### "Permission denied" / "403 Forbidden"

1. **Check file sharing:** Ensure the service account email has access to the file/folder
2. **Domain-wide delegation:** If impersonating users, verify delegation is configured in Google Workspace Admin
3. **Scope permissions:** Verify the service account has the required OAuth scopes

### "storageQuotaExceeded" / "Service Accounts do not have storage quota"

Service Accounts cannot store files in their own Drive (they have no storage quota). Two solutions:

1. **Shared Drives (recommended):** Create a Shared Drive in Google Workspace, add the Service Account as an Editor, and use that folder as `parentFolderId`
2. **Domain-Wide Delegation:** Configure the SA with delegation and set `impersonateUser` to a real user email — files will be stored in that user's Drive

> **Note:** All connectors set `supportsAllDrives=true` internally, so Shared Drive operations work out of the box.

### "File not found" / "404 Not Found"

1. **Verify file ID:** Ensure the file ID is correct and the file exists
2. **Check permissions:** The service account may not have access to the file
3. **Trashed files:** The file may be in trash; set `includeTrashed: true` in search

### "Rate limit exceeded" / "403 userRateLimitExceeded"

The connector handles this automatically with retries. If persistent:

1. **Check quotas:** Review [Google Cloud Console → APIs → Drive API → Quotas](https://console.cloud.google.com/apis/api/drive.googleapis.com/quotas)
2. **Request increase:** Contact Google to increase your quota
3. **Reduce frequency:** Add delays between connector calls in your process

### "Cannot download Google Workspace document"

Google Docs, Sheets, and Slides cannot be downloaded directly. Use the **Export** connector instead:

```groovy
// Wrong: Download Google Doc
gdrive-download:
  fileId: "1ABC..."  // This will fail!

// Correct: Export Google Doc to PDF
gdrive-export:
  fileId: "1ABC..."
  exportMimeType: "application/pdf"
```

---

## Development

### Project Structure

```
bonita-connector-gdrive/
├── pom.xml                              # Parent POM (10 modules)
├── README.md                            # This file
├── GOOGLE_CLOUD_SETUP.md               # Service Account setup guide
├── CLAUDE.md                            # AI assistant instructions
│
├── bonita-connector-gdrive-common/      # Shared components
│   ├── src/main/java/.../
│   │   ├── AbstractGDriveConnector.java # Base connector (4-phase lifecycle)
│   │   ├── GDriveClient.java           # API client (retry, auth)
│   │   ├── GDriveConfiguration.java    # Configuration record (Java 17)
│   │   ├── GDriveException.java        # Exception hierarchy
│   │   └── model/FileMetadata.java     # File metadata model
│   └── src/test/java/.../
│       ├── ConnectorTestToolkit.java    # Test toolkit (shared via test-jar)
│       ├── GDriveClientTest.java        # Retry logic, config tests
│       ├── GDriveConfigurationTest.java
│       ├── GDriveExceptionTest.java
│       └── model/FileMetadataTest.java
│
├── bonita-connector-gdrive-upload/      # Upload operation
│   ├── src/main/java/.../GDriveUploadConnector.java
│   ├── src/main/resources/
│   │   ├── gdrive-upload.def            # Connector definition XML
│   │   ├── gdrive-upload.properties     # i18n labels
│   │   └── gdrive.png                   # Google Drive icon (16x16)
│   ├── src/main/resources-filtered/
│   │   └── gdrive-upload.impl           # Implementation descriptor (Maven-filtered)
│   └── src/test/java/.../
│       ├── GDriveUploadConnectorTest.java           # Validation tests
│       ├── GDriveUploadConnectorDefinitionTest.java # .def XML tests
│       └── GDriveUploadConnectorPropertyTest.java   # Property-based (jqwik)
│
├── bonita-connector-gdrive-download/    # (same pattern for all 8 operations)
├── bonita-connector-gdrive-export/
├── bonita-connector-gdrive-create-folder/
├── bonita-connector-gdrive-delete/
├── bonita-connector-gdrive-move/
├── bonita-connector-gdrive-copy/
├── bonita-connector-gdrive-search/
│   └── src/test/java/.../
│       ├── GDriveSearchConnectorTest.java
│       └── GDriveSearchConnectorIT.java # Integration test (real API)
│
└── bonita-connector-gdrive-all/         # Aggregator (single fat JAR)
    ├── pom.xml                          # Shade plugin + Failsafe
    └── src/test/java/.../
        └── GDriveFullLifecycleIT.java   # Full lifecycle IT (6 operations)
```

### Connector Lifecycle

Every connector follows the 4-phase Bonita lifecycle:

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   VALIDATE   │ →  │   CONNECT    │ →  │   EXECUTE    │ →  │  DISCONNECT  │
│              │    │              │    │              │    │              │
│ Check inputs │    │ Create client│    │ Call API     │    │ Close client │
│ Fail fast    │    │ Authenticate │    │ Set outputs  │    │ Release      │
└──────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
```

### Adding a New Operation

1. Create new module: `bonita-connector-gdrive-{operation}/`
2. Create connector class extending `AbstractGDriveConnector`
3. Implement `validateOperationInputs()` and `executeOperation()`
4. Create `.def` and `.impl` files in resources
5. Add tests (unit, property-based, integration)

---

## References

| Resource | URL |
|----------|-----|
| Google Drive API Docs | https://developers.google.com/drive/api/v3/reference |
| Google Auth Library | https://github.com/googleapis/google-auth-library-java |
| Bonita Connector Tutorial | https://documentation.bonitasoft.com/bonita/latest/process/connector-archetype-tutorial |
| Service Account Setup | See `GOOGLE_CLOUD_SETUP.md` |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-03 | Initial release: 8 operations |

---

## License

Proprietary — Bonitasoft Professional Services

---

*Generated by Bonita Connector Generator Toolkit v1.1.0*

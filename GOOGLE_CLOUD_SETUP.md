# Google Cloud Setup Guide

Step-by-step guide to configure Google Cloud for the Bonita Google Drive Connector.

## Prerequisites

- Google Cloud account
- Access to create projects and enable APIs
- For Google Workspace: Admin access for domain-wide delegation

## Step 1: Create or Select a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click **Select a project** → **New Project**
3. Enter a project name (e.g., "Bonita Integration")
4. Click **Create**

## Step 2: Enable Google Drive API

1. In the Cloud Console, go to **APIs & Services → Library**
2. Search for "Google Drive API"
3. Click **Enable**

## Step 3: Create Service Account

1. Go to **APIs & Services → Credentials**
2. Click **Create Credentials → Service Account**
3. Fill in:
   - **Name**: `bonita-drive-connector`
   - **Description**: "Service account for Bonita BPM connector"
4. Click **Create and Continue**
5. (Optional) Grant roles if needed
6. Click **Done**

## Step 4: Generate JSON Key

1. In the Service Accounts list, click on your new service account
2. Go to **Keys** tab
3. Click **Add Key → Create new key**
4. Select **JSON** format
5. Click **Create**
6. **Save the downloaded JSON file securely**

## Step 5: Configure Access

### Option A: Share Folders Directly (Simplest)

1. Open Google Drive
2. Right-click the folder you want to access
3. Click **Share**
4. Add the service account email (from the JSON file: `client_email`)
5. Set permission level (Viewer, Commenter, or Editor)

### Option B: Domain-Wide Delegation (Google Workspace)

For accessing any user's files in your organization:

1. Go to [Google Workspace Admin Console](https://admin.google.com/)
2. Navigate to **Security → API Controls → Domain-wide Delegation**
3. Click **Add new**
4. Enter the **Client ID** from your service account
5. Add OAuth scopes:
   ```
   https://www.googleapis.com/auth/drive.file
   https://www.googleapis.com/auth/drive
   https://www.googleapis.com/auth/drive.readonly
   ```
6. Click **Authorize**

## Step 6: Configure in Bonita

### Using Service Account JSON

In your connector configuration:

```
serviceAccountJson: <paste entire JSON content here>
```

**Important**: Paste the entire JSON file content, not a file path.

### With Domain-Wide Delegation

```
serviceAccountJson: <paste entire JSON content here>
impersonateUser: user@yourdomain.com
```

## OAuth Scopes Reference

| Scope | Access Level |
|-------|-------------|
| `drive.file` | Files created/opened by the app only (recommended) |
| `drive` | Full read/write access to all files |
| `drive.readonly` | Read-only access to all files |

## Security Best Practices

1. **Store credentials securely**: Never commit JSON keys to version control
2. **Use least privilege**: Start with `drive.file` scope
3. **Rotate keys periodically**: Create new keys and delete old ones
4. **Monitor usage**: Check API quotas in Cloud Console
5. **Limit sharing**: Only share necessary folders with service account

## Troubleshooting

### "Permission denied" errors

1. Verify the service account has access to the file/folder
2. Check if domain-wide delegation is configured correctly
3. Verify the impersonateUser email exists

### "Invalid credentials" errors

1. Ensure you're using the JSON content, not a file path
2. Check the JSON is valid and complete
3. Verify the service account is not disabled

### Rate limit errors

The connector handles these automatically with retry, but if persistent:

1. Check [Quotas page](https://console.cloud.google.com/apis/api/drive.googleapis.com/quotas)
2. Request quota increase if needed

## API Quotas

| Limit | Default Value |
|-------|---------------|
| Queries per day | 1,000,000,000 |
| Queries per 100 seconds per user | 20,000 |
| Uploads per day | 750 GB |

---

*Bonita Connector Generator Toolkit — Google Cloud Setup Guide*

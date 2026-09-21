package com.spendlocker.google;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.spendlocker.db.DatabaseManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Handles "Sign in with Google" (installed-app OAuth flow) and Drive read/write access.
 *
 * Setup required once per user, outside this app:
 *   1. Create a Google Cloud project, enable the "Google Drive API".
 *   2. Create an OAuth 2.0 Client ID of type "Desktop app".
 *   3. Download the client secret JSON and save it as:
 *        ~/.spendlocker/google-client-secret.json
 * This app never bundles its own client secret; it only drives the flow with the file above.
 */
public class GoogleDriveService {

    private static final GoogleDriveService INSTANCE = new GoogleDriveService();

    public static GoogleDriveService getInstance() {
        return INSTANCE;
    }

    private static final String APPLICATION_NAME = "SpendLocker";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    // mail.google.com is the Gmail IMAP/SMTP scope — requesting it up front lets Email Attachment
    // Sync reuse this same "Sign in with Google" session instead of asking for a separate password.
    private static final List<String> SCOPES = List.of(DriveScopes.DRIVE_FILE, "https://mail.google.com/");

    private static final Path CLIENT_SECRET_PATH =
            Path.of(DatabaseManager.vaultDirectory(), "google-client-secret.json");
    private static final Path TOKENS_DIRECTORY_PATH =
            Path.of(DatabaseManager.vaultDirectory(), "google-tokens");

    private Drive drive;
    private com.google.api.client.auth.oauth2.Credential credential;
    private String signedInEmail;

    public static boolean isClientSecretConfigured() {
        return Files.exists(CLIENT_SECRET_PATH);
    }

    public static Path clientSecretPath() {
        return CLIENT_SECRET_PATH;
    }

    /** Runs the OAuth consent flow (opens the system browser) and returns the signed-in email. */
    public String signIn() throws IOException, GeneralSecurityException {
        if (!Files.exists(CLIENT_SECRET_PATH)) {
            throw new FileNotFoundException(
                    "Missing Google OAuth client secret. Place your Desktop-app client_secret.json at: "
                            + CLIENT_SECRET_PATH);
        }

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleClientSecrets clientSecrets;
        try (InputStream in = Files.newInputStream(CLIENT_SECRET_PATH)) {
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        }

        Files.createDirectories(TOKENS_DIRECTORY_PATH);
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(TOKENS_DIRECTORY_PATH.toFile()))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        drive = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        signedInEmail = drive.about().get().setFields("user").execute().getUser().getEmailAddress();
        return signedInEmail;
    }

    /** The email address of the connected Google account, or null if not signed in. */
    public String signedInEmail() {
        return signedInEmail;
    }

    /**
     * A fresh OAuth2 access token for the connected Google account, scoped for Gmail IMAP/SMTP
     * (see {@code https://mail.google.com/} in SCOPES). Lets Email Attachment Sync authenticate
     * to Gmail via IMAP XOAUTH2 instead of asking for a separate app password.
     */
    public String getAccessTokenForImap() throws IOException {
        requireSignedIn();
        Long expiresIn = credential.getExpiresInSeconds();
        if (expiresIn == null || expiresIn < 300) {
            credential.refreshToken();
        }
        return credential.getAccessToken();
    }

    public void signOut() throws IOException {
        drive = null;
        credential = null;
        signedInEmail = null;
        if (Files.exists(TOKENS_DIRECTORY_PATH)) {
            try (var paths = Files.walk(TOKENS_DIRECTORY_PATH)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {
                        // best-effort cleanup of cached tokens
                    }
                });
            }
        }
    }

    public boolean isSignedIn() {
        return drive != null;
    }

    public List<File> listVaultFiles() throws IOException {
        requireSignedIn();
        FileList result = drive.files().list()
                .setFields("files(id, name, modifiedTime, size)")
                .setSpaces("drive")
                .setPageSize(100)
                .setOrderBy("modifiedTime desc")
                .execute();
        return result.getFiles();
    }

    /** Server-side name search (case-insensitive substring), so it scales beyond one page of files. */
    public List<File> searchFilesByName(String query) throws IOException {
        requireSignedIn();
        String escaped = query.replace("'", "\\'");
        FileList result = drive.files().list()
                .setQ("name contains '" + escaped + "' and trashed = false")
                .setFields("files(id, name, modifiedTime, size)")
                .setSpaces("drive")
                .setPageSize(100)
                .setOrderBy("modifiedTime desc")
                .execute();
        return result.getFiles();
    }

    public File uploadFile(java.io.File localFile, String mimeType) throws IOException {
        requireSignedIn();
        File metadata = new File();
        metadata.setName(localFile.getName());
        return drive.files()
                .create(metadata, new com.google.api.client.http.FileContent(mimeType, localFile))
                .setFields("id, name, webViewLink")
                .execute();
    }

    public void downloadFile(String fileId, java.io.File destination) throws IOException {
        requireSignedIn();
        try (var out = Files.newOutputStream(destination.toPath())) {
            drive.files().get(fileId).executeMediaAndDownloadTo(out);
        }
    }

    public void deleteFile(String fileId) throws IOException {
        requireSignedIn();
        drive.files().delete(fileId).execute();
    }

    private void requireSignedIn() {
        if (drive == null) {
            throw new IllegalStateException("Not signed in to Google Drive yet. Call signIn() first.");
        }
    }
}

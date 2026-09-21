package com.spendlocker.email;

import com.spendlocker.dao.DocumentDao;
import com.spendlocker.db.DatabaseManager;
import com.spendlocker.model.Document;
import com.spendlocker.model.FileType;
import com.spendlocker.model.UploadSource;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Connects to an IMAP mailbox and pulls attachments into the document vault.
 * Credentials are supplied by the caller (Settings screen) and never persisted in plaintext
 * outside the encrypted vault.
 */
public class EmailSyncService {

    private final DocumentDao documentDao = new DocumentDao();

    public List<Document> syncAttachments(String host, int port, String user, String password,
                                           String folderName, boolean useSsl,
                                           String subjectContains, String fromContains) throws MessagingException, IOException {
        return syncAttachments(host, port, user, password, folderName, useSsl, subjectContains, fromContains, false);
    }

    /**
     * @param oauthAccessToken when true, {@code password} is treated as a Google OAuth2 access
     *                         token and authentication is done via IMAP XOAUTH2 instead of a
     *                         plain password — lets Gmail sync reuse the existing "Sign in with
     *                         Google" session instead of requiring a separate app password.
     */
    public List<Document> syncAttachments(String host, int port, String user, String password,
                                           String folderName, boolean useSsl,
                                           String subjectContains, String fromContains,
                                           boolean oauthAccessToken) throws MessagingException, IOException {
        String protocolPrefix = "mail.imap" + (useSsl ? "s" : "");
        Properties props = new Properties();
        props.put("mail.store.protocol", "imap" + (useSsl ? "s" : ""));
        props.put(protocolPrefix + ".host", host);
        props.put(protocolPrefix + ".port", String.valueOf(port));
        props.put(protocolPrefix + ".ssl.enable", String.valueOf(useSsl));
        // Fail fast (10s) instead of hanging indefinitely on a network/firewall problem.
        props.put(protocolPrefix + ".connectiontimeout", "10000");
        props.put(protocolPrefix + ".timeout", "10000");
        if (oauthAccessToken) {
            props.put(protocolPrefix + ".sasl.enable", "true");
            props.put(protocolPrefix + ".sasl.mechanisms", "XOAUTH2");
            props.put(protocolPrefix + ".auth.login.disable", "true");
            props.put(protocolPrefix + ".auth.plain.disable", "true");
        }

        Session session = Session.getInstance(props);
        List<Document> imported = new ArrayList<>();

        try (Store store = session.getStore(useSsl ? "imaps" : "imap")) {
            store.connect(host, port, user, password);
            try (Folder folder = store.getFolder(folderName != null ? folderName : "INBOX")) {
                folder.open(Folder.READ_ONLY);
                Message[] messages = matchingMessages(folder, subjectContains, fromContains);
                for (Message message : messages) {
                    imported.addAll(extractAttachments(message));
                }
            }
        }
        return imported;
    }

    private Message[] matchingMessages(Folder folder, String subjectContains, String fromContains) throws MessagingException {
        List<SearchTerm> terms = new ArrayList<>();
        if (subjectContains != null && !subjectContains.isBlank()) {
            terms.add(new SubjectTerm(subjectContains.trim()));
        }
        if (fromContains != null && !fromContains.isBlank()) {
            terms.add(new FromStringTerm(fromContains.trim()));
        }
        if (terms.isEmpty()) {
            return folder.getMessages();
        }
        SearchTerm combined = terms.size() == 1 ? terms.get(0) : new AndTerm(terms.toArray(new SearchTerm[0]));
        return folder.search(combined);
    }

    private List<Document> extractAttachments(Message message) throws MessagingException, IOException {
        List<Document> saved = new ArrayList<>();
        if (!(message.getContent() instanceof MimeMultipart multipart)) {
            return saved;
        }
        Path attachmentsDir = Path.of(DatabaseManager.vaultDirectory(), "attachments");
        Files.createDirectories(attachmentsDir);
        collectAttachments(multipart, attachmentsDir, saved);
        return saved;
    }

    /**
     * Recurses into nested multiparts (e.g. multipart/mixed wrapping a multipart/alternative
     * body plus attachments — a very common real-world structure). A part counts as an
     * attachment if it has a filename at all: many real senders (bank/receipt emails,
     * forwarded mail) never set Content-Disposition to exactly "attachment", or leave it
     * null, so requiring that exact value silently missed real attachments.
     */
    private void collectAttachments(Multipart multipart, Path attachmentsDir, List<Document> saved)
            throws MessagingException, IOException {
        for (int i = 0; i < multipart.getCount(); i++) {
            Part part = multipart.getBodyPart(i);
            Object content = part.getContent();
            if (content instanceof Multipart nested) {
                collectAttachments(nested, attachmentsDir, saved);
                continue;
            }
            String fileName = part.getFileName();
            if (fileName == null || fileName.isBlank()) {
                continue;
            }
            File target = attachmentsDir.resolve(System.currentTimeMillis() + "_" + fileName).toFile();
            try (var in = part.getInputStream()) {
                Files.copy(in, target.toPath());
            }

            Document doc = new Document();
            doc.setFileName(fileName);
            doc.setFilePath(target.getAbsolutePath());
            doc.setFileType(FileType.fromExtension(fileName));
            doc.setFileSizeBytes(target.length());
            doc.setUploadSource(UploadSource.EMAIL);
            saved.add(documentDao.insert(doc));
        }
    }
}

package com.spendlocker.db;

import com.spendlocker.security.KeyWrapStore;
import com.spendlocker.security.RecoveryKeyGenerator;

import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Properties;

/**
 * Owns the single SQLCipher-encrypted SQLite connection for the vault.
 *
 * The actual SQLCipher key is a random "vault key" that never changes. It is stored, wrapped
 * twice (AES-GCM, key derived via PBKDF2), in a small sidecar file: once under the master
 * password, once under a recovery key. Either secret unwraps the same vault key. Changing the
 * master password only re-wraps the "master" slot — the recovery key keeps working forever
 * without needing to be regenerated, and no SQLCipher rekey operation is ever needed for a
 * plain password change.
 */
public class DatabaseManager {

    private static final DatabaseManager INSTANCE = new DatabaseManager();
    private static final SecureRandom RANDOM = new SecureRandom();

    // Overridable via -Dspendlocker.vault.dir=... so tests never touch the real vault.
    private static final String VAULT_DIR = System.getProperty("spendlocker.vault.dir",
            System.getProperty("user.home") + File.separator + ".spendlocker");
    private static final String DB_FILE = VAULT_DIR + File.separator + "vault.db";
    private static final String KEYS_FILE = VAULT_DIR + File.separator + "vault.keys";

    private Connection connection;
    private String vaultKey;
    private String pendingRecoveryKeyToShow;

    private DatabaseManager() {
    }

    public static DatabaseManager getInstance() {
        return INSTANCE;
    }

    public static boolean vaultExists() {
        return new File(DB_FILE).exists();
    }

    public static String vaultDirectory() {
        return VAULT_DIR;
    }

    /**
     * The SQLCipher key MUST be supplied as a connection property (the driver applies it
     * during connection construction, before its own default-pragma setup runs). Supplying
     * it afterwards via a plain "PRAGMA key = ..." statement is too late: on any *existing*
     * non-empty encrypted file, the driver's own setup already tries to touch page 1 first
     * and fails with "file is not a database" — even with the correct key, on every single
     * reopen.
     */
    private static Connection connect(String key) throws SQLException {
        Properties props = new Properties();
        props.setProperty("password", key);
        return DriverManager.getConnection("jdbc:sqlite:" + DB_FILE, props);
    }

    /** Opens (or creates) the vault with the given master password. */
    public synchronized Connection open(String masterPassword) throws VaultLockedException {
        new File(VAULT_DIR).mkdirs();
        pendingRecoveryKeyToShow = null;

        if (!vaultExists()) {
            return createVault(masterPassword);
        }
        if (!KeyWrapStore.exists(KEYS_FILE)) {
            return openLegacyVaultAndUpgrade(masterPassword);
        }
        String resolvedVaultKey = KeyWrapStore.unwrapMaster(KEYS_FILE, masterPassword);
        if (resolvedVaultKey == null) {
            throw new VaultLockedException("Incorrect master password.", null);
        }
        return openConnectionWithKey(resolvedVaultKey);
    }

    /** Opens the vault using the recovery key instead of the master password. */
    public synchronized Connection openWithRecoveryKey(String recoveryKey) throws VaultLockedException {
        if (!vaultExists() || !KeyWrapStore.exists(KEYS_FILE)) {
            throw new VaultLockedException("This vault doesn't have a recovery key set up.", null);
        }
        String resolvedVaultKey = KeyWrapStore.unwrapRecovery(KEYS_FILE, recoveryKey);
        if (resolvedVaultKey == null) {
            throw new VaultLockedException("Incorrect recovery key.", null);
        }
        return openConnectionWithKey(resolvedVaultKey);
    }

    /** Non-null exactly once, right after a vault creation or legacy-vault upgrade generates a new recovery key. */
    public synchronized String consumePendingRecoveryKey() {
        String key = pendingRecoveryKeyToShow;
        pendingRecoveryKeyToShow = null;
        return key;
    }

    private Connection createVault(String masterPassword) throws VaultLockedException {
        String newVaultKey = randomVaultKey();
        String recoveryKey = RecoveryKeyGenerator.generate();
        try {
            Connection c = connect(newVaultKey);
            connection = c;
            runSchemaInit(c);
            KeyWrapStore.init(KEYS_FILE, newVaultKey, masterPassword, recoveryKey);
            this.vaultKey = newVaultKey;
            this.pendingRecoveryKeyToShow = recoveryKey;
            return c;
        } catch (SQLException e) {
            close();
            throw new VaultLockedException("Failed to create vault.", e);
        } catch (java.io.IOException e) {
            close();
            throw new VaultLockedException("Failed to save vault keys.", e);
        }
    }

    /** Vaults created before the recovery-key feature used the password directly as the SQLCipher key. */
    private Connection openLegacyVaultAndUpgrade(String masterPassword) throws VaultLockedException {
        try {
            Connection c = connect(masterPassword);
            try (Statement statement = c.createStatement()) {
                statement.execute("SELECT count(*) FROM sqlite_master");
            }
            connection = c;

            String newVaultKey = randomVaultKey();
            try (Statement statement = c.createStatement()) {
                statement.execute("PRAGMA rekey = '" + newVaultKey.replace("'", "''") + "'");
            }
            String recoveryKey = RecoveryKeyGenerator.generate();
            KeyWrapStore.init(KEYS_FILE, newVaultKey, masterPassword, recoveryKey);
            this.vaultKey = newVaultKey;
            this.pendingRecoveryKeyToShow = recoveryKey;

            runSchemaInit(c);
            return c;
        } catch (SQLException e) {
            close();
            throw new VaultLockedException("Incorrect master password or corrupted vault.", e);
        } catch (java.io.IOException e) {
            close();
            throw new VaultLockedException("Failed to save vault keys.", e);
        }
    }

    private Connection openConnectionWithKey(String resolvedVaultKey) throws VaultLockedException {
        try {
            Connection c = connect(resolvedVaultKey);
            try (Statement statement = c.createStatement()) {
                statement.execute("SELECT count(*) FROM sqlite_master");
            }
            connection = c;
            this.vaultKey = resolvedVaultKey;
            runSchemaInit(c);
            return c;
        } catch (SQLException e) {
            close();
            throw new VaultLockedException("Incorrect master password or corrupted vault.", e);
        }
    }

    /**
     * Schema creation/migration runs as a single transaction: if the process dies mid-way
     * (crash, kill -9, power loss), SQLite's rollback journal restores the file to its
     * pre-transaction state on the next open, instead of leaving a half-written vault.
     */
    private void runSchemaInit(Connection c) throws VaultLockedException {
        try (Statement statement = c.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            throw new VaultLockedException("Failed to configure vault.", e);
        }
        try {
            c.setAutoCommit(false);
            try {
                SchemaInitializer.initialize(c);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            close();
            throw new VaultLockedException("Failed to initialize vault schema.", e);
        }
    }

    private static String randomVaultKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Confirms a master password unlocks the vault, without touching the live session. */
    public synchronized boolean verifyPassphrase(String password) {
        if (!vaultExists()) return false;
        if (!KeyWrapStore.exists(KEYS_FILE)) {
            try (Connection probe = connect(password);
                 Statement statement = probe.createStatement()) {
                statement.execute("SELECT count(*) FROM sqlite_master");
                return true;
            } catch (SQLException e) {
                return false;
            }
        }
        return KeyWrapStore.unwrapMaster(KEYS_FILE, password) != null;
    }

    /**
     * Re-wraps the vault key under a new master password. The vault key itself never changes,
     * so no SQLCipher rekey is needed — this only touches the small sidecar keys file.
     */
    public synchronized void changePassphrase(String newPassword) throws VaultLockedException {
        if (vaultKey == null) {
            throw new VaultLockedException("Vault is not open.", null);
        }
        try {
            KeyWrapStore.rewrapMaster(KEYS_FILE, vaultKey, newPassword);
        } catch (java.io.IOException e) {
            throw new VaultLockedException("Failed to save the new password.", e);
        }
    }

    /** Generates a brand-new recovery key, replacing the old one (e.g. if it was lost). */
    public synchronized String regenerateRecoveryKey() throws VaultLockedException {
        if (vaultKey == null) {
            throw new VaultLockedException("Vault is not open.", null);
        }
        String newRecoveryKey = RecoveryKeyGenerator.generate();
        try {
            KeyWrapStore.rewrapRecovery(KEYS_FILE, vaultKey, newRecoveryKey);
        } catch (java.io.IOException e) {
            throw new VaultLockedException("Failed to save the new recovery key.", e);
        }
        return newRecoveryKey;
    }

    public synchronized Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException("Vault is not open yet. Call open(passphrase) first.");
        }
        return connection;
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // best-effort close on shutdown
            } finally {
                connection = null;
                vaultKey = null;
            }
        }
    }
}

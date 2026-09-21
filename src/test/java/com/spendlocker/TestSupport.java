package com.spendlocker;

import com.spendlocker.db.DatabaseManager;
import com.spendlocker.db.VaultLockedException;

/** Opens the throwaway test vault (see the surefire -Dspendlocker.vault.dir override in pom.xml). */
public final class TestSupport {

    public static final String TEST_PASSWORD = "test-password-123";

    private TestSupport() {
    }

    public static void ensureVaultOpen() {
        try {
            DatabaseManager.getInstance().getConnection();
        } catch (IllegalStateException notOpenYet) {
            try {
                DatabaseManager.getInstance().open(TEST_PASSWORD);
            } catch (VaultLockedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

package com.spendlocker.db;

public class VaultLockedException extends Exception {
    public VaultLockedException(String message, Throwable cause) {
        super(message, cause);
    }
}

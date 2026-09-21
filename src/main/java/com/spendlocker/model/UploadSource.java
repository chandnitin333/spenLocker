package com.spendlocker.model;

public enum UploadSource {
    MANUAL("Manual"),
    EMAIL("Email"),
    EXCEL_IMPORT("Excel Import"),
    GOOGLE_DRIVE("Google Drive");

    private final String dbValue;

    UploadSource(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static UploadSource fromDbValue(String value) {
        for (UploadSource s : values()) {
            if (s.dbValue.equals(value)) return s;
        }
        return MANUAL;
    }

    @Override
    public String toString() {
        return dbValue;
    }
}

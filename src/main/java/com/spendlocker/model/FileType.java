package com.spendlocker.model;

public enum FileType {
    PDF, PNG, JPG, XLSX, DOCX, OTHER;

    public static FileType fromExtension(String fileName) {
        String ext = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase()
                : "";
        try {
            return FileType.valueOf(ext.equals("JPEG") ? "JPG" : ext);
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}

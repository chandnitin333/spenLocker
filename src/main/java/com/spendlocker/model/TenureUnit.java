package com.spendlocker.model;

public enum TenureUnit {
    DAYS("Days"),
    MONTHS("Months"),
    YEARS("Years");

    private final String dbValue;

    TenureUnit(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static TenureUnit fromDbValue(String value) {
        for (TenureUnit unit : values()) {
            if (unit.dbValue.equals(value)) return unit;
        }
        return MONTHS;
    }

    @Override
    public String toString() {
        return dbValue;
    }
}

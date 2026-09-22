package com.spendlocker.model;

public enum Compounding {
    QUARTERLY("Quarterly", 4),
    HALF_YEARLY("Half-Yearly", 2),
    ANNUALLY("Annually", 1),
    SIMPLE("Simple", 0);

    private final String dbValue;
    private final int compoundsPerYear;

    Compounding(String dbValue, int compoundsPerYear) {
        this.dbValue = dbValue;
        this.compoundsPerYear = compoundsPerYear;
    }

    public String dbValue() {
        return dbValue;
    }

    public int compoundsPerYear() {
        return compoundsPerYear;
    }

    public static Compounding fromDbValue(String value) {
        for (Compounding c : values()) {
            if (c.dbValue.equals(value)) return c;
        }
        return QUARTERLY;
    }

    @Override
    public String toString() {
        return dbValue;
    }
}

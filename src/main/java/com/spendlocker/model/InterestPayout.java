package com.spendlocker.model;

public enum InterestPayout {
    CUMULATIVE("Cumulative"),
    PAID_OUT("Paid out periodically");

    private final String dbValue;

    InterestPayout(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static InterestPayout fromDbValue(String value) {
        for (InterestPayout p : values()) {
            if (p.dbValue.equals(value)) return p;
        }
        return CUMULATIVE;
    }

    @Override
    public String toString() {
        return dbValue;
    }
}

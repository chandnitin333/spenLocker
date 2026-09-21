package com.spendlocker.model;

public enum PaymentMethod {
    CASH("Cash"),
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    BANK_TRANSFER("Bank Transfer"),
    UPI("UPI"),
    OTHER("Other");

    private final String dbValue;

    PaymentMethod(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static PaymentMethod fromDbValue(String value) {
        for (PaymentMethod p : values()) {
            if (p.dbValue.equals(value)) return p;
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return dbValue;
    }
}

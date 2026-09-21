package com.spendlocker.model;

public enum InvestmentType {
    STOCKS("Stocks"),
    MUTUAL_FUNDS("Mutual Funds"),
    CRYPTO("Crypto"),
    REAL_ESTATE("Real Estate"),
    FIXED_DEPOSIT("Fixed Deposit"),
    BONDS("Bonds"),
    GOLD("Gold"),
    OTHER("Other");

    private final String dbValue;

    InvestmentType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static InvestmentType fromDbValue(String value) {
        for (InvestmentType t : values()) {
            if (t.dbValue.equals(value)) return t;
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return dbValue;
    }
}

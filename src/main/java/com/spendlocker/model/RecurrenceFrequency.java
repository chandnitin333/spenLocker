package com.spendlocker.model;

import java.time.LocalDate;

public enum RecurrenceFrequency {
    WEEKLY("Weekly") {
        @Override
        public LocalDate advance(LocalDate date) {
            return date.plusWeeks(1);
        }
    },
    MONTHLY("Monthly") {
        @Override
        public LocalDate advance(LocalDate date) {
            return date.plusMonths(1);
        }
    },
    YEARLY("Yearly") {
        @Override
        public LocalDate advance(LocalDate date) {
            return date.plusYears(1);
        }
    };

    private final String dbValue;

    RecurrenceFrequency(String dbValue) {
        this.dbValue = dbValue;
    }

    public abstract LocalDate advance(LocalDate date);

    public String dbValue() {
        return dbValue;
    }

    public static RecurrenceFrequency fromDbValue(String value) {
        for (RecurrenceFrequency f : values()) {
            if (f.dbValue.equals(value)) return f;
        }
        return MONTHLY;
    }

    @Override
    public String toString() {
        return dbValue;
    }
}

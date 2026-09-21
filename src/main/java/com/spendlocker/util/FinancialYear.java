package com.spendlocker.util;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

/** India-style financial year: April 1 through March 31, labeled "FY 2024-25". */
public final class FinancialYear {

    private FinancialYear() {
    }

    public static String labelFor(LocalDate date) {
        int startYear = date.getMonthValue() >= Month.APRIL.getValue() ? date.getYear() : date.getYear() - 1;
        return label(startYear);
    }

    public static String label(int startYear) {
        return String.format("FY %d-%02d", startYear, (startYear + 1) % 100);
    }

    public static LocalDate startOf(String label) {
        return LocalDate.of(startYear(label), Month.APRIL, 1);
    }

    public static LocalDate endOf(String label) {
        return LocalDate.of(startYear(label) + 1, Month.MARCH, 31);
    }

    public static boolean isFinancialYearLabel(String label) {
        return label != null && label.startsWith("FY ");
    }

    /** Most recent first: current FY, then the given number of prior years. */
    public static List<String> recentLabels(int priorYears) {
        int currentStartYear = Integer.parseInt(labelFor(LocalDate.now()).substring(3, 7));
        List<String> labels = new ArrayList<>();
        for (int i = 0; i <= priorYears; i++) {
            labels.add(label(currentStartYear - i));
        }
        return labels;
    }

    private static int startYear(String label) {
        return Integer.parseInt(label.substring(3, 7));
    }
}

package com.spendlocker.util;

import com.spendlocker.model.Compounding;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.model.InterestPayout;
import com.spendlocker.model.TenureUnit;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Interest math for fixed deposits, matching the reference design's formulas exactly. */
public final class FixedDepositCalculator {

    private FixedDepositCalculator() {
    }

    public static double yearsOf(double tenureValue, TenureUnit unit) {
        return switch (unit) {
            case DAYS -> tenureValue / 365.0;
            case MONTHS -> tenureValue / 12.0;
            case YEARS -> tenureValue;
        };
    }

    public static double interest(double principal, double ratePercent, double years,
                                   Compounding compounding, InterestPayout payout) {
        if (principal <= 0 || ratePercent <= 0 || years <= 0) return 0;
        double r = ratePercent / 100.0;
        if (payout == InterestPayout.PAID_OUT) {
            return principal * r * years; // never compounds
        }
        if (compounding == Compounding.SIMPLE) {
            return principal * r * years;
        }
        int n = compounding.compoundsPerYear();
        return principal * (Math.pow(1 + r / n, n * years) - 1);
    }

    public static double interest(FixedDeposit fd) {
        double years = yearsOf(fd.getTenureValue(), fd.getTenureUnit());
        return interest(fd.getPrincipal(), fd.getRatePercent(), years, fd.getCompounding(), fd.getPayout());
    }

    public static double maturityAmount(FixedDeposit fd) {
        double interest = interest(fd);
        return fd.getPrincipal() + (fd.getPayout() == InterestPayout.PAID_OUT ? 0 : interest);
    }

    public static Long daysLeft(String maturityDateIso) {
        if (maturityDateIso == null || maturityDateIso.isBlank()) return null;
        LocalDate maturity = LocalDate.parse(maturityDateIso);
        return ChronoUnit.DAYS.between(LocalDate.now(), maturity);
    }

    /** No maturity date is treated as active — matches the reference's own rule. */
    public static boolean isActive(String maturityDateIso) {
        Long daysLeft = daysLeft(maturityDateIso);
        return daysLeft == null || daysLeft >= 0;
    }

    public static double weightedAverageRate(List<FixedDeposit> activeDeposits) {
        double totalPrincipal = activeDeposits.stream().mapToDouble(FixedDeposit::getPrincipal).sum();
        if (totalPrincipal <= 0) return 0;
        double weighted = activeDeposits.stream()
                .mapToDouble(fd -> fd.getRatePercent() * fd.getPrincipal())
                .sum();
        return weighted / totalPrincipal;
    }

    public static LocalDate suggestMaturityDate(LocalDate startDate, double tenureValue, TenureUnit unit) {
        if (startDate == null) return null;
        return switch (unit) {
            case DAYS -> startDate.plusDays((long) tenureValue);
            case MONTHS -> startDate.plusMonths((long) tenureValue);
            case YEARS -> startDate.plusYears((long) tenureValue);
        };
    }
}

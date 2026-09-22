package com.spendlocker.util;

import com.spendlocker.model.Compounding;
import com.spendlocker.model.InterestPayout;
import com.spendlocker.model.TenureUnit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FixedDepositCalculatorTest {

    @Test
    void cumulativeQuarterlyCompoundingMatchesTheWorkedExample() {
        // Spec acceptance criterion #1: 1,00,000 @ 7% quarterly, 2 years -> interest ~= 14,888.
        double years = FixedDepositCalculator.yearsOf(2, TenureUnit.YEARS);
        double interest = FixedDepositCalculator.interest(100_000, 7, years, Compounding.QUARTERLY, InterestPayout.CUMULATIVE);
        assertEquals(14888, interest, 1.0);
    }

    @Test
    void paidOutInterestNeverCompoundsAndMaturityIsPrincipalOnly() {
        double years = FixedDepositCalculator.yearsOf(2, TenureUnit.YEARS);
        double interest = FixedDepositCalculator.interest(100_000, 7, years, Compounding.QUARTERLY, InterestPayout.PAID_OUT);
        // Simple interest: 100000 * 0.07 * 2 = 14000, never compounded.
        assertEquals(14000, interest, 0.01);

        var fd = new com.spendlocker.model.FixedDeposit();
        fd.setPrincipal(100_000);
        fd.setRatePercent(7);
        fd.setTenureValue(2);
        fd.setTenureUnit(TenureUnit.YEARS);
        fd.setCompounding(Compounding.QUARTERLY);
        fd.setPayout(InterestPayout.PAID_OUT);
        assertEquals(100_000, FixedDepositCalculator.maturityAmount(fd), 0.01);
    }

    @Test
    void simpleCompoundingIsPrincipalTimesRateTimesYears() {
        double years = FixedDepositCalculator.yearsOf(3, TenureUnit.YEARS);
        double interest = FixedDepositCalculator.interest(50_000, 6, years, Compounding.SIMPLE, InterestPayout.CUMULATIVE);
        assertEquals(9000, interest, 0.01);
    }

    @Test
    void zeroRateOrZeroPrincipalYieldsZeroInterest() {
        assertEquals(0, FixedDepositCalculator.interest(100_000, 0, 2, Compounding.QUARTERLY, InterestPayout.CUMULATIVE));
        assertEquals(0, FixedDepositCalculator.interest(0, 7, 2, Compounding.QUARTERLY, InterestPayout.CUMULATIVE));
    }

    @Test
    void daysLeftAndActiveReflectMaturityDate() {
        String future = LocalDate.now().plusDays(10).toString();
        String past = LocalDate.now().minusDays(5).toString();
        assertEquals(10, FixedDepositCalculator.daysLeft(future));
        assertTrue(FixedDepositCalculator.isActive(future));
        assertTrue(FixedDepositCalculator.daysLeft(past) < 0);
        assertFalse(FixedDepositCalculator.isActive(past));
        assertTrue(FixedDepositCalculator.isActive(null));
    }

    @Test
    void weightedAverageRateWeightsByPrincipal() {
        var a = new com.spendlocker.model.FixedDeposit();
        a.setPrincipal(100_000);
        a.setRatePercent(6);
        var b = new com.spendlocker.model.FixedDeposit();
        b.setPrincipal(300_000);
        b.setRatePercent(8);
        double weighted = FixedDepositCalculator.weightedAverageRate(java.util.List.of(a, b));
        // (100000*6 + 300000*8) / 400000 = 7.5
        assertEquals(7.5, weighted, 0.001);
    }

    @Test
    void suggestedMaturityDateAddsTenureToStartDate() {
        LocalDate start = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2028, 1, 15),
                FixedDepositCalculator.suggestMaturityDate(start, 2, TenureUnit.YEARS));
        assertEquals(LocalDate.of(2026, 7, 15),
                FixedDepositCalculator.suggestMaturityDate(start, 6, TenureUnit.MONTHS));
    }
}

package com.spendlocker.service;

import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.dao.FixedDepositDao;
import com.spendlocker.dao.InvestmentDao;
import com.spendlocker.model.Compounding;
import com.spendlocker.model.Expense;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.model.InterestPayout;
import com.spendlocker.model.Investment;
import com.spendlocker.model.PaymentMethod;
import com.spendlocker.model.TenureUnit;

import java.time.LocalDate;

/** Inserts realistic sample rows across every category/type, for trying out the app. */
public class SampleDataService {

    private final ExpenseDao expenseDao = new ExpenseDao();
    private final InvestmentDao investmentDao = new InvestmentDao();
    private final FixedDepositDao fixedDepositDao = new FixedDepositDao();

    public int loadSampleData() {
        int count = 0;
        count += seedExpenses();
        count += seedInvestments();
        count += seedFixedDeposits();
        return count;
    }

    private int seedExpenses() {
        LocalDate today = LocalDate.now();
        Object[][] rows = {
            // daysAgo, amount, category, merchant, paymentMethod, notes
            {1, 45.50, "Groceries", "FreshMart", PaymentMethod.UPI, "Weekly groceries"},
            {2, 1200.00, "Rent", "Green Valley Apartments", PaymentMethod.BANK_TRANSFER, "Monthly rent"},
            {3, 15.99, "Subscriptions", "Netflix", PaymentMethod.CREDIT_CARD, "Monthly plan"},
            {5, 60.00, "Transportation", "City Metro", PaymentMethod.CASH, "Monthly pass"},
            {6, 89.25, "Dining", "The Coffee House", PaymentMethod.DEBIT_CARD, "Team lunch"},
            {8, 220.00, "Utilities", "PowerGrid Co", PaymentMethod.UPI, "Electricity bill"},
            {10, 32.00, "Entertainment", "Cineplex", PaymentMethod.CREDIT_CARD, "Movie night"},
            {12, 150.00, "Healthcare", "City Clinic", PaymentMethod.CASH, "Checkup"},
            {15, 75.40, "Shopping", "UrbanStyle", PaymentMethod.CREDIT_CARD, "New shoes"},
            {18, 500.00, "Travel", "SkyLine Airlines", PaymentMethod.BANK_TRANSFER, "Flight booking"},
            {20, 300.00, "Insurance", "SafeLife Insurance", PaymentMethod.BANK_TRANSFER, "Quarterly premium"},
            {22, 40.00, "Education", "Udemy", PaymentMethod.CREDIT_CARD, "Online course"},
            {25, 25.00, "Groceries", "FreshMart", PaymentMethod.UPI, ""},
            {28, 12.99, "Subscriptions", "Spotify", PaymentMethod.CREDIT_CARD, "Monthly plan"},
            {35, 95.00, "Dining", "Sushi Place", PaymentMethod.DEBIT_CARD, "Anniversary dinner"},
        };
        int inserted = 0;
        for (Object[] row : rows) {
            Expense e = new Expense();
            e.setTransactionDate(today.minusDays((int) row[0]).toString());
            e.setAmount((double) row[1]);
            e.setCategory((String) row[2]);
            e.setMerchantOrVendor((String) row[3]);
            e.setPaymentMethod((PaymentMethod) row[4]);
            e.setNotes((String) row[5]);
            expenseDao.insert(e);
            inserted++;
        }
        return inserted;
    }

    private int seedInvestments() {
        Object[][] rows = {
            // assetName, ticker, type, monthsAgo, principal, unitPrice, units, notes
            {"Apple Inc.", "AAPL", "Stocks", 8, 1500.00, 195.30, 8.0, "Core holding"},
            {"Vanguard Total Market Index", "VTI", "Mutual Funds", 15, 5000.00, 265.10, 20.5, ""},
            {"Bitcoin", "BTC", "Crypto", 6, 2000.00, 62000.00, 0.035, "Long-term hold"},
            {"Downtown Rental Property", "", "Real Estate", 36, 150000.00, 175000.00, 1.0, "2BHK apartment"},
            {"5-Year Fixed Deposit", "", "Fixed Deposit", 10, 10000.00, 11200.00, 1.0, "7% annual interest"},
            {"Sovereign Gold Bond", "SGB", "Gold", 24, 3000.00, 3450.00, 1.0, ""},
            {"Corporate Bond Fund", "", "Bonds", 12, 4000.00, 4180.00, 1.0, "AAA-rated"},
            {"Microsoft Corp.", "MSFT", "Stocks", 5, 1800.00, 415.20, 4.3, ""},
            {"Nifty 50 Index Fund", "", "Mutual Funds", 18, 3000.00, 182.50, 16.0, "SIP"},
            {"Ethereum", "ETH", "Crypto", 9, 1200.00, 3400.00, 0.35, ""},
            {"Recurring Deposit", "", "Fixed Deposit", 4, 2000.00, 2140.00, 1.0, "6.5% annual interest"},
        };
        int inserted = 0;
        for (Object[] row : rows) {
            Investment inv = new Investment();
            inv.setAssetName((String) row[0]);
            inv.setAssetTicker((String) row[1]);
            inv.setInvestmentType((String) row[2]);
            inv.setPurchaseDate(LocalDate.now().minusMonths((int) row[3]).toString());
            inv.setPrincipalAmount((double) row[4]);
            inv.setCurrentUnitPrice((double) row[5]);
            inv.setTotalUnits((double) row[6]);
            inv.setNotes((String) row[7]);
            investmentDao.insert(inv);
            inserted++;
        }
        return inserted;
    }

    /**
     * ~40 deposits across 5 depositors and 8 banks with varied rates/tenures/maturity dates —
     * some maturing within 30/90 days, several already matured, one zero-rate (exercises the
     * "add rate" tag), a couple paid-out periodically (interest never compounds).
     */
    private int seedFixedDeposits() {
        LocalDate today = LocalDate.now();
        String[] depositors = {"Demo 1", "Demo 2", "Demo 3", "Demo 4", "Demo 5"};
        String[] banks = {
            "Canara Bank", "HDFC Bank", "Yes Bank", "IDFC First Bank",
            "ICICI Bank", "State Bank of India", "Axis Bank", "Kotak Mahindra Bank"
        };
        double[] principals = {
            200000, 350000, 550000, 107492, 182737, 25103, 231183, 115591,
            400000, 90000, 275000, 60000, 500000, 128000, 76000
        };
        double[] rates = {8.47, 7.10, 6.30, 7.75, 6.90, 5.50, 8.00, 7.25, 6.65, 7.90};
        int[] tenureMonths = {12, 18, 24, 6, 36, 9, 15, 48, 30, 21};
        // Maturity offsets in days from today: within-30, within-90, further out, and matured.
        int[] maturityOffsets = {
            11, 14, 31, 45, 71, 87, 105, 140, 175, 210,
            250, 300, 340, 380, 420, 460, 500, 540, 580, 620,
            -10, -40, -90, -150, -200,
            3, 20, 60, 80, 95, 130, 160, 190, 220, 260,
            300, 330, -5, -60, 25
        };

        int inserted = 0;
        for (int i = 0; i < maturityOffsets.length; i++) {
            FixedDeposit fd = new FixedDeposit();
            fd.setDepositor(depositors[i % depositors.length]);
            fd.setBank(banks[i % banks.length]);
            fd.setFdNumber("FD" + (1000 + i));
            fd.setPrincipal(principals[i % principals.length]);
            fd.setRatePercent(i == 7 ? 0 : rates[i % rates.length]);
            int tenure = tenureMonths[i % tenureMonths.length];
            fd.setTenureValue(tenure);
            fd.setTenureUnit(TenureUnit.MONTHS);
            fd.setCompounding(i % 9 == 0 ? Compounding.SIMPLE : Compounding.QUARTERLY);
            fd.setPayout(i == 3 || i == 19 ? InterestPayout.PAID_OUT : InterestPayout.CUMULATIVE);
            LocalDate maturity = today.plusDays(maturityOffsets[i]);
            LocalDate start = maturity.minusMonths(tenure);
            fd.setStartDate(start.toString());
            fd.setMaturityDate(maturity.toString());
            fd.setNominee(depositors[(i + 1) % depositors.length]);
            fixedDepositDao.insert(fd);
            inserted++;
        }
        return inserted;
    }
}

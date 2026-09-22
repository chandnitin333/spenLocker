package com.spendlocker.service;

import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.dao.InvestmentDao;
import com.spendlocker.model.Expense;
import com.spendlocker.model.Investment;
import com.spendlocker.model.PaymentMethod;

import java.time.LocalDate;

/** Inserts realistic sample rows across every category/type, for trying out the app. */
public class SampleDataService {

    private final ExpenseDao expenseDao = new ExpenseDao();
    private final InvestmentDao investmentDao = new InvestmentDao();

    public int loadSampleData() {
        int count = 0;
        count += seedExpenses();
        count += seedInvestments();
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
}

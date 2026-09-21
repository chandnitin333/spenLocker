package com.spendlocker.service;

import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.dao.RecurringExpenseDao;
import com.spendlocker.model.Expense;
import com.spendlocker.model.RecurringExpense;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs once per app launch: creates an expense for every recurring rule whose next_due_date
 * has arrived, catching up any occurrences missed while the app was closed, then advances
 * each rule to its next future due date.
 */
public class RecurringExpenseService {

    private final RecurringExpenseDao recurringExpenseDao = new RecurringExpenseDao();
    private final ExpenseDao expenseDao = new ExpenseDao();

    /** Returns the categories of every expense created, in order (empty if nothing was due). */
    public List<String> processDue() {
        List<String> created = new ArrayList<>();
        LocalDate today = LocalDate.now();
        String todayIso = today.format(DateTimeFormatter.ISO_LOCAL_DATE);

        for (RecurringExpense rule : recurringExpenseDao.findActiveDueOnOrBefore(todayIso)) {
            LocalDate dueDate = LocalDate.parse(rule.getNextDueDate());
            while (!dueDate.isAfter(today)) {
                Expense expense = new Expense();
                expense.setTransactionDate(dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                expense.setAmount(rule.getAmount());
                expense.setCategory(rule.getCategory());
                expense.setMerchantOrVendor(rule.getMerchantOrVendor());
                expense.setPaymentMethod(rule.getPaymentMethod());
                expense.setNotes((rule.getNotes() == null || rule.getNotes().isBlank() ? "" : rule.getNotes() + " ") + "(Recurring)");
                expenseDao.insert(expense);
                created.add(rule.getCategory());
                dueDate = rule.getFrequency().advance(dueDate);
            }
            recurringExpenseDao.updateNextDueDate(rule.getId(), dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        return created;
    }
}

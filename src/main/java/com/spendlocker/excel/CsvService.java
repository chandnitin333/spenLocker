package com.spendlocker.excel;

import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.model.Expense;
import com.spendlocker.model.PaymentMethod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Plain-text CSV export/import for expenses — many banks export CSV rather than .xlsx. */
public class CsvService {

    private static final String[] HEADERS = {
        "Date", "Amount", "Category", "Merchant/Vendor", "Payment Method", "Notes"
    };
    private final ExpenseDao expenseDao = new ExpenseDao();

    public void exportExpenses(List<Expense> expenses, File destination) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(destination), StandardCharsets.UTF_8)) {
            writer.write(String.join(",", HEADERS));
            writer.write("\n");
            for (Expense e : expenses) {
                writer.write(String.join(",",
                        escape(e.getTransactionDate()),
                        escape(String.valueOf(e.getAmount())),
                        escape(e.getCategory()),
                        escape(e.getMerchantOrVendor()),
                        escape(e.getPaymentMethod() != null ? e.getPaymentMethod().toString() : ""),
                        escape(e.getNotes())));
                writer.write("\n");
            }
        }
    }

    public List<String> readHeaders(File csvFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return List.of();
            List<String> headers = parseLine(headerLine);
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).isBlank()) headers.set(i, "Column " + (i + 1));
            }
            return headers;
        }
    }

    public List<Expense> importExpenses(File csvFile, ColumnMapping mapping) throws IOException {
        List<Expense> imported = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header row, skipped
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cells = parseLine(line);
                int anchor = mapping.dateColumn() != ColumnMapping.UNMAPPED ? mapping.dateColumn() : mapping.amountColumn();
                if (anchor == ColumnMapping.UNMAPPED || anchor >= cells.size() || cells.get(anchor).isBlank()) continue;

                Expense expense = new Expense();
                expense.setTransactionDate(cellAt(cells, mapping.dateColumn()));
                expense.setAmount(parseAmount(cellAt(cells, mapping.amountColumn())));
                String category = cellAt(cells, mapping.categoryColumn());
                expense.setCategory(category == null || category.isBlank() ? "Uncategorized" : category);
                expense.setMerchantOrVendor(cellAt(cells, mapping.merchantColumn()));
                expense.setPaymentMethod(PaymentMethod.fromDbValue(nullToEmpty(cellAt(cells, mapping.paymentMethodColumn()))));
                expense.setNotes(cellAt(cells, mapping.notesColumn()));

                imported.add(expenseDao.insert(expense));
            }
        }
        return imported;
    }

    private String cellAt(List<String> cells, int index) {
        return index == ColumnMapping.UNMAPPED || index >= cells.size() ? null : cells.get(index);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private double parseAmount(String raw) {
        if (raw == null) return 0.0;
        try {
            return Double.parseDouble(raw.replaceAll("[^0-9.\\-]", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Minimal RFC4180-style parser: handles quoted fields with embedded commas/quotes. */
    private List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

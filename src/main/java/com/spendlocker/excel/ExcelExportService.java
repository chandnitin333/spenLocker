package com.spendlocker.excel;

import com.spendlocker.model.Expense;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.model.Investment;
import com.spendlocker.util.FixedDepositCalculator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class ExcelExportService {

    private static final String[] HEADERS = {
        "Date", "Amount", "Category", "Merchant/Vendor", "Payment Method", "Notes"
    };

    public void exportExpenses(List<Expense> expenses, File destination) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Expenses");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (Expense expense : expenses) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(expense.getTransactionDate());
                row.createCell(1).setCellValue(expense.getAmount());
                row.createCell(2).setCellValue(expense.getCategory());
                row.createCell(3).setCellValue(expense.getMerchantOrVendor());
                row.createCell(4).setCellValue(expense.getPaymentMethod() != null ? expense.getPaymentMethod().toString() : "");
                row.createCell(5).setCellValue(expense.getNotes());
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream out = new FileOutputStream(destination)) {
                workbook.write(out);
            }
        }
    }

    /**
     * The "Send & export" workbook: a Summary sheet plus one sheet per included kind, bold
     * headers, frozen top row, auto-filter. Deliberately doesn't embed receipt images or add
     * internal hyperlinks/conditional formatting (SpendLocker doesn't have per-FD attachments).
     */
    public void exportWealthBook(List<FixedDeposit> deposits, List<Investment> investments,
                                  List<Expense> expenses, Set<String> includeKinds, File destination) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            writeSummarySheet(workbook, headerStyle, deposits, investments, expenses);
            if (includeKinds.contains("Fixed deposit") && !deposits.isEmpty()) {
                writeDepositsSheet(workbook, headerStyle, deposits);
            }
            if (includeKinds.contains("Investments") && !investments.isEmpty()) {
                writeInvestmentsSheet(workbook, headerStyle, investments);
            }
            if (includeKinds.contains("Expense") && !expenses.isEmpty()) {
                writeExpensesSheet(workbook, headerStyle, expenses);
            }

            try (FileOutputStream out = new FileOutputStream(destination)) {
                workbook.write(out);
            }
        }
    }

    private void writeSummarySheet(Workbook workbook, CellStyle headerStyle,
                                    List<FixedDeposit> deposits, List<Investment> investments, List<Expense> expenses) {
        Sheet sheet = workbook.createSheet("Summary");
        int row = 0;

        List<FixedDeposit> active = deposits.stream().filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate())).toList();
        double principal = active.stream().mapToDouble(FixedDeposit::getPrincipal).sum();
        double interest = active.stream().mapToDouble(FixedDepositCalculator::interest).sum();
        double investedTotal = investments.stream().mapToDouble(Investment::getPrincipalAmount).sum();
        double investmentsValue = investments.stream().mapToDouble(Investment::getCurrentTotalValue).sum();
        double expensesTotal = expenses.stream().mapToDouble(Expense::getAmount).sum();

        row = writeLabelValue(sheet, headerStyle, row, "Wealth Book — Summary", null);
        row++;
        row = writeLabelValue(sheet, headerStyle, row, "Fixed deposits", null);
        row = writeLabelValue(sheet, null, row, "  Active deposits", (double) active.size());
        row = writeLabelValue(sheet, null, row, "  Principal", principal);
        row = writeLabelValue(sheet, null, row, "  Interest to come", interest);
        row++;
        row = writeLabelValue(sheet, headerStyle, row, "Investments", null);
        row = writeLabelValue(sheet, null, row, "  Invested", investedTotal);
        row = writeLabelValue(sheet, null, row, "  Value today", investmentsValue);
        row++;
        row = writeLabelValue(sheet, headerStyle, row, "Expenses", null);
        row = writeLabelValue(sheet, null, row, "  Total recorded", expensesTotal);
        row++;
        writeLabelValue(sheet, headerStyle, row, "TOTAL WEALTH TODAY", principal + investmentsValue);

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private int writeLabelValue(Sheet sheet, CellStyle style, int rowNum, String label, Double value) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        if (style != null) labelCell.setCellStyle(style);
        if (value != null) {
            Cell valueCell = row.createCell(1);
            valueCell.setCellValue(value);
            if (style != null) valueCell.setCellStyle(style);
        }
        return rowNum + 1;
    }

    private void writeDepositsSheet(Workbook workbook, CellStyle headerStyle, List<FixedDeposit> deposits) {
        String[] headers = {"Depositor", "Bank", "FD Number", "Rate %", "Principal", "Interest", "Maturity Amount", "Maturity Date"};
        Sheet sheet = workbook.createSheet("Fixed Deposits");
        writeHeaderRow(sheet, headerStyle, headers);
        int rowNum = 1;
        for (FixedDeposit fd : deposits) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(fd.getDepositor());
            row.createCell(1).setCellValue(fd.getBank());
            row.createCell(2).setCellValue(fd.getFdNumber());
            row.createCell(3).setCellValue(fd.getRatePercent());
            row.createCell(4).setCellValue(fd.getPrincipal());
            row.createCell(5).setCellValue(FixedDepositCalculator.interest(fd));
            row.createCell(6).setCellValue(FixedDepositCalculator.maturityAmount(fd));
            row.createCell(7).setCellValue(fd.getMaturityDate());
        }
        finishSheet(sheet, headers.length, rowNum);
    }

    private void writeInvestmentsSheet(Workbook workbook, CellStyle headerStyle, List<Investment> investments) {
        String[] headers = {"Asset", "Type", "Purchase Date", "Invested", "Value Now", "Gain"};
        Sheet sheet = workbook.createSheet("Investments");
        writeHeaderRow(sheet, headerStyle, headers);
        int rowNum = 1;
        for (Investment inv : investments) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(inv.getAssetName());
            row.createCell(1).setCellValue(inv.getInvestmentType());
            row.createCell(2).setCellValue(inv.getPurchaseDate());
            row.createCell(3).setCellValue(inv.getPrincipalAmount());
            row.createCell(4).setCellValue(inv.getCurrentTotalValue());
            row.createCell(5).setCellValue(inv.getCurrentTotalValue() - inv.getPrincipalAmount());
        }
        finishSheet(sheet, headers.length, rowNum);
    }

    private void writeExpensesSheet(Workbook workbook, CellStyle headerStyle, List<Expense> expenses) {
        Sheet sheet = workbook.createSheet("Expenses");
        writeHeaderRow(sheet, headerStyle, HEADERS);
        int rowNum = 1;
        for (Expense expense : expenses) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(expense.getTransactionDate());
            row.createCell(1).setCellValue(expense.getAmount());
            row.createCell(2).setCellValue(expense.getCategory());
            row.createCell(3).setCellValue(expense.getMerchantOrVendor());
            row.createCell(4).setCellValue(expense.getPaymentMethod() != null ? expense.getPaymentMethod().toString() : "");
            row.createCell(5).setCellValue(expense.getNotes());
        }
        finishSheet(sheet, HEADERS.length, rowNum);
    }

    private void writeHeaderRow(Sheet sheet, CellStyle headerStyle, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void finishSheet(Sheet sheet, int columnCount, int lastRow) {
        sheet.createFreezePane(0, 1);
        if (lastRow > 1) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, lastRow - 1, 0, columnCount - 1));
        }
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}

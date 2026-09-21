package com.spendlocker.excel;

import com.spendlocker.model.Expense;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

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
}

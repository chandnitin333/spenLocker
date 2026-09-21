package com.spendlocker.excel;

import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.dao.InvestmentDao;
import com.spendlocker.model.Expense;
import com.spendlocker.model.Investment;
import com.spendlocker.model.PaymentMethod;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Imports expense/investment rows from an .xlsx sheet using a caller-supplied column mapping. */
public class ExcelImportService {

    private static final int UNMAPPED = -1;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private final ExpenseDao expenseDao = new ExpenseDao();
    private final InvestmentDao investmentDao = new InvestmentDao();

    /** Reads the first (header) row so the caller can build a column-mapping UI. */
    public List<String> readHeaders(File xlsxFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(xlsxFile);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return List.of();

            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                String value = formatter.formatCellValue(cell).trim();
                headers.add(value.isEmpty() ? ("Column " + (cell.getColumnIndex() + 1)) : value);
            }
            return headers;
        }
    }

    public List<Expense> importExpenses(File xlsxFile, ColumnMapping mapping) throws IOException {
        List<Expense> imported = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(xlsxFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // header row
                int anchorCol = mapping.dateColumn() != ColumnMapping.UNMAPPED ? mapping.dateColumn() : mapping.amountColumn();
                if (isBlank(row, anchorCol)) continue;

                Expense expense = new Expense();
                expense.setTransactionDate(readDate(cellAt(row, mapping.dateColumn()), formatter));
                expense.setAmount(readAmount(cellAt(row, mapping.amountColumn())));
                expense.setCategory(readString(cellAt(row, mapping.categoryColumn()), formatter));
                expense.setMerchantOrVendor(readString(cellAt(row, mapping.merchantColumn()), formatter));
                expense.setPaymentMethod(readPaymentMethod(cellAt(row, mapping.paymentMethodColumn()), formatter));
                expense.setNotes(readString(cellAt(row, mapping.notesColumn()), formatter));

                if (expense.getCategory() == null || expense.getCategory().isBlank()) {
                    expense.setCategory("Uncategorized");
                }
                imported.add(expenseDao.insert(expense));
            }
        }
        return imported;
    }

    public List<Investment> importInvestments(File xlsxFile, InvestmentColumnMapping mapping) throws IOException {
        List<Investment> imported = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(xlsxFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // header row
                int anchorCol = mapping.assetNameColumn() != UNMAPPED ? mapping.assetNameColumn() : mapping.purchaseDateColumn();
                if (isBlank(row, anchorCol)) continue;

                Investment inv = new Investment();
                inv.setAssetName(readString(cellAt(row, mapping.assetNameColumn()), formatter));
                inv.setAssetTicker(readString(cellAt(row, mapping.tickerColumn()), formatter));
                String type = readString(cellAt(row, mapping.typeColumn()), formatter);
                inv.setInvestmentType(type == null || type.isBlank() ? "Other" : type);
                inv.setPurchaseDate(readDate(cellAt(row, mapping.purchaseDateColumn()), formatter));
                inv.setPrincipalAmount(readAmount(cellAt(row, mapping.principalColumn())));
                double unitPrice = readAmount(cellAt(row, mapping.unitPriceColumn()));
                double units = readAmount(cellAt(row, mapping.unitsColumn()));
                inv.setCurrentUnitPrice(unitPrice > 0 ? unitPrice : inv.getPrincipalAmount());
                inv.setTotalUnits(units > 0 ? units : 1.0);
                inv.setNotes(readString(cellAt(row, mapping.notesColumn()), formatter));

                if (inv.getAssetName() == null || inv.getAssetName().isBlank()) continue;
                imported.add(investmentDao.insert(inv));
            }
        }
        return imported;
    }

    private Cell cellAt(Row row, int columnIndex) {
        return columnIndex == UNMAPPED ? null : row.getCell(columnIndex);
    }

    private boolean isBlank(Row row, int anchorColumn) {
        Cell anchor = cellAt(row, anchorColumn);
        return anchor == null || anchor.getCellType() == CellType.BLANK;
    }

    private String readString(Cell cell, DataFormatter formatter) {
        return cell == null ? null : formatter.formatCellValue(cell).trim();
    }

    private String readDate(Cell cell, DataFormatter formatter) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().format(ISO_DATE);
        }
        return formatter.formatCellValue(cell);
    }

    private double readAmount(Cell cell) {
        if (cell == null) return 0.0;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        try {
            return Double.parseDouble(cell.getStringCellValue().replaceAll("[^0-9.\\-]", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private PaymentMethod readPaymentMethod(Cell cell, DataFormatter formatter) {
        if (cell == null) return PaymentMethod.OTHER;
        return PaymentMethod.fromDbValue(formatter.formatCellValue(cell).trim());
    }
}

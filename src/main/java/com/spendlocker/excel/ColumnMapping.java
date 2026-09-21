package com.spendlocker.excel;

/** Maps each expense field to a 0-based spreadsheet column index, or -1 if unmapped. */
public record ColumnMapping(int dateColumn, int amountColumn, int categoryColumn,
                             int merchantColumn, int paymentMethodColumn, int notesColumn) {

    public static final int UNMAPPED = -1;
}

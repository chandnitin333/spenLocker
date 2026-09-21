package com.spendlocker.excel;

/** Maps each investment field to a 0-based spreadsheet column index, or -1 if unmapped. */
public record InvestmentColumnMapping(int assetNameColumn, int tickerColumn, int typeColumn,
                                       int purchaseDateColumn, int principalColumn,
                                       int unitPriceColumn, int unitsColumn, int notesColumn) {
}

package com.spendlocker.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.spendlocker.model.Expense;
import com.spendlocker.model.Investment;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * The "Sync" button's backing store: a single Google Sheet ("SpendLocker Sync") with one tab
 * each for Expenses and Investments. Both the desktop app and the Android companion app write
 * the same sheet under the same Google account, so it doubles as the cross-device sync bridge —
 * no server, and no bidirectional merge logic to get wrong (each sync is a full overwrite of
 * that device's local data, which is the simplest thing that can't silently corrupt data).
 */
public class GoogleSheetsService {

    private static final String APPLICATION_NAME = "SpendLocker";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String SPREADSHEET_TITLE = "SpendLocker Sync";
    private static final String EXPENSES_TAB = "Expenses";
    private static final String INVESTMENTS_TAB = "Investments";

    private final GoogleDriveService driveService = GoogleDriveService.getInstance();
    private Sheets sheets;
    private String spreadsheetId;

    private Sheets sheetsClient() throws IOException, GeneralSecurityException {
        if (sheets == null) {
            sheets = new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, driveService.credential())
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }
        return sheets;
    }

    /** Finds the shared spreadsheet by name, or creates it (with both tabs) on first use. */
    private String ensureSpreadsheet() throws IOException, GeneralSecurityException {
        if (spreadsheetId != null) {
            return spreadsheetId;
        }
        for (File file : driveService.searchFilesByName(SPREADSHEET_TITLE)) {
            if (SPREADSHEET_TITLE.equals(file.getName())) {
                spreadsheetId = file.getId();
                return spreadsheetId;
            }
        }
        Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties().setTitle(SPREADSHEET_TITLE))
                .setSheets(List.of(
                        new Sheet().setProperties(new SheetProperties().setTitle(EXPENSES_TAB)),
                        new Sheet().setProperties(new SheetProperties().setTitle(INVESTMENTS_TAB))));
        Spreadsheet created = sheetsClient().spreadsheets().create(spreadsheet).execute();
        spreadsheetId = created.getSpreadsheetId();
        return spreadsheetId;
    }

    /** Overwrites the Expenses tab with the given rows. */
    public void syncExpenses(List<Expense> expenses) throws IOException, GeneralSecurityException {
        List<List<Object>> values = new ArrayList<>();
        values.add(List.of("Date", "Amount", "Category", "Merchant", "Payment Method", "Notes"));
        for (Expense e : expenses) {
            values.add(List.of(
                    orEmpty(e.getTransactionDate()),
                    e.getAmount(),
                    orEmpty(e.getCategory()),
                    orEmpty(e.getMerchantOrVendor()),
                    e.getPaymentMethod() != null ? e.getPaymentMethod().toString() : "",
                    orEmpty(e.getNotes())));
        }
        clearAndWrite(EXPENSES_TAB, values);
    }

    /** Overwrites the Investments tab with the given rows. */
    public void syncInvestments(List<Investment> investments) throws IOException, GeneralSecurityException {
        List<List<Object>> values = new ArrayList<>();
        values.add(List.of("Asset", "Ticker", "Type", "Purchase Date", "Principal", "Units", "Unit Price", "Current Value"));
        for (Investment inv : investments) {
            values.add(List.of(
                    orEmpty(inv.getAssetName()),
                    orEmpty(inv.getAssetTicker()),
                    orEmpty(inv.getInvestmentType()),
                    orEmpty(inv.getPurchaseDate()),
                    inv.getPrincipalAmount(),
                    inv.getTotalUnits(),
                    inv.getCurrentUnitPrice(),
                    inv.getCurrentTotalValue()));
        }
        clearAndWrite(INVESTMENTS_TAB, values);
    }

    private void clearAndWrite(String tab, List<List<Object>> values) throws IOException, GeneralSecurityException {
        String id = ensureSpreadsheet();
        sheetsClient().spreadsheets().values().clear(id, tab, new ClearValuesRequest()).execute();
        ValueRange body = new ValueRange().setValues(values);
        sheetsClient().spreadsheets().values()
                .update(id, tab + "!A1", body)
                .setValueInputOption("RAW")
                .execute();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}

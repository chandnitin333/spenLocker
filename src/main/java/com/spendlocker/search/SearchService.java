package com.spendlocker.search;

import com.spendlocker.db.DatabaseManager;
import com.spendlocker.model.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain LIKE-based search across expenses, documents, and investments. Deliberately avoids
 * SQLite's FTS5 module, since it isn't guaranteed to be compiled into every SQLCipher build —
 * a LIKE scan is slower on very large tables but works everywhere and needs no schema changes.
 */
public class SearchService {

    private static final int MAX_RESULTS_PER_TABLE = 50;

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public SearchResults search(String query) {
        if (query == null || query.isBlank()) {
            return new SearchResults(List.of(), List.of(), List.of());
        }
        String term = "%" + query.trim() + "%";
        return new SearchResults(searchExpenses(term), searchDocuments(term), searchInvestments(term));
    }

    private List<Expense> searchExpenses(String term) {
        String sql = """
            SELECT * FROM expenses
             WHERE deleted_at IS NULL
               AND (category LIKE ? COLLATE NOCASE
                OR merchant_or_vendor LIKE ? COLLATE NOCASE
                OR notes LIKE ? COLLATE NOCASE)
             ORDER BY transaction_date DESC
             LIMIT ?
            """;
        List<Expense> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, term);
            ps.setString(2, term);
            ps.setString(3, term);
            ps.setInt(4, MAX_RESULTS_PER_TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Expense e = new Expense();
                    e.setId(rs.getLong("id"));
                    e.setTransactionDate(rs.getString("transaction_date"));
                    e.setAmount(rs.getDouble("amount"));
                    e.setCategory(rs.getString("category"));
                    e.setMerchantOrVendor(rs.getString("merchant_or_vendor"));
                    String pm = rs.getString("payment_method");
                    e.setPaymentMethod(pm != null ? PaymentMethod.fromDbValue(pm) : null);
                    e.setNotes(rs.getString("notes"));
                    result.add(e);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Search failed (expenses)", ex);
        }
        return result;
    }

    private List<Document> searchDocuments(String term) {
        String sql = """
            SELECT * FROM documents
             WHERE deleted_at IS NULL
               AND (file_name LIKE ? COLLATE NOCASE
                OR extracted_text LIKE ? COLLATE NOCASE
                OR tags LIKE ? COLLATE NOCASE)
             ORDER BY upload_date DESC
             LIMIT ?
            """;
        List<Document> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, term);
            ps.setString(2, term);
            ps.setString(3, term);
            ps.setInt(4, MAX_RESULTS_PER_TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Document d = new Document();
                    d.setId(rs.getLong("id"));
                    d.setFileName(rs.getString("file_name"));
                    d.setFilePath(rs.getString("file_path"));
                    String ft = rs.getString("file_type");
                    d.setFileType(ft != null ? FileType.valueOf(ft) : FileType.OTHER);
                    d.setUploadSource(UploadSource.fromDbValue(rs.getString("upload_source")));
                    d.setUploadDate(rs.getString("upload_date"));
                    d.setTags(rs.getString("tags"));
                    result.add(d);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Search failed (documents)", ex);
        }
        return result;
    }

    private List<Investment> searchInvestments(String term) {
        String sql = """
            SELECT * FROM investments
             WHERE deleted_at IS NULL
               AND (asset_name LIKE ? COLLATE NOCASE
                OR asset_ticker LIKE ? COLLATE NOCASE
                OR notes LIKE ? COLLATE NOCASE)
             ORDER BY purchase_date DESC
             LIMIT ?
            """;
        List<Investment> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, term);
            ps.setString(2, term);
            ps.setString(3, term);
            ps.setInt(4, MAX_RESULTS_PER_TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Investment inv = new Investment();
                    inv.setId(rs.getLong("id"));
                    inv.setAssetName(rs.getString("asset_name"));
                    inv.setAssetTicker(rs.getString("asset_ticker"));
                    inv.setInvestmentType(rs.getString("investment_type"));
                    inv.setPurchaseDate(rs.getString("purchase_date"));
                    inv.setPrincipalAmount(rs.getDouble("principal_amount"));
                    inv.setCurrentUnitPrice(rs.getDouble("current_unit_price"));
                    inv.setTotalUnits(rs.getDouble("total_units"));
                    result.add(inv);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Search failed (investments)", ex);
        }
        return result;
    }
}

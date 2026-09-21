package com.spendlocker.dao;

import com.spendlocker.db.DatabaseManager;
import com.spendlocker.model.Expense;
import com.spendlocker.model.PaymentMethod;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ExpenseDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<Expense> findAll() {
        String sql = "SELECT * FROM expenses WHERE deleted_at IS NULL ORDER BY transaction_date DESC, id DESC";
        List<Expense> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load expenses", e);
        }
        return result;
    }

    public List<Expense> findDeleted() {
        String sql = "SELECT * FROM expenses WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC";
        List<Expense> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load deleted expenses", e);
        }
        return result;
    }

    public Expense insert(Expense e) {
        String sql = """
            INSERT INTO expenses (transaction_date, amount, category, merchant_or_vendor,
                                   payment_method, notes, document_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, e);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    e.setId(keys.getLong(1));
                }
            }
            return e;
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to insert expense", ex);
        }
    }

    public void update(Expense e) {
        String sql = """
            UPDATE expenses
               SET transaction_date = ?, amount = ?, category = ?, merchant_or_vendor = ?,
                   payment_method = ?, notes = ?, document_id = ?
             WHERE id = ?
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            bind(ps, e);
            ps.setLong(8, e.getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to update expense", ex);
        }
    }

    /** Moves the row to Trash; it's excluded from every normal query until restored. */
    public void softDelete(long id) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE expenses SET deleted_at = datetime('now', 'localtime') WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to delete expense", ex);
        }
    }

    public void restore(long id) {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE expenses SET deleted_at = NULL WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to restore expense", ex);
        }
    }

    /** Permanently removes a row already in Trash. Cannot be undone. */
    public void hardDelete(long id) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM expenses WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to permanently delete expense", ex);
        }
    }

    public List<String> distinctCategories() {
        return distinctColumn("category");
    }

    public List<String> distinctMerchants() {
        return distinctColumn("merchant_or_vendor");
    }

    /** The {@code limit} most-used merchants, most frequent first. */
    public List<String> topMerchants(int limit) {
        String sql = """
            SELECT merchant_or_vendor FROM expenses
             WHERE merchant_or_vendor IS NOT NULL AND TRIM(merchant_or_vendor) <> '' AND deleted_at IS NULL
             GROUP BY merchant_or_vendor
             ORDER BY COUNT(*) DESC, merchant_or_vendor COLLATE NOCASE
             LIMIT ?
            """;
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load top merchants", e);
        }
        return result;
    }

    /** Every payment method, most-used first (unused ones keep their natural enum order after that). */
    public List<PaymentMethod> paymentMethodsByUsage() {
        String sql = """
            SELECT payment_method FROM expenses
             WHERE payment_method IS NOT NULL AND deleted_at IS NULL
             GROUP BY payment_method
             ORDER BY COUNT(*) DESC
            """;
        List<PaymentMethod> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(PaymentMethod.fromDbValue(rs.getString(1)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load payment method usage", e);
        }
        for (PaymentMethod method : PaymentMethod.values()) {
            if (!result.contains(method)) {
                result.add(method);
            }
        }
        return result;
    }

    private List<String> distinctColumn(String column) {
        String sql = "SELECT DISTINCT " + column + " FROM expenses WHERE " + column
                + " IS NOT NULL AND TRIM(" + column + ") <> '' AND deleted_at IS NULL ORDER BY " + column + " COLLATE NOCASE";
        List<String> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load distinct " + column, e);
        }
        return result;
    }

    public double sumForCurrentMonth() {
        String sql = """
            SELECT COALESCE(SUM(amount), 0) FROM expenses
             WHERE strftime('%Y-%m', transaction_date) = strftime('%Y-%m', 'now', 'localtime')
               AND deleted_at IS NULL
            """;
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sum expenses", e);
        }
    }

    /** Category -> total spent this month, ranked highest first. */
    public java.util.LinkedHashMap<String, Double> categoryTotalsForCurrentMonth() {
        String sql = """
            SELECT category, SUM(amount) AS total FROM expenses
             WHERE strftime('%Y-%m', transaction_date) = strftime('%Y-%m', 'now', 'localtime')
               AND deleted_at IS NULL
             GROUP BY category
             ORDER BY total DESC
            """;
        java.util.LinkedHashMap<String, Double> result = new java.util.LinkedHashMap<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getString("category"), rs.getDouble("total"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load category totals", e);
        }
        return result;
    }

    /** Category -> total spent within [startIso, endIso] inclusive, ranked highest first. */
    public java.util.LinkedHashMap<String, Double> categoryTotalsForRange(String startIso, String endIso) {
        String sql = """
            SELECT category, SUM(amount) AS total FROM expenses
             WHERE transaction_date >= ? AND transaction_date <= ? AND deleted_at IS NULL
             GROUP BY category
             ORDER BY total DESC
            """;
        java.util.LinkedHashMap<String, Double> result = new java.util.LinkedHashMap<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, startIso);
            ps.setString(2, endIso);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("category"), rs.getDouble("total"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load category totals for range", e);
        }
        return result;
    }

    /** Total spent within [startIso, endIso] inclusive. */
    public double sumForRange(String startIso, String endIso) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE transaction_date >= ? AND transaction_date <= ? AND deleted_at IS NULL")) {
            ps.setString(1, startIso);
            ps.setString(2, endIso);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sum expenses for range", e);
        }
    }

    /** The last {@code months} calendar months (oldest first) mapped to total spend, zero-filled. */
    public java.util.LinkedHashMap<String, Double> monthlyTotals(int months) {
        java.util.LinkedHashMap<String, Double> byMonth = new java.util.LinkedHashMap<>();
        java.time.YearMonth current = java.time.YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            byMonth.put(current.minusMonths(i).toString(), 0.0);
        }

        String sql = """
            SELECT strftime('%Y-%m', transaction_date) AS ym, SUM(amount) AS total FROM expenses
             WHERE transaction_date >= date('now', 'localtime', 'start of month', ?) AND deleted_at IS NULL
             GROUP BY ym
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, "-" + (months - 1) + " months");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String month = rs.getString("ym");
                    double total = rs.getDouble("total");
                    byMonth.computeIfPresent(month, (k, v) -> total);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load monthly totals", e);
        }
        return byMonth;
    }

    private void bind(PreparedStatement ps, Expense e) throws SQLException {
        ps.setString(1, e.getTransactionDate());
        ps.setDouble(2, e.getAmount());
        ps.setString(3, e.getCategory());
        ps.setString(4, e.getMerchantOrVendor());
        ps.setString(5, e.getPaymentMethod() != null ? e.getPaymentMethod().dbValue() : null);
        ps.setString(6, e.getNotes());
        if (e.getDocumentId() != null) {
            ps.setLong(7, e.getDocumentId());
        } else {
            ps.setNull(7, java.sql.Types.INTEGER);
        }
    }

    private Expense map(ResultSet rs) throws SQLException {
        Expense e = new Expense();
        e.setId(rs.getLong("id"));
        e.setTransactionDate(rs.getString("transaction_date"));
        e.setAmount(rs.getDouble("amount"));
        e.setCategory(rs.getString("category"));
        e.setMerchantOrVendor(rs.getString("merchant_or_vendor"));
        String pm = rs.getString("payment_method");
        e.setPaymentMethod(pm != null ? PaymentMethod.fromDbValue(pm) : null);
        e.setNotes(rs.getString("notes"));
        long docId = rs.getLong("document_id");
        e.setDocumentId(rs.wasNull() ? null : docId);
        e.setCreatedAt(rs.getString("created_at"));
        e.setDeletedAt(rs.getString("deleted_at"));
        return e;
    }
}

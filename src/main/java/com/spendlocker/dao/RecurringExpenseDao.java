package com.spendlocker.dao;

import com.spendlocker.db.DatabaseManager;
import com.spendlocker.model.PaymentMethod;
import com.spendlocker.model.RecurrenceFrequency;
import com.spendlocker.model.RecurringExpense;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RecurringExpenseDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<RecurringExpense> findAll() {
        List<RecurringExpense> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM recurring_expenses ORDER BY next_due_date")) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load recurring expenses", e);
        }
        return result;
    }

    public List<RecurringExpense> findActiveDueOnOrBefore(String date) {
        List<RecurringExpense> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM recurring_expenses WHERE active = 1 AND next_due_date <= ?")) {
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load due recurring expenses", e);
        }
        return result;
    }

    public RecurringExpense insert(RecurringExpense r) {
        String sql = """
            INSERT INTO recurring_expenses (category, amount, merchant_or_vendor, payment_method,
                                             notes, frequency, next_due_date, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, r);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    r.setId(keys.getLong(1));
                }
            }
            return r;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert recurring expense", e);
        }
    }

    public void update(RecurringExpense r) {
        String sql = """
            UPDATE recurring_expenses
               SET category = ?, amount = ?, merchant_or_vendor = ?, payment_method = ?,
                   notes = ?, frequency = ?, next_due_date = ?, active = ?
             WHERE id = ?
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            bind(ps, r);
            ps.setLong(9, r.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update recurring expense", e);
        }
    }

    public void updateNextDueDate(long id, String nextDueDate) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE recurring_expenses SET next_due_date = ? WHERE id = ?")) {
            ps.setString(1, nextDueDate);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to advance recurring expense", e);
        }
    }

    public void delete(long id) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM recurring_expenses WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete recurring expense", e);
        }
    }

    private void bind(PreparedStatement ps, RecurringExpense r) throws SQLException {
        ps.setString(1, r.getCategory());
        ps.setDouble(2, r.getAmount());
        ps.setString(3, r.getMerchantOrVendor());
        ps.setString(4, r.getPaymentMethod() != null ? r.getPaymentMethod().dbValue() : null);
        ps.setString(5, r.getNotes());
        ps.setString(6, r.getFrequency().dbValue());
        ps.setString(7, r.getNextDueDate());
        ps.setInt(8, r.isActive() ? 1 : 0);
    }

    private RecurringExpense map(ResultSet rs) throws SQLException {
        RecurringExpense r = new RecurringExpense();
        r.setId(rs.getLong("id"));
        r.setCategory(rs.getString("category"));
        r.setAmount(rs.getDouble("amount"));
        r.setMerchantOrVendor(rs.getString("merchant_or_vendor"));
        String pm = rs.getString("payment_method");
        r.setPaymentMethod(pm != null ? PaymentMethod.fromDbValue(pm) : null);
        r.setNotes(rs.getString("notes"));
        r.setFrequency(RecurrenceFrequency.fromDbValue(rs.getString("frequency")));
        r.setNextDueDate(rs.getString("next_due_date"));
        r.setActive(rs.getInt("active") == 1);
        return r;
    }
}

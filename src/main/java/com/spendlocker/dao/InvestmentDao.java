package com.spendlocker.dao;

import com.spendlocker.db.DatabaseManager;
import com.spendlocker.model.Investment;
import com.spendlocker.model.InvestmentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InvestmentDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<Investment> findAll() {
        String sql = "SELECT * FROM investments WHERE deleted_at IS NULL ORDER BY purchase_date DESC, id DESC";
        List<Investment> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load investments", e);
        }
        return result;
    }

    public List<Investment> findDeleted() {
        String sql = "SELECT * FROM investments WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC";
        List<Investment> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load deleted investments", e);
        }
        return result;
    }

    public Investment insert(Investment inv) {
        String sql = """
            INSERT INTO investments (asset_name, asset_ticker, investment_type, purchase_date,
                                      principal_amount, current_unit_price, total_units, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, inv);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    inv.setId(keys.getLong(1));
                }
            }
            return inv;
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to insert investment", ex);
        }
    }

    public void update(Investment inv) {
        String sql = """
            UPDATE investments
               SET asset_name = ?, asset_ticker = ?, investment_type = ?, purchase_date = ?,
                   principal_amount = ?, current_unit_price = ?, total_units = ?, notes = ?
             WHERE id = ?
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            bind(ps, inv);
            ps.setLong(9, inv.getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to update investment", ex);
        }
    }

    /** Moves the row to Trash; it's excluded from every normal query until restored. */
    public void softDelete(long id) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE investments SET deleted_at = datetime('now', 'localtime') WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to delete investment", ex);
        }
    }

    public void restore(long id) {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE investments SET deleted_at = NULL WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to restore investment", ex);
        }
    }

    /** Permanently removes a row already in Trash. Cannot be undone. */
    public void hardDelete(long id) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM investments WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to permanently delete investment", ex);
        }
    }

    public double sumCurrentValue() {
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(SUM(current_total_value), 0) FROM investments WHERE deleted_at IS NULL")) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sum investments", e);
        }
    }

    public double sumPrincipal() {
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(SUM(principal_amount), 0) FROM investments WHERE deleted_at IS NULL")) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sum investment principal", e);
        }
    }

    public double overallRoiPercent() {
        double principal = sumPrincipal();
        return principal == 0 ? 0.0 : ((sumCurrentValue() - principal) / principal) * 100.0;
    }

    public List<String> distinctTypes() {
        List<String> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DISTINCT investment_type FROM investments WHERE deleted_at IS NULL ORDER BY investment_type COLLATE NOCASE")) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load distinct investment types", e);
        }
        return result;
    }

    /**
     * Current value allocated to each investment type: the built-in defaults first (fixed
     * order, so Dashboard colors stay stable), then any custom types by value descending.
     */
    public LinkedHashMap<String, Double> currentValueByType() {
        Map<String, Double> totals = new java.util.HashMap<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT investment_type, SUM(current_total_value) AS total FROM investments WHERE deleted_at IS NULL GROUP BY investment_type")) {
            while (rs.next()) {
                totals.put(rs.getString("investment_type"), rs.getDouble("total"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load investment allocation", e);
        }

        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (InvestmentType type : InvestmentType.values()) {
            Double total = totals.remove(type.dbValue());
            if (total != null && total > 0) {
                result.put(type.dbValue(), total);
            }
        }
        totals.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private void bind(PreparedStatement ps, Investment inv) throws SQLException {
        ps.setString(1, inv.getAssetName());
        ps.setString(2, inv.getAssetTicker());
        ps.setString(3, inv.getInvestmentType());
        ps.setString(4, inv.getPurchaseDate());
        ps.setDouble(5, inv.getPrincipalAmount());
        ps.setDouble(6, inv.getCurrentUnitPrice());
        ps.setDouble(7, inv.getTotalUnits());
        ps.setString(8, inv.getNotes());
    }

    private Investment map(ResultSet rs) throws SQLException {
        Investment inv = new Investment();
        inv.setId(rs.getLong("id"));
        inv.setAssetName(rs.getString("asset_name"));
        inv.setAssetTicker(rs.getString("asset_ticker"));
        inv.setInvestmentType(rs.getString("investment_type"));
        inv.setPurchaseDate(rs.getString("purchase_date"));
        inv.setPrincipalAmount(rs.getDouble("principal_amount"));
        inv.setCurrentUnitPrice(rs.getDouble("current_unit_price"));
        inv.setTotalUnits(rs.getDouble("total_units"));
        inv.setNotes(rs.getString("notes"));
        inv.setDeletedAt(rs.getString("deleted_at"));
        return inv;
    }
}

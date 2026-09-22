package com.spendlocker.dao;

import com.spendlocker.db.DatabaseManager;
import com.spendlocker.model.Compounding;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.model.InterestPayout;
import com.spendlocker.model.TenureUnit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class FixedDepositDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<FixedDeposit> findAll() {
        String sql = "SELECT * FROM fixed_deposits WHERE deleted_at IS NULL ORDER BY maturity_date";
        List<FixedDeposit> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load fixed deposits", e);
        }
        return result;
    }

    public List<FixedDeposit> findDeleted() {
        String sql = "SELECT * FROM fixed_deposits WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC";
        List<FixedDeposit> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load deleted fixed deposits", e);
        }
        return result;
    }

    public FixedDeposit insert(FixedDeposit fd) {
        String sql = """
            INSERT INTO fixed_deposits (depositor, bank, fd_number, principal, rate_percent,
                                         tenure_value, tenure_unit, compounding, payout,
                                         start_date, maturity_date, nominee, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, fd);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    fd.setId(keys.getLong(1));
                }
            }
            return fd;
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to insert fixed deposit", ex);
        }
    }

    public void update(FixedDeposit fd) {
        String sql = """
            UPDATE fixed_deposits
               SET depositor = ?, bank = ?, fd_number = ?, principal = ?, rate_percent = ?,
                   tenure_value = ?, tenure_unit = ?, compounding = ?, payout = ?,
                   start_date = ?, maturity_date = ?, nominee = ?, notes = ?
             WHERE id = ?
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            bind(ps, fd);
            ps.setLong(14, fd.getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to update fixed deposit", ex);
        }
    }

    /** Moves the row to Trash; it's excluded from every normal query until restored. */
    public void softDelete(long id) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE fixed_deposits SET deleted_at = datetime('now', 'localtime') WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to delete fixed deposit", ex);
        }
    }

    public void restore(long id) {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE fixed_deposits SET deleted_at = NULL WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to restore fixed deposit", ex);
        }
    }

    /** Permanently removes a row already in Trash. Cannot be undone. */
    public void hardDelete(long id) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM fixed_deposits WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to permanently delete fixed deposit", ex);
        }
    }

    public List<String> distinctDepositors() {
        return distinctColumn("depositor");
    }

    public List<String> distinctBanks() {
        return distinctColumn("bank");
    }

    private List<String> distinctColumn(String column) {
        List<String> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DISTINCT " + column + " FROM fixed_deposits WHERE deleted_at IS NULL ORDER BY " + column + " COLLATE NOCASE")) {
            while (rs.next()) {
                String value = rs.getString(1);
                if (value != null && !value.isBlank()) result.add(value);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load distinct " + column, e);
        }
        return result;
    }

    private void bind(PreparedStatement ps, FixedDeposit fd) throws SQLException {
        ps.setString(1, fd.getDepositor());
        ps.setString(2, fd.getBank());
        ps.setString(3, fd.getFdNumber());
        ps.setDouble(4, fd.getPrincipal());
        ps.setDouble(5, fd.getRatePercent());
        ps.setInt(6, fd.getTenureValue());
        ps.setString(7, fd.getTenureUnit().dbValue());
        ps.setString(8, fd.getCompounding().dbValue());
        ps.setString(9, fd.getPayout().dbValue());
        ps.setString(10, fd.getStartDate());
        ps.setString(11, fd.getMaturityDate());
        ps.setString(12, fd.getNominee());
        ps.setString(13, fd.getNotes());
    }

    private FixedDeposit map(ResultSet rs) throws SQLException {
        FixedDeposit fd = new FixedDeposit();
        fd.setId(rs.getLong("id"));
        fd.setDepositor(rs.getString("depositor"));
        fd.setBank(rs.getString("bank"));
        fd.setFdNumber(rs.getString("fd_number"));
        fd.setPrincipal(rs.getDouble("principal"));
        fd.setRatePercent(rs.getDouble("rate_percent"));
        fd.setTenureValue(rs.getInt("tenure_value"));
        fd.setTenureUnit(TenureUnit.fromDbValue(rs.getString("tenure_unit")));
        fd.setCompounding(Compounding.fromDbValue(rs.getString("compounding")));
        fd.setPayout(InterestPayout.fromDbValue(rs.getString("payout")));
        fd.setStartDate(rs.getString("start_date"));
        fd.setMaturityDate(rs.getString("maturity_date"));
        fd.setNominee(rs.getString("nominee"));
        fd.setNotes(rs.getString("notes"));
        fd.setDeletedAt(rs.getString("deleted_at"));
        return fd;
    }
}

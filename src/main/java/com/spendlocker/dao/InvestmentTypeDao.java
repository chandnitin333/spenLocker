package com.spendlocker.dao;

import com.spendlocker.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** User-defined investment types (beyond the built-in defaults), added from the Add Investment form. */
public class InvestmentTypeDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<String> findAll() {
        List<String> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM investment_types ORDER BY name COLLATE NOCASE")) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load investment types", e);
        }
        return result;
    }

    public void addIfAbsent(String name) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT OR IGNORE INTO investment_types (name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add investment type", e);
        }
    }
}

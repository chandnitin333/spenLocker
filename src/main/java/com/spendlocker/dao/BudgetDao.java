package com.spendlocker.dao;

import com.spendlocker.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;

/** One monthly spending limit per category. */
public class BudgetDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public LinkedHashMap<String, Double> findAll() {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT category, monthly_limit FROM budgets ORDER BY category COLLATE NOCASE")) {
            while (rs.next()) {
                result.put(rs.getString("category"), rs.getDouble("monthly_limit"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load budgets", e);
        }
        return result;
    }

    public void upsert(String category, double monthlyLimit) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO budgets (category, monthly_limit) VALUES (?, ?) " +
                "ON CONFLICT(category) DO UPDATE SET monthly_limit = excluded.monthly_limit")) {
            ps.setString(1, category);
            ps.setDouble(2, monthlyLimit);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save budget", e);
        }
    }

    public void delete(String category) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM budgets WHERE category = ?")) {
            ps.setString(1, category);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete budget", e);
        }
    }
}

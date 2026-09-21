package com.spendlocker.dao;

import com.spendlocker.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** User-defined expense categories, added on demand from the Add Expense form. */
public class CategoryDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<String> findAll() {
        List<String> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM categories ORDER BY name COLLATE NOCASE")) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load categories", e);
        }
        return result;
    }

    /** No-op if the category already exists (case-sensitive match on the primary key). */
    public void addIfAbsent(String name) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT OR IGNORE INTO categories (name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add category", e);
        }
    }
}

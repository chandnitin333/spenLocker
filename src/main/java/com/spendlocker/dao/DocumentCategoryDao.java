package com.spendlocker.dao;

import com.spendlocker.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** User-defined document categories (e.g. Education, Company, Home), added on demand. */
public class DocumentCategoryDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<String> findAll() {
        List<String> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM document_categories ORDER BY name COLLATE NOCASE")) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load document categories", e);
        }
        return result;
    }

    public void addIfAbsent(String name) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT OR IGNORE INTO document_categories (name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add document category", e);
        }
    }
}

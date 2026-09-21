package com.spendlocker.dao;

import com.spendlocker.db.DatabaseManager;
import com.spendlocker.model.Document;
import com.spendlocker.model.FileType;
import com.spendlocker.model.UploadSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DocumentDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<Document> findAll() {
        String sql = "SELECT * FROM documents WHERE deleted_at IS NULL ORDER BY upload_date DESC, id DESC";
        List<Document> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load documents", e);
        }
        return result;
    }

    public List<Document> findDeleted() {
        String sql = "SELECT * FROM documents WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC";
        List<Document> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load deleted documents", e);
        }
        return result;
    }

    public long count() {
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM documents WHERE deleted_at IS NULL")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count documents", e);
        }
    }

    public Document insert(Document d) {
        String sql = """
            INSERT INTO documents (file_name, file_path, file_type, file_size_bytes,
                                    upload_source, extracted_text, tags, category)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, d.getFileName());
            ps.setString(2, d.getFilePath());
            ps.setString(3, d.getFileType().name());
            ps.setLong(4, d.getFileSizeBytes());
            ps.setString(5, d.getUploadSource().dbValue());
            ps.setString(6, d.getExtractedText());
            ps.setString(7, d.getTags());
            ps.setString(8, d.getCategory());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    d.setId(keys.getLong(1));
                }
            }
            return d;
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to insert document", ex);
        }
    }

    public void updateTags(long id, String tags) {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE documents SET tags = ? WHERE id = ?")) {
            ps.setString(1, tags);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to update document tags", ex);
        }
    }

    public void updateDriveFileId(long id, String driveFileId) {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE documents SET drive_file_id = ? WHERE id = ?")) {
            ps.setString(1, driveFileId);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to update document Drive link", ex);
        }
    }

    public List<String> distinctCategories() {
        List<String> result = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DISTINCT category FROM documents WHERE category IS NOT NULL AND TRIM(category) <> '' AND deleted_at IS NULL ORDER BY category COLLATE NOCASE")) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load distinct document categories", e);
        }
        return result;
    }

    /** Moves the row to Trash; it's excluded from every normal query until restored. */
    public void softDelete(long id) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE documents SET deleted_at = datetime('now', 'localtime') WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to delete document", ex);
        }
    }

    public void restore(long id) {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE documents SET deleted_at = NULL WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to restore document", ex);
        }
    }

    /** Permanently removes a row already in Trash. Cannot be undone. */
    public void hardDelete(long id) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM documents WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to permanently delete document", ex);
        }
    }

    private Document map(ResultSet rs) throws SQLException {
        Document d = new Document();
        d.setId(rs.getLong("id"));
        d.setFileName(rs.getString("file_name"));
        d.setFilePath(rs.getString("file_path"));
        String ft = rs.getString("file_type");
        d.setFileType(ft != null ? FileType.valueOf(ft) : FileType.OTHER);
        d.setFileSizeBytes(rs.getLong("file_size_bytes"));
        d.setUploadSource(UploadSource.fromDbValue(rs.getString("upload_source")));
        d.setUploadDate(rs.getString("upload_date"));
        d.setExtractedText(rs.getString("extracted_text"));
        d.setTags(rs.getString("tags"));
        d.setCategory(rs.getString("category"));
        d.setDriveFileId(rs.getString("drive_file_id"));
        d.setDeletedAt(rs.getString("deleted_at"));
        return d;
    }
}

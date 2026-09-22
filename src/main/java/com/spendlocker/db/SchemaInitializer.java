package com.spendlocker.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SchemaInitializer {

    private static final String[] STATEMENTS = {
        """
        CREATE TABLE IF NOT EXISTS app_config (
            config_key TEXT PRIMARY KEY,
            config_value TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS documents (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            file_name TEXT NOT NULL,
            file_path TEXT NOT NULL UNIQUE,
            file_type TEXT CHECK(file_type IN ('PDF', 'PNG', 'JPG', 'XLSX', 'DOCX', 'OTHER')),
            file_size_bytes INTEGER NOT NULL,
            upload_source TEXT CHECK(upload_source IN ('Manual', 'Email', 'Excel Import', 'Google Drive')),
            upload_date TEXT DEFAULT (datetime('now', 'localtime')),
            extracted_text TEXT,
            tags TEXT,
            category TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS expenses (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            transaction_date TEXT NOT NULL,
            amount REAL NOT NULL,
            category TEXT NOT NULL,
            merchant_or_vendor TEXT,
            payment_method TEXT CHECK(payment_method IN ('Cash', 'Credit Card', 'Debit Card', 'Bank Transfer', 'UPI', 'Other')),
            notes TEXT,
            document_id INTEGER,
            created_at TEXT DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY(document_id) REFERENCES documents(id) ON DELETE SET NULL
        )
        """,
        // No CHECK on investment_type: like category/merchant, it's an open-ended, user-extensible taxonomy.
        """
        CREATE TABLE IF NOT EXISTS investments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            asset_name TEXT NOT NULL,
            asset_ticker TEXT,
            investment_type TEXT NOT NULL,
            purchase_date TEXT NOT NULL,
            principal_amount REAL NOT NULL,
            current_unit_price REAL DEFAULT 0.0,
            total_units REAL DEFAULT 1.0,
            current_total_value REAL GENERATED ALWAYS AS (current_unit_price * total_units) VIRTUAL,
            notes TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS categories (
            name TEXT PRIMARY KEY
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS document_categories (
            name TEXT PRIMARY KEY
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS merchants (
            name TEXT PRIMARY KEY
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS investment_types (
            name TEXT PRIMARY KEY
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS budgets (
            category TEXT PRIMARY KEY,
            monthly_limit REAL NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS fixed_deposits (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            depositor TEXT NOT NULL,
            bank TEXT NOT NULL,
            fd_number TEXT,
            principal REAL NOT NULL,
            rate_percent REAL NOT NULL DEFAULT 0,
            tenure_value INTEGER NOT NULL,
            tenure_unit TEXT NOT NULL CHECK(tenure_unit IN ('Days', 'Months', 'Years')),
            compounding TEXT NOT NULL CHECK(compounding IN ('Quarterly', 'Half-Yearly', 'Annually', 'Simple')),
            payout TEXT NOT NULL CHECK(payout IN ('Cumulative', 'Paid out periodically')),
            start_date TEXT NOT NULL,
            maturity_date TEXT NOT NULL,
            nominee TEXT,
            notes TEXT,
            deleted_at TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS recurring_expenses (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            category TEXT NOT NULL,
            amount REAL NOT NULL,
            merchant_or_vendor TEXT,
            payment_method TEXT,
            notes TEXT,
            frequency TEXT NOT NULL CHECK(frequency IN ('Weekly', 'Monthly', 'Yearly')),
            next_due_date TEXT NOT NULL,
            active INTEGER NOT NULL DEFAULT 1
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_doc_name ON documents(file_name)",
        "CREATE INDEX IF NOT EXISTS idx_doc_tags ON documents(tags)",
        "CREATE INDEX IF NOT EXISTS idx_expense_category_date ON expenses(category, transaction_date)",
        "CREATE INDEX IF NOT EXISTS idx_fd_maturity ON fixed_deposits(maturity_date)",
        "CREATE INDEX IF NOT EXISTS idx_fd_bank ON fixed_deposits(bank)"
    };

    public static void initialize(Connection connection) throws SQLException {
        migrateInvestmentTypeCheckConstraint(connection);
        try (Statement statement = connection.createStatement()) {
            for (String sql : STATEMENTS) {
                statement.execute(sql);
            }
        }
        addColumnIfMissing(connection, "documents", "category", "TEXT");
        addColumnIfMissing(connection, "documents", "drive_file_id", "TEXT");
        addColumnIfMissing(connection, "expenses", "deleted_at", "TEXT");
        addColumnIfMissing(connection, "investments", "deleted_at", "TEXT");
        addColumnIfMissing(connection, "documents", "deleted_at", "TEXT");
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String type) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    return; // already present
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    /**
     * Vaults created before custom investment types existed have a CHECK constraint locking
     * investment_type to the eight built-in names. SQLite can't drop a CHECK constraint in
     * place, so rebuild the table via the standard copy/drop/rename recipe when that old
     * constraint is detected.
     */
    private static void migrateInvestmentTypeCheckConstraint(Connection connection) throws SQLException {
        String existingDdl = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'investments'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    existingDdl = rs.getString(1);
                }
            }
        }
        if (existingDdl == null || !existingDdl.contains("CHECK(investment_type")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE investments RENAME TO investments_old");
            statement.execute("""
                CREATE TABLE investments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    asset_name TEXT NOT NULL,
                    asset_ticker TEXT,
                    investment_type TEXT NOT NULL,
                    purchase_date TEXT NOT NULL,
                    principal_amount REAL NOT NULL,
                    current_unit_price REAL DEFAULT 0.0,
                    total_units REAL DEFAULT 1.0,
                    current_total_value REAL GENERATED ALWAYS AS (current_unit_price * total_units) VIRTUAL,
                    notes TEXT
                )
                """);
            statement.execute("""
                INSERT INTO investments (id, asset_name, asset_ticker, investment_type, purchase_date,
                                          principal_amount, current_unit_price, total_units, notes)
                SELECT id, asset_name, asset_ticker, investment_type, purchase_date,
                       principal_amount, current_unit_price, total_units, notes
                  FROM investments_old
                """);
            statement.execute("DROP TABLE investments_old");
        }
    }
}

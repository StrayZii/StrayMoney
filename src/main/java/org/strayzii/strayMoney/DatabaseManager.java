package org.strayzii.strayMoney;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final StrayMoney plugin;
    private Connection connection;

    public DatabaseManager(StrayMoney plugin) {
        this.plugin = plugin;
    }

    public synchronized void connect() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            File file = new File(plugin.getDataFolder(), "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());

            createTable();
            plugin.getLogger().info("SQLite connected!");
        } catch (SQLException e) {
            throw new RuntimeException("Could not connect to SQLite!", e);
        }
    }

    private void createTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    balance INTEGER NOT NULL DEFAULT 0
                );
                """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public synchronized void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to close SQLite connection", e);
        }
    }

    public synchronized void createPlayer(UUID uuid) throws SQLException {
        String sql = "INSERT OR IGNORE INTO players (uuid, balance) VALUES (?, 0)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    public synchronized long getMoney(UUID id) throws SQLException {
        String sql = "SELECT balance FROM players WHERE uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("balance");
                }
            }
        }

        return 0;
    }

    public synchronized void addMoney(UUID id, long amount) throws SQLException {
        String sql = "INSERT INTO players (uuid, balance) VALUES (?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET balance = balance + excluded.balance";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.setLong(2, Math.max(0, amount));
            statement.executeUpdate();
        }
    }

    public synchronized void setMoney(UUID id, long amount) throws SQLException {
        String sql = "INSERT INTO players (uuid, balance) VALUES (?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.setLong(2, Math.max(0, amount));
            statement.executeUpdate();
        }
    }

    public synchronized void takeMoney(UUID id, long amount) throws SQLException {
        String sql = "UPDATE players SET balance = MAX(0, balance - ?) WHERE uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, Math.max(0, amount));
            statement.setString(2, id.toString());
            statement.executeUpdate();
        }
    }

    public synchronized boolean transfer(UUID from, UUID to, long amount) throws SQLException {
        if (amount <= 0) {
            return false;
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);

        try {
            String takeSql = "UPDATE players SET balance = balance - ? WHERE uuid = ? AND balance >= ?";
            try (PreparedStatement take = connection.prepareStatement(takeSql)) {
                take.setLong(1, amount);
                take.setString(2, from.toString());
                take.setLong(3, amount);

                if (take.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
            }

            createPlayer(to);

            String addSql = "UPDATE players SET balance = balance + ? WHERE uuid = ?";
            try (PreparedStatement add = connection.prepareStatement(addSql)) {
                add.setLong(1, amount);
                add.setString(2, to.toString());

                if (add.executeUpdate() == 0) {
                    connection.rollback();
                    throw new SQLException("Receiver row not found: " + to);
                }
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public synchronized void clearMoney(UUID id) throws SQLException {
        String sql = "DELETE FROM players WHERE uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        }
    }
}

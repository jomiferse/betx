package com.betx.adapter.persistence;

import com.betx.application.port.out.TelegramStateRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.stereotype.Component;

/** JDBC-backed SQLite repository for Telegram polling state. */
@Component
public class JdbcTelegramStateRepository implements TelegramStateRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";
    private static final String LAST_UPDATE_ID_KEY = "telegram_last_processed_update_id";

    private final String databasePath;

    public JdbcTelegramStateRepository() {
        this(DEFAULT_DATABASE_PATH);
    }

    public JdbcTelegramStateRepository(String databasePath) {
        this.databasePath = databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath;
    }

    @Override
    public long loadLastProcessedUpdateId(String databasePath) {
        try (Connection connection = connection(databasePath)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT state_value
                FROM telegram_state
                WHERE state_key = ?
                LIMIT 1
                """)) {
                statement.setString(1, LAST_UPDATE_ID_KEY);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Long.parseLong(resultSet.getString("state_value")) : 0L;
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read Telegram state.", exc);
        }
    }

    @Override
    public void saveLastProcessedUpdateId(String databasePath, long updateId) {
        try (Connection connection = connection(databasePath)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO telegram_state (state_key, state_value)
                VALUES (?, ?)
                ON CONFLICT(state_key) DO UPDATE SET state_value = excluded.state_value
                """)) {
                statement.setString(1, LAST_UPDATE_ID_KEY);
                statement.setString(2, String.valueOf(updateId));
                statement.executeUpdate();
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not save Telegram state.", exc);
        }
    }

    private Connection connection(String path) throws SQLException {
        Path database = Path.of(path == null || path.isBlank() ? databasePath : path);
        Path parent = database.toAbsolutePath().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (java.io.IOException exc) {
                throw new IllegalStateException("Could not create data directory: " + parent, exc);
            }
        }
        return DriverManager.getConnection("jdbc:sqlite:" + database);
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS telegram_state (
                    state_key TEXT PRIMARY KEY,
                    state_value TEXT NOT NULL
                )
                """);
        }
    }
}

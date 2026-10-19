package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

@ApplicationScoped
@Startup
public class LegacyDatabase {
    private static final int LEGACY_SCHEMA_VERSION = 0;
    private static final int HASH_SCHEMA_VERSION = 1;
    private static final String CREATE_USERS = "CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)";
    private static final String FIND_BY_USERNAME = "SELECT id, username, password_hash, display_name, role FROM legacy_users WHERE username=?";
    private static final String INSERT_USER = "INSERT OR IGNORE INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,?)";
    private final String databaseUrl;
    private final PasswordHasher passwords;

    public LegacyDatabase() {
        this(ConfigProvider.getConfig().getValue("auth.database.url", String.class), new PasswordHasher());
    }

    @Inject
    public LegacyDatabase(PasswordHasher passwords, @ConfigProperty(name = "auth.database.url") String databaseUrl) {
        this(databaseUrl, passwords);
    }

    LegacyDatabase(String databaseUrl, PasswordHasher passwords) {
        this.databaseUrl = databaseUrl;
        this.passwords = passwords;
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(databaseUrl)) {
                initializeSchema(connection);
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    public StoredCredentials findByUsername(String username) {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USERNAME)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return credentialsFrom(result);
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
        return null;
    }

    private StoredCredentials credentialsFrom(ResultSet result) throws SQLException {
        UserRecord user = new UserRecord(result.getLong("id"), result.getString("username"),
                result.getString("display_name"), result.getString("role"));
        return new StoredCredentials(user, result.getString("password_hash"));
    }

    private void initializeSchema(Connection connection) throws SQLException {
        try (Statement locking = connection.createStatement()) {
            locking.execute("PRAGMA locking_mode=EXCLUSIVE");
        }
        connection.setAutoCommit(false);
        try {
            migrateSchema(connection);
            seedUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
            seedUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
            connection.commit();
        } catch (Exception error) {
            connection.rollback();
            throw error;
        }
    }

    private void migrateSchema(Connection connection) throws SQLException {
        int version = schemaVersion(connection);
        if (version == HASH_SCHEMA_VERSION) {
            return;
        }
        if (version != LEGACY_SCHEMA_VERSION) {
            throw new SQLException("Unsupported authentication schema version");
        }
        boolean legacy = hasLegacyUsers(connection);
        try (Statement statement = connection.createStatement()) {
            if (legacy) {
                statement.executeUpdate("ALTER TABLE legacy_users RENAME TO legacy_users_plaintext");
            }
            statement.executeUpdate(CREATE_USERS);
        }
        if (legacy) {
            migratePasswords(connection);
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA user_version = " + HASH_SCHEMA_VERSION);
        }
    }

    private int schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            return version.getInt(1);
        }
    }

    private boolean hasLegacyUsers(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='legacy_users'")) {
            return tables.next();
        }
    }

    private void migratePasswords(Connection connection) throws SQLException {
        try (Statement select = connection.createStatement();
             ResultSet users = select.executeQuery("SELECT id,username,password,display_name,role FROM legacy_users_plaintext");
             PreparedStatement insert = connection.prepareStatement("INSERT INTO legacy_users(id,username,password_hash,display_name,role) VALUES (?,?,?,?,?)")) {
            while (users.next()) {
                insert.setLong(1, users.getLong("id"));
                insert.setString(2, users.getString("username"));
                insert.setString(3, passwords.hash(users.getString("password")));
                insert.setString(4, users.getString("display_name"));
                insert.setString(5, users.getString("role"));
                insert.executeUpdate();
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE legacy_users_plaintext");
        }
    }

    private void seedUser(Connection connection, String username, String password, String displayName, String role) throws SQLException {
        try (PreparedStatement existing = connection.prepareStatement(FIND_BY_USERNAME)) {
            existing.setString(1, username);
            try (ResultSet result = existing.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(INSERT_USER)) {
            insert.setString(1, username);
            insert.setString(2, passwords.hash(password));
            insert.setString(3, displayName);
            insert.setString(4, role);
            insert.executeUpdate();
        }
    }
}

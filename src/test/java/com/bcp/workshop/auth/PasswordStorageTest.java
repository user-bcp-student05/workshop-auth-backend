package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordStorageTest {
    private final PasswordHasher passwords = new PasswordHasher();

    @BeforeAll
    static void registerSqliteDriver() throws Exception {
        DriverManager.registerDriver(new org.sqlite.JDBC());
    }

    @Test
    void saltsAndVerifiesWithoutTruncatingPasswords() {
        String password = "\u00f1".repeat(100) + "tail";
        String hash = passwords.hash(password);
        assertNotEquals(password, hash);
        assertNotEquals(hash, passwords.hash(password));
        assertTrue(passwords.verify(password, hash));
        assertFalse(passwords.verify("\u00f1".repeat(100) + "other", hash));
        assertFalse(passwords.verify("wrong", hash));
    }

    @Test
    void rejectsInvalidStoredHashes() {
        assertFalse(passwords.verify(null, "hash"));
        assertFalse(passwords.verify("password", null));
        for (String invalid : new String[]{"cleartext", "other$600000$a$b", "pbkdf2-sha256$1$a$b",
                "pbkdf2-sha256$600000$!$!", "pbkdf2-sha256$600000$YQ==$Yg==",
                "pbkdf2-sha256$600000$AAAAAAAAAAAAAAAAAAAAAA==$Yg=="}) {
            assertFalse(passwords.verify("password", invalid));
        }
    }

    @Test
    void migratesExistingUsersOncePreservingIdentity() throws Exception {
        String url = databaseUrl();
        createLegacyDatabase(url);
        LegacyDatabase database = new LegacyDatabase(url, passwords);
        database.initialize();
        StoredCredentials credentials = database.findByUsername("legacy");
        assertNotNull(credentials);
        assertEquals(new UserRecord(17, "legacy", "Original Name", "TRAINER"), credentials.user());
        assertTrue(passwords.verify("original password", credentials.passwordHash()));
        assertNull(database.findByUsername("absent"));
        String firstHash = credentials.passwordHash();
        database.initialize();
        assertEquals(firstHash, database.findByUsername("legacy").passwordHash());
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            try (var version = statement.executeQuery("PRAGMA user_version")) {
                assertEquals(1, version.getInt(1));
            }
            try (var columns = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
                while (columns.next()) {
                    assertNotEquals("password", columns.getString("name"));
                }
            }
            try (var oldTable = statement.executeQuery("SELECT name FROM sqlite_master WHERE name='legacy_users_plaintext'")) {
                assertFalse(oldTable.next());
            }
        }
    }

    @Test
    void initializesNewDatabaseWithHashedLabUsers() throws Exception {
        LegacyDatabase database = new LegacyDatabase(databaseUrl(), passwords);
        database.initialize();
        assertTrue(passwords.verify("Workshop123!", database.findByUsername("student").passwordHash()));
        assertTrue(passwords.verify("Trainer123!", database.findByUsername("trainer").passwordHash()));
    }

    @Test
    void rollsBackAllSchemaChangesWhenHashingFails() throws Exception {
        String url = databaseUrl();
        createLegacyDatabase(url);
        PasswordHasher failingHasher = new PasswordHasher() {
            @Override
            public String hash(String password) {
                throw new IllegalStateException("controlled hashing failure");
            }
        };
        new LegacyDatabase(url, failingHasher).initialize();
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            try (var version = statement.executeQuery("PRAGMA user_version")) {
                assertEquals(0, version.getInt(1));
            }
            try (var original = statement.executeQuery("SELECT password FROM legacy_users WHERE id=17")) {
                assertEquals("original password", original.getString(1));
            }
        }
        new LegacyDatabase(url, passwords).initialize();
        assertNotNull(new LegacyDatabase(url, passwords).findByUsername("legacy"));
    }

    @Test
    void refusesUnknownSchemaVersionWithoutMutation() throws Exception {
        String url = databaseUrl();
        createLegacyDatabase(url);
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA user_version=5");
        }
        new LegacyDatabase(url, passwords).initialize();
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement();
             var version = statement.executeQuery("PRAGMA user_version")) {
            assertEquals(5, version.getInt(1));
        }
    }

    private String databaseUrl() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "password-storage-");
        return "jdbc:sqlite:" + directory.resolve("users.db").toAbsolutePath();
    }

    private void createLegacyDatabase(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO legacy_users VALUES(17,'legacy','original password','Original Name','TRAINER')");
        }
    }
}
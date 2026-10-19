package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Map;

public class TestSigningKeys implements QuarkusTestResourceLifecycleManager {
    private Path privateKeyFile;

    static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception error) {
            throw new IllegalStateException("Cannot generate test key", error);
        }
    }

    static void writePrivateKey(Path file, PrivateKey key) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded());
        Files.writeString(file, "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");
    }

    @Override
    public Map<String, String> start() {
        try {
            Path directory = Files.createTempDirectory(Path.of("target"), "auth-test-");
            privateKeyFile = directory.resolve("private-key.pem");
            writePrivateKey(privateKeyFile, generate().getPrivate());
            return Map.of("auth.token.key.location", privateKeyFile.toUri().toString(),
                    "auth.database.url", "jdbc:sqlite:" + directory.resolve("users.db").toAbsolutePath());
        } catch (Exception error) {
            throw new IllegalStateException("Cannot initialize isolated tests", error);
        }
    }

    @Override
    public void stop() {
        try {
            if (privateKeyFile != null) {
                Files.deleteIfExists(privateKeyFile);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Cannot remove test key", error);
        }
    }
}
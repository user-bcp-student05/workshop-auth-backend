package com.bcp.workshop.auth;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final KeyPair KEYS = TestSigningKeys.generate();
    private final TokenService tokens = new TokenService(KEYS.getPrivate(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void verifiesSignatureClaimsAndLifetimeWithoutSecrets() throws Exception {
        String token = tokens.create("student", "STUDENT");
        var claims = verifier(KEYS.getPublic(), NOW).processToClaims(token);
        assertEquals("student", claims.getSubject());
        assertEquals("STUDENT", claims.getStringClaimValue("role"));
        assertEquals(NOW.getEpochSecond(), claims.getIssuedAt().getValue());
        assertEquals(TokenService.LIFETIME_SECONDS,
                claims.getExpirationTime().getValue() - claims.getIssuedAt().getValue());
        assertFalse(claims.hasClaim("secret"));
        assertFalse(claims.hasClaim("password"));
        assertFalse(token.contains("WORKSHOP_SECRET"));
    }

    @Test
    void rejectsAlteredSignatureAndWrongKey() {
        String token = tokens.create("student", "STUDENT");
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String altered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
        assertThrows(InvalidJwtException.class, () -> verifier(KEYS.getPublic(), NOW).processToClaims(altered));
        assertThrows(InvalidJwtException.class,
                () -> verifier(TestSigningKeys.generate().getPublic(), NOW).processToClaims(token));
    }

    @Test
    void rejectsExpiredTokensAndUnapprovedAlgorithms() {
        String token = tokens.create("student", "STUDENT");
        assertThrows(InvalidJwtException.class,
                () -> verifier(KEYS.getPublic(), NOW.plusSeconds(TokenService.LIFETIME_SECONDS + 1)).processToClaims(token));
        String otherAlgorithm = Jwt.claims().subject("student").issuedAt(NOW).expiresAt(NOW.plusSeconds(3600))
                .jws().algorithm(SignatureAlgorithm.RS512).sign(KEYS.getPrivate());
        assertThrows(InvalidJwtException.class,
                () -> verifier(KEYS.getPublic(), NOW).processToClaims(otherAlgorithm));
    }

    @Test
    void refusesMissingInvalidAndWeakKeys() throws Exception {
        assertThrows(IllegalStateException.class, () -> new TokenService((String) null));
        assertThrows(IllegalStateException.class, () -> new TokenService(" "));
        assertThrows(IllegalStateException.class, () -> new TokenService("target/absent-signing-key.pem"));
        Path invalid = Files.createTempFile(Path.of("target"), "invalid-key-", ".pem");
        try {
            Files.writeString(invalid, "not a private key");
            assertThrows(IllegalStateException.class, () -> new TokenService(invalid.toString()));
        } finally {
            Files.deleteIfExists(invalid);
        }
        KeyPairGenerator weak = KeyPairGenerator.getInstance("RSA");
        weak.initialize(1024);
        assertThrows(IllegalStateException.class,
                () -> new TokenService(weak.generateKeyPair().getPrivate(), Clock.systemUTC()));
        KeyPairGenerator other = KeyPairGenerator.getInstance("EC");
        assertThrows(IllegalStateException.class,
                () -> new TokenService(other.generateKeyPair().getPrivate(), Clock.systemUTC()));
    }

    @Test
    void loadsExternalPemFromPathAndUri() throws Exception {
        Path key = Files.createTempFile(Path.of("target"), "signing-test-", ".pem");
        try {
            TestSigningKeys.writePrivateKey(key, KEYS.getPrivate());
            for (String location : new String[]{key.toString(), key.toUri().toString()}) {
                String token = new TokenService(location).create("student", "STUDENT");
                assertEquals("student", verifier(KEYS.getPublic(), Instant.now()).processToClaims(token).getSubject());
            }
        } finally {
            Files.deleteIfExists(key);
        }
    }

    private JwtConsumer verifier(PublicKey key, Instant time) {
        return new JwtConsumerBuilder().setVerificationKey(key)
                .setJwsAlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, "RS256")
                .setRequireSubject().setRequireIssuedAt().setRequireExpirationTime()
                .setSkipDefaultAudienceValidation().setEvaluationTime(NumericDate.fromSeconds(time.getEpochSecond()))
                .build();
    }
}
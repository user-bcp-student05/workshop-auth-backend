package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Startup
@ApplicationScoped
public class TokenService {
    public static final long LIFETIME_SECONDS = 3600;
    private static final int MINIMUM_RSA_BITS = 2048;
    private final PrivateKey signingKey;
    private final Clock clock;

    @Inject
    public TokenService(@ConfigProperty(name = "auth.token.key.location") String location) {
        this(loadKey(location), Clock.systemUTC());
    }

    TokenService(PrivateKey signingKey, Clock clock) {
        if (!(signingKey instanceof RSAPrivateKey rsa) || rsa.getModulus().bitLength() < MINIMUM_RSA_BITS) {
            throw new IllegalStateException("A valid RSA signing key is required");
        }
        this.signingKey = signingKey;
        this.clock = clock;
    }

    public String create(String username, String role) {
        long issuedAt = clock.instant().getEpochSecond();
        return Jwt.claims().subject(username).claim("role", role)
                .issuedAt(issuedAt).expiresAt(issuedAt + LIFETIME_SECONDS)
                .jws().algorithm(SignatureAlgorithm.RS256).sign(signingKey);
    }

    private static PrivateKey loadKey(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("An external signing key location is required");
        }
        try {
            Path keyFile = location.startsWith("file:") ? Path.of(URI.create(location)) : Path.of(location);
            return KeyUtils.decodePrivateKey(Files.readString(keyFile), SignatureAlgorithm.RS256);
        } catch (Exception error) {
            throw new IllegalStateException("Signing key unavailable");
        }
    }
}
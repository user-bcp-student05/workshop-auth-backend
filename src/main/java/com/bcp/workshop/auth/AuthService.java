package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthService {
    private final LegacyDatabase database;
    private final TokenService tokens;
    private final PasswordHasher passwords;

    @Inject
    public AuthService(LegacyDatabase database, TokenService tokens, PasswordHasher passwords) {
        this.database = database;
        this.tokens = tokens;
        this.passwords = passwords;
    }

    public Authentication authenticate(String username, String password) {
        StoredCredentials credentials = database.findByUsername(username);
        if (credentials == null || !passwords.verify(password, credentials.passwordHash())) {
            return null;
        }
        UserRecord user = credentials.user();
        return new Authentication(user, tokens.create(user.username(), user.role()));
    }

    public record Authentication(UserRecord user, String token) {}
}
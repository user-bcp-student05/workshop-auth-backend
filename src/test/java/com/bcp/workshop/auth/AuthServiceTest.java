package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthServiceTest {
    @Test
    void coordinatesLookupPasswordVerificationAndToken() {
        PasswordHasher passwords = new PasswordHasher();
        StoredCredentials stored = new StoredCredentials(new UserRecord(1, " spaced ", "Test", "STUDENT"),
                passwords.hash(" password "));
        LegacyDatabase repository = new LegacyDatabase("jdbc:sqlite:unused", passwords) {
            @Override
            public StoredCredentials findByUsername(String username) {
                return " spaced ".equals(username) ? stored : null;
            }
        };
        AuthService service = new AuthService(repository,
                new TokenService(TestSigningKeys.generate().getPrivate(), Clock.systemUTC()), passwords);
        assertNull(service.authenticate("absent", " password "));
        assertNull(service.authenticate(" spaced ", "wrong"));
        AuthService.Authentication authentication = service.authenticate(" spaced ", " password ");
        assertNotNull(authentication);
        assertEquals(stored.user(), authentication.user());
        assertEquals(3, authentication.token().split("\\.").length);
    }
}
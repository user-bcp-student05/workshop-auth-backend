package com.bcp.workshop.auth;

import org.apache.commons.text.StringEscapeUtils;

public record LoginResponse(String token, long expiresIn, UserResponse user, String debug) {
    private static final String LEGACY_DEBUG = "authenticated-with-legacy-flow";

    public static LoginResponse from(AuthService.Authentication authentication) {
        UserRecord user = authentication.user();
        return new LoginResponse(authentication.token(), TokenService.LIFETIME_SECONDS,
                new UserResponse(user.username(), StringEscapeUtils.escapeHtml4(user.displayName()), user.role()),
                LEGACY_DEBUG);
    }

    public record UserResponse(String username, String displayName, String role) {}
}
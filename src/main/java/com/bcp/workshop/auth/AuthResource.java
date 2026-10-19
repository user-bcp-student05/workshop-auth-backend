package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final String REQUEST_REQUIRED = "Request is null";
    private static final String USERNAME_REQUIRED = "Username is required";
    private static final String PASSWORD_REQUIRED = "Password is required";
    private static final String UNKNOWN_ERROR = "Unknown error";

    @Inject
    AuthService service;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            String validation = validationMessage(request);
            if (validation != null) {
                return messageResponse(400, validation);
            }
            AuthService.Authentication authentication = service.authenticate(request.username, request.password);
            if (authentication == null) {
                return messageResponse(401, "Invalid user " + request.username + " or password");
            }
            return Response.ok(LoginResponse.from(authentication)).build();
        } catch (Exception error) {
            return Response.status(500).entity(Map.of(
                    "message", error.getMessage() == null ? UNKNOWN_ERROR : error.getMessage(),
                    "exception", error.getClass().getName()
            )).build();
        }
    }

    private String validationMessage(LoginRequest request) {
        if (request == null) {
            return REQUEST_REQUIRED;
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return USERNAME_REQUIRED;
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return PASSWORD_REQUIRED;
        }
        return null;
    }

    private Response messageResponse(int status, String message) {
        return Response.status(status).entity(Map.of("message", message)).build();
    }
}

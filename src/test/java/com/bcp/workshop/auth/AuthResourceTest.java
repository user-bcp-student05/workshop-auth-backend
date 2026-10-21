package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.smallrye.health.SmallRyeHealthReporter;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestSigningKeys.class)
class AuthResourceTest {
    private static final String LONG_PASSWORD = "x".repeat(100);

    @Inject
    SmallRyeHealthReporter healthReporter;

    @BeforeAll
    static void seedCharacterizationUsers() throws Exception {
        new LegacyDatabase().initialize();
        insertUser(" spaced ", " password ", "<&\"'\u00e9>");
        insertUser("long-password", LONG_PASSWORD, "Long Password");
        insertUser("unicode", "contrase\u00f1a", "Unicode");
    }

    private static void insertUser(String username, String password, String displayName) throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl());
             var statement = connection.prepareStatement("INSERT OR IGNORE INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,'STUDENT')")) {
            statement.setString(1, username);
            statement.setString(2, new PasswordHasher().hash(password));
            statement.setString(3, displayName);
            statement.executeUpdate();
        }
    }

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @Test
    void preservesCompleteSuccessResponse() {
        assertLogin("student", "Workshop123!", "Workshop Student", "STUDENT");
        assertLogin("trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
        assertLogin(" spaced ", " password ", "&lt;&amp;&quot;'&eacute;&gt;", "STUDENT");
        assertLogin("long-password", LONG_PASSWORD, "Long Password", "STUDENT");
        assertLogin("unicode", "contrase\u00f1a", "Unicode", "STUDENT");
    }

    @Test
    void acceptsOptionalClientValues() {
        for (String client : new String[]{null, "", "angular-workshop"}) {
            LoginRequest request = request("student", "Workshop123!");
            request.client = client;
            given().contentType("application/json").accept("application/json").body(request)
                    .when().post("/auth/login").then().statusCode(200)
                    .body("user.username", is("student"))
                    .body("client", is((Object) null));
        }
    }

    @Test
    void rejectsNullAndAbsentHttpBodies() {
        for (String body : new String[]{"null", ""}) {
            given().contentType("application/json").accept("application/json").body(body)
                    .when().post("/auth/login").then().statusCode(400)
                    .body("", aMapWithSize(1)).body("message", is("Request is null"));
        }
    }

    private void assertLogin(String username, String password, String displayName, String role) {
        given().contentType("application/json")
                .body(Map.of("username", username, "password", password, "ignored", true))
                .when().post("/auth/login")
                .then().statusCode(200).contentType("application/json")
                .body("", aMapWithSize(4))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"))
                .body("user", aMapWithSize(3))
                .body("user.username", is(username))
                .body("user.displayName", is(displayName))
                .body("user.role", is(role));
    }

    @Test
    void preservesValidationOrderAndMessages() {
        assertMessage(new AuthResource().login(null), 400, "Request is null");
        for (String missing : new String[]{null, "", " \t "}) {
            assertHttpMessage(new LoginRequest(), 400, "Username is required");
            LoginRequest missingUsername = request(missing, null);
            assertHttpMessage(missingUsername, 400, "Username is required");
            assertHttpMessage(request("student", missing), 400, "Password is required");
        }
    }

    @Test
    void preservesInvalidCredentialsAndWhitespace() {
        assertHttpMessage(request("absent", "incorrect"), 401, "Invalid user absent or password");
        assertHttpMessage(request("student", "incorrect"), 401, "Invalid user student or password");
        assertHttpMessage(request(" student ", "Workshop123!"), 401, "Invalid user  student  or password");
        assertHttpMessage(request("student", " Workshop123! "), 401, "Invalid user student or password");
    }

    @Test
    void rejectsSqlAsCredentials() {
        String username = "student' --";
        assertHttpMessage(request(username, "incorrect"), 401, "Invalid user " + username + " or password");
        assertHttpMessage(request("absent", "' OR '1'='1"), 401, "Invalid user absent or password");
    }

    @Test
    void authenticatesLiteralApostrophe() throws Exception {
        insertUser("o'brien", "quoted-password", "Quoted User");
        assertLogin("o'brien", "quoted-password", "Quoted User", "STUDENT");
    }

    @Test
    void resourceDoesNotDependOnPersistence() {
        assertFalse(Arrays.stream(AuthResource.class.getDeclaredFields())
                .anyMatch(field -> field.getType().equals(LegacyDatabase.class)));
    }

    @Test
    void storesOnlyPasswordHashes() throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl());
             var statement = connection.createStatement();
             var columns = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            boolean hashColumn = false;
            while (columns.next()) {
                assertFalse("password".equals(columns.getString("name")), "Plaintext column must be removed");
                hashColumn |= "password_hash".equals(columns.getString("name"));
            }
            assertTrue(hashColumn);
        }
    }

    @Test
    void emitsSignedToken() {
        String token = given().contentType("application/json")
                .body(request("student", "Workshop123!"))
                .when().post("/auth/login").then().statusCode(200)
                .extract().path("token");
        assertEquals(3, token.split("\\.").length, "A signed JWT has three segments");
    }

    @Test
    void usesReviewedDependencyVersions() {
        assertEquals("3.33.2.1", io.quarkus.runtime.Quarkus.class.getPackage().getImplementationVersion());
        assertEquals("1.15.0", org.apache.commons.text.StringEscapeUtils.class.getPackage().getImplementationVersion());
    }

    @Test
    void preservesExceptionDetailsAndFallback() {
        for (String message : new String[]{"controlled failure", null}) {
            AuthResource resource = new AuthResource();
            LegacyDatabase database = new LegacyDatabase() {
                @Override
                public StoredCredentials findByUsername(String username) {
                    throw new IllegalStateException(message);
                }
            };
            resource.service = new AuthService(database,
                    new TokenService(TestSigningKeys.generate().getPrivate(), Clock.systemUTC()), new PasswordHasher());
            try (Response response = resource.login(request("student", "Workshop123!"))) {
                assertEquals(500, response.getStatus());
                Map<?, ?> body = (Map<?, ?>) response.getEntity();
                assertEquals(message == null ? "Unknown error" : message, body.get("message"));
                assertEquals(IllegalStateException.class.getName(), body.get("exception"));
            }
            QuarkusMock.installMockForType(resource.service, AuthService.class);
            given().contentType("application/json").accept("application/json")
                    .body(request("student", "Workshop123!"))
                    .when().post("/auth/login").then().statusCode(500)
                    .body("", aMapWithSize(2))
                    .body("message", is(message == null ? "Unknown error" : message))
                    .body("exception", is(IllegalStateException.class.getName()));
        }
    }

    @Test
    void preservesJdbcFailureAsUnauthorized() throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl());
             var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users RENAME TO unavailable_users");
            try {
                assertNull(new LegacyDatabase().findByUsername("student"));
                assertHttpMessage(request("student", "Workshop123!"), 401, "Invalid user student or password");
            } finally {
                statement.executeUpdate("ALTER TABLE unavailable_users RENAME TO legacy_users");
            }
        }
    }

    @Test
            void characterizesHealthStatus() {
        given().when().get("/q/health").then().statusCode(200);
    }

    @Test
    void reportsHealthyWithoutAuthentication() {
        assertHealthy(given().accept("application/json").when().get("/q/health").then());
    }

    @Test
    void acceptsEmptyHealthBodyAndUnmatchedAccept() {
        for (String accept : new String[]{"application/json", "application/xml"}) {
            assertHealthy(given().contentType("application/json").accept(accept).body("{}")
                    .when().get("/q/health").then());
        }
    }

    @Test
    void reportsUnavailableForTemporaryFailedCheck() {
        HealthCheck failedCheck = () -> HealthCheckResponse.down("testing-only");
        healthReporter.addHealthCheck(failedCheck);
        try {
            given().accept("application/json").when().get("/q/health").then()
                    .statusCode(503).contentType("application/json")
                    .body("", aMapWithSize(2)).body("status", is("DOWN"))
                    .body("checks", hasSize(1))
                    .body("checks[0].name", is("testing-only"))
                    .body("checks[0].status", is("DOWN"));
        } finally {
            healthReporter.removeHealthCheck(failedCheck);
        }
        assertHealthy(given().accept("application/json").when().get("/q/health").then());
    }

    private void assertHealthy(ValidatableResponse response) {
        response.statusCode(200).contentType("application/json")
                .body("", aMapWithSize(2)).body("status", is("UP"))
                .body("checks", empty());
    }

    @Test
    void corsHonorsConfiguredOrigins() {
        String origin = "https://foreign.invalid";
        String allowed = given().header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login").then().extract().header("Access-Control-Allow-Origin");
        String configured = ConfigProvider.getConfig().getValue("quarkus.http.cors.origins", String.class);
        assertEquals("/.*/".equals(configured) ? origin : null, allowed);
    }

    private static LoginRequest request(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.username = username;
        request.password = password;
        return request;
    }

    private static String databaseUrl() {
        return ConfigProvider.getConfig().getValue("auth.database.url", String.class);
    }

    private void assertHttpMessage(LoginRequest request, int status, String message) {
        given().contentType("application/json").body(request)
                .when().post("/auth/login").then().statusCode(status)
                .body("", aMapWithSize(1)).body("message", is(message));
    }

    private void assertMessage(Response response, int status, String message) {
        try (response) {
            assertEquals(status, response.getStatus());
            assertEquals(Map.of("message", message), response.getEntity());
        }
    }
}

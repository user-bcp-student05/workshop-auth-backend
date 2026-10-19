package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusIntegrationTest
@QuarkusTestResource(TestSigningKeys.class)
class AuthProtocolIT {
    @Test
    void rejectsMalformedJsonAndIncompatibleTypes() {
        for (String body : new String[]{"{", "{\"username\":{},\"password\":\"x\"}"}) {
            given().contentType("application/json").accept("application/json").body(body)
                    .when().post("/auth/login").then().statusCode(400).body(is(""));
        }
    }

    @Test
    void rejectsUnsupportedContentType() {
        given().contentType("text/plain").accept("application/json").body("text")
                .when().post("/auth/login").then().statusCode(415).body(is(""));
    }

    @Test
    void rejectsUnacceptableResponseType() {
        given().contentType("application/json").accept("application/xml").body("{}")
                .when().post("/auth/login").then().statusCode(406).body(is(""));
    }

    @Test
    void rejectsWrongLoginMethod() {
        given().accept("application/json")
                .when().get("/auth/login").then().statusCode(405).body(is(""));
    }
}
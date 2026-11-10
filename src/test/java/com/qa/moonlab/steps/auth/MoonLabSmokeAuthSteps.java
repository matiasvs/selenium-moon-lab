package com.qa.moonlab.steps.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.moonlab.api.RestAssuredHelper;
import com.qa.moonlab.config.EnvConfig;
import com.qa.moonlab.context.TestContext;
import com.qa.moonlab.db.DatabaseHelper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Map;

/**
 * Step Definitions for Moon-Lab Smoke & Auth REST API certification using RestAssured 5.5+
 * and cross-layer PostgreSQL 16 verification via HikariCP (DatabaseHelper).
 */
public class MoonLabSmokeAuthSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final TestContext context;

    public MoonLabSmokeAuthSteps(TestContext context) {
        this.context = context;
    }

    @Given("the client has no Bearer token in the context")
    public void clearBearerToken() {
        context.setAccessToken(null);
    }

    @Given("the user authenticates via RestAssured POST {string} with configured QA credentials")
    public void loginWithConfiguredCredentials(String path) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
                "email", EnvConfig.getQaEmail(),
                "password", EnvConfig.getQaPassword()
        ));
        Response res = RestAssuredHelper.sendRequest(
                "POST " + path + " (QA Auth)",
                "POST",
                path,
                null,
                body,
                null
        );
        context.setLastResponse(res);
        if (res.getStatusCode() == 201 || res.getStatusCode() == 200) {
            String token = res.jsonPath().getString("access_token");
            context.setAccessToken(token);
        }
    }

    @When("a POST request to {string} is sent via RestAssured with raw JSON {string}")
    public void sendRawPostRequest(String path, String rawJson) {
        Response res = RestAssuredHelper.sendRequest(
                "POST " + path + " (Negative Validation)",
                "POST",
                path,
                context.getAccessToken(),
                rawJson,
                null
        );
        context.setLastResponse(res);
    }

    @When("a POST request to {string} is sent via RestAssured with email {string} and password {string}")
    public void sendLoginPostWithCredentials(String path, String email, String password) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
                "email", email,
                "password", password
        ));
        Response res = RestAssuredHelper.sendRequest(
                "POST " + path + " (" + email + ")",
                "POST",
                path,
                null,
                body,
                null
        );
        context.setLastResponse(res);
    }

    @Then("the HTTP response is JSON and responds within {long} ms")
    public void verifyJsonContentTypeAndSla(long maxMillis) {
        Response res = context.getLastResponse();
        Assertions.assertNotNull(res, "No HTTP response stored in context");
        res.then().contentType(ContentType.JSON);
        Assertions.assertTrue(
                res.getTime() < maxMillis,
                "Expected response time < " + maxMillis + " ms, but got " + res.getTime() + " ms"
        );
    }

    @Then("the HTTP response matches the JSON schema {string}")
    public void verifyJsonSchema(String classpathSchema) {
        Response res = context.getLastResponse();
        Assertions.assertNotNull(res, "No HTTP response stored in context");
        res.then().assertThat().body(JsonSchemaValidator.matchesJsonSchemaInClasspath(classpathSchema));
    }

    @Then("the JSON path {string} equals {string} and {string} equals {string}")
    public void verifyTwoJsonPaths(String path1, String expected1, String path2, String expected2) {
        JsonPath jp = context.getLastResponse().jsonPath();
        Assertions.assertEquals(expected1, jp.getString(path1));
        Assertions.assertEquals(expected2, jp.getString(path2));
    }

    @Then("the login response returns a valid Bearer JWT for role {string} without exposing password fields")
    public void verifyLoginJwtAndSensitiveMasking(String expectedRole) {
        Response res = context.getLastResponse();
        JsonPath jp = res.jsonPath();

        String token = jp.getString("access_token");
        Assertions.assertNotNull(token, "access_token must not be null");
        Assertions.assertEquals(3, token.split("\\.").length, "access_token must be a 3-part signed JWT");
        Assertions.assertEquals(expectedRole, jp.getString("user.roleCode"));
        Assertions.assertEquals(EnvConfig.getQaEmail(), jp.getString("user.email"));

        // Mandatory sensitive data masking checks
        Assertions.assertNull(jp.get("password"), "Sensitive field 'password' must never be exposed");
        Assertions.assertNull(jp.get("password_hash"), "Sensitive field 'password_hash' must never be exposed");
        Assertions.assertNull(jp.get("user.password"), "Sensitive field 'user.password' must never be exposed");
        Assertions.assertNull(jp.get("user.passwordHash"), "Sensitive field 'user.passwordHash' must never be exposed");
        Assertions.assertNull(jp.get("user.password_hash"), "Sensitive field 'user.password_hash' must never be exposed");
    }

    @Then("the PostgreSQL {string} table confirms email {string} has role {string} and status {string}")
    public void verifyUserInPostgres(String tableName, String email, String expectedRole, String expectedStatus) {
        List<Map<String, Object>> rows = DatabaseHelper.queryList(
                "Verify User " + email,
                "SELECT id, email, role_code, status, is_active, failed_attempts FROM " + tableName + " WHERE email = ?",
                email
        );
        Assertions.assertEquals(1, rows.size(), "Expected 1 user row in " + tableName + " for " + email);
        Map<String, Object> dbUser = rows.get(0);
        Assertions.assertEquals(expectedRole, dbUser.get("role_code"));
        Assertions.assertEquals(expectedStatus, dbUser.get("status"));
        Assertions.assertEquals(Boolean.TRUE, dbUser.get("is_active"));
    }

    @Then("the {string} profile matches email {string}, role {string} and does not expose password hashes")
    public void verifyAuthMeProfile(String endpoint, String expectedEmail, String expectedRole) {
        JsonPath jp = context.getLastResponse().jsonPath();
        Assertions.assertEquals(expectedEmail, jp.getString("authenticatedUser.email"));
        Assertions.assertEquals(expectedRole, jp.getString("authenticatedUser.role_code"));
        List<String> perms = jp.getList("authenticatedUser.permissions");
        Assertions.assertNotNull(perms, "Permissions array must be present in " + endpoint);
        Assertions.assertFalse(perms.isEmpty(), "Permissions array must not be empty");

        // Sensitive fields check
        Assertions.assertNull(jp.get("authenticatedUser.password"));
        Assertions.assertNull(jp.get("authenticatedUser.password_hash"));
        Assertions.assertNull(jp.get("authenticatedUser.passwordHash"));
    }

    @Then("the {string} user id and document_id match the PostgreSQL record in {string}")
    public void crossCheckAuthMeWithPostgres(String endpoint, String tableName) {
        JsonPath jp = context.getLastResponse().jsonPath();
        int apiUserId = jp.getInt("authenticatedUser.id");
        String apiDocId = jp.getString("authenticatedUser.document_id");
        String apiEmail = jp.getString("authenticatedUser.email");

        List<Map<String, Object>> rows = DatabaseHelper.queryList(
                "Cross-check " + endpoint + " vs SQL",
                "SELECT id, email, document_id, role_code FROM " + tableName + " WHERE id = ?",
                apiUserId
        );
        Assertions.assertEquals(1, rows.size());
        Assertions.assertEquals(apiEmail, rows.get(0).get("email"));
        Assertions.assertEquals(apiDocId, rows.get(0).get("document_id"));
    }

    @Then("the {string} catalog contains at least {int} roles with required fields and matches PostgreSQL {string} count")
    public void verifyRolesCatalogAgainstPostgres(String endpoint, int minRoles, String tableName) {
        JsonPath jp = context.getLastResponse().jsonPath();
        int apiCount = jp.getInt("count");
        List<Map<String, Object>> apiRoles = jp.getList("data");

        Assertions.assertTrue(apiCount >= minRoles, "Expected at least " + minRoles + " roles, got " + apiCount);
        Assertions.assertEquals(apiCount, apiRoles.size());

        for (Map<String, Object> role : apiRoles) {
            Assertions.assertNotNull(role.get("id"), "Role id is required");
            Assertions.assertNotNull(role.get("code"), "Role code is required");
            Assertions.assertNotNull(role.get("name"), "Role name is required");
            Assertions.assertNotNull(role.get("user_type"), "Role user_type is required");
            Assertions.assertNotNull(role.get("permissions"), "Role permissions list is required");
        }

        List<Map<String, Object>> dbCountRows = DatabaseHelper.queryList(
                "Count " + tableName,
                "SELECT COUNT(*)::int AS total_roles FROM " + tableName
        );
        int sqlTotalRoles = ((Number) dbCountRows.get(0).get("total_roles")).intValue();
        Assertions.assertEquals(sqlTotalRoles, apiCount, "API /roles count must match PostgreSQL " + tableName);
    }

    @Then("the branch catalogs health flag {string} is true and matches active branches in PostgreSQL {string}")
    public void verifyBranchCatalogsHealth(String flagField, String tableName) {
        JsonPath jp = context.getLastResponse().jsonPath();
        Assertions.assertTrue(jp.getBoolean(flagField), "Expected healthy=true in /health/catalogs");
        List<Map<String, Object>> branches = jp.getList("branches");
        Assertions.assertNotNull(branches);
        Assertions.assertFalse(branches.isEmpty(), "Expected at least 1 branch in /health/catalogs");

        List<Map<String, Object>> sqlBranches = DatabaseHelper.queryList(
                "Verify Active Branches Catalogs",
                "SELECT COUNT(*)::int AS total_branches FROM " + tableName + " WHERE status = 'ACTIVE'"
        );
        int activeDbBranches = ((Number) sqlBranches.get(0).get("total_branches")).intValue();
        Assertions.assertTrue(activeDbBranches >= 1, "Expected active branches in " + tableName);
    }

    @Then("the CUIT validation result has valid true and formatted {string}")
    public void verifyValidCuitResult(String expectedFormatted) {
        JsonPath jp = context.getLastResponse().jsonPath();
        Assertions.assertTrue(jp.getBoolean("valid"));
        Assertions.assertEquals(expectedFormatted, jp.getString("formatted"));
    }

    @Then("the CUIT validation result has valid false and suggested CUIT {string}")
    public void verifyInvalidCuitResult(String expectedSuggested) {
        JsonPath jp = context.getLastResponse().jsonPath();
        Assertions.assertFalse(jp.getBoolean("valid"));
        Assertions.assertEquals(expectedSuggested, jp.getString("suggestedValidCuit"));
    }

    @Then("the error response contains status {int} and {string}")
    public void verifyUnauthorizedErrorBody(int expectedStatus, String expectedError) {
        JsonPath jp = context.getLastResponse().jsonPath();
        Assertions.assertEquals(expectedStatus, jp.getInt("statusCode"));
        Assertions.assertEquals(expectedError, jp.getString("error"));
    }

    @Then("the 400 validation response reports mandatory fields {string} and {string}")
    public void verifyBadRequestValidationFields(String field1, String field2) {
        JsonPath jp = context.getLastResponse().jsonPath();
        Assertions.assertEquals(400, jp.getInt("statusCode"));
        String message = jp.getString("message");
        Assertions.assertNotNull(message);
        Assertions.assertTrue(message.contains(field1), "Expected validation message to mention " + field1);
        Assertions.assertTrue(message.contains(field2), "Expected validation message to mention " + field2);
    }

    @Then("the 401 response does not return any {string}")
    public void verifyNoTokenOnUnauthorized(String tokenField) {
        JsonPath jp = context.getLastResponse().jsonPath();
        Assertions.assertNull(jp.get(tokenField), "Unauthorized response must never include " + tokenField);
    }
}

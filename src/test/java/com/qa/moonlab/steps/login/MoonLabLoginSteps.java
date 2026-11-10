package com.qa.moonlab.steps.login;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.moonlab.api.RestAssuredHelper;
import com.qa.moonlab.context.TestContext;
import com.qa.moonlab.db.DatabaseHelper;
import com.qa.moonlab.driver.DriverFactory;
import com.qa.moonlab.pages.login.MoonLabLoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Map;

/**
 * Multi-Layer Step Definitions for Moon-Lab Web Login Happy Path (@go-qa):
 *  - Web UI: Selenium 4.27+ via MoonLabLoginPage (BasePage smart waits + 12vh Dual HUD)
 *  - REST API: RestAssured 5.5+ via RestAssuredHelper
 *  - SQL Database: PostgreSQL 16 via DatabaseHelper (HikariCP)
 */
public class MoonLabLoginSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final TestContext context;
    private String latestOtpCode;

    public MoonLabLoginSteps(TestContext context) {
        this.context = context;
    }

    private MoonLabLoginPage loginPage() {
        return new MoonLabLoginPage(DriverFactory.getDriver());
    }

    @Given("the user opens the Moon-Lab Web Login screen in the browser")
    public void openMoonLabWebLoginScreen() {
        loginPage().openLoginScreen();
    }

    @When("the user submits the Web Login form with email {string} and password {string}")
    public void submitWebLoginForm(String email, String password) {
        loginPage().loginWithCredentials(email, password);
    }

    @When("the user clicks the 1-Click Quick Login profile button for role {string}")
    public void clickQuickLoginProfileButton(String roleBadge) {
        loginPage().clickQuickProfileButton(roleBadge);
    }

    @When("the user switches role in the top header bar to {string}")
    public void switchRoleInHeaderBar(String roleBadge) {
        loginPage().clickHeaderRoleSwitcher(roleBadge);
    }

    @When("the user navigates to the {string} module tab in the dashboard")
    public void navigateToDashboardTab(String tabKey) {
        loginPage().selectNavigationTab(tabKey);
    }

    @Then("the Web Dashboard displays the active session pill for {string} with role {string} and healthy PostgreSQL catalogs")
    public void verifyActiveSessionPillAndCatalogs(String expectedFullName, String expectedRoleCode) {
        String pillText = loginPage().waitForActiveUserRole(expectedRoleCode);
        Assertions.assertTrue(
                pillText.contains(expectedFullName),
                "Expected active session pill to contain '" + expectedFullName + "', but got: " + pillText
        );
        Assertions.assertTrue(
                pillText.contains(expectedRoleCode),
                "Expected active session pill to contain role '" + expectedRoleCode + "', but got: " + pillText
        );

        String healthBadge = loginPage().waitForCatalogHealthBadge();
        Assertions.assertTrue(
                healthBadge.contains("PostgreSQL ACID"),
                "Expected catalog health badge to confirm PostgreSQL ACID, but got: " + healthBadge
        );
    }

    @Then("the REST API POST {string} and GET {string} confirm valid JWT session for {string} with role {string}")
    public void verifyApiJwtAndProfileForUser(
            String loginEndpoint,
            String meEndpoint,
            String email,
            String expectedRoleCode
    ) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
                "email", email,
                "password", "Moon2026!"
        ));
        Response loginRes = RestAssuredHelper.sendRequest(
                "POST " + loginEndpoint + " (" + email + ")",
                "POST",
                loginEndpoint,
                null,
                body,
                null
        );
        context.setLastResponse(loginRes);
        Assertions.assertEquals(201, loginRes.getStatusCode(), "Expected HTTP 201 on POST " + loginEndpoint);

        String token = loginRes.jsonPath().getString("access_token");
        Assertions.assertNotNull(token, "JWT access_token must not be null");
        context.setAccessToken(token);

        Response meRes = RestAssuredHelper.sendRequest(
                "GET " + meEndpoint + " (" + expectedRoleCode + ")",
                "GET",
                meEndpoint,
                token,
                null,
                null
        );
        Assertions.assertEquals(200, meRes.getStatusCode(), "Expected HTTP 200 on GET " + meEndpoint);
        Assertions.assertEquals(email, meRes.jsonPath().getString("authenticatedUser.email"));
        Assertions.assertEquals(expectedRoleCode, meRes.jsonPath().getString("authenticatedUser.role_code"));
    }

    @Then("PostgreSQL confirms user {string} is active with role {string} and status {string}")
    public void verifyPostgresUserActiveRoleAndStatus(String email, String expectedRoleCode, String expectedStatus) {
        List<Map<String, Object>> rows = DatabaseHelper.queryList(
                "Audit User " + email,
                "SELECT id, email, full_name, role_code, status, is_active FROM auth.users WHERE email = ?",
                email
        );
        Assertions.assertEquals(1, rows.size(), "Expected 1 user row in auth.users for " + email);
        Map<String, Object> row = rows.get(0);
        Assertions.assertEquals(expectedRoleCode, row.get("role_code"));
        Assertions.assertEquals(expectedStatus, row.get("status"));
        Assertions.assertEquals(Boolean.TRUE, row.get("is_active"));
    }

    @Then("PostgreSQL confirms client {string} has an active multi-balance wallet in {string}")
    public void verifyPostgresClientWallet(String email, String walletTable) {
        List<Map<String, Object>> rows = DatabaseHelper.queryList(
                "Audit Client Wallet " + email,
                "SELECT u.email, u.role_code, w.id AS wallet_id, w.cash_balance, w.promotional_balance, w.status AS wallet_status " +
                "FROM auth.users u JOIN " + walletTable + " w ON w.user_id = u.id WHERE u.email = ?",
                email
        );
        Assertions.assertEquals(1, rows.size(), "Expected 1 active wallet row for " + email);
        Assertions.assertEquals("ACTIVE", rows.get(0).get("wallet_status"));
    }

    @When("the user opens the Forgot Password recovery panel and requests an OTP for {string}")
    public void requestForgotPasswordOtpFromUi(String email) {
        loginPage().openForgotPasswordView();
        loginPage().requestPasswordResetEmail(email);
    }

    @Then("PostgreSQL {string} and Mailpit API confirm a PENDING 6-digit OTP for {string}")
    public void verifyPendingOtpInPostgresAndMailpit(String tableName, String email) {
        List<Map<String, Object>> rows = DatabaseHelper.queryList(
                "Fetch Active OTP for " + email,
                "SELECT id, email, reset_code, status FROM " + tableName +
                " WHERE LOWER(email) = LOWER(?) AND status = 'PENDING' ORDER BY created_at DESC LIMIT 1",
                email
        );
        Assertions.assertEquals(1, rows.size(), "Expected a PENDING OTP row in " + tableName + " for " + email);
        this.latestOtpCode = String.valueOf(rows.get(0).get("reset_code"));
        Assertions.assertTrue(this.latestOtpCode.matches("\\d{6}"), "Expected 6-digit OTP code, got: " + this.latestOtpCode);

        Response mailpitRes = RestAssuredHelper.sendRequest(
                "GET /auth/mailpit-messages (Verify OTP Email)",
                "GET",
                "/auth/mailpit-messages",
                null,
                null,
                null
        );
        Assertions.assertEquals(200, mailpitRes.getStatusCode());
    }

    @When("the user submits the retrieved 6-digit OTP and new password {string} in the recovery form")
    public void submitRetrievedOtpAndNewPassword(String newPassword) {
        Assertions.assertNotNull(this.latestOtpCode, "OTP code must be retrieved before confirming password reset");
        loginPage().confirmPasswordResetWithOtp(this.latestOtpCode, newPassword);
        String banner = loginPage().getFeedbackBannerText();
        Assertions.assertFalse(banner.isBlank(), "Expected confirmation feedback banner after password reset");
    }

    @Then("the user logs out cleanly from the Web Dashboard and returns to the Login screen")
    public void logoutCleanlyFromWebDashboard() {
        loginPage().logoutAndVerifyLoginScreen();
    }
}

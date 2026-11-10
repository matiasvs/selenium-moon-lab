package com.qa.moonlab.steps.sample;

import com.qa.moonlab.api.RestAssuredHelper;
import com.qa.moonlab.config.EnvConfig;
import com.qa.moonlab.context.TestContext;
import com.qa.moonlab.db.DatabaseHelper;
import com.qa.moonlab.driver.DriverFactory;
import com.qa.moonlab.pages.sample.SampleLoginPage;
import com.qa.moonlab.performance.K6RunnerHelper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Map;

/**
 * Base Step Definitions for the Template connecting:
 *  1. Web UI in Selenium 4 (SampleLoginPage -> BasePage with wait.until and 12vh HUD)
 *  2. REST API with RestAssured 5 (RestAssuredHelper)
 *  3. Multi-Engine Database with HikariCP (DatabaseHelper)
 *  4. Load Testing with Grafana k6 (K6RunnerHelper)
 */
public class SampleTemplateSteps {

    private final TestContext context;
    private K6RunnerHelper.K6ExecutionResult k6Result;

    public SampleTemplateSteps(TestContext context) {
        this.context = context;
    }

    @Given("the user opens the login screen in the Selenium browser")
    public void openLoginScreen() {
        SampleLoginPage loginPage = new SampleLoginPage(DriverFactory.getDriver());
        loginPage.openLoginPage();
    }

    @When("the user enters the configured environment credentials")
    public void enterConfiguredCredentials() {
        SampleLoginPage loginPage = new SampleLoginPage(DriverFactory.getDriver());
        loginPage.login(EnvConfig.getQaEmail(), EnvConfig.getQaPassword());
    }

    @When("the {string} endpoint is queried via RestAssured")
    public void sendGetRequestViaRestAssured(String endpointPath) {
        Response res = RestAssuredHelper.sendRequest(
                "GET " + endpointPath,
                "GET",
                endpointPath,
                context.getAccessToken(),
                null,
                null
        );
        context.setLastResponse(res);
    }

    @Then("the HTTP response has status code {int}")
    public void assertHttpStatusCode(int expectedStatus) {
        Assertions.assertNotNull(context.getLastResponse(), "No previous HTTP response found in context");
        Assertions.assertEquals(expectedStatus, context.getLastResponse().getStatusCode());
    }

    @Then("the database responds properly to the verification query {string}")
    public void verifyDatabaseConnectivity(String sqlQuery) {
        List<Map<String, Object>> rows = DatabaseHelper.queryList("SQL Verification", sqlQuery);
        context.setLastQueryRows(rows);
        Assertions.assertFalse(rows.isEmpty(), "SQL query returned no rows");
    }

    @When("the k6 performance test {string} is executed with {int} VUs and {int} iterations")
    public void executeK6FromSelenium(String scriptPath, int vus, int iterations) {
        this.k6Result = K6RunnerHelper.runK6LoadTest(
                scriptPath,
                vus,
                iterations,
                Map.of("BASE_URL", EnvConfig.getApiBaseUrl())
        );
    }

    @Then("the k6 execution finishes with exit code {int}")
    public void assertK6ExitCode(int expectedExitCode) {
        Assertions.assertNotNull(this.k6Result, "No k6 load test was executed");
        Assertions.assertEquals(expectedExitCode, this.k6Result.exitCode(),
                "k6 output:\n" + this.k6Result.consoleOutput());
    }
}

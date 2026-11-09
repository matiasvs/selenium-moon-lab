# language: en
@template
Feature: Generic QA Pro Template - Selenium 4 POM + RestAssured + Multi-DB + k6

  @api @smoke
  Scenario: Verify API health endpoint and SQL database connectivity
    When the "/health" endpoint is queried via RestAssured
    Then the HTTP response has status code 200
    And the database responds properly to the verification query "SELECT 1 AS status"

  @web
  Scenario: Open web login screen with Selenium 4 POM BasePage and smart waits (wait.until)
    Given the user opens the login screen in the Selenium browser
    When the user enters the configured environment credentials

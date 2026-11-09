package com.qa.template.api;

import com.qa.template.config.EnvConfig;
import com.qa.template.driver.DriverFactory;
import com.qa.template.pages.BasePage;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Centralized RestAssured 5.5+ client.
 * Automatically attaches requests/responses to Allure and projects them onto the 12vh Selenium HUD.
 */
public final class RestAssuredHelper {

    private RestAssuredHelper() {
    }

    public static RequestSpecification baseSpec(String baseUrl, String bearerToken, Map<String, String> extraHeaders) {
        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(baseUrl != null ? baseUrl : EnvConfig.getApiBaseUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addHeader("x-channel", "WEB")
                .addHeader("x-version", "1.2.3")
                .addFilter(new AllureRestAssured());

        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.addHeader("Authorization", "Bearer " + bearerToken);
        }
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            builder.addHeaders(extraHeaders);
        }
        return builder.build();
    }

    public static Response sendRequest(
            String title,
            String method,
            String path,
            String bearerToken,
            String jsonBody,
            Map<String, String> extraHeaders
    ) {
        String baseUrl = EnvConfig.getApiBaseUrl();
        RequestSpecification spec = RestAssured.given()
                .spec(baseSpec(baseUrl, bearerToken, extraHeaders));

        if (jsonBody != null && !jsonBody.isBlank()) {
            spec.body(jsonBody);
        }

        Response response = switch (method.toUpperCase()) {
            case "POST" -> spec.when().post(path);
            case "PUT" -> spec.when().put(path);
            case "PATCH" -> spec.when().patch(path);
            case "DELETE" -> spec.when().delete(path);
            default -> spec.when().get(path);
        };

        if (DriverFactory.hasActiveDriver()) {
            BasePage hud = new BasePage(DriverFactory.getDriver()) {};
            hud.showCurlResultBanner(
                    title,
                    method,
                    baseUrl + path,
                    jsonBody,
                    response.getStatusCode(),
                    response.getBody().asString()
            );
        }

        return response;
    }
}

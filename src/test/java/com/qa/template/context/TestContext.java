package com.qa.template.context;

import io.restassured.response.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared context across Step classes within the same Cucumber scenario
 * (automatically injected via cucumber-picocontainer).
 */
public class TestContext {

    private String accessToken;
    private Response lastResponse;
    private List<Map<String, Object>> lastQueryRows;
    private final Map<String, Object> scenarioData = new HashMap<>();

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Response getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(Response lastResponse) {
        this.lastResponse = lastResponse;
    }

    public List<Map<String, Object>> getLastQueryRows() {
        return lastQueryRows;
    }

    public void setLastQueryRows(List<Map<String, Object>> lastQueryRows) {
        this.lastQueryRows = lastQueryRows;
    }

    public void set(String key, Object value) {
        scenarioData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) scenarioData.get(key);
    }
}

package com.qa.moonlab.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Centralized environment variable manager (.env and Maven -D system properties).
 * Automatically resolves Web and API URLs based on TARGET_ENV (DEV, STAGE, or GCP).
 */
public final class EnvConfig {

    private static final Dotenv DOTENV = Dotenv.configure().ignoreIfMissing().load();

    private EnvConfig() {
    }

    public static String get(String key, String defaultValue) {
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }
        String envVar = System.getenv(key);
        if (envVar != null && !envVar.isBlank()) {
            return envVar;
        }
        String dotenvVal = DOTENV.get(key);
        return (dotenvVal != null && !dotenvVal.isBlank()) ? dotenvVal : defaultValue;
    }

    public static String getTargetEnv() {
        return get("target.env", get("TARGET_ENV", "DEV")).toUpperCase().trim();
    }

    public static String getWebBaseUrl() {
        return switch (getTargetEnv()) {
            case "STAGE", "STG" -> get("WEB_BASE_URL_STAGE", "https://stage-app.example.com");
            case "GCP", "CLOUD" -> get("WEB_BASE_URL_GCP", "https://app.example.com");
            default -> get("WEB_BASE_URL_DEV", "http://localhost:3001");
        };
    }

    public static String getApiBaseUrl() {
        return switch (getTargetEnv()) {
            case "STAGE", "STG" -> get("API_BASE_URL_STAGE", "https://stage-api.example.com");
            case "GCP", "CLOUD" -> get("API_BASE_URL_GCP", "https://api.example.com");
            default -> get("API_BASE_URL_DEV", "http://localhost:3000");
        };
    }

    public static String getQaEmail() {
        return get("QA_AUTH_EMAIL", "qa.user@example.com");
    }

    public static String getQaPassword() {
        return get("QA_AUTH_PASSWORD", "ChangeMe123!");
    }
}

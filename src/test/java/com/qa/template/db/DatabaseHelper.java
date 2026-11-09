package com.qa.template.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.template.config.EnvConfig;
import com.qa.template.driver.DriverFactory;
import com.qa.template.pages.BasePage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.qameta.allure.Allure;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-Engine SQL Connection Pool with HikariCP (PostgreSQL, MySQL, Oracle, SQL Server, MariaDB).
 * Supports the 3 profiles (@db-classic, @db-stage read-only SELECT, and @db-gcp) and automatically
 * projects each query and result onto the bottom-left HUD of the Selenium browser.
 */
public final class DatabaseHelper {

    private static final Map<String, HikariDataSource> POOLS = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DatabaseHelper() {
    }

    public static String resolveProfile() {
        String env = EnvConfig.getTargetEnv();
        if (env.contains("GCP") || env.contains("CLOUD")) return "gcp";
        if (env.contains("STAGE") || env.contains("STG")) return "stage";
        return "classic";
    }

    public static String resolveEngine() {
        return EnvConfig.get("db.engine", EnvConfig.get("DB_ENGINE", "postgres")).toLowerCase().trim();
    }

    private static String resolveDriverClass(String engine) {
        return switch (engine) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            case "sqlserver", "mssql" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "mariadb" -> "org.mariadb.jdbc.Driver";
            default -> "org.postgresql.Driver";
        };
    }

    private static String defaultPortForEngine(String engine) {
        return switch (engine) {
            case "mysql", "mariadb" -> "3306";
            case "oracle" -> "1521";
            case "sqlserver", "mssql" -> "1433";
            default -> "5432";
        };
    }

    private static String buildJdbcUrl(String engine, String host, String port, String dbName) {
        return switch (engine) {
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + dbName
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            case "oracle" -> "jdbc:oracle:thin:@//" + host + ":" + port + "/" + dbName;
            case "sqlserver", "mssql" -> "jdbc:sqlserver://" + host + ":" + port
                    + ";databaseName=" + dbName + ";encrypt=false;trustServerCertificate=true";
            case "mariadb" -> "jdbc:mariadb://" + host + ":" + port + "/" + dbName;
            default -> "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        };
    }

    private static HikariDataSource getDataSource(String profile, String engine) {
        String poolKey = profile + ":" + engine;
        return POOLS.computeIfAbsent(poolKey, k -> {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName(resolveDriverClass(engine));
            config.setMaximumPoolSize(5);
            config.setConnectionTimeout(10000);

            String defaultPort = defaultPortForEngine(engine);

            switch (profile) {
                case "gcp" -> {
                    String host = EnvConfig.get("DATABASE_GCP_HOST", "127.0.0.1");
                    String port = EnvConfig.get("DATABASE_GCP_PORT", "5433");
                    String db = EnvConfig.get("POSTGRES_GCP_DB", "app_db");
                    config.setJdbcUrl(buildJdbcUrl(engine, host, port, db));
                    config.setUsername(EnvConfig.get("POSTGRES_GCP_USER", "postgres"));
                    config.setPassword(EnvConfig.get("POSTGRES_GCP_PASSWORD", ""));
                }
                case "stage" -> {
                    String host = EnvConfig.get("DATABASE_STAGE_HOST", "127.0.0.1");
                    String port = EnvConfig.get("DATABASE_STAGE_PORT", "5434");
                    String db = EnvConfig.get("POSTGRES_STAGE_DB", "app_db");
                    config.setJdbcUrl(buildJdbcUrl(engine, host, port, db));
                    config.setUsername(EnvConfig.get("POSTGRES_STAGE_USER", "qa-viewer"));
                    config.setPassword(EnvConfig.get("POSTGRES_STAGE_PASSWORD", ""));
                    config.setReadOnly(true);
                }
                default -> {
                    String host = EnvConfig.get("DATABASE_HOST", "127.0.0.1");
                    String port = EnvConfig.get("DATABASE_PORT", defaultPort);
                    String db = EnvConfig.get("POSTGRES_DB", "app_db");
                    config.setJdbcUrl(buildJdbcUrl(engine, host, port, db));
                    config.setUsername(EnvConfig.get("POSTGRES_USER", "postgres"));
                    config.setPassword(EnvConfig.get("POSTGRES_PASSWORD", ""));
                }
            }
            return new HikariDataSource(config);
        });
    }

    public static List<Map<String, Object>> queryList(String title, String sql, Object... params) {
        String profile = resolveProfile();
        String engine = resolveEngine();
        if ("stage".equals(profile) && !sql.trim().toUpperCase().matches("^(SELECT|WITH)\\b.*")) {
            throw new IllegalStateException(
                    "Rule @go-qa-stg: Only read-only queries (SELECT) are allowed in db-stage. Use RestAssured against Swagger for mutations."
            );
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = getDataSource(profile, engine).getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int col = 1; col <= colCount; col++) {
                        row.put(meta.getColumnLabel(col), rs.getObject(col));
                    }
                    rows.add(row);
                }
            }

            String firstRowJson = rows.isEmpty() ? "{}" : MAPPER.writeValueAsString(rows.get(0));
            Allure.addAttachment("SQL [" + engine.toUpperCase() + " - " + title + "]", "text/plain", sql + "\n\nResult:\n" + firstRowJson);

            if (DriverFactory.hasActiveDriver()) {
                BasePage hud = new BasePage(DriverFactory.getDriver()) {};
                hud.showSqlResultBanner(title + " (" + engine.toUpperCase() + ")", sql, firstRowJson);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error executing SQL query [" + engine + " / " + title + "]: " + e.getMessage(), e);
        }
        return rows;
    }
}

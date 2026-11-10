package com.qa.moonlab.performance;

import com.qa.moonlab.driver.DriverFactory;
import com.qa.moonlab.pages.BasePage;
import io.qameta.allure.Allure;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * QA Pro Helper to execute Grafana k6 performance tests (including xk6-sql binaries)
 * directly from Selenium + Cucumber JVM scenarios in Java.
 */
public final class K6RunnerHelper {

    public record K6ExecutionResult(
            int exitCode,
            String consoleOutput,
            File summaryJsonFile,
            File htmlReportFile
    ) {
    }

    private K6RunnerHelper() {
    }

    public static K6ExecutionResult runK6LoadTest(
            String scriptRelativePath,
            int vus,
            int iterationsPerVu,
            Map<String, String> extraEnv
    ) {
        try {
            File reportsDir = new File("target/k6-reports");
            if (!reportsDir.exists()) {
                reportsDir.mkdirs();
            }

            File summaryJson = new File(reportsDir, "summary-" + vus + "vu.json");
            File htmlReport = new File(reportsDir, "k6-native-" + vus + "vu.html");

            File customK6Bin = new File("src/test/resources/performance/k6/bin/k6");
            String k6Executable = (customK6Bin.exists() && customK6Bin.canExecute())
                    ? customK6Bin.getAbsolutePath()
                    : "k6";

            List<String> command = new ArrayList<>();
            command.add(k6Executable);
            command.add("run");
            command.add("--summary-export=" + summaryJson.getAbsolutePath());
            command.add(scriptRelativePath);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            env.put("VUS", String.valueOf(vus));
            env.put("ITERATIONS_PER_VU", String.valueOf(iterationsPerVu));
            env.put("K6_WEB_DASHBOARD", "true");
            env.put("K6_WEB_DASHBOARD_PERIOD", "1s");
            env.put("K6_WEB_DASHBOARD_EXPORT", htmlReport.getAbsolutePath());
            if (extraEnv != null) {
                env.putAll(extraEnv);
            }

            if (DriverFactory.hasActiveDriver()) {
                BasePage hud = new BasePage(DriverFactory.getDriver()) {};
                hud.showVideoBanner(
                        "Running Grafana k6: " + vus + " VUs (" + iterationsPerVu + " iter/VU) -> " + scriptRelativePath,
                        "info"
                );
            }

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            String logText = output.toString();

            Allure.addAttachment("k6 Console Output (" + vus + " VUs)", "text/plain", logText);
            if (summaryJson.exists()) {
                Allure.addAttachment(
                        "k6 Summary JSON (" + vus + " VUs)",
                        "application/json",
                        Files.readString(summaryJson.toPath(), StandardCharsets.UTF_8)
                );
            }
            if (htmlReport.exists()) {
                Allure.addAttachment(
                        "k6 Native Web Dashboard (" + vus + " VUs)",
                        "text/html",
                        Files.readString(htmlReport.toPath(), StandardCharsets.UTF_8)
                );
            }

            return new K6ExecutionResult(exitCode, logText, summaryJson, htmlReport);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute k6 from Selenium/Java: " + e.getMessage(), e);
        }
    }
}

package com.qa.moonlab.driver;

import com.qa.moonlab.config.EnvConfig;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.time.Duration;

/**
 * Thread-Safe WebDriver Factory for Selenium 4.27+.
 * Uses native Selenium Manager (zero manual driver paths) and keeps
 * implicitlyWait at ZERO to guarantee deterministic waits with WebDriverWait / FluentWait.
 */
public final class DriverFactory {

    private static final ThreadLocal<WebDriver> DRIVER_POOL = new ThreadLocal<>();

    private DriverFactory() {
    }

    public static WebDriver getDriver() {
        if (DRIVER_POOL.get() == null) {
            DRIVER_POOL.set(createDriver());
        }
        return DRIVER_POOL.get();
    }

    public static boolean hasActiveDriver() {
        return DRIVER_POOL.get() != null;
    }

    private static WebDriver createDriver() {
        String browser = EnvConfig.get("browser", EnvConfig.get("BROWSER", "chrome")).toLowerCase();
        boolean headless = Boolean.parseBoolean(
                EnvConfig.get("headless", EnvConfig.get("HEADLESS", "false"))
        );
        boolean browserRight = Boolean.parseBoolean(
                EnvConfig.get("browser.right", EnvConfig.get("BROWSER_RIGHT", "false"))
        );

        WebDriver driver;
        switch (browser) {
            case "firefox" -> {
                FirefoxOptions ffOptions = new FirefoxOptions();
                if (headless) {
                    ffOptions.addArguments("-headless");
                }
                ffOptions.addArguments("--width=1920", "--height=1080");
                driver = new FirefoxDriver(ffOptions);
            }
            case "edge" -> {
                EdgeOptions edgeOptions = new EdgeOptions();
                if (headless) {
                    edgeOptions.addArguments("--headless=new");
                }
                edgeOptions.addArguments("--window-size=1920,1080", "--start-maximized");
                driver = new EdgeDriver(edgeOptions);
            }
            default -> {
                ChromeOptions chromeOptions = new ChromeOptions();
                if (headless) {
                    chromeOptions.addArguments("--headless=new");
                }
                if (browserRight) {
                    chromeOptions.addArguments("--window-size=960,1080", "--window-position=960,0");
                } else {
                    chromeOptions.addArguments("--window-size=1920,1080", "--start-maximized");
                }
                chromeOptions.addArguments(
                        "--disable-gpu",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--remote-allow-origins=*",
                        "--ignore-certificate-errors"
                );
                driver = new ChromeDriver(chromeOptions);
            }
        }

        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(20));
        return driver;
    }

    public static void quitDriver() {
        WebDriver driver = DRIVER_POOL.get();
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception ignored) {
            } finally {
                DRIVER_POOL.remove();
            }
        }
    }
}

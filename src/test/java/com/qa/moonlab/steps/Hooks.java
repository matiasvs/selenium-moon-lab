package com.qa.moonlab.steps;

import com.qa.moonlab.driver.DriverFactory;
import com.qa.moonlab.mobile.MobileApkJavaHelper;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.ByteArrayInputStream;

/**
 * Global lifecycle Hooks for Cucumber JVM.
 * Automatically attaches a screenshot to Allure on completion or failure and closes active drivers.
 */
public class Hooks {

    @After
    public void tearDown(Scenario scenario) {
        if (DriverFactory.hasActiveDriver()) {
            try {
                WebDriver driver = DriverFactory.getDriver();
                byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                Allure.addAttachment(
                        "Browser Screenshot - " + scenario.getName(),
                        "image/png",
                        new ByteArrayInputStream(screenshot),
                        ".png"
                );
                scenario.attach(screenshot, "image/png", scenario.getName());
            } catch (Exception ignored) {
            } finally {
                DriverFactory.quitDriver();
            }
        }
        MobileApkJavaHelper.quitAppiumDriver();
    }
}

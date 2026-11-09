package com.qa.template.pages.sample;

import com.qa.template.config.EnvConfig;
import com.qa.template.pages.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Sample Page Object extending BasePage.
 * In a real project, simply duplicate this class and adjust the By locators.
 */
public class SampleLoginPage extends BasePage {

    private final By emailInput = By.cssSelector("input[name='email'], input[type='email']");
    private final By passwordInput = By.cssSelector("input[name='password'], input[type='password']");
    private final By submitButton = By.cssSelector("button[type='submit']");
    private final By mainHeading = By.cssSelector("h1, [data-testid='dashboard-title']");

    public SampleLoginPage(WebDriver driver) {
        super(driver);
    }

    public void openLoginPage() {
        navigateTo(EnvConfig.getWebBaseUrl() + "/login", "Login Screen");
    }

    public void login(String email, String password) {
        fillElement(emailInput, email, "Email Field");
        fillElement(passwordInput, password, "Password Field");
        clickElement(submitButton, "Sign In Button");
    }

    public String getHeadingText() {
        return getElementText(mainHeading);
    }
}

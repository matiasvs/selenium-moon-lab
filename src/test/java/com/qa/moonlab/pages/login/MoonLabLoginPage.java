package com.qa.moonlab.pages.login;

import com.qa.moonlab.config.EnvConfig;
import com.qa.moonlab.pages.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Page Object Model (POM) for the Moon-Lab Web Login, Quick Role Switcher,
 * and Mailpit Password Recovery screens (http://localhost:3000).
 * Extends BasePage using smart dynamic waits (wait.until) and 12vh Dual-Panel HUD.
 */
public class MoonLabLoginPage extends BasePage {

    // Login Form Locators
    private final By emailInput = By.cssSelector("[data-testid='login-email-input']");
    private final By passwordInput = By.cssSelector("[data-testid='login-password-input']");
    private final By loginSubmitButton = By.cssSelector("[data-testid='btn-login-submit']");
    private final By openForgotPasswordButton = By.cssSelector("[data-testid='btn-open-forgot-password']");
    private final By feedbackBanner = By.cssSelector("[data-testid='login-feedback-banner']");

    // Forgot / Reset Password Form Locators (Mailpit SMTP Flow)
    private final By forgotPasswordPanel = By.cssSelector("[data-testid='forgot-password-panel']");
    private final By forgotEmailInput = By.cssSelector("[data-testid='forgot-email-input']");
    private final By sendResetEmailButton = By.cssSelector("[data-testid='btn-send-reset-email']");
    private final By resetCodeInput = By.cssSelector("[data-testid='reset-code-input']");
    private final By resetNewPasswordInput = By.cssSelector("[data-testid='reset-new-password-input']");
    private final By confirmResetPasswordButton = By.cssSelector("[data-testid='btn-confirm-reset-password']");

    // Authenticated Dashboard Header & Navigation Locators
    private final By activeUserPill = By.cssSelector("[data-testid='active-user-pill']");
    private final By catalogHealthBadge = By.cssSelector("[data-testid='catalog-health-badge']");
    private final By logoutButton = By.cssSelector("[data-testid='btn-logout']");

    public MoonLabLoginPage(WebDriver driver) {
        super(driver);
    }

    public void openLoginScreen() {
        navigateTo(EnvConfig.getWebBaseUrl(), "Moon-Lab Web Login Screen");
        waitUntilVisible(emailInput);
        waitUntilVisible(passwordInput);
        waitUntilVisible(loginSubmitButton);
    }

    public void loginWithCredentials(String email, String password) {
        fillElement(emailInput, email, "Login Email Field");
        fillElement(passwordInput, password, "Login Password Field");
        clickElement(loginSubmitButton, "Sign In Submit Button");
    }

    public void clickQuickProfileButton(String roleBadge) {
        By quickProfileBtn = By.cssSelector("[data-testid='login-quick-" + roleBadge + "']");
        clickElement(quickProfileBtn, "Quick 1-Click Login Profile (" + roleBadge + ")");
    }

    public void clickHeaderRoleSwitcher(String roleBadge) {
        By switcherBtn = By.cssSelector("[data-testid='switch-role-" + roleBadge + "']");
        clickElement(switcherBtn, "Header 1-Click Role Switcher (" + roleBadge + ")");
    }

    public void selectNavigationTab(String tabKey) {
        By tabLocator = By.cssSelector("[data-testid='tab-" + tabKey + "']");
        clickElement(tabLocator, "Navigation Tab (" + tabKey + ")");
    }

    public String waitForActiveUserRole(String expectedRoleCode) {
        waitUntilVisible(activeUserPill);
        waitUntilTextPresent(activeUserPill, expectedRoleCode);
        String pillText = getElementText(activeUserPill);
        showVideoBanner("Verified Active Session Pill: " + pillText, "info");
        return pillText;
    }

    public String waitForCatalogHealthBadge() {
        waitUntilVisible(catalogHealthBadge);
        waitUntilTextPresent(catalogHealthBadge, "PostgreSQL ACID");
        return getElementText(catalogHealthBadge);
    }

    public void openForgotPasswordView() {
        clickElement(openForgotPasswordButton, "Open Forgot Password View");
        waitUntilVisible(forgotPasswordPanel);
    }

    public void requestPasswordResetEmail(String email) {
        fillElement(forgotEmailInput, email, "Recovery Email Field");
        clickElement(sendResetEmailButton, "Send OTP Code to Mailpit Button");
        waitUntilVisible(feedbackBanner);
    }

    public void confirmPasswordResetWithOtp(String otpCode, String newPassword) {
        fillElement(resetCodeInput, otpCode, "6-Digit Mailpit OTP Code Field");
        fillElement(resetNewPasswordInput, newPassword, "New Password Field");
        clickElement(confirmResetPasswordButton, "Confirm OTP and Update Password Button");
        waitUntilVisible(emailInput);
    }

    public String getFeedbackBannerText() {
        return getElementText(feedbackBanner);
    }

    public void logoutAndVerifyLoginScreen() {
        clickElement(logoutButton, "Logout Button");
        waitUntilVisible(emailInput);
        waitUntilVisible(loginSubmitButton);
        showVideoBanner("Session closed cleanly — Returned to Login Screen", "info");
    }
}

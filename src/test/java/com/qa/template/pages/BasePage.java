package com.qa.template.pages;

import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import java.time.Duration;
import java.util.List;

/**
 * Professional BasePage Class (POM) for Selenium 4.27+.
 * Encapsulates all browser interactions over smart dynamic waits
 * (FluentWait / WebDriverWait -> wait.until(ExpectedConditions...)) and projects in real time
 * the 12vh Dual-Panel HUD (Actions/cURL on the right and SQL on the left).
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final Wait<WebDriver> wait;
    protected final JavascriptExecutor js;
    protected final Actions actions;

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration DEFAULT_POLLING = Duration.ofMillis(200);

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.js = (JavascriptExecutor) driver;
        this.actions = new Actions(driver);
        this.wait = buildFluentWait(DEFAULT_TIMEOUT, DEFAULT_POLLING);
    }

    protected Wait<WebDriver> buildFluentWait(Duration timeout, Duration polling) {
        return new FluentWait<>(driver)
                .withTimeout(timeout)
                .pollingEvery(polling)
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class)
                .ignoring(ElementClickInterceptedException.class);
    }

    // =========================================================================
    // 1. SMART DYNAMIC WAITS (wait.until) - ZERO Thread.sleep
    // =========================================================================

    public WebElement waitUntilVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public WebElement waitUntilVisible(By locator, Duration customTimeout) {
        return buildFluentWait(customTimeout, DEFAULT_POLLING)
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public WebElement waitUntilClickable(By locator) {
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    public WebElement waitUntilPresent(By locator) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    public boolean waitUntilInvisible(By locator) {
        return wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    public boolean waitUntilTextPresent(By locator, String expectedText) {
        return wait.until(ExpectedConditions.textToBePresentInElementLocated(locator, expectedText));
    }

    public boolean waitUntilUrlContains(String urlFraction) {
        return wait.until(ExpectedConditions.urlContains(urlFraction));
    }

    public void waitUntilDocumentReady() {
        wait.until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")
        ));
    }

    public List<WebElement> waitUntilAllVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator));
    }

    // =========================================================================
    // 2. ENCAPSULATED POM ACTIONS + 12vh HUD SYNCHRONIZATION
    // =========================================================================

    public void navigateTo(String url, String screenDescription) {
        driver.get(url);
        waitUntilDocumentReady();
        showVideoBanner("Navigation: " + screenDescription + " (" + url + ")", "info");
    }

    public void moveMouseToElement(WebElement element) {
        try {
            js.executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});", element);
            actions.moveToElement(element).perform();
        } catch (Exception ignored) {
        }
    }

    public void clickElement(By locator, String description) {
        WebElement element = waitUntilClickable(locator);
        String label = (description != null && !description.isBlank()) ? description : resolveElementLabel(element);
        showVideoBanner("Action: Click on \"" + label + "\"", "action");
        moveMouseToElement(element);
        try {
            element.click();
        } catch (ElementClickInterceptedException | StaleElementReferenceException e) {
            WebElement refreshed = waitUntilClickable(locator);
            js.executeScript("arguments[0].click();", refreshed);
        }
    }

    public void fillElement(By locator, String text, String description) {
        WebElement element = waitUntilVisible(locator);
        String label = (description != null && !description.isBlank()) ? description : resolveElementLabel(element);
        String displayValue = label.toLowerCase().matches(".*(password|passcode|secret|token|key).*")
                ? "********"
                : text;
        showVideoBanner("Action: Typing \"" + displayValue + "\" into " + label, "input");
        moveMouseToElement(element);
        element.click();
        element.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        element.clear();
        element.sendKeys(text);
    }

    public void selectCustomDropdownOption(By comboboxTrigger, By optionLocator, String description) {
        clickElement(comboboxTrigger, "Open selector " + description);
        clickElement(optionLocator, "Select option in " + description);
    }

    public String getElementText(By locator) {
        return waitUntilVisible(locator).getText().trim();
    }

    public String inspectDisabledElement(By locator, String bannerPrefix, By optionalRowLocator) {
        WebElement element = waitUntilVisible(locator);
        Assertions.assertFalse(element.isEnabled(), "Expected element to be disabled");

        String reason = element.getDomAttribute("title");
        if (reason == null || reason.isBlank()) {
            reason = element.getDomAttribute("aria-label");
        }
        if (reason == null || reason.isBlank()) {
            reason = element.getDomAttribute("data-tooltip");
        }
        if (reason == null || reason.isBlank()) {
            reason = "Disabled by business rule";
        }
        Assertions.assertFalse(reason.isBlank(), "Disabled button reason attribute must not be empty");

        js.executeScript(
                "const el = arguments[0];" +
                "el.style.outline = '2px dashed #ef4444';" +
                "el.style.outlineOffset = '3px';" +
                "const tr = el.closest('tr');" +
                "if (tr) { tr.style.outline = '2px solid #f59e0b'; }",
                element
        );

        String prefix = (bannerPrefix != null && !bannerPrefix.isBlank())
                ? bannerPrefix
                : "Blocked / Disabled control";
        showVideoBanner(prefix + " (" + reason + ")", "blocked");

        if (optionalRowLocator != null) {
            try {
                WebElement row = waitUntilVisible(optionalRowLocator);
                moveMouseToElement(row);
            } catch (Exception ignored) {
            }
        }
        moveMouseToElement(element);
        return reason;
    }

    protected String resolveElementLabel(WebElement element) {
        try {
            Object raw = js.executeScript(
                    "const el = arguments[0];" +
                    "const aria = el.getAttribute('aria-label') || '';" +
                    "const ph = el.getAttribute('placeholder') || '';" +
                    "const title = el.getAttribute('title') || '';" +
                    "const txt = (el.innerText || el.textContent || '').trim().replace(/\\s+/g, ' ');" +
                    "const name = el.getAttribute('name') || el.tagName.toLowerCase();" +
                    "const res = aria || ph || txt || title || name;" +
                    "return res.length > 60 ? res.slice(0, 57) + '...' : res;",
                    element
            );
            return raw != null ? raw.toString() : "element";
        } catch (Exception e) {
            return "element";
        }
    }

    // =========================================================================
    // 3. LIVE 12vh DUAL-PANEL HUD (JavascriptExecutor)
    // =========================================================================

    public void showVideoBanner(String message, String variant) {
        String borderColor = switch (variant != null ? variant : "action") {
            case "input" -> "#10b981";
            case "info" -> "#f59e0b";
            case "blocked" -> "#ef4444";
            default -> "#38bdf8";
        };

        try {
            js.executeScript(
                    "const text = arguments[0];" +
                    "const border = arguments[1];" +
                    "const bannerId = 'qa-video-action-banner';" +
                    "let badge = document.getElementById(bannerId);" +
                    "if (!badge) {" +
                    "  badge = document.createElement('div');" +
                    "  badge.id = bannerId;" +
                    "  document.body.appendChild(badge);" +
                    "}" +
                    "while (badge.firstChild) badge.removeChild(badge.firstChild);" +
                    "const lines = String(text || '').split('\\n').filter(Boolean);" +
                    "if (lines.length <= 1) {" +
                    "  badge.textContent = text;" +
                    "} else {" +
                    "  lines.forEach((line, idx) => {" +
                    "    const row = document.createElement('div');" +
                    "    row.textContent = line;" +
                    "    if (idx === 0) {" +
                    "      row.style.cssText = 'font-weight:700;font-size:14px;color:#f8fafc;margin-bottom:6px;';" +
                    "    } else if (line.startsWith('cURL:')) {" +
                    "      row.style.cssText = 'font-family:monospace;font-size:12.5px;color:#c4b5fd;background:rgba(15,23,42,0.8);padding:5px 8px;border-radius:5px;margin-top:5px;border-left:3px solid #8b5cf6;word-break:break-word;';" +
                    "    } else if (line.startsWith('HTTP ')) {" +
                    "      const isErr = /^HTTP\\s+[45]\\d\\d/.test(line);" +
                    "      row.style.cssText = 'font-family:monospace;font-size:12.5px;color:' + (isErr ? '#fca5a5' : '#6ee7b7') + ';background:rgba(15,23,42,0.8);padding:5px 8px;border-radius:5px;margin-top:4px;border-left:3px solid ' + (isErr ? '#ef4444' : '#10b981') + ';word-break:break-word;';" +
                    "    } else {" +
                    "      row.style.cssText = 'font-size:13px;color:#e2e8f0;margin-top:3px;';" +
                    "    }" +
                    "    badge.appendChild(row);" +
                    "  });" +
                    "}" +
                    "badge.style.cssText = 'position:fixed;bottom:calc(12vh + 24px);right:24px;z-index:99999;max-width:720px;background:#1e293b;color:#f8fafc;border:2px solid ' + border + ';border-radius:8px;padding:10px 16px;font-family:system-ui,sans-serif;font-size:14px;font-weight:600;line-height:1.4;box-shadow:0 10px 25px rgba(0,0,0,0.45);pointer-events:none;';",
                    message, borderColor
            );
        } catch (Exception ignored) {
        }
    }

    public void showCurlResultBanner(
            String title,
            String method,
            String url,
            String requestPayload,
            int statusCode,
            String responseData
    ) {
        String payloadPart = "";
        if (requestPayload != null && !requestPayload.isBlank()) {
            String masked = requestPayload.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"********\"");
            String compact = masked.length() > 160 ? masked.substring(0, 157) + "..." : masked;
            payloadPart = " -d '" + compact + "'";
        }
        String curlLine = "curl -X " + method.toUpperCase() + " '" + url + "'" + payloadPart;
        String rawRes = responseData != null ? responseData : "";
        String compactRes = rawRes.length() > 230 ? rawRes.substring(0, 227) + "..." : rawRes;
        String variant = statusCode >= 400 ? "blocked" : "action";
        showVideoBanner(
                "API Request: " + title + "\ncURL: " + curlLine + "\nHTTP " + statusCode + " Result: " + compactRes,
                variant
        );
    }

    public void showSqlResultBanner(String title, String sqlQuery, String resultJson) {
        String cleanQuery = sqlQuery != null ? sqlQuery.replaceAll("\\s+", " ").trim() : "";
        String rawRes = resultJson != null ? resultJson : "{}";
        String compactRes = rawRes.length() > 250 ? rawRes.substring(0, 247) + "..." : rawRes;

        try {
            js.executeScript(
                    "const headerText = arguments[0];" +
                    "const queryText = arguments[1];" +
                    "const resText = arguments[2];" +
                    "let badge = document.getElementById('qa-video-sql-banner');" +
                    "if (!badge) {" +
                    "  badge = document.createElement('div');" +
                    "  badge.id = 'qa-video-sql-banner';" +
                    "  document.body.appendChild(badge);" +
                    "}" +
                    "badge.textContent = '';" +
                    "const header = document.createElement('div');" +
                    "header.textContent = 'SQL Validation: ' + headerText;" +
                    "header.style.cssText = 'font-weight:700;color:#f8fafc;margin-bottom:4px;font-size:13.5px;';" +
                    "badge.appendChild(header);" +
                    "const sqlRow = document.createElement('div');" +
                    "sqlRow.textContent = 'SQL: ' + queryText;" +
                    "sqlRow.style.cssText = 'font-family:monospace;font-size:12.5px;font-weight:500;color:#7dd3fc;background:rgba(15,23,42,0.8);padding:5px 8px;border-radius:5px;margin-top:5px;border-left:3px solid #38bdf8;word-break:break-word;';" +
                    "badge.appendChild(sqlRow);" +
                    "const resRow = document.createElement('div');" +
                    "resRow.textContent = 'Result: ' + resText;" +
                    "resRow.style.cssText = 'font-family:monospace;font-size:12.5px;font-weight:500;color:#6ee7b7;background:rgba(15,23,42,0.8);padding:5px 8px;border-radius:5px;margin-top:4px;border-left:3px solid #10b981;word-break:break-word;';" +
                    "badge.appendChild(resRow);" +
                    "badge.style.cssText = 'position:fixed;bottom:calc(12vh + 24px);left:24px;z-index:99999;max-width:720px;background:#1e293b;color:#f8fafc;border:2px solid #f59e0b;border-radius:8px;padding:10px 16px;font-family:system-ui,sans-serif;font-size:14px;font-weight:600;line-height:1.4;box-shadow:0 10px 25px rgba(0,0,0,0.45);pointer-events:none;';",
                    title, cleanQuery, compactRes
            );
        } catch (Exception ignored) {
        }
    }
}

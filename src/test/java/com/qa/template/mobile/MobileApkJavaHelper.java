package com.qa.template.mobile;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.qameta.allure.Allure;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified QA Pro Helper for APK Testing on Android Emulator from Selenium + Java 21.
 * Supports the 3 Modes: Native Appium, Direct CLI Skill (adb + android layout --no-idle),
 * and Hybrid Combo Pro (Selenium Web + 3D QR Camera Toren1BD.posters + Appium/ADB).
 */
public final class MobileApkJavaHelper {

    private static final ThreadLocal<AndroidDriver> APPIUM_DRIVER = new ThreadLocal<>();
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(15);
    private static final Duration DEFAULT_POLLING = Duration.ofMillis(250);

    private MobileApkJavaHelper() {
    }

    public static AndroidDriver startAppiumDriver(
            String appiumServerUrl,
            String deviceName,
            String apkAbsolutePath,
            String appPackage,
            String appActivity
    ) {
        try {
            UiAutomator2Options options = new UiAutomator2Options()
                    .setPlatformName("Android")
                    .setAutomationName("UiAutomator2")
                    .setDeviceName(deviceName != null ? deviceName : "emulator-5554")
                    .setAutoGrantPermissions(true)
                    .setNoReset(true)
                    .setNewCommandTimeout(Duration.ofSeconds(120));

            if (apkAbsolutePath != null && !apkAbsolutePath.isBlank()) {
                options.setApp(apkAbsolutePath);
            }
            if (appPackage != null && !appPackage.isBlank()) {
                options.setAppPackage(appPackage);
            }
            if (appActivity != null && !appActivity.isBlank()) {
                options.setAppActivity(appActivity);
            }

            String url = (appiumServerUrl != null && !appiumServerUrl.isBlank())
                    ? appiumServerUrl
                    : "http://127.0.0.1:4723";

            AndroidDriver driver = new AndroidDriver(URI.create(url).toURL(), options);
            driver.manage().timeouts().implicitlyWait(Duration.ZERO);
            APPIUM_DRIVER.set(driver);
            return driver;
        } catch (Exception e) {
            throw new RuntimeException("Could not start Appium AndroidDriver: " + e.getMessage(), e);
        }
    }

    public static AndroidDriver getAppiumDriver() {
        return APPIUM_DRIVER.get();
    }

    public static Wait<AndroidDriver> buildAppiumWait(AndroidDriver driver) {
        return new FluentWait<>(driver)
                .withTimeout(DEFAULT_WAIT)
                .pollingEvery(DEFAULT_POLLING)
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);
    }

    public static WebElement waitUntilApkElementVisible(By locator) {
        AndroidDriver driver = getAppiumDriver();
        return buildAppiumWait(driver).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public static void clickApkElement(By locator) {
        AndroidDriver driver = getAppiumDriver();
        WebElement el = buildAppiumWait(driver).until(ExpectedConditions.elementToBeClickable(locator));
        el.click();
    }

    public static void clickApkByText(String visibleText) {
        By uiAutomatorLocator = AppiumBy.androidUIAutomator(
                "new UiSelector().textContains(\"" + visibleText.replace("\"", "\\\"") + "\")"
        );
        clickApkElement(uiAutomatorLocator);
    }

    public static void fillApkElement(By locator, String text) {
        WebElement el = waitUntilApkElementVisible(locator);
        el.clear();
        el.sendKeys(text);
    }

    public static void quitAppiumDriver() {
        AndroidDriver driver = APPIUM_DRIVER.get();
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception ignored) {
            } finally {
                APPIUM_DRIVER.remove();
            }
        }
    }

    public static String execCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append("\n");
                }
            }
            p.waitFor();
            return out.toString().trim();
        } catch (Exception e) {
            throw new RuntimeException("Error executing CLI command: " + String.join(" ", command), e);
        }
    }

    public static void configureApkSharedPrefs(String appPackage, String apiBaseUrl, String roomId) {
        execCommand("adb", "root");
        execCommand("adb", "shell", "am", "force-stop", appPackage);

        String prefsDir = "/data/data/" + appPackage + "/shared_prefs";
        String prefsFile = prefsDir + "/" + appPackage + ".preferences.xml";
        String xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                + "<map>\n"
                + "    <string name=\"api_base_url\">" + apiBaseUrl + "</string>\n"
                + "    <string name=\"room_id\">" + (roomId != null ? roomId : "1") + "</string>\n"
                + "</map>\n";

        String cmd = "mkdir -p " + prefsDir + " && cat << 'EOF' > " + prefsFile + "\n" + xml + "EOF\n"
                + "UID=$(stat -c '%u' /data/data/" + appPackage + ") && "
                + "chown $UID:$UID " + prefsFile + " && chmod 660 " + prefsFile;
        execCommand("adb", "shell", cmd);
    }

    public static String dumpAndroidLayoutNoIdle() {
        String out = execCommand("android", "layout", "--no-idle");
        if (out.contains("<hierarchy")) {
            return out;
        }
        execCommand("adb", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml");
        return execCommand("adb", "shell", "cat", "/sdcard/window_dump.xml");
    }

    public static String waitForAndroidLayoutText(String expectedText, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String lastXml;
        while (System.currentTimeMillis() < deadline) {
            lastXml = dumpAndroidLayoutNoIdle();
            if (lastXml.contains(expectedText)) {
                return lastXml;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Text '" + expectedText + "' was not found in the APK after " + timeoutMs + "ms");
    }

    public static void tapAndroidElementByText(String visibleText, long timeoutMs) {
        String xml = waitForAndroidLayoutText(visibleText, timeoutMs);
        Pattern nodePattern = Pattern.compile("<node[^>]*text=\"[^\"]*" + Pattern.quote(visibleText) + "[^\"]*\"[^>]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"");
        Matcher m = nodePattern.matcher(xml);
        if (!m.find()) {
            Pattern descPattern = Pattern.compile("<node[^>]*content-desc=\"[^\"]*" + Pattern.quote(visibleText) + "[^\"]*\"[^>]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"");
            m = descPattern.matcher(xml);
        }
        if (m.find()) {
            int x1 = Integer.parseInt(m.group(1));
            int y1 = Integer.parseInt(m.group(2));
            int x2 = Integer.parseInt(m.group(3));
            int y2 = Integer.parseInt(m.group(4));
            execCommand("adb", "shell", "input", "tap", String.valueOf((x1 + x2) / 2), String.valueOf((y1 + y2) / 2));
        } else {
            throw new IllegalStateException("Could not extract bounds coordinates for text: " + visibleText);
        }
    }

    public static File captureApkScreenshot(String outputRelativePath) {
        try {
            File outFile = new File(outputRelativePath);
            if (outFile.getParentFile() != null && !outFile.getParentFile().exists()) {
                outFile.getParentFile().mkdirs();
            }
            ProcessBuilder pb = new ProcessBuilder("adb", "exec-out", "screencap", "-p");
            pb.redirectOutput(outFile);
            pb.start().waitFor();

            if (outFile.exists() && outFile.length() > 0) {
                Allure.addAttachment("APK Screenshot (" + outFile.getName() + ")", "image/png",
                        Files.newInputStream(outFile.toPath()), ".png");
            }
            return outFile;
        } catch (Exception e) {
            throw new RuntimeException("Error capturing APK screenshot: " + e.getMessage(), e);
        }
    }

    public static File captureWebQrAndInjectIntoEmulator3DCamera(WebElement qrWebElement, String outputPngPath) {
        try {
            File rawQr = qrWebElement.getScreenshotAs(OutputType.FILE);
            BufferedImage original = ImageIO.read(rawQr);

            int border = 120;
            int paddedWidth = original.getWidth() + (border * 2);
            int paddedHeight = original.getHeight() + (border * 2);

            BufferedImage padded = new BufferedImage(paddedWidth, paddedHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = padded.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, paddedWidth, paddedHeight);
            g.drawImage(original, border, border, null);
            g.dispose();

            File finalQrFile = new File(outputPngPath);
            if (finalQrFile.getParentFile() != null && !finalQrFile.getParentFile().exists()) {
                finalQrFile.getParentFile().mkdirs();
            }
            ImageIO.write(padded, "png", finalQrFile);

            String absPath = finalQrFile.getAbsolutePath();
            execCommand("adb", "emu", "virtualscene-image", "wall", absPath);
            execCommand("adb", "emu", "virtualscene-image", "table", absPath);

            Allure.addAttachment("Injected 3D Camera QR (120px quiet zone)", "image/png",
                    Files.newInputStream(finalQrFile.toPath()), ".png");
            return finalQrFile;
        } catch (Exception e) {
            throw new RuntimeException("Error injecting Selenium QR into the emulator 3D camera: " + e.getMessage(), e);
        }
    }
}

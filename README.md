# 🚀 QA Automation Framework — Selenium 4.27+ · Java 21 · Maven (`pom.xml`) · Cucumber JVM 7 · RestAssured 5 · Multi-DB (`HikariCP`) · Appium · k6 · Allure

Enterprise QA automation template designed to bootstrap Web UI, REST API, Multi-Engine SQL Database, Mobile APK, and Performance testing projects by configuring only [`pom.xml`](pom.xml) and the `.env` file.

---

## 🏁 Step-by-Step Guide: How to Start a New Project from Scratch

### Step 1 — Clone or Copy the Template
```bash
git clone https://github.com/matiasvs/selenium-moon-lab.git
cd selenium-moon-lab
```

### Step 2 — Verify Prerequisites (Java 21+ and Maven)
```bash
# Verify Java (JDK 21 or higher) and Maven
java -version
mvn -version

# If `mvn` is not installed yet on Ubuntu/Debian:
sudo apt update && sudo apt install -y openjdk-21-jdk maven
```

### Step 3 — Customize the Project Name in [`pom.xml`](pom.xml)
Open [`pom.xml`](pom.xml) and edit only these lines at the top of the file:
```xml
<groupId>com.qa.template</groupId>
<artifactId>your-qa-project-name</artifactId>
<version>1.0.0-SNAPSHOT</version>
<name>QA Suite - Your Project Name</name>
```
*(You do not need to modify any dependency version or plugin; Selenium Manager will automatically download the driver for Chrome, Firefox, or Edge).*

### Step 4 — Configure your URLs, Credentials, and Database in `.env`
Create your local `.env` file from the [`.env.example`](.env.example) template:
```bash
cp .env.example .env
```
Edit `.env` with your project details:
1. **`TARGET_ENV`**: `DEV` (development), `STAGE` (staging read-only DB), or `GCP` (cloud).
2. **System URLs**:
   - `WEB_BASE_URL_DEV=http://localhost:3001`
   - `API_BASE_URL_DEV=http://localhost:3000`
3. **Database Engine (`DB_ENGINE`)**:
   - Choose between **`postgres`** (default), **`mysql`**, **`oracle`**, **`sqlserver`**, or **`mariadb`**.
   - Fill in `DATABASE_HOST`, `DATABASE_PORT`, `POSTGRES_DB` (database name), `POSTGRES_USER`, and `POSTGRES_PASSWORD`.
4. **QA Credentials**:
   - `QA_AUTH_EMAIL` and `QA_AUTH_PASSWORD`.

### Step 5 — Download Dependencies and Validate Project Compilation
```bash
mvn clean test-compile
```
Maven will automatically download **Selenium 4.27**, **Cucumber JVM 7.20**, **RestAssured 5.5**, **HikariCP**, the JDBC drivers (**PostgreSQL, MySQL, Oracle, SQL Server, MariaDB**), **Appium Java Client 9.3**, and **Allure 2.29**.

---

## 🧩 How to Add Your First Test in a New Module (3 Files)

To automate any new module (for example, `users` or `payments`), follow these 3 steps:

### 1. Create the Gherkin scenario in `src/test/resources/features/<module>/<name>.feature`
You can use [`sample-template.feature`](src/test/resources/features/sample/sample-template.feature) as a baseline:
```gherkin
@smoke @users
Feature: User Management

  Scenario: Successful login and Database verification
    Given the user opens the login screen in the Selenium browser
    When the user enters the configured environment credentials
    Then the database responds properly to the verification query "SELECT id, email FROM users WHERE email = 'qa.user@example.com'"
```

### 2. Create your Page Object extending [`BasePage.java`](src/test/java/com/qa/template/pages/BasePage.java)
Duplicate [`SampleLoginPage.java`](src/test/java/com/qa/template/pages/sample/SampleLoginPage.java) inside `src/test/java/com/qa/template/pages/<module>/`:
- **Do not use `Thread.sleep()` or direct `driver.findElement()` calls**.
- Use the inherited `BasePage` methods that apply **`wait.until(ExpectedConditions...)`** over `FluentWait` and project each action onto the **browser's `12vh` Dual-Panel HUD**:
  - `clickElement(By locator, String description)`
  - `fillElement(By locator, String text, String description)`
  - `waitUntilVisible(By locator)` / `waitUntilClickable(By locator)` / `waitUntilInvisible(By locator)`
  - `waitUntilTextPresent(By locator, String text)` / `waitUntilUrlContains(String path)`
  - `selectCustomDropdownOption(By trigger, By option, String description)`
  - `inspectDisabledElement(By disabledButton, String reasonPrefix, By optionalRow)`

### 3. Create or reuse Steps in `src/test/java/com/qa/template/steps/<module>/`
Use [`SampleTemplateSteps.java`](src/test/java/com/qa/template/steps/sample/SampleTemplateSteps.java) as a baseline:
- **For Web UI (Selenium)**: Instantiate your Page Object passing `DriverFactory.getDriver()`.
- **For REST API (RestAssured)**: Call `RestAssuredHelper.sendRequest(title, method, path, token, jsonBody, headers)` (see [`RestAssuredHelper.java`](src/test/java/com/qa/template/api/RestAssuredHelper.java)).
- **For Database (PostgreSQL / MySQL / Oracle / SQL Server / MariaDB)**: Call `DatabaseHelper.queryList(title, sql, params...)` (see [`DatabaseHelper.java`](src/test/java/com/qa/template/db/DatabaseHelper.java)).
- **For Mobile `.apk` Tests (3 Modes: Appium, ADB CLI, or Hybrid Combo with 3D QR Camera)**: Use [`MobileApkJavaHelper.java`](src/test/java/com/qa/template/mobile/MobileApkJavaHelper.java).
- **For Grafana k6 Load Tests from Selenium**: Use [`K6RunnerHelper.java`](src/test/java/com/qa/template/performance/K6RunnerHelper.java).

---

## ▶️ Quick CLI Commands (Maven + Allure)

```bash
# 1. Run the entire suite in DEV (visible Chrome in Full HD 1920x1080):
mvn clean test -Dtarget.env=DEV

# 2. Run in Headless mode filtering by Cucumber tag (@smoke, @api, @web):
mvn test -Dtarget.env=DEV -Dheadless=true -Dcucumber.filter.tags="@api"

# 3. Run a specific .feature file:
mvn test -Dtarget.env=DEV -Dcucumber.features="src/test/resources/features/sample/sample-template.feature"

# 4. Switch browser (chrome | firefox | edge) or use split screen for APK (browser.right=true):
mvn test -Dbrowser=firefox -Dheadless=false
mvn test -Dbrowser=chrome -Dbrowser.right=true

# 5. Switch database engine via command line (postgres | mysql | oracle | sqlserver | mariadb):
mvn test -Dtarget.env=DEV -Ddb.engine=mysql
mvn test -Dtarget.env=DEV -Ddb.engine=oracle

# 6. Run Grafana k6 load test from Maven:
mvn exec:exec@k6-load -Dk6.vus=4

# 7. Generate Single-File Allure Report (--single-file to open via double-click file:// without 500 CORS error):
allure generate target/allure-results --clean --single-file -o target/allure-report
```

---

## 📂 Project Structure

```text
selenium-moon-lab/
├── .env.example                               # Environment variables template (copy to .env)
├── .gitignore                                 # Files ignored by Git
├── README.md                                  # Quick-start guide and architecture reference
├── pom.xml                                    # Centralized Maven dependencies
└── src/test/
    ├── java/com/qa/template/
    │   ├── config/EnvConfig.java              # Automatic .env and Maven -D properties reader
    │   ├── context/TestContext.java           # Shared context across Steps (PicoContainer)
    │   ├── driver/DriverFactory.java          # ThreadLocal<WebDriver> with Selenium Manager
    │   ├── pages/
    │   │   ├── BasePage.java                  # Base POM with wait.until(...) (FluentWait) + 12vh Dual HUD
    │   │   └── sample/SampleLoginPage.java    # Sample Page Object ready to duplicate
    │   ├── api/RestAssuredHelper.java         # RestAssured Client + AllureRestAssured + 12vh HUD
    │   ├── db/DatabaseHelper.java             # Multi-Engine HikariCP Pool (Postgres, MySQL, Oracle, SQLServer, MariaDB)
    │   ├── mobile/MobileApkJavaHelper.java    # All 3 APK modes (Appium, ADB CLI, and Combo Pro with 3D QR Camera)
    │   ├── performance/K6RunnerHelper.java    # Grafana k6 runner from Selenium/Cucumber
    │   ├── runners/TestRunner.java            # JUnit 5 Suite Runner (@Suite + Cucumber + Allure)
    │   └── steps/
    │       ├── Hooks.java                     # @After (Automatic Allure Screenshot + quitDriver)
    │       └── sample/SampleTemplateSteps.java
    └── resources/
        ├── allure.properties
        ├── junit-platform.properties
        └── features/sample/sample-template.feature
```

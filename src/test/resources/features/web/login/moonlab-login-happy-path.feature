# language: en
@web @ui @login @happy-path @go-qa @moonlab
Feature: Moon-Lab Web Login Happy Path E2E Certification (Selenium 4 + RestAssured 5.5 + PostgreSQL 16)
  End-to-end Happy Path Web UI certification of the Moon-Lab Login & Session Management portal
  on http://localhost:3000 with live 12vh Dual-Panel HUD, smart dynamic waits (wait.until),
  REST API contract cross-checks (/auth/login, /auth/me, /auth/mailpit-messages), and PostgreSQL
  ACID verification (auth.users, cash_funds.wallets, auth.password_resets).

  @login-01 @cashier
  Scenario: 01. Manual Web Login as POS Cashier (cajero@moonlab.io) and multi-layer session verification
    Given the user opens the Moon-Lab Web Login screen in the browser
    When the user submits the Web Login form with email "cajero@moonlab.io" and password "Moon2026!"
    Then the Web Dashboard displays the active session pill for "Nicolás Ferreyra" with role "CASHIER" and healthy PostgreSQL catalogs
    And the REST API POST "/auth/login" and GET "/auth/me" confirm valid JWT session for "cajero@moonlab.io" with role "CASHIER"
    And PostgreSQL confirms user "cajero@moonlab.io" is active with role "CASHIER" and status "VALIDATED"

  @login-02 @superadmin
  Scenario: 02. Manual Web Login as SuperAdmin (superadmin@moonlab.io) and navigation to Users Directory tab
    Given the user opens the Moon-Lab Web Login screen in the browser
    When the user submits the Web Login form with email "superadmin@moonlab.io" and password "Moon2026!"
    Then the Web Dashboard displays the active session pill for "Matias Veron" with role "SUPER_ADMIN" and healthy PostgreSQL catalogs
    When the user navigates to the "users" module tab in the dashboard
    Then the REST API POST "/auth/login" and GET "/auth/me" confirm valid JWT session for "superadmin@moonlab.io" with role "SUPER_ADMIN"
    And PostgreSQL confirms user "superadmin@moonlab.io" is active with role "SUPER_ADMIN" and status "VALIDATED"

  @login-03 @client-wallet
  Scenario: 03. One-click Quick Profile Web Login as Validated Client (delfina.socio@moonlab.io) and wallet verification
    Given the user opens the Moon-Lab Web Login screen in the browser
    When the user clicks the 1-Click Quick Login profile button for role "CLIENT_VALIDATED"
    Then the Web Dashboard displays the active session pill for "Delfina Soriano Validada" with role "CLIENT_VALIDATED" and healthy PostgreSQL catalogs
    And the REST API POST "/auth/login" and GET "/auth/me" confirm valid JWT session for "delfina.socio@moonlab.io" with role "CLIENT_VALIDATED"
    And PostgreSQL confirms user "delfina.socio@moonlab.io" is active with role "CLIENT_VALIDATED" and status "VALIDATED"
    And PostgreSQL confirms client "delfina.socio@moonlab.io" has an active multi-balance wallet in "cash_funds.wallets"

  @login-04 @role-switcher
  Scenario: 04. Live 1-Click Role Switcher transition from Treasury Supervisor (SUPERVISOR) to Backoffice Admin (BACKOFFICE_ADMIN)
    Given the user opens the Moon-Lab Web Login screen in the browser
    When the user clicks the 1-Click Quick Login profile button for role "SUPERVISOR"
    Then the Web Dashboard displays the active session pill for "Diego Morales" with role "SUPERVISOR" and healthy PostgreSQL catalogs
    When the user switches role in the top header bar to "BACKOFFICE_ADMIN"
    Then the Web Dashboard displays the active session pill for "Backoffice Admin Moon-Lab" with role "BACKOFFICE_ADMIN" and healthy PostgreSQL catalogs
    And the REST API POST "/auth/login" and GET "/auth/me" confirm valid JWT session for "backoffice.admin@moonlab.io" with role "BACKOFFICE_ADMIN"
    And PostgreSQL confirms user "backoffice.admin@moonlab.io" is active with role "BACKOFFICE_ADMIN" and status "VALIDATED"

  @login-05 @password-recovery @logout
  Scenario: 05. Password Recovery Happy Path via Mailpit 6-digit OTP, Web Login and clean Logout
    Given the user opens the Moon-Lab Web Login screen in the browser
    When the user opens the Forgot Password recovery panel and requests an OTP for "cajero@moonlab.io"
    Then PostgreSQL "auth.password_resets" and Mailpit API confirm a PENDING 6-digit OTP for "cajero@moonlab.io"
    When the user submits the retrieved 6-digit OTP and new password "Moon2026!" in the recovery form
    And the user submits the Web Login form with email "cajero@moonlab.io" and password "Moon2026!"
    Then the Web Dashboard displays the active session pill for "Nicolás Ferreyra" with role "CASHIER" and healthy PostgreSQL catalogs
    And PostgreSQL confirms user "cajero@moonlab.io" is active with role "CASHIER" and status "VALIDATED"
    And the user logs out cleanly from the Web Dashboard and returns to the Login screen

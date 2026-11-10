# language: en
@smoke @api @moonlab
Feature: Moon-Lab Smoke & Auth API Certification (RestAssured 5.5 + PostgreSQL 16 HikariCP)
  Certification of Moon-Lab REST API endpoints on http://localhost:4000 covering Health, Catalogs,
  JWT Authentication, Authenticated Profile (/auth/me), RBAC Roles (/roles), AFIP Modulo 11 CUIT
  validation, negative security assertions (400/401), sensitive field masking, and SQL cross-checks.

  @health
  Scenario: 01. Verify GET /health contract, JSON Schema, SLA and PostgreSQL multi-schema connectivity
    When the "/health" endpoint is queried via RestAssured
    Then the HTTP response has status code 200
    And the HTTP response is JSON and responds within 5000 ms
    And the HTTP response matches the JSON schema "schemas/health-schema.json"
    And the JSON path "status" equals "ok" and "service" equals "moon-lab-server"
    And the database responds properly to the verification query "SELECT current_database() AS db_name, current_user AS db_user"

  @auth @login
  Scenario: 02. Authenticate SuperAdmin via POST /auth/login, verify JWT contract, sensitive masking and SQL user record
    When the user authenticates via RestAssured POST "/auth/login" with configured QA credentials
    Then the HTTP response has status code 201
    And the HTTP response is JSON and responds within 5000 ms
    And the HTTP response matches the JSON schema "schemas/auth-login-schema.json"
    And the login response returns a valid Bearer JWT for role "SUPER_ADMIN" without exposing password fields
    And the PostgreSQL "auth.users" table confirms email "superadmin@moonlab.io" has role "SUPER_ADMIN" and status "VALIDATED"

  @auth @me
  Scenario: 03. Retrieve authenticated profile via GET /auth/me with Bearer JWT and cross-check with PostgreSQL
    Given the user authenticates via RestAssured POST "/auth/login" with configured QA credentials
    When the "/auth/me" endpoint is queried via RestAssured
    Then the HTTP response has status code 200
    And the HTTP response is JSON and responds within 5000 ms
    And the "/auth/me" profile matches email "superadmin@moonlab.io", role "SUPER_ADMIN" and does not expose password hashes
    And the "/auth/me" user id and document_id match the PostgreSQL record in "auth.users"

  @auth @roles
  Scenario: 04. List RBAC catalog via GET /roles and cross-validate total role count against PostgreSQL auth.roles
    Given the user authenticates via RestAssured POST "/auth/login" with configured QA credentials
    When the "/roles" endpoint is queried via RestAssured
    Then the HTTP response has status code 200
    And the HTTP response is JSON and responds within 5000 ms
    And the "/roles" catalog contains at least 9 roles with required fields and matches PostgreSQL "auth.roles" count

  @health @catalogs
  Scenario: 05. Certify branch catalogs health via GET /health/catalogs and cross-check active branches in PostgreSQL
    Given the user authenticates via RestAssured POST "/auth/login" with configured QA credentials
    When the "/health/catalogs" endpoint is queried via RestAssured
    Then the HTTP response has status code 200
    And the HTTP response is JSON and responds within 5000 ms
    And the branch catalogs health flag "healthy" is true and matches active branches in PostgreSQL "core.branches"

  @auth @cuit
  Scenario: 06. Validate AFIP Modulo 11 CUIT checksum via GET /auth/validate-cuit (valid and invalid check digits)
    Given the user authenticates via RestAssured POST "/auth/login" with configured QA credentials
    When the "/auth/validate-cuit/27-39887766-2" endpoint is queried via RestAssured
    Then the HTTP response has status code 200
    And the CUIT validation result has valid true and formatted "27-39887766-2"
    When the "/auth/validate-cuit/27-39887766-9" endpoint is queried via RestAssured
    Then the HTTP response has status code 200
    And the CUIT validation result has valid false and suggested CUIT "27-39887766-2"

  @auth @negative
  Scenario: 07. Reject unauthenticated requests to protected endpoints GET /auth/me and GET /roles with HTTP 401
    Given the client has no Bearer token in the context
    When the "/auth/me" endpoint is queried via RestAssured
    Then the HTTP response has status code 401
    And the error response contains status 401 and "Unauthorized"
    When the "/roles" endpoint is queried via RestAssured
    Then the HTTP response has status code 401

  @auth @negative
  Scenario: 08. Reject malformed empty payload in POST /auth/login with HTTP 400 Payload Validation Failed
    When a POST request to "/auth/login" is sent via RestAssured with raw JSON "{}"
    Then the HTTP response has status code 400
    And the HTTP response is JSON and responds within 5000 ms
    And the 400 validation response reports mandatory fields "email" and "password"

  @auth @negative
  Scenario: 09. Reject unknown user credentials in POST /auth/login with HTTP 401 Unauthorized
    When a POST request to "/auth/login" is sent via RestAssured with email "unregistered.qa@moonlab.io" and password "WrongPassword123!"
    Then the HTTP response has status code 401
    And the HTTP response is JSON and responds within 5000 ms
    And the 401 response does not return any "access_token"

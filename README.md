# CIVICFLOW — Election Management Suite (CEMS)

**CIVICFLOW** is a professional desktop-based **educational election simulation platform** built with **Core Java**, **JavaFX 21**, **MySQL 8.x**, **JDBC (HikariCP)**, and **Apache Maven**. It enables administrators to configure constituencies, political parties, candidates, simulated voters, multi-stage elections, transactional anonymous simulated ballot casting, real-time analytics, immutable audit logging, and branded PDF/CSV reporting.

> **EDUCATIONAL SIMULATION NOTICE:** CIVICFLOW is designed strictly for educational, academic, and local demonstration purposes using simulation data. According to the Election Commission of India (ECI), official election procedures require strictly neutral, database-driven vote counting derived solely from authentic recorded ballots without predetermined winners or fabricated results. CIVICFLOW adheres strictly to these principles. It is **not** a certified public election platform and must **never** be connected to real electoral rolls, government identity databases, or public election infrastructure.

---

## Key Features

- **Role-Based Access Control (RBAC):** Enforced in the Java service layer (`SUPER_ADMIN`, `ELECTION_ADMIN`, `VOTER`).
- **Secure Authentication & Credential Governance:** Salted BCrypt password hashing (`jBCrypt`, cost factor 10), timing-safe generic login errors, configurable failed-login lockouts, session invalidation on logout, administrative password reset, and mandatory first-login password rotation (`must_change_password=TRUE`).
- **Enterprise Admin Dashboard:** Live MySQL-backed KPI metrics, JavaFX graphical charts, quick administrative actions, and real-time audit activity feed.
- **Party Management (10 Active Parties):** Native support for all 10 standard simulation parties (`BJP`, `INC`, `AAP`, `CJP`, `JDS`, `RJP`, `TMC`, `BSP`, `AIADMK`, `SP`), unique code/name constraints, candidate assignments, and neutral styling without misleading party logos.
- **Voter, Candidate & Constituency Management:** Multi-criteria search, filtered tables, pagination, approval/rejection workflows, account suspension/reactivation, referential deletion protection, and validated photo uploads (extension + magic byte + size checks).
- **Deterministic Election Lifecycle:** Finite-state-machine transitions (`DRAFT → SCHEDULED → OPEN → CLOSED → PUBLISHED / CANCELLED`) with injected `Clock` testability and immutable published records.
- **Separated Simulated Voting Architecture:**
  - `voter_participation` records *that* an eligible voter participated (`UNIQUE(voter_id, election_id)`), with **no candidate column**.
  - `anonymous_ballots` records the candidate choice, with **no voter_id column**.
  - Both execute atomically inside a single JDBC transaction with automatic rollback on failure and concurrency protection against duplicate voting.
- **Database-Driven Results & 11 Administrative Reports:** Real-time SQL aggregation sorting results by authentic recorded votes, leading party determination, zero-candidate party handling (0 votes, 0.00% without fabricating ballots), OpenPDF branded PDF exports with simulation watermark, and RFC-4180 CSV exports protected against spreadsheet formula injection.
- **Embedded Web Server & API Security:** Lightweight HTTP server (`/api/health`, `/api/results/:id`), protected by 4 MiB max payload limits (`PayloadTooLargeException` returning HTTP 413), managed executor shutdown, and sanitized browser portal forms with disabled password autofill and cleared password fields on submit.
- **Audit Trail:** Append-only logging of authentication, administration, election transitions, and non-revealing participation events.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Core Java (compiles to Java 17 bytecode; compatible with JDK 17 LTS and JDK 21 LTS) |
| Desktop UI | JavaFX 21.0.5 (`javafx-controls`, `javafx-fxml`, `javafx-graphics`, `javafx-swing`) + Custom CSS |
| Database | MySQL 8.x (InnoDB, `utf8mb4`), JDBC (`mysql-connector-j 8.4.0`), HikariCP 5.1.0 |
| Security | `org.mindrot:jbcrypt 0.4` (BCrypt cost 10), PreparedStatements, Service-Layer RBAC |
| Reporting | OpenPDF 2.0.3 (PDF), Custom Formula-Safe CSV Exporter |
| Logging | SLF4J 2.0.16 + Logback 1.5.12 |
| Build & Testing | Apache Maven 3.9+, JUnit 5.11.3, Mockito 5.14.2 |

---

## Test Verification Summary

The automated test suite has been fully executed and verified:

| Metric | Verified Value | Status |
|---|---|---|
| Test Classes | 11 | Complete |
| Tests Executed | 48 | Complete |
| Passed | 48 | **PASS** |
| Failures | 0 | **PASS** |
| Errors | 0 | **PASS** |
| Skipped | 0 | **PASS** |
| Build Outcome | **BUILD SUCCESS** | **VERIFIED** |

All 48 automated unit, integration, and security tests pass with 100% success.

---

## Project Structure

```text
CIVICFLOW/
├── config/
│   └── application.properties.example   # External configuration template (never commit real secrets)
├── database/
│   ├── schema.sql                       # Normalized 14-table MySQL 8 schema
│   ├── seed_demo_data.sql               # Repeatable demo dataset (10 parties, 14 candidates)
│   ├── cleanup_demo_data.sql            # Resets transactional/demo records safely
│   └── migration_10_parties.sql         # Migration adding 10 standard simulation parties
├── scripts/
│   ├── build-windows.bat / run-windows.bat
│   └── build-linux-macos.sh / run-linux-macos.sh
├── src/
│   ├── main/java/com/civicflow/         # Layered Core Java, JavaFX & Web server source
│   ├── main/resources/                  # CSS design system, HTML assets & Logback config
│   └── test/java/com/civicflow/         # 11 unit, integration, and UI smoke test classes
├── pom.xml                              # Maven build specification (Java 17, JavaFX 21, MySQL 8)
└── README.md                            # Project overview & quick start guide
```

---

## Quick Start

### 1. Prerequisites
- **Java JDK 17 LTS or JDK 21 LTS**
- **Apache Maven 3.9+**
- **MySQL Server 8.0+** (running on port `3306`)

### 2. Initialize MySQL Database
```powershell
# Using MySQL command line or PowerShell
Get-Content database\schema.sql -Raw | mysql -u root -p civicflow_db
Get-Content database\seed_demo_data.sql -Raw | mysql -u root -p civicflow_db
```

### 3. Configure Database Credentials
Option A — Environment variables (recommended):
```powershell
$env:CIVICFLOW_DB_URL="jdbc:mysql://localhost:3306/civicflow_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:CIVICFLOW_DB_USER="root"
$env:CIVICFLOW_DB_PASSWORD="YOUR_LOCAL_MYSQL_PASSWORD"
```
Option B — Local configuration file (gitignored):
Copy `config/application.properties.example` to `config/application.properties` and provide your local MySQL credentials.

### 4. Build & Run Automated Tests
```powershell
# Windows
scripts\build-windows.bat

# Or directly with Maven:
mvn clean test
```

### 5. Launch CIVICFLOW Desktop Application
```powershell
# Windows
scripts\run-windows.bat

# Or launch shaded JAR directly:
java -jar target\civicflow-cems-1.0.0-shaded.jar
```

---

## Demonstration Accounts & Evaluation Credentials

All demonstration accounts are pre-configured in MySQL with salted BCrypt password hashing:

| Role | Username | Password | Purpose & Scope |
|---|---|---|---|
| **System Administrator** | `admin` | `Abc123` | Full administrative control, system settings, audit logs |
| **Election Officer** | `officer` | `Abc123` | Election lifecycle, candidates, parties, constituencies, reports |
| **Compliance Auditor** | `auditor` | `Abc123` | Real-time monitoring, compliance verification, read-only audit |
| **Registered Voter** | `voter.maya` | `Abc123` | Eligible simulated voter for Northbrook District (`ELC-2026-SPR01`) |
| **Registered Voter** | `voter.priya` | `Abc123` | Eligible simulated voter for Riverdale Metro (`ELC-2026-SPR01`) |

> [!TIP]
> All accounts use the standard evaluation password `Abc123`. Upon first login, users can update passwords through their profile or administration settings.

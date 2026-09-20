# SyncMaster Portal

**SyncMaster Portal (PayMaster Attendance Bridge)** is a Spring Boot middleware application designed to seamlessly integrate Hik-Connect OpenAPI access control devices with a MySQL Database used by PayMaster. 

It periodically pulls raw attendance logs and access events from Hik-Connect and securely inserts them into the local MySQL database while ensuring no duplicates are created. It also features a sleek, Stripe/Notion-themed frontend dashboard for monitoring system health, connected devices, and executing manual syncs.

---

## 🚀 Features

- **Automated Synchronization:** Runs scheduled background tasks (e.g., daily at 1:00 AM) to pull the previous day's attendance data automatically.
- **Manual Override Sync:** Provides a user-friendly UI to manually trigger a sync for any custom date range.
- **Secure Access:** Manual syncs are protected by a configurable credential whitelist.
- **Device Health Monitoring:** Pings the Hik-Connect API every 5 minutes to monitor the online/offline status of all connected biometric and access controller devices.
- **Timezone Awareness:** Intelligently handles UTC (`Z`) to local `Asia/Colombo` time conversions to ensure exact timestamp accuracy in PayMaster.
- **Live Dashboard:** Clean, responsive UI built with Vanilla JS and Bootstrap 5, providing real-time system logs and health statuses.

---

## 🛠️ Technology Stack

- **Backend:** Java 17, Spring Boot 3.x
- **Database Access:** Spring Data JPA, Hibernate, MySQL Connector
- **Database:** MySQL 5.7
- **HTTP Client:** Spring `RestClient`
- **Frontend:** HTML5, CSS3, Vanilla JavaScript, Bootstrap 5
- **Build Tool:** Maven

---

## 🏗️ Application Architecture

The application is built using a standard Spring MVC architecture with clearly defined responsibilities:

### Core Services
1. **`HikConnectAuthService`**: Manages the API authentication lifecycle. It signs requests using the `appKey` and `secretKey`, fetches short-lived `access_token`s, and caches them securely in memory until they expire (usually 12 hours).
2. **`HikConnectDataService`**: The core engine. It handles:
   - Pagination across the Hik-Connect OpenAPI (using `pageSize` limits).
   - Parsing massive JSON arrays.
   - Timezone conversion (`ZonedDateTime`).
   - Saving mapped `AttendanceLog` records to the database using `insertIgnore` queries.
3. **`SyncScheduler`**: Spring `@Scheduled` component that manages recurring jobs, including the daily midnight data pull and the 5-minute device health checks.
4. **`SyncStateService`**: An in-memory, thread-safe state container that holds real-time variables for the frontend (API status, Database status, recent system logs, connected device list).

### Data Layer
- **`AttendanceLogRepository`**: A Spring Data interface. Uses native queries (e.g., `INSERT IGNORE`) to safely insert records without throwing constraint violation exceptions when the script attempts to sync records that have already been imported.

### Presentation Layer
- **`DashboardController`**: Exposes REST endpoints (`/api/health`, `/api/logs`, `/api/sync/force`) to the frontend UI.
- **`index.html`**: A static, single-page application (SPA) that constantly polls `/api/health` and `/api/logs` to render a live dashboard.

---

## 💻 Developer Guide

### Prerequisites
- JDK 17 or higher
- Maven 3.6+
- MySQL 5.7+

### Running Locally

1. **Clone the repository / open the directory.**
2. **Configure `application.properties`**:
   Navigate to `src/main/resources/application.properties` and verify your credentials:
   ```properties
   # Hik-Connect API
   hikconnect.api.baseUrl=https://isgp.hikcentralconnect.com
   hikconnect.api.appKey=YOUR_APP_KEY
   hikconnect.api.secretKey=YOUR_SECRET_KEY

   # MySQL Database
   spring.datasource.url=jdbc:mysql://YOUR_DB_IP:3306/techseed_paymaster?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Colombo
   spring.datasource.username=root
   spring.datasource.password=secret

   # Whitelist format -> username:email
   syncmaster.force-sync.whitelist=admin:admin@example.com
   ```
3. **Run the Application:**
   ```bash
   mvn spring-boot:run
   ```
4. **Access the Dashboard:**
   Open your browser and navigate to `http://localhost:8080`.

---

## ⚠️ Important Implementation Details & Gotchas

### 1. Timezone Handling (Critical)
Hik-Connect API returns timestamps in UTC (e.g., `2026-09-19T02:59:44Z`). 
When parsing, **do not** use basic `LocalDateTime.parse()`. You must use `ZonedDateTime` or `OffsetDateTime` to preserve the UTC timezone, and then convert it to the system default timezone (`Asia/Colombo` / `+05:30`) before saving it to the database. Failure to do so will result in attendance logs being recorded 5.5 hours late.

### 2. API Pagination Limits
The Hik-Connect API enforces strict page sizes. 
- The **Events/Attendance** endpoint works reliably at `pageSize: 500`.
- The **Devices** endpoint can fail if the payload requests too much data. `pageSize: 50` is used for `devices/get`.
- Device lists are located under the `data.device` JSON key, while record logs are located under `data.list`.

### 3. Database Schema Updates
The database uses `spring.jpa.hibernate.ddl-auto=none`. The application **will not** attempt to automatically generate or alter MySQL tables. The database schema is assumed to be strictly controlled by PayMaster. 

### 4. Custom Native Queries
We use `INSERT IGNORE` native queries in the JPA repository instead of standard `save()` methods. This allows the scheduled task to pull large time blocks of data (e.g., 24 hours) without crashing if half of those records were already synced during a previous run.

---

## 🔐 Security

- **Manual Sync Authorization:** Since hitting the Hik-Connect API heavily can cause rate limiting, arbitrary manual syncs are restricted via the frontend UI. The user must provide a Username and Email combination that matches the comma-separated `syncmaster.force-sync.whitelist` property.
- **Frontend Storage:** Upon successful manual sync authorization, credentials are saved to browser `localStorage` and automatically invalidated after 7 days.

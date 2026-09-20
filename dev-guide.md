# AI Developer Guide: Hik-Connect to MySQL Sync Service

### Product Name: SyncMaster Portal 
### Product Tagline: PayMaster Attendance Bridge to Hik-Connect


## 1. Project Overview
This project is a synchronization service that pulls raw access/authentication events from the **Hik-Connect Team (HikCentral Connect) OpenAPI** and inserts them into a **MySQL 5.7** database. 

It includes an automated scheduled sync engine and a single-screen unified UI for monitoring health and forcing manual syncs.

### Tech Stack Recommendation
*   **Backend:** Java 17 + Spring Boot 3.x (or Node.js/Python if preferred).
*   **Database:** MySQL 5.7 (Strict attention to 5.7 date/timestamp compatibility).
*   **Frontend:** Plain HTML/JS/CSS (Bootstrap) served statically, making AJAX calls to the backend.

---

## 2. Database Schema (MySQL 5.7)
Create a table named `attendance_logs`. You must strictly map the API response data to this exact schema.

```sql
CREATE TABLE `attendance_logs` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `EmpId` varchar(100) DEFAULT NULL,           -- Mapped from API 'personCode'
  `AuthDateAndTime` timestamp NULL DEFAULT NULL, -- Mapped from API 'occurTime'
  `AuthDate` date DEFAULT NULL,                -- Extracted from API 'occurTime'
  `AuthTime` time DEFAULT NULL,                -- Extracted from API 'occurTime'
  `Direction` varchar(100) DEFAULT NULL,       -- Mapped from event type or logic
  `Device` varchar(500) DEFAULT NULL,          -- Mapped from API 'deviceName'
  `Serial` varchar(500) DEFAULT NULL,          -- Mapped from API 'devSerialNo'
  `Person` varchar(500) DEFAULT NULL,          -- Mapped from API 'personName'
  `Card` varchar(100) DEFAULT NULL,            -- Mapped from API 'cardNo'
  `synctime` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00', -- Current timestamp
  UNIQUE KEY `unique_punch` (`EmpId`, `AuthDateAndTime`) -- Deduplication key
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
> **MySQL 5.7 Constraint Alert:** Handling `DEFAULT '0000-00-00 00:00:00'` requires ensuring the MySQL session `sql_mode` does not have `NO_ZERO_DATE` enabled, or using `CURRENT_TIMESTAMP` instead.

---

## 3. Hik-Connect OpenAPI Reference

**Base URL Configuration:** 
The regional base URL must be configurable in `application.properties`. 
*   Asia Pacific / India: `https://isgp.hikcentralconnect.com`
*   Europe: `https://ieu.hikcentralconnect.com`

**HTTP Client Rules:** Must configure the HTTP Client to **FOLLOW REDIRECTS** (Normal/Always) to handle `302 Found` responses from the API gateways.
**Headers:** All requests (except Auth) require the header: `Token: <accessToken>`

### A. Authentication (Token Generation)
**Endpoint:** `POST /api/hccgw/platform/v1/token/get`
**Payload:** `{"appKey": "{{appKey}}", "secretKey": "{{appSecret}}"}`
**Logic:** Extract `data.accessToken` from the response. Cache this token; it is valid for 7 days.

### B. Raw Access Events (PRIMARY DATA SOURCE)
Use this endpoint to fetch raw punches, disregarding schedules.
**Endpoint:** `POST /api/hccgw/acs/v1/event/certificaterecords/search`
**Payload:**
```json
{
  "pageIndex": 1,
  "pageSize": 500,
  "searchCriteria": {
    "beginTime": "2023-10-21T00:00:00+08:00",
    "endTime": "2023-10-21T23:59:59+08:00"
  }
}
```

### C. Helpful Supplementary Endpoints
These endpoints can be used to build out the health monitoring dashboard or sync additional data if needed.

*   **Get Connected Devices (Device Health):**
    `POST /api/hccgw/resource/v1/devices/get`
    Payload: `{"pageIndex": 1, "pageSize": 50}`
    *Useful for ensuring devices are online (`onlineStatus: 1`).*

*   **Get Daily Timecard Summary (Alternative to Raw Events):**
    `POST /api/hccgw/attendance/v1/report/totaltimecard/list`
    Payload: `{"pageIndex":1, "pageSize":500, "beginTime":"2023-10-21T...", "endTime":"2023-10-21T..."}`
    *Note: Returns aggregated `clockInTime` and `clockOutTime` based on timetables. Excludes out-of-schedule punches.*

*   **Get Employee List (Syncing Persons):**
    `POST /api/hccgw/person/v1/persons/list`
    Payload: `{"pageIndex": 1, "pageSize": 500}`
    *Useful for syncing employee names and Person Codes (`personCode`) to the local DB.*

---

## 4. Backend Sync Engine Logic

1.  **Automated Cron Job:** Create a scheduler (e.g., `@Scheduled(cron = "0 0 2 * * ?")`) that runs every night at 2:00 AM to fetch Raw Access Events for `Yesterday`.
2.  **Deduplication:** Use `INSERT IGNORE` or `ON DUPLICATE KEY UPDATE synctime=VALUES(synctime)` to ensure running a manual sync overlapping an auto-sync does not create duplicate rows for the same `EmpId` + `AuthDateAndTime`.
3.  **State Management:** Track system health in memory or a small local SQLite/Config file to serve to the UI: `last_sync_time`, `last_sync_status`, `api_status`.

---

## 5. Unified Dashboard UI
Create a single, lightweight HTML page (`index.html`) using Bootstrap for styling.

**UI Layout Requirements:**
*   **Header:** System Name & Current Time.
*   **Top Row (Health Cards):** API Status (Green/Red), Database Status (Green/Red), Device Status (Online/Offline count), Last Sync Timestamp.
*   **Middle Row (Force Sync Panel):**
    *   Date Picker (Start Date, End Date).
    *   **"Force Sync Now" Button:** Calls backend API (`/api/sync/force?start=X&end=Y`).
    *   Loading spinner/progress bar during sync.
*   **Bottom Row (Logs):** A scrolling text area showing the latest 10 sync actions (e.g., "Pulled 150 records for 2026-09-10... Inserted successfully").

---

## 6. Development Rules
1.  **Do not use mock APIs** for the Hikvision integration. Build the exact HTTP POST requests mapping to the endpoints provided above.
2.  **Externalize Configuration:** Put base URL, credentials, and DB config in `application.properties`.
3.  **Resilience:** Wrap API calls in try/catch blocks. Log exact HTTP responses to the UI console on failure.

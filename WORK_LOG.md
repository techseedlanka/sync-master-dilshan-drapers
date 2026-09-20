# Work Log

## 2026-09-20
- Scaffolded Spring Boot project for SyncMaster Portal.
- Configured application properties for MySQL 5.7 and Hik-Connect endpoints.
- Created `schema.sql` and `AttendanceLog` JPA Entity for database models.
- Implemented `HikConnectAuthService` with 6-day token caching.
- Implemented `HikConnectDataService` to query events and upsert to database using native queries.
- Set up a daily cron job (`SyncScheduler`) at 2 AM.
- Created `DashboardController` for REST APIs exposing system health, logs, and manual force-sync actions.
- Designed a unified dashboard UI (`index.html`) utilizing Bootstrap, Fetch API, and auto-polling.
- Fixed missing model and repository (AttendanceLog and AttendanceLogRepository) and rewrote HikConnectDataService to use the primary events API.
- Fixed device array mapping issue from the Hik-Connect API by correcting the JSON payload key from 'list' to 'device'.
- Restructured `index.html` to implement a Notion/Stripe aesthetic layout with a clean UI.
- Implemented real-time async polling in `index.html` to keep the "Force Sync" button spinning while the backend is fetching data.
- Added a full Developer Guide / Project Documentation to `README.md`.
- Initialized local Git repository and committed the project codebase.

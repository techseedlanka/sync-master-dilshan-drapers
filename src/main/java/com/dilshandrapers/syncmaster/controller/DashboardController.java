package com.dilshandrapers.syncmaster.controller;

import com.dilshandrapers.syncmaster.service.HikConnectDataService;
import com.dilshandrapers.syncmaster.service.SyncStateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
public class DashboardController {

    @org.springframework.beans.factory.annotation.Value("${syncmaster.force-sync.whitelist}")
    private String forceSyncWhitelist;

    private final SyncStateService stateService;
    private final HikConnectDataService dataService;

    public DashboardController(SyncStateService stateService, HikConnectDataService dataService) {
        this.stateService = stateService;
        this.dataService = dataService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("apiStatus", stateService.getApiStatus());
        health.put("dbStatus", stateService.getDbStatus());
        health.put("onlineDevices", stateService.getOnlineDevices());
        health.put("offlineDevices", stateService.getOfflineDevices());
        health.put("deviceList", stateService.getDeviceList());
        health.put("lastSyncStatus", stateService.getLastSyncStatus());
        
        LocalDateTime lastSyncTime = stateService.getLastSyncTime();
        health.put("lastSyncTime", lastSyncTime != null ? lastSyncTime.toString() : "Never");
        
        return ResponseEntity.ok(health);
    }

    @GetMapping("/logs")
    public ResponseEntity<Iterable<String>> getLogs() {
        return ResponseEntity.ok(stateService.getRecentLogs());
    }

    @PostMapping("/sync/force")
    public ResponseEntity<Map<String, String>> forceSync(
            @RequestParam("start") String start, 
            @RequestParam("end") String end,
            @RequestParam("username") String username,
            @RequestParam("email") String email) {
        
        // Whitelist validation
        String credentials = username + ":" + email;
        boolean isAuthorized = false;
        
        if (forceSyncWhitelist != null && !forceSyncWhitelist.isEmpty()) {
            String[] allowedUsers = forceSyncWhitelist.split(",");
            for (String allowed : allowedUsers) {
                if (allowed.trim().equalsIgnoreCase(credentials)) {
                    isAuthorized = true;
                    break;
                }
            }
        }
        
        if (!isAuthorized) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Unauthorized: User is not whitelisted for manual sync.");
            return ResponseEntity.status(401).body(response);
        }

        // start and end are expected in 'YYYY-MM-DD' format from the UI date picker
        try {
            // Convert 'YYYY-MM-DD' to ISO-8601 with offset
            ZonedDateTime startDateTime = java.time.LocalDate.parse(start).atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime endDateTime = java.time.LocalDate.parse(end).atTime(23, 59, 59).atZone(ZoneId.systemDefault());

            String beginTimeIso = startDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endTimeIso = endDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            // Run asynchronously to not block the UI
            CompletableFuture.runAsync(() -> {
                dataService.syncRawEvents(beginTimeIso, endTimeIso);
            });

            Map<String, String> response = new HashMap<>();
            response.put("message", "Sync initiated for " + start + " to " + end);
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Invalid date format. Expected YYYY-MM-DD.");
            return ResponseEntity.badRequest().body(response);
        }
    }
}

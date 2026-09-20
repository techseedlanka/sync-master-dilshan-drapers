package com.dilshandrapers.syncmaster.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class SyncStateService {
    
    private LocalDateTime lastSyncTime;
    private String lastSyncStatus = "UNKNOWN";
    private String apiStatus = "UNKNOWN";
    private String dbStatus = "OK";
    private int onlineDevices = 0;
    private int offlineDevices = 0;
    private List<Map<String, Object>> deviceList = new ArrayList<>();

    // Keep only last 50 logs
    private final LinkedList<String> recentLogs = new LinkedList<>();

    public void addLog(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        recentLogs.addFirst("[" + timestamp + "] " + message);
        if (recentLogs.size() > 50) {
            recentLogs.removeLast();
        }
    }

    public List<String> getRecentLogs() {
        return new ArrayList<>(recentLogs);
    }

    // Getters and Setters

    public String getApiStatus() {
        return apiStatus;
    }

    public void setApiStatus(String apiStatus) {
        this.apiStatus = apiStatus;
    }

    public String getDbStatus() {
        return dbStatus;
    }

    public void setDbStatus(String dbStatus) {
        this.dbStatus = dbStatus;
    }

    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(LocalDateTime lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public String getLastSyncStatus() {
        return lastSyncStatus;
    }

    public void setLastSyncStatus(String lastSyncStatus) {
        this.lastSyncStatus = lastSyncStatus;
    }

    public int getOnlineDevices() {
        return onlineDevices;
    }

    public void setOnlineDevices(int onlineDevices) {
        this.onlineDevices = onlineDevices;
    }

    public int getOfflineDevices() {
        return offlineDevices;
    }

    public void setOfflineDevices(int offlineDevices) {
        this.offlineDevices = offlineDevices;
    }

    public List<Map<String, Object>> getDeviceList() {
        return deviceList;
    }

    public void setDeviceList(List<Map<String, Object>> deviceList) {
        this.deviceList = deviceList;
    }
}

package com.dilshandrapers.syncmaster.service;

import com.dilshandrapers.syncmaster.config.HikConnectProperties;
import com.dilshandrapers.syncmaster.model.AttendanceLog;
import com.dilshandrapers.syncmaster.repository.AttendanceLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HikConnectDataService {

    private static final Logger logger = LoggerFactory.getLogger(HikConnectDataService.class);

    private final HikConnectProperties properties;
    private final RestClient restClient;
    private final HikConnectAuthService authService;
    private final AttendanceLogRepository repository;
    private final SyncStateService stateService;

    public HikConnectDataService(HikConnectProperties properties, RestClient hikConnectRestClient, HikConnectAuthService authService,
            AttendanceLogRepository repository, SyncStateService stateService) {
        this.properties = properties;
        this.restClient = hikConnectRestClient;
        this.authService = authService;
        this.repository = repository;
        this.stateService = stateService;
    }

    public void syncRawEvents(String beginTimeIso, String endTimeIso) {
        stateService.addLog("Starting raw access events sync for period: " + beginTimeIso + " to " + endTimeIso);
        stateService.setLastSyncStatus("IN_PROGRESS");

        int totalInsertedAllPortals = 0;
        boolean anyFailed = false;

        for (HikConnectProperties.PortalConfig portal : properties.getPortals()) {
            if (portal.getAppKey() == null || portal.getAppKey().isEmpty() || portal.getAppKey().contains("YOUR_")) {
                continue; // skip unconfigured portals
            }
            
            try {
                String token = authService.getAccessToken(portal);
                String url = properties.getApiBaseUrl() + "/api/hccgw/acs/v1/event/certificaterecords/search";

                int pageIndex = 1;
                int totalInserted = 0;
                boolean hasMore = true;

                while (hasMore) {
                    Map<String, Object> searchCriteria = new HashMap<>();
                    searchCriteria.put("beginTime", beginTimeIso);
                    searchCriteria.put("endTime", endTimeIso);
                    
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("pageIndex", pageIndex);
                    payload.put("pageSize", 50);
                    payload.put("searchCriteria", searchCriteria);

                    JsonNode response = restClient.post()
                            .uri(url)
                            .header("Token", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(payload)
                            .retrieve()
                            .body(JsonNode.class);

                    if (response != null && response.has("data")) {
                        JsonNode dataNode = response.get("data");
                        
                        JsonNode records = null;
                        if (dataNode.has("list")) {
                            records = dataNode.get("list");
                        } else if (dataNode.has("recordList")) {
                            records = dataNode.get("recordList");
                        }

                        if (records != null && records.isArray()) {
                            boolean loggedSample = false;
                            for (JsonNode record : records) {
                                try {
                                    if (!loggedSample) {
                                        logger.info("Sample raw record from API for portal {}: {}", portal.getName(), record.toString());
                                        stateService.addLog("Sample raw record: " + record.toString());
                                        loggedSample = true;
                                    }
                                    
                                    AttendanceLog log = mapToAttendanceLog(record, portal);
                                    if (log.getEmpId() != null && !log.getEmpId().isEmpty()) {
                                        int inserted = repository.insertIgnore(log);
                                        if (inserted > 0) {
                                            totalInserted++;
                                            totalInsertedAllPortals++;
                                        }
                                    }
                                } catch (Exception ex) {
                                    logger.error("Error processing record for portal {}: {}", portal.getName(), record, ex);
                                }
                            }

                            int total = dataNode.has("total") ? dataNode.get("total").asInt() : 
                                       (dataNode.has("totalNum") ? dataNode.get("totalNum").asInt() : 0);
                            
                            if (pageIndex * 50 >= total || records.isEmpty()) {
                                hasMore = false;
                            } else {
                                pageIndex++;
                            }
                        } else {
                            hasMore = false;
                        }
                    } else {
                        hasMore = false;
                        if (pageIndex == 1) {
                            anyFailed = true;
                        }
                    }
                }
                
                String successMsg = "Portal [" + portal.getName() + "]: Successfully processed/inserted " + totalInserted + " records.";
                stateService.addLog(successMsg);
                logger.info(successMsg);
                
            } catch (Exception e) {
                String errorMsg = "Portal [" + portal.getName() + "]: Exception during sync: " + e.getMessage();
                logger.error(errorMsg, e);
                stateService.addLog(errorMsg);
                anyFailed = true;
            }
        }

        if (anyFailed) {
            stateService.setLastSyncStatus("PARTIAL_SUCCESS");
        } else {
            stateService.setLastSyncStatus("SUCCESS");
        }
        
        stateService.addLog("Completed total sync. Inserted " + totalInsertedAllPortals + " overall records.");
        stateService.setLastSyncTime(LocalDateTime.now());
    }

    public void syncDeviceHealth() {
        int totalOnline = 0;
        int totalOffline = 0;
        List<Map<String, Object>> allDeviceDetails = new ArrayList<>();
        boolean anyFailed = false;

        for (HikConnectProperties.PortalConfig portal : properties.getPortals()) {
            if (portal.getAppKey() == null || portal.getAppKey().isEmpty() || portal.getAppKey().contains("YOUR_")) {
                continue;
            }
            
            try {
                String token = authService.getAccessToken(portal);
                String url = properties.getApiBaseUrl() + "/api/hccgw/resource/v1/devices/get";

                Map<String, Object> payload = new HashMap<>();
                payload.put("pageIndex", 1);
                payload.put("pageSize", 50);

                JsonNode response = restClient.post()
                        .uri(url)
                        .header("Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(JsonNode.class);

                if (response != null && response.has("data") && response.get("data").has("device")) {
                    JsonNode records = response.get("data").get("device");
                    
                    if (records.isArray()) {
                        for (JsonNode record : records) {
                            String name = record.has("name") ? record.get("name").asText() : "Unknown Device";
                            String serial = record.has("serialNo") ? record.get("serialNo").asText() : "";
                            String category = record.has("category") ? record.get("category").asText() : "";
                            String addTime = record.has("addTime") ? record.get("addTime").asText() : "";
                            int onlineStatus = record.has("onlineStatus") ? record.get("onlineStatus").asInt() : 0;
                            
                            String mappedLocation = properties.getDeviceLocations() != null ? properties.getDeviceLocations().get(serial) : null;
                            String finalDeviceName = mappedLocation != null ? mappedLocation : name;
                            
                            Map<String, Object> dev = new HashMap<>();
                            // Prepend portal name to distinguish
                            dev.put("name", "[" + portal.getName() + "] " + finalDeviceName);
                            dev.put("serial", serial);
                            dev.put("category", category);
                            dev.put("addTime", addTime);
                            dev.put("status", onlineStatus == 1 ? "ONLINE" : "OFFLINE");
                            
                            allDeviceDetails.add(dev);

                            if (onlineStatus == 1) {
                                totalOnline++;
                            } else {
                                totalOffline++;
                            }
                        }
                    }
                } else {
                    anyFailed = true;
                }
            } catch (Exception e) {
                logger.error("Exception during device health sync for portal {}: {}", portal.getName(), e.getMessage());
                stateService.addLog("Portal [" + portal.getName() + "] Device Fetch Error: " + e.getMessage());
                anyFailed = true;
            }
        }

        stateService.setOnlineDevices(totalOnline);
        stateService.setOfflineDevices(totalOffline);
        stateService.setDeviceList(allDeviceDetails);
        stateService.setApiStatus(anyFailed ? (allDeviceDetails.isEmpty() ? "OFFLINE" : "DEGRADED") : "ONLINE");
    }

    private AttendanceLog mapToAttendanceLog(JsonNode record, HikConnectProperties.PortalConfig portal) {
        AttendanceLog log = new AttendanceLog();

        String empId = null;
        String personName = null;

        if (record.has("personInfo") && record.get("personInfo").has("baseInfo")) {
            JsonNode baseInfo = record.get("personInfo").get("baseInfo");
            
            if (baseInfo.has("personCode")) {
                empId = baseInfo.get("personCode").asText();
            } else if (baseInfo.has("personNo")) {
                empId = baseInfo.get("personNo").asText();
            }

            String firstName = baseInfo.has("firstName") ? baseInfo.get("firstName").asText() : "";
            String lastName = baseInfo.has("lastName") ? baseInfo.get("lastName").asText() : "";
            personName = (firstName + " " + lastName).trim();
        }
        
        log.setEmpId(empId);
        log.setPerson(personName);

        if (record.has("deviceTime") && !record.get("deviceTime").asText().isEmpty()) {
            String timeStr = record.get("deviceTime").asText();
            OffsetDateTime odt = OffsetDateTime.parse(timeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            java.time.ZonedDateTime zdt = odt.atZoneSameInstant(java.time.ZoneId.systemDefault());
            LocalDateTime ldt = zdt.toLocalDateTime();
            log.setAuthDateAndTime(ldt);
            log.setAuthDate(ldt.toLocalDate());
            log.setAuthTime(ldt.toLocalTime());
        } else if (record.has("occurTime") && !record.get("occurTime").asText().isEmpty()) {
            String timeStr = record.get("occurTime").asText();
            OffsetDateTime odt = OffsetDateTime.parse(timeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            java.time.ZonedDateTime zdt = odt.atZoneSameInstant(java.time.ZoneId.systemDefault());
            LocalDateTime ldt = zdt.toLocalDateTime();
            log.setAuthDateAndTime(ldt);
            log.setAuthDate(ldt.toLocalDate());
            log.setAuthTime(ldt.toLocalTime());
        }

        String deviceName = record.has("deviceName") ? record.get("deviceName").asText() : null;
        
        String trueSerial = null;
        if (record.has("serialNo") && !record.get("serialNo").asText().isEmpty()) {
            trueSerial = record.get("serialNo").asText();
        } else if (record.has("deviceSerial") && !record.get("deviceSerial").asText().isEmpty()) {
            trueSerial = record.get("deviceSerial").asText();
        } else if (record.has("deviceCode") && !record.get("deviceCode").asText().isEmpty()) {
            trueSerial = record.get("deviceCode").asText();
        } else if (record.has("cardReaderName") && !record.get("cardReaderName").asText().isEmpty()) {
            String crName = record.get("cardReaderName").asText();
            if (crName.contains("-")) {
                trueSerial = crName.split("-")[0];
            } else {
                trueSerial = extractSerial(deviceName);
            }
        } else {
            trueSerial = extractSerial(deviceName);
        }
        
        log.setSerial(trueSerial);
        
        // Use hardcoded location name from properties if available, otherwise fallback to API device name
        String mappedLocation = properties.getDeviceLocations() != null ? properties.getDeviceLocations().get(trueSerial) : null;
        log.setDevice(mappedLocation != null ? mappedLocation : deviceName);
        
        log.setCard(record.has("cardNumber") ? record.get("cardNumber").asText() : (record.has("cardNo") ? record.get("cardNo").asText() : null));
        
        if (record.has("direction")) {
            log.setDirection(record.get("direction").asText());
        }

        return log;
    }

    private static final java.util.regex.Pattern SERIAL_PATTERN = java.util.regex.Pattern.compile("\\((.*?)\\)");

    private String extractSerial(String deviceName) {
        if (deviceName == null || deviceName.isEmpty())
            return null;
        java.util.regex.Matcher m = SERIAL_PATTERN.matcher(deviceName);
        if (m.find()) {
            return m.group(1);
        }
        return deviceName;
    }
}
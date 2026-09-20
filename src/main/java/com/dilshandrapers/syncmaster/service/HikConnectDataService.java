package com.dilshandrapers.syncmaster.service;

import com.dilshandrapers.syncmaster.model.AttendanceLog;
import com.dilshandrapers.syncmaster.repository.AttendanceLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${hikconnect.api.baseUrl}")
    private String baseUrl;

    private final RestClient restClient;
    private final HikConnectAuthService authService;
    private final AttendanceLogRepository repository;
    private final SyncStateService stateService;

    public HikConnectDataService(RestClient hikConnectRestClient, HikConnectAuthService authService,
            AttendanceLogRepository repository, SyncStateService stateService) {
        this.restClient = hikConnectRestClient;
        this.authService = authService;
        this.repository = repository;
        this.stateService = stateService;
    }

    public void syncRawEvents(String beginTimeIso, String endTimeIso) {
        stateService.addLog("Starting raw access events sync for period: " + beginTimeIso + " to " + endTimeIso);
        stateService.setLastSyncStatus("IN_PROGRESS");

        try {
            String token = authService.getAccessToken();
            String url = baseUrl + "/api/hccgw/acs/v1/event/certificaterecords/search";

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
                        for (JsonNode record : records) {
                            try {
                                if (logger.isDebugEnabled() && totalInserted == 0 && pageIndex == 1) {
                                    stateService.addLog("Sample raw record from API: " + record.toString());
                                }
                                
                                AttendanceLog log = mapToAttendanceLog(record);
                                if (log.getEmpId() != null && !log.getEmpId().isEmpty()) {
                                    int inserted = repository.insertIgnore(log);
                                    if (inserted > 0) {
                                        totalInserted++;
                                    }
                                } else {
                                    // Log if we skip due to missing EmpId
                                    if (logger.isDebugEnabled()) {
                                        logger.debug("Skipped record missing EmpId: {}", record.toString());
                                    }
                                }
                            } catch (Exception ex) {
                                logger.error("Error processing record: {}", record, ex);
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
                        if (logger.isDebugEnabled()) {
                            stateService.addLog("API response missing 'list' or 'recordList' on page " + pageIndex + ". Response: " + response.toString());
                        }
                        hasMore = false;
                    }
                } else {
                    if (logger.isDebugEnabled()) {
                        stateService.addLog("API response missing data on page " + pageIndex + ". Response: " + (response != null ? response.toString() : "null"));
                    } else {
                        String msg = response != null && response.has("msg") ? response.get("msg").asText() : "Unknown API Error";
                        stateService.addLog("API response missing data on page " + pageIndex + ". Msg: " + msg);
                    }
                    hasMore = false;
                    if (pageIndex == 1) {
                        stateService.setLastSyncStatus("FAILED");
                        return;
                    }
                }
            }

            String successMsg = "Successfully processed/inserted " + totalInserted + " records across " + pageIndex + " pages.";
            stateService.addLog(successMsg);
            logger.info(successMsg);

            stateService.setLastSyncStatus("SUCCESS");
            stateService.setLastSyncTime(LocalDateTime.now());
            stateService.setApiStatus("ONLINE");
        } catch (Exception e) {
            String errorMsg = "Exception during sync: " + e.getMessage();
            logger.error(errorMsg, e);
            stateService.addLog(errorMsg);
            stateService.setLastSyncStatus("FAILED");
            stateService.setApiStatus("OFFLINE");
            throw new RuntimeException("Sync failed", e);
        }
    }

    public void syncDeviceHealth() {
        try {
            String token = authService.getAccessToken();
            String url = baseUrl + "/api/hccgw/resource/v1/devices/get";

            Map<String, Object> payload = new HashMap<>();
            payload.put("pageIndex", 1);
            payload.put("pageSize", 50); // Fetch up to 50 devices at once, 500 might be too high

            JsonNode response = restClient.post()
                    .uri(url)
                    .header("Token", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            
            logger.info("Device Health API Response: {}", response.toString());

            if (response != null && response.has("data") && response.get("data").has("device")) {
                JsonNode records = response.get("data").get("device");
                
                int online = 0;
                int offline = 0;
                List<Map<String, Object>> deviceDetailsList = new ArrayList<>();
                
                if (records.isArray()) {
                    for (JsonNode record : records) {
                        String name = record.has("name") ? record.get("name").asText() : "Unknown Device";
                        String serial = record.has("serialNo") ? record.get("serialNo").asText() : "";
                        String category = record.has("category") ? record.get("category").asText() : "";
                        String addTime = record.has("addTime") ? record.get("addTime").asText() : "";
                        int onlineStatus = record.has("onlineStatus") ? record.get("onlineStatus").asInt() : 0;
                        
                        Map<String, Object> dev = new HashMap<>();
                        dev.put("name", name);
                        dev.put("serial", serial);
                        dev.put("category", category);
                        dev.put("addTime", addTime);
                        dev.put("status", onlineStatus == 1 ? "ONLINE" : "OFFLINE");
                        
                        deviceDetailsList.add(dev);

                        if (onlineStatus == 1) {
                            online++;
                        } else {
                            offline++;
                        }
                    }
                }
                
                stateService.setOnlineDevices(online);
                stateService.setOfflineDevices(offline);
                stateService.setDeviceList(deviceDetailsList);
                stateService.setApiStatus("ONLINE");
            } else {
                String rawResp = response != null ? response.toString() : "null";
                logger.warn("Failed to fetch device health or no 'device' in data. Response: {}", rawResp);
                stateService.addLog("Device Fetch Response: " + rawResp);
                stateService.setApiStatus("ERROR");
            }
        } catch (Exception e) {
            logger.error("Exception during device health sync: {}", e.getMessage());
            stateService.addLog("Device Fetch Error: " + e.getMessage());
            stateService.setApiStatus("OFFLINE");
        }
    }

    private AttendanceLog mapToAttendanceLog(JsonNode record) {
        AttendanceLog log = new AttendanceLog();

        String empId = null;
        String personName = null;

        // Hik-Connect OpenAPI wraps employee details in personInfo.baseInfo
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
        log.setDevice(deviceName);
        log.setSerial(extractSerial(deviceName));
        
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
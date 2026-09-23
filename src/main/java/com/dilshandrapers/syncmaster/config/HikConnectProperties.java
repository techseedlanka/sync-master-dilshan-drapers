package com.dilshandrapers.syncmaster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Configuration
@ConfigurationProperties(prefix = "hikconnect")
public class HikConnectProperties {

    private String apiBaseUrl;
    private List<PortalConfig> portals = new ArrayList<>();
    private Map<String, String> deviceLocations = new HashMap<>();

    public Map<String, String> getDeviceLocations() {
        return deviceLocations;
    }

    public void setDeviceLocations(Map<String, String> deviceLocations) {
        this.deviceLocations = deviceLocations;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public List<PortalConfig> getPortals() {
        return portals;
    }

    public void setPortals(List<PortalConfig> portals) {
        this.portals = portals;
    }

    public static class PortalConfig {
        private String name;
        private String appKey;
        private String secretKey;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }
}

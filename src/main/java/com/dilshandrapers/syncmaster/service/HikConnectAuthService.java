package com.dilshandrapers.syncmaster.service;

import com.dilshandrapers.syncmaster.config.HikConnectProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HikConnectAuthService {

    private static final Logger logger = LoggerFactory.getLogger(HikConnectAuthService.class);

    private final HikConnectProperties properties;
    private final RestClient restClient;

    private final Map<String, String> cachedTokens = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> tokenExpiries = new ConcurrentHashMap<>();

    public HikConnectAuthService(HikConnectProperties properties, RestClient hikConnectRestClient) {
        this.properties = properties;
        this.restClient = hikConnectRestClient;
    }

    public synchronized String getAccessToken(HikConnectProperties.PortalConfig portal) {
        String appKey = portal.getAppKey();
        
        if (cachedTokens.containsKey(appKey) && tokenExpiries.containsKey(appKey) 
                && LocalDateTime.now().isBefore(tokenExpiries.get(appKey))) {
            return cachedTokens.get(appKey);
        }
        
        logger.info("Fetching new access token for portal: {}", portal.getName());
        return fetchNewToken(portal);
    }

    private String fetchNewToken(HikConnectProperties.PortalConfig portal) {
        String url = properties.getApiBaseUrl() + "/api/hccgw/platform/v1/token/get";
        
        try {
            JsonNode response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("appKey", portal.getAppKey(), "secretKey", portal.getSecretKey()))
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null && response.has("data") && response.get("data").has("accessToken")) {
                String token = response.get("data").get("accessToken").asText();
                cachedTokens.put(portal.getAppKey(), token);
                // Token is valid for 7 days. We cache it for 6 days to be safe.
                tokenExpiries.put(portal.getAppKey(), LocalDateTime.now().plusDays(6));
                
                logger.info("Successfully fetched and cached new access token for portal: {}", portal.getName());
                return token;
            } else {
                logger.error("Failed to extract access token from response for portal {}: {}", portal.getName(), response);
                throw new RuntimeException("Invalid response format from Hik-Connect auth API for portal " + portal.getName());
            }
        } catch (Exception e) {
            logger.error("Exception while fetching access token for portal " + portal.getName(), e);
            throw new RuntimeException("Failed to fetch access token for portal " + portal.getName(), e);
        }
    }
}

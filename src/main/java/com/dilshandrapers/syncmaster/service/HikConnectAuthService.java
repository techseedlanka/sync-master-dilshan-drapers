package com.dilshandrapers.syncmaster.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class HikConnectAuthService {

    private static final Logger logger = LoggerFactory.getLogger(HikConnectAuthService.class);

    @Value("${hikconnect.api.baseUrl}")
    private String baseUrl;

    @Value("${hikconnect.api.appKey}")
    private String appKey;

    @Value("${hikconnect.api.secretKey}")
    private String secretKey;

    private final RestClient restClient;

    private String cachedToken = null;
    private LocalDateTime tokenExpiry = null;

    public HikConnectAuthService(RestClient hikConnectRestClient) {
        this.restClient = hikConnectRestClient;
    }

    public synchronized String getAccessToken() {
        if (cachedToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        
        logger.info("Fetching new access token from Hik-Connect API");
        return fetchNewToken();
    }

    private String fetchNewToken() {
        String url = baseUrl + "/api/hccgw/platform/v1/token/get";
        
        try {
            JsonNode response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("appKey", appKey, "secretKey", secretKey))
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null && response.has("data") && response.get("data").has("accessToken")) {
                this.cachedToken = response.get("data").get("accessToken").asText();
                // Token is valid for 7 days. We cache it for 6 days to be safe.
                this.tokenExpiry = LocalDateTime.now().plusDays(6);
                logger.info("Successfully fetched and cached new access token");
                return this.cachedToken;
            } else {
                logger.error("Failed to extract access token from response: {}", response);
                throw new RuntimeException("Invalid response format from Hik-Connect auth API");
            }
        } catch (Exception e) {
            logger.error("Exception while fetching access token", e);
            throw new RuntimeException("Failed to fetch access token", e);
        }
    }
}

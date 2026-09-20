package com.dilshandrapers.syncmaster.scheduler;

import com.dilshandrapers.syncmaster.service.HikConnectDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class SyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SyncScheduler.class);

    private final HikConnectDataService dataService;

    public SyncScheduler(HikConnectDataService dataService) {
        this.dataService = dataService;
    }

    // Run every night at 2:00 AM
    @Scheduled(cron = "0 0 2 * * ?")
    public void performDailySync() {
        logger.info("Starting scheduled daily sync for 'Yesterday'...");
        
        // Calculate "Yesterday" range
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        // Convert to ISO-8601 with offset
        // Assuming system default timezone for the API request
        ZonedDateTime startOfDay = yesterday.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime endOfDay = yesterday.atTime(23, 59, 59).atZone(ZoneId.systemDefault());

        String beginTimeIso = startOfDay.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String endTimeIso = endOfDay.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        dataService.syncRawEvents(beginTimeIso, endTimeIso);
        
        logger.info("Scheduled daily sync completed.");
    }

    // Run every 5 minutes to ping device health
    @Scheduled(fixedRate = 300000)
    public void pingDeviceHealth() {
        logger.info("Pinging Hik-Connect device health...");
        dataService.syncDeviceHealth();
    }
}

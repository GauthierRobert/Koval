package com.koval.trainingplannerbackend.club.recurring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringSessionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringSessionScheduler.class);

    private final RecurringSessionService recurringSessionService;

    public RecurringSessionScheduler(RecurringSessionService recurringSessionService) {
        this.recurringSessionService = recurringSessionService;
    }

    @Scheduled(cron = "0 0 2 * * MON")
    public void generateWeeklyRecurringSessions() {
        log.info("Running weekly recurring session generation...");
        recurringSessionService.generateAllRecurring();
        log.info("Weekly recurring session generation complete.");
    }
}

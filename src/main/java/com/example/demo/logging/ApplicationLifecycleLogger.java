package com.example.demo.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLifecycleLogger {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleLogger.class);

    private final JdbcTemplate jdbcTemplate;

    public ApplicationLifecycleLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application started and ready to serve requests.");
        verifyDatabaseConnection();
    }

    private void verifyDatabaseConnection() {
        try {
            Integer probe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            log.info("Database connection verified successfully. Probe result={}", probe);
        } catch (Exception ex) {
            log.error("Database connection check failed.", ex);
        }
    }
}

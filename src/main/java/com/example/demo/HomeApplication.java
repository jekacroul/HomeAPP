package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class HomeApplication extends SpringBootServletInitializer {

    private static final Logger log = LoggerFactory.getLogger(HomeApplication.class);

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        log.info("Configuring HomeApplication as WAR deployment.");
        return application.sources(HomeApplication.class);
    }

    public static void main(String[] args) {
        log.info("Starting HomeApplication...");
        SpringApplication.run(HomeApplication.class, args);
    }
}

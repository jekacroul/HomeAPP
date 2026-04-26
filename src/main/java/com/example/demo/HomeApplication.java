package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.util.StringUtils;

import java.net.URI;

@SpringBootApplication
public class HomeApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        normalizeDatasourceFromEnvironment();
        return application.sources(HomeApplication.class);
    }

    public static void main(String[] args) {
        normalizeDatasourceFromEnvironment();
        SpringApplication.run(HomeApplication.class, args);
    }

    private static void normalizeDatasourceFromEnvironment() {
        String dbUrl = System.getenv("DB_URL");
        if (!StringUtils.hasText(dbUrl)) {
            return;
        }

        String jdbcUrl = dbUrl;
        if (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) {
            URI uri = URI.create(dbUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            if (!StringUtils.hasText(host) || !StringUtils.hasText(path)) {
                return;
            }

            StringBuilder value = new StringBuilder("jdbc:postgresql://").append(host);
            if (uri.getPort() > 0) {
                value.append(":").append(uri.getPort());
            }
            value.append(path);
            if (StringUtils.hasText(uri.getQuery())) {
                value.append("?").append(uri.getQuery());
            }
            jdbcUrl = value.toString();

            String userInfo = uri.getUserInfo();
            if (StringUtils.hasText(userInfo)) {
                String[] parts = userInfo.split(":", 2);
                if (!StringUtils.hasText(System.getenv("DB_USERNAME")) && parts.length > 0) {
                    System.setProperty("spring.datasource.username", parts[0]);
                }
                if (!StringUtils.hasText(System.getenv("DB_PASSWORD")) && parts.length > 1) {
                    System.setProperty("spring.datasource.password", parts[1]);
                }
            }
        }

        System.setProperty("spring.datasource.url", jdbcUrl);

        if (StringUtils.hasText(System.getenv("DB_USERNAME"))) {
            System.setProperty("spring.datasource.username", System.getenv("DB_USERNAME"));
        }
        if (StringUtils.hasText(System.getenv("DB_PASSWORD"))) {
            System.setProperty("spring.datasource.password", System.getenv("DB_PASSWORD"));
        }
    }
}

package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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
        String rawDbUrl = firstNonBlank(System.getenv("DB_URL"), System.getenv("DATABASE_URL"), System.getenv("SPRING_DATASOURCE_URL"));
        if (!StringUtils.hasText(rawDbUrl)) {
            return;
        }

        String dbUrl = stripWrappingQuotes(rawDbUrl.trim());
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
                if (!StringUtils.hasText(firstNonBlank(System.getenv("DB_USERNAME"), System.getenv("SPRING_DATASOURCE_USERNAME"))) && parts.length > 0) {
                    System.setProperty("spring.datasource.username", URLDecoder.decode(parts[0], StandardCharsets.UTF_8));
                }
                if (!StringUtils.hasText(firstNonBlank(System.getenv("DB_PASSWORD"), System.getenv("SPRING_DATASOURCE_PASSWORD"))) && parts.length > 1) {
                    System.setProperty("spring.datasource.password", URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                }
            }
        }

        System.setProperty("DB_URL", jdbcUrl);
        System.setProperty("DATABASE_URL", jdbcUrl);
        System.setProperty("SPRING_DATASOURCE_URL", jdbcUrl);
        System.setProperty("spring.datasource.url", jdbcUrl);

        String username = firstNonBlank(System.getenv("DB_USERNAME"), System.getenv("SPRING_DATASOURCE_USERNAME"));
        String password = firstNonBlank(System.getenv("DB_PASSWORD"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        if (StringUtils.hasText(username)) {
            System.setProperty("spring.datasource.username", stripWrappingQuotes(username.trim()));
        }
        if (StringUtils.hasText(password)) {
            System.setProperty("spring.datasource.password", stripWrappingQuotes(password.trim()));
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String stripWrappingQuotes(String value) {
        if (!StringUtils.hasText(value) || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}

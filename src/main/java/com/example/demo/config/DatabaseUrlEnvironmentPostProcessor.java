package com.example.demo.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Supports non-JDBC DB_URL values from PaaS providers (e.g. postgres://...)
 * by converting them into JDBC URL + credentials before datasource binding.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "dbUrlNormalization";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dbUrl = environment.getProperty("DB_URL");
        if (!StringUtils.hasText(dbUrl) || dbUrl.startsWith("jdbc:")) {
            return;
        }

        if (!dbUrl.startsWith("postgres://") && !dbUrl.startsWith("postgresql://")) {
            return;
        }

        URI uri = URI.create(dbUrl);
        String host = uri.getHost();
        String path = uri.getPath();
        if (!StringUtils.hasText(host) || !StringUtils.hasText(path)) {
            return;
        }

        int port = uri.getPort();
        String query = uri.getQuery();

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(host);
        if (port > 0) {
            jdbcUrl.append(":").append(port);
        }
        jdbcUrl.append(path);
        if (StringUtils.hasText(query)) {
            jdbcUrl.append("?").append(query);
        }

        Map<String, Object> normalized = new HashMap<>();
        normalized.put("spring.datasource.url", jdbcUrl.toString());

        String explicitUser = environment.getProperty("DB_USERNAME");
        String explicitPassword = environment.getProperty("DB_PASSWORD");

        if (!StringUtils.hasText(explicitUser) || !StringUtils.hasText(explicitPassword)) {
            String userInfo = uri.getUserInfo();
            if (StringUtils.hasText(userInfo)) {
                String[] parts = userInfo.split(":", 2);
                if (!StringUtils.hasText(explicitUser) && parts.length > 0) {
                    normalized.put("spring.datasource.username", parts[0]);
                }
                if (!StringUtils.hasText(explicitPassword) && parts.length > 1) {
                    normalized.put("spring.datasource.password", parts[1]);
                }
            }
        }

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, normalized));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

package com.example.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class TelegramFeedbackService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${feedback.telegram.bot-url:}")
    private String botUrl;

    public TelegramFeedbackService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public boolean isConfigured() {
        return botUrl != null && !botUrl.isBlank();
    }

    public boolean sendFeedback(String name, String email, String message, List<String> screenshotUrls) {
        if (!isConfigured()) {
            return false;
        }

        Map<String, Object> payload = Map.of(
            "name", name,
            "email", email,
            "message", message,
            "screenshots", screenshotUrls
        );

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return false;
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(botUrl))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}

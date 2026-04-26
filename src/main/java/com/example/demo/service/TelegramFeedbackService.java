package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TelegramFeedbackService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${feedback.telegram.bot-url:}")
    private String botUrl;

    @Value("${feedback.telegram.chat-id:0}")
    private Long chatId;

    public TelegramFeedbackService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isConfigured() {
        return botUrl != null && !botUrl.isBlank() && chatId != null && chatId > 0;
    }

    public boolean sendFeedback(String name, String email, String message, List<String> screenshotUrls) {
        if (!isConfigured()) {
            return false;
        }

        long nextTaskId = resolveNextTaskId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", nextTaskId);
        payload.put("description", buildDescription(name, email, message, screenshotUrls));
        payload.put("chatId", chatId);
        payload.put("completed", false);

        final String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
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

    private long resolveNextTaskId() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(botUrl))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return 1;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode tasks = root.path("_embedded").path("tasks");
            if (!tasks.isArray()) {
                return 1;
            }

            long maxTaskId = 0;
            for (JsonNode task : tasks) {
                long currentId = task.path("taskId").asLong(0);
                if (currentId > maxTaskId) {
                    maxTaskId = currentId;
                }
            }

            return maxTaskId + 1;
        } catch (IOException e) {
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        }
    }

    private String buildDescription(String name, String email, String message, List<String> screenshotUrls) {
        StringBuilder builder = new StringBuilder();
        builder.append("Новый отзыв\n");
        builder.append("Имя: ").append(name == null ? "" : name).append("\n");
        builder.append("Email: ").append(email == null ? "" : email).append("\n");
        builder.append("Сообщение: ").append(message == null ? "" : message);

        if (screenshotUrls != null && !screenshotUrls.isEmpty()) {
            builder.append("\nСкриншоты:\n");
            for (String url : screenshotUrls) {
                if (url != null && !url.isBlank()) {
                    builder.append("- ").append(url).append("\n");
                }
            }
        }

        return builder.toString().trim();
    }
}

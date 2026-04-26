package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class TelegramFeedbackService {

    private final HttpClient httpClient;

    @Value("${feedback.telegram.bot-url:}")
    private String botUrl;

    @Value("${feedback.telegram.chat-id:0}")
    private Long chatId;

    public TelegramFeedbackService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public boolean isConfigured() {
        return botUrl != null && !botUrl.isBlank() && chatId != null && chatId > 0;
    }

    public boolean sendFeedback(String name, String email, String message, List<String> screenshotUrls) {
        if (!isConfigured()) {
            return false;
        }

        String description = buildDescription(name, email, message, screenshotUrls);
        String jsonBody = "{"
            + "\"description\":\"" + escapeJson(description) + "\","
            + "\"chatId\":" + chatId + ","
            + "\"completed\":false"
            + "}";

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

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}

package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class TelegramFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(TelegramFeedbackService.class);

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

        String description = buildDescription(name, email, message, screenshotUrls);
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(new FeedbackTaskPayload(description, chatId, false));
        } catch (JsonProcessingException e) {
            log.warn("Не удалось сериализовать payload для Telegram feedback", e);
            return false;
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(botUrl))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!success) {
                log.warn("Telegram feedback API вернул ошибку: status={}, body={}", response.statusCode(), response.body());
            }
            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            log.warn("Ошибка при отправке feedback в Telegram API", e);
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

    private record FeedbackTaskPayload(String description, Long chatId, boolean completed) {
        public Long chat_id() {
            return chatId;
        }
    }
}

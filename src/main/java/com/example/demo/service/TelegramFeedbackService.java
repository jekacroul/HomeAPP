package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TelegramFeedbackService {

    private final HttpClient httpClient;

    @Value("${feedback.telegram.bot-url:}")
    private String botUrl;

    @Value("${feedback.telegram.bot-token:}")
    private String botToken;

    @Value("${feedback.telegram.chat-id:}")
    private String chatId;

    public TelegramFeedbackService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public boolean isConfigured() {
        return isWebhookConfigured() || isTelegramApiConfigured();
    }

    public boolean sendFeedback(String name, String email, String message, List<String> screenshotUrls) {
        if (!isConfigured()) {
            return false;
        }

        if (isTelegramApiConfigured()) {
            return sendViaTelegramApi(name, email, message, screenshotUrls);
        }

        return sendViaWebhook(name, email, message, screenshotUrls);
    }

    private boolean isWebhookConfigured() {
        return botUrl != null && !botUrl.isBlank();
    }

    private boolean isTelegramApiConfigured() {
        return botToken != null && !botToken.isBlank()
            && chatId != null && !chatId.isBlank();
    }

    private boolean sendViaWebhook(String name, String email, String message, List<String> screenshotUrls) {
        if (!isWebhookConfigured()) {
            return false;
        }

        String screenshotsJson = screenshotUrls.stream()
            .map(this::escapeJson)
            .map(url -> "\"" + url + "\"")
            .collect(Collectors.joining(","));

        String jsonBody = "{"
            + "\"name\":\"" + escapeJson(name) + "\","
            + "\"email\":\"" + escapeJson(email) + "\","
            + "\"message\":\"" + escapeJson(message) + "\","
            + "\"screenshots\":[" + screenshotsJson + "]"
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

    private boolean sendViaTelegramApi(String name, String email, String message, List<String> screenshotUrls) {
        String text = buildTelegramText(name, email, message, screenshotUrls);
        if (!sendMessage(text)) {
            return false;
        }

        for (String screenshotUrl : screenshotUrls) {
            if (!sendDocument(screenshotUrl)) {
                return false;
            }
        }
        return true;
    }

    private String buildTelegramText(String name, String email, String message, List<String> screenshotUrls) {
        String screenshotsInfo = screenshotUrls.isEmpty()
            ? "без скриншотов"
            : "скриншотов: " + screenshotUrls.size();
        return "🆕 Новый фидбек:\n"
            + "👤 Имя: " + name + "\n"
            + "📧 Email: " + email + "\n"
            + "📝 Сообщение: " + message + "\n"
            + "📎 " + screenshotsInfo;
    }

    private boolean sendMessage(String text) {
        String endpoint = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        String body = "chat_id=" + urlEncode(chatId)
            + "&text=" + urlEncode(text);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
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

    private boolean sendDocument(String screenshotUrl) {
        if (screenshotUrl == null || screenshotUrl.isBlank() || !screenshotUrl.startsWith("/")) {
            return true;
        }
        Path filePath = Path.of(screenshotUrl.substring(1));
        if (!Files.exists(filePath)) {
            return false;
        }

        String endpoint = "https://api.telegram.org/bot" + botToken + "/sendDocument";
        String boundary = "----HomeAppBoundary" + UUID.randomUUID();
        byte[] multipartBody;

        try {
            multipartBody = buildMultipartDocumentBody(boundary, filePath);
        } catch (IOException e) {
            return false;
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
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

    private byte[] buildMultipartDocumentBody(String boundary, Path filePath) throws IOException {
        String CRLF = "\r\n";
        byte[] fileBytes = Files.readAllBytes(filePath);
        String fileName = filePath.getFileName().toString();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

        writer.append("--").append(boundary).append(CRLF);
        writer.append("Content-Disposition: form-data; name=\"chat_id\"").append(CRLF);
        writer.append(CRLF);
        writer.append(chatId).append(CRLF);

        writer.append("--").append(boundary).append(CRLF);
        writer.append("Content-Disposition: form-data; name=\"document\"; filename=\"").append(fileName).append("\"").append(CRLF);
        writer.append("Content-Type: application/octet-stream").append(CRLF);
        writer.append(CRLF);
        writer.flush();
        outputStream.write(fileBytes);
        outputStream.write(CRLF.getBytes(StandardCharsets.UTF_8));

        writer.append("--").append(boundary).append("--").append(CRLF);
        writer.flush();
        return outputStream.toByteArray();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

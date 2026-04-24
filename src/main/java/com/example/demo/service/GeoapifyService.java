package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeoapifyService {

    private static final String MINSK_RECT = "27.340,53.745,27.800,54.050";
    private static final String MINSK_BIAS = "proximity:27.5615,53.9023";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${geoapify.api-key}")
    private String apiKey;

    public GeoapifyService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<GeoPlaceSuggestion> searchInMinsk(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            URI uri = UriComponentsBuilder
                .fromUriString("https://api.geoapify.com/v1/geocode/autocomplete")
                .queryParam("text", query + " Минск")
                .queryParam("filter", "rect:" + MINSK_RECT)
                .queryParam("bias", MINSK_BIAS)
                .queryParam("format", "json")
                .queryParam("limit", limit)
                .queryParam("apiKey", apiKey)
                .build(true)
                .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                return List.of();
            }

            List<GeoPlaceSuggestion> suggestions = new ArrayList<>();
            for (JsonNode item : results) {
                double lat = item.path("lat").asDouble(Double.NaN);
                double lon = item.path("lon").asDouble(Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    continue;
                }

                String formatted = item.path("formatted").asText("Неизвестный адрес");
                String name = item.path("name").asText("");
                if (name.isBlank()) {
                    name = formatted;
                }

                suggestions.add(new GeoPlaceSuggestion(name, formatted, lat, lon));
            }

            return suggestions;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public record GeoPlaceSuggestion(String name, String address, double latitude, double longitude) {}
}

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
public class YandexMapsService {

    private static final String DEFAULT_BBOX = "27.438915081299548,53.890216642813314,27.520990677155474,53.84386961984724";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${yandex.maps.api-key}")
    private String apiKey;

    @Value("${yandex.maps.geocode-url:https://geocode-maps.yandex.ru/1.x/}")
    private String geocodeUrl;

    @Value("${yandex.maps.search-limit:100}")
    private int searchLimit;

    public YandexMapsService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<MapPlaceSuggestion> searchInMinsk(String query, int limit, String bbox) {
        try {
            String cleanQuery = query == null ? "" : query.trim();
            if (cleanQuery.isBlank()) {
                return List.of();
            }

            String effectiveBbox = isValidBbox(bbox) ? bbox : DEFAULT_BBOX;
            String yandexBbox = toYandexBbox(effectiveBbox);
            int safeLimit = Math.max(1, Math.min(Math.max(limit, searchLimit), 100));

            URI uri = UriComponentsBuilder.fromUriString(geocodeUrl)
                .queryParam("apikey", apiKey)
                .queryParam("format", "json")
                .queryParam("geocode", "Минск " + cleanQuery)
                .queryParam("bbox", yandexBbox)
                .queryParam("rspn", "1")
                .queryParam("results", safeLimit)
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

            JsonNode members = objectMapper.readTree(response.body())
                .path("response")
                .path("GeoObjectCollection")
                .path("featureMember");

            if (!members.isArray()) {
                return List.of();
            }

            List<MapPlaceSuggestion> suggestions = new ArrayList<>();
            for (JsonNode member : members) {
                JsonNode geoObject = member.path("GeoObject");
                String pos = geoObject.path("Point").path("pos").asText("").trim();
                if (pos.isBlank()) {
                    continue;
                }

                String[] parts = pos.split("\\s+");
                if (parts.length != 2) {
                    continue;
                }

                double lon;
                double lat;
                try {
                    lon = Double.parseDouble(parts[0]);
                    lat = Double.parseDouble(parts[1]);
                } catch (NumberFormatException ex) {
                    continue;
                }

                String name = geoObject.path("name").asText("Без названия").trim();
                String address = geoObject.path("description").asText("Без адреса").trim();

                suggestions.add(new MapPlaceSuggestion(name, address, lat, lon));
            }

            return suggestions;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String toYandexBbox(String bbox) {
        String[] parts = bbox.split(",");
        double west = Double.parseDouble(parts[0].trim());
        double north = Double.parseDouble(parts[1].trim());
        double east = Double.parseDouble(parts[2].trim());
        double south = Double.parseDouble(parts[3].trim());
        return west + "," + south + "~" + east + "," + north;
    }

    private boolean isValidBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return false;
        }
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            return false;
        }
        try {
            for (String part : parts) {
                Double.parseDouble(part.trim());
            }
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public record MapPlaceSuggestion(String name, String address, double latitude, double longitude) {}
}

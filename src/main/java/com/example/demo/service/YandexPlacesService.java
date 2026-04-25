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
public class YandexPlacesService {

    private static final String DEFAULT_BBOX = "27.438915081299548,53.890216642813314,27.520990677155474,53.84386961984724";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${yandex.maps.api-key}")
    private String apiKey;

    @Value("${yandex.maps.search-api-url:https://search-maps.yandex.ru/v1/}")
    private String searchApiUrl;

    @Value("${yandex.maps.search-limit:20}")
    private int searchLimit;

    public YandexPlacesService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<MapPlaceSuggestion> searchPlaces(String query, String bbox) {
        try {
            String cleanedQuery = query == null ? "" : query.trim();
            if (cleanedQuery.isBlank()) {
                return List.of();
            }

            String effectiveBbox = isValidBbox(bbox) ? bbox : DEFAULT_BBOX;
            BoundingBox box = parseBoundingBox(effectiveBbox);

            double centerLon = (box.west + box.east) / 2.0;
            double centerLat = (box.south + box.north) / 2.0;
            double spanLon = Math.max(Math.abs(box.east - box.west), 0.01);
            double spanLat = Math.max(Math.abs(box.north - box.south), 0.01);
            int safeLimit = Math.max(1, Math.min(searchLimit, 50));

            URI uri = UriComponentsBuilder.fromUriString(searchApiUrl)
                .queryParam("apikey", apiKey)
                .queryParam("text", cleanedQuery)
                .queryParam("lang", "ru_RU")
                .queryParam("type", "biz")
                .queryParam("ll", centerLon + "," + centerLat)
                .queryParam("spn", spanLon + "," + spanLat)
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

            JsonNode features = objectMapper.readTree(response.body()).path("features");
            if (!features.isArray()) {
                return List.of();
            }

            List<MapPlaceSuggestion> suggestions = new ArrayList<>();
            for (JsonNode feature : features) {
                JsonNode coordinates = feature.path("geometry").path("coordinates");
                if (!coordinates.isArray() || coordinates.size() < 2) {
                    continue;
                }

                double lon = coordinates.get(0).asDouble(Double.NaN);
                double lat = coordinates.get(1).asDouble(Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    continue;
                }

                JsonNode properties = feature.path("properties");
                JsonNode companyMetaData = properties.path("CompanyMetaData");

                String name = companyMetaData.path("name").asText("").trim();
                if (name.isBlank()) {
                    name = properties.path("name").asText("Без названия").trim();
                }

                String address = companyMetaData.path("address").asText("").trim();
                if (address.isBlank()) {
                    address = properties.path("description").asText("Без адреса").trim();
                }

                suggestions.add(new MapPlaceSuggestion(name, address, lat, lon));
            }

            return suggestions;
        } catch (Exception ignored) {
            return List.of();
        }
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

    private BoundingBox parseBoundingBox(String bbox) {
        String[] parts = bbox.split(",");
        return new BoundingBox(
            Double.parseDouble(parts[0].trim()),
            Double.parseDouble(parts[1].trim()),
            Double.parseDouble(parts[2].trim()),
            Double.parseDouble(parts[3].trim())
        );
    }

    private record BoundingBox(double west, double north, double east, double south) {}

    public record MapPlaceSuggestion(String name, String address, double latitude, double longitude) {}
}

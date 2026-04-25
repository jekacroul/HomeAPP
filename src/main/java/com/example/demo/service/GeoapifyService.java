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
import java.util.Locale;

@Service
public class GeoapifyService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${geoapify.places-api-url}")
    private String placesApiUrl;

    @Value("${geoapify.places-categories}")
    private String categories;

    @Value("${geoapify.default-rect}")
    private String defaultRect;

    @Value("${geoapify.search-limit:100}")
    private int searchLimit;

    @Value("${geoapify.api-key}")
    private String apiKey;

    public GeoapifyService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<GeoPlaceSuggestion> searchInMinsk(String query, int limit, String bbox) {
        try {
            String rect = isValidRect(bbox) ? bbox : defaultRect;
            int safeLimit = Math.max(20, Math.min(Math.max(limit, searchLimit), 500));

            URI uri = UriComponentsBuilder.fromUriString(placesApiUrl)
                .queryParam("categories", categories)
                .queryParam("filter", "rect:" + rect)
                .queryParam("limit", safeLimit)
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
            JsonNode features = root.path("features");
            if (!features.isArray()) {
                return List.of();
            }

            String queryLower = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
            List<GeoPlaceSuggestion> suggestions = new ArrayList<>();

            for (JsonNode feature : features) {
                JsonNode properties = feature.path("properties");
                JsonNode coordinates = feature.path("geometry").path("coordinates");
                if (!coordinates.isArray() || coordinates.size() < 2) {
                    continue;
                }

                double lon = coordinates.get(0).asDouble(Double.NaN);
                double lat = coordinates.get(1).asDouble(Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    continue;
                }

                String name = properties.path("name").asText("").trim();
                String address = properties.path("formatted").asText("").trim();

                if (name.isBlank()) {
                    name = properties.path("address_line1").asText("Без названия");
                }
                if (address.isBlank()) {
                    address = properties.path("address_line2").asText("Без адреса");
                }

                String searchable = (name + " " + address).toLowerCase(Locale.ROOT);
                if (!queryLower.isBlank() && !searchable.contains(queryLower)) {
                    continue;
                }

                suggestions.add(new GeoPlaceSuggestion(name, address, lat, lon));
            }

            return suggestions;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private boolean isValidRect(String bbox) {
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

    public record GeoPlaceSuggestion(String name, String address, double latitude, double longitude) {}
}

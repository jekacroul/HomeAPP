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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class GeoapifyService {

    private static final String DEFAULT_RECT = "27.438915081299548,53.890216642813314,27.520990677155474,53.84386961984724";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${geoapify.places-url:https://api.geoapify.com/v2/places?categories=catering.cafe,catering.restaurant,catering.fast_food,catering.bar&filter=rect:27.438915081299548,53.890216642813314,27.520990677155474,53.84386961984724&limit=50&apiKey=7aa2f88d19d7416988f9da9e123b5729}")
    private String placesUrl;

    @Value("${geoapify.search-limit:50}")
    private int searchLimit;

    public GeoapifyService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<GeoPlaceSuggestion> searchInMinsk(String query, int limit, String bbox) {
        try {
            String rect = isValidRect(bbox) ? bbox : DEFAULT_RECT;
            int safeLimit = Math.max(20, Math.min(Math.max(limit, searchLimit), 100));

            URI uri = UriComponentsBuilder.fromUriString(placesUrl)
                .replaceQueryParam("filter", "rect:" + rect)
                .replaceQueryParam("limit", safeLimit)
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
            List<ScoredSuggestion> scored = new ArrayList<>();
            List<GeoPlaceSuggestion> allSuggestions = new ArrayList<>();

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

                GeoPlaceSuggestion suggestion = new GeoPlaceSuggestion(name, address, lat, lon);
                allSuggestions.add(suggestion);

                if (queryLower.isBlank()) {
                    scored.add(new ScoredSuggestion(suggestion, 1));
                    continue;
                }

                String searchable = (name + " " + address).toLowerCase(Locale.ROOT);
                int score = 0;
                if (name.toLowerCase(Locale.ROOT).contains(queryLower)) {
                    score += 4;
                }
                if (address.toLowerCase(Locale.ROOT).contains(queryLower)) {
                    score += 2;
                }
                for (String token : queryLower.split("\\s+")) {
                    if (!token.isBlank() && searchable.contains(token)) {
                        score += 1;
                    }
                }

                if (score > 0) {
                    scored.add(new ScoredSuggestion(suggestion, score));
                }
            }

            if (!scored.isEmpty()) {
                return scored.stream()
                    .sorted(Comparator.comparingInt(ScoredSuggestion::score).reversed())
                    .map(ScoredSuggestion::suggestion)
                    .limit(20)
                    .toList();
            }

            // Workaround: if text matching returned nothing, show nearby places from current bbox anyway.
            return allSuggestions.stream().limit(20).toList();
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

    private record ScoredSuggestion(GeoPlaceSuggestion suggestion, int score) {}

    public record GeoPlaceSuggestion(String name, String address, double latitude, double longitude) {}
}

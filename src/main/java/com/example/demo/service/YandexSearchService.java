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
public class YandexSearchService {

    private static final String DEFAULT_BBOX = "27.438915081299548,53.890216642813314,27.520990677155474,53.84386961984724";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${yandex.maps.api-key}")
    private String apiKey;

    @Value("${yandex.maps.search-api-url:https://search-maps.yandex.ru/v1/}")
    private String searchApiUrl;

    @Value("${yandex.maps.search-limit:20}")
    private int searchLimit;

    public YandexSearchService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<SearchPlaceDto> search(String query, String bbox) {
        try {
            String cleanQuery = query == null ? "" : query.trim();
            if (cleanQuery.isBlank()) {
                return List.of();
            }

            Bounds bounds = parseBounds(isValidBbox(bbox) ? bbox : DEFAULT_BBOX);
            List<SearchPlaceDto> primary = callYandexSearch(cleanQuery, bounds, true);
            if (!primary.isEmpty()) {
                return primary;
            }
            return callYandexSearch(cleanQuery, bounds, false);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<SearchPlaceDto> callYandexSearch(String query, Bounds bounds, boolean businessOnly) throws Exception {
        int safeLimit = Math.max(1, Math.min(searchLimit, 50));

        URI uriBuilder = UriComponentsBuilder.fromUriString(searchApiUrl)
            .queryParam("apikey", apiKey)
            .queryParam("text", query)
            .queryParam("lang", "ru_RU")
            .queryParam("bbox", bounds.west + "," + bounds.south + "~" + bounds.east + "," + bounds.north)
            .queryParam("rspn", "1")
            .queryParam("results", safeLimit)
            .queryParamIfPresent("type", businessOnly ? java.util.Optional.of("biz") : java.util.Optional.empty())
            .build(true)
            .toUri();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(uriBuilder)
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

        String queryLower = query.toLowerCase(Locale.ROOT);
        List<SearchPlaceDto> result = new ArrayList<>();

        for (JsonNode feature : features) {
            JsonNode geometry = feature.path("geometry").path("coordinates");
            if (!geometry.isArray() || geometry.size() < 2) {
                continue;
            }

            double lon = geometry.get(0).asDouble(Double.NaN);
            double lat = geometry.get(1).asDouble(Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                continue;
            }

            JsonNode properties = feature.path("properties");
            JsonNode meta = properties.path("CompanyMetaData");

            String name = meta.path("name").asText(properties.path("name").asText("Без названия")).trim();
            String address = meta.path("address").asText(properties.path("description").asText("Без адреса")).trim();

            String searchable = (name + " " + address).toLowerCase(Locale.ROOT);
            if (!searchable.contains(queryLower)) {
                continue;
            }

            result.add(new SearchPlaceDto(name, address, lat, lon));
        }

        return result;
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
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Bounds parseBounds(String bbox) {
        String[] p = bbox.split(",");
        return new Bounds(
            Double.parseDouble(p[0].trim()),
            Double.parseDouble(p[1].trim()),
            Double.parseDouble(p[2].trim()),
            Double.parseDouble(p[3].trim())
        );
    }

    private record Bounds(double west, double north, double east, double south) {}

    public record SearchPlaceDto(String name, String address, double latitude, double longitude) {}
}

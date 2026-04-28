package com.example.demo.service;

import com.example.demo.model.Place;
import com.example.demo.repository.PlaceRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.StreamSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlaceLookupService {

    private static final Pattern GOOGLE_IMAGE_PATTERN = Pattern.compile("https?:\\\\/\\\\/[^\"\\\\]{20,}?\\.(?:jpg|jpeg|png|webp)(?:[^\"\\\\]*)", Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_GEOCODER_API_KEY = "../application.properties/${yandex.maps.api-key}";

    private final PlaceRepository placeRepository;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String yandexApiKey;

    public PlaceLookupService(PlaceRepository placeRepository,
                              @Value("${yandex.maps.api-key:}") String yandexApiKey) {
        this.placeRepository = placeRepository;
        this.yandexApiKey = yandexApiKey == null ? "" : yandexApiKey.trim();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<GeoPlaceSuggestion> searchInMinsk(String query, int limit, String bbox) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Bounds bounds = parseBounds(bbox);
        String queryLower = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<GeoPlaceSuggestion> apiSuggestions = searchInYandexMaps(query, safeLimit, bounds);
        if (!apiSuggestions.isEmpty()) {
            return apiSuggestions;
        }

        return placeRepository.findAll().stream()
            .filter(this::hasCoordinates)
            .filter(place -> bounds == null || bounds.contains(place.getLatitude(), place.getLongitude()))
            .map(place -> toScoredSuggestion(place, queryLower))
            .filter(scored -> queryLower.isBlank() || scored.score() > 0)
            .sorted(Comparator.comparingInt(ScoredSuggestion::score).reversed()
                .thenComparing(scored -> scored.suggestion().name()))
            .limit(safeLimit)
            .map(ScoredSuggestion::suggestion)
            .toList();
    }

    private List<GeoPlaceSuggestion> searchInYandexMaps(String query, int limit, Bounds bounds) {
        if (query == null || query.isBlank() || !isYandexApiKeyConfigured()) {
            return List.of();
        }

        try {
            URI searchUri = UriComponentsBuilder
                .fromUriString("https://geocode-maps.yandex.ru/1.x/")
                .queryParam("apikey", yandexApiKey)
                .queryParam("format", "json")
                .queryParam("lang", "ru_RU")
                .queryParam("results", limit)
                .queryParam("geocode", query.trim() + ", Минск")
                .queryParamIfPresent("bbox", Optional.ofNullable(formatBounds(bounds)))
                .queryParamIfPresent("rspn", Optional.of(bounds == null ? null : "1"))
                .build(true)
                .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(searchUri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }

            return extractGeoSuggestions(response.body(), limit, bounds);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public Optional<String> lookupPlaceImage(String placeName, String address) {
        String normalizedName = placeName == null ? "" : placeName.trim();
        if (normalizedName.isBlank()) {
            return Optional.empty();
        }

        String searchQuery = (normalizedName + " " + (address == null ? "" : address)).trim();
        String[] queries = new String[]{searchQuery, normalizedName + " Минск", normalizedName};

        for (String query : queries) {
            Optional<String> image = lookupFromGoogleImages(query);
            if (image.isPresent()) {
                return image;
            }
        }

        return Optional.empty();
    }

    private ScoredSuggestion toScoredSuggestion(Place place, String queryLower) {
        String name = Optional.ofNullable(place.getName()).orElse("Без названия").trim();
        String address = Optional.ofNullable(place.getAddress()).orElse("Без адреса").trim();
        String searchable = (name + " " + address).toLowerCase(Locale.ROOT);

        int score = 1;
        if (!queryLower.isBlank()) {
            score = 0;
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
        }

        return new ScoredSuggestion(new GeoPlaceSuggestion(name, address, place.getLatitude(), place.getLongitude()), score);
    }

    private boolean hasCoordinates(Place place) {
        return place.getLatitude() != null && place.getLongitude() != null;
    }

    private Optional<String> lookupFromGoogleImages(String query) {
        try {
            URI searchUri = UriComponentsBuilder
                .fromUriString("https://images.google.com/search")
                .queryParam("hl", "ru")
                .queryParam("tbm", "isch")
                .queryParam("q", query)
                .build(true)
                .toUri();

            HttpRequest searchRequest = HttpRequest.newBuilder()
                .uri(searchUri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                .GET()
                .build();

            HttpResponse<String> searchResponse = httpClient.send(searchRequest, HttpResponse.BodyHandlers.ofString());
            if (searchResponse.statusCode() < 200 || searchResponse.statusCode() >= 300) {
                return Optional.empty();
            }

            return extractImageFromGoogleHtml(searchResponse.body());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<String> extractImageFromGoogleHtml(String html) {
        Matcher matcher = GOOGLE_IMAGE_PATTERN.matcher(html);
        while (matcher.find()) {
            String candidate = matcher.group()
                .replace("\\/", "/")
                .replace("\\u003d", "=")
                .replace("\\u0026", "&");

            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.contains("gstatic.com")
                || lower.contains("google.com")
                || lower.contains("logo")
                || lower.contains("sprite")) {
                continue;
            }

            return Optional.of(candidate);
        }

        return Optional.empty();
    }

    private List<GeoPlaceSuggestion> extractGeoSuggestions(String json, int limit, Bounds bounds) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode featureMembers = root.path("response")
                .path("GeoObjectCollection")
                .path("featureMember");
            if (!featureMembers.isArray()) {
                return List.of();
            }

            return StreamSupport.stream(featureMembers.spliterator(), false)
                .map(this::mapFeatureToSuggestion)
                .flatMap(Optional::stream)
                .filter(suggestion -> bounds == null || bounds.contains(suggestion.latitude(), suggestion.longitude()))
                .limit(limit)
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Optional<GeoPlaceSuggestion> mapFeatureToSuggestion(JsonNode featureMember) {
        JsonNode geoObject = featureMember.path("GeoObject");
        String pos = geoObject.path("Point").path("pos").asText("");
        String[] coords = pos.trim().split("\\s+");
        if (coords.length != 2) {
            return Optional.empty();
        }

        try {
            double longitude = Double.parseDouble(coords[0]);
            double latitude = Double.parseDouble(coords[1]);

            String name = geoObject.path("name").asText("").trim();
            String description = geoObject.path("description").asText("").trim();
            String formattedAddress = geoObject.path("metaDataProperty")
                .path("GeocoderMetaData")
                .path("Address")
                .path("formatted")
                .asText("")
                .trim();

            String resolvedName = name.isBlank() ? "Без названия" : name;
            String resolvedAddress = !formattedAddress.isBlank() ? formattedAddress :
                (!description.isBlank() ? description : "Без адреса");

            return Optional.of(new GeoPlaceSuggestion(resolvedName, resolvedAddress, latitude, longitude));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String formatBounds(Bounds bounds) {
        if (bounds == null) {
            return null;
        }

        return bounds.west() + "," + bounds.south() + "~" + bounds.east() + "," + bounds.north();
    }

    private boolean isYandexApiKeyConfigured() {
        return !yandexApiKey.isBlank() && !DEFAULT_GEOCODER_API_KEY.equals(yandexApiKey);
    }

    private Bounds parseBounds(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return null;
        }

        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            return null;
        }

        try {
            double west = Double.parseDouble(parts[0].trim());
            double north = Double.parseDouble(parts[1].trim());
            double east = Double.parseDouble(parts[2].trim());
            double south = Double.parseDouble(parts[3].trim());
            return new Bounds(west, north, east, south);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record Bounds(double west, double north, double east, double south) {
        private boolean contains(double lat, double lon) {
            return lon >= Math.min(west, east)
                && lon <= Math.max(west, east)
                && lat >= Math.min(south, north)
                && lat <= Math.max(south, north);
        }
    }

    private record ScoredSuggestion(GeoPlaceSuggestion suggestion, int score) {}

    public record GeoPlaceSuggestion(String name, String address, double latitude, double longitude) {}
}

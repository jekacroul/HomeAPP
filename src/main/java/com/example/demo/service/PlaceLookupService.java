package com.example.demo.service;

import com.example.demo.model.Place;
import com.example.demo.repository.PlaceRepository;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlaceLookupService {

    private static final Pattern GOOGLE_IMAGE_PATTERN = Pattern.compile("https?:\\\\/\\\\/[^\"\\\\]{20,}?\\.(?:jpg|jpeg|png|webp)(?:[^\"\\\\]*)", Pattern.CASE_INSENSITIVE);

    private final PlaceRepository placeRepository;
    private final HttpClient httpClient;

    @Value("${places.search-limit:50}")
    private int searchLimit;

    public PlaceLookupService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<GeoPlaceSuggestion> searchInMinsk(String query, int limit, String bbox) {
        int safeLimit = Math.max(20, Math.min(Math.max(limit, searchLimit), 100));
        Bounds bounds = parseBounds(bbox);
        String queryLower = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();

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

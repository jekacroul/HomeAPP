package com.example.demo.controller;

import com.example.demo.model.Place;
import com.example.demo.model.Review;
import com.example.demo.model.User;
import com.example.demo.repository.PlaceRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.GeoapifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Controller
public class HomeController {

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GeoapifyService geoapifyService;

    @Value("${geoapify.map-tile-url}")
    private String mapTileUrl;

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        List<Place> places = placeRepository.findAll();
        model.addAttribute("places", places);

        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            userRepository.findByEmail(email).ifPresent(user ->
                model.addAttribute("currentUser", user));
        }

        return "home";
    }

    @GetMapping("/place/{id}")
    public String placeDetail(@PathVariable Long id, Model model, Authentication authentication) {
        Place place = placeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Place not found"));
        model.addAttribute("place", place);

        List<Review> reviews = reviewRepository.findByPlaceId(id);
        model.addAttribute("reviews", reviews);
        model.addAttribute("mapTileUrl", mapTileUrl);

        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            userRepository.findByEmail(email).ifPresent(user ->
                model.addAttribute("currentUser", user));
        }

        return "place-detail";
    }

    @PostMapping("/place/{id}/review")
    public String addReview(@PathVariable Long id,
                            @RequestParam Integer rating,
                            @RequestParam String comment,
                            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        Place place = placeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Place not found"));

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Review review = new Review();
        review.setPlace(place);
        review.setUser(user);
        review.setRating(rating);
        review.setComment(comment);

        reviewRepository.save(review);

        List<Review> placeReviews = reviewRepository.findByPlaceId(id);
        double avgRating = placeReviews.stream()
            .mapToInt(Review::getRating)
            .average()
            .orElse(0.0);
        place.setRating(avgRating);
        placeRepository.save(place);

        return "redirect:/place/" + id;
    }

    @GetMapping("/add-place")
    public String addPlaceForm(Model model) {
        model.addAttribute("place", new Place());
        model.addAttribute("mapTileUrl", mapTileUrl);
        return "add-place";
    }

    @GetMapping(value = "/api/minsk-places", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<GeoapifyService.GeoPlaceSuggestion> searchMinskPlaces(
        @RequestParam(value = "query", required = false) String query,
        @RequestParam(value = "bbox", required = false) String bbox
    ) {
        return geoapifyService.searchInMinsk(query, 100, bbox);
    }

    @PostMapping(value = "/add-place", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String addPlace(@ModelAttribute Place place,
                           @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                           @RequestParam(value = "imageUrl", required = false) String imageUrl) {

        String finalImageUrl = resolveImage(imageFile, imageUrl);
        place.setImageUrl(finalImageUrl);
        place.setRating(0.0);

        if (place.getWifi() == null) {
            place.setWifi(false);
            place.setWifiSpeed(null);
        }

        if (place.getStayDurationAllowed() == null) {
            place.setStayDurationAllowed(false);
        }

        placeRepository.save(place);
        return "redirect:/";
    }

    private String resolveImage(MultipartFile imageFile, String imageUrl) {
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                Path uploadDir = Paths.get("uploads");
                Files.createDirectories(uploadDir);

                String originalName = imageFile.getOriginalFilename();
                String extension = ".jpg";
                if (originalName != null && originalName.contains(".")) {
                    extension = originalName.substring(originalName.lastIndexOf('.'));
                }

                String fileName = UUID.randomUUID() + extension;
                Path target = uploadDir.resolve(fileName);
                Files.copy(imageFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                return "/uploads/" + fileName;
            } catch (IOException e) {
                return "https://images.unsplash.com/photo-1497215728101-856f4ea42174?w=800";
            }
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl;
        }

        return "https://images.unsplash.com/photo-1497215728101-856f4ea42174?w=800";
    }
}

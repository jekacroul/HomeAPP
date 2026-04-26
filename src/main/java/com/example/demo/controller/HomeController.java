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
import java.util.Optional;
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

    @Value("${yandex.maps.js-api-url}")
    private String mapApiUrl;

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        List<Place> places = placeRepository.findAll();
        model.addAttribute("places", places);

        getCurrentUser(authentication).ifPresent(user -> model.addAttribute("currentUser", user));

        return "home";
    }

    @GetMapping("/place/{id}")
    public String placeDetail(@PathVariable Long id, Model model, Authentication authentication) {
        Place place = placeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Place not found"));
        model.addAttribute("place", place);

        List<Review> reviews = reviewRepository.findByPlaceIdOrderByCreatedAtDesc(id);
        model.addAttribute("reviews", reviews);
        model.addAttribute("mapApiUrl", mapApiUrl);
        model.addAttribute("isAdmin", false);

        getCurrentUser(authentication).ifPresent(user -> {
            model.addAttribute("currentUser", user);
            model.addAttribute("isAdmin", user.getRole() == User.Role.ADMIN);
        });

        return "place-detail";
    }

    @PostMapping(value = "/place/{id}/review", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String addReview(@PathVariable Long id,
                            @RequestParam Integer rating,
                            @RequestParam String comment,
                            @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
                            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        Place place = placeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Place not found"));

        User user = getCurrentUser(authentication)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Review review = new Review();
        review.setPlace(place);
        review.setUser(user);
        review.setRating(rating);
        review.setComment(comment);
        review.setImageUrls(resolveImages(imageFiles, "uploads/reviews"));
        review.setImageUrl(null);

        reviewRepository.save(review);
        recalculatePlaceRating(id);

        return "redirect:/place/" + id;
    }

    @PostMapping(value = "/review/{reviewId}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String editReview(@PathVariable Long reviewId,
                             @RequestParam Integer rating,
                             @RequestParam String comment,
                             @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
                             @RequestParam(value = "removeImage", required = false, defaultValue = "false") Boolean removeImage,
                             Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new RuntimeException("Review not found"));

        User currentUser = getCurrentUser(authentication)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (!canManageReview(review, currentUser)) {
            return "redirect:/place/" + review.getPlace().getId();
        }

        review.setRating(rating);
        review.setComment(comment);

        List<String> currentImages = review.getAllImageUrls();
        if (Boolean.TRUE.equals(removeImage)) {
            currentImages.clear();
        }
        currentImages.addAll(resolveImages(imageFiles, "uploads/reviews"));

        review.setImageUrls(currentImages);
        review.setImageUrl(null);

        reviewRepository.save(review);
        recalculatePlaceRating(review.getPlace().getId());

        return "redirect:/place/" + review.getPlace().getId();
    }

    @PostMapping("/review/{reviewId}/delete")
    public String deleteReview(@PathVariable Long reviewId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new RuntimeException("Review not found"));

        User currentUser = getCurrentUser(authentication)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (!canManageReview(review, currentUser)) {
            return "redirect:/place/" + review.getPlace().getId();
        }

        Long placeId = review.getPlace().getId();
        reviewRepository.delete(review);
        recalculatePlaceRating(placeId);

        return "redirect:/place/" + placeId;
    }

    @GetMapping("/add-place")
    public String addPlaceForm(Model model) {
        model.addAttribute("place", new Place());
        model.addAttribute("mapApiUrl", mapApiUrl);
        return "add-place";
    }

    @GetMapping(value = "/api/minsk-places", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<GeoapifyService.GeoPlaceSuggestion> searchPlaces(
        @RequestParam(value = "query", required = false) String query,
        @RequestParam(value = "bbox", required = false) String bbox
    ) {
        return geoapifyService.searchInMinsk(query, 50, bbox);
    }

    @PostMapping(value = "/add-place", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String addPlace(@ModelAttribute Place place,
                           @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                           @RequestParam(value = "imageUrl", required = false) String imageUrl) {

        String finalImageUrl = resolveImage(
            imageFile,
            imageUrl,
            "uploads",
            "https://images.unsplash.com/photo-1497215728101-856f4ea42174?w=800"
        );
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

    private Optional<User> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(authentication.getName());
    }

    private boolean canManageReview(Review review, User currentUser) {
        return currentUser.getRole() == User.Role.ADMIN
            || review.getUser().getId().equals(currentUser.getId());
    }

    private void recalculatePlaceRating(Long placeId) {
        Place place = placeRepository.findById(placeId)
            .orElseThrow(() -> new RuntimeException("Place not found"));

        List<Review> placeReviews = reviewRepository.findByPlaceId(placeId);
        double avgRating = placeReviews.stream()
            .mapToInt(Review::getRating)
            .average()
            .orElse(0.0);
        place.setRating(avgRating);

        placeRepository.save(place);
    }

    private String resolveImage(MultipartFile imageFile, String imageUrl, String uploadDirName) {
        return resolveImage(imageFile, imageUrl, uploadDirName, imageUrl);
    }

    private List<String> resolveImages(MultipartFile[] imageFiles, String uploadDirName) {
        List<String> uploadedUrls = new java.util.ArrayList<>();
        if (imageFiles == null || imageFiles.length == 0) {
            return uploadedUrls;
        }

        for (MultipartFile imageFile : imageFiles) {
            if (imageFile == null || imageFile.isEmpty()) {
                continue;
            }
            String imageUrl = resolveImage(imageFile, null, uploadDirName, null);
            if (imageUrl != null && !imageUrl.isBlank()) {
                uploadedUrls.add(imageUrl);
            }
        }
        return uploadedUrls;
    }

    private String resolveImage(MultipartFile imageFile, String imageUrl, String uploadDirName, String fallbackUrl) {
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                Path uploadDir = Paths.get(uploadDirName);
                Files.createDirectories(uploadDir);

                String originalName = imageFile.getOriginalFilename();
                String extension = ".jpg";
                if (originalName != null && originalName.contains(".")) {
                    extension = originalName.substring(originalName.lastIndexOf('.'));
                }

                String fileName = UUID.randomUUID() + extension;
                Path target = uploadDir.resolve(fileName);
                Files.copy(imageFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                return "/" + uploadDirName + "/" + fileName;
            } catch (IOException e) {
                return fallbackUrl;
            }
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl;
        }

        return fallbackUrl;
    }
}

package com.example.demo.controller;

import com.example.demo.model.Place;
import com.example.demo.model.Review;
import com.example.demo.model.User;
import com.example.demo.repository.PlaceRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class HomeController {
    
    @Autowired
    private PlaceRepository placeRepository;
    
    @Autowired
    private ReviewRepository reviewRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        List<Place> places = placeRepository.findAll();
        model.addAttribute("places", places);
        addCurrentUserToModel(model, authentication);
        
        return "home";
    }
    
    @GetMapping("/place/{id}")
    public String placeDetail(@PathVariable Long id, Model model, Authentication authentication) {
        Place place = placeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Place not found"));
        model.addAttribute("place", place);
        
        List<Review> reviews = reviewRepository.findByPlaceId(id);
        model.addAttribute("reviews", reviews);
        addCurrentUserToModel(model, authentication);
        
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
        
        // Update place rating
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
        return "add-place";
    }
    
    @PostMapping("/add-place")
    public String addPlace(@ModelAttribute Place place) {
        place.setRating(0.0);
        placeRepository.save(place);
        return "redirect:/";
    }

    @GetMapping("/admin")
    public String adminDashboard(Model model, Authentication authentication) {
        addCurrentUserToModel(model, authentication);
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("places", placeRepository.findAll());
        return "admin-dashboard";
    }

    @PostMapping("/admin/place/{id}/delete")
    public String deletePlace(@PathVariable Long id) {
        placeRepository.deleteById(id);
        return "redirect:/admin?deletedPlace=true";
    }

    @PostMapping("/admin/user/{id}/toggle-ban")
    public String toggleUserBan(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentEmail = authentication.getName();

        userRepository.findById(id).ifPresent(user -> {
            if (user.getEmail().equalsIgnoreCase(currentEmail) || user.getRole() == User.Role.ADMIN) {
                return;
            }
            user.setBanned(!user.isBanned());
            userRepository.save(user);
        });

        return "redirect:/admin?updatedUser=true";
    }

    private void addCurrentUserToModel(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        String email = authentication.getName();
        userRepository.findByEmail(email).ifPresent(user -> model.addAttribute("currentUser", user));
    }
}

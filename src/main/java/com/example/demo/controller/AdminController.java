package com.example.demo.controller;

import com.example.demo.model.Place;
import com.example.demo.model.User;
import com.example.demo.repository.PlaceRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class AdminController {

    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;

    public AdminController(UserRepository userRepository, PlaceRepository placeRepository) {
        this.userRepository = userRepository;
        this.placeRepository = placeRepository;
    }

    @GetMapping("/admin")
    public String adminPage(Model model) {
        List<User> users = userRepository.findAll();
        List<Place> places = placeRepository.findAll();

        model.addAttribute("users", users);
        model.addAttribute("places", places);
        return "admin";
    }

    @PostMapping("/place/{id}/delete")
    public String deletePlace(@PathVariable Long id) {
        placeRepository.deleteById(id);
        return "redirect:/admin";
    }

    @PostMapping("/admin/user/{id}/ban")
    public String banUser(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            if (user.getRole() != User.Role.ADMIN) {
                user.setBanned(true);
                userRepository.save(user);
            }
        });

        return "redirect:/admin";
    }

    @PostMapping("/admin/user/{id}/unban")
    public String unbanUser(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setBanned(false);
            userRepository.save(user);
        });

        return "redirect:/admin";
    }
}

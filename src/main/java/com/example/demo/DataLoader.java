package com.example.demo;

import com.example.demo.model.User;
import com.example.demo.repository.PlaceRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(PlaceRepository placeRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        placeRepository.deleteByNameIn(List.of("Coffee & Work", "Coworking Space Hub", "Cafe Relax"));

        if (userRepository.findByEmail("admin@homeapp.local").isEmpty()) {
            User admin = new User();
            admin.setEmail("admin@homeapp.local");
            admin.setName("Администратор");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole(User.Role.ADMIN);
            admin.setBanned(false);
            userRepository.save(admin);
        }
    }
}

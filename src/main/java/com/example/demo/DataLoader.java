package com.example.demo;

import com.example.demo.model.User;
import com.example.demo.repository.PlaceRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public DataLoader(
        PlaceRepository placeRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JdbcTemplate jdbcTemplate
    ) {
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureUserColumnsExist();
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

    private void ensureUserColumnsExist() {
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS banned BOOLEAN");
        jdbcTemplate.execute("UPDATE users SET role = 'USER' WHERE role IS NULL");
        jdbcTemplate.execute("UPDATE users SET banned = FALSE WHERE banned IS NULL");
    }
}

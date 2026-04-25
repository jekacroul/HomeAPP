package com.example.demo;

import com.example.demo.model.User;
import com.example.demo.repository.PlaceRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final String adminSeedEmail;
    private final String adminSeedName;
    private final String adminSeedPasswordHash;

    public DataLoader(
        PlaceRepository placeRepository,
        UserRepository userRepository,
        JdbcTemplate jdbcTemplate,
        @Value("${app.admin.seed.email:}") String adminSeedEmail,
        @Value("${app.admin.seed.name:Администратор}") String adminSeedName,
        @Value("${app.admin.seed.password-hash:}") String adminSeedPasswordHash
    ) {
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.adminSeedEmail = adminSeedEmail;
        this.adminSeedName = adminSeedName;
        this.adminSeedPasswordHash = adminSeedPasswordHash;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureUserColumnsExist();
        placeRepository.deleteByNameIn(List.of("Coffee & Work", "Coworking Space Hub", "Cafe Relax"));
        createAdminFromSeedIfNeeded();
    }

    private void createAdminFromSeedIfNeeded() {
        if (adminSeedEmail == null || adminSeedEmail.isBlank()) {
            return;
        }
        if (adminSeedPasswordHash == null || adminSeedPasswordHash.isBlank()) {
            return;
        }
        if (!adminSeedPasswordHash.matches("^\\$2[aby]\\$.{56}$")) {
            throw new IllegalStateException("app.admin.seed.password-hash must be a BCrypt hash");
        }

        if (userRepository.findByEmail(adminSeedEmail).isEmpty()) {
            User admin = new User();
            admin.setEmail(adminSeedEmail);
            admin.setName(adminSeedName);
            admin.setPassword(adminSeedPasswordHash);
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

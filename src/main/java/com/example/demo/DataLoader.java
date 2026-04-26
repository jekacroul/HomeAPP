package com.example.demo;

import com.example.demo.model.User;
import com.example.demo.repository.PlaceRepository;
import com.example.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

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
        log.info("Running startup data loader tasks.");
        ensureUserColumnsExist();

        int beforeCleanup = placeRepository.findAll().size();
        placeRepository.deleteByNameIn(List.of("Coffee & Work", "Coworking Space Hub", "Cafe Relax"));
        int afterCleanup = placeRepository.findAll().size();
        log.info("Place cleanup completed. Before={}, after={}", beforeCleanup, afterCleanup);

        createAdminFromSeedIfNeeded();
        log.info("Startup data loader tasks completed.");
    }

    private void createAdminFromSeedIfNeeded() {
        if (adminSeedEmail == null || adminSeedEmail.isBlank()) {
            log.info("Admin seed skipped: app.admin.seed.email is empty.");
            return;
        }
        if (adminSeedPasswordHash == null || adminSeedPasswordHash.isBlank()) {
            log.warn("Admin seed skipped: app.admin.seed.password-hash is empty.");
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
            log.info("Seed admin created for email={}", adminSeedEmail);
            return;
        }

        log.info("Admin seed skipped: user with email={} already exists.", adminSeedEmail);
    }

    private void ensureUserColumnsExist() {
        log.info("Ensuring user service columns exist.");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS banned BOOLEAN");
        jdbcTemplate.execute("UPDATE users SET role = 'USER' WHERE role IS NULL");
        jdbcTemplate.execute("UPDATE users SET banned = FALSE WHERE banned IS NULL");
        log.info("User service columns are ready.");
    }
}

package com.example.demo;

import com.example.demo.model.User;
import com.example.demo.model.Place;
import com.example.demo.repository.PlaceRepository;
import com.example.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String adminSeedEmail;
    private final String adminSeedName;
    private final String adminSeedPasswordHash;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final boolean migratePlainPasswords;

    public DataLoader(
        PlaceRepository placeRepository,
        UserRepository userRepository,
        JdbcTemplate jdbcTemplate,
        PasswordEncoder passwordEncoder,
        @Value("${app.admin.seed.email:}") String adminSeedEmail,
        @Value("${app.admin.seed.name:Администратор}") String adminSeedName,
        @Value("${app.admin.seed.password-hash:}") String adminSeedPasswordHash,
        @Value("${spring.datasource.url:unknown}") String datasourceUrl,
        @Value("${spring.datasource.username:unknown}") String datasourceUsername,
        @Value("${app.auth.migrate-plain-passwords:false}") boolean migratePlainPasswords
    ) {
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.adminSeedEmail = adminSeedEmail;
        this.adminSeedName = adminSeedName;
        this.adminSeedPasswordHash = adminSeedPasswordHash;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.migratePlainPasswords = migratePlainPasswords;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureUserColumnsExist();
        migrateLegacyPlainPasswordsIfEnabled();
        createAdminFromSeedIfNeeded();
        seedPlacesIfEmpty();
        logStartupDataSummary();
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

    private void migrateLegacyPlainPasswordsIfEnabled() {
        if (!migratePlainPasswords) {
            return;
        }

        int migrated = 0;
        List<User> users = userRepository.findAll();
        for (User user : users) {
            String password = user.getPassword();
            if (password == null || password.matches("^\\$2[aby]\\$.{56}$")) {
                continue;
            }
            user.setPassword(passwordEncoder.encode(password));
            migrated++;
        }
        if (migrated > 0) {
            userRepository.saveAll(users);
            log.warn("Migrated {} legacy plaintext password(s) to BCrypt. Disable app.auth.migrate-plain-passwords after first run.", migrated);
        }
    }

    private void seedPlacesIfEmpty() {
        if (placeRepository.count() > 0) {
            return;
        }

        List<Place> seed = new ArrayList<>();
        seed.add(newPlace(
            "Coffee & Work",
            "ул. Ленина, 10",
            53.9023,
            27.5615,
            "https://images.unsplash.com/photo-1497215728101-856f4ea42174?w=800",
            true,
            80,
            Place.NoiseLevel.MEDIUM,
            Place.SocketAvailability.MANY,
            true
        ));
        seed.add(newPlace(
            "Coworking Space Hub",
            "пр-т Независимости, 25",
            53.9045,
            27.5588,
            "https://images.unsplash.com/photo-1522199755839-a2bacb67c546?w=800",
            true,
            120,
            Place.NoiseLevel.QUIET,
            Place.SocketAvailability.MANY,
            true
        ));
        seed.add(newPlace(
            "Cafe Relax",
            "ул. Интернациональная, 3",
            53.9007,
            27.5552,
            "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800",
            true,
            40,
            Place.NoiseLevel.NOISY,
            Place.SocketAvailability.FEW,
            false
        ));

        placeRepository.saveAll(seed);
        log.info("Seeded {} demo place(s) because places table was empty.", seed.size());
    }

    private Place newPlace(
        String name,
        String address,
        double latitude,
        double longitude,
        String imageUrl,
        boolean wifi,
        int wifiSpeed,
        Place.NoiseLevel noiseLevel,
        Place.SocketAvailability sockets,
        boolean stayDurationAllowed
    ) {
        Place place = new Place();
        place.setName(name);
        place.setAddress(address);
        place.setLatitude(latitude);
        place.setLongitude(longitude);
        place.setImageUrl(imageUrl);
        place.setWifi(wifi);
        place.setWifiSpeed(wifiSpeed);
        place.setNoiseLevel(noiseLevel);
        place.setSockets(sockets);
        place.setStayDurationAllowed(stayDurationAllowed);
        place.setRating(0.0);
        return place;
    }

    private void logStartupDataSummary() {
        log.info("Datasource in use: url='{}', username='{}'", datasourceUrl, datasourceUsername);
        log.info("Data summary: users={}, places={}", userRepository.count(), placeRepository.count());
    }
}

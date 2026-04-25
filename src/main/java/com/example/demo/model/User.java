package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(name = "password_hash", nullable = false, length = 72)
    private String password;
    
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(nullable = false)
    private Boolean banned = false;
    
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Review> reviews = new ArrayList<>();

    public enum Role {
        USER, ADMIN
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        validatePasswordHash();
    }

    @PreUpdate
    protected void onUpdate() {
        validatePasswordHash();
    }

    private void validatePasswordHash() {
        if (password == null || !password.matches("^\\$2[aby]\\$.{56}$")) {
            throw new IllegalStateException("Password must be stored as a BCrypt hash");
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Boolean getBanned() { return banned; }
    public void setBanned(Boolean banned) { this.banned = banned; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    
    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }
}

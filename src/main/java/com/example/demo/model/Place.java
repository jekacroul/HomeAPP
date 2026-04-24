package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "places")
public class Place {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    @Column(length = 1000)
    private String address;
    
    private Double latitude;
    private Double longitude;
    
    @Column(length = 2000)
    private String imageUrl;
    
    private Boolean wifi;
    private Integer wifiSpeed; // Mbps
    
    @Enumerated(EnumType.STRING)
    private NoiseLevel noiseLevel;
    
    @Enumerated(EnumType.STRING)
    private SocketAvailability sockets;
    
    private Boolean stayDurationAllowed;
    
    private Double rating;
    
    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Review> reviews = new ArrayList<>();
    
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    public enum NoiseLevel {
        QUIET, MEDIUM, NOISY
    }
    
    public enum SocketAvailability {
        MANY, FEW, NONE
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public Boolean getWifi() { return wifi; }
    public void setWifi(Boolean wifi) { this.wifi = wifi; }
    
    public Integer getWifiSpeed() { return wifiSpeed; }
    public void setWifiSpeed(Integer wifiSpeed) { this.wifiSpeed = wifiSpeed; }
    
    public NoiseLevel getNoiseLevel() { return noiseLevel; }
    public void setNoiseLevel(NoiseLevel noiseLevel) { this.noiseLevel = noiseLevel; }
    
    public SocketAvailability getSockets() { return sockets; }
    public void setSockets(SocketAvailability sockets) { this.sockets = sockets; }
    
    public Boolean getStayDurationAllowed() { return stayDurationAllowed; }
    public void setStayDurationAllowed(Boolean stayDurationAllowed) { this.stayDurationAllowed = stayDurationAllowed; }
    
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    
    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

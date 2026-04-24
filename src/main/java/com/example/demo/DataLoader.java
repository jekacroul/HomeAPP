package com.example.demo;

import com.example.demo.model.Place;
import com.example.demo.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {
    
    @Autowired
    private PlaceRepository placeRepository;
    
    @Override
    public void run(String... args) {
        if (placeRepository.count() == 0) {
            Place place1 = new Place();
            place1.setName("Coffee & Work");
            place1.setAddress("ул. Ленина, 10");
            place1.setLatitude(55.7558);
            place1.setLongitude(37.6173);
            place1.setImageUrl("https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800");
            place1.setWifi(true);
            place1.setWifiSpeed(50);
            place1.setNoiseLevel(Place.NoiseLevel.QUIET);
            place1.setSockets(Place.SocketAvailability.MANY);
            place1.setStayDurationAllowed(true);
            place1.setRating(4.5);
            placeRepository.save(place1);
            
            Place place2 = new Place();
            place2.setName("Coworking Space Hub");
            place2.setAddress("пр. Мира, 25");
            place2.setLatitude(55.7658);
            place2.setLongitude(37.6273);
            place2.setImageUrl("https://images.unsplash.com/photo-1497215728101-856f4ea42174?w=800");
            place2.setWifi(true);
            place2.setWifiSpeed(100);
            place2.setNoiseLevel(Place.NoiseLevel.MEDIUM);
            place2.setSockets(Place.SocketAvailability.MANY);
            place2.setStayDurationAllowed(true);
            place2.setRating(4.8);
            placeRepository.save(place2);
            
            Place place3 = new Place();
            place3.setName("Cafe Relax");
            place3.setAddress("ул. Пушкина, 5");
            place3.setLatitude(55.7458);
            place3.setLongitude(37.6073);
            place3.setImageUrl("https://images.unsplash.com/photo-1521017432531-fbd92d768814?w=800");
            place3.setWifi(true);
            place3.setWifiSpeed(30);
            place3.setNoiseLevel(Place.NoiseLevel.NOISY);
            place3.setSockets(Place.SocketAvailability.FEW);
            place3.setStayDurationAllowed(false);
            place3.setRating(3.5);
            placeRepository.save(place3);
        }
    }
}

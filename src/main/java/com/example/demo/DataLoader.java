package com.example.demo;

import com.example.demo.repository.PlaceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private final PlaceRepository placeRepository;

    public DataLoader(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        placeRepository.deleteByNameIn(List.of("Coffee & Work", "Coworking Space Hub", "Cafe Relax"));
    }
}

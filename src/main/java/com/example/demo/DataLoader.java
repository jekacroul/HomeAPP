package com.example.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    @Override
    public void run(String... args) {
        // Заглушки убраны: места добавляются пользователями через форму
        // и поиск через Geoapify API по Минску.
    }
}

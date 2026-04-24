package org.example.homeapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HomeAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomeAppApplication.class, args);
        System.out.println("Application Started");
    }

}

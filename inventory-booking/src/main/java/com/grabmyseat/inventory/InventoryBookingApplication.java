package com.grabmyseat.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InventoryBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryBookingApplication.class, args);
    }
}

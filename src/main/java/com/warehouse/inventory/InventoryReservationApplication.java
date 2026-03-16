package com.warehouse.inventory;

import com.warehouse.inventory.config.AotRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(AotRuntimeHints.class)
public class InventoryReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryReservationApplication.class, args);
    }
}

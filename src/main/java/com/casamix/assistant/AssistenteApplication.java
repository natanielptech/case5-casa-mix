package com.casamix.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AssistenteApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssistenteApplication.class, args);
    }
}

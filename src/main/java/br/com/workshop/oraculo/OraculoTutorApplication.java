package br.com.workshop.oraculo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OraculoTutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OraculoTutorApplication.class, args);
    }
}

package com.rbaudu.obsstreamviewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principale de l'application Spring Boot.
 * 
 * @author rbaudu
 */
@SpringBootApplication
@EnableScheduling
public class ObsStreamViewerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObsStreamViewerApplication.class, args);
    }
}

package com.krakow.theaters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.*;
import java.net.URI;

@Slf4j
@SpringBootApplication
public class KrakowTheatreApplication {

    public static void main(String[] args) {
        SpringApplication.run(KrakowTheatreApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Aplikacja uruchomiona.");
        openBrowser();
    }

    private void openBrowser() {
        try {
            String url = "http://localhost:8080/api/v1/plays";
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Runtime.getRuntime().exec(new String[]{"open", url});
            }
        } catch (Exception e) {
            log.error("Nie można otworzyć przeglądarki: {}", e.getMessage());
        }
    }
}
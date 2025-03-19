package com.rbaudu.obsstreamviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration pour OBS.
 * Cette classe est mappée sur les propriétés définies dans application.yml
 * sous le préfixe "obs".
 * 
 * @author rbaudu
 */
@Configuration
@ConfigurationProperties(prefix = "obs")
@Data
public class ObsProperties {
    
    private Connection connection = new Connection();
    private Video video = new Video();
    private Audio audio = new Audio();
    
    /**
     * Propriétés de connexion à OBS.
     */
    @Data
    public static class Connection {
        private String host = "localhost";
        private int videoPort = 8081;
        private int audioPort = 8082;
        private int websocketPort = 4455;
        private String password = "";
    }
    
    /**
     * Propriétés vidéo.
     */
    @Data
    public static class Video {
        private int width = 1280;
        private int height = 720;
        private int framerate = 30;
        private String format = "yuv420p";
    }
    
    /**
     * Propriétés audio.
     */
    @Data
    public static class Audio {
        private int sampleRate = 44100;
        private int channels = 2;
        private String format = "s16le";
    }
}

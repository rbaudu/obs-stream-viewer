package com.rbaudu.obsstreamviewer.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Classe représentant un paquet de données vidéo.
 * 
 * @author rbaudu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VideoPacket extends StreamPacket {
    
    /**
     * Données vidéo encodées en Base64.
     */
    private String frameData;
    
    /**
     * Largeur de l'image en pixels.
     */
    private int width;
    
    /**
     * Hauteur de l'image en pixels.
     */
    private int height;
    
    /**
     * Format de l'image (par exemple, "yuv420p").
     */
    private String pixelFormat;
    
    /**
     * PTS (Presentation Time Stamp) de la frame vidéo.
     */
    private long pts;
    
    /**
     * Horodatage de présentation en millisecondes.
     */
    private long presentationTimestampMs;
    
    public VideoPacket() {
        super();
        setSource("video");
        setDataType("frame");
    }
}

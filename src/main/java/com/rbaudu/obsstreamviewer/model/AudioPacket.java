package com.rbaudu.obsstreamviewer.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Classe représentant un paquet de données audio.
 * 
 * @author rbaudu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AudioPacket extends StreamPacket {
    
    /**
     * Données audio encodées en Base64.
     */
    private String audioData;
    
    /**
     * Fréquence d'échantillonnage (Hz).
     */
    private int sampleRate;
    
    /**
     * Nombre de canaux audio.
     */
    private int channels;
    
    /**
     * Format du son (par exemple, "s16le").
     */
    private String audioFormat;
    
    /**
     * Nombre d'échantillons dans ce paquet.
     */
    private int sampleCount;
    
    /**
     * PTS (Presentation Time Stamp) des données audio.
     */
    private long pts;
    
    /**
     * Durée du segment audio en millisecondes.
     */
    private long durationMs;
    
    /**
     * Horodatage de présentation en millisecondes.
     */
    private long presentationTimestampMs;
    
    public AudioPacket() {
        super();
        setSource("audio");
        setDataType("samples");
    }
}

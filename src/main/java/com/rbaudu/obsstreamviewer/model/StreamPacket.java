package com.rbaudu.obsstreamviewer.model;

import java.time.Instant;

import lombok.Data;

/**
 * Classe de base représentant un paquet de données de streaming.
 * 
 * @author rbaudu
 */
@Data
public abstract class StreamPacket {
    
    /**
     * Identifiant unique du paquet.
     */
    private String id;
    
    /**
     * Horodatage de création du paquet.
     */
    private Instant timestamp;
    
    /**
     * Source du paquet (par exemple, "video", "audio").
     */
    private String source;
    
    /**
     * Type de données du paquet.
     */
    private String dataType;
    
    /**
     * Numéro de séquence du paquet.
     */
    private long sequenceNumber;
    
    /**
     * Indicateur si ce paquet est une keyframe (pour la vidéo).
     */
    private boolean keyFrame;
    
    public StreamPacket() {
        this.timestamp = Instant.now();
    }
}

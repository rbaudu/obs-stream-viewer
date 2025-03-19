package com.rbaudu.obsstreamviewer.model;

import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;

import lombok.Data;

/**
 * Classe représentant un flux média synchronisé contenant à la fois 
 * des paquets vidéo et audio.
 * 
 * @author rbaudu
 */
@Data
public class SyncedMediaStream {
    
    /**
     * Identifiant unique du flux.
     */
    private String id;
    
    /**
     * Nom du flux.
     */
    private String name;
    
    /**
     * Horodatage de création du flux.
     */
    private Instant createdAt;
    
    /**
     * Horodatage de la dernière mise à jour du flux.
     */
    private Instant updatedAt;
    
    /**
     * File d'attente des paquets vidéo.
     */
    private Queue<VideoPacket> videoPackets = new LinkedList<>();
    
    /**
     * File d'attente des paquets audio.
     */
    private Queue<AudioPacket> audioPackets = new LinkedList<>();
    
    /**
     * Décalage de synchronisation en millisecondes.
     * Valeur positive : l'audio est en avance sur la vidéo.
     * Valeur négative : la vidéo est en avance sur l'audio.
     */
    private long syncOffsetMs = 0;
    
    /**
     * Indicateur de flux actif.
     */
    private boolean active = true;
    
    /**
     * Largeur de la vidéo.
     */
    private int videoWidth;
    
    /**
     * Hauteur de la vidéo.
     */
    private int videoHeight;
    
    /**
     * Taux d'échantillonnage audio.
     */
    private int audioSampleRate;
    
    /**
     * Nombre de canaux audio.
     */
    private int audioChannels;
    
    public SyncedMediaStream() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    /**
     * Ajoute un paquet vidéo au flux.
     * 
     * @param packet Le paquet vidéo à ajouter
     */
    public void addVideoPacket(VideoPacket packet) {
        videoPackets.add(packet);
        this.updatedAt = Instant.now();
        
        // Si c'est le premier paquet, initialiser les propriétés vidéo
        if (videoWidth == 0 || videoHeight == 0) {
            videoWidth = packet.getWidth();
            videoHeight = packet.getHeight();
        }
    }
    
    /**
     * Ajoute un paquet audio au flux.
     * 
     * @param packet Le paquet audio à ajouter
     */
    public void addAudioPacket(AudioPacket packet) {
        audioPackets.add(packet);
        this.updatedAt = Instant.now();
        
        // Si c'est le premier paquet, initialiser les propriétés audio
        if (audioSampleRate == 0 || audioChannels == 0) {
            audioSampleRate = packet.getSampleRate();
            audioChannels = packet.getChannels();
        }
    }
    
    /**
     * Récupère le prochain paquet vidéo synchronisé avec le flux audio.
     * 
     * @return Le paquet vidéo ou null si aucun n'est disponible
     */
    public VideoPacket nextSyncedVideoPacket() {
        if (videoPackets.isEmpty()) {
            return null;
        }
        return videoPackets.poll();
    }
    
    /**
     * Récupère le prochain paquet audio synchronisé avec le flux vidéo.
     * 
     * @return Le paquet audio ou null si aucun n'est disponible
     */
    public AudioPacket nextSyncedAudioPacket() {
        if (audioPackets.isEmpty()) {
            return null;
        }
        return audioPackets.poll();
    }
}

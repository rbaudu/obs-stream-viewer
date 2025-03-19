package com.rbaudu.obsstreamviewer.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.rbaudu.obsstreamviewer.config.ObsProperties;
import com.rbaudu.obsstreamviewer.model.AudioPacket;
import com.rbaudu.obsstreamviewer.model.SyncedMediaStream;
import com.rbaudu.obsstreamviewer.model.VideoPacket;

import lombok.extern.slf4j.Slf4j;

/**
 * Service responsable de la synchronisation des flux audio et vidéo.
 * 
 * @author rbaudu
 */
@Service
@Slf4j
public class StreamSynchronizer {
    
    /**
     * Destination WebSocket pour les flux synchronisés.
     */
    private static final String SYNCED_STREAM_DESTINATION = "/topic/stream/synced";
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private ObsProperties obsProperties;
    
    /**
     * Map des flux médias synchronisés actifs.
     */
    private Map<String, SyncedMediaStream> activeStreams = new HashMap<>();
    
    /**
     * Flux média par défaut.
     */
    private SyncedMediaStream defaultStream;
    
    /**
     * Initialisation du service.
     */
    public void init() {
        // Créer un flux par défaut
        defaultStream = new SyncedMediaStream();
        defaultStream.setId(UUID.randomUUID().toString());
        defaultStream.setName("Default Stream");
        
        activeStreams.put(defaultStream.getId(), defaultStream);
        
        log.info("StreamSynchronizer initialized with default stream ID: {}", defaultStream.getId());
    }
    
    /**
     * Traite un paquet vidéo entrant.
     * 
     * @param videoPacket Le paquet vidéo à traiter
     */
    public void processVideoPacket(VideoPacket videoPacket) {
        if (defaultStream == null) {
            init();
        }
        
        defaultStream.addVideoPacket(videoPacket);
        log.debug("Added video packet to stream {}, queue size: {}", 
                defaultStream.getId(), defaultStream.getVideoPackets().size());
    }
    
    /**
     * Traite un paquet audio entrant.
     * 
     * @param audioPacket Le paquet audio à traiter
     */
    public void processAudioPacket(AudioPacket audioPacket) {
        if (defaultStream == null) {
            init();
        }
        
        defaultStream.addAudioPacket(audioPacket);
        log.debug("Added audio packet to stream {}, queue size: {}", 
                defaultStream.getId(), defaultStream.getAudioPackets().size());
    }
    
    /**
     * Tâche planifiée pour synchroniser et envoyer les flux aux clients.
     * S'exécute toutes les 33 ms (environ 30 fps).
     */
    @Scheduled(fixedRate = 33)
    public void synchronizeAndSendStreams() {
        if (defaultStream == null || !defaultStream.isActive()) {
            return;
        }
        
        // Récupérer les paquets synchronisés
        VideoPacket videoPacket = defaultStream.nextSyncedVideoPacket();
        AudioPacket audioPacket = defaultStream.nextSyncedAudioPacket();
        
        // Envoyer les paquets si disponibles
        if (videoPacket != null) {
            messagingTemplate.convertAndSend(SYNCED_STREAM_DESTINATION + "/video", videoPacket);
        }
        
        if (audioPacket != null) {
            messagingTemplate.convertAndSend(SYNCED_STREAM_DESTINATION + "/audio", audioPacket);
        }
    }
    
    /**
     * Récupère le flux par défaut.
     * 
     * @return Le flux par défaut
     */
    public SyncedMediaStream getDefaultStream() {
        if (defaultStream == null) {
            init();
        }
        return defaultStream;
    }
    
    /**
     * Récupère un flux par son ID.
     * 
     * @param streamId L'ID du flux
     * @return Le flux ou null s'il n'existe pas
     */
    public SyncedMediaStream getStream(String streamId) {
        return activeStreams.get(streamId);
    }
    
    /**
     * Ajuste le décalage de synchronisation pour un flux.
     * 
     * @param streamId L'ID du flux
     * @param offsetMs Le décalage en millisecondes
     * @return true si l'ajustement a réussi, false sinon
     */
    public boolean adjustSyncOffset(String streamId, long offsetMs) {
        SyncedMediaStream stream = activeStreams.get(streamId);
        if (stream == null) {
            return false;
        }
        
        stream.setSyncOffsetMs(offsetMs);
        log.info("Adjusted sync offset for stream {} to {} ms", streamId, offsetMs);
        return true;
    }
}

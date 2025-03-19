package com.rbaudu.obsstreamviewer.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rbaudu.obsstreamviewer.model.SyncedMediaStream;
import com.rbaudu.obsstreamviewer.service.ObsStreamCapture;
import com.rbaudu.obsstreamviewer.service.StreamSynchronizer;

/**
 * Contrôleur REST pour gérer les opérations liées aux flux.
 * 
 * @author rbaudu
 */
@RestController
@RequestMapping("/api/stream")
public class StreamController {
    
    @Autowired
    private StreamSynchronizer streamSynchronizer;
    
    @Autowired
    private ObsStreamCapture obsStreamCapture;
    
    /**
     * Récupère les informations sur le flux par défaut.
     * 
     * @return Informations sur le flux par défaut
     */
    @GetMapping("/default")
    public ResponseEntity<Map<String, Object>> getDefaultStreamInfo() {
        SyncedMediaStream stream = streamSynchronizer.getDefaultStream();
        return ResponseEntity.ok(createStreamInfoMap(stream));
    }
    
    /**
     * Récupère les informations sur un flux spécifique.
     * 
     * @param streamId ID du flux
     * @return Informations sur le flux
     */
    @GetMapping("/{streamId}")
    public ResponseEntity<Map<String, Object>> getStreamInfo(@PathVariable String streamId) {
        SyncedMediaStream stream = streamSynchronizer.getStream(streamId);
        
        if (stream == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(createStreamInfoMap(stream));
    }
    
    /**
     * Ajuste le décalage de synchronisation pour un flux.
     * 
     * @param streamId ID du flux
     * @param offsetMs Décalage en millisecondes
     * @return Résultat de l'opération
     */
    @PostMapping("/{streamId}/sync")
    public ResponseEntity<Map<String, Object>> adjustSyncOffset(
            @PathVariable String streamId,
            @RequestParam long offsetMs) {
        
        boolean success = streamSynchronizer.adjustSyncOffset(streamId, offsetMs);
        
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Décalage de synchronisation ajusté à " + offsetMs + " ms");
        response.put("streamId", streamId);
        response.put("syncOffsetMs", offsetMs);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Démarre la capture des flux.
     * 
     * @return Résultat de l'opération
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startCapture() {
        obsStreamCapture.start();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Capture des flux démarrée");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Arrête la capture des flux.
     * 
     * @return Résultat de l'opération
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopCapture() {
        obsStreamCapture.stop();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Capture des flux arrêtée");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Crée une map d'informations pour un flux.
     * 
     * @param stream Le flux
     * @return Map d'informations
     */
    private Map<String, Object> createStreamInfoMap(SyncedMediaStream stream) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", stream.getId());
        info.put("name", stream.getName());
        info.put("active", stream.isActive());
        info.put("createdAt", stream.getCreatedAt());
        info.put("updatedAt", stream.getUpdatedAt());
        info.put("syncOffsetMs", stream.getSyncOffsetMs());
        
        Map<String, Object> video = new HashMap<>();
        video.put("width", stream.getVideoWidth());
        video.put("height", stream.getVideoHeight());
        video.put("queueSize", stream.getVideoPackets().size());
        
        Map<String, Object> audio = new HashMap<>();
        audio.put("sampleRate", stream.getAudioSampleRate());
        audio.put("channels", stream.getAudioChannels());
        audio.put("queueSize", stream.getAudioPackets().size());
        
        info.put("video", video);
        info.put("audio", audio);
        
        return info;
    }
}

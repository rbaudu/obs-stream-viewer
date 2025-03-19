package com.rbaudu.obsstreamviewer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbaudu.obsstreamviewer.config.ObsProperties;
import io.obswebsocket.community.client.OBSRemoteController;
import io.obswebsocket.community.client.OBSRemoteControllerBuilder;
import io.obswebsocket.community.client.listener.lifecycle.ReasonThrowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service pour gérer les communications WebSocket avec OBS Studio 
 * utilisant le protocole WebSocket 5.x via la bibliothèque officielle community.
 * 
 * @author rbaudu
 */
@Service
@Slf4j
public class ObsWebSocketService {

    @Autowired
    private ObsProperties obsProperties;

    @Autowired
    private StreamSynchronizer streamSynchronizer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OBSRemoteController obsRemoteController;
    private boolean connected = false;
    
    private final Map<String, Consumer<JsonNode>> eventHandlers = new ConcurrentHashMap<>();

    /**
     * Initialise le service après l'injection des dépendances.
     */
    @PostConstruct
    public void init() {
        // Les handlers d'événements peuvent être initialisés ici
        registerEventHandlers();
    }

    /**
     * Libération des ressources à la fermeture.
     */
    @PreDestroy
    public void cleanup() {
        disconnect();
    }

    /**
     * Connecte au serveur WebSocket d'OBS Studio.
     *
     * @return true si la connexion est établie avec succès
     */
    public boolean connect() {
        if (connected && obsRemoteController != null) {
            return true;
        }

        try {
            String host = obsProperties.getConnection().getHost();
            int port = obsProperties.getConnection().getWebsocketPort();
            String password = obsProperties.getConnection().getPassword();

            OBSRemoteControllerBuilder builder = OBSRemoteControllerBuilder.builder()
                .host(host)
                .port(port)
                .lifecycle()
                .withControllerLifecycle(lifecycle -> lifecycle
                    .onConnectionAttempt(() -> log.info("Tentative de connexion à OBS WebSocket"))
                    .onConnectionEstablished(() -> {
                        log.info("Connexion établie avec OBS WebSocket");
                        connected = true;
                    })
                    .onDisconnect(reason -> {
                        log.info("Déconnexion d'OBS WebSocket: {}", reason.getMessage());
                        connected = false;
                    })
                    .onControllerError(error -> log.error("Erreur OBS WebSocket: {}", error.getMessage(), error))
                );

            // Ajouter le mot de passe si présent
            if (password != null && !password.isEmpty()) {
                builder = builder.password(password);
            }
            
            obsRemoteController = builder.build();
            
            // Connexion et attente du résultat
            obsRemoteController.connect();
            
            // Permettre un peu de temps pour la connexion
            Thread.sleep(1000);
            
            return connected;
        } catch (Exception e) {
            log.error("Erreur lors de la connexion à OBS WebSocket: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Déconnecte du serveur WebSocket d'OBS Studio.
     */
    public void disconnect() {
        if (obsRemoteController != null) {
            try {
                obsRemoteController.disconnect();
            } catch (Exception e) {
                log.error("Erreur lors de la déconnexion: {}", e.getMessage(), e);
            }
        }
        connected = false;
    }

    /**
     * Enregistre les gestionnaires d'événements pour les événements OBS.
     */
    private void registerEventHandlers() {
        if (obsRemoteController != null) {
            // Exemple d'abonnement à un événement spécifique
            obsRemoteController.registerEventListener("StreamStateChanged", event -> {
                try {
                    boolean outputActive = event.getEventData().get("outputActive").asBoolean();
                    log.info("État du flux OBS modifié: {}", outputActive ? "démarré" : "arrêté");
                    
                    // Mettre à jour le statut du streaming dans l'application
                    if (streamSynchronizer != null) {
                        // Appeler les méthodes correspondantes dans le synchroniseur
                    }
                } catch (Exception e) {
                    log.error("Erreur lors du traitement de l'événement StreamStateChanged: {}", e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Démarre le streaming dans OBS.
     *
     * @return Future contenant true si le démarrage a réussi
     */
    public CompletableFuture<Boolean> startStreaming() {
        if (!isConnected()) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.complete(false);
            return future;
        }

        Map<String, Object> requestFields = new HashMap<>();
        return obsRemoteController.sendRequest("StartStream", requestFields)
                .thenApply(response -> true)
                .exceptionally(ex -> {
                    log.error("Erreur lors du démarrage du streaming OBS: {}", ex.getMessage(), ex);
                    return false;
                });
    }

    /**
     * Arrête le streaming dans OBS.
     *
     * @return Future contenant true si l'arrêt a réussi
     */
    public CompletableFuture<Boolean> stopStreaming() {
        if (!isConnected()) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.complete(false);
            return future;
        }

        Map<String, Object> requestFields = new HashMap<>();
        return obsRemoteController.sendRequest("StopStream", requestFields)
                .thenApply(response -> true)
                .exceptionally(ex -> {
                    log.error("Erreur lors de l'arrêt du streaming OBS: {}", ex.getMessage(), ex);
                    return false;
                });
    }

    /**
     * Vérifie si le streaming est actif dans OBS.
     *
     * @return Future contenant true si le streaming est actif
     */
    public CompletableFuture<Boolean> isStreamingActive() {
        if (!isConnected()) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.complete(false);
            return future;
        }

        Map<String, Object> requestFields = new HashMap<>();
        return obsRemoteController.sendRequest("GetStreamStatus", requestFields)
                .thenApply(response -> {
                    try {
                        return response.getResponseData().get("outputActive").asBoolean();
                    } catch (Exception e) {
                        log.error("Erreur lors du traitement de la réponse GetStreamStatus: {}", e.getMessage(), e);
                        return false;
                    }
                })
                .exceptionally(ex -> {
                    log.error("Erreur lors de la vérification du statut de streaming OBS: {}", ex.getMessage(), ex);
                    return false;
                });
    }

    /**
     * Vérifie si la connexion au WebSocket OBS est établie.
     *
     * @return true si connecté
     */
    public boolean isConnected() {
        return connected && obsRemoteController != null;
    }
}

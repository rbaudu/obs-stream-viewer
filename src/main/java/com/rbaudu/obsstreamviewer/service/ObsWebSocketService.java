package com.rbaudu.obsstreamviewer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbaudu.obsstreamviewer.config.ObsProperties;
import io.obswebsocket.community.client.OBSRemoteController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
        // Les handlers d'événements peuvent être initialisés ici si nécessaire
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

            // Crée le builder avec les paramètres de connexion
            OBSRemoteController.Builder builder = OBSRemoteController.builder()
                .host(host)
                .port(port);

            // Ajouter le mot de passe si présent
            if (password != null && !password.isEmpty()) {
                builder.password(password);
            }
            
            // Enregistrer les listeners d'événements génériques
            builder.registerEventCallback("StreamStateChanged", eventData -> {
                try {
                    boolean outputActive = eventData.get("outputActive").asBoolean();
                    log.info("État du flux OBS modifié: {}", outputActive ? "démarré" : "arrêté");
                    
                    // Mettre à jour le statut du streaming dans l'application
                    if (streamSynchronizer != null) {
                        // Appeler les méthodes correspondantes dans le synchroniseur
                    }
                } catch (Exception e) {
                    log.error("Erreur lors du traitement de l'événement StreamStateChanged: {}", e.getMessage(), e);
                }
            });
            
            // Créer et démarrer le contrôleur OBS
            obsRemoteController = builder.build();
            
            // Définir les gestionnaires de cycle de vie
            CompletableFuture<Boolean> connectionFuture = new CompletableFuture<>();
            
            obsRemoteController.onConnectionEstablished(() -> {
                log.info("Connexion établie avec OBS WebSocket");
                connected = true;
                connectionFuture.complete(true);
            });
            
            obsRemoteController.onDisconnect((reason) -> {
                log.info("Déconnexion d'OBS WebSocket: {}", reason);
                connected = false;
            });
            
            obsRemoteController.onError((error) -> {
                log.error("Erreur OBS WebSocket: {}", error.getMessage(), error);
                if (!connectionFuture.isDone()) {
                    connectionFuture.complete(false);
                }
            });
            
            // Connexion au serveur OBS WebSocket
            obsRemoteController.connect();
            
            // Attendre la connexion (timeout après 5 secondes)
            try {
                return connectionFuture.get(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("Timeout lors de la connexion à OBS WebSocket", e);
                return false;
            }
            
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

        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        // Utiliser des requêtes génériques au lieu des classes spécifiques
        Map<String, Object> requestFields = new HashMap<>();
        
        obsRemoteController.sendRequest("StartStream", requestFields, response -> {
            resultFuture.complete(true);
        }).exceptionally(ex -> {
            log.error("Erreur lors du démarrage du streaming OBS: {}", ex.getMessage(), ex);
            resultFuture.complete(false);
            return null;
        });
        
        return resultFuture;
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

        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        // Utiliser des requêtes génériques au lieu des classes spécifiques
        Map<String, Object> requestFields = new HashMap<>();
        
        obsRemoteController.sendRequest("StopStream", requestFields, response -> {
            resultFuture.complete(true);
        }).exceptionally(ex -> {
            log.error("Erreur lors de l'arrêt du streaming OBS: {}", ex.getMessage(), ex);
            resultFuture.complete(false);
            return null;
        });
        
        return resultFuture;
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

        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        // Utiliser des requêtes génériques au lieu des classes spécifiques
        Map<String, Object> requestFields = new HashMap<>();
        
        obsRemoteController.sendRequest("GetStreamStatus", requestFields, response -> {
            try {
                boolean outputActive = response.getResponseData().get("outputActive").asBoolean();
                resultFuture.complete(outputActive);
            } catch (Exception e) {
                log.error("Erreur lors du traitement de la réponse GetStreamStatus: {}", e.getMessage(), e);
                resultFuture.complete(false);
            }
        }).exceptionally(ex -> {
            log.error("Erreur lors de la vérification du statut de streaming OBS: {}", ex.getMessage(), ex);
            resultFuture.complete(false);
            return null;
        });
        
        return resultFuture;
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

package com.rbaudu.obsstreamviewer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rbaudu.obsstreamviewer.config.ObsProperties;
import io.obswebsocket.community.client.OBSRemoteController;
import io.obswebsocket.community.client.OBSRemoteControllerBuilder;
import io.obswebsocket.community.client.message.event.Event;
import io.obswebsocket.community.client.message.request.stream.GetStreamStatusRequest;
import io.obswebsocket.community.client.message.request.stream.StartStreamRequest;
import io.obswebsocket.community.client.message.request.stream.StopStreamRequest;
import io.obswebsocket.community.client.message.response.stream.GetStreamStatusResponse;
import io.obswebsocket.community.client.message.response.stream.StartStreamResponse;
import io.obswebsocket.community.client.message.response.stream.StopStreamResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
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

            // Créer une nouvelle instance du builder
            OBSRemoteControllerBuilder builder = new OBSRemoteControllerBuilder()
                .host(host)
                .port(port);

            // Ajouter le mot de passe si présent
            if (password != null && !password.isEmpty()) {
                builder.password(password);
            }
            
            // Configurer les écouteurs de cycle de vie
            // La méthode withControllerLifecycle n'est pas disponible directement
            // Utilisons les méthodes disponibles individuellement
            builder.lifecycle()
                .onConnectionAttempt(() -> log.info("Tentative de connexion à OBS WebSocket"))
                .onControllerError(error -> log.error("Erreur OBS WebSocket: {}", error.getMessage(), error));
            
            // Enregistrer l'écouteur d'événements pour détecter les changements d'état du stream
            builder.registerEventListener(Event.class, event -> {
                if (event.getMessageData().getEventType().name().equals("StreamStateChanged")) {
                    try {
                        JsonNode eventData = (JsonNode) event.getMessageData().getEventData();
                        Boolean outputActive = eventData.get("outputActive").asBoolean();
                        log.info("État du flux OBS modifié: {}", outputActive ? "démarré" : "arrêté");
                    } catch (Exception e) {
                        log.error("Erreur lors du traitement de l'événement StreamStateChanged", e);
                    }
                }
            });
            
            // Créer le contrôleur OBS
            obsRemoteController = builder.build();
            
            // Ajouter des écouteurs post-construction sur le contrôleur
            obsRemoteController.onConnectionEstablished(() -> {
                log.info("Connexion établie avec OBS WebSocket");
                connected = true;
            });
            
            obsRemoteController.onDisconnect(reason -> {
                log.info("Déconnexion d'OBS WebSocket: {}", reason);
                connected = false;
            });
            
            // Se connecter
            obsRemoteController.connect();
            
            // Attendre un peu pour voir si la connexion s'établit
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
     * Démarre le streaming dans OBS.
     *
     * @return Future contenant true si le démarrage a réussi
     */
    public CompletableFuture<Boolean> startStreaming() {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        if (!isConnected()) {
            resultFuture.complete(false);
            return resultFuture;
        }

        try {
            // Utiliser le builder statique pour créer les requêtes
            StartStreamRequest request = StartStreamRequest.builder().build();
            
            obsRemoteController.sendRequest(request, (StartStreamResponse response) -> {
                resultFuture.complete(true);
            });
        } catch (Exception e) {
            log.error("Erreur lors du démarrage du streaming OBS: {}", e.getMessage(), e);
            resultFuture.complete(false);
        }
        
        return resultFuture;
    }

    /**
     * Arrête le streaming dans OBS.
     *
     * @return Future contenant true si l'arrêt a réussi
     */
    public CompletableFuture<Boolean> stopStreaming() {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        if (!isConnected()) {
            resultFuture.complete(false);
            return resultFuture;
        }

        try {
            // Utiliser le builder statique pour créer les requêtes
            StopStreamRequest request = StopStreamRequest.builder().build();
            
            obsRemoteController.sendRequest(request, (StopStreamResponse response) -> {
                resultFuture.complete(true);
            });
        } catch (Exception e) {
            log.error("Erreur lors de l'arrêt du streaming OBS: {}", e.getMessage(), e);
            resultFuture.complete(false);
        }
        
        return resultFuture;
    }

    /**
     * Vérifie si le streaming est actif dans OBS.
     *
     * @return Future contenant true si le streaming est actif
     */
    public CompletableFuture<Boolean> isStreamingActive() {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        if (!isConnected()) {
            resultFuture.complete(false);
            return resultFuture;
        }

        try {
            // Utiliser le builder statique pour créer les requêtes
            GetStreamStatusRequest request = GetStreamStatusRequest.builder().build();
            
            obsRemoteController.sendRequest(request, (GetStreamStatusResponse response) -> {
                resultFuture.complete(response.getOutputActive());
            });
        } catch (Exception e) {
            log.error("Erreur lors de la vérification du statut de streaming OBS: {}", e.getMessage(), e);
            resultFuture.complete(false);
        }
        
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

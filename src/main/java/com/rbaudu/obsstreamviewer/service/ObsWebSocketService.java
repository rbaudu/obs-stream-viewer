package com.rbaudu.obsstreamviewer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

            // Créer le builder avec les paramètres de connexion et configuration
            OBSRemoteControllerBuilder builder = OBSRemoteControllerBuilder.builder()
                .host(host)
                .port(port);

            // Ajouter le mot de passe si présent
            if (password != null && !password.isEmpty()) {
                builder.password(password);
            }
            
            // Configurer les écouteurs d'événements via la façade lifecycle
            builder.lifecycle()
                .withControllerLifecycle(controllerLifecycle -> controllerLifecycle
                    .onConnectionAttempt(() -> log.info("Tentative de connexion à OBS WebSocket"))
                    .onConnectionEstablished(() -> {
                        log.info("Connexion établie avec OBS WebSocket");
                        connected = true;
                    })
                    .onDisconnect(reason -> {
                        log.info("Déconnexion d'OBS WebSocket: {}", reason.getMessage());
                        connected = false;
                    })
                    .onControllerError(error -> 
                        log.error("Erreur OBS WebSocket: {}", error.getMessage(), error)
                    )
                );
            
            // Enregistrer l'écouteur pour l'événement StreamStateChanged
            builder.registerEventListener(Event.StreamStateChanged.class, event -> {
                Boolean outputActive = event.getEventData().get("outputActive").asBoolean();
                log.info("État du flux OBS modifié: {}", outputActive ? "démarré" : "arrêté");
            });
            
            // Créer le contrôleur OBS et se connecter
            obsRemoteController = builder.build();
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
            // Utiliser la méthode correcte avec le type concret de requête
            obsRemoteController.sendRequest(new StartStreamRequest(), (StartStreamResponse response) -> {
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
            // Utiliser la méthode correcte avec le type concret de requête
            obsRemoteController.sendRequest(new StopStreamRequest(), (StopStreamResponse response) -> {
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
            // Utiliser la méthode correcte avec le type concret de requête
            obsRemoteController.sendRequest(new GetStreamStatusRequest(), (GetStreamStatusResponse response) -> {
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

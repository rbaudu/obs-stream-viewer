package com.rbaudu.obsstreamviewer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbaudu.obsstreamviewer.config.ObsProperties;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service pour gérer les communications WebSocket avec OBS Studio 
 * utilisant le protocole WebSocket 5.x.
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
    private WebSocketClient wsClient;
    private boolean connected = false;
    private String sessionId;
    
    private final Map<String, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonNode>> eventHandlers = new ConcurrentHashMap<>();
    
    private int messageId = 1;

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
        if (connected && wsClient != null && wsClient.isOpen()) {
            return true;
        }

        try {
            String host = obsProperties.getConnection().getHost();
            int port = obsProperties.getConnection().getWebsocketPort();
            URI serverUri = new URI("ws://" + host + ":" + port);

            wsClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("Connexion établie avec OBS WebSocket");
                    authenticateIfNeeded();
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("Connexion fermée: code={}, raison={}, distant={}", code, reason, remote);
                    connected = false;
                }

                @Override
                public void onError(Exception ex) {
                    log.error("Erreur WebSocket: {}", ex.getMessage(), ex);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    // Gérer les messages binaires si nécessaire
                }
            };

            boolean success = wsClient.connectBlocking(5, TimeUnit.SECONDS);
            
            if (success) {
                connected = true;
                return true;
            } else {
                log.error("Échec de la connexion à OBS WebSocket");
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
        if (wsClient != null && wsClient.isOpen()) {
            try {
                wsClient.closeBlocking();
            } catch (InterruptedException e) {
                log.error("Erreur lors de la déconnexion: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }
        connected = false;
        pendingRequests.clear();
    }

    /**
     * Envoie une demande à OBS et attend la réponse.
     *
     * @param requestType Type de demande (comme défini dans l'API OBS WebSocket 5.x)
     * @param data Données supplémentaires pour la demande (peut être null)
     * @return Future contenant la réponse JSON, ou exceptionnellement en cas d'erreur
     */
    public CompletableFuture<JsonNode> sendRequest(String requestType, Map<String, Object> data) {
        if (!connected || wsClient == null || !wsClient.isOpen()) {
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Non connecté à OBS WebSocket"));
            return future;
        }

        String messageId = String.valueOf(this.messageId++);
        CompletableFuture<JsonNode> responseFuture = new CompletableFuture<>();
        pendingRequests.put(messageId, responseFuture);

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("op", 6); // Request operation code for WebSocket 5.x
            request.put("d", new HashMap<String, Object>() {{
                put("requestType", requestType);
                put("requestId", messageId);
                if (data != null) {
                    put("requestData", data);
                }
            }});

            String requestJson = objectMapper.writeValueAsString(request);
            wsClient.send(requestJson);
            
            // Définir un timeout pour la demande
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(10000); // 10 secondes timeout
                    CompletableFuture<JsonNode> future = pendingRequests.remove(messageId);
                    if (future != null && !future.isDone()) {
                        future.completeExceptionally(new RuntimeException("Timeout de la demande"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            return responseFuture;
        } catch (Exception e) {
            pendingRequests.remove(messageId);
            responseFuture.completeExceptionally(e);
            return responseFuture;
        }
    }

    /**
     * Gère les messages reçus du serveur WebSocket OBS.
     *
     * @param message Le message JSON reçu
     */
    private void handleMessage(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            int opCode = jsonNode.get("op").asInt();
            JsonNode data = jsonNode.get("d");

            switch (opCode) {
                case 0: // Hello
                    handleHello(data);
                    break;
                case 2: // Identified
                    connected = true;
                    log.info("Authentification réussie auprès d'OBS WebSocket");
                    break;
                case 5: // Event
                    handleEvent(data);
                    break;
                case 7: // RequestResponse
                    handleRequestResponse(data);
                    break;
                default:
                    log.debug("Message WebSocket non géré: {}", message);
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement du message WebSocket: {}", e.getMessage(), e);
        }
    }

    /**
     * Gère le message Hello reçu lors de la connexion initiale.
     *
     * @param data Données du message Hello
     */
    private void handleHello(JsonNode data) {
        try {
            this.sessionId = data.get("sessionId").asText();
            boolean authRequired = data.get("authentication").get("required").asBoolean();
            
            if (authRequired) {
                String challenge = data.get("authentication").get("challenge").asText();
                String salt = data.get("authentication").get("salt").asText();
                
                String password = obsProperties.getConnection().getPassword();
                if (password == null || password.isEmpty()) {
                    log.error("Authentification requise mais aucun mot de passe n'est configuré");
                    disconnect();
                    return;
                }
                
                String authResponse = calculateAuthResponse(password, salt, challenge);
                sendIdentify(authResponse);
            } else {
                sendIdentify(null);
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement du message Hello: {}", e.getMessage(), e);
        }
    }

    /**
     * Calcule la réponse d'authentification selon le protocole WebSocket 5.x d'OBS.
     *
     * @param password Mot de passe configuré
     * @param salt Sel d'authentification reçu
     * @param challenge Challenge d'authentification reçu
     * @return Réponse d'authentification
     */
    private String calculateAuthResponse(String password, String salt, String challenge) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        // Première étape: secret_string = password + salt (base64 decoded)
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        byte[] passwordBytes = password.getBytes();
        
        byte[] secretString = new byte[passwordBytes.length + saltBytes.length];
        System.arraycopy(passwordBytes, 0, secretString, 0, passwordBytes.length);
        System.arraycopy(saltBytes, 0, secretString, passwordBytes.length, saltBytes.length);
        
        // Deuxième étape: secret_hash = SHA256(secret_string)
        byte[] secretHash = digest.digest(secretString);
        
        // Troisième étape: auth_response = SHA256(secret_hash + challenge (base64 decoded))
        byte[] challengeBytes = Base64.getDecoder().decode(challenge);
        
        byte[] authString = new byte[secretHash.length + challengeBytes.length];
        System.arraycopy(secretHash, 0, authString, 0, secretHash.length);
        System.arraycopy(challengeBytes, 0, authString, secretHash.length, challengeBytes.length);
        
        byte[] authHash = digest.digest(authString);
        
        // Quatrième étape: auth_response (base64 encoded)
        return Base64.getEncoder().encodeToString(authHash);
    }

    /**
     * Envoie le message d'identification à OBS.
     *
     * @param authResponse Réponse d'authentification (ou null si non requise)
     */
    private void sendIdentify(String authResponse) {
        try {
            Map<String, Object> identify = new HashMap<>();
            identify.put("op", 1); // Identify operation code for WebSocket 5.x
            
            Map<String, Object> d = new HashMap<>();
            d.put("rpcVersion", 1);
            
            if (authResponse != null) {
                d.put("authentication", authResponse);
            }
            identify.put("d", d);
            
            String identifyJson = objectMapper.writeValueAsString(identify);
            wsClient.send(identifyJson);
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi du message d'identification: {}", e.getMessage(), e);
        }
    }

    /**
     * Gère un événement reçu d'OBS.
     *
     * @param data Données de l'événement
     */
    private void handleEvent(JsonNode data) {
        try {
            String eventType = data.get("eventType").asText();
            JsonNode eventData = data.get("eventData");
            
            log.debug("Événement OBS reçu: {}", eventType);
            
            Consumer<JsonNode> handler = eventHandlers.get(eventType);
            if (handler != null) {
                handler.accept(eventData);
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement de l'événement: {}", e.getMessage(), e);
        }
    }

    /**
     * Gère une réponse à une demande précédente.
     *
     * @param data Données de la réponse
     */
    private void handleRequestResponse(JsonNode data) {
        try {
            String requestId = data.get("requestId").asText();
            String requestStatus = data.get("requestStatus").get("result").asText();
            
            CompletableFuture<JsonNode> future = pendingRequests.remove(requestId);
            
            if (future != null) {
                if ("success".equals(requestStatus)) {
                    JsonNode responseData = data.get("responseData");
                    future.complete(responseData != null ? responseData : objectMapper.createObjectNode());
                } else {
                    String errorMessage = data.get("requestStatus").get("comment").asText();
                    future.completeExceptionally(new RuntimeException("Échec de la demande: " + errorMessage));
                }
            } else {
                log.warn("Réponse reçue pour une demande inconnue: {}", requestId);
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement de la réponse à la demande: {}", e.getMessage(), e);
        }
    }

    /**
     * Vérifie si une authentification est nécessaire et l'effectue.
     */
    private void authenticateIfNeeded() {
        // L'authentification est gérée dans handleHello
    }

    /**
     * Enregistre les gestionnaires d'événements pour les événements OBS.
     */
    private void registerEventHandlers() {
        // Exemple d'enregistrement d'un gestionnaire d'événements
        eventHandlers.put("StreamStateChanged", data -> {
            try {
                boolean outputActive = data.get("outputActive").asBoolean();
                log.info("État du flux OBS modifié: {}", outputActive ? "démarré" : "arrêté");
                
                // Mettre à jour le statut du streaming dans l'application
                if (streamSynchronizer != null) {
                    // Appeler les méthodes correspondantes dans le synchroniseur
                }
            } catch (Exception e) {
                log.error("Erreur lors du traitement de l'événement StreamStateChanged: {}", e.getMessage(), e);
            }
        });
        
        // Ajouter d'autres gestionnaires d'événements selon les besoins
    }

    /**
     * Démarre le streaming dans OBS.
     *
     * @return Future contenant true si le démarrage a réussi
     */
    public CompletableFuture<Boolean> startStreaming() {
        Map<String, Object> data = new HashMap<>();
        return sendRequest("StartStream", data)
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
        return sendRequest("StopStream", null)
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
        return sendRequest("GetStreamStatus", null)
                .thenApply(response -> response.get("outputActive").asBoolean())
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
        return connected && wsClient != null && wsClient.isOpen();
    }
}

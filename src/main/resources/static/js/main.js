/**
 * Script JavaScript principal pour l'application OBS Stream Viewer
 * Ce fichier contient les fonctions communes utilisées sur toutes les pages
 */

// Configuration globale
const config = {
    // URL de base pour les appels API
    apiBaseUrl: '/api',
    
    // Points de terminaison WebSocket
    wsEndpoints: {
        base: '/obs-websocket',
        videoTopic: '/topic/stream/synced/video',
        audioTopic: '/topic/stream/synced/audio'
    },
    
    // Configuration par défaut du lecteur média
    player: {
        videoMuted: true,
        syncOffset: 0
    }
};

// Objets globaux
let stompClient = null;
let videoConnection = null;
let audioConnection = null;

/**
 * Initialise la connexion WebSocket STOMP
 * 
 * @param {Function} onConnectCallback Fonction de rappel exécutée après connexion
 * @param {Function} onErrorCallback Fonction de rappel exécutée en cas d'erreur
 */
function initializeWebSocketConnection(onConnectCallback, onErrorCallback) {
    // Création d'une connexion SockJS
    const socket = new SockJS(config.wsEndpoints.base);
    
    // Création du client STOMP
    stompClient = Stomp.over(socket);
    
    // Désactiver les logs
    stompClient.debug = null;
    
    // Connexion au serveur
    stompClient.connect({}, frame => {
        console.log('Connecté au serveur WebSocket');
        
        if (typeof onConnectCallback === 'function') {
            onConnectCallback(frame);
        }
    }, error => {
        console.error('Erreur de connexion au serveur WebSocket:', error);
        
        if (typeof onErrorCallback === 'function') {
            onErrorCallback(error);
        }
    });
}

/**
 * S'abonne aux sujets WebSocket pour recevoir les flux vidéo et audio
 * 
 * @param {Function} onVideoMessage Fonction de rappel pour les messages vidéo
 * @param {Function} onAudioMessage Fonction de rappel pour les messages audio
 */
function subscribeToStreams(onVideoMessage, onAudioMessage) {
    if (!stompClient || !stompClient.connected) {
        console.error('Client WebSocket non connecté');
        return;
    }
    
    // S'abonner au flux vidéo
    if (typeof onVideoMessage === 'function') {
        videoConnection = stompClient.subscribe(config.wsEndpoints.videoTopic, message => {
            try {
                const videoPacket = JSON.parse(message.body);
                onVideoMessage(videoPacket);
            } catch (error) {
                console.error('Erreur lors du traitement du paquet vidéo:', error);
            }
        });
    }
    
    // S'abonner au flux audio
    if (typeof onAudioMessage === 'function') {
        audioConnection = stompClient.subscribe(config.wsEndpoints.audioTopic, message => {
            try {
                const audioPacket = JSON.parse(message.body);
                onAudioMessage(audioPacket);
            } catch (error) {
                console.error('Erreur lors du traitement du paquet audio:', error);
            }
        });
    }
}

/**
 * Se déconnecte des sujets WebSocket
 */
function unsubscribeFromStreams() {
    if (videoConnection) {
        videoConnection.unsubscribe();
        videoConnection = null;
    }
    
    if (audioConnection) {
        audioConnection.unsubscribe();
        audioConnection = null;
    }
}

/**
 * Déconnecte le client WebSocket
 */
function disconnectWebSocket() {
    if (stompClient && stompClient.connected) {
        unsubscribeFromStreams();
        stompClient.disconnect();
        stompClient = null;
    }
}

/**
 * Effectue une requête API
 * 
 * @param {string} endpoint Point de terminaison API
 * @param {string} method Méthode HTTP (GET, POST, etc.)
 * @param {Object} data Données à envoyer (pour POST, PUT)
 * @returns {Promise} Promesse résolue avec les données de réponse
 */
async function apiRequest(endpoint, method = 'GET', data = null) {
    const url = `${config.apiBaseUrl}${endpoint}`;
    const options = {
        method: method,
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        }
    };
    
    if (data && (method === 'POST' || method === 'PUT')) {
        options.body = JSON.stringify(data);
    }
    
    try {
        const response = await fetch(url, options);
        
        if (!response.ok) {
            throw new Error(`Erreur HTTP: ${response.status} ${response.statusText}`);
        }
        
        return await response.json();
    } catch (error) {
        console.error(`Erreur lors de la requête API ${method} ${url}:`, error);
        throw error;
    }
}

/**
 * Démarre la capture du flux
 * 
 * @returns {Promise} Promesse résolue avec la réponse API
 */
function startStreamCapture() {
    return apiRequest('/stream/start', 'POST');
}

/**
 * Arrête la capture du flux
 * 
 * @returns {Promise} Promesse résolue avec la réponse API
 */
function stopStreamCapture() {
    return apiRequest('/stream/stop', 'POST');
}

/**
 * Ajuste le décalage de synchronisation pour un flux
 * 
 * @param {string} streamId ID du flux
 * @param {number} offsetMs Décalage en millisecondes
 * @returns {Promise} Promesse résolue avec la réponse API
 */
function adjustSyncOffset(streamId, offsetMs) {
    return apiRequest(`/stream/${streamId}/sync?offsetMs=${offsetMs}`, 'POST');
}

/**
 * Récupère les informations sur un flux
 * 
 * @param {string} streamId ID du flux (ou 'default' pour le flux par défaut)
 * @returns {Promise} Promesse résolue avec les informations sur le flux
 */
function getStreamInfo(streamId = 'default') {
    const endpoint = streamId === 'default' ? '/stream/default' : `/stream/${streamId}`;
    return apiRequest(endpoint);
}

/**
 * Affiche un message de notification
 * 
 * @param {string} message Message à afficher
 * @param {string} type Type de message (success, danger, warning, info)
 * @param {number} duration Durée d'affichage en ms (0 pour persistant)
 */
function showNotification(message, type = 'info', duration = 3000) {
    // Créer un élément pour la notification
    const notification = document.createElement('div');
    notification.className = `alert alert-${type} alert-dismissible fade show notification-toast`;
    notification.role = 'alert';
    
    // Contenu de la notification
    notification.innerHTML = `
        <span>${message}</span>
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Fermer"></button>
    `;
    
    // Ajouter au conteneur des notifications
    let container = document.querySelector('.notification-container');
    
    // Créer le conteneur s'il n'existe pas
    if (!container) {
        container = document.createElement('div');
        container.className = 'notification-container';
        document.body.appendChild(container);
    }
    
    // Ajouter la notification
    container.appendChild(notification);
    
    // Supprimer automatiquement après la durée spécifiée
    if (duration > 0) {
        setTimeout(() => {
            notification.classList.remove('show');
            setTimeout(() => {
                notification.remove();
            }, 300);
        }, duration);
    }
}

// Exécuter lors du chargement du document
document.addEventListener('DOMContentLoaded', () => {
    // Code commun à toutes les pages
    console.log('OBS Stream Viewer initialized');
});

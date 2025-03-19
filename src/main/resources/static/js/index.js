/**
 * Script JavaScript spécifique à la page d'accueil (index.html)
 * Gère la connexion aux flux vidéo et audio, leur affichage et leur contrôle
 */

// Éléments DOM
const videoElement = document.getElementById('videoPlayer');
const videoLoadingElement = document.getElementById('videoLoading');
const startStreamButton = document.getElementById('startStreamBtn');
const stopStreamButton = document.getElementById('stopStreamBtn');
const muteAudioCheckbox = document.getElementById('muteAudio');
const syncOffsetSlider = document.getElementById('syncOffset');
const syncOffsetValueElement = document.getElementById('syncOffsetValue');

// Variables pour la gestion du flux
let streamActive = false;
let videoBuffer = [];
let audioBuffer = [];
let mediaSource = null;
let videoSourceBuffer = null;
let audioSourceBuffer = null;
let streamInfo = null;

// Initialisation au chargement de la page
document.addEventListener('DOMContentLoaded', () => {
    // Vérifier si MediaSource est supporté
    if (!window.MediaSource) {
        showNotification('Votre navigateur ne supporte pas l\'API MediaSource nécessaire pour afficher les flux.', 'danger', 0);
        disableStreamControls();
        return;
    }
    
    // Initialiser les contrôles
    initializeControls();
    
    // Récupérer les informations sur le flux par défaut
    getStreamInfo()
        .then(info => {
            streamInfo = info;
            updateStreamInfoDisplay(info);
        })
        .catch(error => {
            console.error('Erreur lors de la récupération des informations du flux:', error);
            showNotification('Impossible de récupérer les informations du flux.', 'warning');
        });
});

/**
 * Initialise les contrôles de la page
 */
function initializeControls() {
    // Gérer le bouton de démarrage
    startStreamButton.addEventListener('click', () => {
        startStream();
    });
    
    // Gérer le bouton d'arrêt
    stopStreamButton.addEventListener('click', () => {
        stopStream();
    });
    
    // Gérer le contrôle du son
    muteAudioCheckbox.addEventListener('change', () => {
        videoElement.muted = muteAudioCheckbox.checked;
    });
    
    // Gérer le curseur de synchronisation
    syncOffsetSlider.addEventListener('input', () => {
        const offsetValue = parseInt(syncOffsetSlider.value);
        syncOffsetValueElement.textContent = offsetValue;
        
        // Si le flux est actif, mettre à jour le décalage de synchronisation
        if (streamActive && streamInfo) {
            adjustSyncOffset(streamInfo.id, offsetValue)
                .catch(error => {
                    console.error('Erreur lors de l\'ajustement du décalage de synchronisation:', error);
                });
        }
    });
}

/**
 * Démarre la capture et l'affichage du flux
 */
function startStream() {
    if (streamActive) {
        return;
    }
    
    // Afficher le chargement
    videoLoadingElement.style.display = 'flex';
    
    // Démarrer la capture côté serveur
    startStreamCapture()
        .then(() => {
            // Initialiser la connexion WebSocket
            initializeWebSocketConnection(() => {
                streamActive = true;
                
                // Configurer le lecteur média
                setupMediaPlayer();
                
                // S'abonner aux flux vidéo et audio
                subscribeToStreams(handleVideoPacket, handleAudioPacket);
                
                // Mettre à jour l'interface
                updateUIForActiveStream();
                
                showNotification('Flux démarré avec succès', 'success');
            }, error => {
                showNotification('Erreur de connexion au serveur WebSocket', 'danger');
                videoLoadingElement.style.display = 'none';
            });
        })
        .catch(error => {
            showNotification('Erreur lors du démarrage du flux', 'danger');
            videoLoadingElement.style.display = 'none';
        });
}

/**
 * Arrête la capture et l'affichage du flux
 */
function stopStream() {
    if (!streamActive) {
        return;
    }
    
    // Arrêter la capture côté serveur
    stopStreamCapture()
        .then(() => {
            // Se déconnecter du WebSocket
            disconnectWebSocket();
            
            // Réinitialiser le lecteur média
            resetMediaPlayer();
            
            // Mettre à jour l'interface
            updateUIForInactiveStream();
            
            streamActive = false;
            
            showNotification('Flux arrêté', 'info');
        })
        .catch(error => {
            showNotification('Erreur lors de l\'arrêt du flux', 'warning');
        });
}

/**
 * Configure le lecteur média pour l'affichage des flux
 */
function setupMediaPlayer() {
    // Créer un nouvel objet MediaSource
    mediaSource = new MediaSource();
    videoElement.src = URL.createObjectURL(mediaSource);
    
    // Configurer les tampons de source lors de l'ouverture du MediaSource
    mediaSource.addEventListener('sourceopen', () => {
        try {
            // Créer le tampon vidéo
            videoSourceBuffer = mediaSource.addSourceBuffer('video/mp4; codecs="avc1.42E01E"');
            
            // Créer le tampon audio
            audioSourceBuffer = mediaSource.addSourceBuffer('audio/mp4; codecs="mp4a.40.2"');
            
            // Masquer le chargement
            videoLoadingElement.style.display = 'none';
        } catch (error) {
            console.error('Erreur lors de la configuration du lecteur média:', error);
            showNotification('Erreur lors de la configuration du lecteur média', 'danger');
            videoLoadingElement.style.display = 'none';
        }
    });
}

/**
 * Réinitialise le lecteur média
 */
function resetMediaPlayer() {
    if (videoElement) {
        videoElement.pause();
        videoElement.removeAttribute('src');
        videoElement.load();
    }
    
    mediaSource = null;
    videoSourceBuffer = null;
    audioSourceBuffer = null;
    videoBuffer = [];
    audioBuffer = [];
}

/**
 * Gère la réception d'un paquet vidéo depuis le WebSocket
 * 
 * @param {Object} packet Le paquet vidéo reçu
 */
function handleVideoPacket(packet) {
    if (!streamActive || !videoSourceBuffer) {
        return;
    }
    
    try {
        // Décoder les données vidéo depuis Base64
        const videoData = atob(packet.frameData);
        
        // Ajouter au tampon (implémentation simplifiée)
        // Dans une implémentation réelle, il faudrait gérer correctement le format vidéo
        
        // Mettre à jour les informations d'affichage
        document.getElementById('videoResolution').textContent = `${packet.width}x${packet.height}`;
        document.getElementById('videoFormat').textContent = packet.pixelFormat || '-';
        document.getElementById('videoFramerate').textContent = '30'; // À remplacer par la valeur réelle
    } catch (error) {
        console.error('Erreur lors du traitement du paquet vidéo:', error);
    }
}

/**
 * Gère la réception d'un paquet audio depuis le WebSocket
 * 
 * @param {Object} packet Le paquet audio reçu
 */
function handleAudioPacket(packet) {
    if (!streamActive || !audioSourceBuffer) {
        return;
    }
    
    try {
        // Décoder les données audio depuis Base64
        const audioData = atob(packet.audioData);
        
        // Ajouter au tampon (implémentation simplifiée)
        // Dans une implémentation réelle, il faudrait gérer correctement le format audio
        
        // Mettre à jour les informations d'affichage
        document.getElementById('audioSampleRate').textContent = packet.sampleRate;
        document.getElementById('audioChannels').textContent = packet.channels;
        document.getElementById('audioFormat').textContent = packet.audioFormat || '-';
    } catch (error) {
        console.error('Erreur lors du traitement du paquet audio:', error);
    }
}

/**
 * Met à jour l'affichage des informations sur le flux
 * 
 * @param {Object} info Informations sur le flux
 */
function updateStreamInfoDisplay(info) {
    if (!info) {
        return;
    }
    
    // Mettre à jour les informations vidéo
    if (info.video) {
        document.getElementById('videoResolution').textContent = `${info.video.width}x${info.video.height}`;
        document.getElementById('videoFramerate').textContent = info.video.framerate || '30';
    }
    
    // Mettre à jour les informations audio
    if (info.audio) {
        document.getElementById('audioSampleRate').textContent = info.audio.sampleRate;
        document.getElementById('audioChannels').textContent = info.audio.channels;
    }
    
    // Mettre à jour le décalage de synchronisation
    syncOffsetSlider.value = info.syncOffsetMs || 0;
    syncOffsetValueElement.textContent = info.syncOffsetMs || 0;
}

/**
 * Met à jour l'interface pour un flux actif
 */
function updateUIForActiveStream() {
    startStreamButton.disabled = true;
    stopStreamButton.disabled = false;
    muteAudioCheckbox.disabled = false;
    syncOffsetSlider.disabled = false;
}

/**
 * Met à jour l'interface pour un flux inactif
 */
function updateUIForInactiveStream() {
    startStreamButton.disabled = false;
    stopStreamButton.disabled = true;
    muteAudioCheckbox.disabled = true;
    syncOffsetSlider.disabled = true;
    
    // Réinitialiser les informations d'affichage
    document.getElementById('videoResolution').textContent = '-';
    document.getElementById('videoFormat').textContent = '-';
    document.getElementById('videoFramerate').textContent = '-';
    document.getElementById('audioSampleRate').textContent = '-';
    document.getElementById('audioChannels').textContent = '-';
    document.getElementById('audioFormat').textContent = '-';
}

/**
 * Désactive les contrôles de flux
 */
function disableStreamControls() {
    startStreamButton.disabled = true;
    stopStreamButton.disabled = true;
    muteAudioCheckbox.disabled = true;
    syncOffsetSlider.disabled = true;
}

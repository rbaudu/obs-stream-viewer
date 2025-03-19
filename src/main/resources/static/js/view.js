/**
 * Script JavaScript spécifique à la page de visualisation d'un flux (view.html)
 * Gère la visualisation d'un flux spécifique avec des contrôles adaptés
 */

// Éléments DOM
const videoElement = document.getElementById('videoPlayer');
const videoLoadingElement = document.getElementById('videoLoading');
const viewerContainer = document.getElementById('viewerContainer');
const videoControls = document.getElementById('videoControls');
const fullscreenButton = document.getElementById('fullscreenBtn');
const muteToggleButton = document.getElementById('muteToggleBtn');
const syncOffsetSlider = document.getElementById('syncOffset');
const syncOffsetValueElement = document.getElementById('syncOffsetValue');
const currentOffsetElement = document.getElementById('currentOffset');
const lastUpdatedElement = document.getElementById('lastUpdated');
const resolutionElement = document.getElementById('resolution');
const audioInfoElement = document.getElementById('audioInfo');

// Variables pour la gestion du flux
let streamActive = false;
let videoBuffer = [];
let audioBuffer = [];
let mediaSource = null;
let videoSourceBuffer = null;
let audioSourceBuffer = null;
let isFullscreen = false;
let isMuted = true; // État initial : son coupé

// Initialisation au chargement de la page
document.addEventListener('DOMContentLoaded', () => {
    // Vérifier si MediaSource est supporté
    if (!window.MediaSource) {
        showNotification('Votre navigateur ne supporte pas l\'API MediaSource nécessaire pour afficher les flux.', 'danger', 0);
        return;
    }
    
    // Initialiser les contrôles
    initializeControls();
    
    // Démarrer automatiquement le streaming
    startStream();
    
    // Mettre à jour l'UI avec les valeurs initiales
    updateUIWithInitialValues();
});

/**
 * Initialise les contrôles de la page
 */
function initializeControls() {
    // Gérer le bouton plein écran
    fullscreenButton.addEventListener('click', toggleFullscreen);
    
    // Gérer le bouton muet
    muteToggleButton.addEventListener('click', toggleMute);
    
    // Gérer le curseur de synchronisation
    syncOffsetSlider.addEventListener('input', () => {
        const offsetValue = parseInt(syncOffsetSlider.value);
        syncOffsetValueElement.textContent = offsetValue;
        
        // Mettre à jour le décalage de synchronisation
        if (streamActive) {
            adjustSyncOffset(streamId, offsetValue)
                .then(() => {
                    currentOffsetElement.textContent = `${offsetValue} ms`;
                })
                .catch(error => {
                    console.error('Erreur lors de l\'ajustement du décalage de synchronisation:', error);
                });
        }
    });
    
    // Gérer les événements plein écran
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    document.addEventListener('webkitfullscreenchange', handleFullscreenChange);
    document.addEventListener('mozfullscreenchange', handleFullscreenChange);
    document.addEventListener('MSFullscreenChange', handleFullscreenChange);
    
    // Afficher/masquer les contrôles sur mobile avec un tap
    viewerContainer.addEventListener('click', () => {
        if (videoControls.style.opacity === '1') {
            videoControls.style.opacity = '0';
        } else {
            videoControls.style.opacity = '1';
            
            // Masquer automatiquement après 3 secondes
            setTimeout(() => {
                if (!viewerContainer.matches(':hover')) {
                    videoControls.style.opacity = '0';
                }
            }, 3000);
        }
    });
}

/**
 * Met à jour l'interface avec les valeurs initiales
 */
function updateUIWithInitialValues() {
    // Définir la valeur initiale du curseur de synchronisation
    syncOffsetSlider.value = initialSyncOffset;
    syncOffsetValueElement.textContent = initialSyncOffset;
    
    // Appliquer l'état muet initial
    videoElement.muted = isMuted;
    updateMuteButtonUI();
}

/**
 * Démarre la lecture du flux
 */
function startStream() {
    if (streamActive) {
        return;
    }
    
    // Afficher le chargement
    videoLoadingElement.style.display = 'flex';
    
    // Initialiser la connexion WebSocket
    initializeWebSocketConnection(() => {
        streamActive = true;
        
        // Configurer le lecteur média
        setupMediaPlayer();
        
        // S'abonner aux flux vidéo et audio
        subscribeToStreams(handleVideoPacket, handleAudioPacket);
        
        // Démarrer la récupération périodique des informations à jour
        startInfoUpdates();
        
        showNotification('Connexion au flux établie', 'success');
    }, error => {
        showNotification('Erreur de connexion au serveur WebSocket', 'danger');
        videoLoadingElement.style.display = 'none';
    });
}

/**
 * Démarre la mise à jour périodique des informations
 */
function startInfoUpdates() {
    // Mettre à jour les informations toutes les 10 secondes
    setInterval(() => {
        if (streamActive) {
            getStreamInfo(streamId)
                .then(info => {
                    // Mettre à jour l'horodatage
                    if (info.updatedAt) {
                        const date = new Date(info.updatedAt);
                        lastUpdatedElement.textContent = date.toLocaleString();
                    }
                })
                .catch(error => {
                    console.error('Erreur lors de la mise à jour des informations:', error);
                });
        }
    }, 10000);
}

/**
 * Configure le lecteur média pour l'affichage des flux
 */
function setupMediaPlayer() {
    // Similaire à la fonction dans index.js
    mediaSource = new MediaSource();
    videoElement.src = URL.createObjectURL(mediaSource);
    
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
 * Gère la réception d'un paquet vidéo depuis le WebSocket
 * 
 * @param {Object} packet Le paquet vidéo reçu
 */
function handleVideoPacket(packet) {
    // Similaire à la fonction dans index.js
    if (!streamActive || !videoSourceBuffer) {
        return;
    }
    
    try {
        // Traitement du paquet vidéo...
        
        // Mettre à jour les informations d'affichage
        if (packet.width && packet.height) {
            resolutionElement.textContent = `${packet.width}x${packet.height}`;
        }
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
    // Similaire à la fonction dans index.js
    if (!streamActive || !audioSourceBuffer) {
        return;
    }
    
    try {
        // Traitement du paquet audio...
        
        // Mettre à jour les informations d'affichage
        if (packet.sampleRate && packet.channels) {
            audioInfoElement.textContent = `${packet.sampleRate} Hz, ${packet.channels} canaux`;
        }
    } catch (error) {
        console.error('Erreur lors du traitement du paquet audio:', error);
    }
}

/**
 * Bascule l'affichage en mode plein écran
 */
function toggleFullscreen() {
    if (!isFullscreen) {
        // Entrer en mode plein écran
        if (viewerContainer.requestFullscreen) {
            viewerContainer.requestFullscreen();
        } else if (viewerContainer.mozRequestFullScreen) {
            viewerContainer.mozRequestFullScreen();
        } else if (viewerContainer.webkitRequestFullscreen) {
            viewerContainer.webkitRequestFullscreen();
        } else if (viewerContainer.msRequestFullscreen) {
            viewerContainer.msRequestFullscreen();
        }
    } else {
        // Quitter le mode plein écran
        if (document.exitFullscreen) {
            document.exitFullscreen();
        } else if (document.mozCancelFullScreen) {
            document.mozCancelFullScreen();
        } else if (document.webkitExitFullscreen) {
            document.webkitExitFullscreen();
        } else if (document.msExitFullscreen) {
            document.msExitFullscreen();
        }
    }
}

/**
 * Gère les changements d'état du mode plein écran
 */
function handleFullscreenChange() {
    // Vérifier si on est en mode plein écran
    isFullscreen = document.fullscreenElement || 
                  document.webkitFullscreenElement || 
                  document.mozFullScreenElement || 
                  document.msFullscreenElement;
    
    // Mettre à jour la classe CSS et le bouton
    if (isFullscreen) {
        viewerContainer.classList.add('fullscreen-mode');
        fullscreenButton.innerHTML = '<i class="fas fa-compress me-1"></i> Quitter';
    } else {
        viewerContainer.classList.remove('fullscreen-mode');
        fullscreenButton.innerHTML = '<i class="fas fa-expand me-1"></i> Plein écran';
    }
}

/**
 * Bascule l'état muet de la vidéo
 */
function toggleMute() {
    isMuted = !isMuted;
    videoElement.muted = isMuted;
    updateMuteButtonUI();
}

/**
 * Met à jour l'apparence du bouton muet
 */
function updateMuteButtonUI() {
    if (isMuted) {
        muteToggleButton.innerHTML = '<i class="fas fa-volume-mute me-1"></i> Son';
        muteToggleButton.classList.remove('btn-primary');
        muteToggleButton.classList.add('btn-secondary');
    } else {
        muteToggleButton.innerHTML = '<i class="fas fa-volume-up me-1"></i> Son';
        muteToggleButton.classList.remove('btn-secondary');
        muteToggleButton.classList.add('btn-primary');
    }
}

/**
 * Script JavaScript spécifique à la page des paramètres (settings.html)
 * Gère la modification et l'enregistrement des paramètres de l'application
 */

// Éléments du formulaire
const settingsForm = document.getElementById('settingsForm');
const resetButton = document.getElementById('resetBtn');

// Champs du formulaire
const obsHostInput = document.getElementById('obsHost');
const obsPasswordInput = document.getElementById('obsPassword');
const obsVideoPortInput = document.getElementById('obsVideoPort');
const obsAudioPortInput = document.getElementById('obsAudioPort');
const obsWebsocketPortInput = document.getElementById('obsWebsocketPort');
const videoWidthInput = document.getElementById('videoWidth');
const videoHeightInput = document.getElementById('videoHeight');
const videoFramerateInput = document.getElementById('videoFramerate');
const audioSampleRateSelect = document.getElementById('audioSampleRate');
const audioChannelsSelect = document.getElementById('audioChannels');
const defaultSyncOffsetInput = document.getElementById('defaultSyncOffset');

// Paramètres par défaut pour le reset
const defaultSettings = {
    obsHost: 'localhost',
    obsPassword: '',
    obsVideoPort: 8081,
    obsAudioPort: 8082,
    obsWebsocketPort: 4444,
    videoWidth: 1280,
    videoHeight: 720,
    videoFramerate: 30,
    audioSampleRate: 44100,
    audioChannels: 2,
    defaultSyncOffset: 0
};

// Configuration actuelle
let currentSettings = {};

// Initialisation au chargement de la page
document.addEventListener('DOMContentLoaded', () => {
    // Récupérer les paramètres actuels
    loadCurrentSettings();
    
    // Initialiser les gestionnaires d'événements
    initializeEventHandlers();
});

/**
 * Charge les paramètres actuels depuis le formulaire
 */
function loadCurrentSettings() {
    currentSettings = {
        obsHost: obsHostInput.value,
        obsPassword: obsPasswordInput.value,
        obsVideoPort: parseInt(obsVideoPortInput.value),
        obsAudioPort: parseInt(obsAudioPortInput.value),
        obsWebsocketPort: parseInt(obsWebsocketPortInput.value),
        videoWidth: parseInt(videoWidthInput.value),
        videoHeight: parseInt(videoHeightInput.value),
        videoFramerate: parseInt(videoFramerateInput.value),
        audioSampleRate: parseInt(audioSampleRateSelect.value),
        audioChannels: parseInt(audioChannelsSelect.value),
        defaultSyncOffset: parseInt(defaultSyncOffsetInput.value)
    };
}

/**
 * Initialise les gestionnaires d'événements pour les contrôles
 */
function initializeEventHandlers() {
    // Gestion de la soumission du formulaire
    settingsForm.addEventListener('submit', handleFormSubmit);
    
    // Gestion du bouton de réinitialisation
    resetButton.addEventListener('click', resetFormToDefaults);
    
    // Validation des champs numériques
    const numericInputs = [
        obsVideoPortInput, obsAudioPortInput, obsWebsocketPortInput,
        videoWidthInput, videoHeightInput, videoFramerateInput,
        defaultSyncOffsetInput
    ];
    
    numericInputs.forEach(input => {
        input.addEventListener('input', validateNumericInput);
    });
    
    // Validation spécifique pour les ports
    const portInputs = [obsVideoPortInput, obsAudioPortInput, obsWebsocketPortInput];
    portInputs.forEach(input => {
        input.addEventListener('change', validatePortInput);
    });
}

/**
 * Gère la soumission du formulaire
 * 
 * @param {Event} event Événement de soumission
 */
function handleFormSubmit(event) {
    event.preventDefault();
    
    // Vérifier que tous les champs sont valides
    if (!validateForm()) {
        showNotification('Veuillez corriger les erreurs dans le formulaire.', 'danger');
        return;
    }
    
    // Récupérer les valeurs du formulaire
    const formData = getFormData();
    
    // Enregistrer les paramètres
    saveSettings(formData)
        .then(() => {
            showNotification('Paramètres enregistrés avec succès.', 'success');
            
            // Mettre à jour les paramètres actuels
            currentSettings = formData;
        })
        .catch(error => {
            console.error('Erreur lors de l\'enregistrement des paramètres:', error);
            showNotification('Erreur lors de l\'enregistrement des paramètres.', 'danger');
        });
}

/**
 * Valide le formulaire entier
 * 
 * @returns {boolean} true si le formulaire est valide, false sinon
 */
function validateForm() {
    // Vérifier que l'hôte n'est pas vide
    if (!obsHostInput.value.trim()) {
        obsHostInput.classList.add('is-invalid');
        return false;
    }
    
    // Vérifier que les ports sont valides
    const portInputs = [obsVideoPortInput, obsAudioPortInput, obsWebsocketPortInput];
    let portsValid = true;
    
    portInputs.forEach(input => {
        if (!validatePortInput({ target: input })) {
            portsValid = false;
        }
    });
    
    if (!portsValid) {
        return false;
    }
    
    // Vérifier que les dimensions de la vidéo sont valides
    if (videoWidthInput.value < 320 || videoWidthInput.value > 3840) {
        videoWidthInput.classList.add('is-invalid');
        return false;
    }
    
    if (videoHeightInput.value < 240 || videoHeightInput.value > 2160) {
        videoHeightInput.classList.add('is-invalid');
        return false;
    }
    
    // Vérifier que la fréquence d'images est valide
    if (videoFramerateInput.value < 1 || videoFramerateInput.value > 120) {
        videoFramerateInput.classList.add('is-invalid');
        return false;
    }
    
    return true;
}

/**
 * Récupère les données du formulaire
 * 
 * @returns {Object} Données du formulaire
 */
function getFormData() {
    return {
        obsHost: obsHostInput.value.trim(),
        obsPassword: obsPasswordInput.value,
        obsVideoPort: parseInt(obsVideoPortInput.value),
        obsAudioPort: parseInt(obsAudioPortInput.value),
        obsWebsocketPort: parseInt(obsWebsocketPortInput.value),
        videoWidth: parseInt(videoWidthInput.value),
        videoHeight: parseInt(videoHeightInput.value),
        videoFramerate: parseInt(videoFramerateInput.value),
        audioSampleRate: parseInt(audioSampleRateSelect.value),
        audioChannels: parseInt(audioChannelsSelect.value),
        defaultSyncOffset: parseInt(defaultSyncOffsetInput.value)
    };
}

/**
 * Enregistre les paramètres
 * 
 * @param {Object} settings Paramètres à enregistrer
 * @returns {Promise} Promesse résolue lorsque les paramètres sont enregistrés
 */
function saveSettings(settings) {
    // Création de l'objet de configuration à envoyer au serveur
    const configData = {
        connection: {
            host: settings.obsHost,
            videoPort: settings.obsVideoPort,
            audioPort: settings.obsAudioPort,
            websocketPort: settings.obsWebsocketPort,
            password: settings.obsPassword
        },
        video: {
            width: settings.videoWidth,
            height: settings.videoHeight,
            framerate: settings.videoFramerate
        },
        audio: {
            sampleRate: settings.audioSampleRate,
            channels: settings.audioChannels
        },
        defaultSyncOffset: settings.defaultSyncOffset
    };
    
    // Envoyer les paramètres au serveur
    return apiRequest('/settings', 'POST', configData);
}

/**
 * Réinitialise le formulaire aux valeurs par défaut
 */
function resetFormToDefaults() {
    // Demander confirmation
    if (!confirm('Êtes-vous sûr de vouloir réinitialiser tous les paramètres aux valeurs par défaut ?')) {
        return;
    }
    
    // Appliquer les valeurs par défaut
    obsHostInput.value = defaultSettings.obsHost;
    obsPasswordInput.value = defaultSettings.obsPassword;
    obsVideoPortInput.value = defaultSettings.obsVideoPort;
    obsAudioPortInput.value = defaultSettings.obsAudioPort;
    obsWebsocketPortInput.value = defaultSettings.obsWebsocketPort;
    videoWidthInput.value = defaultSettings.videoWidth;
    videoHeightInput.value = defaultSettings.videoHeight;
    videoFramerateInput.value = defaultSettings.videoFramerate;
    audioSampleRateSelect.value = defaultSettings.audioSampleRate;
    audioChannelsSelect.value = defaultSettings.audioChannels;
    defaultSyncOffsetInput.value = defaultSettings.defaultSyncOffset;
    
    // Supprimer les classes d'invalidation
    const inputs = settingsForm.querySelectorAll('input, select');
    inputs.forEach(input => {
        input.classList.remove('is-invalid');
    });
    
    showNotification('Paramètres réinitialisés aux valeurs par défaut.', 'info');
}

/**
 * Valide que l'entrée est numérique
 * 
 * @param {Event} event Événement d'entrée
 */
function validateNumericInput(event) {
    const input = event.target;
    const value = input.value;
    
    // Autoriser uniquement les chiffres
    if (!/^\d*$/.test(value)) {
        input.value = value.replace(/[^\d]/g, '');
    }
    
    // Supprimer la classe d'invalidation si la valeur est correcte
    input.classList.remove('is-invalid');
}

/**
 * Valide qu'un port est dans la plage valide
 * 
 * @param {Event} event Événement de changement
 * @returns {boolean} true si le port est valide, false sinon
 */
function validatePortInput(event) {
    const input = event.target;
    const port = parseInt(input.value);
    
    // Vérifier que le port est dans la plage valide
    if (isNaN(port) || port < 1024 || port > 65535) {
        input.classList.add('is-invalid');
        return false;
    }
    
    input.classList.remove('is-invalid');
    return true;
}

/**
 * Crée et affiche un message de notification dans le formulaire
 * 
 * @param {string} message Message à afficher
 * @param {string} type Type de message (success, danger, warning, info)
 */
function showFormNotification(message, type = 'info') {
    // Créer l'élément de notification
    const notification = document.createElement('div');
    notification.className = `alert alert-${type} alert-dismissible fade show mt-3`;
    notification.role = 'alert';
    
    // Contenu de la notification
    notification.innerHTML = `
        <span>${message}</span>
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Fermer"></button>
    `;
    
    // Ajouter au formulaire
    const existingNotification = settingsForm.querySelector('.alert');
    if (existingNotification) {
        existingNotification.remove();
    }
    
    settingsForm.appendChild(notification);
    
    // Faire défiler jusqu'à la notification
    notification.scrollIntoView({ behavior: 'smooth', block: 'end' });
    
    // Supprimer automatiquement après 5 secondes
    if (type === 'success' || type === 'info') {
        setTimeout(() => {
            notification.classList.remove('show');
            setTimeout(() => {
                notification.remove();
            }, 300);
        }, 5000);
    }
}

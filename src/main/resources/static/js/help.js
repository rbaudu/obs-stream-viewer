/**
 * Script JavaScript spécifique à la page d'aide (help.html)
 * Gère l'interface des onglets d'aide et le comportement de navigation
 */

// Initialisation au chargement de la page
document.addEventListener('DOMContentLoaded', () => {
    // Gérer le défilement vers les sections spécifiques via les paramètres d'URL
    handleHashNavigation();
    
    // Configurer les événements pour la navigation par onglets
    setupTabNavigation();
});

/**
 * Gère la navigation vers une section spécifique via le hash de l'URL
 */
function handleHashNavigation() {
    // Vérifier s'il y a un hash dans l'URL
    if (window.location.hash) {
        const targetId = window.location.hash.substring(1);
        const targetTab = document.getElementById(`${targetId}-tab`);
        
        if (targetTab) {
            // Activer l'onglet correspondant
            const tab = new bootstrap.Tab(targetTab);
            tab.show();
            
            // Faire défiler jusqu'à la section après un court délai
            setTimeout(() => {
                window.scrollTo({
                    top: document.getElementById(targetId).offsetTop - 100,
                    behavior: 'smooth'
                });
            }, 300);
        }
    }
}

/**
 * Configure les événements pour la navigation par onglets
 */
function setupTabNavigation() {
    // Gérer le changement d'onglet pour mettre à jour l'URL
    const tabs = document.querySelectorAll('#help-tabs button[data-bs-toggle="pill"]');
    
    tabs.forEach(tab => {
        tab.addEventListener('shown.bs.tab', event => {
            // Extraire l'ID de l'onglet
            const targetId = event.target.getAttribute('aria-controls');
            
            // Mettre à jour l'URL sans recharger la page
            history.pushState(null, null, `#${targetId}`);
        });
    });
    
    // Gérer les événements de navigation dans l'historique
    window.addEventListener('popstate', () => {
        handleHashNavigation();
    });
    
    // Gérer les clics sur les liens internes
    document.querySelectorAll('a[href^="#"]').forEach(link => {
        link.addEventListener('click', event => {
            const targetId = link.getAttribute('href').substring(1);
            const targetElement = document.getElementById(targetId);
            
            if (targetElement) {
                event.preventDefault();
                
                // Si la cible est un onglet, l'activer
                const tabContent = targetElement.closest('.tab-pane');
                if (tabContent) {
                    const tabId = tabContent.id;
                    const tab = document.querySelector(`button[aria-controls="${tabId}"]`);
                    
                    if (tab) {
                        const bsTab = new bootstrap.Tab(tab);
                        bsTab.show();
                    }
                }
                
                // Faire défiler jusqu'à l'élément
                setTimeout(() => {
                    targetElement.scrollIntoView({
                        behavior: 'smooth',
                        block: 'start'
                    });
                }, 300);
                
                // Mettre à jour l'URL
                history.pushState(null, null, `#${targetId}`);
            }
        });
    });
}

/**
 * Recherche dans la documentation
 * 
 * @param {string} query Requête de recherche
 */
function searchDocumentation(query) {
    query = query.toLowerCase().trim();
    
    if (!query) {
        // Réinitialiser la recherche
        document.querySelectorAll('.tab-pane').forEach(pane => {
            pane.querySelectorAll('p, li, h4, h5').forEach(el => {
                el.style.backgroundColor = '';
                el.classList.remove('search-highlight');
            });
        });
        return;
    }
    
    // Rechercher dans tous les onglets
    let foundResults = false;
    let firstResultPane = null;
    let firstResultElement = null;
    
    document.querySelectorAll('.tab-pane').forEach(pane => {
        let paneHasResults = false;
        
        pane.querySelectorAll('p, li, h4, h5').forEach(el => {
            const text = el.innerText.toLowerCase();
            
            if (text.includes(query)) {
                el.style.backgroundColor = '#fff3cd';
                el.classList.add('search-highlight');
                paneHasResults = true;
                foundResults = true;
                
                if (!firstResultPane) {
                    firstResultPane = pane;
                    firstResultElement = el;
                }
            } else {
                el.style.backgroundColor = '';
                el.classList.remove('search-highlight');
            }
        });
        
        // Marquer visuellement les onglets avec des résultats
        const tabButton = document.querySelector(`button[aria-controls="${pane.id}"]`);
        if (tabButton) {
            if (paneHasResults) {
                tabButton.classList.add('has-search-results');
            } else {
                tabButton.classList.remove('has-search-results');
            }
        }
    });
    
    // Naviguer vers le premier résultat
    if (foundResults && firstResultPane && firstResultElement) {
        // Activer l'onglet contenant le premier résultat
        const tabId = firstResultPane.id;
        const tab = document.querySelector(`button[aria-controls="${tabId}"]`);
        
        if (tab) {
            const bsTab = new bootstrap.Tab(tab);
            bsTab.show();
            
            // Faire défiler jusqu'au premier résultat
            setTimeout(() => {
                firstResultElement.scrollIntoView({
                    behavior: 'smooth',
                    block: 'center'
                });
            }, 300);
        }
    }
    
    return foundResults;
}

/**
 * Crée un élément DOM avec le contenu spécifié
 * 
 * @param {string} tag Nom de la balise
 * @param {Object} attrs Attributs à ajouter à l'élément
 * @param {string|Node|Array} content Contenu de l'élément
 * @returns {HTMLElement} L'élément créé
 */
function createElement(tag, attrs = {}, content = null) {
    const element = document.createElement(tag);
    
    // Ajouter les attributs
    Object.entries(attrs).forEach(([key, value]) => {
        if (key === 'className') {
            element.className = value;
        } else {
            element.setAttribute(key, value);
        }
    });
    
    // Ajouter le contenu
    if (content) {
        if (Array.isArray(content)) {
            content.forEach(item => {
                if (typeof item === 'string') {
                    element.appendChild(document.createTextNode(item));
                } else {
                    element.appendChild(item);
                }
            });
        } else if (typeof content === 'string') {
            element.textContent = content;
        } else {
            element.appendChild(content);
        }
    }
    
    return element;
}

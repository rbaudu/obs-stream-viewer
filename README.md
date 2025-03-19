# OBS Stream Viewer

Application Spring Boot pour capturer, synchroniser et afficher des flux vidéo et audio d'OBS Studio.

## Fonctionnalités

- Capture de flux vidéo depuis OBS Studio
- Capture de flux audio depuis OBS Studio
- Synchronisation des flux vidéo et audio
- Affichage des flux dans une interface web

## Compatibilité

Cette application est compatible avec :
- OBS Studio version 31.0.0 ou supérieure
- OBS WebSocket version 5.x (inclus dans OBS Studio 31.0.0+)

## Prérequis

- Java 17 ou supérieur
- Maven
- OBS Studio 31.0.0+ avec WebSocket 5.x activé

## Technologies utilisées

- Spring Boot
- Thymeleaf
- HTML5
- JavaScript
- CSS
- WebSockets

## Installation

1. Clonez ce dépôt :
   ```
   git clone https://github.com/rbaudu/obs-stream-viewer.git
   ```

2. Accédez au répertoire du projet :
   ```
   cd obs-stream-viewer
   ```

3. Compilez l'application avec Maven :
   ```
   mvn clean install
   ```

4. Lancez l'application :
   ```
   mvn spring-boot:run
   ```

5. Ouvrez votre navigateur et accédez à :
   ```
   http://localhost:8080
   ```

## Configuration d'OBS Studio

1. Ouvrez OBS Studio (version 31.0.0 ou supérieure)
2. Dans le menu Outils, choisissez "obs-websocket Settings"
3. Activez le serveur WebSocket et configurez-le avec le port correspondant (par défaut 4455)
4. Configurez une sortie vidéo et audio TCP vers l'adresse localhost sur les ports correspondants

Pour des instructions détaillées, consultez la page d'aide dans l'application.

## Licence

Ce projet est distribué sous la licence MIT. Voir le fichier `LICENSE` pour plus de détails.

## Contribution

Les contributions sont les bienvenues ! N'hésitez pas à ouvrir une issue ou à proposer une pull request.

package com.rbaudu.obsstreamviewer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration des WebSockets pour la communication en temps réel.
 * 
 * @author rbaudu
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Préfixe pour les points de terminaison de destination où les messages sont diffusés
        config.enableSimpleBroker("/topic");
        
        // Préfixe pour les points de terminaison de mappage où les messages sont envoyés
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Point de terminaison où les clients se connectent
        registry.addEndpoint("/obs-websocket")
               .setAllowedOrigins("*")
               .withSockJS();
    }
}

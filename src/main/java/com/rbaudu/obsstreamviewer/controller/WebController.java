package com.rbaudu.obsstreamviewer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.rbaudu.obsstreamviewer.config.ObsProperties;
import com.rbaudu.obsstreamviewer.model.SyncedMediaStream;
import com.rbaudu.obsstreamviewer.service.StreamSynchronizer;

/**
 * Contrôleur pour les pages web de l'application.
 * 
 * @author rbaudu
 */
@Controller
public class WebController {
    
    @Autowired
    private StreamSynchronizer streamSynchronizer;
    
    @Autowired
    private ObsProperties obsProperties;
    
    /**
     * Page d'accueil.
     * 
     * @param model Le modèle pour la vue
     * @return Le nom de la vue
     */
    @GetMapping("/")
    public String home(Model model) {
        SyncedMediaStream defaultStream = streamSynchronizer.getDefaultStream();
        model.addAttribute("stream", defaultStream);
        model.addAttribute("obsConfig", obsProperties);
        return "index";
    }
    
    /**
     * Page d'affichage d'un flux spécifique.
     * 
     * @param streamId ID du flux
     * @param model Le modèle pour la vue
     * @return Le nom de la vue
     */
    @GetMapping("/view/{streamId}")
    public String viewStream(@PathVariable String streamId, Model model) {
        SyncedMediaStream stream = streamSynchronizer.getStream(streamId);
        
        if (stream == null) {
            return "redirect:/";
        }
        
        model.addAttribute("stream", stream);
        model.addAttribute("obsConfig", obsProperties);
        return "view";
    }
    
    /**
     * Page de configuration des paramètres de synchronisation.
     * 
     * @param model Le modèle pour la vue
     * @return Le nom de la vue
     */
    @GetMapping("/settings")
    public String settings(Model model) {
        SyncedMediaStream defaultStream = streamSynchronizer.getDefaultStream();
        model.addAttribute("stream", defaultStream);
        model.addAttribute("obsConfig", obsProperties);
        return "settings";
    }
    
    /**
     * Page d'aide.
     * 
     * @return Le nom de la vue
     */
    @GetMapping("/help")
    public String help() {
        return "help";
    }
}

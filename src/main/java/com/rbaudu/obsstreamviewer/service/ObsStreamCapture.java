package com.rbaudu.obsstreamviewer.service;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.rbaudu.obsstreamviewer.config.ObsProperties;
import com.rbaudu.obsstreamviewer.model.AudioPacket;
import com.rbaudu.obsstreamviewer.model.VideoPacket;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsable de la capture des flux vidéo et audio depuis OBS.
 * 
 * @author rbaudu
 */
@Service
@Slf4j
public class ObsStreamCapture {
    
    @Autowired
    private StreamSynchronizer streamSynchronizer;
    
    @Autowired
    private ObsProperties obsProperties;
    
    private AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    
    private long videoSequence = 0;
    private long audioSequence = 0;
    
    /**
     * Initialisation du service.
     */
    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(2);
        start();
    }
    
    /**
     * Libération des ressources.
     */
    @PreDestroy
    public void cleanup() {
        stop();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    /**
     * Démarre la capture des flux.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting OBS stream capture");
            executorService.submit(this::captureVideoStream);
            executorService.submit(this::captureAudioStream);
        }
    }
    
    /**
     * Arrête la capture des flux.
     */
    public void stop() {
        running.set(false);
        log.info("Stopping OBS stream capture");
    }
    
    /**
     * Capture le flux vidéo depuis OBS.
     */
    private void captureVideoStream() {
        log.info("Starting video capture from OBS at {}:{}", 
                obsProperties.getConnection().getHost(), 
                obsProperties.getConnection().getVideoPort());
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber("tcp://" + 
                obsProperties.getConnection().getHost() + ":" + 
                obsProperties.getConnection().getVideoPort())) {
            
            grabber.setOption("analyzeduration", "0");
            grabber.setOption("probesize", "32");
            grabber.setOption("rtsp_transport", "tcp");
            grabber.setFrameRate(obsProperties.getVideo().getFramerate());
            grabber.setVideoOption("threads", "1");
            
            grabber.start();
            
            log.info("Video grabber started with parameters: {}x{} @ {} fps", 
                    grabber.getImageWidth(), grabber.getImageHeight(), grabber.getFrameRate());
            
            while (running.get()) {
                Frame frame = grabber.grab();
                if (frame == null) {
                    continue;
                }
                
                if (frame.image != null) {
                    processVideoFrame(frame);
                }
            }
        } catch (Exception e) {
            log.error("Error capturing video stream", e);
        }
        
        log.info("Video capture stopped");
    }
    
    /**
     * Capture le flux audio depuis OBS.
     */
    private void captureAudioStream() {
        log.info("Starting audio capture from OBS at {}:{}", 
                obsProperties.getConnection().getHost(), 
                obsProperties.getConnection().getAudioPort());
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber("tcp://" + 
                obsProperties.getConnection().getHost() + ":" + 
                obsProperties.getConnection().getAudioPort())) {
            
            grabber.setOption("analyzeduration", "0");
            grabber.setOption("probesize", "32");
            grabber.setSampleRate(obsProperties.getAudio().getSampleRate());
            grabber.setAudioChannels(obsProperties.getAudio().getChannels());
            
            grabber.start();
            
            log.info("Audio grabber started with parameters: {} Hz, {} channels", 
                    grabber.getSampleRate(), grabber.getAudioChannels());
            
            while (running.get()) {
                Frame frame = grabber.grab();
                if (frame == null) {
                    continue;
                }
                
                if (frame.samples != null) {
                    processAudioFrame(frame);
                }
            }
        } catch (Exception e) {
            log.error("Error capturing audio stream", e);
        }
        
        log.info("Audio capture stopped");
    }
    
    /**
     * Traite une frame vidéo.
     * 
     * @param frame La frame vidéo à traiter
     */
    private void processVideoFrame(Frame frame) {
        try {
            byte[] frameBytes = convertFrameToBytes(frame);
            String encodedFrame = Base64.getEncoder().encodeToString(frameBytes);
            
            VideoPacket packet = new VideoPacket();
            packet.setId(UUID.randomUUID().toString());
            packet.setSequenceNumber(videoSequence++);
            packet.setWidth(frame.imageWidth);
            packet.setHeight(frame.imageHeight);
            packet.setFrameData(encodedFrame);
            packet.setPts(frame.timestamp);
            packet.setPresentationTimestampMs(System.currentTimeMillis());
            packet.setKeyFrame(frame.keyFrame);
            
            streamSynchronizer.processVideoPacket(packet);
        } catch (Exception e) {
            log.error("Error processing video frame", e);
        }
    }
    
    /**
     * Traite une frame audio.
     * 
     * @param frame La frame audio à traiter
     */
    private void processAudioFrame(Frame frame) {
        try {
            byte[] sampleBytes = convertAudioToBytes(frame);
            String encodedSamples = Base64.getEncoder().encodeToString(sampleBytes);
            
            AudioPacket packet = new AudioPacket();
            packet.setId(UUID.randomUUID().toString());
            packet.setSequenceNumber(audioSequence++);
            packet.setSampleRate(frame.sampleRate);
            packet.setChannels(frame.audioChannels);
            packet.setAudioData(encodedSamples);
            packet.setPts(frame.timestamp);
            packet.setSampleCount(frame.samples[0].length);
            packet.setPresentationTimestampMs(System.currentTimeMillis());
            
            // Calculer la durée en ms
            float duration = (float) frame.samples[0].length / frame.sampleRate * 1000;
            packet.setDurationMs((long) duration);
            
            streamSynchronizer.processAudioPacket(packet);
        } catch (Exception e) {
            log.error("Error processing audio frame", e);
        }
    }
    
    /**
     * Convertit une frame vidéo en tableau d'octets.
     * 
     * @param frame La frame à convertir
     * @return Un tableau d'octets représentant la frame
     */
    private byte[] convertFrameToBytes(Frame frame) {
        // Cette implémentation est simplifiée.
        // Dans un environnement de production, utilisez un encodeur vidéo approprié.
        
        // Pour l'instant, on simule la conversion
        byte[] bytes = new byte[frame.imageWidth * frame.imageHeight * 3];
        // TODO: Implémentation réelle de la conversion
        
        return bytes;
    }
    
    /**
     * Convertit des échantillons audio en tableau d'octets.
     * 
     * @param frame La frame audio à convertir
     * @return Un tableau d'octets représentant les échantillons audio
     */
    private byte[] convertAudioToBytes(Frame frame) {
        // Cette implémentation est simplifiée.
        // Dans un environnement de production, utilisez un encodeur audio approprié.
        
        // Pour l'instant, on simule la conversion
        byte[] bytes = new byte[frame.samples[0].length * frame.audioChannels * 2];
        // TODO: Implémentation réelle de la conversion
        
        return bytes;
    }
}

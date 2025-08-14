package com.refinitiv.markethub.service;

import com.refinitiv.markethub.config.FIXConfig;
import com.refinitiv.markethub.fix.MarketDataFIXApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import quickfix.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;

/**
 * Service that manages the FIX protocol engine lifecycle.
 * 
 * This service:
 * - Initializes and starts the FIX acceptor on application startup
 * - Manages FIX sessions
 * - Handles graceful shutdown
 * - Provides methods for session management
 * 
 * The acceptor listens for incoming FIX connections from market makers.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Service
@Slf4j
public class FIXService {

    @Autowired
    private FIXConfig fixConfig;

    @Autowired
    private MarketDataFIXApplication fixApplication;

    @Autowired
    private SessionSettings sessionSettings;

    @Autowired
    private MessageStoreFactory messageStoreFactory;

    @Autowired
    private LogFactory logFactory;

    @Autowired
    private MessageFactory messageFactory;

    /**
     * The FIX acceptor that listens for incoming connections.
     */
    private Acceptor acceptor;

    /**
     * Flag to track if the acceptor is running.
     */
    private volatile boolean isRunning = false;

    /**
     * Initializes and starts the FIX acceptor after Spring context is ready.
     * This method is called automatically after dependency injection.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing FIX Service");
        
        try {
            // Create the acceptor with all required components
            acceptor = new SocketAcceptor(
                fixApplication,
                messageStoreFactory,
                sessionSettings,
                logFactory,
                messageFactory
            );
            
            // Start the acceptor
            start();
            
            log.info("FIX Service initialized successfully");
            
        } catch (ConfigError e) {
            log.error("Failed to initialize FIX acceptor due to configuration error", e);
            throw new RuntimeException("FIX initialization failed", e);
        }
    }

    /**
     * Starts the FIX acceptor to begin accepting connections.
     * This method is idempotent - calling it multiple times has no effect if already running.
     */
    public void start() {
        if (isRunning) {
            log.warn("FIX acceptor is already running");
            return;
        }
        
        try {
            log.info("Starting FIX acceptor on port {}", fixConfig.getAcceptorPort());
            
            acceptor.start();
            isRunning = true;
            
            log.info("FIX acceptor started successfully and listening for connections");
            
            // Log all configured sessions
            logConfiguredSessions();
            
        } catch (RuntimeError | ConfigError e) {
            log.error("Failed to start FIX acceptor", e);
            throw new RuntimeException("Failed to start FIX service", e);
        }
    }

    /**
     * Stops the FIX acceptor gracefully.
     * Waits for active sessions to disconnect properly.
     */
    public void stop() {
        if (!isRunning) {
            log.warn("FIX acceptor is not running");
            return;
        }
        
        try {
            log.info("Stopping FIX acceptor");
            
            // Stop accepting new connections and disconnect existing sessions
            acceptor.stop();
            isRunning = false;
            
            log.info("FIX acceptor stopped successfully");
            
        } catch (RuntimeError e) {
            log.error("Error stopping FIX acceptor", e);
        }
    }

    /**
     * Gracefully shuts down the FIX service before application termination.
     * This method is called automatically during Spring shutdown.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down FIX Service");
        
        if (isRunning) {
            stop();
        }
        
        log.info("FIX Service shutdown complete");
    }

    /**
     * Restarts the FIX acceptor.
     * Useful for applying configuration changes.
     */
    public void restart() {
        log.info("Restarting FIX acceptor");
        stop();
        
        // Wait a moment for clean shutdown
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        start();
        log.info("FIX acceptor restarted");
    }

    /**
     * Checks if the FIX acceptor is currently running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Gets the current status of all FIX sessions.
     * 
     * @return A string describing session statuses
     */
    public String getSessionStatus() {
        StringBuilder status = new StringBuilder();
        status.append("FIX Session Status:\n");
        
        if (!isRunning) {
            status.append("Acceptor is not running\n");
            return status.toString();
        }
        
        // Get all sessions managed by the acceptor
        for (SessionID sessionId : acceptor.getSessions()) {
            Session session = Session.lookupSession(sessionId);
            if (session != null) {
                status.append(String.format("Session %s -> %s: %s\n",
                    sessionId.getSenderCompID(),
                    sessionId.getTargetCompID(),
                    session.isLoggedOn() ? "LOGGED ON" : "LOGGED OFF"
                ));
            }
        }
        
        return status.toString();
    }

    /**
     * Sends a test message to a specific session.
     * Useful for testing connectivity.
     * 
     * @param targetCompId The target component ID
     * @return true if message was sent, false otherwise
     */
    public boolean sendTestMessage(String targetCompId) {
        for (SessionID sessionId : acceptor.getSessions()) {
            if (sessionId.getTargetCompID().equals(targetCompId)) {
                Session session = Session.lookupSession(sessionId);
                if (session != null && session.isLoggedOn()) {
                    try {
                        // Send a test request message
                        quickfix.fix44.TestRequest testRequest = new quickfix.fix44.TestRequest(
                            new quickfix.field.TestReqID("TEST_" + System.currentTimeMillis())
                        );
                        
                        session.send(testRequest);
                        log.info("Sent test message to {}", targetCompId);
                        return true;
                        
                    } catch (Exception e) {
                        log.error("Failed to send test message to " + targetCompId, e);
                    }
                }
            }
        }
        
        log.warn("Could not send test message to {} - session not found or not logged on", targetCompId);
        return false;
    }

    /**
     * Disconnects a specific session.
     * 
     * @param targetCompId The target component ID to disconnect
     * @return true if disconnected, false otherwise
     */
    public boolean disconnectSession(String targetCompId) {
        for (SessionID sessionId : acceptor.getSessions()) {
            if (sessionId.getTargetCompID().equals(targetCompId)) {
                Session session = Session.lookupSession(sessionId);
                if (session != null && session.isLoggedOn()) {
                    try {
                        session.disconnect("Administrative disconnect", true);
                        log.info("Disconnected session for {}", targetCompId);
                        return true;
                    } catch (Exception e) {
                        log.error("Failed to disconnect session for " + targetCompId, e);
                    }
                }
            }
        }
        
        log.warn("Could not disconnect {} - session not found or not logged on", targetCompId);
        return false;
    }

    /**
     * Logs all configured FIX sessions for debugging.
     */
    private void logConfiguredSessions() {
        log.info("Configured FIX Sessions:");
        for (SessionID sessionId : acceptor.getSessions()) {
            log.info("  - {} -> {} (Qualifier: {})",
                sessionId.getSenderCompID(),
                sessionId.getTargetCompID(),
                sessionId.getSessionQualifier()
            );
        }
    }

    /**
     * Gets statistics for a specific session.
     * 
     * @param targetCompId The target component ID
     * @return Session statistics or null if not found
     */
    public SessionStatistics getSessionStatistics(String targetCompId) {
        for (SessionID sessionId : acceptor.getSessions()) {
            if (sessionId.getTargetCompID().equals(targetCompId)) {
                Session session = Session.lookupSession(sessionId);
                if (session != null) {
                    SessionStatistics stats = new SessionStatistics();
                    stats.setTargetCompId(targetCompId);
                    stats.setLoggedOn(session.isLoggedOn());
                    
                    // These would need to be tracked in the FIX application
                    // For now, returning basic info
                    stats.setMessagesReceived(0L);
                    stats.setMessagesSent(0L);
                    
                    return stats;
                }
            }
        }
        return null;
    }

    /**
     * Inner class to hold session statistics.
     */
    public static class SessionStatistics {
        private String targetCompId;
        private boolean loggedOn;
        private Long messagesReceived;
        private Long messagesSent;
        private LocalDateTime lastMessageTime;

        // Getters and setters
        public String getTargetCompId() { return targetCompId; }
        public void setTargetCompId(String targetCompId) { this.targetCompId = targetCompId; }
        
        public boolean isLoggedOn() { return loggedOn; }
        public void setLoggedOn(boolean loggedOn) { this.loggedOn = loggedOn; }
        
        public Long getMessagesReceived() { return messagesReceived; }
        public void setMessagesReceived(Long messagesReceived) { this.messagesReceived = messagesReceived; }
        
        public Long getMessagesSent() { return messagesSent; }
        public void setMessagesSent(Long messagesSent) { this.messagesSent = messagesSent; }
        
        public LocalDateTime getLastMessageTime() { return lastMessageTime; }
        public void setLastMessageTime(LocalDateTime lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    }
}
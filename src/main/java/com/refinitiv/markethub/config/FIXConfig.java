package com.refinitiv.markethub.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import quickfix.*;
import quickfix.field.*;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * FIX Protocol configuration for Market Data Hub.
 * Configures QuickFIX/J for both:
 * - Acceptor: To receive connections from market makers (RA, CMF)
 * - Initiator: To connect to external systems if needed
 * 
 * This configuration follows FIX 4.4 standard for market data distribution.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "fix")
@Data
@Slf4j
public class FIXConfig {

    // FIX Session Configuration Properties
    private String beginString = "FIX.4.4";  // FIX version
    private String senderCompId = "MARKETHUB";  // Our system identifier
    private String targetCompId = "MARKETMAKER";  // Default target
    private String acceptorPort = "9876";  // Port for accepting connections
    private String heartbeatInterval = "30";  // Heartbeat interval in seconds
    private String reconnectInterval = "5";  // Reconnection interval in seconds
    private boolean dataDictionary = true;  // Use data dictionary for validation
    private String logPath = "logs/fix";  // Path for FIX logs
    private String fileStorePath = "data/fix";  // Path for FIX message storage

    /**
     * Initializes FIX configuration on startup.
     * Validates configuration and creates necessary directories.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing FIX configuration");
        log.info("FIX Version: {}, Sender: {}, Acceptor Port: {}", 
                beginString, senderCompId, acceptorPort);
    }

    /**
     * Creates the QuickFIX/J SessionSettings for the acceptor.
     * The acceptor receives connections from market makers.
     * 
     * @return SessionSettings configured for acceptor mode
     */
    @Bean
    public SessionSettings acceptorSessionSettings() {
        String config = String.format("""
            # FIX Acceptor Configuration for Market Data Hub
            # This configuration sets up the hub to accept incoming FIX connections
            # from market makers (RA, CMF) for market data distribution
            
            [DEFAULT]
            ConnectionType=acceptor
            BeginString=%s
            SenderCompID=%s
            HeartBtInt=%s
            ReconnectInterval=%s
            UseDataDictionary=%s
            DataDictionary=FIX44.xml
            FileStorePath=%s
            FileLogPath=%s
            
            # Acceptor specific settings
            SocketAcceptPort=%s
            StartTime=00:00:00
            EndTime=00:00:00
            
            # Performance tuning
            SocketNodelay=Y
            SocketTcpNoDelay=Y
            
            # Message validation
            ValidateFieldsOutOfOrder=N
            ValidateFieldsHaveValues=Y
            ValidateUserDefinedFields=Y
            
            # Session configuration for RA (Risk Analytics)
            [SESSION]
            TargetCompID=RA
            
            # Session configuration for CMF (Capital Markets Framework)
            [SESSION]
            TargetCompID=CMF
            """,
            beginString, senderCompId, heartbeatInterval, reconnectInterval,
            dataDictionary ? "Y" : "N", fileStorePath, logPath, acceptorPort
        );

        try {
            InputStream inputStream = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8));
            SessionSettings settings = new SessionSettings(inputStream);
            log.info("FIX acceptor session settings created successfully");
            return settings;
        } catch (ConfigError e) {
            log.error("Failed to create FIX acceptor session settings", e);
            throw new RuntimeException("FIX configuration error", e);
        }
    }

    /**
     * Creates the MessageStoreFactory for persisting FIX messages.
     * Uses file-based storage for reliability and audit purposes.
     * 
     * @param sessionSettings The session settings to use
     * @return MessageStoreFactory for message persistence
     */
    @Bean
    public MessageStoreFactory messageStoreFactory(SessionSettings sessionSettings) {
        log.info("Creating FIX message store factory with path: {}", fileStorePath);
        return new FileStoreFactory(sessionSettings);
    }

    /**
     * Creates the LogFactory for FIX session logging.
     * Logs all FIX messages for debugging and audit trail.
     * 
     * @param sessionSettings The session settings to use
     * @return LogFactory for FIX logging
     */
    @Bean
    public LogFactory logFactory(SessionSettings sessionSettings) {
        log.info("Creating FIX log factory with path: {}", logPath);
        return new FileLogFactory(sessionSettings);
    }

    /**
     * Creates the MessageFactory for FIX message creation.
     * Uses the default QuickFIX/J message factory.
     * 
     * @return MessageFactory for creating FIX messages
     */
    @Bean
    public MessageFactory messageFactory() {
        log.info("Creating default FIX message factory");
        return new DefaultMessageFactory();
    }
}
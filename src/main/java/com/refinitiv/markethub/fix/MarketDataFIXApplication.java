package com.refinitiv.markethub.fix;

import com.refinitiv.markethub.event.MarketDataEvent;
import com.refinitiv.markethub.model.MarketData;
import com.refinitiv.markethub.model.MarketMakerConfig;
import com.refinitiv.markethub.service.MarketMakerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.MarketDataRequest;
import quickfix.fix44.MarketDataSnapshotFullRefresh;
import quickfix.fix44.MessageCracker;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main FIX Application handler for Market Data Hub.
 * 
 * This class implements the QuickFIX/J Application interface and handles:
 * - FIX session lifecycle events
 * - Incoming market data requests from market makers
 * - Outgoing market data distribution
 * - Session management and monitoring
 * 
 * Uses MessageCracker for type-safe message handling of FIX 4.4 messages.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Component
@Slf4j
public class MarketDataFIXApplication extends MessageCracker implements Application {

    @Autowired
    private MarketMakerService marketMakerService;

    /**
     * Map to track active sessions by SessionID.
     * Used for message routing and session management.
     */
    private final Map<SessionID, MarketMakerConfig> activeSessions = new ConcurrentHashMap<>();

    /**
     * Map to track market data subscriptions per session.
     * Key: SessionID, Value: Set of subscribed symbols
     */
    private final Map<SessionID, Map<String, String>> subscriptions = new ConcurrentHashMap<>();

    /**
     * Called when a new FIX session is created.
     * This happens before logon.
     * 
     * @param sessionId The session identifier
     */
    @Override
    public void onCreate(SessionID sessionId) {
        log.info("FIX session created: {} -> {}", 
                sessionId.getSenderCompID(), sessionId.getTargetCompID());
        
        // Initialize session tracking
        subscriptions.put(sessionId, new ConcurrentHashMap<>());
    }

    /**
     * Called when a FIX session successfully logs on.
     * This is where we activate the session for market data distribution.
     * 
     * @param sessionId The session identifier
     */
    @Override
    public void onLogon(SessionID sessionId) {
        log.info("FIX session logged on: {} -> {}", 
                sessionId.getSenderCompID(), sessionId.getTargetCompID());
        
        // Load market maker configuration
        String targetCompId = sessionId.getTargetCompID();
        MarketMakerConfig config = marketMakerService.getConfigByCompId(targetCompId);
        
        if (config != null && config.getActive()) {
            activeSessions.put(sessionId, config);
            
            // Update connection status
            marketMakerService.updateConnectionStatus(config.getId(), 
                MarketMakerConfig.ConnectionStatus.CONNECTED);
            
            log.info("Market maker {} connected and activated", config.getName());
            
            // Send initial market data snapshot if configured
            sendInitialSnapshot(sessionId, config);
        } else {
            log.warn("No active configuration found for CompID: {}", targetCompId);
        }
    }

    /**
     * Called when a FIX session logs off.
     * Cleans up session resources and subscriptions.
     * 
     * @param sessionId The session identifier
     */
    @Override
    public void onLogout(SessionID sessionId) {
        log.info("FIX session logged off: {} -> {}", 
                sessionId.getSenderCompID(), sessionId.getTargetCompID());
        
        // Clean up session
        MarketMakerConfig config = activeSessions.remove(sessionId);
        if (config != null) {
            // Update connection status
            marketMakerService.updateConnectionStatus(config.getId(), 
                MarketMakerConfig.ConnectionStatus.DISCONNECTED);
            
            log.info("Market maker {} disconnected", config.getName());
        }
        
        // Clear subscriptions
        subscriptions.get(sessionId).clear();
    }

    /**
     * Called when an admin message is sent to the counterparty.
     * Admin messages include logon, logout, heartbeat, etc.
     * 
     * @param message The admin message
     * @param sessionId The session identifier
     */
    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        log.debug("Sending admin message to {}: {}", 
                sessionId.getTargetCompID(), message.getClass().getSimpleName());
        
        // Add custom fields to logon message if needed
        if (message.getHeader().isSetField(MsgType.FIELD)) {
            try {
                String msgType = message.getHeader().getString(MsgType.FIELD);
                if (MsgType.LOGON.equals(msgType)) {
                    // Can add custom logon fields here if needed
                    log.debug("Customizing logon message for session: {}", sessionId);
                }
            } catch (FieldNotFound e) {
                log.error("Error processing admin message", e);
            }
        }
    }

    /**
     * Called when an admin message is received from the counterparty.
     * 
     * @param message The admin message
     * @param sessionId The session identifier
     * @throws FieldNotFound If a required field is missing
     * @throws IncorrectDataFormat If data format is incorrect
     * @throws IncorrectTagValue If a tag has an incorrect value
     * @throws RejectLogon If the logon should be rejected
     */
    @Override
    public void fromAdmin(Message message, SessionID sessionId) 
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        log.debug("Received admin message from {}: {}", 
                sessionId.getTargetCompID(), message.getClass().getSimpleName());
        
        // Handle specific admin messages if needed
        String msgType = message.getHeader().getString(MsgType.FIELD);
        
        if (MsgType.LOGON.equals(msgType)) {
            // Validate logon if authentication is required
            MarketMakerConfig config = marketMakerService.getConfigByCompId(
                sessionId.getTargetCompID());
            
            if (config != null && config.getRequiresAuthentication()) {
                // Implement authentication logic here
                log.info("Authenticating market maker: {}", config.getName());
                // For now, we'll accept all connections
                // In production, validate credentials from the message
            }
        }
    }

    /**
     * Called when an application message is sent to the counterparty.
     * Application messages include market data, orders, executions, etc.
     * 
     * @param message The application message
     * @param sessionId The session identifier
     * @throws DoNotSend If the message should not be sent
     */
    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        log.debug("Sending application message to {}: Type={}", 
                sessionId.getTargetCompID(), message.getClass().getSimpleName());
        
        // Can add message validation or transformation here
        // For example, applying rate limiting
        MarketMakerConfig config = activeSessions.get(sessionId);
        if (config != null && config.getMaxMessagesPerSecond() != null) {
            // Implement rate limiting logic
            // This is a placeholder - actual implementation would track message rates
            log.debug("Applying rate limit of {} msg/sec for {}", 
                    config.getMaxMessagesPerSecond(), config.getName());
        }
    }

    /**
     * Called when an application message is received from the counterparty.
     * Routes the message to appropriate handlers using MessageCracker.
     * 
     * @param message The application message
     * @param sessionId The session identifier
     * @throws FieldNotFound If a required field is missing
     * @throws IncorrectDataFormat If data format is incorrect
     * @throws IncorrectTagValue If a tag has an incorrect value
     * @throws UnsupportedMessageType If the message type is not supported
     */
    @Override
    public void fromApp(Message message, SessionID sessionId) 
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        log.debug("Received application message from {}: {}", 
                sessionId.getTargetCompID(), message.toString());
        
        // Use MessageCracker to route to specific handlers
        crack(message, sessionId);
    }

    /**
     * Handles incoming Market Data Request messages (Type V).
     * Market makers send these to subscribe to market data feeds.
     * 
     * @param request The market data request
     * @param sessionId The session identifier
     * @throws FieldNotFound If a required field is missing
     */
    public void onMessage(MarketDataRequest request, SessionID sessionId) throws FieldNotFound {
        log.info("Received Market Data Request from {}", sessionId.getTargetCompID());
        
        // Extract request details
        String mdReqId = request.getMDReqID().getValue();
        char subscriptionType = request.getSubscriptionRequestType().getValue();
        int marketDepth = request.getMarketDepth().getValue();
        
        log.debug("MDReqID: {}, SubscriptionType: {}, MarketDepth: {}", 
                mdReqId, subscriptionType, marketDepth);
        
        // Process subscription request
        if (subscriptionType == '1') { // SNAPSHOT_PLUS_UPDATES
            // Subscribe to updates
            processSubscriptionRequest(request, sessionId, mdReqId);
        } else if (subscriptionType == '2') { // DISABLE_PREVIOUS_SNAPSHOT_PLUS_UPDATE_REQUEST
            // Unsubscribe
            processUnsubscribeRequest(mdReqId, sessionId);
        } else if (subscriptionType == '0') { // SNAPSHOT
            // Send one-time snapshot
            processSnapshotRequest(request, sessionId, mdReqId);
        }
    }

    /**
     * Processes a subscription request for market data updates.
     * 
     * @param request The market data request
     * @param sessionId The session identifier
     * @param mdReqId The market data request ID
     */
    private void processSubscriptionRequest(MarketDataRequest request, SessionID sessionId, String mdReqId) {
        try {
            // Get the symbols from the request
            int symbolCount = request.getNoRelatedSym().getValue();
            
            for (int i = 0; i < symbolCount; i++) {
                MarketDataRequest.NoRelatedSym group = new MarketDataRequest.NoRelatedSym();
                request.getGroup(i + 1, group);
                
                String symbol = group.getSymbol().getValue();
                
                // Add to subscriptions
                subscriptions.get(sessionId).put(mdReqId + "_" + symbol, symbol);
                
                log.info("Added subscription for {} on session {}", symbol, sessionId);
            }
        } catch (Exception e) {
            log.error("Error processing subscription request", e);
        }
    }

    /**
     * Processes an unsubscribe request.
     * 
     * @param mdReqId The market data request ID to unsubscribe
     * @param sessionId The session identifier
     */
    private void processUnsubscribeRequest(String mdReqId, SessionID sessionId) {
        Map<String, String> sessionSubs = subscriptions.get(sessionId);
        if (sessionSubs != null) {
            // Remove all subscriptions with this request ID
            sessionSubs.entrySet().removeIf(entry -> entry.getKey().startsWith(mdReqId + "_"));
            log.info("Removed subscriptions for request {} on session {}", mdReqId, sessionId);
        }
    }

    /**
     * Processes a one-time snapshot request.
     * 
     * @param request The market data request
     * @param sessionId The session identifier
     * @param mdReqId The market data request ID
     */
    private void processSnapshotRequest(MarketDataRequest request, SessionID sessionId, String mdReqId) {
        try {
            int symbolCount = request.getNoRelatedSym().getValue();
            
            for (int i = 0; i < symbolCount; i++) {
                MarketDataRequest.NoRelatedSym group = new MarketDataRequest.NoRelatedSym();
                request.getGroup(i + 1, group);
                
                String symbol = group.getSymbol().getValue();
                
                // Request snapshot generation
                log.info("Snapshot requested for {} on session {}", symbol, sessionId);
            }
        } catch (Exception e) {
            log.error("Error processing snapshot request", e);
        }
    }

    /**
     * Sends a market data snapshot to a session.
     * Creates and sends a MarketDataSnapshotFullRefresh message.
     * 
     * @param sessionId The session to send to
     * @param mdReqId The market data request ID
     * @param marketData The market data to send
     */
    public void sendMarketDataSnapshot(SessionID sessionId, String mdReqId, MarketData marketData) {
        try {
            // Create FIX message for market data snapshot
            MarketDataSnapshotFullRefresh snapshot = new MarketDataSnapshotFullRefresh();
            
            // Set required fields
            if (mdReqId != null) {
                snapshot.set(new MDReqID(mdReqId));
            }
            snapshot.set(new Symbol(marketData.getSymbol()));
            
            // Add market data entries
            int entryCount = 0;
            
            // Bid
            if (marketData.getBidPrice() != null) {
                MarketDataSnapshotFullRefresh.NoMDEntries bidEntry = 
                    new MarketDataSnapshotFullRefresh.NoMDEntries();
                bidEntry.set(new MDEntryType(MDEntryType.BID));
                bidEntry.set(new MDEntryPx(marketData.getBidPrice().doubleValue()));
                if (marketData.getBidSize() != null) {
                    bidEntry.set(new MDEntrySize(marketData.getBidSize().doubleValue()));
                }
                snapshot.addGroup(bidEntry);
                entryCount++;
            }
            
            // Ask
            if (marketData.getAskPrice() != null) {
                MarketDataSnapshotFullRefresh.NoMDEntries askEntry = 
                    new MarketDataSnapshotFullRefresh.NoMDEntries();
                askEntry.set(new MDEntryType(MDEntryType.OFFER));
                askEntry.set(new MDEntryPx(marketData.getAskPrice().doubleValue()));
                if (marketData.getAskSize() != null) {
                    askEntry.set(new MDEntrySize(marketData.getAskSize().doubleValue()));
                }
                snapshot.addGroup(askEntry);
                entryCount++;
            }
            
            // Last trade
            if (marketData.getLastPrice() != null) {
                MarketDataSnapshotFullRefresh.NoMDEntries lastEntry = 
                    new MarketDataSnapshotFullRefresh.NoMDEntries();
                lastEntry.set(new MDEntryType(MDEntryType.TRADE));
                lastEntry.set(new MDEntryPx(marketData.getLastPrice().doubleValue()));
                if (marketData.getLastSize() != null) {
                    lastEntry.set(new MDEntrySize(marketData.getLastSize().doubleValue()));
                }
                snapshot.addGroup(lastEntry);
                entryCount++;
            }
            
            // Set the count
            snapshot.set(new NoMDEntries(entryCount));
            
            // Send the message
            Session.sendToTarget(snapshot, sessionId);
            
            log.debug("Sent market data snapshot for {} to {}", 
                    marketData.getSymbol(), sessionId.getTargetCompID());
            
        } catch (Exception e) {
            log.error("Error sending market data snapshot", e);
        }
    }

    /**
     * Sends initial market data snapshot when a market maker connects.
     * 
     * @param sessionId The session identifier
     * @param config The market maker configuration
     */
    private void sendInitialSnapshot(SessionID sessionId, MarketMakerConfig config) {
        log.info("Sending initial market data snapshot to {}", config.getName());
        
        // Initial snapshot sending would be triggered by market data events
    }

    /**
     * Handles market data events published by the MarketDataService.
     * This decouples the FIX application from the service layer.
     * 
     * @param event The market data event
     */
    @EventListener
    public void handleMarketDataEvent(MarketDataEvent event) {
        MarketData marketData = event.getMarketData();
        broadcastMarketData(marketData);
    }
    
    /**
     * Broadcasts market data to all subscribed sessions.
     * 
     * @param marketData The market data to broadcast
     */
    private void broadcastMarketData(MarketData marketData) {
        String symbol = marketData.getSymbol();
        
        // Send to all sessions that subscribe to this symbol
        for (Map.Entry<SessionID, Map<String, String>> entry : subscriptions.entrySet()) {
            SessionID sessionId = entry.getKey();
            Map<String, String> sessionSubs = entry.getValue();
            
            // Check if this session subscribes to this symbol
            for (Map.Entry<String, String> sub : sessionSubs.entrySet()) {
                if (sub.getValue().equals(symbol)) {
                    String mdReqId = sub.getKey().split("_")[0];
                    sendMarketDataSnapshot(sessionId, mdReqId, marketData);
                }
            }
        }
    }
}
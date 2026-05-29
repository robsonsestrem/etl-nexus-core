package com.etl.importer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(MongoConnectionManager.class);

    public enum ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private static ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;
    private static String lastError = "";

    private final MongoTemplate mongoTemplate;

    @Autowired
    public MongoConnectionManager(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public boolean validateConnection() {
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            connectionStatus = ConnectionStatus.CONNECTED;
            lastError = "";
            logger.info("MongoDB connection validated successfully.");
            return true;
        } catch (Exception e) {
            connectionStatus = ConnectionStatus.ERROR;
            lastError = e.getMessage();
            logger.error("MongoDB connection validation failed: {}", lastError);
            return false;
        }
    }

    public String getStatus() {
        return connectionStatus.name();
    }

    public String getLastError() {
        return lastError;
    }
}


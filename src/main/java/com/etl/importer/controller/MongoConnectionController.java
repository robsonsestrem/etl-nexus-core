package com.etl.importer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.etl.importer.config.MongoConnectionManager;

import java.util.Map;

@RestController
@RequestMapping("/api/mongo")
public class MongoConnectionController {

    private static final Logger log = LoggerFactory.getLogger(MongoConnectionController.class);

    private final MongoConnectionManager connectionManager;

    public MongoConnectionController(MongoConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStatus() {
        log.info("GET /api/mongo/status called");
        try {
            var status = connectionManager.getStatus();
            var lastError = connectionManager.getLastError();
            var response = Map.of(
                "status", status,
                "lastError", lastError != null ? lastError : ""
            );

            log.debug("Status response: {}", response);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting status", e);
            var errorResponse = Map.of(
                "status", "ERROR",
                "lastError", e.getMessage() != null ? e.getMessage() : "Unknown error"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect() {
        log.info("POST /api/mongo/connect called");
        try {
            boolean validationResult = connectionManager.validateConnection();
            var success = validationResult;
            var status = validationResult ? "CONNECTED" : "FAILED";
            var message = validationResult ? "Connection successful" : "Connection failed";
            Map<String, Object> response = Map.of(
                "success", success,
                "status", status,
                "message", message != null ? message : ""
            );

            log.debug("Connect response: {}", response);
            
            if (success) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } catch (Exception e) {
            log.error("Error during connect", e);
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "status", "ERROR",
                "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
package org.example.yasspfe.controllers;

import org.example.yasspfe.entities.DatabaseConfig;
import org.example.yasspfe.scenarios.DatabaseStressTester;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.yasspfe.scenarios.MySQLProxy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "*") // Enable CORS for all origins (for development)
public class DatabaseConfigController {

    private final DatabaseStressTester stressTester;

    @Autowired
    public DatabaseConfigController(DatabaseStressTester stressTester) {
        this.stressTester = stressTester;
    }

    @PostMapping("/stress-test")
    public ResponseEntity<?> configureStressTest(@RequestBody DatabaseConfig config) {
        // Add more detailed logging
        System.out.println("[BACKEND] Received stress test configuration:");
        System.out.println("[BACKEND] JDBC URL: " + config.getJdbcUrl());
        System.out.println("[BACKEND] Username: " + config.getUsername());
        System.out.println("[BACKEND] Password set: " + (config.getPassword() != null));

        MySQLProxy.setStressTesterConnectionInfo(config.getJdbcUrl(), config.getUsername(), config.getPassword());
        System.out.println("[BACKEND] Stress test configuration received and stored (via /stress-test).");

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Stress test configuration stored successfully.");
        response.put("frontendConfigured", MySQLProxy.isFrontendConfigured());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stop-stress-test")
    public ResponseEntity<String> stopStressTest() {
        System.out.println("[BACKEND] Stop stress test request received");

        if (MySQLProxy.getStressTester().isRunning()) {
            System.out.println("[BACKEND] Stress test is running, stopping it now");
            boolean stopped = MySQLProxy.getStressTester().stopStressTest();
            System.out.println("[BACKEND] Stop result: " + stopped);
            return ResponseEntity.ok("Stress test stop initiated. Result: " + stopped);
        } else {
            System.out.println("[BACKEND] Stress test is not currently running");
            return ResponseEntity.ok("Stress test is not currently running.");
        }
    }

    @PostMapping("/test-connection")
    public ResponseEntity<?> testConnection(@RequestBody DatabaseConfig config) {
        System.out.println("[BACKEND] Testing connection with:");
        System.out.println("[BACKEND] JDBC URL: " + config.getJdbcUrl());
        System.out.println("[BACKEND] Username: " + config.getUsername());

        try (Connection connection = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            // Connection successful, store the information in MySQLProxy
            MySQLProxy.setStressTesterConnectionInfo(config.getJdbcUrl(), config.getUsername(), config.getPassword());
            System.out.println("[BACKEND] Connection test successful. Configuration stored.");

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Connection successful! Configuration stored.");
            response.put("frontendConfigured", MySQLProxy.isFrontendConfigured());
            return ResponseEntity.ok(response);
        } catch (SQLException e) {
            System.err.println("[BACKEND] Connection test failed: " + e.getMessage());

            Map<String, String> response = new HashMap<>();
            response.put("message", "Connection failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
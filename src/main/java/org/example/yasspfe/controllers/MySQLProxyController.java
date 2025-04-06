package org.example.yasspfe.controllers;

import org.example.yasspfe.entities.MySQLProxyConfig;
import org.example.yasspfe.services.MySQLProxyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Map;

@RestController
@RequestMapping("/api/proxy")
@CrossOrigin(origins = "*")
public class MySQLProxyController {

    @Autowired
    private MySQLProxyService proxyService;

    @GetMapping("/config")
    public ResponseEntity<MySQLProxyConfig> getProxyConfig() {
        return ResponseEntity.ok(proxyService.getProxyConfig());
    }

    @PostMapping("/config")
    public ResponseEntity<?> saveProxyConfig(@RequestBody MySQLProxyConfig proxyConfig) {
        try {
            // Validate port range
            if (proxyConfig.getPort() < 1 || proxyConfig.getPort() > 65535) {
                return ResponseEntity.badRequest().body("Port must be between 1 and 65535");
            }

            // Validate host is not empty
            if (proxyConfig.getHost() == null || proxyConfig.getHost().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Host cannot be empty");
            }

            MySQLProxyConfig saved = proxyService.saveProxyConfig(proxyConfig);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error saving configuration: " + e.getMessage());
        }
    }

    // Changed to GET for simplicity - this avoids potential issues with empty POST bodies
    @GetMapping("/start")
    public ResponseEntity<String> startProxy() {
        try {
            System.out.println("[MySQLProxyController] Received request to start proxy");

            MySQLProxyConfig config = proxyService.getProxyConfig();
            if (config == null) {
                return ResponseEntity.badRequest().body("No proxy configuration found");
            }

            System.out.println("[MySQLProxyController] Using config: " + config);

            boolean started = proxyService.startProxy();

            if (started) {
                return ResponseEntity.ok("Proxy started with configuration: " + config.getHost() + ":" + config.getPort());
            } else {
                return ResponseEntity.badRequest().body("Failed to start proxy. Check logs for details.");
            }
        } catch (Exception e) {
            System.err.println("[MySQLProxyController] Error starting proxy: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error starting proxy: " + e.getMessage());
        }
    }

    // Changed to GET for simplicity
    @GetMapping("/stop")
    public ResponseEntity<String> stopProxy() {
        try {
            boolean stopped = proxyService.stopProxy();
            if (stopped) {
                return ResponseEntity.ok("Proxy stopped successfully.");
            } else {
                return ResponseEntity.ok("Proxy was not running.");
            }
        } catch (Exception e) {
            System.err.println("[MySQLProxyController] Error stopping proxy: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error stopping proxy: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getProxyStatus() {
        boolean isRunning = proxyService.isProxyRunning();
        MySQLProxyConfig config = proxyService.getProxyConfig();

        return ResponseEntity.ok(
                Map.of(
                        "running", isRunning,
                        "config", config
                )
        );
    }

    // Accept any JSON object with host and port fields
    @PostMapping(value = "/test-connection", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> testConnection(@RequestBody String requestBody) {
        try {
            // Log the raw request body for debugging
            System.out.println("Received raw test connection request body: " + requestBody);

            // Parse the request body manually
            String host = null;
            int port = 0;

            // Simple parsing for debugging
            if (requestBody.contains("\"host\"")) {
                int hostStart = requestBody.indexOf("\"host\"") + 7;
                int hostEnd = requestBody.indexOf("\"", hostStart + 1);
                host = requestBody.substring(hostStart, hostEnd).trim();

                int portStart = requestBody.indexOf("\"port\"") + 7;
                int portEnd = requestBody.indexOf(",", portStart);
                if (portEnd == -1) {
                    portEnd = requestBody.indexOf("}", portStart);
                }
                String portStr = requestBody.substring(portStart, portEnd).trim();
                portStr = portStr.replace("\"", ""); // Remove quotes if present
                port = Integer.parseInt(portStr);
            }

            System.out.println("Parsed connection parameters - host: " + host + ", port: " + port);

            // Validate port range
            if (port < 1 || port > 65535) {
                return ResponseEntity.badRequest().body("Port must be between 1 and 65535");
            }

            // Validate host is not empty
            if (host == null || host.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Host cannot be empty");
            }

            boolean reachable = proxyService.isDatabaseReachable(host, port);
            if (reachable) {
                return ResponseEntity.ok("Connection successful to " + host + ":" + port);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Connection failed to " + host + ":" + port + ". Database is not reachable.");
            }
        } catch (Exception e) {
            System.err.println("Error testing connection: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error testing connection: " + e.getMessage());
        }
    }
}


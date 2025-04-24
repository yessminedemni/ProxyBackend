package org.example.yasspfe.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
@RestController
@RequestMapping("/api/configuration")
@CrossOrigin(origins = "*")
public class ApplicationProxyController {

    @Autowired
    private DataSource dataSource;

    // Get application configuration
    @GetMapping(value = "/app-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAppConfig() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT host, port, app_name FROM app_config ORDER BY id DESC LIMIT 1")) {
            if (rs.next()) {
                Map<String, Object> result = new HashMap<>();
                result.put("host", rs.getString("host"));
                result.put("port", rs.getInt("port"));
                result.put("appName", rs.getString("app_name"));
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No application configuration found."));
            }
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to fetch application configuration: " + e.getMessage()));
        }
    }

    // Save application configuration
    @PostMapping(value = "/app-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveAppConfig(@RequestBody Map<String, Object> request) {
        try {
            // Log the incoming request for debugging
            System.out.println("Received app config request: " + request);

            // Extract values with proper type checking
            String host = request.get("host") != null ? String.valueOf(request.get("host")) : null;
            Integer port = null;
            try {
                if (request.get("port") != null) {
                    if (request.get("port") instanceof Integer) {
                        port = (Integer) request.get("port");
                    } else if (request.get("port") instanceof String) {
                        port = Integer.parseInt((String) request.get("port"));
                    } else {
                        port = Integer.parseInt(String.valueOf(request.get("port")));
                    }
                }
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid port format: " + e.getMessage()));
            }

            String appName = request.get("appName") != null ? String.valueOf(request.get("appName")) : null;

            // Validate required fields
            if (host == null || port == null || appName == null || host.isEmpty() || appName.isEmpty() || port <= 0 || port > 65535) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid application configuration data.",
                        "host", host != null ? host : "null",
                        "port", port != null ? port.toString() : "null",
                        "appName", appName != null ? appName : "null"
                ));
            }

            // Insert into database with proper error handling
            try (Connection conn = dataSource.getConnection()) {
                PreparedStatement ps = conn.prepareStatement("INSERT INTO app_config (host, port, app_name) VALUES (?, ?, ?)");
                ps.setString(1, host);
                ps.setInt(2, port);
                ps.setString(3, appName);
                ps.executeUpdate();

                return ResponseEntity.ok(Map.of(
                        "message", "Application configuration saved successfully",
                        "host", host,
                        "port", port,
                        "appName", appName
                ));
            } catch (SQLException e) {
                e.printStackTrace(); // Log the full stack trace
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "error", "Failed to save application configuration: " + e.getMessage(),
                        "sqlState", e.getSQLState() != null ? e.getSQLState() : "unknown"
                ));
            }
        } catch (Exception e) {
            e.printStackTrace(); // Log any unexpected exceptions
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Unexpected error processing request: " + e.getMessage()
            ));
        }
    }




    // Test application proxy connection
    // Only showing the method that needs fixing
    @PostMapping(value = "/test-app-proxy", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> testAppProxyConnection(@RequestBody Map<String, Object> config) {
        String targetHost = (String) config.get("targetHost");
        String targetPortStr = (String) config.get("targetPort");  // Received as String
        String proxyPortStr = (String) config.get("proxyPort");    // Received as String

        if (targetHost == null || targetPortStr == null || proxyPortStr == null ||
                targetHost.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid proxy configuration."));
        }

        try {
            // Parsing the strings to integers
            Integer targetPort = Integer.parseInt(targetPortStr);
            Integer proxyPort = Integer.parseInt(proxyPortStr);

            if (targetPort <= 0 || targetPort > 65535 || proxyPort <= 0 || proxyPort > 65535) {
                return ResponseEntity.badRequest().body(Map.of("error", "Port values are out of range."));
            }

            // Log the received values for debugging
            System.out.println("Received proxy config: targetHost=" + targetHost +
                    ", targetPort=" + targetPort + ", proxyPort=" + proxyPort);

            // Save the proxy config temporarily
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO application_proxy_config (host, port, proxy_port) VALUES (?, ?, ?)")) {
                stmt.setString(1, targetHost);
                stmt.setInt(2, targetPort);
                stmt.setInt(3, proxyPort);
                stmt.executeUpdate();
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Proxy configuration test successful",
                    "targetHost", targetHost,
                    "targetPort", targetPort,
                    "proxyPort", proxyPort));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid port values, must be integers: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace(); // Add detailed logging for troubleshooting
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to test proxy connection: " + e.getMessage()));
        }
    }


    // Stop application proxy
    @PostMapping(value = "/stop-app-proxy", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> stopAppProxy() {
        try {
            return ResponseEntity.ok(Map.of("message", "Application proxy stopped successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to stop proxy: " + e.getMessage()));
        }
    }



    // Test application connection with enhanced feedback
    @PostMapping(value = "/test-app-connection", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> testAppConnection(@RequestBody Map<String, Object> request) {
        try {
            // Log the incoming request for debugging
            System.out.println("Received test app connection request: " + request);

            // Extract values with proper type checking
            String host = request.get("host") != null ? String.valueOf(request.get("host")) : null;
            Integer port = null;
            try {
                if (request.get("port") != null) {
                    if (request.get("port") instanceof Integer) {
                        port = (Integer) request.get("port");
                    } else if (request.get("port") instanceof String) {
                        port = Integer.parseInt((String) request.get("port"));
                    } else {
                        port = Integer.parseInt(String.valueOf(request.get("port")));
                    }
                }
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid port format: " + e.getMessage()));
            }

            // Get additional connection parameters
            String endpoint = request.get("healthEndpoint") != null ?
                    String.valueOf(request.get("healthEndpoint")) :
                    (request.get("endpoint") != null ? String.valueOf(request.get("endpoint")) : null);

            String apiPath = request.get("apiPath") != null ? String.valueOf(request.get("apiPath")) : "";
            String authType = request.get("authType") != null ? String.valueOf(request.get("authType")) : "None";
            String username = request.get("username") != null ? String.valueOf(request.get("username")) : null;
            String password = request.get("password") != null ? String.valueOf(request.get("password")) : null;
            String appName = request.get("appName") != null ? String.valueOf(request.get("appName")) : "Unknown App";

            // Validate required fields
            if (host == null || port == null || host.isEmpty() || port <= 0 || port > 65535) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid connection parameters.",
                        "host", host != null ? host : "null",
                        "port", port != null ? port.toString() : "null"
                ));
            }

            // If no endpoint specified, use a default health check endpoint
            if (endpoint == null || endpoint.isEmpty()) {
                endpoint = "/actuator/health";
            }

            try {
                String urlString = "http://" + host + ":" + port;

                // Add API path if provided
                if (apiPath != null && !apiPath.isEmpty()) {
                    if (!apiPath.startsWith("/")) {
                        urlString += "/";
                    }
                    urlString += apiPath;
                }

                // Add health endpoint for testing
                if (!endpoint.startsWith("/")) {
                    urlString += "/";
                }
                urlString += endpoint;

                System.out.println("Testing connection to: " + urlString);

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                // Add authentication if needed
                if ("Basic".equals(authType) && username != null && password != null) {
                    String auth = username + ":" + password;
                    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                    connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
                }

                int responseCode = connection.getResponseCode();

                // Get response message if available
                String responseMessage = connection.getResponseMessage();

                // Try to read response body for more information
                StringBuilder responseBody = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        responseCode < 400 ? connection.getInputStream() : connection.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBody.append(line);
                    }
                } catch (Exception e) {
                    // Ignore errors reading response body
                }

                connection.disconnect();

                System.out.println("Connection test response code: " + responseCode);

                // Save connection attempt to database for history
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                             "INSERT INTO connection_history (app_name, host, port, url, response_code, success, timestamp) VALUES (?, ?, ?, ?, ?, ?, NOW())")) {
                    stmt.setString(1, appName);
                    stmt.setString(2, host);
                    stmt.setInt(3, port);
                    stmt.setString(4, urlString);
                    stmt.setInt(5, responseCode);
                    stmt.setBoolean(6, responseCode >= 200 && responseCode < 400);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    // Log but don't fail if history recording fails
                    System.err.println("Failed to record connection history: " + e.getMessage());
                }

                if (responseCode >= 200 && responseCode < 400) {
                    return ResponseEntity.ok(Map.of(
                            "message", "Connection successful",
                            "responseCode", responseCode,
                            "responseMessage", responseMessage,
                            "responseBody", responseBody.toString(),
                            "url", urlString,
                            "timestamp", System.currentTimeMillis(),
                            "appName", appName
                    ));
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                            "error", "Connection test returned error code: " + responseCode,
                            "responseMessage", responseMessage,
                            "responseBody", responseBody.toString(),
                            "url", urlString
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace(); // Log the full stack trace
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "error", "Connection failed: " + e.getMessage()
                ));
            }
        } catch (Exception e) {
            e.printStackTrace(); // Log any unexpected exceptions
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Unexpected error processing request: " + e.getMessage()
            ));
        }
    }

    // Add this method to your ApplicationProxyController class
    @PostMapping(value = "/update-proxy-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProxyConfig(@RequestBody Map<String, Object> config) {
        try {
            // Log the incoming request for debugging
            System.out.println("Received update proxy config request: " + config);

            // Extract values with proper type checking
            String host = config.get("host") != null ? String.valueOf(config.get("host")) : null;
            Integer port = null;
            Integer proxyPort = null;

            try {
                if (config.get("port") != null) {
                    if (config.get("port") instanceof Integer) {
                        port = (Integer) config.get("port");
                    } else if (config.get("port") instanceof String) {
                        port = Integer.parseInt((String) config.get("port"));
                    } else {
                        port = Integer.parseInt(String.valueOf(config.get("port")));
                    }
                }

                if (config.get("proxyPort") != null) {
                    if (config.get("proxyPort") instanceof Integer) {
                        proxyPort = (Integer) config.get("proxyPort");
                    } else if (config.get("proxyPort") instanceof String) {
                        proxyPort = Integer.parseInt((String) config.get("proxyPort"));
                    } else {
                        proxyPort = Integer.parseInt(String.valueOf(config.get("proxyPort")));
                    }
                }
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid port format: " + e.getMessage()));
            }

            // Validate required fields
            if (host == null || port == null || proxyPort == null ||
                    host.isEmpty() || port <= 0 || port > 65535 || proxyPort <= 0 || proxyPort > 65535) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid proxy configuration parameters.",
                        "host", host != null ? host : "null",
                        "port", port != null ? port.toString() : "null",
                        "proxyPort", proxyPort != null ? proxyPort.toString() : "null"
                ));
            }

            // Update the database with the new configuration
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO application_proxy_config (host, port, proxy_port) VALUES (?, ?, ?)")) {
                stmt.setString(1, host);
                stmt.setInt(2, port);
                stmt.setInt(3, proxyPort);
                stmt.executeUpdate();

                System.out.println("Updated proxy configuration in database: host=" + host +
                        ", port=" + port + ", proxyPort=" + proxyPort);

                return ResponseEntity.ok(Map.of(
                        "message", "Proxy configuration updated successfully",
                        "host", host,
                        "port", port,
                        "proxyPort", proxyPort
                ));
            } catch (SQLException e) {
                e.printStackTrace(); // Log the full stack trace
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "error", "Failed to update proxy configuration: " + e.getMessage(),
                        "sqlState", e.getSQLState() != null ? e.getSQLState() : "unknown"
                ));
            }
        } catch (Exception e) {
            e.printStackTrace(); // Log any unexpected exceptions
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Unexpected error processing request: " + e.getMessage()
            ));
        }
    }

}

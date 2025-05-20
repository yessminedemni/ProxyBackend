package org.example.yasspfe.appscenrios;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ApplicationProxy {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/proxybase";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";

    private static String targetHost = "localhost";
    private static int targetPort = 8080;
    private static int proxyPort = 3303;

    private static final Map<String, Boolean> scenarios = new ConcurrentHashMap<>();

    // Scenarios instances
    private static HighLoadScenario highLoadScenario;

    public static void main(String[] args) {
        try {
            setupDatabase();
            updateScenarios();
            updateTargetConfig();

            // Initialize scenarios
            highLoadScenario = new HighLoadScenario(targetHost, targetPort);

            // Make sure ServiceDownScenario has correct target configuration
            ServiceDownScenario.updateTargetConfig(targetHost, targetPort);

            new Thread(() -> {
                while (true) {
                    try {
                        updateScenarios();
                        updateTargetConfig();

                        // Update ServiceDownScenario with latest target configuration
                        ServiceDownScenario.updateTargetConfig(targetHost, targetPort);

                        // High load scenario control
                        boolean highLoadEnabled = isScenarioEnabled("high_load");
                        if (highLoadEnabled && !highLoadScenario.isActive()) {
                            highLoadScenario.startHighLoad();
                        } else if (!highLoadEnabled && highLoadScenario.isActive()) {
                            highLoadScenario.stopHighLoad();
                        }

                        // CPU load scenario control
                        boolean cpuLoadEnabled = isScenarioEnabled("cpu_load");
                        if (cpuLoadEnabled) {
                            CPULoadHandler.startCpuLoad(60); // 60 seconds of CPU load
                        } else {
                            CPULoadHandler.stopCpuLoad();
                        }

                        // Memory load scenario control
                        boolean memoryLoadEnabled = isScenarioEnabled("memory_load");
                        if (memoryLoadEnabled && !MemoryLoadScenario.isActive()) {
                            MemoryLoadScenario.startMemoryLoad();
                        } else if (!memoryLoadEnabled && MemoryLoadScenario.isActive()) {
                            MemoryLoadScenario.stopMemoryLoad();
                        }

                        // Service down scenario control
                        boolean serviceDownEnabled = isScenarioEnabled("service_down");
                        if (serviceDownEnabled) {
                            ServiceDownScenario.startServiceDown();
                        } else {
                            ServiceDownScenario.stopServiceDown();
                        }

                        // Database down scenario control
                        boolean dbDownEnabled = isScenarioEnabled("db_down");
                        if (dbDownEnabled) {
                            DatabaseDownScenario.startDbDown();
                        } else {
                            DatabaseDownScenario.stopDbDown();
                        }

                        Thread.sleep(1000); // Check scenarios every second
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        System.err.println("[ApplicationProxy] Error in update thread: " + e.getMessage());
                    }
                }
            }).start();

            ServerSocket serverSocket = new ServerSocket(proxyPort);
            System.out.println("[ApplicationProxy] Listening on port " + proxyPort);

            while (true) {
                try {
                    final Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleConnection(clientSocket)).start();
                } catch (Exception e) {
                    System.err.println("[ApplicationProxy] Error handling connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[ApplicationProxy] Critical error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleConnection(final Socket clientSocket) {
        // First check if service down scenario is active - if so, handle it accordingly
        if (ServiceDownScenario.isServiceDown()) {
            System.out.println("[ApplicationProxy] Service down scenario active - sending 503 response");
            ServiceDownScenario.handleServiceDowntime(clientSocket);
            return; // Important: exit early to prevent forwarding the request
        }

        Socket targetSocket = null;
        try {
            // Handle DB downtime scenario before establishing connection
            if (DatabaseDownScenario.isDbDown()) {
                System.out.println("[ApplicationProxy] Database down scenario active");
                DatabaseDownScenario.handleDbDowntime();
            }

            targetSocket = new Socket(targetHost, targetPort);
            final Socket finalTargetSocket = targetSocket;
            final AtomicReference<ConnectionState> state = new AtomicReference<>(new ConnectionState());

            // Force immediate update of scenarios before processing the request
            updateScenarios();

            // Start the threads for handling the proxy connection
            Thread clientToServerThread = new Thread(() -> forwardClientToServer(clientSocket, finalTargetSocket, state));
            Thread serverToClientThread = new Thread(() -> forwardServerToClient(finalTargetSocket, clientSocket, state));

            clientToServerThread.start();
            serverToClientThread.start();

            clientToServerThread.join();
            serverToClientThread.join();

        } catch (Exception e) {
            System.err.println("[handleConnection] Error: " + e.getMessage());
            try {
                // Send a proper error response back to the client if connection to target fails
                if (clientSocket != null && !clientSocket.isClosed()) {
                    sendErrorResponse(clientSocket, 502, "Bad Gateway: " + e.getMessage());
                }
            } catch (IOException ioe) {
                System.err.println("[handleConnection] Failed to send error response: " + ioe.getMessage());
            }
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
            } catch (IOException e) {
                System.err.println("[handleConnection] Error closing client socket: " + e.getMessage());
            }
            try {
                if (targetSocket != null && !targetSocket.isClosed()) targetSocket.close();
            } catch (IOException e) {
                System.err.println("[handleConnection] Error closing target socket: " + e.getMessage());
            }
        }
    }

    private static void sendErrorResponse(Socket clientSocket, int statusCode, String message) throws IOException {
        OutputStream out = clientSocket.getOutputStream();
        String statusText = statusCode == 500 ? "Internal Server Error" :
                (statusCode == 502 ? "Bad Gateway" : "Service Unavailable");

        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Type: application/json\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}";

        out.write(response.getBytes());
        out.flush();
    }

    private static void setupDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Create the app_scenarios table if it doesn't exist
            stmt.execute("CREATE TABLE IF NOT EXISTS app_scenarios (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL UNIQUE, " +
                    "enabled BOOLEAN DEFAULT FALSE, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Create the application_proxy_config table if it doesn't exist
            stmt.execute("CREATE TABLE IF NOT EXISTS application_proxy_config (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "host VARCHAR(255) NOT NULL, " +
                    "port INT NOT NULL, " +
                    "proxy_port INT NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Insert default scenarios into the app_scenarios table if the table is empty
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM app_scenarios");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO app_scenarios (name, enabled) VALUES ('return_404', false)");
                stmt.execute("INSERT INTO app_scenarios (name, enabled) VALUES ('cpu_load', false)");
                stmt.execute("INSERT INTO app_scenarios (name, enabled) VALUES ('high_load', false)");
                stmt.execute("INSERT INTO app_scenarios (name, enabled) VALUES ('memory_load', false)");
                stmt.execute("INSERT INTO app_scenarios (name, enabled) VALUES ('db_down', false)");
                stmt.execute("INSERT INTO app_scenarios (name, enabled) VALUES ('service_down', false)");
            } else {
                // Check if service_down scenario exists, if not add it
                rs = stmt.executeQuery("SELECT COUNT(*) FROM app_scenarios WHERE name = 'service_down'");
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO app_scenarios (name, enabled) VALUES ('service_down', false)");
                    System.out.println("[ApplicationProxy] Added service_down scenario to database");
                }
            }

            // Insert default proxy configuration if it doesn't exist
            rs = stmt.executeQuery("SELECT COUNT(*) FROM application_proxy_config");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO application_proxy_config (host, port, proxy_port) VALUES ('localhost', 8080, 3303)");
            }

            System.out.println("[ApplicationProxy] Database setup completed");

        } catch (SQLException e) {
            System.err.println("[ApplicationProxy] Database setup error: " + e.getMessage());
        }
    }

    private static void updateTargetConfig() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT host, port, proxy_port FROM application_proxy_config ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                targetHost = rs.getString("host");
                targetPort = rs.getInt("port");
                proxyPort = rs.getInt("proxy_port");
                System.out.println("[ApplicationProxy] Updated target to " + targetHost + ":" + targetPort + " with proxy on port " + proxyPort);

                // Update HighLoadScenario with new target if it exists
                if (highLoadScenario != null) {
                    // Restart with new configuration if active
                    boolean wasActive = highLoadScenario.isActive();
                    if (wasActive) {
                        highLoadScenario.stopHighLoad();
                    }
                    highLoadScenario = new HighLoadScenario(targetHost, targetPort);
                    if (wasActive && isScenarioEnabled("high_load")) {
                        highLoadScenario.startHighLoad();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ApplicationProxy] Error reading target config: " + e.getMessage());
        }
    }

    private static synchronized void updateScenarios() {
        if (DatabaseDownScenario.isDbDown()) {
            System.out.println("[ApplicationProxy] Database is down, skipping scenario update");
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT name, enabled FROM app_scenarios");
            Map<String, Boolean> newScenarios = new ConcurrentHashMap<>();
            while (rs.next()) {
                newScenarios.put(rs.getString("name"), rs.getBoolean("enabled"));
            }
            scenarios.clear();
            scenarios.putAll(newScenarios);

            System.out.println("[ApplicationProxy] Updated scenarios: " + scenarios);

        } catch (SQLException e) {
            System.err.println("[updateScenarios] Error loading scenarios: " + e.getMessage());
        }
    }

    private static boolean isScenarioEnabled(String name) {
        return scenarios.getOrDefault(name, false);
    }

    private static void forwardClientToServer(final Socket clientSocket, final Socket targetSocket, final AtomicReference<ConnectionState> state) {
        try (InputStream clientIn = clientSocket.getInputStream();
             OutputStream targetOut = targetSocket.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int len;

            while ((len = clientIn.read(buffer)) != -1) {
                String path = extractPathFromHttp(buffer, len);
                state.get().path = path;

                // Check for 404 scenario
                if (isScenarioEnabled("return_404")) {
                    Return404Scenario.apply(clientSocket, path);
                    return;
                }

                // Forward the original request
                targetOut.write(buffer, 0, len);
                targetOut.flush();
            }
        } catch (IOException e) {
            System.err.println("[forwardClientToServer] Client to server error: " + e.getMessage());
        }
    }

    private static void forwardServerToClient(final Socket targetSocket, final Socket clientSocket, final AtomicReference<ConnectionState> state) {
        try {
            InputStream targetIn = targetSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();

            byte[] buffer = new byte[4096];
            int len;

            while ((len = targetIn.read(buffer)) != -1) {
                clientOut.write(buffer, 0, len);
                clientOut.flush();
            }
        } catch (IOException e) {
            System.err.println("[forwardServerToClient] Server to client error: " + e.getMessage());
        }
    }

    private static String extractPathFromHttp(byte[] buffer, int length) {
        String request = new String(buffer, 0, length);
        String[] lines = request.split("\\r?\\n");
        if (lines.length > 0) {
            String[] parts = lines[0].split(" ");
            if (parts.length > 1) return parts[1];
        }
        return "/unknown";
    }

    public static class ConnectionState {
        public String path = "/unknown";
    }
}
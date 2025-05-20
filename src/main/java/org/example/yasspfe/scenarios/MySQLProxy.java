package org.example.yasspfe.scenarios;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class MySQLProxy {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/proxybase";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";
    private static final Map<String, Boolean> scenarios = new HashMap<>();
    private static DatabaseStressTester stressTester = new DatabaseStressTester();
    private static final QueryBlackholeInjector queryBlackholeInjector = new QueryBlackholeInjector();
    private static final ConnectionKillInjector connectionKillInjector = new ConnectionKillInjector();
    private static final DiskFaultInjector diskFaultInjector = new DiskFaultInjector();
    private static final LatencyInjector latencyInjector = new LatencyInjector();
    private static final PacketLossInjector packetLossInjector = new PacketLossInjector(0.1); // 10% loss rate

    private static String targetHost = "localhost"; // Default value
    private static int targetPort = 3306;
    private static boolean frontendConfigured = false; // Flag to track if frontend config is set

    public static void setTargetConnectionInfo(String host, int port) {
        targetHost = host;
        targetPort = port;
        System.out.println("[MySQLProxy] Target connection info set to " + host + ":" + port);
    }

    public static String getTargetHost() {
        return targetHost;
    }

    public static int getTargetPort() {
        return targetPort;
    }

    public static DatabaseStressTester getStressTester() {
        return stressTester;
    }

    // Method to set connection info for the stress tester
    public static void setStressTesterConnectionInfo(String jdbcUrl, String username, String password) {
        if (stressTester != null) {
            stressTester.setJdbcUrl(jdbcUrl);
            stressTester.setUsername(username);
            stressTester.setPassword(password);
            frontendConfigured = true;
            System.out.println("[MySQLProxy] Stress tester connection info set. frontendConfigured: " + frontendConfigured);
        } else {
            System.err.println("[MySQLProxy] Error: stressTester is not initialized.");
        }
    }

    public static boolean isFrontendConfigured() {
        return frontendConfigured;
    }

    public static void main(String[] args) throws IOException {
        setStressTesterConnectionInfo(DB_URL, DB_USER, DB_PASSWORD);
        updateScenariosOnce();
        updateTargetConnectionInfo();

        // Background thread to update scenarios and connection info
        new Thread(() -> {
            while (true) {
                updateScenariosOnce();
                updateTargetConnectionInfo();
                try {
                    Thread.sleep(5000); // Refresh every 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();

        ServerSocket proxyServer = new ServerSocket(3301);
        System.out.println("MySQL Proxy started on port 3301");

        while (true) {
            Socket clientSocket = proxyServer.accept();
            System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());

            Socket mysqlSocket = new Socket(targetHost, targetPort);
            System.out.println("Connected to target database at " + targetHost + ":" + targetPort);

            AtomicReference<ConnectionState> state = new AtomicReference<>(new ConnectionState());

            Thread clientToServer = new Thread(() -> forwardClientToServer(clientSocket, mysqlSocket, state));
            Thread serverToClient = new Thread(() -> forwardServerToClient(mysqlSocket, clientSocket, state));
            clientToServer.start();
            serverToClient.start();

            try {
                clientToServer.join();
                serverToClient.join();
            } catch (InterruptedException e) {
                System.err.println("Error in thread execution: " + e.getMessage());
            } finally {
                closeSockets(clientSocket, mysqlSocket);
            }
        }
    }

    private static void updateTargetConnectionInfo() {
        System.out.println("Fetching target database connection info...");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT host, port FROM proxy_config ORDER BY id DESC LIMIT 1");

            if (rs.next()) {
                String host = rs.getString("host");
                int port = rs.getInt("port");

                // Check if host or port has changed and if so, update connection
                if (!host.equals(targetHost) || port != targetPort) {
                    // Check if the new host and port are reachable before updating
                    if (!isDatabaseReachable(host, port)) {
                        System.err.println("Error: Cannot connect to database at " + host + ":" + port);
                        return; // Don't proceed if database is unreachable
                    }
                    setTargetConnectionInfo(host, port);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching target connection info: " + e.getMessage());
        }
    }

    private static boolean isDatabaseReachable(String host, int port) {
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port;
        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD)) {
            // If connection is established, database is reachable
            return true;
        } catch (SQLException e) {
            System.err.println("Error connecting to database: " + e.getMessage());
            return false;
        }
    }

    private static void updateScenariosOnce() {
        if (stressTester == null) {
            stressTester = new DatabaseStressTester(); // Ensure it's not null
        }

        // Validate the database connection before proceeding with scenarios
        if (!isDatabaseConnected()) {
            System.err.println("[MySQLProxy] Unable to connect to database. Skipping scenario updates.");
            return;  // Exit early if the connection is not valid
        }

        System.out.println("Fetching scenario settings from the database...");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT name, enabled FROM scenarios");

            synchronized (scenarios) {
                scenarios.clear();
                while (rs.next()) {
                    String scenarioName = rs.getString("name");
                    boolean isEnabled = false;

                    // Handle different types of enabled columns
                    Object enabledValue = rs.getObject("enabled");
                    if (enabledValue instanceof byte[]) {
                        byte[] bytes = (byte[]) enabledValue;
                        isEnabled = bytes.length > 0 && bytes[0] == 1;
                    } else if (enabledValue instanceof Boolean) {
                        isEnabled = (Boolean) enabledValue;
                    } else if (enabledValue instanceof Number) {
                        isEnabled = ((Number) enabledValue).intValue() == 1;
                    } else if (enabledValue instanceof String) {
                        isEnabled = "1".equals(enabledValue) ||
                                "true".equalsIgnoreCase((String) enabledValue) ||
                                "b'1'".equalsIgnoreCase((String) enabledValue);
                    }

                    // Log scenario name and enabled state
                    System.out.println("Fetched scenario: " + scenarioName + " is " + (isEnabled ? "enabled" : "disabled"));
                    scenarios.put(scenarioName, isEnabled);
                }
            }

            // Update injector states based on scenario settings
            boolean blackholeEnabled = scenarios.getOrDefault("query_blackhole", false);
            queryBlackholeInjector.setEnabled(blackholeEnabled);
            System.out.println("🛑 [Query Blackhole] Scenario enabled: " + blackholeEnabled);

            boolean killEnabled = scenarios.getOrDefault("connection_kill", false);
            connectionKillInjector.setEnabled(killEnabled);
            System.out.println("💣 [Connection Kill] Scenario enabled: " + killEnabled);

            boolean diskFaultEnabled = scenarios.getOrDefault("disk_fault_injection", false);
            diskFaultInjector.setEnabled(diskFaultEnabled);
            System.out.println("🗃️ [Disk Fault] Scenario enabled: " + diskFaultEnabled);

            boolean packetLossEnabled = scenarios.getOrDefault("packet_loss", false);
            packetLossInjector.setEnabled(packetLossEnabled);
            System.out.println("🔥 [Packet Loss] Scenario enabled: " + packetLossEnabled);

            boolean latencyEnabled = scenarios.getOrDefault("latency_injection", false);
            latencyInjector.setEnabled(latencyEnabled);
            System.out.println("⏱️ [Latency Injection] Scenario enabled: " + latencyEnabled);

            // Handle stress testing
            boolean stressTestingEnabled = scenarios.getOrDefault("stress_testing", false);

            System.out.println("💥 [Stress Test] Status check:");
            System.out.println("💥 [Stress Test] Scenario enabled in DB: " + stressTestingEnabled);
            System.out.println("💥 [Stress Test] Frontend configured: " + isFrontendConfigured());
            System.out.println("💥 [Stress Test] Currently running: " + stressTester.isRunning());

            if (stressTestingEnabled) {
                System.out.println("💥 [Stress Test] JDBC URL: " + stressTester.getJdbcUrl());
                System.out.println("💥 [Stress Test] Username: " + stressTester.getUsername());
                System.out.println("💥 [Stress Test] Password set: " + (stressTester.getPassword() != null));
            }

            if (!stressTestingEnabled && stressTester.isRunning()) {
                System.out.println("💥 [Stress Test] Stopping stress test from database update");
                stressTester.stopStressTest();
            } else if (stressTestingEnabled && isFrontendConfigured() && !stressTester.isRunning()) {
                System.out.println("💥 [Stress Test] Scenario enabled and configured - attempting start from updateScenariosOnce");
                boolean started = stressTester.startStressTest();
                System.out.println("💥 [Stress Test] Start attempt result: " + started);
            }
        } catch (SQLException e) {
            System.err.println("[MySQLProxy] Error fetching scenario settings: " + e.getMessage());
        }
    }

    private static boolean isDatabaseConnected() {
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://" + targetHost + ":" + targetPort + "/proxybase", DB_USER, DB_PASSWORD)) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[MySQLProxy] Connection failed: " + e.getMessage());
            return false;
        }
    }

    private static void forwardClientToServer(Socket clientSocket, Socket mysqlSocket, AtomicReference<ConnectionState> state) {
        try (InputStream clientIn = clientSocket.getInputStream();
             OutputStream mysqlOut = mysqlSocket.getOutputStream()) {

            while (!clientSocket.isClosed() && !mysqlSocket.isClosed()) {
                byte[] packet = readPacket(clientIn);
                if (packet == null) break;

                if (isScenarioEnabled("packet_loss") && packetLossInjector.shouldSuppressResponseAfterDb()) {
                    System.out.println("🔥 [Packet Loss] Suppressing server->client response (after DB)");
                    continue;
                }


                ConnectionState currentState = state.get();
                if (currentState.isHandshakeComplete() && isComQuery(packet)) {
                    String query = extractQuery(packet);
                    if (query != null) {
                        String queryType = getQueryType(query);
                        currentState.setCurrentQueryType(queryType);
                        currentState.setLastQuery(query); // Store for later use
                        System.out.println("Detected query: " + query + " (Type: " + queryType + ")");

                        if (isScenarioEnabled("latency_injection")) {
                            latencyInjector.injectLatencyBeforeQuery(query);
                        }

                        if (isScenarioEnabled("connection_kill") && connectionKillInjector.shouldKill(query)) {
                            System.out.println("💣 [Connection Kill] Killing connection for query: " + query);
                            connectionKillInjector.killConnection(clientSocket);
                            return;
                        }

                        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                            queryBlackholeInjector.updateLastQuery(query, conn);
                        } catch (SQLException e) {
                            System.err.println("[QueryBlackhole] DB logging failed: " + e.getMessage());
                        }

                        // ❌ Remove diskFaultInjector.shouldBlockQuery() from here!
                    }
                }

                mysqlOut.write(packet);
                mysqlOut.flush();
            }
        } catch (IOException e) {
            System.err.println("Client to server error: " + e.getMessage());
        }
    }



    private static void forwardServerToClient(Socket mysqlSocket, Socket clientSocket, AtomicReference<ConnectionState> state) {
        try (InputStream mysqlIn = mysqlSocket.getInputStream();
             OutputStream clientOut = clientSocket.getOutputStream()) {

            while (!mysqlSocket.isClosed() && !clientSocket.isClosed()) {
                byte[] packet = readPacket(mysqlIn);
                if (packet == null) break;

                ConnectionState currentState = state.get();

                if (!currentState.isHandshakeComplete() && isOkPacket(packet)) {
                    currentState.setHandshakeComplete(true);
                    System.out.println("[MySQLProxy] Handshake complete for a connection.");
                    attemptStartStressTest();
                }

                // ✅ Disk fault injection happens here AFTER the query has been executed by the DB
                if (isScenarioEnabled("disk_fault_injection")) {
                    String lastQuery = currentState.getLastQuery();
                    boolean dbExecutionSuccess = isOkPacket(packet); // MySQL OK packet

                    if (diskFaultInjector.shouldInjectError(lastQuery, dbExecutionSuccess)) {
                        System.out.println("🗃️ [Disk Fault] Injecting fake disk error AFTER DB execution.");
                        clientOut.write(diskFaultInjector.fakeDiskErrorPacket());
                        clientOut.flush();
                        continue; // skip sending real response
                    }
                }

                if (isScenarioEnabled("query_blackhole") && queryBlackholeInjector.shouldDropResponse()) {
                    System.out.println("🛑 [Query Blackhole] Dropping server->client response packet.");
                    continue;
                }

                // Handle post-DB packet loss (simulate response being lost AFTER DB processed query)
                if (isScenarioEnabled("packet_loss") && packetLossInjector.shouldSuppressResponseAfterDb()) {
                    System.out.println("🔥 [Packet Loss] Suppressing server->client response (after DB)");
                    continue;
                }



                if (!isScenarioEnabled("stress_testing") && stressTester.isRunning()) {
                    System.out.println("💥 [Stress Test] Stopping test as scenario is disabled");
                    stressTester.stopStressTest();
                }

                clientOut.write(packet);
                clientOut.flush();
            }
        } catch (IOException e) {
            System.err.println("Server to client error: " + e.getMessage());
        }
    }


    private static void attemptStartStressTest() {
        if (isScenarioEnabled("stress_testing") && isFrontendConfigured() && !stressTester.isRunning()) {
            System.out.println("💥 [Stress Test] Attempting to start stress test - Scenario: " + isScenarioEnabled("stress_testing") + ", Configured: " + isFrontendConfigured() + ", Running: " + stressTester.isRunning());
            boolean started = stressTester.startStressTest();
            System.out.println("💥 [Stress Test] Start attempt result: " + started);
        } else {
            System.out.println("[MySQLProxy] Conditions not met to start stress test - Scenario: " + isScenarioEnabled("stress_testing") + ", Configured: " + isFrontendConfigured() + ", Running: " + stressTester.isRunning());
        }
    }

    private static boolean isScenarioEnabled(String scenarioName) {
        synchronized (scenarios) {
            return scenarios.getOrDefault(scenarioName, false);
        }
    }

    private static byte[] readPacket(InputStream in) throws IOException {
        DataInputStream din = new DataInputStream(in);
        ByteArrayOutputStream completePacket = new ByteArrayOutputStream();

        while (true) {
            byte[] header = new byte[4];
            try {
                din.readFully(header);
            } catch (EOFException e) {
                return completePacket.size() > 0 ? completePacket.toByteArray() : null;
            }

            int length = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8) | ((header[2] & 0xFF) << 16);
            completePacket.write(header);
            byte[] payload = new byte[length];
            try {
                din.readFully(payload);
            } catch (EOFException e) {
                throw new IOException("Incomplete packet read: expected " + length + " bytes, got less", e);
            }
            completePacket.write(payload);

            if (length < 0xFFFFFF) break;
        }
        return completePacket.toByteArray();
    }

    private static boolean isComQuery(byte[] packet) {
        return packet.length > 4 && packet[4] == 0x03;
    }

    private static String extractQuery(byte[] packet) {
        return new String(packet, 5, packet.length - 5, StandardCharsets.UTF_8).trim();
    }

    private static boolean isOkPacket(byte[] packet) {
        return packet.length > 4 && packet[4] == 0x00;
    }

    private static String getQueryType(String query) {
        String upperQuery = query.toUpperCase().trim();
        if (upperQuery.startsWith("SELECT") || upperQuery.startsWith("SHOW") || upperQuery.startsWith("DESCRIBE")) {
            return "DQL";
        } else if (upperQuery.startsWith("INSERT") || upperQuery.startsWith("UPDATE") || upperQuery.startsWith("DELETE")) {
            return "DML";
        } else if (upperQuery.startsWith("CREATE") || upperQuery.startsWith("ALTER") || upperQuery.startsWith("DROP") || upperQuery.startsWith("TRUNCATE")) {
            return "DDL";
        } else if (upperQuery.startsWith("COMMIT") || upperQuery.startsWith("ROLLBACK") || upperQuery.startsWith("SAVEPOINT")) {
            return "TCL";
        } else if (upperQuery.startsWith("GRANT") || upperQuery.startsWith("REVOKE") || upperQuery.startsWith("SET PASSWORD")) {
            return "DCL";
        }
        return "OTHER";
    }

    private static void closeSockets(Socket... sockets) {
        for (Socket socket : sockets) {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    public static class ConnectionState {
        private String currentQueryType;
        private String lastQuery;
        private boolean addDelay;
        private boolean handshakeComplete = false;

        public synchronized String getCurrentQueryType() {
            return currentQueryType;
        }

        public synchronized void setCurrentQueryType(String type) {
            this.currentQueryType = type;
            this.addDelay = true;
        }

        public synchronized boolean isAddDelay() {
            return addDelay;
        }

        public synchronized void setAddDelay(boolean addDelay) {
            this.addDelay = addDelay;
        }

        public synchronized boolean isHandshakeComplete() {
            return handshakeComplete;
        }

        public synchronized void setHandshakeComplete(boolean complete) {
            this.handshakeComplete = complete;
        }

        public synchronized String getLastQuery() {
            return lastQuery;
        }

        public synchronized void setLastQuery(String lastQuery) {
            this.lastQuery = lastQuery;
        }
    }

}
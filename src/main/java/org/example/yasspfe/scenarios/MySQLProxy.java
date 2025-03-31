package org.example.yasspfe.scenarios;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class MySQLProxy {

    public MySQLProxy() {
        this.stressTester = new DatabaseStressTester(); // Ensure this line exists
    }


    private static final String DB_URL = "jdbc:mysql://localhost:3306/proxybase";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";
    private static final Map<String, Boolean> scenarios = new HashMap<>();
    private static DatabaseStressTester stressTester;

    // Fetch the singleton instance of DatabaseStressTester
    public static DatabaseStressTester getStressTester() {
        if (stressTester == null) {
            stressTester = new DatabaseStressTester();  // Initialize the stressTester if it hasn't been initialized yet
        }
        return stressTester;
    }

    // Make the stress tester static AND singleton, to be accessible from controller
    private static final PacketLossInjector packetLossInjector = new PacketLossInjector(0.1); // 10% loss rate


    public static void main(String[] args) throws IOException {
        updateScenariosOnce();
        new Thread(() -> {
            while (true) {
                updateScenariosOnce();
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

            Socket mysqlSocket = new Socket("localhost", 3306);
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

    private static void updateScenariosOnce() {
        if (stressTester == null) {
            stressTester = new DatabaseStressTester(); // Ensure it's not null
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

            // Check if stress testing is disabled in the database and stop it if necessary
            boolean stressTestingEnabled = scenarios.getOrDefault("stress_testing", false);
            if (!stressTestingEnabled && stressTester.isRunning()) {
                System.out.println("💥 [Stress Test] Stopping stress test from database update");
                stressTester.stopStressTest();
            }

            System.out.println("Scenarios fetched and updated.");
        } catch (SQLException e) {
            System.err.println("Error fetching scenario settings: " + e.getMessage());
        }
    }

    private static void forwardClientToServer(Socket clientSocket, Socket mysqlSocket, AtomicReference<ConnectionState> state) {
        try (InputStream clientIn = clientSocket.getInputStream();
             OutputStream mysqlOut = mysqlSocket.getOutputStream()) {

            while (!clientSocket.isClosed() && !mysqlSocket.isClosed()) {
                byte[] packet = readPacket(clientIn);
                if (packet == null) break;
                if (isScenarioEnabled("packet_loss") && packetLossInjector.shouldDropPacket()) {
                    System.out.println("🔥 [Packet Loss] Dropping client->server packet (" + packet.length + " bytes)");
                    continue;
                }

                ConnectionState currentState = state.get();
                if (currentState.isHandshakeComplete() && isComQuery(packet)) {
                    String query = extractQuery(packet);
                    if (query != null) {
                        currentState.setCurrentQueryType(getQueryType(query));
                        System.out.println("Detected query: " + query);
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
                }

                if (currentState.isHandshakeComplete() && currentState.isAddDelay()) {
                    if (isScenarioEnabled("latency_injection")) {
                        long latency = getLatencyForType(currentState.getCurrentQueryType());
                        System.out.println("Injecting latency: " + latency + " ms for query type: " + currentState.getCurrentQueryType());
                        Thread.sleep(latency);
                        currentState.setAddDelay(false);
                    }
                }

                if (isScenarioEnabled("packet_loss") && packetLossInjector.shouldDropPacket()) {
                    System.out.println("🔥 [Packet Loss] Dropping server->client packet (" + packet.length + " bytes)");
                    continue;
                }

                // Trigger the stress test if enabled and not already running
                if (isScenarioEnabled("stress_testing") && currentState.isHandshakeComplete() && !stressTester.isRunning()) {
                    System.out.println("💥 [Stress Test] Triggering intensive queries");
                    stressTester.startStressTest(); // Trigger stress test
                } else if (!isScenarioEnabled("stress_testing") && stressTester.isRunning()) {
                    // Stop if scenario disabled but test still running
                    System.out.println("💥 [Stress Test] Stopping test as scenario is disabled");
                    stressTester.stopStressTest();
                }

                clientOut.write(packet);
                clientOut.flush();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Server to client error: " + e.getMessage());
        }
    }

    private static boolean isScenarioEnabled(String scenarioName) {
        synchronized (scenarios) {
            boolean enabled = scenarios.getOrDefault(scenarioName, false);
            return enabled;
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

    private static long getLatencyForType(String type) {
        if (type == null) return 0;
        return switch (type) {
            case "DQL" -> 600;
            case "DML" -> 60000;
            case "DDL" -> 500;
            case "TCL" -> 150;
            case "DCL" -> 250;
            default -> 0;
        };
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

    static class ConnectionState {
        private String currentQueryType;
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
    }
}
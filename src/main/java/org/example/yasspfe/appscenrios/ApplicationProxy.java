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
    private static final Map<String, Boolean> return404Endpoints = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> cpuLoadScenarios = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> highLoadScenarios = new ConcurrentHashMap<>();


    // CPU load control
    private static final AtomicBoolean cpuLoadActive = new AtomicBoolean(false);
    private static ExecutorService cpuLoadExecutor;
    private static HighLoadScenario highLoadScenario;

    private static ScheduledExecutorService cpuLoadMonitor;
    private static final int LOAD_THREADS = 10; // Number of threads to use for generating load

    // Response stats
    private static int successfulRequests = 0;
    private static int failedRequests = 0;

    public static void main(String[] args) {
        try {
            setupDatabase();
            updateScenarios();
            updateTargetConfig();


            new Thread(() -> {
                while (true) {
                    try {
                        updateScenarios();
                        updateTargetConfig();
                        if (highLoadScenario != null) {
                            boolean highLoadEnabled = isScenarioEnabled("high_load");
                            if (highLoadEnabled && !highLoadScenario.isActive()) {
                                highLoadScenario.startHighLoad();
                            } else if (!highLoadEnabled && highLoadScenario.isActive()) {
                                highLoadScenario.stopHighLoad();
                            }
                        }

                        boolean cpuLoadEnabled = isScenarioEnabled("cpu_load");
                        if (cpuLoadEnabled && !cpuLoadActive.get()) {
                            startCpuLoadOnTarget();
                        } else if (!cpuLoadEnabled && cpuLoadActive.get()) {
                            stopCpuLoadOnTarget();
                        }

                        Thread.sleep(1000); // Reduced from 5000ms to 1000ms for faster updates
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
        Socket targetSocket = null;
        try {
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

    private static void setupDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS app_scenarios (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL UNIQUE, " +
                    "enabled BOOLEAN DEFAULT FALSE, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS application_proxy_config (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "host VARCHAR(255) NOT NULL, " +
                    "port INT NOT NULL, " +
                    "proxy_port INT NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS target_app_config (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "app_name VARCHAR(100) NOT NULL, " +
                    "cpu_load_enabled BOOLEAN DEFAULT FALSE, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM app_scenarios");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO app_scenarios (name, enabled) VALUES ('return_404', false)");
                stmt.execute("INSERT INTO app_scenarios (name, enabled) VALUES ('cpu_load', false)");
                stmt.execute("INSERT INTO app_scenarios (name, enabled) VALUES ('high_load', false)");

            }


            rs = stmt.executeQuery("SELECT COUNT(*) FROM application_proxy_config");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO application_proxy_config (host, port, proxy_port) VALUES ('localhost', 8080, 3303)");
            }

            rs = stmt.executeQuery("SELECT COUNT(*) FROM target_app_config");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO target_app_config (app_name, cpu_load_enabled) VALUES ('default', false)");
            }

            System.out.println("[ApplicationProxy] Database setup completed");

        } catch (SQLException e) {
            System.err.println("[ApplicationProxy] Database setup error: " + e.getMessage());
        }
    }
    private static final AtomicBoolean highLoadActive = new AtomicBoolean(false);


    private static void updateTargetConfig() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT host, port, proxy_port FROM application_proxy_config ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                targetHost = rs.getString("host");
                targetPort = rs.getInt("port");
                proxyPort = rs.getInt("proxy_port");
                System.out.println("[ApplicationProxy] Updated target to " + targetHost + ":" + targetPort + " with proxy on port " + proxyPort);
            }
        } catch (SQLException e) {
            System.err.println("[ApplicationProxy] Error reading target config: " + e.getMessage());
        }
    }

    private static synchronized void updateScenarios() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT name, enabled FROM app_scenarios");
            Map<String, Boolean> newScenarios = new ConcurrentHashMap<>();
            while (rs.next()) {
                newScenarios.put(rs.getString("name"), rs.getBoolean("enabled"));
            }
            scenarios.clear();
            scenarios.putAll(newScenarios);

            ResultSet rs404 = stmt.executeQuery("SELECT name, enabled FROM app_scenarios WHERE name = 'return_404'");
            Map<String, Boolean> newReturn404Endpoints = new ConcurrentHashMap<>();
            while (rs404.next()) {
                newReturn404Endpoints.put("return_404", rs404.getBoolean("enabled"));
            }
            return404Endpoints.clear();
            return404Endpoints.putAll(newReturn404Endpoints);

            ResultSet rsCPU = stmt.executeQuery("SELECT name, enabled FROM app_scenarios WHERE name = 'cpu_load'");
            Map<String, Boolean> newCPULoadScenarios = new ConcurrentHashMap<>();
            while (rsCPU.next()) {
                newCPULoadScenarios.put("cpu_load", rsCPU.getBoolean("enabled"));
            }
            cpuLoadScenarios.clear();
            cpuLoadScenarios.putAll(newCPULoadScenarios);

            ResultSet rsHighLoad = stmt.executeQuery("SELECT name, enabled FROM app_scenarios WHERE name = 'high_load'");
            Map<String, Boolean> newHighLoadScenarios = new ConcurrentHashMap<>();
            while (rsHighLoad.next()) {
                newHighLoadScenarios.put("high_load", rsHighLoad.getBoolean("enabled"));
            }
            highLoadScenarios.clear();
            highLoadScenarios.putAll(newHighLoadScenarios);

            System.out.println("[ApplicationProxy] Updated scenarios: " + scenarios);

        } catch (SQLException e) {
            System.err.println("[updateScenarios] Error loading scenarios: " + e.getMessage());
        }
    }
    private static boolean isScenarioEnabled(String name) {
        return scenarios.getOrDefault(name, false);
    }

    /**
     * Start CPU load on the target application without modifying it
     * We achieve this by generating many requests that are CPU-intensive
     */
    private static void startCpuLoadOnTarget() {
        if (cpuLoadActive.compareAndSet(false, true)) {
            System.out.println("[ApplicationProxy] Starting CPU load on target application with " + LOAD_THREADS + " threads");

            // Reset stats
            successfulRequests = 0;
            failedRequests = 0;

            // Create a thread pool for generating load
            cpuLoadExecutor = Executors.newFixedThreadPool(LOAD_THREADS);

            // Start multiple threads to generate load on the target
            for (int i = 0; i < LOAD_THREADS; i++) {
                final int threadId = i;
                cpuLoadExecutor.submit(() -> generateLoadOnTarget(threadId));
            }

            // Start a monitor thread to ensure load generation continues
            cpuLoadMonitor = Executors.newScheduledThreadPool(1);
            cpuLoadMonitor.scheduleAtFixedRate(() -> {
                if (cpuLoadActive.get()) {
                    System.out.println("[ApplicationProxy] CPU load monitor: Active=" + cpuLoadActive.get() +
                            ", Successful requests=" + successfulRequests +
                            ", Failed requests=" + failedRequests);

                    // If we're failing too much, try to restart some workers
                    if (failedRequests > successfulRequests * 3) {
                        System.out.println("[ApplicationProxy] High failure rate detected, restarting some workers");
                        for (int i = 0; i < 3; i++) {
                            final int threadId = i;
                            cpuLoadExecutor.submit(() -> generateLoadOnTarget(threadId + 100)); // Use different thread IDs
                        }
                        // Reset counters
                        failedRequests = 0;
                        successfulRequests = 0;
                    }
                }
            }, 3, 3, TimeUnit.SECONDS);
        }
    }

    /**
     * Stop CPU load on target application
     */
    private static void stopCpuLoadOnTarget() {
        if (cpuLoadActive.compareAndSet(true, false)) {
            System.out.println("[ApplicationProxy] Stopping CPU load on target application");

            if (cpuLoadMonitor != null) {
                cpuLoadMonitor.shutdownNow();
                cpuLoadMonitor = null;
            }

            if (cpuLoadExecutor != null) {
                cpuLoadExecutor.shutdownNow();
                try {
                    if (!cpuLoadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        System.err.println("[ApplicationProxy] CPU load executor did not terminate within 10 seconds");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                cpuLoadExecutor = null;
            }

            System.out.println("[ApplicationProxy] Final stats: Successful requests=" + successfulRequests +
                    ", Failed requests=" + failedRequests);
        }
    }

    /**
     * Generate load on the target application by sending various types of requests
     */
    private static void generateLoadOnTarget(int threadId) {
        System.out.println("[ApplicationProxy] Load generator thread " + threadId + " starting");

        while (cpuLoadActive.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Choose a request type randomly
                int requestType = (int)(Math.random() * 8);
                boolean success = false;

                switch (requestType) {
                    case 0:
                        // 1. Request with complex regex pattern matching
                        success = sendCpuIntensiveRequest("/api/search?q=" + generateComplexRegexPattern(), threadId);
                        break;
                    case 1:
                        // 2. Request with deep JSON nesting
                        success = sendCpuIntensivePostRequest("/api/process", generateDeepNestedJson(10), "application/json", threadId);
                        break;
                    case 2:
                        // 3. Request with large data to parse
                        success = sendCpuIntensivePostRequest("/api/analyze", generateLargeDataPayload(1000), "application/json", threadId);
                        break;
                    case 3:
                        // 4. Request with sorting parameters
                        success = sendCpuIntensiveRequest("/api/users?sort=complex&order=nested&fields=many,many,many", threadId);
                        break;
                    case 4:
                        // 5. XML parsing (typically CPU intensive)
                        success = sendCpuIntensivePostRequest("/api/xml", generateComplexXml(10), "application/xml", threadId);
                        break;
                    case 5:
                        // 6. GET request to likely existing paths
                        success = sendCpuIntensiveRequest("/" + getCommonPath() + "?ts=" + System.currentTimeMillis(), threadId);
                        break;
                    case 6:
                        // 7. POST request with form data
                        success = sendCpuIntensivePostFormRequest("/api/form", generateFormData(20), threadId);
                        break;
                    case 7:
                        // 8. Request to common API endpoints
                        success = sendCpuIntensiveRequest("/api/" + getCommonApiEndpoint() + "?id=" + UUID.randomUUID(), threadId);
                        break;
                }

                synchronized (ApplicationProxy.class) {
                    if (success) {
                        successfulRequests++;
                    } else {
                        failedRequests++;
                    }
                }

                // Brief pause to allow the target's CPU to register the strain
                // and prevent overwhelming the network
                Thread.sleep(20 + (int)(Math.random() * 30));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log but continue generating load
                System.err.println("[ApplicationProxy] Error in load generator " + threadId + ": " + e.getMessage());
                try {
                    Thread.sleep(200); // Back off a bit on error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        System.out.println("[ApplicationProxy] Load generator thread " + threadId + " stopped");
    }

    /**
     * Send a GET request to the target that's CPU intensive
     */
    private static boolean sendCpuIntensiveRequest(String path, int threadId) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.setSoTimeout(5000); // 5 second timeout
            socket.connect(new InetSocketAddress(targetHost, targetPort), 2000); // 2 second connection timeout

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Create a request with many headers to parse
            out.println("GET " + path + " HTTP/1.1");
            out.println("Host: " + targetHost + ":" + targetPort);
            out.println("User-Agent: CPULoadGenerator/1.0");
            out.println("Accept: */*");
            out.println("X-Request-ID: " + UUID.randomUUID().toString());
            out.println("Connection: close");

            // Add headers that most applications will process
            out.println("Accept-Encoding: gzip, deflate, br");
            out.println("Accept-Language: en-US,en;q=0.9");
            out.println("Cache-Control: no-cache");
            out.println("Pragma: no-cache");

            // Add many random headers to increase parsing workload
            for (int i = 0; i < 10; i++) {
                out.println("X-Custom-Header-" + i + ": " + generateRandomString(20));
            }
            out.println();
            out.flush();

            // Read response but ignore it
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];
            while (in.read(buffer) != -1) {
                // Just drain the response
            }

            return true;
        } catch (Exception e) {
            //System.err.println("[ApplicationProxy] Error sending GET request from thread " +
            //                   threadId + ": " + e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Send a POST request to the target that's CPU intensive
     */
    private static boolean sendCpuIntensivePostRequest(String path, String body, String contentType, int threadId) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.setSoTimeout(5000); // 5 second timeout
            socket.connect(new InetSocketAddress(targetHost, targetPort), 2000); // 2 second connection timeout

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Create a POST request with a complex payload
            out.println("POST " + path + " HTTP/1.1");
            out.println("Host: " + targetHost + ":" + targetPort);
            out.println("User-Agent: CPULoadGenerator/1.0");
            out.println("Content-Type: " + contentType);
            out.println("Content-Length: " + body.length());
            out.println("X-Request-ID: " + UUID.randomUUID().toString());
            out.println("Connection: close");
            out.println();
            out.print(body); // Don't use println here to avoid adding extra newline
            out.flush();

            // Read response but ignore it
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];
            while (in.read(buffer) != -1) {
                // Just drain the response
            }

            return true;
        } catch (Exception e) {
            //System.err.println("[ApplicationProxy] Error sending POST request from thread " +
            //                   threadId + ": " + e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Send a POST form request to the target
     */
    private static boolean sendCpuIntensivePostFormRequest(String path, String formData, int threadId) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.setSoTimeout(5000); // 5 second timeout
            socket.connect(new InetSocketAddress(targetHost, targetPort), 2000); // 2 second connection timeout

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Create a POST request with form data
            out.println("POST " + path + " HTTP/1.1");
            out.println("Host: " + targetHost + ":" + targetPort);
            out.println("User-Agent: CPULoadGenerator/1.0");
            out.println("Content-Type: application/x-www-form-urlencoded");
            out.println("Content-Length: " + formData.length());
            out.println("Connection: close");
            out.println();
            out.print(formData); // Don't use println here
            out.flush();

            // Read response but ignore it
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];
            while (in.read(buffer) != -1) {
                // Just drain the response
            }

            return true;
        } catch (Exception e) {
            //System.err.println("[ApplicationProxy] Error sending form POST from thread " +
            //                   threadId + ": " + e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Generate a complex regex pattern that will be CPU intensive to process
     */
    private static String generateComplexRegexPattern() {
        StringBuilder regex = new StringBuilder();

        // Create a complex pattern with nested groups and repetitions
        regex.append("(a+b*c?){5,10}");
        regex.append("(?:[a-z0-9]{1,5}\\d+){3,8}");
        regex.append("(?:(?:ab|cd|ef|gh){2,4}\\w+){3,7}");

        // Add some catastrophic backtracking patterns
        regex.append("(x+x+)+y");
        regex.append("(a|a?)+b");

        return regex.toString();
    }

    /**
     * Generate deeply nested JSON that will be CPU intensive to parse
     */
    private static String generateDeepNestedJson(int depth) {
        if (depth <= 0) {
            return "\"" + generateRandomString(10) + "\"";
        }

        StringBuilder json = new StringBuilder();
        json.append("{");
        for (int i = 0; i < 3; i++) {
            json.append("\"prop").append(i).append("\": ");

            if (i < 2) {
                json.append(generateDeepNestedJson(depth - 1)).append(",");
            } else {
                json.append(generateDeepNestedJson(depth - 1));
            }
        }
        json.append("}");

        return json.toString();
    }

    /**
     * Generate a large data payload that will be CPU intensive to process
     */
    private static String generateLargeDataPayload(int items) {
        StringBuilder json = new StringBuilder();
        json.append("{\"items\":[");

        for (int i = 0; i < items; i++) {
            json.append("{");
            json.append("\"id\":\"").append(UUID.randomUUID()).append("\",");
            json.append("\"name\":\"").append(generateRandomString(20)).append("\",");
            json.append("\"value\":").append(Math.random() * 1000).append(",");
            json.append("\"nested\":{");
            json.append("\"prop1\":\"").append(generateRandomString(15)).append("\",");
            json.append("\"prop2\":").append(Math.random() * 100).append(",");
            json.append("\"prop3\":true");
            json.append("}");
            json.append("}");

            if (i < items - 1) {
                json.append(",");
            }
        }

        json.append("]}");
        return json.toString();
    }

    /**
     * Generate complex XML data that will be CPU intensive to parse
     */
    private static String generateComplexXml(int depth) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<root>\n");
        appendXmlNode(xml, "node", depth, 1);
        xml.append("</root>");
        return xml.toString();
    }

    private static void appendXmlNode(StringBuilder xml, String name, int depth, int indent) {
        if (depth <= 0) return;

        String pad = "  ".repeat(indent);
        xml.append(pad).append("<").append(name).append(">\n");

        // Add attributes and child nodes
        for (int i = 0; i < 3; i++) {
            String childName = name + "_child_" + i;
            appendXmlNode(xml, childName, depth - 1, indent + 1);
        }

        // Add some text content
        xml.append(pad).append("  <text>").append(generateRandomString(20)).append("</text>\n");

        xml.append(pad).append("</").append(name).append(">\n");
    }

    /**
     * Generate form data for POST requests
     */
    private static String generateFormData(int fields) {
        StringBuilder form = new StringBuilder();

        for (int i = 0; i < fields; i++) {
            if (i > 0) form.append("&");
            form.append("field").append(i).append("=").append(generateRandomString(15));
        }

        return form.toString();
    }

    /**
     * Generate a random string of specified length
     */
    private static String generateRandomString(int length) {
        final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = (int)(CHARS.length() * Math.random());
            sb.append(CHARS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Get a common path that might exist in the target application
     */
    private static String getCommonPath() {
        String[] paths = {
                "index.html", "home", "login", "dashboard", "users",
                "products", "services", "about", "contact", "api/data",
                "api/status", "health", "metrics", "admin", "public/index.html"
        };
        return paths[(int)(Math.random() * paths.length)];
    }

    /**
     * Get a common API endpoint that might exist in the target application
     */
    private static String getCommonApiEndpoint() {
        String[] endpoints = {
                "users", "products", "orders", "items", "customers",
                "clients", "data", "search", "query", "status",
                "health", "metrics", "config", "settings", "preferences"
        };
        return endpoints[(int)(Math.random() * endpoints.length)];
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

                // Forward the original request - we've moved CPU load generation to separate threads
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
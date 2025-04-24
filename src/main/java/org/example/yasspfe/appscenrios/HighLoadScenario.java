package org.example.yasspfe.appscenrios;

import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High Load Scenario implementation that generates excessive traffic to the target application
 * to test its resilience under high load conditions.
 */
public class HighLoadScenario {

    private static final int LOAD_THREADS = 15; // Higher number of threads than CPU load

    // High load control
    private final AtomicBoolean highLoadActive = new AtomicBoolean(false);
    private ExecutorService loadExecutor;
    private ScheduledExecutorService loadMonitor;

    // Target application details
    private final String targetHost;
    private final int targetPort;

    // Stats
    private int successfulRequests = 0;
    private int failedRequests = 0;

    public HighLoadScenario(String targetHost, int targetPort) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    /**
     * Start high load on the target application
     */
    public void startHighLoad() {
        if (highLoadActive.compareAndSet(false, true)) {
            System.out.println("[HighLoadScenario] Starting high load on target application with " + LOAD_THREADS + " threads");

            // Reset stats
            successfulRequests = 0;
            failedRequests = 0;

            // Create a thread pool for generating load
            loadExecutor = Executors.newFixedThreadPool(LOAD_THREADS);

            // Start multiple threads to generate load on the target
            for (int i = 0; i < LOAD_THREADS; i++) {
                final int threadId = i;
                loadExecutor.submit(() -> generateHighLoad(threadId));
            }

            // Start a monitor thread
            loadMonitor = Executors.newScheduledThreadPool(1);
            loadMonitor.scheduleAtFixedRate(() -> {
                if (highLoadActive.get()) {
                    System.out.println("[HighLoadScenario] High load monitor: Active=" + highLoadActive.get() +
                            ", Successful requests=" + successfulRequests +
                            ", Failed requests=" + failedRequests);

                    // If we're failing too much, try to restart some workers
                    if (failedRequests > successfulRequests * 3) {
                        System.out.println("[HighLoadScenario] High failure rate detected, restarting some workers");
                        for (int i = 0; i < 5; i++) {
                            final int threadId = i;
                            loadExecutor.submit(() -> generateHighLoad(threadId + 100)); // Use different thread IDs
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
     * Stop high load on target application
     */
    public void stopHighLoad() {
        if (highLoadActive.compareAndSet(true, false)) {
            System.out.println("[HighLoadScenario] Stopping high load on target application");

            if (loadMonitor != null) {
                loadMonitor.shutdownNow();
                loadMonitor = null;
            }

            if (loadExecutor != null) {
                loadExecutor.shutdownNow();
                try {
                    if (!loadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        System.err.println("[HighLoadScenario] High load executor did not terminate within 10 seconds");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                loadExecutor = null;
            }

            System.out.println("[HighLoadScenario] Final stats: Successful requests=" + successfulRequests +
                    ", Failed requests=" + failedRequests);
        }
    }

    /**
     * Generate high load by sending many concurrent requests to various endpoints
     */
    private void generateHighLoad(int threadId) {
        System.out.println("[HighLoadScenario] High load generator thread " + threadId + " starting");

        while (highLoadActive.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Choose a request type
                int requestType = (int)(Math.random() * 10);
                boolean success = false;

                // Make multiple requests to create higher load than CPU load scenario
                for (int i = 0; i < 3; i++) {
                    switch (requestType % 4) {
                        case 0:
                            // Heavy GET requests with many parameters
                            success = sendHighLoadRequest("/api/data?id=" + UUID.randomUUID() +
                                    "&timestamp=" + System.currentTimeMillis() +
                                    "&fields=all,nested,complex,detailed,expanded" +
                                    "&format=full&include=metadata,stats,logs,history", threadId);
                            break;
                        case 1:
                            // Heavy POST requests with large payloads
                            success = sendHighLoadPostRequest("/api/process",
                                    generateLargeJsonPayload(2000), "application/json", threadId);
                            break;
                        case 2:
                            // Requests to static resources
                            success = sendHighLoadRequest("/assets/images/large-" +
                                    (int)(Math.random() * 20) + ".jpg", threadId);
                            break;
                        case 3:
                            // API endpoints that might involve database operations
                            success = sendHighLoadRequest("/api/users/" +
                                    (int)(Math.random() * 10000) + "/details", threadId);
                            break;
                    }

                    requestType++; // Rotate through request types
                }

                synchronized (this) {
                    if (success) {
                        successfulRequests++;
                    } else {
                        failedRequests++;
                    }
                }

                // Brief pause to prevent completely overwhelming the target
                Thread.sleep(10 + (int)(Math.random() * 20));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[HighLoadScenario] Error in high load generator " + threadId + ": " + e.getMessage());
                try {
                    Thread.sleep(100); // Back off a bit on error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        System.out.println("[HighLoadScenario] High load generator thread " + threadId + " stopped");
    }

    /**
     * Send a GET request for high load testing
     */
    private boolean sendHighLoadRequest(String path, int threadId) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.setSoTimeout(3000); // 3 second timeout
            socket.connect(new InetSocketAddress(targetHost, targetPort), 1000); // 1 second connection timeout

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Create a request with many headers
            out.println("GET " + path + " HTTP/1.1");
            out.println("Host: " + targetHost + ":" + targetPort);
            out.println("User-Agent: HighLoadGenerator/1.0");
            out.println("Accept: */*");
            out.println("X-Request-ID: " + UUID.randomUUID().toString());
            out.println("X-Load-Test: true");
            out.println("X-Thread-ID: " + threadId);
            out.println("X-Timestamp: " + System.currentTimeMillis());
            out.println("Connection: close");

            // Add more headers to increase request size
            for (int i = 0; i < 20; i++) {
                out.println("X-Custom-Header-" + i + ": " + generateRandomString(30));
            }
            out.println();
            out.flush();

            // Read response but don't process it
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];
            while (in.read(buffer) != -1) {
                // Just drain the response
            }

            return true;
        } catch (Exception e) {
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
     * Send a POST request for high load testing
     */
    private boolean sendHighLoadPostRequest(String path, String body, String contentType, int threadId) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.setSoTimeout(3000); // 3 second timeout
            socket.connect(new InetSocketAddress(targetHost, targetPort), 1000); // 1 second connection timeout

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Create a POST request with a large payload
            out.println("POST " + path + " HTTP/1.1");
            out.println("Host: " + targetHost + ":" + targetPort);
            out.println("User-Agent: HighLoadGenerator/1.0");
            out.println("Content-Type: " + contentType);
            out.println("Content-Length: " + body.length());
            out.println("X-Request-ID: " + UUID.randomUUID().toString());
            out.println("X-Load-Test: true");
            out.println("X-Thread-ID: " + threadId);
            out.println("Connection: close");
            out.println();
            out.print(body);
            out.flush();

            // Read response but don't process it
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];
            while (in.read(buffer) != -1) {
                // Just drain the response
            }

            return true;
        } catch (Exception e) {
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
     * Generate a large JSON payload for high load testing
     */
    private String generateLargeJsonPayload(int items) {
        StringBuilder json = new StringBuilder();
        json.append("{\"items\":[");

        for (int i = 0; i < items; i++) {
            json.append("{");
            json.append("\"id\":\"").append(UUID.randomUUID()).append("\",");
            json.append("\"name\":\"").append(generateRandomString(20)).append("\",");
            json.append("\"description\":\"").append(generateRandomString(100)).append("\",");
            json.append("\"value\":").append(Math.random() * 10000).append(",");
            json.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
            json.append("\"active\":").append(Math.random() > 0.5).append(",");
            json.append("\"properties\":{");
            for (int j = 0; j < 10; j++) {
                json.append("\"prop").append(j).append("\":\"").append(generateRandomString(15)).append("\"");
                if (j < 9) json.append(",");
            }
            json.append("},");
            json.append("\"tags\":[");
            for (int k = 0; k < 5; k++) {
                json.append("\"").append(generateRandomString(8)).append("\"");
                if (k < 4) json.append(",");
            }
            json.append("]");
            json.append("}");

            if (i < items - 1) {
                json.append(",");
            }
        }

        json.append("]}");
        return json.toString();
    }

    /**
     * Generate a random string of specified length
     */
    private String generateRandomString(int length) {
        final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = (int)(CHARS.length() * Math.random());
            sb.append(CHARS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Check if high load scenario is currently active
     */
    public boolean isActive() {
        return highLoadActive.get();
    }
}
package org.example.yasspfe.appscenrios;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServiceDownScenario {

    private static final AtomicBoolean serviceDownActive = new AtomicBoolean(false);
    private static String targetHost = "localhost";
    private static int targetPort = 8080;

    // Allow updating the target host and port dynamically
    public static void updateTargetConfig(String host, int port) {
        targetHost = host;
        targetPort = port;
    }

    // Method to simulate service downtime (disable service)
    public static void startServiceDown() {
        if (serviceDownActive.compareAndSet(false, true)) {
            System.out.println("[ServiceDownScenario] Service is now down.");
        }
    }

    // Method to stop service downtime (restore service)
    public static void stopServiceDown() {
        if (serviceDownActive.compareAndSet(true, false)) {
            System.out.println("[ServiceDownScenario] Service is back up.");
        }
    }

    // Check if the service is down
    public static boolean isServiceDown() {
        return serviceDownActive.get();
    }

    // Simulate service downtime by rejecting requests
    public static void handleServiceDowntime(Socket clientSocket) {
        if (isServiceDown()) {
            try {
                OutputStream clientOut = clientSocket.getOutputStream();
                // Respond with a Service Unavailable error (503)
                String response = "HTTP/1.1 503 Service Unavailable\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Connection: close\r\n" +
                        "\r\n" +
                        "The service is temporarily down for maintenance.";
                clientOut.write(response.getBytes());
                clientOut.flush();
                clientSocket.close();
                System.out.println("[ServiceDownScenario] Service unavailable response sent to client.");
            } catch (IOException e) {
                System.err.println("[ServiceDownScenario] Error while handling service downtime: " + e.getMessage());
            }
        }
    }
}

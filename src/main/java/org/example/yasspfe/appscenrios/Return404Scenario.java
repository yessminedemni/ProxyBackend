package org.example.yasspfe.appscenrios;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Scenario implementation that returns 404 responses for HTTP requests.
 * Used by ApplicationProxy to simulate error responses without forwarding to the target service.
 */
public class Return404Scenario {

    /**
     * Applies the 404 scenario by sending a HTTP 404 Not Found response directly to the client socket.
     *
     * @param clientSocket The client socket to send the 404 response to
     * @param path The requested path that resulted in a 404
     * @throws IOException If an I/O error occurs when sending the response
     */
    public static void apply(Socket clientSocket, String path) throws IOException {
        System.out.println("[Return404Scenario] apply() called for path: " + path + " at: " + System.currentTimeMillis());

        if (clientSocket == null) {
            System.out.println("[Return404Scenario] Client socket is null, cannot send 404 for path: " + path + " at: " + System.currentTimeMillis());
            return;
        }

        try (OutputStream out = clientSocket.getOutputStream()) {
            String response = "HTTP/1.1 404 Not Found\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    "{\"error\":\"Resource not found\",\"path\":\"" + path + "\"}";

            out.write(response.getBytes());
            out.flush();
            System.out.println("[Return404Scenario] Sent 404 for path: " + path + " at: " + System.currentTimeMillis());
        } catch (IOException e) {
            System.err.println("[Return404Scenario] Error sending 404: " + e.getMessage() + " at: " + System.currentTimeMillis());
            throw e; // Re-throw to allow caller to handle the exception
        }
    }
}
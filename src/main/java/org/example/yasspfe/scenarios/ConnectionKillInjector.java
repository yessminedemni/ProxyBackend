package org.example.yasspfe.scenarios;

import java.net.Socket;

public class ConnectionKillInjector {
    private boolean enabled = false;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean shouldKill(String query) {
        if (!enabled || query == null) return false;

        String upper = query.trim().toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("INSERT");
    }

    public void killConnection(Socket clientSocket) {
        try {
            System.out.println("💣 [Connection Kill] Forcibly closing client socket.");
            clientSocket.close();
        } catch (Exception e) {
            System.err.println("❌ [Connection Kill] Failed to close client socket: " + e.getMessage());
        }
    }
}

package org.example.yasspfe.scenarios;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests database resilience by creating and killing multiple connections to the database
 * to observe how it handles connection pool exhaustion and recovery.
 */
public class ConnectionKillInjector {
    private boolean enabled = false;
    private final AtomicInteger connectionAttempts = new AtomicInteger(0);
    private final AtomicInteger failedConnections = new AtomicInteger(0);
    private static final int CONNECTION_LIMIT_THRESHOLD = 100;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            // Reset counters when enabling
            connectionAttempts.set(0);
            failedConnections.set(0);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Determines if we should kill the database connection based on query type
     * Focuses on long-running transactions to test database recovery
     */
    public boolean shouldKill(String query) {
        if (!enabled || query == null) return false;

        String upper = query.trim().toUpperCase();

        // Track connection attempts for database connection pool testing
        connectionAttempts.incrementAndGet();

        // Kill connections that are performing write operations or complex queries
        // to test database's transaction recovery mechanisms
        return upper.contains("BEGIN") ||
                upper.contains("START TRANSACTION") ||
                (upper.startsWith("INSERT") && upper.length() > 100) ||
                (upper.startsWith("SELECT") && upper.contains("JOIN"));
    }

    /**
     * Kill the connection to the database to test its recovery capabilities
     */
    public void killConnection(Socket dbSocket) {
        try {
            System.out.println("💣 [DB Resilience Test] Forcibly closing database socket connection.");
            failedConnections.incrementAndGet();
            dbSocket.close();

            // Test database connection pool limits
            if (connectionAttempts.get() > CONNECTION_LIMIT_THRESHOLD) {
                System.out.println("🔍 [DB Resilience Test] Testing database connection pool limits: " +
                        failedConnections.get() + "/" + connectionAttempts.get() + " connections terminated.");
            }
        } catch (Exception e) {
            System.err.println("❌ [DB Resilience Test] Failed to close database socket: " + e.getMessage());
        }
    }

    /**
     * Gets metrics on connection kill tests
     */
    public String getMetrics() {
        return String.format("Connection Kill Test Metrics - Attempts: %d, Terminated: %d, Success Rate: %.2f%%",
                connectionAttempts.get(),
                failedConnections.get(),
                connectionAttempts.get() > 0 ?
                        (100 - ((double)failedConnections.get() / connectionAttempts.get() * 100)) : 0);
    }
}

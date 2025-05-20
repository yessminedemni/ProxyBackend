package org.example.yasspfe.appscenrios;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scenario implementation that simulates database downtime in the application.
 */
public class DatabaseDownScenario {

    private static final AtomicBoolean dbDownActive = new AtomicBoolean(false);

    /**
     * Start simulating database downtime
     */
    public static void startDbDown() {
        if (dbDownActive.compareAndSet(false, true)) {
            System.out.println("[DatabaseDownScenario] Database is now down.");
        }
    }

    /**
     * Stop simulating database downtime
     */
    public static void stopDbDown() {
        if (dbDownActive.compareAndSet(true, false)) {
            System.out.println("[DatabaseDownScenario] Database is back up.");
        }
    }

    /**
     * Check if the database downtime scenario is active
     *
     * @return true if the database is "down", false otherwise
     */
    public static boolean isDbDown() {
        return dbDownActive.get();
    }

    /**
     * Simulate database downtime by throwing SQLException when database operations are attempted.
     * This method should be called before any critical database operations in the proxy.
     *
     * @throws SQLException if the database is simulated as being down
     */
    public static void handleDbDowntime() throws SQLException {
        if (isDbDown()) {
            throw new SQLException("The database is down for maintenance.");
        }
    }
}
package org.example.yasspfe.scenarios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tests database resilience to lost queries by simulating complete query loss
 */
public class QueryBlackholeInjector {
    private boolean enabled = false;
    private String lastQuery = null;
    private final AtomicLong queriesProcessed = new AtomicLong(0);
    private final AtomicLong queriesBlackholed = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> queryTypeMetrics = new ConcurrentHashMap<>();

    private final String logInsertSQL = "INSERT INTO chaos_blackhole_log (query_text, query_type, timestamp, blackholed) VALUES (?, ?, NOW(), ?)";

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            queriesProcessed.set(0);
            queriesBlackholed.set(0);
            queryTypeMetrics.clear();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Updates tracking for database queries and logs them for analysis
     */
    public void updateLastQuery(String query, Connection conn) {
        this.lastQuery = query;
        queriesProcessed.incrementAndGet();

        if (!enabled) return;

        try {
            String type = getQueryType(query);
            queryTypeMetrics.compute(type, (k, v) -> (v == null) ? 1 : v + 1);

            // Log all query types for comprehensive database resilience testing
            logToDatabase(conn, query, type, shouldDropResponse());
        } catch (Exception e) {
            System.err.println("❌ [DB Resilience Test] Failed to log query: " + e.getMessage());
        }
    }

    /**
     * Determines if a query response should be dropped to test database
     * recovery mechanisms
     */
    public boolean shouldDropResponse() {
        if (!enabled || lastQuery == null) return false;

        String upper = lastQuery.toUpperCase().trim();

        // Focus on dropping responses for complex or large operations
        // to test database recovery capabilities
        boolean shouldDrop = upper.contains("JOIN") ||
                upper.contains("GROUP BY") ||
                upper.contains("ORDER BY") ||
                (upper.startsWith("SELECT") && upper.length() > 150) ||
                (upper.startsWith("INSERT") && upper.contains("SELECT"));

        if (shouldDrop) {
            queriesBlackholed.incrementAndGet();
            System.out.println("🔍 [DB Resilience Test] Blackholing database query response to test recovery");
        }

        return shouldDrop;
    }

    /**
     * Logs database operations for analysis
     */
    private void logToDatabase(Connection conn, String query, String type, boolean blackholed) {
        try (PreparedStatement stmt = conn.prepareStatement(logInsertSQL)) {
            stmt.setString(1, query);
            stmt.setString(2, type);
            stmt.setBoolean(3, blackholed);
            stmt.executeUpdate();
            System.out.println("[DB Resilience Test] Logged " + (blackholed ? "blackholed " : "") + type + " query.");
        } catch (SQLException e) {
            System.err.println("❌ [DB Resilience Test] Error logging to DB: " + e.getMessage());
        }
    }

    /**
     * Determines query type for metrics and logging
     */
    private String getQueryType(String query) {
        String upper = query.toUpperCase().trim();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        if (upper.startsWith("CREATE")) return "CREATE";
        if (upper.startsWith("ALTER")) return "ALTER";
        if (upper.startsWith("DROP")) return "DROP";
        return "OTHER";
    }

    /**
     * Gets metrics on blackhole tests
     */
    public String getMetrics() {
        StringBuilder metrics = new StringBuilder(String.format(
                "Query Blackhole Test Metrics - Total: %d, Blackholed: %d, Rate: %.2f%%\n",
                queriesProcessed.get(),
                queriesBlackholed.get(),
                queriesProcessed.get() > 0 ?
                        ((double)queriesBlackholed.get() / queriesProcessed.get() * 100) : 0));

        // Add per-query type metrics
        metrics.append("Query type distribution:\n");
        queryTypeMetrics.forEach((type, count) -> {
            metrics.append(String.format("  %s: %d queries (%.2f%%)\n",
                    type, count,
                    queriesProcessed.get() > 0 ?
                            ((double)count / queriesProcessed.get() * 100) : 0));
        });

        return metrics.toString();
    }
}

package org.example.yasspfe.scenarios;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.Random;

/**
 * Tests database performance under latency by injecting delay before and measuring real DB execution time.
 */
public class LatencyInjector {
    // Configuration
    private static boolean enabled = false;
    private static double timeoutProbability = 0.05;
    private static final Random RANDOM = new Random();

    // Metrics
    private static final AtomicLong totalQueries = new AtomicLong(0);
    private static final ConcurrentHashMap<String, Long> queryTypeMetrics = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> queryTypeLatencyTotals = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> dbExecutionTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> dbExecutionCounts = new ConcurrentHashMap<>();

    // Latency configuration
    private static final Map<String, Long> LATENCY_SETTINGS = Map.of(
            "DQL", 600L, "DML", 2000L, "DDL", 3000L,
            "TCL", 1500L, "DCL", 1000L, "OTHER", 500L
    );

    // Query detection patterns
    private static final Map<Pattern, String> QUERY_PATTERNS = Map.of(
            Pattern.compile("^\\s*(SELECT|SHOW|DESCRIBE)", Pattern.CASE_INSENSITIVE), "DQL",
            Pattern.compile("^\\s*(INSERT|UPDATE|DELETE)", Pattern.CASE_INSENSITIVE), "DML",
            Pattern.compile("^\\s*(CREATE|ALTER|DROP|TRUNCATE)", Pattern.CASE_INSENSITIVE), "DDL",
            Pattern.compile("^\\s*(COMMIT|ROLLBACK|SAVEPOINT)", Pattern.CASE_INSENSITIVE), "TCL",
            Pattern.compile("^\\s*(GRANT|REVOKE|SET\\s+PASSWORD)", Pattern.CASE_INSENSITIVE), "DCL"
    );
    public boolean isEnabled() {
        return enabled;
    }


    public static void configure(boolean isEnabled, double timeoutProb) {
        enabled = isEnabled;
        timeoutProbability = timeoutProb;
        if (enabled) resetMetrics();
    }

    public static void resetMetrics() {
        totalQueries.set(0);
        queryTypeMetrics.clear();
        queryTypeLatencyTotals.clear();
        dbExecutionTimes.clear();
        dbExecutionCounts.clear();
    }

    public static void setEnabled(boolean enabled) {
        LatencyInjector.enabled = enabled;
    }

    public static void injectLatencyBeforeQuery(String query) {
        if (!enabled || query == null) return;

        String queryType = getQueryType(query);
        long latency = LATENCY_SETTINGS.getOrDefault(queryType, 0L);

        // Update metrics
        totalQueries.incrementAndGet();
        queryTypeMetrics.merge(queryType, 1L, Long::sum);
        queryTypeLatencyTotals.merge(queryType, latency, Long::sum);

        // Apply latency
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Latency interrupted: " + e.getMessage());
        }
    }

    public static long recordExecutionTiming(String query, Runnable dbExecution) {
        if (!enabled || query == null || dbExecution == null) return 0;

        String queryType = getQueryType(query);
        long startTime = System.nanoTime();
        dbExecution.run();
        long duration = System.nanoTime() - startTime;

        dbExecutionTimes.merge(queryType, duration, Long::sum);
        dbExecutionCounts.merge(queryType, 1L, Long::sum);
        return duration;
    }

    public static boolean shouldSimulateTimeout() {
        return enabled && RANDOM.nextDouble() < timeoutProbability;
    }

    public static String getMetrics() {
        StringBuilder sb = new StringBuilder("=== Database Latency Test Report ===\n");
        sb.append("Total Queries: ").append(totalQueries.get()).append("\n\n");

        sb.append("Injected Latency Metrics:\n");
        LATENCY_SETTINGS.keySet().forEach(type -> {
            long count = queryTypeMetrics.getOrDefault(type, 0L);
            long totalLatency = queryTypeLatencyTotals.getOrDefault(type, 0L);
            sb.append(String.format("  %-6s: %4d queries | Avg Latency: %6.2f ms\n",
                    type, count, count > 0 ? (double) totalLatency / count : 0));
        });

        sb.append("\nDatabase Execution Times:\n");
        dbExecutionTimes.forEach((type, totalNanos) -> {
            long count = dbExecutionCounts.getOrDefault(type, 1L);
            double avgMs = (totalNanos / 1_000_000.0) / count;
            sb.append(String.format("  %-6s: Avg Execution: %6.2f ms\n", type, avgMs));
        });

        return sb.toString();
    }

    private static String getQueryType(String query) {
        if (query == null || query.isBlank()) return "OTHER";
        return QUERY_PATTERNS.entrySet().stream()
                .filter(e -> e.getKey().matcher(query).find())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("OTHER");
    }
}

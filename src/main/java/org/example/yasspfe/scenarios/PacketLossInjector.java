package org.example.yasspfe.scenarios;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tests database resilience by simulating packet loss AFTER query reaches the database.
 * The database always processes the query. We simulate a lost response to see how systems recover.
 */
public class PacketLossInjector {
    private boolean enabled = false;
    private final double lossRate; // Loss percentage (e.g., 10% -> 0.1)
    private final Random random;
    private final AtomicLong responsesIntercepted = new AtomicLong(0);
    private final AtomicLong responsesSuppressed = new AtomicLong(0);

    public PacketLossInjector(double lossRate) {
        this.lossRate = lossRate;
        this.random = new Random();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            responsesIntercepted.set(0);
            responsesSuppressed.set(0);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Determines if the database response should be suppressed (simulated as lost)
     * after the query has reached and been processed by the database.
     */
    public boolean shouldSuppressResponseAfterDb() {
        if (!enabled) return false;

        responsesIntercepted.incrementAndGet();
        boolean suppress = random.nextDouble() < lossRate;

        if (suppress) {
            responsesSuppressed.incrementAndGet();
            System.out.println("🔍 [DB Resilience Test] Simulating packet loss AFTER DB execution (suppressing response)");
        }

        return suppress;
    }

    /**
     * Gets metrics on post-DB packet suppression
     */
    public String getMetrics() {
        return String.format("Packet Loss Test Metrics - Responses: %d, Suppressed: %d, Suppression Rate: %.2f%%",
                responsesIntercepted.get(),
                responsesSuppressed.get(),
                responsesIntercepted.get() > 0 ?
                        ((double)responsesSuppressed.get() / responsesIntercepted.get() * 100) : 0);
    }
}

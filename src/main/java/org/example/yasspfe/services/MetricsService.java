package org.example.yasspfe.services;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class MetricsService {

    public Map<String, Object> getDatabaseMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Simulated data (Replace this with real database monitoring logic)
        metrics.put("queryLatency", Math.random() * 100); // Simulated query latency in ms
        metrics.put("activeConnections", (int) (Math.random() * 50)); // Simulated active connections
        metrics.put("cpuUsage", Math.random() * 100); // Simulated CPU usage percentage

        return metrics;
    }
}

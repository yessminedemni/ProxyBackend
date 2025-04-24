package org.example.yasspfe.controllers;

import org.example.yasspfe.services.Appscenrioservice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = "*")
public class MetricsController {

    private static final Logger logger = LoggerFactory.getLogger(MetricsController.class);
    private final Appscenrioservice appscenrioservice;

    @Autowired
    public MetricsController(Appscenrioservice appscenrioservice) {
        this.appscenrioservice = appscenrioservice;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();

            // Add app scenario metrics
            metrics.putAll(getAppScenarioMetrics());

            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            logger.error("Error fetching metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private Map<String, Object> getAppScenarioMetrics() {
        Map<String, Object> appMetrics = new HashMap<>();

        // Get current scenario states
        boolean cpuLoadEnabled = appscenrioservice.isScenarioEnabled("cpu_load");
        boolean highLoadEnabled = appscenrioservice.isScenarioEnabled("high_load");
        boolean return404Enabled = appscenrioservice.isScenarioEnabled("return_404");

        logger.info("Current scenario states - CPU Load: {}, High Load: {}, Return 404: {}",
                cpuLoadEnabled, highLoadEnabled, return404Enabled);

        // Get CPU load metrics
        List<Map<String, Object>> cpuLoad = new ArrayList<>();
        long now = System.currentTimeMillis();

        // Generate data based on scenario state
        for (int i = 0; i < 10; i++) {
            Map<String, Object> dataPoint = new HashMap<>();
            dataPoint.put("timestamp", now - (9 - i) * 1000); // 1 second intervals

            // Generate realistic values based on scenario status
            double cpuLoadValue = cpuLoadEnabled
                    ? 70 + Math.random() * 25 // 70-95% when enabled
                    : 10 + Math.random() * 20; // 10-30% when disabled

            dataPoint.put("value", cpuLoadValue);
            cpuLoad.add(dataPoint);
        }
        appMetrics.put("cpuLoad", cpuLoad);

        // Get traffic load metrics
        List<Map<String, Object>> trafficLoad = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> dataPoint = new HashMap<>();
            dataPoint.put("timestamp", now - (9 - i) * 1000);

            // Generate realistic values based on scenario status
            double trafficLoadValue = highLoadEnabled
                    ? 800 + Math.random() * 600 // 800-1400 req/s when enabled
                    : 100 + Math.random() * 200; // 100-300 req/s when disabled

            dataPoint.put("value", trafficLoadValue);
            trafficLoad.add(dataPoint);
        }
        appMetrics.put("trafficLoad", trafficLoad);

        // Get response time metrics
        List<Map<String, Object>> responseTime = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> dataPoint = new HashMap<>();
            dataPoint.put("timestamp", now - (9 - i) * 1000);

            // Generate realistic values based on scenario status
            double responseTimeValue = return404Enabled
                    ? 400 + Math.random() * 300 // 400-700ms when enabled
                    : 50 + Math.random() * 100; // 50-150ms when disabled

            dataPoint.put("value", responseTimeValue);
            responseTime.add(dataPoint);
        }
        appMetrics.put("responseTime", responseTime);

        // Add scenario states to the response
        Map<String, Boolean> scenarioStates = new HashMap<>();
        scenarioStates.put("cpuLoad", cpuLoadEnabled);
        scenarioStates.put("highLoad", highLoadEnabled);
        scenarioStates.put("return404", return404Enabled);
        appMetrics.put("scenarioStates", scenarioStates);

        return appMetrics;
    }
}

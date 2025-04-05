package org.example.yasspfe.controllers;

import org.example.yasspfe.scenarios.MySQLProxy;
import org.example.yasspfe.services.ScenarioService;
import org.example.yasspfe.entities.Scenario;
import org.example.yasspfe.scenarios.DatabaseStressTester;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/scenarios")
@CrossOrigin(origins = "*")
public class ScenarioController {

    private static final Logger logger = LoggerFactory.getLogger(ScenarioController.class);
    private final ScenarioService scenarioService;
    private final DatabaseStressTester databaseStressTester;

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
        this.databaseStressTester = initializeStressTester();
    }

    private DatabaseStressTester initializeStressTester() {
        try {
            return MySQLProxy.getStressTester();
        } catch (Exception e) {
            logger.warn("Could not get stress tester from proxy, creating new instance", e);
            return new DatabaseStressTester();
        }
    }

    @GetMapping
    public ResponseEntity<List<Scenario>> getAllScenarios() {
        return ResponseEntity.ok(scenarioService.getAllScenarios());
    }

    @PostMapping("/enable/{name}")
    public ResponseEntity<Map<String, Object>> enableScenario(@PathVariable String name) {
        logger.info("Enable scenario request received: {}", name);
        boolean success = false;

        // Add more detailed logging
        if ("stress_testing".equals(name)) {
            logger.info("Enabling stress_testing scenario");
            logger.info("Frontend configured: {}", MySQLProxy.isFrontendConfigured());
            logger.info("Current JDBC URL: {}", databaseStressTester.getJdbcUrl());
            logger.info("Current Username: {}", databaseStressTester.getUsername());
            logger.info("Current Password set: {}", databaseStressTester.getPassword() != null);

            success = databaseStressTester.startStressTest();
            logger.info("Stress test start result: {}", success);

            if (!success) {
                logger.warn("Failed to start stress test. Check if database connection is configured.");
            }
        }

        scenarioService.enableScenario(name);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Enabled " + name,
                "operationSuccess", success,
                "frontendConfigured", MySQLProxy.isFrontendConfigured()
        ));
    }

    @PostMapping("/disable/{name}")
    public ResponseEntity<Map<String, Object>> disableScenario(@PathVariable String name) {
        logger.info("Disabling scenario: {}", name);
        boolean success = true;

        if ("stress_testing".equals(name) && databaseStressTester.isRunning()) {
            logger.info("Stopping stress test");
            success = databaseStressTester.stopStressTest();
            logger.info("Stress test stop result: {}", success);

            if (!success) {
                logger.warn("Normal stop failed, attempting force stop...");
                databaseStressTester.forceStop();
            }
        }

        scenarioService.disableScenario(name);
        return ResponseEntity.ok(Map.of("success", true, "message", "Disabled " + name, "operationSuccess", success));
    }

    @GetMapping("/status/{name}")
    public ResponseEntity<Map<String, Object>> getScenarioStatus(@PathVariable String name) {
        boolean enabled = scenarioService.isScenarioEnabled(name);

        // For stress_testing, also return if it's actually running
        if ("stress_testing".equals(name)) {
            boolean isRunning = databaseStressTester.isRunning();
            boolean isConfigured = MySQLProxy.isFrontendConfigured();

            return ResponseEntity.ok(Map.of(
                    "enabled", enabled,
                    "running", isRunning,
                    "configured", isConfigured
            ));
        }

        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @PutMapping("/toggle/{name}")
    public ResponseEntity<Map<String, Object>> toggleScenario(@PathVariable String name) {
        try {
            boolean result = scenarioService.toggleScenario(name);

            // If toggled to enabled and it's the stress test, try to start it
            if (result && "stress_testing".equals(name)) {
                boolean started = databaseStressTester.startStressTest();
                logger.info("Stress test auto-start on toggle: {}", started);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "enabled", result,
                        "stressTestStarted", started
                ));
            }

            // If toggled to disabled and it's the stress test, stop it
            if (!result && "stress_testing".equals(name) && databaseStressTester.isRunning()) {
                databaseStressTester.stopStressTest();
            }

            return ResponseEntity.ok(Map.of("success", true, "enabled", result));
        } catch (Exception e) {
            logger.error("Error toggling scenario: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
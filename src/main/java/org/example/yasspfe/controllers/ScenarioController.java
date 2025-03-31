package org.example.yasspfe.controllers;

import org.example.yasspfe.services.ScenarioService;
import org.example.yasspfe.entities.Scenario;
import org.example.yasspfe.scenarios.DatabaseStressTester;
import org.example.yasspfe.scenarios.MySQLProxy;
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
        if ("stress_testing".equals(name)) {
            success = databaseStressTester.startStressTest();
            logger.info("Stress test start result: {}", success);
        }
        scenarioService.enableScenario(name);
        return ResponseEntity.ok(Map.of("success", true, "message", "Enabled " + name, "operationSuccess", success));
    }

    @PostMapping("/disable/{name}")
    public ResponseEntity<Map<String, Object>> disableScenario(@PathVariable String name) {
        logger.info("Disabling scenario: {}", name);
        boolean success = false;
        if ("stress_testing".equals(name) && databaseStressTester.isRunning()) {
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
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @PutMapping("/toggle/{name}")
    public ResponseEntity<Map<String, Object>> toggleScenario(@PathVariable String name) {
        try {
            boolean result = scenarioService.toggleScenario(name);
            return ResponseEntity.ok(Map.of("success", true, "enabled", result));
        } catch (Exception e) {
            logger.error("Error toggling scenario: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}

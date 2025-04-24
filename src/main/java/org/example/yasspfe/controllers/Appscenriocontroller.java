package org.example.yasspfe.controllers;

import org.example.yasspfe.entities.Appscenario;
import org.example.yasspfe.services.Appscenrioservice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appscenarios")
@CrossOrigin(origins = "*")
public class Appscenriocontroller {

    private static final Logger logger = LoggerFactory.getLogger(Appscenriocontroller.class);

    private final Appscenrioservice appscenrioservice;

    @Autowired
    public Appscenriocontroller(Appscenrioservice appscenrioservice) {
        this.appscenrioservice = appscenrioservice;
    }

    @GetMapping("/status/{name}")
    public ResponseEntity<Map<String, Object>> getScenarioStatus(@PathVariable String name) {
        logger.info("Getting status for scenario: {}", name);
        boolean enabled = appscenrioservice.isScenarioEnabled(name);
        logger.info("Scenario {} is {}", name, enabled ? "enabled" : "disabled");
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @PutMapping("/toggle/{name}")
    public ResponseEntity<Map<String, Object>> toggleScenario(@PathVariable String name) {
        try {
            logger.info("Received request to toggle scenario: {}", name);

            // Toggle the scenario (will create it if it doesn't exist)
            boolean newState = appscenrioservice.toggleScenario(name);

            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("enabled", newState);
            response.put("message", "Scenario " + name + " toggled to " + (newState ? "enabled" : "disabled"));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error toggling scenario: {}", name, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping
    public ResponseEntity<List<Appscenario>> getAllScenarios() {
        logger.info("Getting all scenarios");
        List<Appscenario> scenarios = appscenrioservice.getAllScenarios();
        logger.info("Found {} scenarios", scenarios.size());
        return ResponseEntity.ok(scenarios);
    }

    @PostMapping("/enable/{name}")
    public ResponseEntity<Map<String, Object>> enableScenario(@PathVariable String name) {
        try {
            logger.info("Enabling scenario: {}", name);

            // Enable the scenario (will create it if it doesn't exist)
            appscenrioservice.enableScenario(name);

            // Verify the new state
            boolean verifiedState = appscenrioservice.isScenarioEnabled(name);
            logger.info("Scenario {} enabled, verified state: {}", name, verifiedState);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("enabled", true);
            response.put("message", "Enabled " + name);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error enabling scenario: {}", name, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/disable/{name}")
    public ResponseEntity<Map<String, Object>> disableScenario(@PathVariable String name) {
        try {
            logger.info("Disabling scenario: {}", name);

            // Disable the scenario (will create it if it doesn't exist)
            appscenrioservice.disableScenario(name);

            // Verify the new state
            boolean verifiedState = appscenrioservice.isScenarioEnabled(name);
            logger.info("Scenario {} disabled, verified state: {}", name, verifiedState);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("enabled", false);
            response.put("message", "Disabled " + name);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error disabling scenario: {}", name, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/return404")
    public ResponseEntity<Map<String, Object>> configureReturn404(@RequestBody Map<String, Object> request) {
        try {
            String endpoint = (String) request.get("endpoint");
            boolean enabled = (boolean) request.get("enabled");

            logger.info("Configuring Return404 for endpoint: {}, enabled: {}", endpoint, enabled);

            if (endpoint == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Endpoint parameter is required"
                ));
            }

            appscenrioservice.configureReturn404Scenario(endpoint, enabled);

            // Verify the configuration
            String scenarioName = "return_404_" + (endpoint.startsWith("/") ? endpoint.substring(1) : endpoint);
            boolean verifiedState = appscenrioservice.isScenarioEnabled(scenarioName);
            logger.info("Return404 for endpoint {} configured, verified state: {}", endpoint, verifiedState);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", (enabled ? "Enabled" : "Disabled") + " 404 responses for " + endpoint);
            response.put("endpoint", endpoint);
            response.put("enabled", enabled);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error configuring 404 scenario", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/return404/endpoints")
    public ResponseEntity<List<Appscenario>> getReturn404Endpoints() {
        logger.info("Getting Return404 endpoints");
        List<Appscenario> endpoints = appscenrioservice.getReturn404Endpoints();
        logger.info("Found {} Return404 endpoints", endpoints.size());
        return ResponseEntity.ok(endpoints);
    }
}

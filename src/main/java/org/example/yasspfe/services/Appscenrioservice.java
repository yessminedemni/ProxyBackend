package org.example.yasspfe.services;

import org.example.yasspfe.entities.Appscenario;
import org.example.yasspfe.reposotories.Appscenariorepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class Appscenrioservice {

    private final Appscenariorepo scenarioRepository;

    @Autowired
    public Appscenrioservice(Appscenariorepo scenarioRepository) {
        this.scenarioRepository = scenarioRepository;
    }

    // Check if a scenario is enabled
    public boolean isScenarioEnabled(String name) {
        Optional<Appscenario> scenario = scenarioRepository.findByName(name);
        return scenario.isPresent() && scenario.get().isEnabled();
    }

    // Enable a scenario
    @Transactional
    public void enableScenario(String name) {
        Appscenario scenario = getOrCreateScenario(name);
        scenario.setEnabled(true);
        scenarioRepository.saveAndFlush(scenario);
    }

    // Disable a scenario
    @Transactional
    public void disableScenario(String name) {
        Appscenario scenario = getOrCreateScenario(name);
        scenario.setEnabled(false);
        scenarioRepository.saveAndFlush(scenario);
    }

    // Toggle the state of a scenario
    @Transactional
    public boolean toggleScenario(String name) {
        Appscenario scenario = getOrCreateScenario(name);
        boolean newState = !scenario.isEnabled();
        scenario.setEnabled(newState);
        scenarioRepository.saveAndFlush(scenario);
        return newState;
    }

    // Configure the Return 404 scenario for a given endpoint
    @Transactional
    public void configureReturn404Scenario(String endpoint, boolean enabled) {
        String scenarioName = endpoint == null || endpoint.trim().isEmpty() ? "return_404" : "return_404_" + endpoint;
        Appscenario scenario = getOrCreateScenario(scenarioName);
        scenario.setEnabled(enabled);
        scenarioRepository.saveAndFlush(scenario);
    }

    // Get all scenarios
    public List<Appscenario> getAllScenarios() {
        return scenarioRepository.findAll();
    }

    // Get all return 404 endpoints
    public List<Appscenario> getReturn404Endpoints() {
        return scenarioRepository.findAll().stream()
                .filter(s -> s.getName().startsWith("return_404_"))
                .toList();
    }

    // Helper method to get or create a scenario
    private Appscenario getOrCreateScenario(String name) {
        Optional<Appscenario> scenarioOpt = scenarioRepository.findByName(name);
        if (scenarioOpt.isPresent()) {
            return scenarioOpt.get();
        } else {
            Appscenario scenario = new Appscenario();
            scenario.setName(name);
            scenario.setDescription("Auto-created scenario: " + name);
            return scenario;
        }
    }
}

package org.example.yasspfe.entities;

import org.example.yasspfe.reposotories.ScenarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ScenarioInitializer implements CommandLineRunner {
    private final ScenarioRepository repository;

    public ScenarioInitializer(ScenarioRepository repository) {
        this.repository = repository;
    }
    @Override
    public void run(String... args) {
        List<String> scenarioNames = Arrays.asList("latency_injection", "packet_loss", "stress_testing");

        for (String name : scenarioNames) {
            if (repository.findByName(name).isEmpty()) {
                repository.save(new Scenario(name, false, "")); // Default to disabled, added description
            }
        }
    }
}

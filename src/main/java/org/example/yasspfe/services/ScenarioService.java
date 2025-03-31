package org.example.yasspfe.services;

import org.example.yasspfe.entities.Scenario;
import org.example.yasspfe.reposotories.ScenarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.List;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Service
public class ScenarioService {

    private final ScenarioRepository repository;

    public ScenarioService(ScenarioRepository repository) {
        this.repository = repository;
    }

    @Autowired
    private ScenarioRepository scenarioRepository;

    public void enableScenario(String name) {
        executeUpdate("UPDATE scenarios SET enabled = b'1' WHERE name = ?", name);
    }

    public void disableScenario(String name) {
        executeUpdate("UPDATE scenarios SET enabled = b'0' WHERE name = ?", name);
    }

    public boolean isScenarioEnabled(String name) {
        return repository.findByName(name).map(Scenario::isEnabled).orElse(false);
    }

    public boolean toggleScenario(String name) {
        // Using XOR to toggle the bit value in a more reliable way
        executeUpdate("UPDATE scenarios SET enabled = IF(enabled = b'1', b'0', b'1') WHERE name = ?", name);
        return false;
    }


    private void executeUpdate(String sql, String name) {
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/proxybase", "root", "root");
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            int updatedRows = stmt.executeUpdate();
            if (updatedRows == 0) {
                // If no rows updated, scenario doesn't exist - create it
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO scenarios (name, enabled) VALUES (?, b'1')")) {
                    insertStmt.setString(1, name);
                    insertStmt.executeUpdate();
                    System.out.println("Created new scenario: " + name);
                }
            } else {
                System.out.println("Scenario updated successfully: " + name);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update scenario: " + e.getMessage(), e);
        }
    }

    public List<Scenario> getAllScenarios() {
        return scenarioRepository.findAll();
    }
}
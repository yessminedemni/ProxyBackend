package org.example.yasspfe.reposotories;

import org.example.yasspfe.entities.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {
     Optional<Scenario> findByName(String name);
}
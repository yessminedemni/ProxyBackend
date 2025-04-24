package org.example.yasspfe.reposotories;

import org.example.yasspfe.entities.Appscenario; // Use Appscenario here
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface Appscenariorepo extends JpaRepository<Appscenario, Long> { // Use Appscenario here
    Optional<Appscenario> findByName(String name); // Use Appscenario here
}

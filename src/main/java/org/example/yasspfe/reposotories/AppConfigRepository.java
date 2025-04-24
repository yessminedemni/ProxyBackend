package org.example.yasspfe.reposotories;

import org.example.yasspfe.entities.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppConfigRepository extends JpaRepository<AppConfig, Integer> {

    Optional<AppConfig> findTopByOrderByIdDesc();
}

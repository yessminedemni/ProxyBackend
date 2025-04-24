package org.example.yasspfe.services;

import org.example.yasspfe.entities.AppConfig;
import org.example.yasspfe.reposotories.AppConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AppConfigService {

    private final AppConfigRepository appConfigRepository;

    @Autowired
    public AppConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    // Save app configuration to the database
    public AppConfig saveAppConfig(String host, int port, String appName) {
        AppConfig config = new AppConfig();
        config.setHost(host);
        config.setPort(port);
        config.setAppName(appName);
        return appConfigRepository.save(config);
    }

    // Get the latest configuration


    public AppConfig getLatestConfig() {
        // Implement logic to retrieve latest config
        // This could be getting the most recent entry by ID or timestamp
        return appConfigRepository.findTopByOrderByIdDesc().orElse(null);
    }
}

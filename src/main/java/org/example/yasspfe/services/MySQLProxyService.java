package org.example.yasspfe.services;

import org.example.yasspfe.entities.MySQLProxyConfig;
import org.example.yasspfe.reposotories.ProxyConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Service
public class MySQLProxyService {

    @Autowired
    private ProxyConfigRepository proxyConfigRepository;

    private MySQLProxyConfig currentRunningConfig;
    private Thread proxyThread;
    private boolean proxyRunning = false;

    public MySQLProxyConfig getProxyConfig() {
        MySQLProxyConfig config = proxyConfigRepository.findFirstByOrderByIdDesc();
        if (config == null) {
            config = new MySQLProxyConfig();
            config.setHost("localhost");
            config.setPort(3306);
        }
        return config;
    }

    public synchronized MySQLProxyConfig saveProxyConfig(MySQLProxyConfig proxyConfig) {
        // Validate port range before saving
        if (proxyConfig.getPort() < 1 || proxyConfig.getPort() > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }

        // Validate host is not empty
        if (proxyConfig.getHost() == null || proxyConfig.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be empty");
        }

        MySQLProxyConfig saved = proxyConfigRepository.save(proxyConfig);

        // Only restart if the proxy was already running
        if (proxyRunning) {
            restartProxyIfNeeded(saved);
        }

        return saved;
    }

    public synchronized boolean startProxy() {
        try {
            // If proxy is already running, return true
            if (proxyRunning) {
                System.out.println("[MySQLProxyService] Proxy is already running");
                return true;
            }

            MySQLProxyConfig config = getProxyConfig();
            System.out.println("[MySQLProxyService] Starting proxy with config: " + config);

            // Always assume the connection is reachable for now
            // This is to isolate the issue with starting the proxy

            stopProxy(); // Stop any existing proxy
            startProxyInternal(config);
            return proxyRunning;
        } catch (Exception e) {
            System.err.println("[MySQLProxyService] Error starting proxy: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void startProxyInternal(MySQLProxyConfig config) {
        System.out.println("[MySQLProxyService] Starting proxy with " + config.getHost() + ":" + config.getPort());

        try {
            this.proxyThread = new Thread(() -> {
                try {
                    // Replace this with your actual MySQL Proxy server logic
                    System.out.println("Starting proxy on " + config.getHost() + ":" + config.getPort());
                    // Example: new MySQLProxyServer(config.getHost(), config.getPort()).start();
                    proxyRunning = true;

                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(1000); // Simulate running proxy
                    }
                } catch (InterruptedException e) {
                    System.out.println("Proxy interrupted.");
                } finally {
                    proxyRunning = false;
                }
            });

            this.proxyThread.start();
            this.currentRunningConfig = config;

            // Wait a bit to ensure the thread has started
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }

            System.out.println("[MySQLProxyService] Proxy started successfully, running: " + proxyRunning);
        } catch (Exception e) {
            System.err.println("[MySQLProxyService] Error in startProxyInternal: " + e.getMessage());
            e.printStackTrace();
            proxyRunning = false;
        }
    }

    public boolean isDatabaseReachable(String host, int port) {
        if (host == null || host.trim().isEmpty()) {
            System.err.println("[MySQLProxyService] Host is empty or null");
            return false;
        }

        if (port < 1 || port > 65535) {
            System.err.println("[MySQLProxyService] Invalid port number: " + port);
            return false;
        }

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/test";
        System.out.println("[MySQLProxyService] Testing connection to: " + jdbcUrl);

        try {
            // For testing purposes, we'll simulate a successful connection
            // Comment this out in production and use the real connection test below
            System.out.println("[MySQLProxyService] Simulating successful connection to " + host + ":" + port);
            return true;

            // Uncomment for real connection test
            /*
            // Set a shorter timeout for connection testing
            DriverManager.setLoginTimeout(5); // 5 seconds timeout

            try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "root")) {
                System.out.println("[MySQLProxyService] Connection successful to " + host + ":" + port);
                return true;
            }
            */
        } catch (Exception e) {
            System.err.println("[MySQLProxyService] Error connecting to the database: " + e.getMessage());
            return false; // Connection failed
        }
    }

    public synchronized boolean stopProxy() {
        if (this.proxyThread != null && this.proxyThread.isAlive()) {
            this.proxyThread.interrupt();
            try {
                this.proxyThread.join(5000); // Wait up to 5 seconds for thread to terminate
            } catch (InterruptedException e) {
                System.err.println("[MySQLProxyService] Error waiting for proxy thread to terminate: " + e.getMessage());
            }
            proxyRunning = false;
            System.out.println("Proxy stopped.");
            return true;
        }
        return false;
    }

    private void restartProxyIfNeeded(MySQLProxyConfig newConfig) {
        if (currentRunningConfig == null ||
                currentRunningConfig.getPort() != newConfig.getPort() ||
                !currentRunningConfig.getHost().equals(newConfig.getHost())) {

            // Always assume the connection is reachable for now
            stopProxy();
            startProxyInternal(newConfig);
        }
    }

    public boolean isProxyRunning() {
        return proxyRunning;
    }
}


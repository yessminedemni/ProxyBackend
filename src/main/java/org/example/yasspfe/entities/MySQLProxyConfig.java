package org.example.yasspfe.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Entity
public class MySQLProxyConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Host cannot be empty")
    private String host;

    @Min(value = 1, message = "Port must be at least 1")
    @Max(value = 65535, message = "Port must be at most 65535")
    private int port;

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public MySQLProxyConfig(Long id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
    }

    public MySQLProxyConfig() {
    }

    @Override
    public String toString() {
        return "MySQLProxyConfig{" +
                "id=" + id +
                ", host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}


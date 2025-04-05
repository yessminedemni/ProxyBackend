package org.example.yasspfe.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class DatabaseConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String databaseType;
    private String host;
    private String port;
    private String databaseName;

    private boolean useCustomUrl;
    private String customUrl;

    private String username;
    private String password;

    // ✅ Default constructor
    public DatabaseConfig() {}

    // ✅ Safe constructor if you have all fields
    public DatabaseConfig(String databaseType, String host, String port, String databaseName,
                          boolean useCustomUrl, String customUrl, String username, String password) {
        this.databaseType = databaseType;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.useCustomUrl = useCustomUrl;
        this.customUrl = customUrl;
        this.username = username;
        this.password = password;
    }

    // ✅ Fixed constructor for full JDBC URL
    public DatabaseConfig(String jdbcUrl, String username, String password) {
        this.useCustomUrl = true;
        this.customUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    // ✅ Builds the JDBC URL dynamically, or returns the custom one
    public String getJdbcUrl() {
        if (useCustomUrl && customUrl != null && !customUrl.isEmpty()) {
            return customUrl;
        }
        return "jdbc:" + databaseType.toLowerCase() + "://" + host + ":" + port + "/" + databaseName;
    }

    // ➕ Debug-friendly output
    @Override
    public String toString() {
        return "DatabaseConfig{" +
                "useCustomUrl=" + useCustomUrl +
                ", jdbcUrl='" + getJdbcUrl() + '\'' +
                ", username='" + username + '\'' +
                '}';
    }

    // Getters & setters

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public boolean isUseCustomUrl() {
        return useCustomUrl;
    }

    public void setUseCustomUrl(boolean useCustomUrl) {
        this.useCustomUrl = useCustomUrl;
    }

    public String getCustomUrl() {
        return customUrl;
    }

    public void setCustomUrl(String customUrl) {
        this.customUrl = customUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}

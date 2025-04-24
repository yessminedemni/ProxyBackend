package org.example.yasspfe.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "app_scenarios") // Make sure this matches your actual table name
public class Appscenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean enabled = false;

    private String description;

    // Default constructor required by JPA
    public Appscenario() {
    }

    public Appscenario(Long id, String name, boolean enabled, String description) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.description = description;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "Appscenario{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                ", description='" + description + '\'' +
                '}';
    }
}

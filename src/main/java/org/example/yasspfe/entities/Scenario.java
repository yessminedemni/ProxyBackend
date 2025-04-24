package org.example.yasspfe.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "scenarios")
public class Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean enabled;
    private String description ;

    public Scenario() {}

    public Scenario(String name, boolean enabled, String description) {
        this.name = name;
        this.enabled = enabled;
        this.description = description;

    }

    public Scenario(String name, boolean b) {
        this.name = name;
        this.enabled = b;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

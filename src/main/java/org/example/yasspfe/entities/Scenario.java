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

    public Scenario() {}

    public Scenario(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}

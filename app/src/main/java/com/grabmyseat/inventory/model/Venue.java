package com.grabmyseat.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "venue")
public class Venue {

    @Id
    private Long id;

    private String name;

    private String city;

    protected Venue() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }
}

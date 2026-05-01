package com.grabmyseat.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "organizer_profiles")
public class OrganizerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "company_email", nullable = false, unique = true)
    private String companyEmail;

    @Column(name = "company_phone", nullable = false)
    private String companyPhone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected OrganizerProfile() {
    }

    public OrganizerProfile(Long userId, String companyName, String companyEmail, String companyPhone) {
        this.userId = userId;
        this.companyName = companyName;
        this.companyEmail = companyEmail;
        this.companyPhone = companyPhone;
    }
}

package com.grabmyseat.auth.repository;

import com.grabmyseat.auth.model.OrganizerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizerProfileRepository extends JpaRepository<OrganizerProfile, Long> {

    boolean existsByCompanyEmail(String companyEmail);
}

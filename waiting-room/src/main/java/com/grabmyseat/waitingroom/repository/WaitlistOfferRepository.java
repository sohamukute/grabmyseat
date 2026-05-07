package com.grabmyseat.waitingroom.repository;

import com.grabmyseat.waitingroom.model.WaitlistOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WaitlistOfferRepository extends JpaRepository<WaitlistOffer, Long> {
    Optional<WaitlistOffer> findByToken(String token);
}

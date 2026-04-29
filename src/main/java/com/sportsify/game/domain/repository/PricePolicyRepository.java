package com.sportsify.game.domain.repository;

import com.sportsify.game.domain.model.PricePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricePolicyRepository extends JpaRepository<PricePolicy, Long> {
}

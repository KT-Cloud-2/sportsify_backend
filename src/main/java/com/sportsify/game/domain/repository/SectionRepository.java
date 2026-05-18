package com.sportsify.game.domain.repository;

import com.sportsify.game.domain.model.Section;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {
}

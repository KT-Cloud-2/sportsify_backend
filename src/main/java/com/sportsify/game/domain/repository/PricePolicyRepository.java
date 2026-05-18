package com.sportsify.game.domain.repository;

import com.sportsify.game.domain.model.DayType;
import com.sportsify.game.domain.model.GameGrade;
import com.sportsify.game.domain.model.PricePolicy;
import com.sportsify.game.domain.model.Stadium;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface PricePolicyRepository extends JpaRepository<PricePolicy, Long> {
    @Query("SELECT p FROM PricePolicy p " +
            "WHERE p.stadium = :stadium " +
            "AND p.dayType = :dayType " +
            "AND p.gameGrade = :gameGrade " +
            "AND p.zoneGrade.name IN :zoneGradeNames")
    List<PricePolicy> findAllByGameInfoAndZoneGrades(
            @Param("stadium") Stadium stadium,
            @Param("dayType") DayType dayType,
            @Param("gameGrade") GameGrade gameGrade,
            @Param("zoneGradeNames") Set<String> zoneGradeNames
    );
}

package com.sportsify.game.application.service;

import com.sportsify.game.domain.model.*;
import com.sportsify.game.domain.repository.PricePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PricePolicyService {
    private final PricePolicyRepository pricePolicyRepository;

    public Map<String, Integer> getPriceMap(Game game, List<GameSeat> availableSeats) {
        Stadium stadium = game.getStadium();
        DayType dayType = game.getDayType();
        GameGrade gameGrade = game.getGameGrade();

        Set<String> zoneGradeNames = availableSeats.stream()
                .map(GameSeat::getZoneGradeName)
                .collect(Collectors.toSet());

        return pricePolicyRepository
                .findAllByGameInfoAndZoneGrades(stadium, dayType, gameGrade, zoneGradeNames)
                .stream()
                .collect(Collectors.toMap(
                        pp -> pp.getZoneGrade().getName(),
                        PricePolicy::getPrice
                ));
    }
}

package com.sportsify.game.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.PricePolicy;
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
        Set<String> zoneGradeNames = extractZoneGradeNames(availableSeats);
        Map<String, Integer> priceMap = fetchPriceMap(game, zoneGradeNames);

        validateAllZoneGradesHavePrice(zoneGradeNames, priceMap);

        return priceMap;
    }

    private Set<String> extractZoneGradeNames(List<GameSeat> seats) {
        return seats.stream()
                .map(GameSeat::getZoneGradeName)
                .collect(Collectors.toSet());
    }

    private Map<String, Integer> fetchPriceMap(Game game, Set<String> zoneGradeNames) {
        return pricePolicyRepository
                .findAllByGameInfoAndZoneGrades(
                        game.getStadium(),
                        game.getDayType(),
                        game.getGameGrade(),
                        zoneGradeNames
                )
                .stream()
                .collect(Collectors.toMap(
                        pp -> pp.getZoneGrade().getName(),
                        PricePolicy::getPrice,
                        (existing, replacement) -> existing
                ));
    }

    private void validateAllZoneGradesHavePrice(Set<String> zoneGradeNames, Map<String, Integer> priceMap) {
        List<String> missingZoneGrades = zoneGradeNames.stream()
                .filter(name -> !priceMap.containsKey(name))
                .toList();

        if (!missingZoneGrades.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.PRICE_POLICY_NOT_FOUND,
                    "가격 정책 없음: " + String.join(", ", missingZoneGrades)
            );
        }
    }
}

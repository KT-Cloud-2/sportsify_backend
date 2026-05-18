package com.sportsify.game.application;

import com.sportsify.game.application.service.PricePolicyService;
import com.sportsify.game.domain.model.*;
import com.sportsify.game.domain.repository.PricePolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricePolicyServiceTest {

    @Spy
    @InjectMocks
    private PricePolicyService pricePolicyService;

    @Mock
    private PricePolicyRepository pricePolicyRepository;

    @Test
    @DisplayName("좌석 등급에 따른 가격을 반환한다.")
    void getPriceMap_returnsCorrectPriceMap() {

        Game mockGame = mock(Game.class);
        Stadium mockStadium = mock(Stadium.class);

        GameSeat seat1 = mock(GameSeat.class);
        GameSeat seat2 = mock(GameSeat.class);

        PricePolicy mockPricePolicy = mock(PricePolicy.class);
        ZoneGrade mockZoneGrade = mock(ZoneGrade.class);

        when(mockGame.getStadium()).thenReturn(mockStadium);
        when(mockGame.getDayType()).thenReturn(DayType.WEEKDAY);
        when(mockGame.getGameGrade()).thenReturn(GameGrade.NORMAL);

        when(seat1.getZoneGradeName()).thenReturn("STANDARD");
        when(seat2.getZoneGradeName()).thenReturn("STANDARD");

        when(mockPricePolicy.getPrice()).thenReturn(10000);
        when(mockPricePolicy.getZoneGrade()).thenReturn(mockZoneGrade);
        when(mockZoneGrade.getName()).thenReturn("STANDARD");

        Set<String> expectedZoneGradeNames = Set.of("STANDARD");
        when(pricePolicyRepository.findAllByGameInfoAndZoneGrades(
                eq(mockStadium),
                eq(DayType.WEEKDAY),
                eq(GameGrade.NORMAL),
                eq(expectedZoneGradeNames)))
                .thenReturn(List.of(mockPricePolicy));

        Map<String, Integer> priceMap = pricePolicyService.getPriceMap(
                mockGame,
                List.of(seat1, seat2)
        );

        assertThat(priceMap).hasSize(1);
        assertThat(priceMap.get("STANDARD")).isEqualTo(10000);

    }
}

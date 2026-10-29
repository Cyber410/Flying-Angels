package com.flyingangels.backend.event;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ResultValuesTest {
    @Test void recognizesRaceAndFieldUnitsIndependentlyOfRankingFallback() {
        for (String race : new String[]{"50m", "55m", "60m", "100M", "150M", "200M", "300M", "400M",
                "600M", "800M", "1200M", "1500M", "1600m", "2000M", "3000M", "1 MILE RUN",
                "300m Steeple Chase", "55M HURDLES", "60M HURDLES", "80M HURDLES", "100M HURDLES",
                "110M HURDLES", "200M HURDLES", "300M HURDLES", "400M HURDLES", "4x100m", "Sprint race"}) {
            assertTrue(ResultValues.isPerformanceEvent(race), race);
            assertEquals(ResultUnit.SECONDS, ResultValues.unitFor(race, RankingOrder.DESC), race);
        }
        for (String field : new String[]{"LONG JUMP", "TRIPLE JUMP", "HIGH JUMP", "SHOT PUT", "DISCUS",
                "JAVELIN", "HAMMER THROW", "Weight Throw", "Pole Vault"}) {
            assertTrue(ResultValues.isPerformanceEvent(field), field);
            assertEquals(ResultUnit.METRES, ResultValues.unitFor(field, RankingOrder.ASC), field);
        }
        assertFalse(ResultValues.isPerformanceEvent("Administrative column"));
        assertEquals(ResultUnit.POINTS, ResultValues.unitFor("Decathlon", RankingOrder.DESC));
    }
    @Test void normalizesTrackTimesAndFieldMeasurements() {
        assertEquals(new BigDecimal("164.07"),ResultValues.parse("2:44.07",ResultUnit.SECONDS,null).score());
        assertEquals(new BigDecimal("4.85"),ResultValues.parse("4.85m",ResultUnit.METRES,null).score());
        assertEquals(ResultStatus.NT,ResultValues.parse("NT",ResultUnit.SECONDS,null).status());
        assertNull(ResultValues.parse("FOUL",ResultUnit.METRES,null).score());
    }
    @Test void rejectsInconsistentScoresAndInvalidFormats() {
        assertThrows(IllegalArgumentException.class,()->ResultValues.parse("12.40",ResultUnit.SECONDS,new BigDecimal("13")));
        assertThrows(IllegalArgumentException.class,()->ResultValues.parse("2:99.00",ResultUnit.SECONDS,null));
        assertThrows(IllegalArgumentException.class,()->ResultValues.parse("2:44.07",ResultUnit.METRES,null));
        assertThrows(IllegalArgumentException.class,()->ResultValues.parse("NT",ResultUnit.SECONDS,BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,()->ResultValues.parse("12.4q",ResultUnit.SECONDS,null));
    }
}

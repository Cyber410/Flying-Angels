package com.flyingangels.backend.event;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ResultValuesTest {
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

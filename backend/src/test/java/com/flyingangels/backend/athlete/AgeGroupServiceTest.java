package com.flyingangels.backend.athlete;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgeGroupServiceTest {
    @Test void noCategoriesAreAssumedWhenTheClubHasNotConfiguredBands() {
        var service = new AgeGroupService(""); assertFalse(service.configuration().configured());
        assertNull(service.classify(LocalDate.of(2010, 1, 1), LocalDate.of(2026, 1, 1)));
    }
    @Test void leapDayBoundariesAndMissingBirthDatesUseTheExplicitDate() {
        var service = new AgeGroupService("Junior:0:16;Senior:17:150");
        assertEquals("Junior", service.classify(LocalDate.of(2008, 2, 29), LocalDate.of(2025, 2, 28)));
        assertEquals("Senior", service.classify(LocalDate.of(2008, 2, 29), LocalDate.of(2025, 3, 1)));
        assertNull(service.classify(null, LocalDate.of(2025, 3, 1)));
        assertNull(service.classify(LocalDate.of(2030, 1, 1), LocalDate.of(2025, 3, 1)));
    }
    @Test void duplicateOverlappingAndMalformedBandDefinitionsAreRejected() {
        for (String config : List.of("A:0:10;B:10:20", "A:0:10;a:11:20", "A:20:10", "A:-1:10", "A:0:151", "A:0", "A:x:20", ":0:10"))
            assertThrows(IllegalArgumentException.class, () -> new AgeGroupService(config), config);
    }
}

package com.flyingangels.backend.athlete;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class AgeRangeTest {
    @Test void ageChangesOnBirthdayAndTreatsUnknownOrNotYetBornAsUnknown() {
        var dob=LocalDate.of(2012,5,15);
        assertEquals(13,AgeRange.ageOn(dob,LocalDate.of(2026,5,14)));
        assertEquals(14,AgeRange.ageOn(dob,LocalDate.of(2026,5,15)));
        assertNull(AgeRange.ageOn(null,LocalDate.of(2026,5,15)));
        assertNull(AgeRange.ageOn(dob,LocalDate.of(2010,1,1)));
    }
    @Test void leapDayBirthdaysAdvanceOnMarchFirstInNonLeapYears() {
        var dob=LocalDate.of(2012,2,29);
        assertEquals(13,AgeRange.ageOn(dob,LocalDate.of(2026,2,28)));
        assertEquals(14,AgeRange.ageOn(dob,LocalDate.of(2026,3,1)));
    }
    @Test void ageBoundsAndDirectoryReferenceDateAreValidated() {
        assertThrows(IllegalArgumentException.class,()->new AgeRange(15,10));
        assertThrows(IllegalArgumentException.class,()->new AgeRange(-1,null));
        assertThrows(IllegalArgumentException.class,()->new AgeRange(null,151));
        assertThrows(IllegalArgumentException.class,()->new AgeRange(10,12).birthDates(null));
        assertFalse(new AgeRange(null,null).active());
    }
}

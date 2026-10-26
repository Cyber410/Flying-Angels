package com.flyingangels.backend.importing;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class AthleteRegistrationParserTest {
    @Test void mapsReorderedCsvHeadersAndReturnsDateErrorsWithoutAbortingOtherRows() throws Exception {
        String csv="MEMBER #,LAST NAME,FIRST NAME,DATE OF BIRTH,Gender,100m,Administrative column\n"
            +"42,Lovelace,Ada,2010-03-21,Female,12.4,Ignore\n"
            +"43,Smith,Bob,0000-00-00,Male,NT,Ignore\n";
        var rows=new AthleteRegistrationParser().parse(csv.getBytes(StandardCharsets.UTF_8),"athletes.csv");
        assertEquals(2,rows.size());assertTrue(rows.get(0).errors().isEmpty());
        assertEquals("Ada",rows.get(0).athlete().firstName());assertEquals("42",rows.get(0).externalId());
        assertEquals(1,rows.get(0).athlete().events().size());assertFalse(rows.get(1).errors().isEmpty()); assertNull(rows.get(1).athlete().dateOfBirth());
    }
    @Test void flagsMissingBirthDateEvenWithoutADobColumn() throws Exception {
        var rows=new AthleteRegistrationParser().parse("FIRST NAME\tLAST NAME\nAda\tLovelace\n".getBytes(StandardCharsets.UTF_8),"athletes.tsv");
        assertEquals(1,rows.size());assertTrue(rows.get(0).errors().contains("Birth date is required for import"));assertNull(rows.get(0).athlete().dateOfBirth());
    }
    @Test void parsesClubHeaderAndFlagsAmbiguousSourceValues() throws Exception {
        String headers="FIRST Name\tLAST NAME\tNOTE\t(FLY) Flying Angels TF Academy\t\"FLYA\" Flying Angels Academy\tDATE OF BIRTH\tGender\t60m\t800M\tLONG JUMP\t\n";
        String data="Test\tAthlete\tSource note\tin Roster\tADDED\t2010-03-21\tfemale \t8.86q\t02:44.1\t5.18m\t\n"
            +"Review\tDate\t\t\t\t40700\tFemale\t\t\t\t\n";
        var rows=new AthleteRegistrationParser().parse((headers+data).getBytes(StandardCharsets.UTF_8),"club.tsv");
        assertEquals(2,rows.size());assertTrue(rows.get(0).errors().isEmpty());
        assertEquals("Added",rows.get(0).athlete().flyaStatus());assertEquals("Female",rows.get(0).athlete().gender());
        assertEquals("8.86q",rows.get(0).athlete().events().get(0).result());
        assertTrue(rows.get(0).warnings().stream().anyMatch(w->w.contains("8.86q")));
        assertFalse(rows.get(1).errors().isEmpty()); assertNull(rows.get(1).athlete().dateOfBirth());
    }

    @Test void rejectsMissingMalformedAndNonPastDates() throws Exception {
        for (String date : new String[]{"", "0000-00-00", "40700", "No birth date", "2010-02-30",
                "2999-01-01", java.time.LocalDate.now().toString()}) {
            var row = new AthleteRegistrationParser().parse(("FIRST NAME,LAST NAME,DOB\nTest,Athlete," + date + "\n")
                    .getBytes(StandardCharsets.UTF_8), "dates.csv").get(0);
            assertFalse(row.errors().isEmpty(), date);
            assertNull(row.athlete().dateOfBirth(), date);
        }
    }

    @Test void identifiesMixedPerformanceColumnsAndExplainsIncompatibleUnits() throws Exception {
        String csv = "FIRST NAME,LAST NAME,DOB,800M,LONG JUMP,HAMMER THROW,POLE VAULT,Administrative column\n"
                + "Test,Athlete,2010-03-21,5.18m,2:44.07,35.74m,1.40m,Not a result\n";
        var row = new AthleteRegistrationParser().parse(csv.getBytes(StandardCharsets.UTF_8), "mixed.csv").get(0);
        assertTrue(row.errors().isEmpty());
        assertEquals(4, row.athlete().events().size());
        assertTrue(row.warnings().stream().anyMatch(w -> w.contains("800M") && w.contains("expected SECONDS")));
        assertTrue(row.warnings().stream().anyMatch(w -> w.contains("LONG JUMP") && w.contains("expected METRES")));
        assertTrue(row.warnings().stream().noneMatch(w -> w.contains("HAMMER THROW") || w.contains("POLE VAULT")));
    }
}

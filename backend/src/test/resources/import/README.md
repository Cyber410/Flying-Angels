# UC-06 CSV import fixtures

Test files for the UC-06 import tests (T84–T104 in `sprint-2-test-cases-uc04-uc06.md`). Used by
`AthleteCsvImportIntegrationTest`, which is still a skeleton.

## Pending team decisions

- **Delimiter (comma or tab).** These fixtures use **commas**, to match the Trello card ("Only CSV files").
  The current `AthleteImportService` parses **tab-separated** files (`CSVFormat.TDF`), so these files will not
  import with today's code. Dev and the team need to choose one.
- **Required columns and exact header names (I2).** No real Trackie export has been received yet. The headers
  below come from the current code's test (`AthleteImportServiceTest`). The current `AthleteImportService`
  **does not check header names at all**. It reads columns by position and only requires at least 7 of them.
- **Duplicate rule (I3)** and **duplicates inside one file (I6)**. The counts below assume a duplicate is the same
  first name, last name, date of birth and gender (ignoring case), as the current code does, and that the first
  occurrence in a file is kept.
- **Date format (I4).** These fixtures use `yyyy-MM-dd`, the only format the current code reads.

## Header row

```
FIRST Name,LAST NAME,NOTE,(FLY) Flying Angels TF Academy,FLYA Flying Angels Academy,DATE OF BIRTH,Gender,100M,LONG JUMP
```

The first 7 columns are the athlete. Every column after that is an event, and its cell holds the result.
Team columns hold `In Roster` when the athlete is on that team and are empty otherwise.

## Files

| File | Rows | Contents |
|---|---|---|
| `valid.csv` | 5 | All valid, all different athletes |
| `empty.csv` | 0 | 0 bytes |
| `header-only.csv` | 0 | Header row only |
| `missing-column.csv` | 2 | No `DATE OF BIRTH` column (6 athlete columns) |
| `incomplete-rows.csv` | 5 | Rows 1 and 5 valid. Row 2 has no last name, row 3 no date of birth, row 4 no gender |
| `duplicates-in-file.csv` | 4 | Row 3 repeats row 1 (Ava Chen). 3 different athletes |
| `duplicate-of-existing.csv` | 3 | Row 1 is Jane Doe, 2004-03-15, Female, the same athlete as `AthleteTestData.janeDoe()`. The test seeds her first. Rows 2–3 are new |
| `mixed.csv` | 10 | **6 valid** (rows 1–6). **2 duplicates**: row 7 repeats row 1, and row 8 is Jane Doe (the test must seed her). **2 invalid**: row 9 has no last name, row 10 has an impossible date (2004-02-30) |
| `special-chars.csv` | 4 | UTF-8 **with BOM**. `Émilie`, `Siobhán O'Brien` (with a quoted note containing a comma), `Zoë Núñez` and `Seán D'Arcy-Lefèvre` with extra spaces around the names. They must be stored trimmed and unchanged |
| `not-csv.txt` | – | Plain text, for T90 (non-CSV upload) |

T90 also lists `.xlsx` and `.pdf`. Those can be generated in the test itself, so no binary fixtures are committed.

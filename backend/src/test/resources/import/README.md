# UC-06 import fixtures

Test files loaded by `AthleteCsvImportIntegrationTest` (T90 and T104 in `sprint-2-test-cases-uc04-uc06.md`).
Every other import case is covered by Dev's JUnit tests or the Postman collection, which build their CSV content
inline or use `docs/postman/fixtures/`.

## Files

| File | Rows | Contents |
|---|---|---|
| `special-chars.csv` | 4 | UTF-8 **with BOM**. `Émilie`, `Siobhán O'Brien` (with a quoted note containing a comma), `Zoë Núñez` and `Seán D'Arcy-Lefèvre` with extra spaces around the names. They must be stored trimmed and unchanged (T104) |
| `not-csv.txt` | – | Plain text, for T90 (non-CSV upload) |

Header row of `special-chars.csv`:

```
FIRST Name,LAST NAME,NOTE,(FLY) Flying Angels TF Academy,FLYA Flying Angels Academy,DATE OF BIRTH,Gender,100M,LONG JUMP
```

T90 also uploads a `.pdf`. It is generated in the test itself, so no binary fixture is committed.

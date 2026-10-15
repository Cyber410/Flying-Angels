# Athlete API

The backend accepts the supplied spreadsheet as either a tab-separated CSV/TSV file or an `.xlsx` workbook. For `.xlsx` files, the first worksheet is imported. The first seven columns are mapped as follows:

1. `FIRST Name`
2. `LAST NAME`
3. `NOTE`
4. `(FLY) Flying Angels TF Academy`
5. `FLYA Flying Angels Academy`
6. `DATE OF BIRTH`
7. `Gender`

Every later column becomes an athlete event when its cell has a value. Blank dates and non-ISO date values in the source are stored as unknown rather than blocking the entire import. Excel date cells and ISO-formatted text dates are supported.

## Endpoints

## Event Endpoints

## Meet Endpoints

A meet is the parent competition. One meet can contain multiple event categories, such as `100M`, `200M`, and `Long Jump`.

### Create meet

`POST /api/meets`

```json
{
	"name": "Spring Track Meet",
	"meetDate": "2026-05-16",
	"location": "GTA Stadium",
	"externalUrl": "https://example.com/spring-track-meet"
}
```

### List meets

`GET /api/meets`

### View a meet with its events

`GET /api/meets/{id}`

### Get one event from a meet

`GET /api/meets/{meetId}/events/{eventId}`

This only returns the event when it belongs to the specified meet.

### Update or delete a meet

`PUT /api/meets/{id}`

`DELETE /api/meets/{id}`

Deleting a meet deletes its child events and their assigned results.

Event categories are shared across imported results and newly created events. Examples include `50m`, `100M`, `LONG JUMP`, and `SHOT PUT`.

### List event categories

`GET /api/event-categories`

### Create a new event category

`POST /api/event-categories`

```json
{
	"name": "400M Mixed Relay",
	"rankingOrder": "ASC"
}
```

### Create event

`POST /api/events` returns `201 Created`.

```json
{
	"name": "Spring Track Meet",
	"eventDate": "2026-05-16",
	"location": "GTA Stadium",
	"eventType": "100M",
	"externalUrl": "https://example.com/spring-track-meet",
	"rankingOrder": "ASC",
	"meetId": 1,
	"categoryId": 2
}
```

Use either an existing `categoryId` or provide `categoryName` to create/reuse a category automatically:

```json
"categoryName": "400M Mixed Relay"
```

`externalUrl` is optional and must begin with `http://` or `https://`. `ASC` means lower scores rank first, which is appropriate for timed events. Use `DESC` for higher-is-better events such as jumps or throws.

### List events

`GET /api/events`

### View event and assigned results

`GET /api/events/{id}`

### Update event

`PUT /api/events/{id}`

### Delete event

`DELETE /api/events/{id}` returns `204 No Content` and removes its assigned results.

### Assign an athlete to an event

`POST /api/events/{id}/participants` returns `201 Created`.

```json
{
	"athleteId": 440
}
```

This creates the participation record without requiring a result.

### Record or update an assigned athlete's result

`PUT /api/events/{id}/participants/{athleteId}/result`

```json
{
	"result": "12.40",
	"score": 12.40
}
```

The athlete must already be assigned to the event.

### Assign an athlete and result in one request (legacy)

`POST /api/events/{id}/results` returns `201 Created`.

```json
{
	"athleteId": 440,
	"result": "12.40",
	"score": 12.40
}
```

An athlete can only be assigned once to the same event. A duplicate assignment returns `409 Conflict` with code `ATHLETE_ALREADY_ASSIGNED`.

### Update an assigned result

`PUT /api/events/{id}/results/{resultId}`

### Remove an assigned result

`DELETE /api/events/{id}/results/{resultId}` returns `204 No Content`.

### Get leaderboard

`GET /api/events/{id}/leaderboard`

Results are sorted by numeric `score` using the event's `rankingOrder`, with athlete name used as a tie-breaker.

Athlete profiles return the same centralized event-result shape. Imported results without a competition instance have a category and display result, while competition results additionally have a score and event assignment.

### Create athlete

`POST /api/athletes` creates one athlete and returns the profile response with status `201 Created`.

```json
{
	"firstName": "Ada",
	"lastName": "Lovelace",
	"note": "New athlete",
	"flyStatus": "In Roster",
	"flyaStatus": "",
	"dateOfBirth": "2010-03-21",
	"gender": "Female",
	"events": [
		{"eventName": "100M", "result": "12.40"}
	]
}
```

### Update athlete

`PUT /api/athletes/{id}` replaces the athlete profile and event list. It returns status `200 OK`.

### Delete athlete

`DELETE /api/athletes/{id}` removes the athlete and their events. It returns status `204 No Content`.

Create and update return `409 Conflict` with code `ATHLETE_ALREADY_EXISTS` when the same name, date of birth, and gender already exist.

### Import

`POST /api/athletes/import` with a multipart field named `file`.

Response:

```json
{"athleteCount": 2, "eventCount": 4, "duplicateCount": 0}
```

Repeated uploads are idempotent. Records with the same normalized identity are skipped, and the response reports them in `duplicateCount`. Athletes without a date of birth are compared using their remaining profile fields and populated event results so legitimate athletes with the same name are not merged.

### Directory

`GET /api/athletes?search=ada&gender=Female&team=FLYA`

All query parameters are optional. Search is trimmed, case-insensitive, and matches partial full names. `gender` matches case-insensitively. `team` accepts `FLY` or `FLYA` and checks whether the corresponding source status is populated. An empty array is returned when nothing matches.

### Profile

`GET /api/athletes/{id}` returns the selected athlete's name, date of birth, source status fields, and event results. The `events` array is empty when no event result was imported.
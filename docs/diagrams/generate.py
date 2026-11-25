"""Generate all ten editable draw.io pages and their SVG exports. No dependencies.

Run from the repository root: python3 docs/diagrams/generate.py
Edit PAGES for the earlier implemented diagrams and SPRINT4_PAGES for target designs.
"""
from pathlib import Path
from html import escape
import xml.etree.ElementTree as ET
import textwrap

ROOT = Path(__file__).resolve().parent
# Each page traces the implemented backend and its API consumers.
PAGES = [('01-meet-management',
  'Meet Management',
  [('Client', 'HTTP API consumer', 'Create, list, view, edit, delete meet'),
   ('HTTP API',
    'Meet routes',
    'GET, POST /api/meets',
    'GET, PUT, DELETE /api/meets/{id}',
    'GET /api/meets/{id}/events'),
   ('Controller',
    'MeetController',
    '@Valid MeetRequest',
    'list(), get(), create(), update(), delete(), events()'),
   ('Service',
    'MeetService',
    'Validate date range; keep existing events inside dates',
    'Get meet; save changes; delete meet and events'),
   ('Repository',
    'MeetRepository + EventRepository',
    'findById(), findAllByOrderByMeetDateDescNameAsc()',
    'save(), delete(); fetch meet events and categories'),
   ('Entity / Database',
    'Meet / meets; Event / events',
    'name, startDate, endDate, location, externalUrl',
    'meetDate retained as a compatibility alias'),
   ('Response',
    'MeetResponse + MeetProfileResponse',
    'Summary: dates and eventCount',
    'Profile: meet + events[]; static DTO from() mapping')]),
 ('02-event-management',
  'Event Management',
  [('Client', 'HTTP API consumer', 'Reusable definitions; batch add; selected copy'),
   ('HTTP API',
    'Definition and meet-event routes',
    'GET, POST /api/event-categories',
    'GET, POST /api/events | GET, PUT, DELETE /api/events/{id}',
    'POST /api/meets/{id}/events/bulk',
    'POST /api/meets/{id}/events/copy'),
   ('Controller',
    'EventCategoryController / EventController',
    'MeetEventController: add(), copy()',
    'Validate EventBatchRequest / CopyEventsRequest'),
   ('Service',
    'EventCategoryService + EventService',
    'Resolve reusable category; validate meet dates',
    'createBatch(), copyEvents(): atomic operations',
    'Copy definitions into new events with empty participation'),
   ('Repository',
    'EventCategoryRepository / EventRepository',
    'Category name lookup; meet/category duplicate checks',
    'MeetRepository verifies source and target meets'),
   ('Entity / Database',
    'EventCategory + Event',
    'Category: name, rankingOrder, unit',
    'Event: meet_id, category_id, eventDate',
    'UNIQUE(meet_id, category_id)'),
   ('Response',
    'EventCategoryResponse / EventResponse',
    'Category includes measurement unit',
    'Event includes meet, category, participantCount')]),
 ('03-athlete-participation',
  'Athlete Event Participation',
  [('Client', 'HTTP API consumer', 'Assign one/many athletes; list; remove'),
   ('HTTP API',
    'Participation routes',
    'POST, GET /api/events/{id}/participants',
    'POST /api/events/{id}/participants/bulk',
    'DELETE /api/events/{id}/participants/{athleteId}'),
   ('Controller',
    'EventController',
    'assignParticipant(), assignBulk(), participants()',
    'removeParticipant(); validated athlete IDs'),
   ('Service',
    'EventService',
    'assignParticipant() creates independent assignment',
    'assignBatch() rolls back if any assignment fails',
    'removeParticipant() deletes assignment and its attempts'),
   ('Repository',
    'EventParticipationRepository',
    'existsByEventIdAndAthleteId(), save(), delete()',
    'findByEventIdOrderByAthleteLastNameAscAthleteFirstNameAsc()'),
   ('Entity / Database',
    'EventParticipation / event_participations',
    'event_id + athlete_id are required',
    'UNIQUE(event_id, athlete_id)',
    'Same athlete can enter different events'),
   ('Response',
    'EventParticipationResponse',
    'participationId; athlete identity',
    'event and meet IDs/names; category identity')]),
 ('04-results-management',
  'Results Management',
  [('Frontend',
    'AthleteProfilePage.tsx',
    'getAthleteById() displays historical + meet results',
    'HTTP API consumers record and correct attempts'),
   ('HTTP API',
    'Result routes',
    'GET, POST /api/events/{id}/results',
    'POST /api/events/{id}/results/bulk',
    'PUT, DELETE /api/events/{id}/results/{resultId}',
    'GET /api/events/{id}/leaderboard'),
   ('Controller',
    'EventController',
    'addResult(), results(), resultsBulk()',
    'updateResult(), deleteResult(), leaderboard()'),
   ('Service',
    'EventService + ResultValues',
    'Append attempts; validate unit and normalized value',
    'Parse seconds/metres/points and performance statuses',
    'Bulk saves are atomic; leaderboard uses best attempt'),
   ('Repository',
    'EventResultRepository + participation repositories',
    'save(), findByIdAndEventId(), delete()',
    'Create participation if missing when recording result'),
   ('Entity / Database',
    'EventResult / event_results',
    'Independent IDs allow repeated athlete/event results',
    'Raw result, score, unit, status, recordedAt, source',
    'EventParticipation stores unique assignment separately'),
   ('Response',
    'EventResultResponse',
    'resultId, athlete, category, raw result, numeric score',
    'eventId/name, meetId/name, unit, status, source')]),
 ('05-athlete-notes',
  'Athlete Notes',
  [('Client',
    'Athlete profile + HTTP API consumer',
    'General note remains in profile',
    'Timestamped history via dedicated notes API'),
   ('HTTP API',
    'Note routes',
    'GET, POST /api/athletes/{id}/notes',
    'GET /api/athletes/{id} includes general note'),
   ('Controller',
    'AthleteNoteController',
    'create(), list(); @Valid AthleteNoteRequest',
    'Optional eventId; required note text'),
   ('Service',
    'AthleteNoteService',
    'Verify athlete and optional event; append note',
    'List newest first; no general-note replacement'),
   ('Repository',
    'AthleteNoteRepository',
    'findByAthleteIdOrderByCreatedAtDescIdDesc()',
    'save(); detachEvent() preserves notes on event deletion'),
   ('Entity / Database',
    'AthleteNote / athlete_notes',
    'athlete_id, optional event_id, text, created_at',
    'Athlete.note remains separate',
    'Index supports athlete/time retrieval'),
   ('Response',
    'AthleteNoteResponse',
    'id, athleteId, text, createdAt',
    'Optional eventId/name and meetId/name')]),
 ('06-athlete-import',
  'CSV Athlete Import',
  [('Client', 'HTTP API consumer', 'Upload CSV/TSV/XLSX; review rows; select row numbers'),
   ('HTTP API',
    'Preview and commit routes',
    'POST /api/athletes/import/preview',
    'GET /api/athletes/import/{id}',
    'POST /api/athletes/import/{id}/commit',
    'Immediate POST /api/athletes/import uses the same service'),
   ('Controller',
    'AthleteImportController',
    'preview(), get(), commit()',
    'source-scoped identifier; validated row selection'),
   ('Service',
    'AthleteImportService / AthleteRegistrationParser',
    'Header mapping; NEW / UPDATE / UNCHANGED classification',
    'Valid DOB required; race seconds / field metres',
    'ImportRowWriter commits one selected row atomically',
    'AthleteImportMatcher compares supplied profile and values'),
   ('Repository',
    'ImportJobRepository / ImportRowRepository',
    'Persist original file + row outcomes; lock row on commit',
    'Different DOB: new person; matching name/DOB requires ID'),
   ('Entity / Database',
    'ImportJob + ImportRow + Athlete + EventResult',
    'Rows: NEW, UPDATE, UNCHANGED; IMPORTED / UPDATED',
    'Raw historical values retained; unknown format UNPARSED',
    'No meet context invented for roster performance columns'),
   ('Response',
    'ImportPreviewResponse + ImportCommitResponse',
    'Rows include parsed data, status and explanation',
    'Commit returns imported, updated, skipped, failed counts')]),
 ('07-athlete-api',
  'Existing Athlete API',
  [('Frontend',
    'AthleteDirectoryPage / AthleteProfilePage',
    'React Router: / and /athletes/:athleteId',
    'services/athleteService.ts fetches athlete API'),
   ('HTTP API',
    'Athlete routes',
    'GET, POST /api/athletes',
    'GET, PUT, DELETE /api/athletes/{id}',
    'PUT response retains results and participation'),
   ('Controller',
    'AthleteController',
    'search(), profile(), create(), update(), delete()',
    'Delegates profile assembly and writes to service'),
   ('Service',
    'AthleteService',
    'profile(), createProfile(), updateProfile()',
    'Load athlete, central results and participation together',
    'Delete related notes/results/assignments before athlete'),
   ('Repository',
    'AthleteRepository + related repositories',
    'search(), findById(), save(), delete()',
    'EventResultRepository / EventParticipationRepository'),
   ('Entity / Database',
    'Athlete + participation + results',
    'gender, dateOfBirth, flyStatus, flyaStatus',
    'Source-scoped external ID; historical events retained',
    'Known status spellings canonicalized; missing status empty'),
   ('Response',
    'AthleteSummaryResponse / AthleteProfileResponse',
    'Summary exposes all requested demographic/status fields',
    'Profile includes results[] in events + participations[]',
    'Historical records remain visible with nullable context')])]

# Target designs from the three supplied Sprint 4 use cases.
SPRINT4_PAGES = [{'slug': '14-competition-programs',
  'title': 'Competition Programs: Draft, Publish and Athlete Schedule',
  'actors': 'Admin / Coach: edit and publish; nominated Athlete: published views only',
  'frontend': ('EXTEND',
               'MeetDetailsPage / ProgramTab',
               ['[E] MeetDetailsPage + existing Events workflow',
                '[P] ProgramTab, DayEditor, SessionEditor',
                'General / daily instructions; Preview; Publish',
                '+ Add Event preserves meet and session form; return or cancel without losing '
                'input',
                'Autosave status: saving / saved / failed + retry',
                'Confirm day/session deletion; Cancel preserves content',
                'Athlete: Full Program / My Schedule, read-only']),
  'api': ('P',
          'Program routes / target contracts',
          ['ADMIN / COACH: base = /api/meets/{id}/program',
           'GET, PUT {base}/draft (autosave draft + expected revision)',
           'POST {base}/publish (publish or republish saved revision)',
           'PUT draft owns nested day/session create, update and delete',
           'ATHLETE: GET /api/portal/meets/{id}/program',
           'ATHLETE: GET /api/portal/meets/{id}/schedule',
           '[E] POST /api/events via existing Events workflow',
           '400 validation; 403 ownership; 409 stale draft; 404 unpublished']),
  'scope': ('NOTE',
            'Proposed design / existing integration',
            ['Program classes and routes are new.',
             'Reuse Meet, Event, EventCategory and EventParticipation.',
             'Nomination uses the existing active AvailabilityRequest / assignment model.',
             'No separate qualification-confirmation workflow in Sprint 4.',
             'Publish emits a durable notification trigger (notification design).']),
  'controller': ('EXTEND',
                 'ProgramController / PortalProgramController',
                 ['[P] Read draft; validate and save aggregate; publish',
                  '[E] AthleteSessionService resolves linked athlete',
                  '[P] Portal checks nominated meet before either read',
                  '[EXTEND] SecurityConfig: program staff rules before generic /api/** matchers',
                  '[E] EventController / EventService create events and prevent duplicate '
                  'meet/category events',
                  '[P] Validate every day/session ID belongs to this draft']),
  'service': ('P',
              'ProgramService / PublishedScheduleService',
              ['saveDraft(): transaction + optimistic revision check; never overwrite published '
               'snapshot',
               'Validate dates inside Meet.startDate..endDate; event belongs to selected meet; '
               'category matches event',
               'Require event, round and location; each report/start time is a value OR explicit '
               'TBC',
               'Conditional flag for qualification-dependent rounds',
               'publish(): require >= 1 day with >= 1 valid session; atomically store immutable '
               'snapshot + publication event',
               'mySchedule(): published sessions joined to own assignments; preserve TBC and '
               'Conditional labels']),
  'response': ('P',
               'DraftProgramDto / PublishedProgramDto',
               ['Draft: revision, instructions, days[], sessions[]',
                'Save acknowledgment returned only after commit',
                'Preview uses the current draft after a successful save',
                'Published: version, publishedAt, days[], sessions[]',
                'MyScheduleDto filters by authenticated athlete',
                'No draft fields appear in portal responses',
                'Failed autosave keeps local edits and shows retry; publishing waits for a '
                'successful save']),
  'repository': ('EXTEND',
                 'ProgramRepository / ProgramVersionRepository',
                 ['[P] Lock/check draft revision; persist nested days and sessions atomically',
                  '[P] Day removal cascades only its draft content',
                  '[P] Publish snapshot and pointer in one transaction',
                  '[E] MeetRepository / EventRepository validate references',
                  '[E] EventParticipationRepository + AvailabilityRequestRepository restrict '
                  'athlete reads',
                  '[P] Publication event outbox saved with snapshot']),
  'data': ('EXTEND',
           'Program data / version isolation',
           ['[P] ProgramDraft: meet_id UNIQUE, revision, instructions',
            '[P] ProgramDay: draft_id FK, date, instructions, displayOrder',
            '[P] ProgramSession: day_id FK, event_id FK, round, location, conditional',
            'reportTime / startTime nullable with separate TBC flags',
            '[P] ProgramVersion: meet_id, version, immutable snapshot, publishedAt',
            'UNIQUE(meet_id, version); current published version pointer',
            'Published snapshot freezes days, instructions, event/category labels and sessions',
            '[E] Meet 1 -> many Event; Event -> EventCategory',
            '[E] EventParticipation links Event to Athlete',
            '[E] AvailabilityRequest identifies nominated athlete/meet',
            'Snapshot retained across later draft edits or draft day deletion',
            'Version-qualified session IDs support links from published schedules']),
  'paths': ['SUNNY: Meet -> Program -> instructions -> add valid day -> event/category/round -> '
            'times or TBC + location -> save draft -> Preview -> Publish -> athlete views Full '
            'Program / My Schedule.',
            'EVENT SHORTCUT: missing event -> + Add Event -> existing create workflow -> return '
            'and refresh options; cancellation returns without creating an event or clearing the '
            'session form.',
            'VALIDATION: reject out-of-range date, missing event/round/location, mismatched '
            'event/category, and Publish with no session; surface errors so staff can correct '
            'input.',
            'DELETION: confirm removes day + its instructions/sessions from draft; cancel '
            'preserves all content. Confirm session deletion separately.',
            'ISOLATION / FAILURE: edits after Publish remain draft until Republish; athletes '
            'cannot edit or read drafts. Failed saves show unsaved state and retry; stale '
            'revisions cannot silently overwrite newer changes.']},
 {'slug': '15-in-app-notifications',
  'title': 'In-App Notifications and Scheduled Reminders',
  'actors': 'Admin / Coach / Athlete: receive and manage their own notifications',
  'frontend': ('EXTEND',
               'Header / NotificationBell / NotificationsPage',
               ['[E] Shared header.tsx + authenticated apiFetch',
                '[P] Bell: unread count + five most recent items',
                'View All -> paginated history, All / Unread',
                'Open marks read; mark read/unread; mark all read',
                'Delete immediately; Undo visible for 5 seconds',
                'Undo restores previous read state; expiry keeps deletion',
                'Empty/error states; accessible desktop/mobile menu']),
  'api': ('P',
          'Recipient-owned notification routes',
          ['GET /api/notifications?filter=ALL|UNREAD&limit=5|...&cursor=...',
           'GET /api/notifications/unread-count',
           'PATCH /api/notifications/{id} {read: true|false}',
           'POST /api/notifications/read-all',
           'DELETE /api/notifications/{id} -> {undoUntil}',
           'POST /api/notifications/{id}/restore (before server deadline)',
           'All roles: derive recipient from session, never request user ID',
           'Reauthorize destination API when opening any notification']),
  'scope': ('NOTE',
            'Scheduling decisions / integration gaps',
            ['All notification components are proposed.',
             'Extend nomination, assignment, publication and cancellation workflows with durable '
             'triggers.',
             'Meet cancellation state is new; current delete API is not a cancellation workflow.',
             'Local time requires a configured IANA zone; day-before send time is configurable, '
             'unspecified by use case.']),
  'controller': ('EXTEND',
                 'NotificationController / SecurityConfig',
                 ['[P] Require authenticated account for every route',
                  '[P] Query/mutate only principal.id-owned notifications',
                  '[P] Hide deleted items; validate restore deadline',
                  '[EXTEND] Explicit all-role /api/notifications rules must precede generic '
                  'staff-only API rules',
                  '[P] Safe destination resolver; inaccessible target -> message and accessible '
                  'fallback page',
                  '[P] Count/list/read/delete errors show retry feedback']),
  'service': ('P',
              'NotificationService / ReminderScheduler',
              ['RecipientPolicy: staff alert -> every Admin and Coach; athlete alert -> affected '
               'athletes only',
               'Domain triggers: nomination, publish/republish, assignment changes and meet '
               'cancellation',
               'Pending reminder: local 09:00 on meet.startDate minus 2 calendar days; only '
               'still-Pending responses',
               'Participation reminder: group own confirmed published sessions per participation '
               'date; send day before',
               'Exclude unconfirmed Conditional rounds; skip cancelled meets; recheck eligibility '
               'before delivery',
               'Persist event/reminder keys atomically so retries cannot duplicate recipient '
               'notifications']),
  'response': ('P',
               'NotificationDto / UnreadCountDto',
               ['id, type, title, message, createdAt, readAt',
                'Typed destination: meet/program/availability/schedule IDs',
                'List cursor + unreadCount; deleted rows excluded',
                'Opening an already-read item stays read',
                'Delete hides item and updates count immediately',
                'Restore returns original read/unread state',
                'UI refreshes bell/list/count after mutations; failed mutations reconcile to '
                'server state']),
  'repository': ('EXTEND',
                 'NotificationRepository / TriggerOutboxRepository',
                 ['[P] Recipient + newest-first / unread indexes',
                  '[P] Atomic soft delete sets server undoUntil = now + 5s; restore only within '
                  'that deadline',
                  '[P] Hidden expired deletions may be purged later',
                  '[P] Retry durable triggers after transient failures',
                  '[E] StaffUserRepository for role recipients; AvailabilityRequestRepository for '
                  'current Pending state',
                  '[P] Published schedule + assignment queries; cancellation lock/recheck before '
                  'reminder commit']),
  'data': ('EXTEND',
           'Recipient data / durable deduplication',
           ['[P] Notification: id, recipient_user_id FK, type, payload, destination, createdAt, '
            'readAt',
            'deletedAt + undoUntil; deletion preserves readAt',
            '[P] Delivery key UNIQUE: recipient + domainEventId',
            '[P] ReminderDelivery UNIQUE: type + athlete_id + meet_id + participationDateKey',
            'Pending key uses meet ID, independent of assignment revision; once per athlete/meet',
            'Participation key uses local participation date; once per athlete/meet/day',
            '[P] TriggerOutbox: immutable event ID + payload + retry status, persisted with source '
            'action',
            '[EXTEND] Meet.cancelledAt / status for scheduler exclusion',
            '[E] StaffUser (all roles), AvailabilityRequest, EventParticipation',
            '[P] ProgramVersion provides published sessions; Conditional alone is not '
            'confirmation']),
  'paths': ['SUNNY: source action commits + outbox event -> resolve recipients -> persist '
            'notifications -> refresh bell -> open one -> mark read + authorized navigation -> '
            'View All -> filter/manage history.',
            'REMINDERS: Pending at 09:00 local two calendar days before start -> one reminder; '
            'Available/Unavailable or already-sent -> skip. Confirmed events on one participation '
            'date -> one consolidated day-before reminder.',
            'CONDITIONAL / CANCELLATION: exclude unconfirmed Conditional sessions; introduce no '
            'qualification workflow. Cancellation notifies affected athletes + staff and '
            'suppresses future reminders.',
            'UNDO: delete removes immediately -> restore within 5 seconds returns prior read '
            'state; expired restore rejected. No notifications / no unread -> appropriate empty '
            'state.',
            'FAILURE / PRIVACY: retry durable triggers without duplicates; mutation failures '
            'reconcile UI; unavailable destination shows a message. Every recipient query and '
            'destination read enforces current authorization.']},
 {'slug': '16-role-based-dashboards',
  'title': 'Role-Based Dashboards and Shared Navigation',
  'actors': 'Admin: organization overview; Coach: all meets; Athlete: own nominated meets',
  'frontend': ('EXTEND',
               'DashboardPage / role-specific sections',
               ['[E] AuthProvider, RequireSession, header.tsx',
                '[P] Post-login role dashboard + reusable cards',
                'Upcoming meets, outstanding actions, recent notifications',
                '[P] Role-permitted desktop nav / mobile hamburger',
                'Orange/white, Inter, desktop/tablet/mobile layout',
                'Each section: loading, empty, error + retry',
                'Existing feature links perform authorized actions']),
  'api': ('P',
          'Role-specific dashboard query contracts',
          ['ADMIN: GET /api/dashboard/admin',
           'COACH: GET /api/dashboard/coach',
           'ATHLETE: GET /api/portal/dashboard',
           '[P] GET /api/notifications?filter=ALL&limit=5 (UC-14)',
           '[E] GET /api/auth/me -> role + permitted actions',
           'Server chooses data scope from principal; no athleteId parameter',
           'Independent summary/notification requests allow partial rendering',
           '401 no session; 403 wrong role; section error remains retryable']),
  'scope': ('NOTE',
            'Proposed dashboard / reused features',
            ['Dashboard classes and summary routes are new.',
             'Reuse existing meet, athlete, assignment and availability sources.',
             'Programs and UC-14 notifications depend on the companion proposed designs.',
             'Coach sees all meets; quick actions retain existing feature permissions.',
             'Admin-only links: Add Athlete, Create Meet, Manage Users. Import uses current '
             'permission.']),
  'controller': ('EXTEND',
                 'DashboardController / PortalDashboardController',
                 ['[P] Admin/Coach route guards + dashboard query service',
                  '[E] AthleteSessionService.require(identity) resolves athlete',
                  '[EXTEND] SecurityConfig: role summary routes before generic staff API rules',
                  '[E] RequireSession and auth permissions guard feature routes',
                  '[P] Destination endpoints enforce role/ownership again',
                  '[P] Return section availability/error metadata where one data source fails']),
  'service': ('P',
              'DashboardService / AthleteDashboardService',
              ['Admin: total athletes, upcoming meets, Pending responses and unpublished program '
               'drafts',
               'Coach: all upcoming meets, nominated athlete counts, Pending responses and '
               'permitted outstanding actions',
               'Athlete: only nominated meets, own availability and next scheduled event in '
               'published programs',
               'Pending availability -> action links; none -> All Caught Up',
               'Next event from own assigned published sessions; TBC has no invented ordering '
               'time; keep Conditional label',
               'Recent notifications fetched through recipient-owned UC-14 API; dashboard performs '
               'no duplicate notification writes']),
  'response': ('P',
               'Admin / Coach / AthleteDashboardDto',
               ['Role-scoped stats, upcomingMeets[], outstandingActions[]',
                'Athlete: own availability + nextScheduledEvent or empty state',
                'Action links match current authorization; Manage Users hidden from Coach',
                'No nominations / no upcoming schedule -> explicit empty states',
                'No outstanding staff actions / notifications -> empty state',
                'Section failures show retry while other loaded sections remain usable',
                'Unauthorized destination -> access denied + authorized return']),
  'repository': ('EXTEND',
                 'DashboardQueries / existing repositories',
                 ['[P] Aggregate queries with shared upcoming/Pending definitions; count distinct '
                  'nominated athletes',
                  '[E] AthleteRepository, MeetRepository / MeetOverviewRepository',
                  '[E] AvailabilityRequest.status() semantics include assignment revisions; do not '
                  'count raw lastAnswer',
                  '[E] EventParticipationRepository scopes schedule to own assignments',
                  '[P] ProgramVersion query excludes drafts; owner filtering in database queries',
                  '[P] No coach-specific meet ownership restriction']),
  'data': ('EXTEND',
           'Read model / existing authoritative records',
           ['[E] Athlete: organization athlete count',
            '[E] Meet: start/end dates; all-meet staff overview',
            '[E] EventParticipation: athlete/event assignments',
            '[E] AvailabilityRequest: meet/athlete nomination + computed '
            'Pending/Available/Unavailable state',
            '[E] StaffUser: role + linked athlete ID',
            '[P] ProgramDraft vs ProgramVersion for unpublished count and published personal '
            'schedule',
            'Unpublished count includes saved changes newer than the published version',
            '[P] UC-14 Notification data stays behind notification API; recipient scope always '
            'applies',
            'No new dashboard table required; read-only aggregation over authoritative sources',
            "Return only authenticated athlete's records; never accept another athlete ID for "
            'portal scope']),
  'paths': ['SUNNY: login / dashboard route -> session role -> role summary + own notifications -> '
            'cards / meets / outstanding actions -> existing authorized feature. Desktop top nav '
            'or mobile hamburger uses the same permissions.',
            'ADMIN / COACH: Admin receives organization statistics and Admin quick actions; Coach '
            'monitors all meets and can assign events, record results, edit programs and review '
            'availability without Admin-only actions.',
            'ATHLETE: nominated meets + own availability + next published scheduled event. No '
            'Pending responses -> All Caught Up; no nominations/schedule -> clear empty state; My '
            'Profile and notifications remain accessible.',
            'EMPTY / FAILURE: no upcoming meets, no staff actions or no recent notifications -> '
            'section-specific empty state. Failed section -> unavailable message + retry; other '
            'accessible sections stay usable.',
            'AUTHORIZATION: wrong-role feature request -> server denies; unrelated athlete records '
            '-> denied using linked athlete identity. Role navigation and responsive layouts '
            'preserve the same access rules on every device.']}]

DESIGN_COLORS = {
    'E': ('#edf8f0', '#34734b'),
    'P': ('#f3f0ff', '#7050a3'),
    'EXTEND': ('#fff8e8', '#966719'),
    'NOTE': ('#f7f9fc', '#53617b'),
}


def design_lines(lines, width):
    """Wrap conservatively so proportional-font text fits both export formats."""
    return [part for line in lines for part in textwrap.wrap(
        line, width=max(20, int((width - 40) / 8.8)),
        break_long_words=False, break_on_hyphens=False,
    )]


def design_height(spec, width):
    _, title, lines = spec
    return 80 + len(design_lines([title], width)) * 25 + len(design_lines(lines, width)) * 22


def append_design_pages(mxfile):
    """Build the new pages from source definitions, without reading generated files."""
    for page in SPRINT4_PAGES:
        svg_root = ET.Element('svg', xmlns='http://www.w3.org/2000/svg', role='img')
        ET.SubElement(svg_root, 'title').text = page['title'] + ' — code design'
        ET.SubElement(svg_root, 'desc').text = page['actors'] + '. ' + ' '.join(page['paths'])
        defs = ET.SubElement(svg_root, 'defs')
        marker = ET.SubElement(defs, 'marker', id='arrow', markerWidth='10', markerHeight='10',
                               refX='9', refY='3', orient='auto')
        ET.SubElement(marker, 'path', d='M0,0 L9,3 L0,6', fill='none', stroke='#53617b')
        background = ET.SubElement(svg_root, 'rect', width='1900', fill='white')
        diagram = ET.SubElement(mxfile, 'diagram', id=page['slug'], name=page['title'])
        model = ET.SubElement(diagram, 'mxGraphModel', grid='1', gridSize='10', page='1',
                              pageWidth='1900')
        graph = ET.SubElement(model, 'root')
        ET.SubElement(graph, 'mxCell', id='0')
        ET.SubElement(graph, 'mxCell', id='1', parent='0')

        def label(x, y, value, size=16, color='#26334a', bold=False):
            ET.SubElement(svg_root, 'text', x=str(x), y=str(y), fill=color,
                          **{'font-family': 'Arial, Helvetica, sans-serif', 'font-size': str(size),
                             'font-weight': '700' if bold else '400'}).text = value

        def design_box(key, x, y, w, h, status, title, lines):
            fill, stroke = DESIGN_COLORS[status]
            body = design_lines(lines, w)
            headings = design_lines([title], w)
            required = 62 + len(headings) * 25 + len(body) * 22
            if required > h:
                raise ValueError(f"{page['slug']}/{key}: text needs {required}px, box has {h}px")
            ET.SubElement(svg_root, 'rect', x=str(x), y=str(y), width=str(w), height=str(h),
                          rx='8', fill=fill, stroke=stroke)
            label(x + 18, y + 25, key.upper().replace('-', ' ') + ' / ' + status,
                  size=12, color=stroke, bold=True)
            for i, line in enumerate(headings):
                label(x + 18, y + 54 + i * 25, line, size=18, bold=True)
            for i, line in enumerate(body):
                label(x + 18, y + 64 + len(headings) * 25 + i * 22, line)
            value = '\n'.join([key.upper() + ' / ' + status, title, *lines])
            style = ('rounded=1;whiteSpace=wrap;html=0;align=left;verticalAlign=top;'
                     f'spacing=18;fontSize=16;fontFamily=Arial;fillColor={fill};strokeColor={stroke};')
            cell = ET.SubElement(graph, 'mxCell', id=key, value=value, vertex='1', parent='1',
                                 style=style)
            ET.SubElement(cell, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h),
                          **{'as': 'geometry'})

        def design_edge(source, target, points, dashed=False):
            style = 'edgeStyle=orthogonalEdgeStyle;rounded=0;endArrow=open;html=0;'
            if dashed:
                style += 'dashed=1;'
            cell = ET.SubElement(graph, 'mxCell', id=f'{source}-to-{target}', edge='1', parent='1',
                                 source=source, target=target, style=style)
            geometry = ET.SubElement(cell, 'mxGeometry', relative='1', **{'as': 'geometry'})
            if len(points) > 2:
                waypoints = ET.SubElement(geometry, 'Array', **{'as': 'points'})
                for x, y in points[1:-1]:
                    ET.SubElement(waypoints, 'mxPoint', x=str(x), y=str(y))
            attrs = {'points': ' '.join(f'{x},{y}' for x, y in points), 'fill': 'none',
                     'stroke': '#53617b', 'stroke-width': '2', 'marker-end': 'url(#arrow)'}
            if dashed:
                attrs['stroke-dasharray'] = '7 5'
            ET.SubElement(svg_root, 'polyline', attrs)

        design_box('title', 32, 24, 1836, 130, 'NOTE', page['title'], [page['actors']])
        design_box('legend', 32, 168, 1836, 130, 'NOTE', 'Sprint 4 target code design', [
            'E = existing source | P = proposed component/contract | EXTEND = existing code + planned changes | solid = request/data flow | dashed = response',
        ])
        top = 330
        top_height = max(design_height(page[key], w) for key, w in
                         [('frontend', 450), ('api', 820), ('scope', 502)])
        for key, x, w in [('frontend', 32, 450), ('api', 514, 820), ('scope', 1366, 502)]:
            design_box(key, x, top, w, top_height, *page[key])
        middle = top + top_height + 64
        mid_height = max(design_height(page[key], w) for key, w in
                         [('controller', 550), ('service', 714)])
        lower = middle + mid_height + 48
        low_height = max(design_height(page[key], w) for key, w in
                         [('response', 550), ('repository', 714)])
        data_height = max(mid_height + 48 + low_height, design_height(page['data'], 502))
        for key, x, w in [('controller', 32, 550), ('service', 620, 714)]:
            design_box(key, x, middle, w, mid_height, *page[key])
        for key, x, w in [('response', 32, 550), ('repository', 620, 714)]:
            design_box(key, x, lower, w, low_height, *page[key])
        design_box('data', 1366, middle, 502, data_height, *page['data'])
        design_edge('frontend', 'api', [(482, top + 100), (514, top + 100)])
        design_edge('api', 'controller', [(924, top + top_height), (924, middle - 32),
                                         (307, middle - 32), (307, middle)])
        design_edge('controller', 'service', [(582, middle + 110), (620, middle + 110)])
        design_edge('service', 'repository', [(977, middle + mid_height), (977, lower)])
        design_edge('repository', 'data', [(1334, lower + 120), (1366, lower + 120)])
        design_edge('controller', 'response', [(307, middle + mid_height), (307, lower)], dashed=True)
        design_edge('response', 'frontend', [(32, lower + 100), (14, lower + 100),
                                           (14, top + 150), (32, top + 150)], dashed=True)
        paths_y = middle + data_height + 40
        paths_spec = ('NOTE', 'Sunny-day and rainy-day paths', page['paths'])
        paths_height = design_height(paths_spec, 1836)
        design_box('paths', 32, paths_y, 1836, paths_height, *paths_spec)
        height = str(paths_y + paths_height + 32)
        svg_root.set('width', '1900')
        svg_root.set('height', height)
        svg_root.set('viewBox', f'0 0 1900 {height}')
        model.set('pageHeight', height)
        background.set('height', height)
        ET.indent(svg_root)
        ET.ElementTree(svg_root).write(ROOT / (page['slug'] + '.svg'), encoding='utf-8',
                                      xml_declaration=True)


mx = ET.Element('mxfile', host='app.diagrams.net')
for slug, title, rows in PAGES:
    diagram = ET.SubElement(mx, 'diagram', id=slug, name=title)
    model = ET.SubElement(diagram, 'mxGraphModel', dx='1600', dy='1000', grid='1', gridSize='10', page='1', pageWidth='1600', pageHeight='820')
    root = ET.SubElement(model, 'root')
    ET.SubElement(root, 'mxCell', id='0')
    ET.SubElement(root, 'mxCell', id='1', parent='0')
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="820" viewBox="0 0 1600 820">', '<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L9,3 L0,6" fill="none" stroke="#444"/></marker></defs>', '<rect width="1600" height="820" fill="white"/>']
    def box(key, x, y, w, h, lines, fill='#ffffff', dashed=False):
        style = f'rounded=0;whiteSpace=wrap;html=0;align=left;verticalAlign=top;spacing=12;fontSize=14;fillColor={fill};strokeColor=#555555;' + ('dashed=1;' if dashed else '')
        cell = ET.SubElement(root, 'mxCell', id=key, value='\n'.join(lines), style=style, vertex='1', parent='1')
        ET.SubElement(cell, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h), **{'as':'geometry'})
        svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}" stroke="#555"' + (' stroke-dasharray="7 5"' if dashed else '') + '/>')
        for i, line in enumerate(lines):
            svg.append(f'<text x="{x+14}" y="{y+24+i*21}" font-family="Arial,sans-serif" font-size="{16 if i==0 else 14}" font-weight="{"bold" if i==0 else "normal"}">{escape(line)}</text>')
    def edge(key, src, dst, points, label='', dashed=False):
        cell = ET.SubElement(root, 'mxCell', id=key, value=label, style='edgeStyle=orthogonalEdgeStyle;rounded=0;endArrow=open;html=0;' + ('dashed=1;' if dashed else ''), edge='1', parent='1', source=src, target=dst)
        ET.SubElement(cell, 'mxGeometry', relative='1', **{'as':'geometry'})
        svg.append(f'<polyline points="{points}" fill="none" stroke="#444" marker-end="url(#arrow)"' + (' stroke-dasharray="5 5"' if dashed else '') + '/>')
    box('title', 30, 20, 1540, 55, [title + ' — code flow', 'Frontend / HTTP API / Spring Boot / JPA / MySQL'], '#f5f5f5')
    box('front-layer', 30, 100, 390, 230, ['Frontend — React + TypeScript'], '#f8fafc')
    box('api-layer', 460, 100, 650, 230, ['HTTP API'], '#f8fafc')
    box('back-layer', 30, 370, 1080, 430, ['Backend — Java + Spring Boot / JPA'], '#f8fafc')
    box('db-layer', 1150, 370, 420, 430, ['Database — MySQL 8.4'], '#f8fafc')
    positions = [(50,145,350,160),(480,145,610,160),(50,420,510,150),(590,420,500,150),(590,625,500,150),(1170,420,380,210),(50,625,510,150)]
    for i, (row, pos) in enumerate(zip(rows, positions)):
        # Wrap long text for both rendered SVG and editable cells.
        import textwrap
        lines = [row[0] + ': ' + row[1]]
        for line in row[2:]:
            lines.extend(textwrap.wrap(line, width=int((pos[2]-28)/7.2)))
        # Keep title readable by wrapping it as well.
        lines = sum((textwrap.wrap(line, width=int((pos[2]-28)/7.2)) for line in lines), [])
        box('n'+str(i), *pos, lines)
    edge('e1','n0','n1','400,220 480,220')
    edge('e2','n1','n2','785,305 785,350 305,350 305,420')
    edge('e3','n2','n3','560,490 590,490')
    edge('e4','n3','n4','840,570 840,625')
    edge('e5','n4','n5','1090,700 1130,700 1130,525 1170,525')
    edge('e6','n2','n6','305,570 305,625', dashed=True)
    edge('e7','n6','n1','50,700 20,700 20,345 1125,345 1125,220 1090,220', dashed=True)
    svg.append('</svg>')
    (ROOT / (slug+'.svg')).write_text('\n'.join(svg))
append_design_pages(mx)
ET.indent(mx)
ET.ElementTree(mx).write(ROOT / 'use-case-code-diagrams.drawio', encoding='utf-8', xml_declaration=True)
print(f'Generated {len(PAGES) + len(SPRINT4_PAGES)} draw.io pages and SVGs.')

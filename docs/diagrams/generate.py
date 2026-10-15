"""Generate editable draw.io pages and standalone SVG code-flow diagrams. No dependencies."""
from pathlib import Path
from html import escape
import xml.etree.ElementTree as ET

ROOT = Path(__file__).parent
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
    'ImportRowWriter commits one selected row atomically',
    'AthleteImportMatcher compares supplied profile and values'),
   ('Repository',
    'ImportJobRepository / ImportRowRepository',
    'Persist original file + row outcomes; lock row on commit',
    'Match external ID or unique name; preserve missing fields'),
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
ET.indent(mx)
ET.ElementTree(mx).write(ROOT / 'use-case-code-diagrams.drawio', encoding='utf-8', xml_declaration=True)
print(f'Generated {len(PAGES)} draw.io pages and SVGs.')

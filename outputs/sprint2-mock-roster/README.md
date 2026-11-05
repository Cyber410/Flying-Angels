# Fictional base roster

The accompanying workbook contains 125 invented athlete identities with complete
first names, last names, and dates of birth. No athlete records were copied from
the original workbook. Any resemblance to a real person is incidental.

The first worksheet is the import data: one header row followed by 125 athletes,
using the original first seven columns and event-column names in the order the
current backend importer expects. Birth dates are real Excel dates displayed as
YYYY-MM-DD. Optional gender, roster status, and note fields vary intentionally.

There are 23 athletes without seed marks, 54 with one, 37 with two, and 11 with
three (161 marks total, 66 more than the first version). All 125 first names and
all 125 surnames are distinct. Blank marks mean no recorded baseline, not a zero performance. Track marks
are numeric seconds; jumps and throws are numeric metres, as identified by their
column heading. These are fictional historical baselines, without meet identity,
event date, or competition participation. They must not create past meets or
meet-event instances. New incoming competitions use the existing Meets workflow.

The backend may create event-category records for these marks; a category such as
100M is a discipline label, not an actual meet event. Its import path already
stores these historical performances with a null event association.

The revised workbook replaced the local base roster on 2026-11-02 with explicit
user approval and without a backup, as requested. The previous athletes and their
associated results were removed before import. The 1 meet and 4 event definitions
were preserved unchanged. Verification found 125 athletes with complete names and
birth dates, 161 seed results with numeric scores, and no seed results linked to
meet events. The API confirmed the same athlete, meet, and event counts.

The import API adds records rather than replacing the roster. Future replacements
need an explicit deletion scope; importing another workbook by itself will not
remove the current athletes. Browser-only assignments, attempts, and notes are
separate from this database replacement. Athlete IDs were not reset or reused.

The original source workbook remains untouched. No backend code changes are
needed to read this workbook.

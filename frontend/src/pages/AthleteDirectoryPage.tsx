import { useOptionalAuth } from "../auth/authContext";
// Searchable athlete table with creation, pagination, and bulk archive/restore.
import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Header from "../components/header";
import MeetDialog from "../components/MeetDialog";
import type { Athlete } from "../types/athlete";
import {
  createAthlete,
  deleteAthlete,
  getAthleteById,
  getAthletes,
  setAthletesArchived,
} from "../services/athleteService";
import "./AthleteDirectoryPage.css";

type NewEvent = {
  eventName: string;
};

const EVENT_OPTIONS = [
  "60M",
  "100M",
  "200M",
  "400M",
  "800M",
  "1500M",
  "60M HURDLES",
  "80M HURDLES",
  "100M HURDLES",
  "110M HURDLES",
  "LONG JUMP",
  "HIGH JUMP",
  "SHOT PUT",
];



// Calculate whole-year age from the supplied birth date.
function calculateAge(dateOfBirth: string) {
  const today = new Date();
  const birthDate = new Date(`${dateOfBirth}T00:00:00`);

  let age =
    today.getFullYear() -
    birthDate.getFullYear();

  const monthDifference =
    today.getMonth() -
    birthDate.getMonth();

  if (
    monthDifference < 0 ||
    (monthDifference === 0 &&
      today.getDate() < birthDate.getDate())
  ) {
    age--;
  }

  return age;
}

// Convert an athlete age into the matching directory age-group label.
function getAgeGroup(
  dateOfBirth: string | null
) {
  if (!dateOfBirth) {
    return "";
  }

  const age = calculateAge(dateOfBirth);

  if (age >= 5 && age <= 14) {
    return "5-14";
  }

  if (age >= 15 && age <= 18) {
    return "15-18";
  }

  if (age > 18) {
    return "18+";
  }

  return "";
}

// Manage the searchable athlete directory, creation, and bulk archive actions.
function AthleteDirectoryPage() {
  const auth = useOptionalAuth();
  const canManage = auth ? auth.can("MANAGE_ATHLETES") : true;
  const navigate = useNavigate();

  const [athletes, setAthletes] =
    useState<Athlete[]>([]);

  const [isLoading, setIsLoading] =
    useState(true);

  const [loadError, setLoadError] =
    useState("");

  const [searchTerm, setSearchTerm] =
    useState("");

  const [pageSize, setPageSize] = useState(10);
  const [genderFilter, setGenderFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<Athlete | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");

  // Selection survives pagination and filtering; switching archive views clears it.
  const [archiveView, setArchiveView] = useState<"active" | "archived">("active");
  const [selectedAthleteIds, setSelectedAthleteIds] = useState<number[]>([]);
  const [archiveDialogOpen, setArchiveDialogOpen] = useState(false);
  const [isArchiving, setIsArchiving] = useState(false);
  const [archiveError, setArchiveError] = useState("");
  const [archiveFeedback, setArchiveFeedback] = useState("");
  const selectPageRef = useRef<HTMLInputElement>(null);

  const [currentPage, setCurrentPage] =
    useState(1);

  /* ==================================================
     FILTERS
     ================================================== */



  const [ageDropdownOpen, setAgeDropdownOpen] =
    useState(false);

  const [
    selectedAgeGroups,
    setSelectedAgeGroups,
  ] = useState<string[]>([]);

  const [
    eventFilterDropdownOpen,
    setEventFilterDropdownOpen,
  ] = useState(false);

  const [
    selectedEventFilters,
    setSelectedEventFilters,
  ] = useState<string[]>([]);

  const [
    athleteEventMap,
    setAthleteEventMap,
  ] = useState<Record<number, string[]>>({});

  const [
    eventsLoaded,
    setEventsLoaded,
  ] = useState(false);

  const [
    eventsLoading,
    setEventsLoading,
  ] = useState(false);

  /* ==================================================
     ADD ATHLETE
     ================================================== */

  const [addModalOpen, setAddModalOpen] =
    useState(false);

  const [newFirstName, setNewFirstName] =
    useState("");

  const [newLastName, setNewLastName] =
    useState("");

  const [
    newDateOfBirth,
    setNewDateOfBirth,
  ] = useState("");

  const [newGender, setNewGender] =
    useState("");

  const [
    newFlyStatus,
    setNewFlyStatus,
  ] = useState("");

  const [
    newFlyaStatus,
    setNewFlyaStatus,
  ] = useState("");

  const [newNote, setNewNote] =
    useState("");

  const [newEvents, setNewEvents] =
    useState<NewEvent[]>([]);

  const [
    eventDropdownOpen,
    setEventDropdownOpen,
  ] = useState(false);

  const [isAdding, setIsAdding] =
    useState(false);

  const [addError, setAddError] =
    useState("");

  /* ==================================================
     DATE OF BIRTH OPTIONS
     ================================================== */

  const birthDateParts = newDateOfBirth
    ? newDateOfBirth.split("-")
    : ["", "", ""];

  const birthYear =
    birthDateParts[0] ?? "";

  const birthMonth =
    birthDateParts[1] ?? "";

  const birthDay =
    birthDateParts[2] ?? "";

  const today = new Date();
  const currentYear = today.getFullYear();

  const youngestBirthYear =
    currentYear - 5;

  const birthYears = Array.from(
    {
      length:
        youngestBirthYear - 1940 + 1,
    },
    /* Build each generated item from its index. */ (_, index) =>
      youngestBirthYear - index
  );

  const months = [
    { value: "01", label: "January" },
    { value: "02", label: "February" },
    { value: "03", label: "March" },
    { value: "04", label: "April" },
    { value: "05", label: "May" },
    { value: "06", label: "June" },
    { value: "07", label: "July" },
    { value: "08", label: "August" },
    { value: "09", label: "September" },
    { value: "10", label: "October" },
    { value: "11", label: "November" },
    { value: "12", label: "December" },
  ];

  // Combine the selected year, month, and day into the birth-date field.
  const updateBirthDate = (
    year: string,
    month: string,
    day: string
  ) => {
    if (!year && !month && !day) {
      setNewDateOfBirth("");
      return;
    }

    setNewDateOfBirth(
      `${year}-${month}-${day}`
    );
  };

  // Check that the athlete has reached their fifth birthday.
  const isAtLeastFiveYearsOld = (
    dateOfBirth: string
  ) => {
    const birthDate = new Date(
      `${dateOfBirth}T00:00:00`
    );

    const fifthBirthday = new Date(
      birthDate.getFullYear() + 5,
      birthDate.getMonth(),
      birthDate.getDate()
    );

    return fifthBirthday <= new Date();
  };

  /* ==================================================
     ADD ATHLETE EVENT FUNCTIONS
     ================================================== */

  // Add or remove a discipline from the new athlete form.
  const toggleEvent = (
    eventName: string
  ) => {
    setNewEvents(/* Derive the next new events value from its current state. */ (currentEvents) => {
      const isSelected =
        currentEvents.some(
          /* Check whether any event matches the required condition. */ (event) =>
            event.eventName === eventName
        );

      if (isSelected) {
        return currentEvents.filter(
          /* Exclude entries that do not belong in this result or selection. */ (event) =>
            event.eventName !== eventName
        );
      }

      return [
        ...currentEvents,
        {
          eventName,
        },
      ];
    });
  };

  // Remove a discipline from the new athlete form.
  const removeEvent = (
    eventName: string
  ) => {
    setNewEvents(/* Derive the next new events value from its current state. */ (currentEvents) =>
      currentEvents.filter(
        /* Exclude entries that do not belong in this result or selection. */ (event) =>
          event.eventName !== eventName
      )
    );
  };

  /* ==================================================
     LOAD ATHLETES
     ================================================== */

  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    // Fetch directory records and update the page loading and error states.
    async function loadAthletes() {
      try {
        setIsLoading(true);
        setLoadError("");

        const data =
          await getAthletes("all");

        setAthletes(data);
      } catch (error) {
        console.error(error);

        setLoadError(
          "Athlete information could not be loaded."
        );
      } finally {
        setIsLoading(false);
      }
    }

    loadAthletes();
  }, []);

  /* ==================================================
     RESET PAGINATION
     ================================================== */



  /* ==================================================
     LOAD EVENTS FOR FILTERING
     The directory API does not include event data.
     We therefore load each athlete's existing profile
     only when the Filters panel is first opened.
     ================================================== */

  const loadAthleteEvents =
    async () => {
      if (
        eventsLoaded ||
        eventsLoading ||
        athletes.length === 0
      ) {
        return;
      }

      try {
        setEventsLoading(true);

        const profiles =
          await Promise.all(
            athletes.map(/* Build profiles from each athlete. */ (athlete) =>
              getAthleteById(athlete.id)
            )
          );

        const eventMap: Record<
          number,
          string[]
        > = {};

        profiles.forEach(/* Apply this operation to each profile. */ (profile) => {
          eventMap[profile.id] = (
            profile.events ?? []
          ).map(/* Trim each value before comparing or saving it. */ (event) =>
            event.categoryName
              .trim()
              .toUpperCase()
          );
        });

        setAthleteEventMap(eventMap);
        setEventsLoaded(true);
      } catch (error) {
        console.error(
          "Unable to load athlete events for filtering.",
          error
        );
      } finally {
        setEventsLoading(false);
      }
    };

  /* ==================================================
     FILTER FUNCTIONS
     ================================================== */

  // Toggle an age-group filter and reset the visible page.
  const toggleAgeGroup = (
  ageGroup: string
) => {
  setSelectedAgeGroups(
    /* Derive the next selected age groups value from its current state. */ (currentGroups) =>
      currentGroups.includes(ageGroup)
        ? currentGroups.filter(
            /* Exclude entries that do not belong in this result or selection. */ (group) =>
              group !== ageGroup
          )
        : [
            ...currentGroups,
            ageGroup,
          ]
  );

  setCurrentPage(1);
};

// Toggle a discipline filter and reset the visible page.
const toggleEventFilter = (
  eventName: string
) => {
  setSelectedEventFilters(
    /* Derive the next selected event filters value from its current state. */ (currentEvents) =>
      currentEvents.includes(eventName)
        ? currentEvents.filter(
            /* Exclude entries that do not belong in this result or selection. */ (event) =>
              event !== eventName
          )
        : [
            ...currentEvents,
            eventName,
          ]
  );

  setCurrentPage(1);
};
// Clear directory filters and return to the first page.
const clearAll = () => {
  setGenderFilter("");
  setStatusFilter("");
  setSearchTerm("");
  setSelectedAgeGroups([]);
  setSelectedEventFilters([]);
  setAgeDropdownOpen(false);
  setEventFilterDropdownOpen(false);
  setCurrentPage(1);
};

  /* ==================================================
     ADD ATHLETE
     ================================================== */

  // Restore the new athlete form to its initial values.
  const resetAddForm = () => {
    setNewFirstName("");
    setNewLastName("");
    setNewDateOfBirth("");
    setNewGender("");
    setNewFlyStatus("");
    setNewFlyaStatus("");
    setNewNote("");
    setNewEvents([]);
    setEventDropdownOpen(false);
    setAddError("");
  };

  // Close the add-athlete dialog and clear its form state.
  const closeAddModal = () => {
    if (isAdding) {
      return;
    }

    setAddModalOpen(false);
    resetAddForm();
  };

  // Validate the new athlete form, save it, and refresh the directory state.
  const handleAddAthlete = async (
    event: React.FormEvent<HTMLFormElement>
  ) => {
    event.preventDefault();

    if (
      !newFirstName.trim() ||
      !newLastName.trim()
    ) {
      return;
    }

    if (
      birthYear &&
      birthMonth &&
      birthDay &&
      !isAtLeastFiveYearsOld(
        newDateOfBirth
      )
    ) {
      setAddError(
        "Athletes must be at least 5 years old."
      );
      return;
    }

    try {
      setIsAdding(true);
      setAddError("");

      const createdAthlete =
        await createAthlete({
          firstName:
            newFirstName.trim(),
          lastName:
            newLastName.trim(),
          dateOfBirth:
            birthYear &&
            birthMonth &&
            birthDay
              ? newDateOfBirth
              : null,
          gender:
            newGender.trim(),
          note:
            newNote.trim(),
          flyStatus:
            newFlyStatus.trim(),
          flyaStatus:
            newFlyaStatus.trim(),
          events: newEvents.map(/* Build created athlete from each event. */ (event) => ({
            eventName: event.eventName,
            result: "",
          })),
        });

      setAthletes(
        /* Derive the next athletes value from its current state. */ (currentAthletes) => [
          ...currentAthletes,
          createdAthlete,
        ]
      );

      /*
       * The event-filter cache was created before
       * this athlete existed, so force it to reload
       * next time the directory filters need it.
       */
      setEventsLoaded(false);

      setAddModalOpen(false);
      resetAddForm();

      navigate(
        `/athletes/${createdAthlete.id}`
      );
    } catch (error) {
      console.error(error);

      setAddError(
        "The athlete could not be added. Please check the information and try again."
      );
    } finally {
      setIsAdding(false);
    }
  };

  /* ==================================================
     FILTER ATHLETES
     ================================================== */

  const filteredAthletes =
    athletes.filter(/* Keep the records included in the current selection. */ (athlete) => {
      const fullName =
        `${athlete.firstName} ${athlete.lastName}`.toLowerCase();

      const matchesSearch =
        fullName.includes(
          searchTerm
            .trim()
            .toLowerCase()
        );

      const athleteAgeGroup =
        getAgeGroup(
          athlete.dateOfBirth
        );

      const matchesAgeGroup =
        selectedAgeGroups.length ===
          0 ||
        selectedAgeGroups.includes(
          athleteAgeGroup
        );

      const athleteEvents =
        athleteEventMap[
          athlete.id
        ] ?? [];

      /*
       * Multiple selected events use OR logic.
       * Example:
       * 100M + 200M shows an athlete who
       * participates in either event.
       */
      const matchesEvent =
        selectedEventFilters.length ===
          0 ||
        selectedEventFilters.some(
          /* Check whether any selected event matches the required condition. */ (selectedEvent) =>
            athleteEvents.includes(
              selectedEvent.toUpperCase()
            )
        );

      return (
        Boolean(athlete.archived) === (archiveView === "archived") &&
        matchesSearch &&
        matchesAgeGroup &&
        matchesEvent &&
        (!genderFilter || athlete.gender?.trim().toLowerCase() === genderFilter) &&
        (!statusFilter || [athlete.flyStatus, athlete.flyaStatus].some(/* Check whether any status matches the required condition. */ status => status?.trim().toLowerCase() === statusFilter))
      );
    });

  /* ==================================================
     PAGINATION
     ================================================== */

  const totalPages = Math.ceil(
    filteredAthletes.length /
      pageSize
  );

  const startIndex =
    (currentPage - 1) *
    pageSize;

  const endIndex =
    startIndex + pageSize;

  const paginatedAthletes =
    filteredAthletes.slice(
      startIndex,
      endIndex
    );

  const hasActiveFilters =
    searchTerm.trim() !== "" ||
    selectedAgeGroups.length > 0 ||
    selectedEventFilters.length > 0 || genderFilter !== "" || statusFilter !== "";

  const pageSelectedCount = paginatedAthletes.filter(/* Keep the records included in the current selection. */ athlete => selectedAthleteIds.includes(athlete.id)).length;
  const selectedAthletes = athletes.filter(/* Keep the records included in the current selection. */ athlete => selectedAthleteIds.includes(athlete.id));
  const activeCount = athletes.filter(/* Apply the search and selection criteria for active count. */ athlete => !athlete.archived).length;
  const archivedCount = athletes.length - activeCount;

  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    if (selectPageRef.current) selectPageRef.current.indeterminate = pageSelectedCount > 0 && pageSelectedCount < paginatedAthletes.length;
  }, [pageSelectedCount, paginatedAthletes.length]);

  // Archive or restore the confirmed selection and refresh the displayed records.
  async function applyArchiveSelection() {
    if (isArchiving || !selectedAthletes.length) return;
    setIsArchiving(true); setArchiveError("");
    try {
      const updated = await setAthletesArchived(selectedAthletes.map(/* Extract each athlete ID for lookup or selection. */ athlete => athlete.id), archiveView === "active");
      const updatedById = new Map(updated.map(/* Index each athlete by its ID. */ athlete => [athlete.id, athlete]));
      setAthletes(/* Derive the next athletes value from its current state. */ current => current.map(/* Convert each athlete into the required display or request data. */ athlete => updatedById.get(athlete.id) ?? athlete));
      setArchiveFeedback(`${updated.length} athlete${updated.length === 1 ? "" : "s"} ${archiveView === "active" ? "archived" : "restored to the active directory"}.`);
      setSelectedAthleteIds([]); setCurrentPage(1); setArchiveDialogOpen(false);
    } catch (error) { setArchiveError(error instanceof Error ? error.message : "Unable to update the archive."); }
    finally { setIsArchiving(false); }
  }



  return (
    <div className="directory-page">
      <Header />

      <main className="directory-container">
        <section className="page-heading">
          <h1>Athletes</h1>

          <p className="page-description">
            Search, edit and update Flying Angels athlete
            information.
          </p>
        </section>

        <section className="directory-panel">
          {/* Archive view and feedback are separate from membership/search filters. */}
          <div className="directory-view-switch" role="group" aria-label="Directory view">
            {(["active", "archived"] as const).map(/* Render each view in the list. */ view => <button type="button" key={view} aria-pressed={archiveView === view} onClick={/* Toggle the selected rows for the next bulk action. */ () => { setArchiveView(view); setSelectedAthleteIds([]); setCurrentPage(1); setArchiveFeedback(""); }}>
              {view === "active" ? "Active athletes" : "Archived athletes"} <span>{isLoading ? "…" : view === "active" ? activeCount : archivedCount}</span>
            </button>)}
          </div>
          {archiveView === "archived" && <p className="directory-archive-help">Archived athletes keep their profiles, results, and notes. Restore them when they should return to the active directory.</p>}
          {archiveFeedback && <p className="directory-archive-feedback" role="status">{archiveFeedback}</p>}
          <div className="directory-toolbar">
            <div className="search-wrapper">
              <span className="search-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></svg>
              </span>

              <input
                type="text"
                className="search-input"
                placeholder="Search by athlete name..."
                aria-label="Search athletes by name"
                value={searchTerm}
               onChange={/* Update the search term value from this control. */ (event) => {
  setSearchTerm(
    event.target.value
  );
  setCurrentPage(1);
}}
              />
            </div>

            <button hidden={!canManage}
              className="add-athlete-button"
              type="button"
              onClick={/* Update add modal open for the selected action. */ () =>
                setAddModalOpen(true)
              }
            >
              + Add Athlete
            </button>
          </div>

          <div className="filter-panel" aria-label="Filter athletes">
              {/* AGE GROUP FILTER */}

              <div className="multi-select">
                <span className="filter-label">
                  Age Group
                </span>

                <button
                  className="multi-select-button"
                  type="button"
                  onClick={/* Update age dropdown open for the selected action. */ () => {
                    setAgeDropdownOpen(
                      !ageDropdownOpen
                    );

                    setEventFilterDropdownOpen(
                      false
                    );
                  }}
                >
                  <span>
                    {selectedAgeGroups.length ===
                    0
                      ? "All age groups"
                      : selectedAgeGroups.join(
                          ", "
                        )}
                  </span>

                  <span
                    className={`event-select-arrow ${
                      ageDropdownOpen
                        ? "open"
                        : ""
                    }`}
                  ></span>
                </button>

                {ageDropdownOpen && (
                  <div className="multi-select-menu">
                    {[
                      "5-14",
                      "15-18",
                      "18+",
                    ].map(
                      /* Render each age group in the list. */ (ageGroup) => {
                        const isSelected =
                          selectedAgeGroups.includes(
                            ageGroup
                          );

                        return (
                          <button
                            key={
                              ageGroup
                            }
                            type="button"
                            className={`multi-select-option ${
                              isSelected
                                ? "selected"
                                : ""
                            }`}
                            onClick={/* Perform the selected button action. */ () =>
                              toggleAgeGroup(
                                ageGroup
                              )
                            }
                          >
                            <span>
                              {
                                ageGroup
                              }
                            </span>

                            <span className="option-check">
                              {isSelected
                                ? "✓"
                                : ""}
                            </span>
                          </button>
                        );
                      }
                    )}
                  </div>
                )}
              </div>

              {/* EVENT FILTER */}

              <div className="multi-select">
                <span className="filter-label">
                  Events
                </span>

                <button
                  className="multi-select-button"
                  type="button"
                  onClick={/* Update event filter dropdown open for the selected action. */ () => {
                    void loadAthleteEvents();
                    setEventFilterDropdownOpen(
                      !eventFilterDropdownOpen
                    );

                    setAgeDropdownOpen(
                      false
                    );
                  }}
                  disabled={
                    eventsLoading
                  }
                >
                  <span>
                    {eventsLoading
                      ? "Loading events..."
                      : selectedEventFilters.length ===
                          0
                        ? "All events"
                        : `${selectedEventFilters.length} ${
                            selectedEventFilters.length ===
                            1
                              ? "event"
                              : "events"
                          } selected`}
                  </span>

                  <span
                    className={`event-select-arrow ${
                      eventFilterDropdownOpen
                        ? "open"
                        : ""
                    }`}
                  ></span>
                </button>

                {eventFilterDropdownOpen &&
                  !eventsLoading && (
                    <div className="multi-select-menu">
                      {EVENT_OPTIONS.map(
                        /* Render each event name in the list. */ (
                          eventName
                        ) => {
                          const isSelected =
                            selectedEventFilters.includes(
                              eventName
                            );

                          return (
                            <button
                              key={
                                eventName
                              }
                              type="button"
                              className={`multi-select-option ${
                                isSelected
                                  ? "selected"
                                  : ""
                              }`}
                              onClick={/* Perform the selected button action. */ () =>
                                toggleEventFilter(
                                  eventName
                                )
                              }
                            >
                              <span>
                                {
                                  eventName
                                }
                              </span>

                              <span className="option-check">
                                {isSelected
                                  ? "✓"
                                  : ""}
                              </span>
                            </button>
                          );
                        }
                      )}
                    </div>
                  )}
              </div>

              <label className="directory-select-filter"><span className="filter-label">Gender</span><select aria-label="Filter by gender" value={genderFilter} onChange={/* Update the gender filter value from this control. */ event => { setGenderFilter(event.target.value); setCurrentPage(1); }}><option value="">All genders</option><option value="female">Female</option><option value="male">Male</option></select></label>
              <label className="directory-select-filter"><span className="filter-label">Status</span><select aria-label="Filter by roster status" value={statusFilter} onChange={/* Update the status filter value from this control. */ event => { setStatusFilter(event.target.value); setCurrentPage(1); }}><option value="">All statuses</option>{Array.from(new Set(athletes.flatMap(/* Convert each athlete into the required display or request data. */ athlete => [athlete.flyStatus, athlete.flyaStatus]).filter(/* Keep entries that satisfy the current filter conditions. */ (status): status is string => Boolean(status?.trim())).map(/* Trim each value before comparing or saving it. */ status => status.trim().toLowerCase()))).sort().map(/* Render each status in the list. */ status => <option key={status} value={status}>{status.charAt(0).toUpperCase() + status.slice(1)}</option>)}</select></label>
              <button className="clear-filters-button" type="button" onClick={clearAll} disabled={!hasActiveFilters}>Clear</button>
          </div>

          {/* Bulk actions use the complete selection, including other pages. */}
          {!isLoading && !loadError && <div hidden={!canManage} className="directory-bulk-actions">
            <span>{selectedAthletes.length} selected{selectedAthletes.length > pageSelectedCount ? " across the directory" : ""}</span>
            {selectedAthletes.length > 0 && <button type="button" className="directory-clear-selection" onClick={/* Toggle the selected rows for the next bulk action. */ () => setSelectedAthleteIds([])}>Clear selection</button>}
            <button type="button" className="directory-archive-button" disabled={!selectedAthletes.length} onClick={/* Update archive error for the selected action. */ () => { setArchiveError(""); setArchiveDialogOpen(true); }}>{archiveView === "active" ? "Archive selected" : "Restore selected"}</button>
          </div>}
          <div className="athlete-list">
            {isLoading ? (
              <div className="no-results">
                <p>
                  Loading athletes...
                </p>

                <span>
                  Athlete information is
                  being retrieved.
                </span>
              </div>
            ) : loadError ? (
              <div className="no-results">
                <p>
                  Unable to load
                  athletes.
                </p>

                <span>
                  {loadError}
                </span>
              </div>
            ) : filteredAthletes.length ===
              0 ? (
              <div className="no-results">
                <p>
                  {archiveView === "archived" && !hasActiveFilters ? "No archived athletes." : "No athletes found."}
                </p>

                <span>
                  {archiveView === "archived" && !hasActiveFilters ? "Select athletes in the active directory to archive them." : "Try changing your search or filters."}
                </span>
              </div>
            ) : (
              <div className="directory-table-scroll" role="region" aria-label="Athlete directory" tabIndex={0}>
                <table className="directory-table">
                  <thead><tr><th hidden={!canManage} scope="col" className="directory-selection-cell"><input ref={selectPageRef} type="checkbox" aria-label="Select page" checked={paginatedAthletes.length > 0 && pageSelectedCount === paginatedAthletes.length} onChange={/* Update the selected athlete ids value from this control. */ event => {
                    const pageIds = paginatedAthletes.map(/* Extract each athlete ID for lookup or selection. */ athlete => athlete.id);
                    setSelectedAthleteIds(/* Derive the next selected athlete ids value from its current state. */ current => event.target.checked ? [...new Set([...current, ...pageIds])] : current.filter(/* Keep entries that satisfy the current filter conditions. */ id => !pageIds.includes(id)));
                  }} /></th>{["Name", "Age Group", "Gender", "FLY Status", "FLYA Status", "Actions"].map(/* Render each label in the list. */ label => <th key={label} scope="col">{label}</th>)}</tr></thead>
                  <tbody>{paginatedAthletes.map(/* Render each athlete in the list. */ athlete => <tr key={athlete.id}>
                    <td hidden={!canManage} className="directory-selection-cell"><input type="checkbox" aria-label={`Select ${athlete.firstName} ${athlete.lastName}`} checked={selectedAthleteIds.includes(athlete.id)} onChange={/* Update the selected athlete ids value from this control. */ event => setSelectedAthleteIds(/* Derive the next selected athlete ids value from its current state. */ current => event.target.checked ? [...current, athlete.id] : current.filter(/* Exclude entries that do not belong in this result or selection. */ id => id !== athlete.id))} /></td>
                    <th scope="row"><Link to={`/athletes/${athlete.id}`}>{athlete.firstName} {athlete.lastName}</Link></th>
                    <td>{getAgeGroup(athlete.dateOfBirth) || "Not provided"}</td>
                    <td>{athlete.gender?.trim() || "Not provided"}</td>
                    <td>{athlete.flyStatus?.trim() || "Not provided"}</td>
                    <td>{athlete.flyaStatus?.trim() || "Not provided"}</td>
                    <td><div hidden={!canManage} className="directory-row-actions">
                      <button type="button" title="Edit athlete" aria-label={`Edit ${athlete.firstName} ${athlete.lastName}`} onClick={/* Perform the selected button action. */ () => navigate(`/athletes/${athlete.id}`, {state: {editAthlete: true}})}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="m15 5 4 4M4 20l4-1 12-12a2.8 2.8 0 0 0-4-4L4 15z" /></svg></button>
                      <button type="button" className="directory-delete-action" title="Delete athlete" aria-label={`Delete ${athlete.firstName} ${athlete.lastName}`} onClick={/* Choose the record for deletion confirmation. */ () => { setDeleteError(""); setDeleteTarget(athlete); }}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7" /></svg></button>
                    </div></td>
                  </tr>)}</tbody>
                </table>
              </div>
            )}
          </div>

          {!isLoading && !loadError && <div className="pagination">
            <label className="directory-page-size">Show <select value={pageSize} onChange={/* Update the page size value from this control. */ event => {setPageSize(Number(event.target.value)); setCurrentPage(1);}}>{[10,25,50].map(/* Render each size in the list. */ size => <option key={size} value={size}>{size}</option>)}</select></label>
            <span className="pagination-summary" role="status">{filteredAthletes.length ? startIndex + 1 : 0}–{Math.min(endIndex, filteredAthletes.length)} of {filteredAthletes.length}</span>
            <nav className="pagination-controls" aria-label="Athlete pages">
              <button className="pagination-button" type="button" aria-label="Previous page" disabled={currentPage <= 1} onClick={/* Update current page for the selected action. */ () => setCurrentPage(/* Derive the next current page value from its current state. */ page => Math.max(1,page-1))}>‹</button>
              <span className="pagination-page" aria-current="page">{currentPage}</span>
              <button className="pagination-button" type="button" aria-label="Next page" disabled={currentPage >= totalPages} onClick={/* Update current page for the selected action. */ () => setCurrentPage(/* Derive the next current page value from its current state. */ page => Math.min(totalPages,page+1))}>›</button>
            </nav>
          </div>}
        </section>
      </main>

      {archiveDialogOpen && <MeetDialog title={archiveView === "active" ? "Archive selected athletes?" : "Restore selected athletes?"} busy={isArchiving} onClose={/* Close the dialog and clear its visible state. */ () => { if (!isArchiving) setArchiveDialogOpen(false); }}>
        <div className="athlete-form">
          <p>{archiveView === "active" ? "These athletes will leave the active directory. Their profiles, results, and notes will be preserved, and you can restore them later." : "These athletes will return to the active directory and be available for new event assignments."}</p>
          <ul className="directory-archive-list">{selectedAthletes.map(/* Render each athlete in the list. */ athlete => <li key={athlete.id}>{athlete.firstName} {athlete.lastName}</li>)}</ul>
          {archiveError && <p className="form-error" role="alert">{archiveError}</p>}
          <div className="modal-actions"><button className="cancel-button" type="button" disabled={isArchiving} onClick={/* Update archive dialog open for the selected action. */ () => setArchiveDialogOpen(false)} autoFocus>Cancel</button><button className="save-button" type="button" disabled={isArchiving} onClick={applyArchiveSelection}>{isArchiving ? "Saving…" : `${archiveView === "active" ? "Archive" : "Restore"} ${selectedAthletes.length} athlete${selectedAthletes.length === 1 ? "" : "s"}`}</button></div>
        </div>
      </MeetDialog>}

      {deleteTarget && <MeetDialog title="Delete athlete?" busy={isDeleting} onClose={/* Close the dialog and clear its visible state. */ () => { if (!isDeleting) setDeleteTarget(null); }}>
        <div className="athlete-form"><p>Delete {deleteTarget.firstName} {deleteTarget.lastName}? This permanently removes their athlete record.</p>
          {deleteError && <p className="form-error" role="alert">{deleteError}</p>}
          <div className="modal-actions"><button className="cancel-button" type="button" disabled={isDeleting} onClick={/* Choose the record for deletion confirmation. */ () => setDeleteTarget(null)} autoFocus>Cancel</button><button className="save-button directory-confirm-delete" type="button" disabled={isDeleting} onClick={/* Choose the record for deletion confirmation. */ async () => {
            setIsDeleting(true); setDeleteError("");
            try { await deleteAthlete(deleteTarget.id); setAthletes(/* Derive the next athletes value from its current state. */ current => current.filter(/* Exclude entries that do not belong in this result or selection. */ athlete => athlete.id !== deleteTarget.id)); setCurrentPage(1); setDeleteTarget(null); }
            catch { setDeleteError("Unable to delete this athlete. Please try again."); }
            finally { setIsDeleting(false); }
          }}>{isDeleting ? "Deleting..." : "Delete athlete"}</button></div>
        </div>
      </MeetDialog>}

      {/* ==================================================
          ADD ATHLETE MODAL
          ================================================== */}

      {addModalOpen && (
        <div
          className="modal-overlay"
          onMouseDown={
            closeAddModal
          }
        >
          <div
            className="athlete-modal"
            onMouseDown={/* Handle the mouse down interaction. */ (event) =>
              event.stopPropagation()
            }
          >
            <div className="modal-heading">
              <div>
                <p className="modal-eyebrow">
                  ATHLETE MANAGEMENT
                </p>

                <h2>
                  Add Athlete
                </h2>
              </div>

              <button
                className="modal-close"
                type="button"
                onClick={
                  closeAddModal
                }
                aria-label="Close"
                disabled={isAdding}
              >
                ×
              </button>
            </div>

            <form
              className="athlete-form"
              onSubmit={
                handleAddAthlete
              }
            >
              <div className="form-grid">
                <div className="form-field">
                  <label htmlFor="first-name">
                    First Name *
                  </label>

                  <input
                    id="first-name"
                    type="text"
                    value={
                      newFirstName
                    }
                    maxLength={100}
                    onChange={/* Update the new first name value from this control. */ (
                      event
                    ) =>
                      setNewFirstName(
                        event.target
                          .value
                      )
                    }
                    required
                  />
                </div>

                <div className="form-field">
                  <label htmlFor="last-name">
                    Last Name *
                  </label>

                  <input
                    id="last-name"
                    type="text"
                    value={
                      newLastName
                    }
                    maxLength={100}
                    onChange={/* Update the new last name value from this control. */ (
                      event
                    ) =>
                      setNewLastName(
                        event.target
                          .value
                      )
                    }
                    required
                  />
                </div>

                <div className="form-field form-field-wide">
                  <label>
                    Date of Birth
                  </label>

                  <div className="dob-fields">
                    <select
                      aria-label="Birth month"
                      value={
                        birthMonth
                      }
                      onChange={/* Apply the changed control value to the current selection. */ (
                        event
                      ) =>
                        updateBirthDate(
                          birthYear,
                          event.target
                            .value,
                          birthDay
                        )
                      }
                    >
                      <option value="">
                        Month
                      </option>

                      {months.map(
                        /* Render each month in the list. */ (month) => (
                          <option
                            key={
                              month.value
                            }
                            value={
                              month.value
                            }
                          >
                            {
                              month.label
                            }
                          </option>
                        )
                      )}
                    </select>

                    <select
                      aria-label="Birth day"
                      value={birthDay}
                      onChange={/* Apply the changed control value to the current selection. */ (
                        event
                      ) =>
                        updateBirthDate(
                          birthYear,
                          birthMonth,
                          event.target
                            .value
                        )
                      }
                    >
                      <option value="">
                        Day
                      </option>

                      {Array.from(
                        {
                          length: 31,
                        },
                        /* Build each generated item from its index. */ (
                          _,
                          index
                        ) => {
                          const day =
                            String(
                              index +
                                1
                            ).padStart(
                              2,
                              "0"
                            );

                          return (
                            <option
                              key={
                                day
                              }
                              value={
                                day
                              }
                            >
                              {index +
                                1}
                            </option>
                          );
                        }
                      )}
                    </select>

                    <select
                      aria-label="Birth year"
                      value={
                        birthYear
                      }
                      onChange={/* Apply the changed control value to the current selection. */ (
                        event
                      ) =>
                        updateBirthDate(
                          event.target
                            .value,
                          birthMonth,
                          birthDay
                        )
                      }
                    >
                      <option value="">
                        Year
                      </option>

                      {birthYears.map(
                        /* Render each year in the list. */ (year) => (
                          <option
                            key={
                              year
                            }
                            value={
                              year
                            }
                          >
                            {year}
                          </option>
                        )
                      )}
                    </select>
                  </div>
                </div>

                <div className="form-field">
                  <label htmlFor="gender">
                    Gender
                  </label>

                  <select
                    id="gender"
                    value={newGender}
                    onChange={/* Update the new gender value from this control. */ (
                      event
                    ) =>
                      setNewGender(
                        event.target
                          .value
                      )
                    }
                  >
                    <option value="">
                      Select gender
                    </option>

                    <option value="Male">
                      Male
                    </option>

                    <option value="Female">
                      Female
                    </option>

                    <option value="Rather not say">
                      Rather not say
                    </option>
                  </select>
                </div>

                <div className="form-field">
                  <label htmlFor="fly-status">
                    FLY Status
                  </label>

                  <input
                    id="fly-status"
                    type="text"
                    value={
                      newFlyStatus
                    }
                    maxLength={100}
                    onChange={/* Update the new fly status value from this control. */ (
                      event
                    ) =>
                      setNewFlyStatus(
                        event.target
                          .value
                      )
                    }
                  />
                </div>

                <div className="form-field">
                  <label htmlFor="flya-status">
                    FLYA Status
                  </label>

                  <input
                    id="flya-status"
                    type="text"
                    value={
                      newFlyaStatus
                    }
                    maxLength={100}
                    onChange={/* Update the new flya status value from this control. */ (
                      event
                    ) =>
                      setNewFlyaStatus(
                        event.target
                          .value
                      )
                    }
                  />
                </div>

                <div className="form-field form-field-wide">
                  <label htmlFor="note">
                    Note
                  </label>

                  <textarea
                    id="note"
                    value={newNote}
                    maxLength={500}
                    onChange={/* Update the new note value from this control. */ (
                      event
                    ) =>
                      setNewNote(
                        event.target
                          .value
                      )
                    }
                  />
                </div>
              </div>

              {/* ======================================
                  EVENTS
                  ====================================== */}

              <div className="add-events-section">
                <div className="add-events-heading">
                  <span className="add-events-title">
                    Events
                  </span>

                  <span className="add-events-description">
                    Select the athlete's events. Scores are provided by the system.
                  </span>
                </div>

                <div className="event-multi-select">
                  <button
                    className="event-multi-select-button"
                    type="button"
                    onClick={/* Update event dropdown open for the selected action. */ () =>
                      setEventDropdownOpen(
                        !eventDropdownOpen
                      )
                    }
                    disabled={
                      isAdding
                    }
                  >
                    <span>
                      {newEvents.length ===
                      0
                        ? "Select events"
                        : `${newEvents.length} ${
                            newEvents.length ===
                            1
                              ? "event"
                              : "events"
                          } selected`}
                    </span>

                    <span className="dropdown-arrow">
                      {eventDropdownOpen
                        ? "⌃"
                        : "⌄"}
                    </span>
                  </button>

                  {eventDropdownOpen && (
                    <div className="event-multi-select-menu">
                      {EVENT_OPTIONS.map(
                        /* Render each event name in the list. */ (
                          eventName
                        ) => {
                          const isSelected =
                            newEvents.some(
                              /* Check whether any event matches the required condition. */ (
                                event
                              ) =>
                                event.eventName ===
                                eventName
                            );

                          return (
                            <button
                              key={
                                eventName
                              }
                              type="button"
                              className={`event-multi-select-option ${
                                isSelected
                                  ? "selected"
                                  : ""
                              }`}
                              onClick={/* Perform the selected button action. */ () =>
                                toggleEvent(
                                  eventName
                                )
                              }
                            >
                              <span>
                                {
                                  eventName
                                }
                              </span>

                              <span className="option-check">
                                {isSelected
                                  ? "✓"
                                  : ""}
                              </span>
                            </button>
                          );
                        }
                      )}
                    </div>
                  )}
                </div>

                {newEvents.length >
                  0 && (
                  <div className="selected-events">
                    <div className="selected-events-header">
                      <span>
                        Event
                      </span>

                      <span>
                        Score
                      </span>

                      <span></span>
                    </div>

                    {newEvents.map(
                      /* Render each event in the list. */ (event) => (
                        <div
                          className="selected-event-row"
                          key={
                            event.eventName
                          }
                        >
                          <span className="selected-event-name">
                            {
                              event.eventName
                            }
                          </span>

                          <span className="selected-event-score">
                            Not available
                          </span>

                          <button
                            className="remove-event-button"
                            type="button"
                            onClick={/* Perform the selected button action. */ () =>
                              removeEvent(
                                event.eventName
                              )
                            }
                            disabled={
                              isAdding
                            }
                          >
                            Remove
                          </button>
                        </div>
                      )
                    )}
                  </div>
                )}
              </div>

              {addError && (
                <div className="form-error">
                  {addError}
                </div>
              )}

              <div className="modal-actions">
                <button
                  className="cancel-button"
                  type="button"
                  onClick={
                    closeAddModal
                  }
                  disabled={
                    isAdding
                  }
                >
                  Cancel
                </button>

                <button
                  className="save-button"
                  type="submit"
                  disabled={
                    isAdding
                  }
                >
                  {isAdding
                    ? "Adding..."
                    : "Add Athlete"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default AthleteDirectoryPage;

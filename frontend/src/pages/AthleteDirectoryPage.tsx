import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Header from "../components/header";
import type { Athlete } from "../types/athlete";
import {
  createAthlete,
  getAthleteById,
  getAthletes,
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

const ATHLETES_PER_PAGE = 10;

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

function AthleteDirectoryPage() {
  const navigate = useNavigate();

  const [athletes, setAthletes] =
    useState<Athlete[]>([]);

  const [isLoading, setIsLoading] =
    useState(true);

  const [loadError, setLoadError] =
    useState("");

  const [searchTerm, setSearchTerm] =
    useState("");

  const [currentPage, setCurrentPage] =
    useState(1);

  /* ==================================================
     FILTERS
     ================================================== */

  const [filtersOpen, setFiltersOpen] =
    useState(false);

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
    (_, index) =>
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

  const toggleEvent = (
    eventName: string
  ) => {
    setNewEvents((currentEvents) => {
      const isSelected =
        currentEvents.some(
          (event) =>
            event.eventName === eventName
        );

      if (isSelected) {
        return currentEvents.filter(
          (event) =>
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

  const removeEvent = (
    eventName: string
  ) => {
    setNewEvents((currentEvents) =>
      currentEvents.filter(
        (event) =>
          event.eventName !== eventName
      )
    );
  };

  /* ==================================================
     LOAD ATHLETES
     ================================================== */

  useEffect(() => {
    async function loadAthletes() {
      try {
        setIsLoading(true);
        setLoadError("");

        const data =
          await getAthletes();

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
            athletes.map((athlete) =>
              getAthleteById(athlete.id)
            )
          );

        const eventMap: Record<
          number,
          string[]
        > = {};

        profiles.forEach((profile) => {
          eventMap[profile.id] = (
            profile.events ?? []
          ).map((event) =>
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

  const toggleAgeGroup = (
  ageGroup: string
) => {
  setSelectedAgeGroups(
    (currentGroups) =>
      currentGroups.includes(ageGroup)
        ? currentGroups.filter(
            (group) =>
              group !== ageGroup
          )
        : [
            ...currentGroups,
            ageGroup,
          ]
  );

  setCurrentPage(1);
};

const toggleEventFilter = (
  eventName: string
) => {
  setSelectedEventFilters(
    (currentEvents) =>
      currentEvents.includes(eventName)
        ? currentEvents.filter(
            (event) =>
              event !== eventName
          )
        : [
            ...currentEvents,
            eventName,
          ]
  );

  setCurrentPage(1);
};
const clearFilters = () => {
  setSelectedAgeGroups([]);
  setSelectedEventFilters([]);
  setAgeDropdownOpen(false);
  setEventFilterDropdownOpen(false);
  setCurrentPage(1);
};

const clearAll = () => {
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

  const closeAddModal = () => {
    if (isAdding) {
      return;
    }

    setAddModalOpen(false);
    resetAddForm();
  };

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
          events: newEvents.map((event) => ({
            eventName: event.eventName,
            result: "",
          })),
        });

      setAthletes(
        (currentAthletes) => [
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
    athletes.filter((athlete) => {
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
          (selectedEvent) =>
            athleteEvents.includes(
              selectedEvent.toUpperCase()
            )
        );

      return (
        matchesSearch &&
        matchesAgeGroup &&
        matchesEvent
      );
    });

  /* ==================================================
     PAGINATION
     ================================================== */

  const totalPages = Math.ceil(
    filteredAthletes.length /
      ATHLETES_PER_PAGE
  );

  const startIndex =
    (currentPage - 1) *
    ATHLETES_PER_PAGE;

  const endIndex =
    startIndex + ATHLETES_PER_PAGE;

  const paginatedAthletes =
    filteredAthletes.slice(
      startIndex,
      endIndex
    );

  const hasActiveFilters =
    searchTerm.trim() !== "" ||
    selectedAgeGroups.length > 0 ||
    selectedEventFilters.length > 0;

  const activeFilterCount =
    selectedAgeGroups.length +
    selectedEventFilters.length;

  return (
    <div className="directory-page">
      <Header />

      <main className="directory-container">
        <section className="page-heading">
          <p className="page-eyebrow">
            ATHLETE MANAGEMENT
          </p>

          <p className="page-description">
            Search, edit and update Flying Angels athlete
            information.
          </p>
        </section>

        <section className="directory-panel">
          <div className="directory-toolbar">
            <div className="search-wrapper">
              <span className="search-icon">
                ⌕
              </span>

              <input
                type="text"
                className="search-input"
                placeholder="Search by athlete name..."
                value={searchTerm}
               onChange={(event) => {
  setSearchTerm(
    event.target.value
  );
  setCurrentPage(1);
}}
              />
            </div>

            <button
              className="filter-button"
              type="button"
              onClick={() => {
                const willOpen =
                  !filtersOpen;

                setFiltersOpen(
                  willOpen
                );

                if (willOpen) {
                  void loadAthleteEvents();
                }
              }}
            >
              <span>
                Filters

                {activeFilterCount >
                  0 && (
                  <span className="filter-count">
                    {
                      activeFilterCount
                    }
                  </span>
                )}
              </span>

              <span
                className={`filter-arrow ${
                  filtersOpen
                    ? "open"
                    : ""
                }`}
              ></span>
            </button>

            <button
              className="add-athlete-button"
              type="button"
              onClick={() =>
                setAddModalOpen(true)
              }
            >
              + Add Athlete
            </button>
          </div>

          {filtersOpen && (
            <div className="filter-panel">
              {/* AGE GROUP FILTER */}

              <div className="multi-select">
                <span className="filter-label">
                  Age Group
                </span>

                <button
                  className="multi-select-button"
                  type="button"
                  onClick={() => {
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
                      (ageGroup) => {
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
                            onClick={() =>
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
                  onClick={() => {
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
                        (
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
                              onClick={() =>
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

              {(selectedAgeGroups.length >
                0 ||
                selectedEventFilters.length >
                  0) && (
                <button
                  className="clear-filters-button"
                  type="button"
                  onClick={
                    clearFilters
                  }
                >
                  Clear Filters
                </button>
              )}
            </div>
          )}

          <div className="results-header">
            <h2>Athletes</h2>

            <div className="results-summary">
              <span>
                {
                  filteredAthletes.length
                }{" "}
                {filteredAthletes.length ===
                1
                  ? "athlete"
                  : "athletes"}{" "}
                found
              </span>

              {searchTerm.trim() !==
                "" && (
                <>
                  <span className="summary-divider">
                    •
                  </span>

                  <span>
                    Search: "
                    {searchTerm}"
                  </span>
                </>
              )}

              {selectedAgeGroups.length >
                0 && (
                <>
                  <span className="summary-divider">
                    •
                  </span>

                  <span>
                    {
                      selectedAgeGroups.length
                    }{" "}
                    {selectedAgeGroups.length ===
                    1
                      ? "age group"
                      : "age groups"}
                  </span>
                </>
              )}

              {selectedEventFilters.length >
                0 && (
                <>
                  <span className="summary-divider">
                    •
                  </span>

                  <span>
                    {
                      selectedEventFilters.length
                    }{" "}
                    {selectedEventFilters.length ===
                    1
                      ? "event"
                      : "events"}
                  </span>
                </>
              )}

              {hasActiveFilters && (
                <button
                  className="clear-all-button"
                  type="button"
                  onClick={clearAll}
                >
                  Clear all
                </button>
              )}
            </div>
          </div>

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
                  No athletes found.
                </p>

                <span>
                  Try changing your
                  search or filters.
                </span>
              </div>
            ) : (
              paginatedAthletes.map(
                (athlete) => (
                  <button
                    className="athlete-row"
                    type="button"
                    key={athlete.id}
                    onClick={() =>
                      navigate(
                        `/athletes/${athlete.id}`
                      )
                    }
                  >
                    <div className="athlete-identity">
                      <div className="athlete-initials">
                        {athlete.firstName.charAt(
                          0
                        )}
                        {athlete.lastName.charAt(
                          0
                        )}
                      </div>

                      <div>
                        <span className="athlete-name">
                          {
                            athlete.firstName
                          }{" "}
                          {
                            athlete.lastName
                          }
                        </span>

                        <span className="athlete-id">
                          Athlete #
                          {athlete.id}
                        </span>
                      </div>
                    </div>

                    <span className="row-arrow">
                      ›
                    </span>
                  </button>
                )
              )
            )}
          </div>

          {!isLoading &&
            !loadError &&
            filteredAthletes.length > 0 && (
              <div className="pagination">
                <span className="pagination-summary">
                  Showing{" "}
                  {startIndex + 1}–
                  {Math.min(
                    endIndex,
                    filteredAthletes.length
                  )}{" "}
                  of{" "}
                  {filteredAthletes.length}{" "}
                  athletes
                </span>

                {totalPages > 1 && (
                  <div className="pagination-controls">
                    <button
                      className="pagination-button"
                      type="button"
                      onClick={() =>
                        setCurrentPage(
                          (page) =>
                            Math.max(
                              1,
                              page - 1
                            )
                        )
                      }
                      disabled={
                        currentPage === 1
                      }
                    >
                      ← Previous
                    </button>

                    <span className="pagination-page">
                      Page {currentPage} of{" "}
                      {totalPages}
                    </span>

                    <button
                      className="pagination-button"
                      type="button"
                      onClick={() =>
                        setCurrentPage(
                          (page) =>
                            Math.min(
                              totalPages,
                              page + 1
                            )
                        )
                      }
                      disabled={
                        currentPage ===
                        totalPages
                      }
                    >
                      Next →
                    </button>
                  </div>
                )}
              </div>
            )}
        </section>
      </main>

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
            onMouseDown={(event) =>
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
                    onChange={(
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
                    onChange={(
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
                      onChange={(
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
                        (month) => (
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
                      onChange={(
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
                        (
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
                      onChange={(
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
                        (year) => (
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
                    onChange={(
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
                    onChange={(
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
                    onChange={(
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
                    onChange={(
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
                    onClick={() =>
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
                        (
                          eventName
                        ) => {
                          const isSelected =
                            newEvents.some(
                              (
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
                              onClick={() =>
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
                      (event) => (
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
                            onClick={() =>
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
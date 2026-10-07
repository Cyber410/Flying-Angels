import { useState } from "react";
import { useNavigate } from "react-router-dom";
import Header from "../components/header";
import { EVENT_OPTIONS } from "../data/athleteOptions";
import { useAthletes } from "../context/AthleteContext";

import "./AthleteDirectoryPage.css";

function calculateAge(dateOfBirth: string) {
  const today = new Date();
  const birthDate = new Date(`${dateOfBirth}T00:00:00`);

  let age = today.getFullYear() - birthDate.getFullYear();

  const monthDifference =
    today.getMonth() - birthDate.getMonth();

  if (
    monthDifference < 0 ||
    (monthDifference === 0 &&
      today.getDate() < birthDate.getDate())
  ) {
    age--;
  }

  return age;
}

function getAgeGroup(dateOfBirth: string) {
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

  const { athletes, addAthlete } = useAthletes();

  const [searchTerm, setSearchTerm] = useState("");

  const [filtersOpen, setFiltersOpen] =
    useState(false);

  const [ageDropdownOpen, setAgeDropdownOpen] =
    useState(false);

  const [eventDropdownOpen, setEventDropdownOpen] =
    useState(false);

  const [selectedAgeGroups, setSelectedAgeGroups] =
    useState<string[]>([]);

  const [selectedEvents, setSelectedEvents] =
    useState<string[]>([]);

  const [addModalOpen, setAddModalOpen] =
    useState(false);

  const [newFirstName, setNewFirstName] =
    useState("");

  const [newLastName, setNewLastName] =
    useState("");

  const [newDateOfBirth, setNewDateOfBirth] =
    useState("");

  const [newHometown, setNewHometown] =
    useState("");

  const [newTeam, setNewTeam] =
    useState("");

  const [newEvents, setNewEvents] =
    useState<string[]>([]);

  const toggleAgeGroup = (ageGroup: string) => {
    setSelectedAgeGroups((currentGroups) =>
      currentGroups.includes(ageGroup)
        ? currentGroups.filter(
            (group) => group !== ageGroup
          )
        : [...currentGroups, ageGroup]
    );
  };

  const toggleEvent = (event: string) => {
    setSelectedEvents((currentEvents) =>
      currentEvents.includes(event)
        ? currentEvents.filter(
            (selectedEvent) =>
              selectedEvent !== event
          )
        : [...currentEvents, event]
    );
  };

  const toggleNewAthleteEvent = (event: string) => {
    setNewEvents((currentEvents) =>
      currentEvents.includes(event)
        ? currentEvents.filter(
            (selectedEvent) =>
              selectedEvent !== event
          )
        : [...currentEvents, event]
    );
  };

  const clearFilters = () => {
    setSelectedAgeGroups([]);
    setSelectedEvents([]);
    setAgeDropdownOpen(false);
    setEventDropdownOpen(false);
  };

  const clearAll = () => {
    setSearchTerm("");
    setSelectedAgeGroups([]);
    setSelectedEvents([]);
    setAgeDropdownOpen(false);
    setEventDropdownOpen(false);
  };

  const resetAddForm = () => {
    setNewFirstName("");
    setNewLastName("");
    setNewDateOfBirth("");
    setNewHometown("");
    setNewTeam("");
    setNewEvents([]);
  };

  const closeAddModal = () => {
    setAddModalOpen(false);
    resetAddForm();
  };

  const handleAddAthlete = (
    event: React.FormEvent<HTMLFormElement>
  ) => {
    event.preventDefault();

    if (
      !newFirstName.trim() ||
      !newLastName.trim() ||
      !newDateOfBirth
    ) {
      return;
    }

    addAthlete({
      firstName: newFirstName.trim(),
      lastName: newLastName.trim(),
      dateOfBirth: newDateOfBirth,
      hometown: newHometown.trim(),
      team: newTeam.trim(),
      events: newEvents,
    });

    closeAddModal();
  };

  const filteredAthletes = athletes.filter(
    (athlete) => {
      const fullName =
        `${athlete.firstName} ${athlete.lastName}`.toLowerCase();

      const matchesSearch = fullName.includes(
        searchTerm.trim().toLowerCase()
      );

      const athleteAgeGroup = getAgeGroup(
        athlete.dateOfBirth
      );

      const matchesAgeGroup =
        selectedAgeGroups.length === 0 ||
        selectedAgeGroups.includes(athleteAgeGroup);

      const matchesEvent =
        selectedEvents.length === 0 ||
        selectedEvents.some((event) =>
          athlete.events.includes(event)
        );

      return (
        matchesSearch &&
        matchesAgeGroup &&
        matchesEvent
      );
    }
  );

  const hasActiveFilters =
    searchTerm.trim() !== "" ||
    selectedAgeGroups.length > 0 ||
    selectedEvents.length > 0;

  const activeFilterCount =
    selectedAgeGroups.length +
    selectedEvents.length;

  return (
    <div className="directory-page">
      <Header />

      <main className="directory-container">
        <section className="page-heading">
          <p className="page-eyebrow">
            ATHLETE MANAGEMENT
          </p>

          <p className="page-description">
            Search, edit and update
            Flying Angels athlete information.
          </p>
        </section>

        <section className="directory-panel">
          <div className="directory-toolbar">
            <div className="search-wrapper">
              <span className="search-icon">⌕</span>

              <input
                type="text"
                className="search-input"
                placeholder="Search by athlete name..."
                value={searchTerm}
                onChange={(event) =>
                  setSearchTerm(event.target.value)
                }
              />
            </div>

            <button
              className="filter-button"
              type="button"
              onClick={() =>
                setFiltersOpen(!filtersOpen)
              }
            >
              <span>
                Filters

                {activeFilterCount > 0 && (
                  <span className="filter-count">
                    {activeFilterCount}
                  </span>
                )}
              </span>

              <span
                className={`filter-arrow ${
                  filtersOpen ? "open" : ""
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

                    setEventDropdownOpen(false);
                  }}
                >
                  <span>
                    {selectedAgeGroups.length === 0
                      ? "All age groups"
                      : selectedAgeGroups.join(", ")}
                  </span>

                  <span className="dropdown-arrow">
                    {ageDropdownOpen ? "⌃" : "⌄"}
                  </span>
                </button>

                {ageDropdownOpen && (
                  <div className="multi-select-menu">
                    {[
                      "5-14",
                      "15-18",
                      "18+",
                    ].map((ageGroup) => {
                      const isSelected =
                        selectedAgeGroups.includes(
                          ageGroup
                        );

                      return (
                        <button
                          key={ageGroup}
                          type="button"
                          className={`multi-select-option ${
                            isSelected
                              ? "selected"
                              : ""
                          }`}
                          onClick={() =>
                            toggleAgeGroup(ageGroup)
                          }
                        >
                          <span>{ageGroup}</span>

                          <span className="option-check">
                            {isSelected ? "✓" : ""}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              <div className="multi-select">
                <span className="filter-label">
                  Event
                </span>

                <button
                  className="multi-select-button"
                  type="button"
                  onClick={() => {
                    setEventDropdownOpen(
                      !eventDropdownOpen
                    );

                    setAgeDropdownOpen(false);
                  }}
                >
                  <span>
                    {selectedEvents.length === 0
                      ? "All events"
                      : selectedEvents.length === 1
                        ? selectedEvents[0]
                        : `${selectedEvents.length} events selected`}
                  </span>

                  <span className="dropdown-arrow">
                    {eventDropdownOpen
                      ? "⌃"
                      : "⌄"}
                  </span>
                </button>

                {eventDropdownOpen && (
                  <div className="multi-select-menu event-select-menu">
                    {EVENT_OPTIONS.map((event) => {
                      const isSelected =
                        selectedEvents.includes(
                          event
                        );

                      return (
                        <button
                          key={event}
                          type="button"
                          className={`multi-select-option ${
                            isSelected
                              ? "selected"
                              : ""
                          }`}
                          onClick={() =>
                            toggleEvent(event)
                          }
                        >
                          <span>{event}</span>

                          <span className="option-check">
                            {isSelected ? "✓" : ""}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              {(selectedAgeGroups.length > 0 ||
                selectedEvents.length > 0) && (
                <button
                  className="clear-filters-button"
                  type="button"
                  onClick={clearFilters}
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
                {filteredAthletes.length}{" "}
                {filteredAthletes.length === 1
                  ? "athlete"
                  : "athletes"}{" "}
                found
              </span>

              {searchTerm.trim() !== "" && (
                <>
                  <span className="summary-divider">
                    •
                  </span>

                  <span>
                    Search: "{searchTerm}"
                  </span>
                </>
              )}

              {selectedAgeGroups.length > 0 && (
                <>
                  <span className="summary-divider">
                    •
                  </span>

                  <span>
                    {selectedAgeGroups.length}{" "}
                    {selectedAgeGroups.length === 1
                      ? "age group"
                      : "age groups"}
                  </span>
                </>
              )}

              {selectedEvents.length > 0 && (
                <>
                  <span className="summary-divider">
                    •
                  </span>

                  <span>
                    {selectedEvents.length}{" "}
                    {selectedEvents.length === 1
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
            <div className="list-heading">
              <span>Athlete</span>
              <span>Events</span>
              <span></span>
            </div>

            {filteredAthletes.map((athlete) => (
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
                    {athlete.firstName.charAt(0)}
                    {athlete.lastName.charAt(0)}
                  </div>

                  <div>
                    <span className="athlete-name">
                      {athlete.firstName}{" "}
                      {athlete.lastName}
                    </span>

                    <span className="athlete-id">
                      Athlete #{athlete.id}
                    </span>
                  </div>
                </div>

                <div className="event-list">
                  {athlete.events
                    .slice(0, 2)
                    .map((event) => (
                      <span
                        className="event-tag"
                        key={event}
                      >
                        {event}
                      </span>
                    ))}

                  {athlete.events.length > 2 && (
                    <span className="more-events">
                      +
                      {athlete.events.length - 2}
                    </span>
                  )}
                </div>

                <span className="row-arrow">
                  ›
                </span>
              </button>
            ))}

            {filteredAthletes.length === 0 && (
              <div className="no-results">
                <p>No athletes found.</p>

                <span>
                  Try changing your search or
                  filters.
                </span>
              </div>
            )}
          </div>
        </section>
      </main>

      {addModalOpen && (
        <div
          className="modal-overlay"
          onMouseDown={closeAddModal}
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

                <h2>Add Athlete</h2>
              </div>

              <button
                className="modal-close"
                type="button"
                onClick={closeAddModal}
                aria-label="Close"
              >
                ×
              </button>
            </div>

            <form
              className="athlete-form"
              onSubmit={handleAddAthlete}
            >
              <div className="form-grid">
                <div className="form-field">
                  <label htmlFor="first-name">
                    First Name *
                  </label>

                  <input
                    id="first-name"
                    type="text"
                    value={newFirstName}
                    onChange={(event) =>
                      setNewFirstName(
                        event.target.value
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
                    value={newLastName}
                    onChange={(event) =>
                      setNewLastName(
                        event.target.value
                      )
                    }
                    required
                  />
                </div>

                <div className="form-field">
                  <label htmlFor="date-of-birth">
                    Date of Birth *
                  </label>

                  <input
                    id="date-of-birth"
                    type="date"
                    value={newDateOfBirth}
                    onChange={(event) =>
                      setNewDateOfBirth(
                        event.target.value
                      )
                    }
                    required
                  />
                </div>

                <div className="form-field">
                  <label htmlFor="hometown">
                    Hometown
                  </label>

                  <input
                    id="hometown"
                    type="text"
                    value={newHometown}
                    onChange={(event) =>
                      setNewHometown(
                        event.target.value
                      )
                    }
                  />
                </div>

                <div className="form-field form-field-wide">
                  <label htmlFor="team">
                    Team
                  </label>

                  <input
                    id="team"
                    type="text"
                    value={newTeam}
                    onChange={(event) =>
                      setNewTeam(
                        event.target.value
                      )
                    }
                  />
                </div>
              </div>

              <div className="form-events">
                <span className="form-label">
                  Events
                </span>

                <div className="form-event-options">
                  {EVENT_OPTIONS.map((event) => {
                    const isSelected =
                      newEvents.includes(event);

                    return (
                      <button
                        key={event}
                        type="button"
                        className={`form-event-option ${
                          isSelected
                            ? "selected"
                            : ""
                        }`}
                        onClick={() =>
                          toggleNewAthleteEvent(
                            event
                          )
                        }
                      >
                        {event}

                        {isSelected && (
                          <span>✓</span>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="modal-actions">
                <button
                  className="cancel-button"
                  type="button"
                  onClick={closeAddModal}
                >
                  Cancel
                </button>

                <button
                  className="save-button"
                  type="submit"
                >
                  Add Athlete
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
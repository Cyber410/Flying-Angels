import { useState } from "react";
import {
  useNavigate,
  useParams,
} from "react-router-dom";
import Header from "../components/header";

import { EVENT_OPTIONS } from "../data/athleteOptions";
import { useAthletes } from "../context/AthleteContext";

import "./AthleteProfilePage.css";

function calculateAge(dateOfBirth: string) {
  const today = new Date();

  const birthDate = new Date(
    `${dateOfBirth}T00:00:00`
  );

  let age =
    today.getFullYear() -
    birthDate.getFullYear();

  const monthDifference =
    today.getMonth() -
    birthDate.getMonth();

  if (
    monthDifference < 0 ||
    (monthDifference === 0 &&
      today.getDate() <
        birthDate.getDate())
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

  return "Not assigned";
}

function AthleteProfilePage() {
  const { athleteId } = useParams();

  const navigate = useNavigate();

  const {
    athletes,
    updateAthlete,
    deleteAthlete,
  } = useAthletes();

  const athlete = athletes.find(
    (athlete) =>
      athlete.id === Number(athleteId)
  );

  const [isEditing, setIsEditing] =
    useState(false);

  const [deleteModalOpen, setDeleteModalOpen] =
    useState(false);

  const [editedHometown, setEditedHometown] =
    useState("");

  const [editedTeam, setEditedTeam] =
    useState("");

  const [editedEvents, setEditedEvents] =
    useState<string[]>([]);

  if (!athlete) {
    return (
      <div className="profile-page">
      <Header />

        <main className="profile-container">
          <button
            className="back-button"
            type="button"
            onClick={() => navigate("/")}
          >
            ← Back to Athlete Directory
          </button>

          <div className="profile-not-found">
            <h1>Athlete not found</h1>

            <p>
              The athlete you are looking for
              could not be found.
            </p>
          </div>
        </main>
      </div>
    );
  }

  const formattedDateOfBirth = new Date(
    `${athlete.dateOfBirth}T00:00:00`
  ).toLocaleDateString("en-CA", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  const ageGroup = getAgeGroup(
    athlete.dateOfBirth
  );

  const startEditing = () => {
    setEditedHometown(athlete.hometown);
    setEditedTeam(athlete.team);
    setEditedEvents([...athlete.events]);

    setIsEditing(true);
  };

  const cancelEditing = () => {
    setIsEditing(false);

    setEditedHometown("");
    setEditedTeam("");
    setEditedEvents([]);
  };

  const toggleEditedEvent = (event: string) => {
    setEditedEvents((currentEvents) =>
      currentEvents.includes(event)
        ? currentEvents.filter(
            (selectedEvent) =>
              selectedEvent !== event
          )
        : [...currentEvents, event]
    );
  };

  const saveChanges = () => {
    updateAthlete(athlete.id, {
      hometown: editedHometown.trim(),
      team: editedTeam.trim(),
      events: editedEvents,
    });

    setIsEditing(false);
  };

  const confirmDelete = () => {
    deleteAthlete(athlete.id);

    navigate("/");
  };

  return (
    <div className="profile-page">
<Header />

      <main className="profile-container">
        <button
          className="back-button"
          type="button"
          onClick={() => navigate("/")}
        >
          ← Back to Athlete Directory
        </button>

    <section className="profile-heading">
  <div className="profile-identity">
    <p className="profile-eyebrow">
      ATHLETE PROFILE
    </p>

    <h1>
      {athlete.firstName} {athlete.lastName}
    </h1>
  </div>

  {!isEditing ? (
    <button
      className="edit-athlete-button"
      type="button"
      onClick={startEditing}
    >
      Edit Athlete
    </button>
  ) : (
    <div className="edit-actions">
      <button
        className="profile-cancel-button"
        type="button"
        onClick={cancelEditing}
      >
        Cancel
      </button>

      <button
        className="profile-save-button"
        type="button"
        onClick={saveChanges}
      >
        Save Changes
      </button>
    </div>
  )}
</section>

        <section className="profile-panel">
          <div className="profile-section">
            <div className="profile-section-heading">
              <h2>
                Athlete Information
              </h2>
            </div>

            <div className="profile-details">
              <div className="profile-detail">
                <span className="detail-label">
                  First Name
                </span>

                <span className="detail-value">
                  {athlete.firstName}
                </span>
              </div>

              <div className="profile-detail">
                <span className="detail-label">
                  Last Name
                </span>

                <span className="detail-value">
                  {athlete.lastName}
                </span>
              </div>

              <div className="profile-detail">
                <span className="detail-label">
                  Athlete ID
                </span>

                <span className="detail-value">
                  {athlete.id}
                </span>
              </div>

              <div className="profile-detail">
                <span className="detail-label">
                  Date of Birth
                </span>

                <span className="detail-value">
                  {formattedDateOfBirth}
                </span>
              </div>

              <div className="profile-detail">
                <span className="detail-label">
                  Age Group
                </span>

                <span className="detail-value">
                  {ageGroup}
                </span>
              </div>

              <div className="profile-detail">
                <span className="detail-label">
                  Hometown
                </span>

                {isEditing ? (
                  <input
                    className="profile-input"
                    type="text"
                    value={editedHometown}
                    onChange={(event) =>
                      setEditedHometown(
                        event.target.value
                      )
                    }
                  />
                ) : (
                  <span className="detail-value">
                    {athlete.hometown ||
                      "Not provided"}
                  </span>
                )}
              </div>

              <div className="profile-detail profile-team-detail">
                <span className="detail-label">
                  Team
                </span>

                {isEditing ? (
                  <input
                    className="profile-input"
                    type="text"
                    value={editedTeam}
                    onChange={(event) =>
                      setEditedTeam(
                        event.target.value
                      )
                    }
                  />
                ) : (
                  <span className="detail-value">
                    {athlete.team ||
                      "Not provided"}
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="profile-section">
            <div className="profile-section-heading">
              <h2>Events</h2>
            </div>

            {!isEditing ? (
              <div className="profile-events">
                {athlete.events.length > 0 ? (
                  athlete.events.map((event) => (
                    <span
                      className="profile-event-tag"
                      key={event}
                    >
                      {event}
                    </span>
                  ))
                ) : (
                  <span className="empty-events">
                    No events assigned.
                  </span>
                )}
              </div>
            ) : (
              <div className="edit-event-area">
                {EVENT_OPTIONS.map((event) => {
                  const isSelected =
                    editedEvents.includes(event);

                  return (
                    <button
                      key={event}
                      type="button"
                      className={`edit-event-option ${
                        isSelected
                          ? "selected"
                          : ""
                      }`}
                      onClick={() =>
                        toggleEditedEvent(event)
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
            )}
          </div>
        </section>

        <section className="danger-zone">
          <div>
            <span className="danger-title">
              Delete Athlete
            </span>

            <span className="danger-description">
              Permanently remove this athlete
              from the directory.
            </span>
          </div>

          <button
            className="delete-athlete-button"
            type="button"
            onClick={() =>
              setDeleteModalOpen(true)
            }
          >
            Delete Athlete
          </button>
        </section>
      </main>

      {deleteModalOpen && (
        <div
          className="profile-modal-overlay"
          onMouseDown={() =>
            setDeleteModalOpen(false)
          }
        >
          <div
            className="delete-modal"
            onMouseDown={(event) =>
              event.stopPropagation()
            }
          >
            <h2>Delete athlete?</h2>

            <p>
              Are you sure you want to delete{" "}
              {athlete.firstName}{" "}
              {athlete.lastName}? This action
              cannot be undone.
            </p>

            <div className="delete-modal-actions">
              <button
                className="profile-cancel-button"
                type="button"
                onClick={() =>
                  setDeleteModalOpen(false)
                }
              >
                Cancel
              </button>

              <button
                className="confirm-delete-button"
                type="button"
                onClick={confirmDelete}
              >
                Delete Athlete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default AthleteProfilePage;
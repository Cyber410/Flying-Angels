import {
  useEffect,
  useState,
} from "react";
import {
  useNavigate,
  useParams,
} from "react-router-dom";
import Header from "../components/header";
import type { Athlete } from "../types/athlete";
import {
  deleteAthlete,
  getAthleteById,
  updateAthlete,
} from "../services/athleteService";
import "./AthleteProfilePage.css";
type EditableEvent = {
  eventName: string;
};
function calculateAge(
  dateOfBirth: string | null
) {
  if (!dateOfBirth) {
    return null;
  }
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
function displayValue(
  value?: string | null
) {
  return value?.trim()
    ? value
    : "Not provided";
}
function AthleteProfilePage() {
  const { athleteId } = useParams();
  const navigate = useNavigate();
  const [athlete, setAthlete] =
    useState<Athlete | null>(null);
  const [isLoading, setIsLoading] =
    useState(true);
  const [loadError, setLoadError] =
    useState("");
  /* ==================================================
     EDIT ATHLETE
     ================================================== */
  const [isEditing, setIsEditing] =
    useState(false);
  const [isSaving, setIsSaving] =
    useState(false);
  const [saveError, setSaveError] =
    useState("");
  const [
    editedGender,
    setEditedGender,
  ] = useState("");
  const [
    editedFlyStatus,
    setEditedFlyStatus,
  ] = useState("");
  const [
    editedFlyaStatus,
    setEditedFlyaStatus,
  ] = useState("");
  const [
    editedNote,
    setEditedNote,
  ] = useState("");
  const [
    editedEvents,
    setEditedEvents,
  ] = useState<EditableEvent[]>([]);
  /* ==================================================
     DELETE ATHLETE
     ================================================== */
  const [
    deleteModalOpen,
    setDeleteModalOpen,
  ] = useState(false);
  const [
    isDeleting,
    setIsDeleting,
  ] = useState(false);
  const [
    deleteError,
    setDeleteError,
  ] = useState("");
  /* ==================================================
     LOAD ATHLETE
     ================================================== */
  useEffect(() => {
    async function loadAthlete() {
      const id = Number(athleteId);
      if (
        !athleteId ||
        Number.isNaN(id)
      ) {
        setLoadError(
          "Invalid athlete ID."
        );
        setIsLoading(false);
        return;
      }
      try {
        setIsLoading(true);
        setLoadError("");
        const loadedAthlete =
          await getAthleteById(id);
        setAthlete(
          loadedAthlete
        );
      } catch (error) {
        console.error(error);
        setLoadError(
          "Unable to load athlete profile."
        );
      } finally {
        setIsLoading(false);
      }
    }
    loadAthlete();
  }, [athleteId]);
  /* ==================================================
     EDIT FUNCTIONS
     ================================================== */
  const startEditing = () => {
    if (!athlete) {
      return;
    }
    setEditedGender(
      athlete.gender ?? ""
    );
    setEditedFlyStatus(
      athlete.flyStatus ?? ""
    );
    setEditedFlyaStatus(
      athlete.flyaStatus ?? ""
    );
    setEditedNote(
      athlete.note ?? ""
    );
    setEditedEvents(
      (athlete.events ?? []).map(
        (event) => ({
          eventName:
            event.categoryName,
        })
      )
    );
    setSaveError("");
    setIsEditing(true);
  };
  const cancelEditing = () => {
    setIsEditing(false);
    setSaveError("");
    setEditedEvents([]);
  };
  const saveChanges =
    async () => {
      if (!athlete) {
        return;
      }
      try {
        setIsSaving(true);
        setSaveError("");
        await updateAthlete(
          athlete.id,
          {
            firstName:
              athlete.firstName,
            lastName:
              athlete.lastName,
            dateOfBirth:
              athlete.dateOfBirth,
            gender:
              editedGender.trim(),
            flyStatus:
              editedFlyStatus.trim(),
            flyaStatus:
              editedFlyaStatus.trim(),
            note:
              editedNote.trim(),
            events: editedEvents.map((event) => {
              const existingEvent = athlete.events?.find(
                (currentEvent) =>
                  currentEvent.categoryName === event.eventName
              );

              return {
                eventName: event.eventName,
                result: existingEvent?.result ?? "",
              };
            }),
          }
        );
        /*
         * Refresh the profile after the PUT
         * so the event-result data is loaded
         * again from the profile endpoint.
         */
        const refreshedAthlete =
          await getAthleteById(
            athlete.id
          );
        setAthlete(
          refreshedAthlete
        );
        setIsEditing(false);
        setEditedEvents([]);
      } catch (error) {
        console.error(error);
        setSaveError(
          "Unable to save athlete changes."
        );
      } finally {
        setIsSaving(false);
      }
    };
  /* ==================================================
     DELETE FUNCTIONS
     ================================================== */
  const openDeleteModal = () => {
    setDeleteError("");
    setDeleteModalOpen(true);
  };
  const closeDeleteModal = () => {
    if (isDeleting) {
      return;
    }
    setDeleteError("");
    setDeleteModalOpen(false);
  };
  const handleDeleteAthlete =
    async () => {
      if (
        !athlete ||
        isDeleting
      ) {
        return;
      }
      try {
        setIsDeleting(true);
        setDeleteError("");
        await deleteAthlete(
          athlete.id
        );
        navigate("/");
      } catch (error) {
        console.error(error);
        setDeleteError(
          "Unable to delete this athlete. Please try again."
        );
        setIsDeleting(false);
      }
    };
  /* ==================================================
     LOADING
     ================================================== */
  if (isLoading) {
    return (
      <div className="profile-page">
        <Header />
        <main className="profile-container">
          <button
            className="back-button"
            type="button"
            onClick={() =>
              navigate("/")
            }
          >
            ← Back to Athlete
            Directory
          </button>
          <section className="profile-panel">
            <div className="profile-not-found">
              <h1>
                Loading athlete...
              </h1>
              <p>
                Retrieving athlete
                information.
              </p>
            </div>
          </section>
        </main>
      </div>
    );
  }
  /* ==================================================
     NOT FOUND / ERROR
     ================================================== */
  if (loadError || !athlete) {
    return (
      <div className="profile-page">
        <Header />
        <main className="profile-container">
          <button
            className="back-button"
            type="button"
            onClick={() =>
              navigate("/")
            }
          >
            ← Back to Athlete
            Directory
          </button>
          <div className="profile-not-found">
            <h1>
              Athlete not found
            </h1>
            <p>
              {loadError ||
                "The athlete you are looking for could not be found."}
            </p>
          </div>
        </main>
      </div>
    );
  }
  const formattedDateOfBirth =
    athlete.dateOfBirth
      ? new Date(
          `${athlete.dateOfBirth}T00:00:00`
        ).toLocaleDateString(
          "en-CA",
          {
            year: "numeric",
            month: "long",
            day: "numeric",
          }
        )
      : "Not provided";
  const age = calculateAge(
    athlete.dateOfBirth
  );
  return (
    <div className="profile-page">
      <Header />
      <main className="profile-container">
        <button
          className="back-button"
          type="button"
          onClick={() =>
            navigate("/")
          }
        >
          ← Back to Athlete
          Directory
        </button>
        <section className="profile-heading">
          <div className="profile-identity">
            <p className="profile-eyebrow">
              ATHLETE PROFILE
            </p>
            <h1>
              {athlete.firstName}{" "}
              {athlete.lastName}
            </h1>
          </div>
          {!isEditing ? (
            <button
              className="edit-athlete-button"
              type="button"
              onClick={
                startEditing
              }
            >
              Edit Athlete
            </button>
          ) : (
            <div className="edit-actions">
              <button
                className="profile-cancel-button"
                type="button"
                onClick={
                  cancelEditing
                }
                disabled={
                  isSaving
                }
              >
                Cancel
              </button>
              <button
                className="profile-save-button"
                type="button"
                onClick={
                  saveChanges
                }
                disabled={
                  isSaving
                }
              >
                {isSaving
                  ? "Saving..."
                  : "Save Changes"}
              </button>
            </div>
          )}
        </section>
        <section className="profile-panel">
          {/* ==================================================
              ATHLETE INFORMATION
              ================================================== */}
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
                  {
                    athlete.firstName
                  }
                </span>
              </div>
              <div className="profile-detail">
                <span className="detail-label">
                  Last Name
                </span>
                <span className="detail-value">
                  {
                    athlete.lastName
                  }
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
                  {
                    formattedDateOfBirth
                  }
                </span>
              </div>
              <div className="profile-detail">
                <span className="detail-label">
                  Age
                </span>
                <span className="detail-value">
                  {age !== null
                    ? `${age}`
                    : "Not provided"}
                </span>
              </div>
              <div className="profile-detail">
                <span className="detail-label">
                  Gender
                </span>
                {isEditing ? (
                  <input
                    className="profile-input"
                    type="text"
                    value={
                      editedGender
                    }
                    onChange={(
                      event
                    ) =>
                      setEditedGender(
                        event.target
                          .value
                      )
                    }
                  />
                ) : (
                  <span className="detail-value">
                    {displayValue(
                      athlete.gender
                    )}
                  </span>
                )}
              </div>
              <div className="profile-detail">
                <span className="detail-label">
                  FLY Status
                </span>
                {isEditing ? (
                  <input
                    className="profile-input"
                    type="text"
                    value={
                      editedFlyStatus
                    }
                    onChange={(
                      event
                    ) =>
                      setEditedFlyStatus(
                        event.target
                          .value
                      )
                    }
                  />
                ) : (
                  <span className="detail-value">
                    {displayValue(
                      athlete.flyStatus
                    )}
                  </span>
                )}
              </div>
              <div className="profile-detail">
                <span className="detail-label">
                  FLYA Status
                </span>
                {isEditing ? (
                  <input
                    className="profile-input"
                    type="text"
                    value={
                      editedFlyaStatus
                    }
                    onChange={(
                      event
                    ) =>
                      setEditedFlyaStatus(
                        event.target
                          .value
                      )
                    }
                  />
                ) : (
                  <span className="detail-value">
                    {displayValue(
                      athlete.flyaStatus
                    )}
                  </span>
                )}
              </div>
              <div className="profile-detail profile-team-detail">
                <span className="detail-label">
                  Note
                </span>
                {isEditing ? (
                  <textarea
                    className="profile-textarea"
                    value={
                      editedNote
                    }
                    onChange={(
                      event
                    ) =>
                      setEditedNote(
                        event.target
                          .value
                      )
                    }
                    rows={4}
                  />
                ) : (
                  <span className="detail-value">
                    {displayValue(
                      athlete.note
                    )}
                  </span>
                )}
              </div>
            </div>
            {saveError && (
              <div className="profile-save-error">
                {saveError}
              </div>
            )}
          </div>
          {/* ==================================================
              EVENT RESULTS
              ================================================== */}
          <div className="profile-section">
            <div className="profile-section-heading">
              <h2>
                Event Results
              </h2>
            </div>
            {athlete.events &&
            athlete.events.length >
              0 ? (
              <div className="event-results-table">
                <div className="event-results-header">
                  <span>Event</span>
                  <span>Score</span>
                </div>

                {athlete.events.map((event) => (
                  <div
                    className="event-result-row"
                    key={event.resultId}
                  >
                    <span>
                      {displayValue(event.categoryName)}
                    </span>

                    <span>
                      {event.score ??
                        "Not provided"}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="profile-events">
                <span className="empty-events">
                  No event results are
                  available for this
                  athlete.
                </span>
              </div>
            )}
          </div>
          {/* ==================================================
              DELETE ATHLETE
              ================================================== */}
          <div className="profile-section danger-zone">
            <div className="profile-section-heading">
              <h2>
                Delete Athlete
              </h2>
            </div>
            <div className="danger-zone-content">
              <div>
                <p className="danger-zone-title">
                  Permanently delete
                  this athlete
                </p>
                <p className="danger-zone-description">
                  Remove this athlete
                  and their information
                  from the system.
                </p>
              </div>
              <button
                className="delete-athlete-button"
                type="button"
                onClick={
                  openDeleteModal
                }
                disabled={
                  isEditing
                }
              >
                Delete Athlete
              </button>
            </div>
          </div>
        </section>
      </main>
      {/* ==================================================
          DELETE CONFIRMATION MODAL
          ================================================== */}
      {deleteModalOpen && (
        <div
          className="profile-modal-overlay"
          onMouseDown={
            closeDeleteModal
          }
        >
          <div
            className="delete-modal"
            onMouseDown={(event) =>
              event.stopPropagation()
            }
          >
            <p className="delete-modal-eyebrow">
              ATHLETE MANAGEMENT
            </p>
            <h2>
              Delete Athlete?
            </h2>
            <p className="delete-modal-message">
              Are you sure you want
              to delete{" "}
              <strong>
                {
                  athlete.firstName
                }{" "}
                {
                  athlete.lastName
                }
              </strong>
              ? This action cannot be
              undone.
            </p>
            {deleteError && (
              <div className="profile-save-error">
                {deleteError}
              </div>
            )}
            <div className="delete-modal-actions">
              <button
                className="profile-cancel-button"
                type="button"
                onClick={
                  closeDeleteModal
                }
                disabled={
                  isDeleting
                }
              >
                Cancel
              </button>
              <button
                className="confirm-delete-button"
                type="button"
                onClick={
                  handleDeleteAthlete
                }
                disabled={
                  isDeleting
                }
              >
                {isDeleting
                  ? "Deleting..."
                  : "Delete Athlete"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
export default AthleteProfilePage;

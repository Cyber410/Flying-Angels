import {
  useEffect,
  useState,
} from "react";
import type { KeyboardEvent } from "react";
import {
  useNavigate,
  useParams,
} from "react-router-dom";
import Header from "../components/header";
import AthleteMeetHistory from "../components/AthleteMeetHistory";
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
type ProfileTab = "overview" | "history" | "notes";
const PROFILE_TABS: { id: ProfileTab; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "history", label: "Meets & Results" },
  { id: "notes", label: "Notes" },
];
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
  const [activeTab, setActiveTab] = useState<ProfileTab>("overview");
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
  const startEditing = (tab: ProfileTab = "overview") => {
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
    setActiveTab(tab);
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
  function handleTabKey(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    let nextIndex = index;
    if (event.key === "ArrowRight") nextIndex = (index + 1) % PROFILE_TABS.length;
    else if (event.key === "ArrowLeft") nextIndex = (index + PROFILE_TABS.length - 1) % PROFILE_TABS.length;
    else if (event.key === "Home") nextIndex = 0;
    else if (event.key === "End") nextIndex = PROFILE_TABS.length - 1;
    else return;
    event.preventDefault();
    const next = PROFILE_TABS[nextIndex].id;
    setActiveTab(next);
    document.getElementById(`profile-tab-${next}`)?.focus();
  }
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
  const formattedDateOfBirth = athlete.dateOfBirth
    ? new Date(`${athlete.dateOfBirth}T00:00:00`).toLocaleDateString("en-CA", { year: "numeric", month: "long", day: "numeric" })
    : "Not provided";
  const age = calculateAge(athlete.dateOfBirth);
  const ageGroup = age === null ? "Not provided" : age < 5 ? "Under 5" : age <= 14 ? "5-14" : age <= 18 ? "15-18" : "18+";
  const personalDetails = [
    { label: "First Name", value: athlete.firstName },
    { label: "Last Name", value: athlete.lastName },
    { label: "Athlete ID", value: String(athlete.id) },
    { label: "Date of Birth", value: formattedDateOfBirth },
    { label: "Age", value: age !== null ? String(age) : "Not provided" },
    { label: "Age Group", value: ageGroup },
  ];

  return (
    <div className="profile-page">
      <Header />
      <main className="profile-container">
        <button className="back-button" type="button" onClick={() => navigate("/")}>
          ← Back to Athlete Directory
        </button>

        <section className="profile-heading" aria-label="Athlete summary">
          <div className="profile-identity">
            <p className="profile-eyebrow">ATHLETE PROFILE</p>
            <h1>{athlete.firstName} {athlete.lastName}</h1>
            <p className="profile-identity-meta">
              Athlete #{athlete.id}
              {age !== null && <> · {age} years</>}
              {athlete.gender?.trim() && <> · {athlete.gender}</>}
            </p>
            {(athlete.flyStatus?.trim() || athlete.flyaStatus?.trim()) && (
              <div className="profile-status-list">
                {athlete.flyStatus?.trim() && <span className="profile-status-pill"><span>FLY</span>{athlete.flyStatus}</span>}
                {athlete.flyaStatus?.trim() && <span className="profile-status-pill"><span>FLYA</span>{athlete.flyaStatus}</span>}
              </div>
            )}
          </div>
          {!isEditing ? (
            <button className="edit-athlete-button" type="button" onClick={() => startEditing()}>Edit Athlete</button>
          ) : (
            <div className="edit-actions">
              <button className="profile-cancel-button" type="button" onClick={cancelEditing} disabled={isSaving}>Cancel</button>
              <button className="profile-save-button" type="button" onClick={saveChanges} disabled={isSaving}>{isSaving ? "Saving..." : "Save Changes"}</button>
            </div>
          )}
        </section>

        {isEditing && <p className="profile-edit-hint">Edit details in Overview or the general note in Notes, then save your changes.</p>}
        {saveError && <div className="profile-save-error profile-page-error" role="alert">{saveError}</div>}

        <section className="profile-panel">
          <div className="profile-tabs" role="tablist" aria-label="Athlete profile sections">
            {PROFILE_TABS.map((tab, index) => (
              <button
                key={tab.id}
                id={`profile-tab-${tab.id}`}
                role="tab"
                type="button"
                className={`profile-tab${activeTab === tab.id ? " active" : ""}`}
                aria-selected={activeTab === tab.id}
                aria-controls={`profile-panel-${tab.id}`}
                tabIndex={activeTab === tab.id ? 0 : -1}
                disabled={isSaving}
                onClick={() => setActiveTab(tab.id)}
                onKeyDown={(event) => handleTabKey(event, index)}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {activeTab !== "overview" && (
            <details className="profile-storage-note">
              <summary>Some data is saved only in this browser</summary>
              <p>The best result and general athlete note are shared. Extra attempts, pending assignments, and performance notes stay in this browser and are removed if its data is cleared.</p>
            </details>
          )}

          <section
            id="profile-panel-overview"
            role="tabpanel"
            aria-labelledby="profile-tab-overview"
            className="profile-tab-content profile-overview"
            hidden={activeTab !== "overview"}
            tabIndex={0}
          >
            <div className="profile-tab-heading">
              <h2>Athlete Information</h2>
              <p>Personal details and academy membership.</p>
            </div>
            <dl className="profile-details">
              {personalDetails.map((detail) => (
                <div className="profile-detail" key={detail.label}>
                  <dt className="detail-label">{detail.label}</dt>
                  <dd className="detail-value">{detail.value}</dd>
                </div>
              ))}
              <div className="profile-detail">
                <dt className="detail-label"><label htmlFor="profile-gender">Gender</label></dt>
                <dd className="detail-value">
                  {isEditing ? <input id="profile-gender" className="profile-input" value={editedGender} disabled={isSaving} onChange={(event) => setEditedGender(event.target.value)} /> : displayValue(athlete.gender)}
                </dd>
              </div>
              <div className="profile-detail">
                <dt className="detail-label"><label htmlFor="profile-fly-status">FLY Status</label></dt>
                <dd className="detail-value">
                  {isEditing ? <input id="profile-fly-status" className="profile-input" value={editedFlyStatus} disabled={isSaving} onChange={(event) => setEditedFlyStatus(event.target.value)} /> : displayValue(athlete.flyStatus)}
                </dd>
              </div>
              <div className="profile-detail">
                <dt className="detail-label"><label htmlFor="profile-flya-status">FLYA Status</label></dt>
                <dd className="detail-value">
                  {isEditing ? <input id="profile-flya-status" className="profile-input" value={editedFlyaStatus} disabled={isSaving} onChange={(event) => setEditedFlyaStatus(event.target.value)} /> : displayValue(athlete.flyaStatus)}
                </dd>
              </div>
            </dl>
            <details className="profile-manage">
              <summary>Manage athlete</summary>
              <div className="danger-zone-content">
                <div>
                  <p className="danger-zone-title">Permanently delete this athlete</p>
                  <p className="danger-zone-description">Remove this athlete and their information from the system.</p>
                </div>
                <button className="delete-athlete-button" type="button" onClick={openDeleteModal} disabled={isEditing}>Delete Athlete</button>
              </div>
            </details>
          </section>

          <AthleteMeetHistory
            key={athlete.id}
            athlete={athlete}
            readOnly={isEditing}
            view={activeTab === "overview" ? "hidden" : activeTab}
            onShowHistory={() => {
              setActiveTab("history");
              document.getElementById("profile-tab-history")?.focus();
            }}
            generalNote={
              <section className="profile-general-note" aria-labelledby="profile-general-note-heading">
                <div className="profile-tab-heading profile-note-heading">
                  <div>
                    <h2 id="profile-general-note-heading">General athlete note</h2>
                    <p>Shared with your academy team.</p>
                  </div>
                  {!isEditing && <button className="ath-history-button" type="button" aria-label={athlete.note?.trim() ? "Edit general athlete note" : "Add general athlete note"} onClick={() => startEditing("notes")}>{athlete.note?.trim() ? "Edit Note" : "Add Note"}</button>}
                </div>
                <div className="profile-note-field">
                  <label className="detail-label" htmlFor="profile-general-note-input">Note</label>
                  {isEditing ? (
                    <textarea id="profile-general-note-input" className="profile-textarea" value={editedNote} disabled={isSaving} maxLength={500} onChange={(event) => setEditedNote(event.target.value)} rows={4} placeholder="Add useful information about this athlete..." />
                  ) : (
                    <p className={`profile-note-text${athlete.note?.trim() ? "" : " profile-note-empty"}`}>{athlete.note?.trim() || "No general note yet."}</p>
                  )}
                </div>
              </section>
            }
          />
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

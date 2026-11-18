import { useOptionalAuth } from "../auth/authContext";
// Athlete details, meet history, and notes, with existing edit/delete actions.
import {
  useEffect,
  useState,
} from "react";
import type { KeyboardEvent } from "react";
import {
  useNavigate,
  useLocation,
  useParams,
} from "react-router-dom";
import Header from "../components/header";
import AthleteMeetHistory from "../components/AthleteMeetHistory";
import AthletePersonalBests from "../components/AthletePersonalBests";
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
type ProfileTab = "overview" | "history" | "bests" | "notes";
const PROFILE_TABS: { id: ProfileTab; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "history", label: "Meets & Results" },
  { id: "bests", label: "Personal Bests" },
  { id: "notes", label: "Notes" },
];
// Calculate whole-year age from the supplied birth date.
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
// Show a consistent fallback when a profile field is missing.
function displayValue(
  value?: string | null
) {
  return value?.trim()
    ? value
    : "Not provided";
}
// Display athlete details, history, personal bests, and staff notes with permitted edit actions.
function AthleteProfilePage() {
  const auth = useOptionalAuth();
  const canManage = auth ? auth.can("MANAGE_ATHLETES") : true;
  const { athleteId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const openEditor = location.state?.editAthlete === true;
  const [activeTab, setActiveTab] = useState<ProfileTab>(/* Compute the initial state when this component mounts. */ () => new URLSearchParams(location.search).get("tab") === "personal-bests" ? "bests" : "overview");
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
  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    // Fetch the athlete identified by the current profile route.
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
        if (openEditor && canManage) {
          setEditedGender(loadedAthlete.gender ?? "");
          setEditedFlyStatus(loadedAthlete.flyStatus ?? "");
          setEditedFlyaStatus(loadedAthlete.flyaStatus ?? "");
          setEditedNote(loadedAthlete.note ?? "");
          setEditedEvents((loadedAthlete.events ?? []).map(/* Convert each event into the required display or request data. */ event => ({eventName: event.categoryName})));
          setIsEditing(true);
        }
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
  }, [athleteId, openEditor, canManage]);
  /* ==================================================
     EDIT FUNCTIONS
     ================================================== */
  // Copy the current profile into editable fields and open the requested tab.
  const startEditing = (tab: ProfileTab = "overview") => {
    if (!canManage) return;
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
        /* Convert each event into the required display or request data. */ (event) => ({
          eventName:
            event.categoryName,
        })
      )
    );
    setSaveError("");
    setActiveTab(tab);
    setIsEditing(true);
  };
  // Leave edit mode and clear unsaved validation feedback.
  const cancelEditing = () => {
    setIsEditing(false);
    setSaveError("");
    setEditedEvents([]);
  };
  // Validate and persist the edited athlete details.
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
            events: editedEvents.map(/* Convert each event into the required display or request data. */ (event) => {
              const existingEvent = athlete.events?.find(
                /* Find the current event matching the requested identifier or value. */ (currentEvent) =>
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
  // Open the athlete deletion confirmation.
  const openDeleteModal = () => {
    setDeleteError("");
    setDeleteModalOpen(true);
  };
  // Close deletion confirmation and clear its previous error.
  const closeDeleteModal = () => {
    if (isDeleting) {
      return;
    }
    setDeleteError("");
    setDeleteModalOpen(false);
  };
  // Delete the confirmed athlete and return to the directory.
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
  // Move profile-tab focus using the supported keyboard navigation keys.
  function handleTabKey(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    let nextIndex: number;
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
            onClick={/* Perform the selected button action. */ () =>
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
            onClick={/* Perform the selected button action. */ () =>
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
        <button className="back-button" type="button" onClick={/* Perform the selected button action. */ () => navigate("/")}>
          ← Back to Athlete Directory
        </button>

        <section className="profile-heading" aria-label="Athlete summary">
          <div className="profile-identity">
            <p className="profile-eyebrow">ATHLETE PROFILE</p>
            <h1>{athlete.firstName} {athlete.lastName}</h1>
            {athlete.archived && <p className="profile-archive-notice">Archived athlete · History is preserved. Restore from the Directory to include in new events.</p>}
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
            <button hidden={!canManage} className="edit-athlete-button" type="button" onClick={/* Perform the selected button action. */ () => startEditing()}>Edit Athlete</button>
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
            {PROFILE_TABS.map(/* Render each tab in the list. */ (tab, index) => (
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
                onClick={/* Update active tab for the selected action. */ () => setActiveTab(tab.id)}
                onKeyDown={/* Handle keyboard navigation for this control. */ (event) => handleTabKey(event, index)}
              >
                {tab.label}
              </button>
            ))}
          </div>


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
              {personalDetails.map(/* Render each detail in the list. */ (detail) => (
                <div className="profile-detail" key={detail.label}>
                  <dt className="detail-label">{detail.label}</dt>
                  <dd className="detail-value">{detail.value}</dd>
                </div>
              ))}
              <div className="profile-detail">
                <dt className="detail-label"><label htmlFor="profile-gender">Gender</label></dt>
                <dd className="detail-value">
                  {isEditing ? <input id="profile-gender" className="profile-input" value={editedGender} disabled={isSaving} onChange={/* Update the edited gender value from this control. */ (event) => setEditedGender(event.target.value)} /> : displayValue(athlete.gender)}
                </dd>
              </div>
              <div className="profile-detail">
                <dt className="detail-label"><label htmlFor="profile-fly-status">FLY Status</label></dt>
                <dd className="detail-value">
                  {isEditing ? <input id="profile-fly-status" className="profile-input" value={editedFlyStatus} disabled={isSaving} onChange={/* Update the edited fly status value from this control. */ (event) => setEditedFlyStatus(event.target.value)} /> : displayValue(athlete.flyStatus)}
                </dd>
              </div>
              <div className="profile-detail">
                <dt className="detail-label"><label htmlFor="profile-flya-status">FLYA Status</label></dt>
                <dd className="detail-value">
                  {isEditing ? <input id="profile-flya-status" className="profile-input" value={editedFlyaStatus} disabled={isSaving} onChange={/* Update the edited flya status value from this control. */ (event) => setEditedFlyaStatus(event.target.value)} /> : displayValue(athlete.flyaStatus)}
                </dd>
              </div>
            </dl>
            <details hidden={!canManage} className="profile-manage">
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

          <section id="profile-panel-bests" role="tabpanel" aria-labelledby="profile-tab-bests" hidden={activeTab !== "bests"} tabIndex={0}>
            {activeTab === "bests" && <AthletePersonalBests key={athlete.id} athleteId={athlete.id} />}
          </section>

          <AthleteMeetHistory
            key={athlete.id}
            athlete={athlete}
            readOnly={isEditing}
            view={activeTab === "history" || activeTab === "notes" ? activeTab : "hidden"}
            onShowHistory={/* Switch the profile to its meet-history view. */ () => {
              setActiveTab("history");
              document.getElementById("profile-tab-history")?.focus();
            }}
            generalNote={
              <section className="profile-general-note" aria-labelledby="profile-general-note-heading">
                <div className="profile-tab-heading profile-note-heading">
                  <div>
                    <span className="profile-note-kind">Athlete-wide</span>
                    <h2 id="profile-general-note-heading">Athlete note</h2>
                    <p>General coaching context, training preferences, and reminders about this athlete.</p>
                  </div>
                  {!isEditing && <button hidden={!canManage} className="ath-history-button" type="button" aria-label={athlete.note?.trim() ? "Edit general athlete note" : "Add general athlete note"} onClick={/* Perform the selected button action. */ () => startEditing("notes")}>{athlete.note?.trim() ? "Edit athlete note" : "Add athlete note"}</button>}
                </div>
                <div className="profile-note-field">
                  {isEditing && <label className="detail-label" htmlFor="profile-general-note-input">Athlete note</label>}
                  {isEditing ? (
                    <textarea id="profile-general-note-input" className="profile-textarea" value={editedNote} disabled={isSaving} maxLength={500} onChange={/* Update the edited note value from this control. */ (event) => setEditedNote(event.target.value)} rows={4} placeholder="For example: Prefers afternoon training. Focus on building confidence at the start." />
                  ) : (
                    <p className={`profile-note-text${athlete.note?.trim() ? "" : " profile-note-empty"}`}>{athlete.note?.trim() || "No athlete note yet. Add a reminder that applies across training and competitions."}</p>
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
            onMouseDown={/* Handle the mouse down interaction. */ (event) =>
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

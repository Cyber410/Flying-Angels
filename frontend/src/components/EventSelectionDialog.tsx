import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import type { EventCategory, MeetEvent, RankingOrder } from "../types/event";
import type { Meet } from "../types/meet";
import { createEvent, getEventCategories } from "../services/eventService";
import { getMeetById, getMeets } from "../services/meetService";
import MeetDialog from "./MeetDialog";

type Choice = {
  id: number;
  name: string;
  categoryId: number | null;
  rankingOrder: RankingOrder;
  eventType: string | null;
};

type Props = {
  mode: "add" | "copy";
  meet: Meet;
  existingEvents: MeetEvent[];
  onCreated: (events: MeetEvent[]) => void;
  onClose: () => void;
};

export default function EventSelectionDialog({ mode, meet, existingEvents, onCreated, onClose }: Props) {
  const [categories, setCategories] = useState<EventCategory[]>([]);
  const [sourceMeets, setSourceMeets] = useState<Meet[]>([]);
  const [sourceId, setSourceId] = useState("");
  const [sourceEvents, setSourceEvents] = useState<MeetEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSourceLoading, setIsSourceLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [revision, setRevision] = useState(0);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<number[]>([]);
  const [completed, setCompleted] = useState<number[]>([]);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [customChoices, setCustomChoices] = useState<Choice[]>([]);
  const [newName, setNewName] = useState("");
  const [newRanking, setNewRanking] = useState<RankingOrder>("ASC");

  useEffect(() => {
    let ignore = false;
    async function load() {
      try {
        if (mode === "add") {
          const data = await getEventCategories();
          if (!ignore) setCategories(data);
        } else {
          const data = await getMeets();
          if (!ignore) setSourceMeets(data.filter((item) => item.id !== meet.id));
        }
      } catch (error) {
        if (!ignore) setLoadError(error instanceof Error ? error.message : "Unable to load events.");
      } finally {
        if (!ignore) setIsLoading(false);
      }
    }
    void load();
    return () => { ignore = true; };
  }, [mode, meet.id, revision]);

  useEffect(() => {
    if (mode !== "copy" || !sourceId) return;
    let ignore = false;
    getMeetById(Number(sourceId)).then((data) => {
      if (!ignore) setSourceEvents(data.events);
    }).catch((error: unknown) => {
      if (!ignore) setLoadError(error instanceof Error ? error.message : "Unable to load source events.");
    }).finally(() => {
      if (!ignore) setIsSourceLoading(false);
    });
    return () => { ignore = true; };
  }, [mode, sourceId, revision]);

  const choices: Choice[] = mode === "add"
    ? [...categories.map((category) => ({
      id: category.id, name: category.name, categoryId: category.id,
      rankingOrder: category.rankingOrder, eventType: null,
    })), ...customChoices]
    : sourceEvents.map((event) => ({
      id: event.id, name: event.name, categoryId: event.category.id,
      rankingOrder: event.rankingOrder, eventType: event.eventType,
    }));

  function alreadyAdded(choice: Choice) {
    return completed.includes(choice.id) || existingEvents.some((event) =>
      event.name.trim().toLowerCase() === choice.name.trim().toLowerCase() &&
      (choice.categoryId === null || event.category.id === choice.categoryId)
    );
  }

  const selectedChoices = choices.filter((choice) => selected.includes(choice.id) && !alreadyAdded(choice));
  const visibleChoices = choices.filter((choice) => choice.name.toLowerCase().includes(search.trim().toLowerCase()));
  const loading = isLoading || isSourceLoading;

  async function saveSelected() {
    if (isSaving || selectedChoices.length === 0) return;
    setIsSaving(true);
    setSaveError("");
    const created: MeetEvent[] = [];
    const successfulIds: number[] = [];
    const failures: string[] = [];
    // Existing API creates one event at a time. Preserve successes on partial failure.
    for (const choice of selectedChoices) {
      try {
        created.push(await createEvent({
          name: choice.name,
          eventDate: meet.meetDate,
          location: meet.location,
          eventType: choice.eventType,
          externalUrl: null,
          rankingOrder: choice.rankingOrder,
          meetId: meet.id,
          categoryId: choice.categoryId,
          categoryName: choice.categoryId === null ? choice.name : null,
        }));
        successfulIds.push(choice.id);
      } catch (error) {
        failures.push(`${choice.name}: ${error instanceof Error ? error.message : "Could not create event."}`);
      }
    }
    if (created.length) onCreated(created);
    setCompleted((current) => [...current, ...successfulIds]);
    setSelected((current) => current.filter((id) => !successfulIds.includes(id)));
    setIsSaving(false);
    if (failures.length) {
      setSaveError(`${created.length} of ${selectedChoices.length} events added. ${failures.join(" ")}`);
    } else {
      onClose();
    }
  }

  function addCustomChoice(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = newName.trim();
    if (!name) return;
    if (choices.some((choice) => choice.name.toLowerCase() === name.toLowerCase())) {
      setSaveError("That event type is already listed. Select it from the list.");
      return;
    }
    const choice: Choice = {
      id: -(customChoices.length + 1), name, categoryId: null,
      rankingOrder: newRanking, eventType: null,
    };
    setCustomChoices((current) => [...current, choice]);
    setSelected((current) => [...current, choice.id]);
    setNewName("");
    setSearch("");
    setSaveError("");
  }

  return (
    <MeetDialog title={mode === "copy" ? "Copy Events from Existing Meet" : "Add Events"} busy={isSaving} onClose={onClose}>
      <div className="meet-form">
        <p className="meet-dialog-message">
          {mode === "copy"
            ? "Copy event types into this meet. Athletes and results are not copied."
            : "Search and select the events to add to this meet."}
        </p>
        {mode === "copy" && (
          <label className="meet-form-field">
            Source meet
            <select
              value={sourceId}
              disabled={isSaving || isLoading}
              onChange={(event) => {
                setSourceId(event.target.value);
                setSourceEvents([]);
                setSelected([]);
                setCompleted([]);
                setSearch("");
                setSaveError("");
                setLoadError("");
                setIsSourceLoading(Boolean(event.target.value));
              }}
            >
              <option value="">Select a meet...</option>
              {sourceMeets.map((source) => <option key={source.id} value={source.id}>{source.name} ({source.meetDate})</option>)}
            </select>
          </label>
        )}
        <label className="meet-form-field">
          Search events
          <input type="search" placeholder="Search events..." value={search} disabled={isSaving} onChange={(event) => setSearch(event.target.value)} />
        </label>
        {loading ? <p role="status">Loading events...</p> : loadError ? (
          <div>
            <p className="meet-dialog-error" role="alert">{loadError}</p>
            <button className="meets-button" type="button" onClick={() => { setLoadError(""); setIsLoading(true); setIsSourceLoading(Boolean(sourceId)); setRevision((value) => value + 1); }}>
              Try again
            </button>
          </div>
        ) : (
          <fieldset className="meet-choice-list" disabled={isSaving}>
            <legend className="meets-sr-only">Available events</legend>
            {visibleChoices.map((choice) => (
              <label className="meet-choice" key={choice.id}>
                <input
                  type="checkbox"
                  checked={selected.includes(choice.id)}
                  disabled={alreadyAdded(choice)}
                  onChange={(event) => setSelected((current) => event.target.checked ? [...current, choice.id] : current.filter((id) => id !== choice.id))}
                />
                <span>{choice.name}{alreadyAdded(choice) && <small>Already in this meet</small>}</span>
              </label>
            ))}
            {visibleChoices.length === 0 && (
              <p className="meet-choice-empty">
                {mode === "copy" && !sourceId
                  ? sourceMeets.length ? "Choose a source meet to see its events." : "No other meets are available to copy from."
                  : "No events found."}
              </p>
            )}
          </fieldset>
        )}
        {mode === "add" && (
          <details className="meet-custom-event">
            <summary>Add a new event type</summary>
            <form className="meet-form" onSubmit={addCustomChoice}>
              <label className="meet-form-field">
                Event name
                <input value={newName} maxLength={150} required disabled={isSaving} onChange={(event) => setNewName(event.target.value)} />
              </label>
              <label className="meet-form-field">
                Ranking
                <select value={newRanking} disabled={isSaving} onChange={(event) => setNewRanking(event.target.value as RankingOrder)}>
                  <option value="ASC">Lowest value wins (timed events)</option>
                  <option value="DESC">Highest value wins (distance or height)</option>
                </select>
              </label>
              <button className="meets-button" type="submit" disabled={isSaving}>Add to selection</button>
            </form>
          </details>
        )}
        {saveError && <p className="meet-dialog-error" role="alert">{saveError}</p>}
        <div className="meet-dialog-actions">
          <button className="meets-button" type="button" disabled={isSaving} onClick={onClose}>Cancel</button>
          <button className="meets-button meets-button-primary" type="button" disabled={isSaving || loading || Boolean(loadError) || selectedChoices.length === 0} onClick={saveSelected}>
            {isSaving ? "Adding..." : `${mode === "copy" ? "Copy" : "Add"} Selected (${selectedChoices.length})`}
          </button>
        </div>
      </div>
    </MeetDialog>
  );
}

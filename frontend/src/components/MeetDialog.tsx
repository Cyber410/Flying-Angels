// Reusable native modal with keyboard dismissal and busy-state protection.
import { useEffect, useId, useRef } from "react";
import type { ReactNode } from "react";
import "./MeetDialog.css";

type Props = {
  title: string;
  busy?: boolean;
  children: ReactNode;
  onClose: () => void;
};

// Provide a shared modal with keyboard dismissal and saving-state protection.
export default function MeetDialog({ title, busy = false, children, onClose }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    const dialog = dialogRef.current;
    dialog?.showModal();
    return /* Stop pending work and release this effect when it is cleaned up. */ () => dialog?.close();
  }, []);

  return (
    <dialog
      ref={dialogRef}
      className="meet-dialog"
      aria-labelledby={titleId}
      aria-busy={busy}
      onCancel={/* Handle keyboard dismissal while respecting the saving state. */ (event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
    >
      <div className="meet-dialog-heading">
        <h2 id={titleId}>{title}</h2>
        <button
          className="meet-dialog-close"
          type="button"
          aria-label="Close dialog"
          disabled={busy}
          onClick={onClose}
        >
          &times;
        </button>
      </div>
      {children}
    </dialog>
  );
}

import { useEffect, useId, useRef } from "react";
import type { ReactNode } from "react";
import "./MeetDialog.css";

type Props = {
  title: string;
  busy?: boolean;
  children: ReactNode;
  onClose: () => void;
};

export default function MeetDialog({ title, busy = false, children, onClose }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);

  return (
    <dialog
      ref={dialogRef}
      className="meet-dialog"
      aria-labelledby={titleId}
      aria-busy={busy}
      onCancel={(event) => {
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

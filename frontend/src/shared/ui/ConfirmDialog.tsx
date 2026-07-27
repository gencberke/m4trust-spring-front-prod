import type { ReactNode } from "react";
import styles from "./ConfirmDialog.module.css";

/**
 * Shared confirmation dialog for the inline title/body/two-button pattern that
 * was reimplemented four times (review accept, ratification approve,
 * ratification reject, deal cancel).
 *
 * Deliberately NOT covering two other dialogs:
 *   - `AcceptInvitationDialog` carries its own form state and is a fixed
 *     bottom-right overlay, not an inline block.
 *   - `.casework-confirm` uses a different CSS class with different colours and
 *     no `aria-labelledby`.
 * Folding either in requires a styling decision, which belongs to Faz 3.
 */

export interface ConfirmDialogProps {
  /** Unique id for the heading; wired to `aria-labelledby`. */
  titleId: string;
  title: string;
  /** Body copy and any extra detail rows. */
  children: ReactNode;
  pending?: boolean;
  cancelLabel?: string;
  confirmLabel: string;
  confirmPendingLabel: string;
  /** `danger` renders the destructive button styling. */
  confirmVariant?: "primary" | "danger";
  /** `warning` is the amber inline card used by review and ratification. */
  variant?: "default" | "warning";
  onCancel: () => void;
  onConfirm: () => void;
}

export function ConfirmDialog({
  titleId,
  title,
  children,
  pending = false,
  cancelLabel = "Vazgeç",
  confirmLabel,
  confirmPendingLabel,
  confirmVariant = "primary",
  variant = "default",
  onCancel,
  onConfirm,
}: ConfirmDialogProps) {
  const className =
    variant === "warning"
      ? `${styles.confirmationDialog} ${styles.warning}`
      : styles.confirmationDialog;

  return (
    <div
      className={className}
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
    >
      <h3 id={titleId}>{title}</h3>
      {children}
      <div className={styles.actions}>
        <button
          className="text-button"
          type="button"
          onClick={onCancel}
          disabled={pending}
        >
          {cancelLabel}
        </button>
        <button
          className={
            confirmVariant === "danger" ? "danger-button" : "primary-button"
          }
          type="button"
          onClick={onConfirm}
          disabled={pending}
        >
          {pending ? confirmPendingLabel : confirmLabel}
        </button>
      </div>
    </div>
  );
}

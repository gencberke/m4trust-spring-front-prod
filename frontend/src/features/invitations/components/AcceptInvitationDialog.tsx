import { useState } from "react";

import type { LegalEntityMembership } from "../../organization/organizationApi";
import type { IncomingDealInvitation } from "../invitationApi";
import { getInvitationErrorMessage } from "../invitationErrors";
import styles from "../Invitations.module.css";

interface AcceptInvitationDialogProps {
  invitation: IncomingDealInvitation;
  memberships: LegalEntityMembership[];
  error: unknown;
  isPending: boolean;
  onAccept: (legalEntityId: string) => void;
  onClose: () => void;
}

export function AcceptInvitationDialog({
  invitation,
  memberships,
  error,
  isPending,
  onAccept,
  onClose,
}: AcceptInvitationDialogProps) {
  const [legalEntityId, setLegalEntityId] = useState("");

  return (
    <div
      className={styles.dialog}
      role="dialog"
      aria-modal="true"
      aria-labelledby="accept-invitation-title"
    >
      <h3 id="accept-invitation-title">Daveti kabul et</h3>
      <p>
        <strong>{invitation.deal.initiatorLegalName}</strong> tarafından davet
        edildiğiniz <strong>{invitation.deal.title}</strong> anlaşmasına hangi
        kuruluşla katılacağınızı seçin.
      </p>
      {error ? (
        <p className="form-alert" role="alert">
          {getInvitationErrorMessage(error)}
        </p>
      ) : null}
      {memberships.length === 0 ? (
        <p className="form-alert" role="alert">
          Daveti kabul etmek için önce üyesi olduğunuz bir kuruluş gerekir.
        </p>
      ) : (
        <label className="field-group" htmlFor="accept-invitation-entity">
          <span>Katılımcı kuruluş</span>
          <select
            id="accept-invitation-entity"
            value={legalEntityId}
            onChange={(event) => setLegalEntityId(event.target.value)}
            disabled={isPending}
          >
            <option value="">Kuruluş seçin</option>
            {memberships.map((membership) => (
              <option
                key={membership.legalEntityId}
                value={membership.legalEntityId}
              >
                {membership.legalName}
              </option>
            ))}
          </select>
        </label>
      )}
      <div>
        <button
          className="text-button"
          type="button"
          onClick={onClose}
          disabled={isPending}
        >
          Vazgeç
        </button>
        <button
          className="primary-button"
          type="button"
          onClick={() => onAccept(legalEntityId)}
          disabled={isPending || !legalEntityId}
        >
          {isPending ? "Kabul ediliyor…" : "Katılımı onayla"}
        </button>
      </div>
    </div>
  );
}

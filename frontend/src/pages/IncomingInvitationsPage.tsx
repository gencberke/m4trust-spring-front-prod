import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate, useOutletContext } from "react-router";

import type { LegalEntityMembership } from "../features/organization/organizationApi";
import {
  acceptDealInvitation,
  rejectDealInvitation,
  type IncomingDealInvitation,
} from "../features/invitations/invitationApi";
import { getInvitationErrorMessage, isInvitationRefreshRequired } from "../features/invitations/invitationErrors";
import { incomingInvitationsQueryOptions } from "../features/invitations/invitationQueries";
import type { AuthenticatedWorkspaceContext } from "./AuthenticatedLayout";

const INVITATION_PAGE_SIZE = 50;

interface AcceptInvitationDialogProps {
  invitation: IncomingDealInvitation;
  memberships: LegalEntityMembership[];
  error: unknown;
  isPending: boolean;
  onAccept: (legalEntityId: string) => void;
  onClose: () => void;
}

function AcceptInvitationDialog({
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
      className="confirmation-dialog invitation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="accept-invitation-title"
    >
      <h3 id="accept-invitation-title">Daveti kabul et</h3>
      <p>
        <strong>{invitation.deal.initiatorLegalName}</strong> tarafından davet
        edildiğiniz <strong>{invitation.deal.title}</strong> Deal’ine hangi legal
        entity ile katılacağınızı seçin.
      </p>
      {error ? (
        <p className="form-alert" role="alert">{getInvitationErrorMessage(error)}</p>
      ) : null}
      {memberships.length === 0 ? (
        <p className="form-alert" role="alert">
          Daveti kabul etmek için önce üyesi olduğunuz bir legal entity gerekir.
        </p>
      ) : (
        <label className="field-group" htmlFor="accept-invitation-entity">
          <span>Katılımcı legal entity</span>
          <select
            id="accept-invitation-entity"
            value={legalEntityId}
            onChange={(event) => setLegalEntityId(event.target.value)}
            disabled={isPending}
          >
            <option value="">Legal entity seçin</option>
            {memberships.map((membership) => (
              <option key={membership.legalEntityId} value={membership.legalEntityId}>
                {membership.legalName}
              </option>
            ))}
          </select>
        </label>
      )}
      <div>
        <button className="text-button" type="button" onClick={onClose} disabled={isPending}>
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

export function IncomingInvitationsPage() {
  const { user, selectLegalEntity } = useOutletContext<AuthenticatedWorkspaceContext>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [acceptingInvitationId, setAcceptingInvitationId] = useState<string>();
  const invitationsQuery = useQuery(
    incomingInvitationsQueryOptions({ page: 0, size: INVITATION_PAGE_SIZE }),
  );
  const acceptingInvitation = invitationsQuery.data?.items.find(
    (invitation) => invitation.id === acceptingInvitationId,
  );

  function refreshInbox() {
    void queryClient.invalidateQueries({ queryKey: ["deal-invitations", "incoming"] });
  }

  const acceptMutation = useMutation({
    mutationFn: ({ invitation, legalEntityId }: {
      invitation: IncomingDealInvitation;
      legalEntityId: string;
    }) => acceptDealInvitation(invitation.id, {
      legalEntityId,
      expectedVersion: invitation.version,
    }),
    onSuccess: (accepted, variables) => {
      selectLegalEntity(variables.legalEntityId);
      queryClient.invalidateQueries({ queryKey: ["deals", variables.legalEntityId] });
      refreshInbox();
      navigate(`/app/deals/${accepted.deal.id}`);
    },
    onError: (error) => {
      if (isInvitationRefreshRequired(error)) {
        refreshInbox();
      }
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (invitation: IncomingDealInvitation) =>
      rejectDealInvitation(invitation.id, { expectedVersion: invitation.version }),
    onSuccess: refreshInbox,
    onError: (error) => {
      if (isInvitationRefreshRequired(error)) {
        refreshInbox();
      }
    },
  });

  return (
    <main className="workspace-main invitation-workspace">
      <div className="workspace-column">
        <div className="page-introduction invitation-page-heading">
          <span className="section-kicker">Katılım davetleri</span>
          <h1>Gelen davetler</h1>
          <p>
            Davetler hesabınıza gönderilir; kabul ederken katılacak legal entity’yi
            seçersiniz.
          </p>
        </div>

        {invitationsQuery.isPending ? (
          <section className="workspace-panel workspace-state" role="status">
            <span className="loading-line" aria-hidden="true" />
            <h2>Davetler yükleniyor</h2>
            <p>Hesabınıza gelen bekleyen davetler hazırlanıyor.</p>
          </section>
        ) : null}
        {invitationsQuery.isError ? (
          <section className="workspace-panel workspace-state" role="alert">
            <h2>Davetler alınamadı</h2>
            <p>{getInvitationErrorMessage(invitationsQuery.error)}</p>
            <button
              className="secondary-button"
              type="button"
              onClick={() => void invitationsQuery.refetch()}
              disabled={invitationsQuery.isFetching}
            >
              {invitationsQuery.isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
            </button>
          </section>
        ) : null}
        {invitationsQuery.data?.items.length === 0 ? (
          <section className="workspace-panel workspace-state" role="status">
            <h2>Bekleyen davet yok</h2>
            <p>Hesabınıza gönderilmiş bekleyen bir Deal daveti bulunmuyor.</p>
          </section>
        ) : null}
        {invitationsQuery.data?.items.length ? (
          <div className="incoming-invitation-list">
            {invitationsQuery.data.items.map((invitation) => (
              <article className="workspace-panel incoming-invitation-card" key={invitation.id}>
                <div>
                  <span className="section-kicker">{invitation.deal.reference}</span>
                  <h2>{invitation.deal.title}</h2>
                  <p className="inviter-legal-name">{invitation.deal.initiatorLegalName}</p>
                  <p className="muted-copy">Davet eden legal entity</p>
                </div>
                <div className="invitation-card-actions">
                  {invitation.availableActions.canAccept ? (
                    <button
                      className="primary-button"
                      type="button"
                      onClick={() => {
                        acceptMutation.reset();
                        setAcceptingInvitationId(invitation.id);
                      }}
                    >
                      Daveti kabul et
                    </button>
                  ) : null}
                  {invitation.availableActions.canReject ? (
                    <button
                      className="danger-button"
                      type="button"
                      onClick={() => rejectMutation.mutate(invitation)}
                      disabled={rejectMutation.isPending}
                    >
                      {rejectMutation.isPending ? "Reddediliyor…" : "Daveti reddet"}
                    </button>
                  ) : null}
                </div>
              </article>
            ))}
          </div>
        ) : null}
        {rejectMutation.isError ? (
          <p className="form-alert workspace-notice" role="alert">
            {getInvitationErrorMessage(rejectMutation.error)}
          </p>
        ) : null}
        {acceptingInvitation ? (
          <AcceptInvitationDialog
            invitation={acceptingInvitation}
            memberships={user.memberships}
            error={acceptMutation.error}
            isPending={acceptMutation.isPending}
            onAccept={(legalEntityId) =>
              acceptMutation.mutate({ invitation: acceptingInvitation, legalEntityId })
            }
            onClose={() => {
              acceptMutation.reset();
              setAcceptingInvitationId(undefined);
            }}
          />
        ) : null}
      </div>
    </main>
  );
}

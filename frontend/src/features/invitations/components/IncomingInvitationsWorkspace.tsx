import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router";

import {
  acceptDealInvitation,
  rejectDealInvitation,
  type IncomingDealInvitation,
} from "../invitationApi";
import {
  getInvitationErrorMessage,
  isInvitationRefreshRequired,
} from "../invitationErrors";
import { incomingInvitationsQueryOptions } from "../invitationQueries";
import { AcceptInvitationDialog } from "./AcceptInvitationDialog";
import type { LegalEntityMembership } from "../../organization/organizationApi";
import styles from "../Invitations.module.css";

const INVITATION_PAGE_SIZE = 50;

interface IncomingInvitationsWorkspaceProps {
  memberships: LegalEntityMembership[];
  selectLegalEntity: (legalEntityId: string | undefined) => void;
}

export function IncomingInvitationsWorkspace({
  memberships,
  selectLegalEntity,
}: IncomingInvitationsWorkspaceProps) {
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
    void queryClient.invalidateQueries({
      queryKey: ["deal-invitations", "incoming"],
    });
  }

  const acceptMutation = useMutation({
    mutationFn: ({
      invitation,
      legalEntityId,
    }: {
      invitation: IncomingDealInvitation;
      legalEntityId: string;
    }) =>
      acceptDealInvitation(invitation.id, {
        legalEntityId,
        expectedVersion: invitation.version,
      }),
    onSuccess: (accepted, variables) => {
      selectLegalEntity(variables.legalEntityId);
      queryClient.invalidateQueries({
        queryKey: ["deals", variables.legalEntityId],
      });
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
      rejectDealInvitation(invitation.id, {
        expectedVersion: invitation.version,
      }),
    onSuccess: refreshInbox,
    onError: (error) => {
      if (isInvitationRefreshRequired(error)) {
        refreshInbox();
      }
    },
  });

  return (
    <main className={`workspace-main ${styles.workspace}`}>
      <div className="workspace-column">
        <div className={`page-introduction ${styles.pageHeading}`}>
          <span className="section-kicker">Katılım davetleri</span>
          <h1>Gelen davetler</h1>
          <p>
            Davetler hesabınıza gönderilir; kabul ederken katılacak kuruluşu
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
              {invitationsQuery.isFetching
                ? "Yeniden deneniyor…"
                : "Yeniden dene"}
            </button>
          </section>
        ) : null}
        {invitationsQuery.data?.items.length === 0 ? (
          <section className="workspace-panel workspace-state" role="status">
            <h2>Bekleyen davet yok</h2>
            <p>
              Hesabınıza gönderilmiş bekleyen bir anlaşma daveti bulunmuyor.
            </p>
          </section>
        ) : null}
        {invitationsQuery.data?.items.length ? (
          <div className={styles.incomingList}>
            {invitationsQuery.data.items.map((invitation) => (
              <article
                className={`workspace-panel ${styles.card}`}
                key={invitation.id}
              >
                <div>
                  <span className="section-kicker">
                    {invitation.deal.reference}
                  </span>
                  <h2>{invitation.deal.title}</h2>
                  <p className={styles.inviterName}>
                    {invitation.deal.initiatorLegalName}
                  </p>
                  <p className="muted-copy">Daveti gönderen kuruluş</p>
                </div>
                <div className={styles.cardActions}>
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
                      {rejectMutation.isPending
                        ? "Reddediliyor…"
                        : "Daveti reddet"}
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
            memberships={memberships}
            error={acceptMutation.error}
            isPending={acceptMutation.isPending}
            onAccept={(legalEntityId) =>
              acceptMutation.mutate({
                invitation: acceptingInvitation,
                legalEntityId,
              })
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

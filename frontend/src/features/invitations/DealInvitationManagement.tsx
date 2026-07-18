import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState, type FormEvent } from "react";

import type { DealDetail } from "../deals/dealApi";
import {
  createDealInvitation,
  revokeDealInvitation,
  type CreateDealInvitationRequest,
} from "./invitationApi";
import { getInvitationErrorMessage, isInvitationRefreshRequired } from "./invitationErrors";
import { dealInvitationsQueryOptions } from "./invitationQueries";

const INVITATION_PAGE_SIZE = 100;

interface DealInvitationManagementProps {
  deal: DealDetail;
  legalEntityId: string;
}

export function DealInvitationManagement({
  deal,
  legalEntityId,
}: DealInvitationManagementProps) {
  const queryClient = useQueryClient();
  const [recipientEmail, setRecipientEmail] = useState("");
  const [clientError, setClientError] = useState<string>();
  const idempotencyKeyRef = useRef<string | undefined>(undefined);
  const idempotencyIntentEmailRef = useRef<string | undefined>(undefined);
  const invitationsQuery = useQuery(
    dealInvitationsQueryOptions(
      legalEntityId,
      deal.id,
      { page: 0, size: INVITATION_PAGE_SIZE },
      deal.availableActions.canCreateInvitation,
    ),
  );

  function refreshInvitations() {
    void queryClient.invalidateQueries({
      queryKey: ["deal-invitations", legalEntityId, "deal", deal.id],
    });
  }

  const createMutation = useMutation({
    mutationFn: (request: CreateDealInvitationRequest) =>
      createDealInvitation(
        legalEntityId,
        deal.id,
        request,
        idempotencyKeyRef.current!,
      ),
    onSuccess: () => {
      setRecipientEmail("");
      setClientError(undefined);
      idempotencyKeyRef.current = undefined;
      idempotencyIntentEmailRef.current = undefined;
      refreshInvitations();
    },
  });

  const revokeMutation = useMutation({
    mutationFn: ({ invitationId, expectedVersion }: {
      invitationId: string;
      expectedVersion: number;
    }) => revokeDealInvitation(legalEntityId, invitationId, { expectedVersion }),
    onSuccess: refreshInvitations,
    onError: (error) => {
      if (isInvitationRefreshRequired(error)) {
        refreshInvitations();
      }
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedEmail = recipientEmail.trim().toLocaleLowerCase("en-US");
    if (!normalizedEmail) {
      setClientError("Alıcı e-posta adresini girin.");
      return;
    }

    // The key survives retries of the same canonical request, but never crosses
    // into a newly edited invitation intent.
    if (idempotencyIntentEmailRef.current !== normalizedEmail) {
      idempotencyIntentEmailRef.current = normalizedEmail;
      idempotencyKeyRef.current = crypto.randomUUID();
    }
    setClientError(undefined);
    createMutation.mutate({ recipientEmail: normalizedEmail });
  }

  if (!deal.availableActions.canCreateInvitation) {
    return (
      <section className="workspace-panel invitation-management-panel">
        <div className="panel-heading">
          <span className="section-kicker">Katılım</span>
          <h2>Davet yönetimi kapalı</h2>
          <p>
            Sunucunun güncel action projection’ı bu Deal için davet oluşturmaya
            izin vermiyor.
          </p>
        </div>
      </section>
    );
  }

  return (
    <section className="workspace-panel invitation-management-panel">
      <div className="panel-heading">
        <span className="section-kicker">Katılım</span>
        <h2>Deal davetleri</h2>
        <p>Yeni katılımcıları e-posta adresleriyle davet edin.</p>
      </div>

      {createMutation.isError ? (
        <p className="form-alert panel-alert" role="alert">
          {getInvitationErrorMessage(createMutation.error)}
        </p>
      ) : null}

      <form className="auth-form invitation-form" onSubmit={handleSubmit}>
        <div className="field-group">
          <label htmlFor="invitation-recipient-email">Alıcı e-posta adresi</label>
          <input
            id="invitation-recipient-email"
            type="email"
            value={recipientEmail}
            onChange={(event) => {
              setRecipientEmail(event.target.value);
              setClientError(undefined);
            }}
            maxLength={320}
            required
            aria-invalid={Boolean(clientError)}
          />
          {clientError ? <span className="field-error">{clientError}</span> : null}
        </div>
        <button className="primary-button" type="submit" disabled={createMutation.isPending}>
          {createMutation.isPending ? "Davet gönderiliyor…" : "Davet gönder"}
        </button>
      </form>

      <div className="invitation-list-heading">
        <h3>Gönderilen davetler</h3>
        {invitationsQuery.data ? (
          <span>{invitationsQuery.data.totalElements} kayıt</span>
        ) : null}
      </div>

      {invitationsQuery.isPending ? (
        <div className="inline-state" role="status">
          <span className="loading-line" aria-hidden="true" />
          Davetler yükleniyor…
        </div>
      ) : null}
      {invitationsQuery.isError ? (
        <div className="form-alert panel-alert" role="alert">
          <p>{getInvitationErrorMessage(invitationsQuery.error)}</p>
          <button
            className="secondary-button"
            type="button"
            onClick={() => void invitationsQuery.refetch()}
            disabled={invitationsQuery.isFetching}
          >
            {invitationsQuery.isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
          </button>
        </div>
      ) : null}
      {invitationsQuery.data?.items.length === 0 ? (
        <p className="muted-copy invitation-empty">Henüz gönderilmiş bir davet yok.</p>
      ) : null}
      {invitationsQuery.data?.items.length ? (
        <ul className="invitation-list">
          {invitationsQuery.data.items.map((invitation) => (
            <li key={invitation.id}>
              <div>
                <strong>{invitation.recipientEmail}</strong>
                <span>Durum: {invitation.status}</span>
              </div>
              {invitation.availableActions.canRevoke ? (
                <button
                  className="danger-button"
                  type="button"
                  onClick={() =>
                    revokeMutation.mutate({
                      invitationId: invitation.id,
                      expectedVersion: invitation.version,
                    })
                  }
                  disabled={revokeMutation.isPending}
                >
                  {revokeMutation.isPending ? "Geri alınıyor…" : "Daveti geri al"}
                </button>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}
      {revokeMutation.isError ? (
        <p className="form-alert panel-alert" role="alert">
          {getInvitationErrorMessage(revokeMutation.error)}
        </p>
      ) : null}
    </section>
  );
}

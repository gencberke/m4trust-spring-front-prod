import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import {
  useEffect,
  useState,
  type FormEvent,
} from "react";
import {
  Link,
  useOutletContext,
  useParams,
} from "react-router";

import { DealContractAnalysis } from "../features/analysis/DealContractAnalysis";
import {
  cancelDeal,
  updateDeal,
  updateDealParties,
  type DealDetail,
  type UpdateDealPartiesRequest,
  type UpdateDealRequest,
} from "../features/deals/dealApi";
import {
  getDealErrorMessage,
  getDealFieldErrors,
  isDealNotFound,
  isDealStaleVersion,
} from "../features/deals/dealErrors";
import {
  dealDetailQueryKey,
  dealDetailQueryOptions,
} from "../features/deals/dealQueries";
import { DealDocumentManagement } from "../features/documents/DealDocumentManagement";
import { DealInvitationManagement } from "../features/invitations/DealInvitationManagement";
import { isInvalidLegalEntitySelection } from "../features/organization/organizationErrors";
import type { AuthenticatedWorkspaceContext } from "./AuthenticatedLayout";
import { DealMembershipBootstrapState } from "./DealMembershipBootstrapState";

const DATE_FORMATTER = new Intl.DateTimeFormat("tr-TR", {
  dateStyle: "long",
  timeStyle: "short",
});

function formatDate(value: string): string {
  return DATE_FORMATTER.format(new Date(value));
}

interface EditDealFormProps {
  deal: DealDetail;
  isPending: boolean;
  error: unknown;
  isReloading: boolean;
  onReload: () => void;
  onSubmit: (request: UpdateDealRequest) => void;
}

function EditDealForm({
  deal,
  isPending,
  error,
  isReloading,
  onReload,
  onSubmit,
}: EditDealFormProps) {
  const [title, setTitle] = useState(deal.title);
  const [description, setDescription] = useState(deal.description ?? "");
  const [clientTitleError, setClientTitleError] = useState<string>();
  const serverErrors = getDealFieldErrors(error);
  const stale = isDealStaleVersion(error);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedTitle = title.trim();
    if (!normalizedTitle) {
      setClientTitleError("Deal başlığını girin.");
      return;
    }
    onSubmit({
      title: normalizedTitle,
      description: description.trim() || null,
      expectedVersion: deal.version,
    });
  }

  return (
    <section className="workspace-panel deal-edit-panel">
      <div className="panel-heading">
        <span className="section-kicker">Temel bilgiler</span>
        <h2>Deal’i düzenle</h2>
        <p>Bu form sunucudan yüklenen sürüm {deal.version} ile kaydedilir.</p>
      </div>

      {error ? (
        <div className="form-alert panel-alert" role="alert">
          <p>{getDealErrorMessage(error)}</p>
          {stale ? (
            <button
              className="secondary-button"
              type="button"
              onClick={onReload}
              disabled={isReloading}
            >
              {isReloading ? "Güncel veri yükleniyor…" : "Güncel veriyi yükle"}
            </button>
          ) : null}
        </div>
      ) : null}

      <form className="auth-form deal-form" onSubmit={handleSubmit}>
        <div className="field-group">
          <label htmlFor="edit-deal-title">Başlık</label>
          <input
            id="edit-deal-title"
            value={title}
            onChange={(event) => {
              setTitle(event.target.value);
              setClientTitleError(undefined);
            }}
            maxLength={200}
            required
            aria-invalid={Boolean(clientTitleError ?? serverErrors.title)}
          />
          {clientTitleError ?? serverErrors.title ? (
            <span className="field-error">
              {clientTitleError ?? serverErrors.title}
            </span>
          ) : null}
        </div>
        <div className="field-group">
          <label htmlFor="edit-deal-description">Açıklama</label>
          <textarea
            id="edit-deal-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={4000}
            rows={6}
          />
          <span className="field-hint">
            Boş gönderildiğinde açıklama açıkça temizlenir.
          </span>
          {serverErrors.description ? (
            <span className="field-error">{serverErrors.description}</span>
          ) : null}
        </div>
        <button className="primary-button" type="submit" disabled={isPending}>
          {isPending ? "Kaydediliyor…" : "Değişiklikleri kaydet"}
        </button>
      </form>
    </section>
  );
}

interface DealPartiesFormProps {
  deal: DealDetail;
  isPending: boolean;
  error: unknown;
  isReloading: boolean;
  onReload: () => void;
  onSubmit: (request: UpdateDealPartiesRequest) => void;
}

function DealPartiesForm({
  deal,
  isPending,
  error,
  isReloading,
  onReload,
  onSubmit,
}: DealPartiesFormProps) {
  const [buyerLegalEntityId, setBuyerLegalEntityId] = useState(
    deal.buyer?.legalEntityId ?? "",
  );
  const [sellerLegalEntityId, setSellerLegalEntityId] = useState(
    deal.seller?.legalEntityId ?? "",
  );
  const serverErrors = getDealFieldErrors(error);
  const stale = isDealStaleVersion(error);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({
      buyerLegalEntityId: buyerLegalEntityId || null,
      sellerLegalEntityId: sellerLegalEntityId || null,
      expectedVersion: deal.version,
    });
  }

  return (
    <form className="auth-form deal-form party-management-form" onSubmit={handleSubmit}>
      {error ? (
        <div className="form-alert" role="alert">
          <p>{getDealErrorMessage(error)}</p>
          {stale ? (
            <button
              className="secondary-button"
              type="button"
              onClick={onReload}
              disabled={isReloading}
            >
              {isReloading ? "Güncel veri yükleniyor…" : "Güncel veriyi yükle"}
            </button>
          ) : null}
        </div>
      ) : null}
      <div className="party-management-fields">
        <div className="field-group">
          <label htmlFor="deal-buyer">Alıcı</label>
          <select
            id="deal-buyer"
            value={buyerLegalEntityId}
            onChange={(event) => setBuyerLegalEntityId(event.target.value)}
            aria-invalid={Boolean(serverErrors.buyerLegalEntityId)}
            aria-describedby={
              serverErrors.buyerLegalEntityId ? "deal-buyer-error" : undefined
            }
          >
            <option value="">Atanmamış</option>
            {deal.participants.map((participant) => (
              <option key={participant.legalEntityId} value={participant.legalEntityId}>
                {participant.legalName}
              </option>
            ))}
          </select>
          {serverErrors.buyerLegalEntityId ? (
            <span className="field-error" id="deal-buyer-error">
              {serverErrors.buyerLegalEntityId}
            </span>
          ) : null}
        </div>
        <div className="field-group">
          <label htmlFor="deal-seller">Satıcı</label>
          <select
            id="deal-seller"
            value={sellerLegalEntityId}
            onChange={(event) => setSellerLegalEntityId(event.target.value)}
            aria-invalid={Boolean(serverErrors.sellerLegalEntityId)}
            aria-describedby={
              serverErrors.sellerLegalEntityId ? "deal-seller-error" : undefined
            }
          >
            <option value="">Atanmamış</option>
            {deal.participants.map((participant) => (
              <option key={participant.legalEntityId} value={participant.legalEntityId}>
                {participant.legalName}
              </option>
            ))}
          </select>
          {serverErrors.sellerLegalEntityId ? (
            <span className="field-error" id="deal-seller-error">
              {serverErrors.sellerLegalEntityId}
            </span>
          ) : null}
        </div>
      </div>
      <button className="primary-button" type="submit" disabled={isPending}>
        {isPending ? "Taraflar kaydediliyor…" : "Tarafları kaydet"}
      </button>
    </form>
  );
}

export function DealDetailPage() {
  const { dealId } = useParams();
  const {
    selectedLegalEntityId,
    selectedMembership,
    selectionNotice,
    clearInvalidSelection,
    membershipsPending,
    membershipsError,
    membershipsFetching,
    refetchMemberships,
  } = useOutletContext<AuthenticatedWorkspaceContext>();
  const queryClient = useQueryClient();
  const [updateNotice, setUpdateNotice] = useState<string>();
  const [cancelConfirmationOpen, setCancelConfirmationOpen] = useState(false);
  const detailQuery = useQuery(
    dealDetailQueryOptions(selectedLegalEntityId, dealId),
  );
  const invalidSelection = isInvalidLegalEntitySelection(detailQuery.error);

  useEffect(() => {
    if (invalidSelection) {
      clearInvalidSelection();
    }
  }, [clearInvalidSelection, invalidSelection]);

  const updateMutation = useMutation({
    mutationFn: (request: UpdateDealRequest) =>
      updateDeal(selectedLegalEntityId!, dealId!, request),
    onSuccess: async (updated) => {
      queryClient.setQueryData(
        dealDetailQueryKey(selectedLegalEntityId!, updated.id),
        updated,
      );
      setUpdateNotice(`Değişiklikler sürüm ${updated.version} olarak kaydedildi.`);
      await queryClient.invalidateQueries({
        queryKey: ["deals", selectedLegalEntityId, "list"],
      });
    },
    onError: (error) => {
      if (isInvalidLegalEntitySelection(error)) {
        clearInvalidSelection();
      }
    },
  });

  const partiesMutation = useMutation({
    mutationFn: (request: UpdateDealPartiesRequest) =>
      updateDealParties(selectedLegalEntityId!, dealId!, request),
    onSuccess: async (updated) => {
      queryClient.setQueryData(
        dealDetailQueryKey(selectedLegalEntityId!, updated.id),
        updated,
      );
      setUpdateNotice(`Taraflar sürüm ${updated.version} olarak kaydedildi.`);
      await queryClient.invalidateQueries({
        queryKey: ["deals", selectedLegalEntityId, "list"],
      });
    },
    onError: (error) => {
      if (isInvalidLegalEntitySelection(error)) {
        clearInvalidSelection();
      }
    },
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelDeal(selectedLegalEntityId!, dealId!),
    onSuccess: async (cancelled) => {
      queryClient.setQueryData(
        dealDetailQueryKey(selectedLegalEntityId!, cancelled.id),
        cancelled,
      );
      setCancelConfirmationOpen(false);
      setUpdateNotice(`${cancelled.reference} iptal edildi.`);
      await queryClient.invalidateQueries({
        queryKey: ["deals", selectedLegalEntityId, "list"],
      });
    },
    onError: (error) => {
      if (isInvalidLegalEntitySelection(error)) {
        clearInvalidSelection();
      }
    },
  });

  if (membershipsPending) {
    return (
      <DealMembershipBootstrapState
        isFetching={membershipsFetching}
        onRetry={refetchMemberships}
      />
    );
  }

  if (membershipsError) {
    return (
      <DealMembershipBootstrapState
        error={membershipsError}
        isFetching={membershipsFetching}
        onRetry={refetchMemberships}
      />
    );
  }

  if (!selectedLegalEntityId || !selectedMembership) {
    return (
      <main className="workspace-main deal-workspace">
        <div className="workspace-column">
          <span className="section-kicker">Deal detayı</span>
          <h1>Aktif legal entity seçin.</h1>
          <p className="workspace-lead">
            Bu Deal’i görüntülemek için üst menüden bir legal entity seçin.
          </p>
          {selectionNotice ? (
            <p className="form-notice workspace-notice" role="status">
              {selectionNotice}
            </p>
          ) : null}
          <Link className="primary-link-button" to="/app">
            Organizasyonlara dön
          </Link>
        </div>
      </main>
    );
  }

  if (detailQuery.isPending) {
    return (
      <main className="workspace-main deal-workspace">
        <div className="workspace-column">
          <section className="workspace-panel workspace-state" role="status">
            <span className="loading-line" aria-hidden="true" />
            <h2>Deal yükleniyor</h2>
            <p>Güncel detay ve kullanılabilir aksiyonlar alınıyor.</p>
          </section>
        </div>
      </main>
    );
  }

  if (isDealNotFound(detailQuery.error)) {
    return (
      <main className="workspace-main deal-workspace">
        <div className="workspace-column">
          <section className="workspace-panel workspace-state" role="alert">
            <span className="section-kicker">Bilgi ifşa edilmedi</span>
            <h2>Deal bulunamadı</h2>
            <p>
              Kayıt mevcut olmayabilir veya {selectedMembership.legalName} bu
              Deal’in katılımcısı değildir.
            </p>
            <Link className="primary-link-button" to="/app/deals">
              Deal listesine dön
            </Link>
          </section>
        </div>
      </main>
    );
  }

  if (detailQuery.isError && !invalidSelection) {
    return (
      <main className="workspace-main deal-workspace">
        <div className="workspace-column">
          <section className="workspace-panel workspace-state" role="alert">
            <h2>Deal detayı alınamadı</h2>
            <p>{getDealErrorMessage(detailQuery.error)}</p>
            <button
              className="secondary-button"
              type="button"
              onClick={() => void detailQuery.refetch()}
              disabled={detailQuery.isFetching}
            >
              {detailQuery.isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
            </button>
          </section>
        </div>
      </main>
    );
  }

  const deal = detailQuery.data;
  if (!deal) {
    return null;
  }

  return (
    <main className="workspace-main deal-workspace">
      <div className="workspace-column">
        <Link className="back-link" to="/app/deals">
          ← Deal listesine dön
        </Link>
        <div className="deal-detail-heading">
          <div>
            <span className="deal-reference">{deal.reference}</span>
            <h1>{deal.title}</h1>
            <p className="workspace-lead">
              {deal.description ?? "Bu Deal için açıklama girilmemiş."}
            </p>
          </div>
          <div className="deal-status-stack">
            <span className="status-badge" data-status={deal.status}>
              {deal.status}
            </span>
            <span>Lifecycle: {deal.lifecycle}</span>
          </div>
        </div>

        {updateNotice ? (
          <p className="success-notice workspace-notice" role="status">
            {updateNotice}
          </p>
        ) : null}
        {cancelMutation.isError ? (
          <p className="form-alert workspace-notice" role="alert">
            {getDealErrorMessage(cancelMutation.error)}
          </p>
        ) : null}

        <dl className="deal-facts">
          <div>
            <dt>Referans</dt>
            <dd>{deal.reference}</dd>
          </div>
          <div>
            <dt>Durum</dt>
            <dd>{deal.status}</dd>
          </div>
          <div>
            <dt>Lifecycle</dt>
            <dd>{deal.lifecycle}</dd>
          </div>
          <div>
            <dt>Sürüm</dt>
            <dd>{deal.version}</dd>
          </div>
          <div>
            <dt>Oluşturuldu</dt>
            <dd>{formatDate(deal.createdAt)}</dd>
          </div>
          <div>
            <dt>Güncellendi</dt>
            <dd>{formatDate(deal.updatedAt)}</dd>
          </div>
        </dl>

        <section className="workspace-panel deal-participants-panel">
          <div className="panel-heading">
            <span className="section-kicker">Katılımcılar</span>
            <h2>Deal görünürlüğü olan legal entity’ler</h2>
            <p>Katılım, taraf rolü veya sözleşmesel onay anlamına gelmez.</p>
          </div>
          <ul className="participant-list">
            {deal.participants.map((participant) => (
              <li key={participant.legalEntityId}>
                <div>
                  <strong>{participant.legalName}</strong>
                  <span>Katılım: {formatDate(participant.joinedAt)}</span>
                </div>
                {participant.partyRoles.length ? (
                  <div
                    className="party-role-badges"
                    aria-label={`${participant.legalName} taraf rolleri`}
                  >
                    {participant.partyRoles.map((role) => (
                      <span className="role-badge" key={role}>{role}</span>
                    ))}
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        </section>

        <section className="workspace-panel deal-parties-panel">
          <div className="panel-heading">
            <span className="section-kicker">Taraflar</span>
            <h2>Alıcı ve satıcı atamaları</h2>
            <p>Taraf rolleri onay veya Deal aktivasyonu anlamına gelmez.</p>
          </div>
          <dl className="party-assignment-list">
            <div>
              <dt>Alıcı</dt>
              <dd>{deal.buyer?.legalName ?? "Atanmamış"}</dd>
            </div>
            <div>
              <dt>Satıcı</dt>
              <dd>{deal.seller?.legalName ?? "Atanmamış"}</dd>
            </div>
          </dl>
          {deal.buyer && deal.seller ? (
            <p className="party-readiness-notice" role="status">
              Taraflar ratification için hazır.
            </p>
          ) : null}
          {deal.availableActions.canManageParties ? (
            <DealPartiesForm
              key={`${deal.id}:${deal.version}`}
              deal={deal}
              error={partiesMutation.error}
              isPending={partiesMutation.isPending}
              isReloading={detailQuery.isFetching}
              onReload={() => {
                partiesMutation.reset();
                setUpdateNotice(undefined);
                void detailQuery.refetch();
              }}
              onSubmit={(request) => {
                setUpdateNotice(undefined);
                partiesMutation.mutate(request);
              }}
            />
          ) : null}
        </section>

        <div className="deal-detail-layout">
          {deal.availableActions.canUpdate ? (
            <EditDealForm
              key={`${deal.id}:${deal.version}`}
              deal={deal}
              error={updateMutation.error}
              isPending={updateMutation.isPending}
              isReloading={detailQuery.isFetching}
              onReload={() => {
                updateMutation.reset();
                setUpdateNotice(undefined);
                void detailQuery.refetch();
              }}
              onSubmit={(request) => {
                setUpdateNotice(undefined);
                updateMutation.mutate(request);
              }}
            />
          ) : (
            <section className="workspace-panel deal-edit-panel">
              <div className="panel-heading">
                <span className="section-kicker">Salt okunur</span>
                <h2>Düzenleme kapalı</h2>
                <p>
                  Sunucunun güncel action projection’ı bu Deal için düzenlemeye
                  izin vermiyor.
                </p>
              </div>
            </section>
          )}

          <aside className="workspace-panel deal-actions-panel">
            <div className="panel-heading">
              <span className="section-kicker">İşlemler</span>
              <h2>Deal aksiyonları</h2>
              <p>
                Kontroller sunucunun güncel availableActions alanından gelir.
              </p>
            </div>

            {deal.availableActions.canCancel ? (
              <button
                className="danger-button"
                type="button"
                onClick={() => setCancelConfirmationOpen(true)}
              >
                Deal’i iptal et
              </button>
            ) : (
              <p className="muted-copy">
                Bu Deal için kullanılabilir iptal aksiyonu yok.
              </p>
            )}

            {cancelConfirmationOpen ? (
              <div
                className="confirmation-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="cancel-deal-title"
              >
                <h3 id="cancel-deal-title">Deal iptal edilsin mi?</h3>
                <p>
                  Bu işlem Deal’i CANCELLED durumuna taşır ve düzenleme
                  aksiyonlarını kapatır.
                </p>
                <div>
                  <button
                    className="text-button"
                    type="button"
                    onClick={() => setCancelConfirmationOpen(false)}
                    disabled={cancelMutation.isPending}
                  >
                    Vazgeç
                  </button>
                  <button
                    className="danger-button"
                    type="button"
                    onClick={() => cancelMutation.mutate()}
                    disabled={cancelMutation.isPending}
                  >
                    {cancelMutation.isPending ? "İptal ediliyor…" : "İptali onayla"}
                  </button>
                </div>
              </div>
            ) : null}
          </aside>
        </div>

        <DealDocumentManagement
          deal={deal}
          legalEntityId={selectedLegalEntityId}
        />

        <DealContractAnalysis
          deal={deal}
          legalEntityId={selectedLegalEntityId}
        />

        <DealInvitationManagement
          deal={deal}
          legalEntityId={selectedLegalEntityId}
        />
      </div>
    </main>
  );
}

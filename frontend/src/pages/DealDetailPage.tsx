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
import { DealReviewWorkspace } from "../features/review/DealReviewWorkspace";
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
import { DealFundingPanel } from "../features/funding/DealFundingPanel";
import { DealFulfillmentPanel } from "../features/fulfillment/DealFulfillmentPanel";
import {
  FULFILLMENT_LIVE_POLL_STATUSES,
  FULFILLMENT_POLL_INTERVAL_MS,
} from "../features/fulfillment/fulfillmentQueries";
import { DealCaseworkPanel } from "../features/casework/DealCaseworkPanel";
import { DealInvitationManagement } from "../features/invitations/DealInvitationManagement";
import { DealRatificationPanel } from "../features/ratification/DealRatificationPanel";
import { DealSettlementPanel } from "../features/settlement/DealSettlementPanel";
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

type WorkspaceArea =
  | "agreement"
  | "review"
  | "approval"
  | "payment"
  | "delivery"
  | "closure";

const WORKSPACE_AREAS: ReadonlyArray<{ id: WorkspaceArea; label: string }> = [
  { id: "agreement", label: "Anlaşma" },
  { id: "review", label: "İnceleme" },
  { id: "approval", label: "Onay" },
  { id: "payment", label: "Ödeme" },
  { id: "delivery", label: "Teslimat" },
  { id: "closure", label: "Kapanış" },
];

function workspaceAreaForLifecycle(lifecycle: DealDetail["lifecycle"]): WorkspaceArea {
  switch (lifecycle) {
    case "CONTRACT_ANALYSIS":
    case "MANUAL_REVIEW":
      return "review";
    case "RATIFICATION":
      return "approval";
    case "FUNDING":
      return "payment";
    case "FULFILLMENT":
    case "DISPUTE":
      return "delivery";
    case "SETTLEMENT":
    case "COMPLETED":
      return "closure";
    case "CANCELLED":
    case "ARCHIVED":
      return "agreement";
    default:
      return "agreement";
  }
}

function lifecycleLabel(lifecycle: DealDetail["lifecycle"]): string {
  const labels: Record<DealDetail["lifecycle"], string> = {
    DRAFT: "Hazırlık",
    CONTRACT_ANALYSIS: "Belge incelemesi",
    MANUAL_REVIEW: "Manuel inceleme",
    RATIFICATION: "Ticari onay",
    FUNDING: "Ödeme",
    FULFILLMENT: "Teslimat",
    SETTLEMENT: "Kapanış",
    DISPUTE: "Uyuşmazlık",
    COMPLETED: "Tamamlandı",
    CANCELLED: "İptal edildi",
    ARCHIVED: "Arşivde",
  };
  return labels[lifecycle];
}

function TerminalStatePanel({
  deal,
  settlementReadOnly,
}: {
  deal: DealDetail;
  settlementReadOnly: boolean;
}) {
  return (
    <section className="workspace-panel terminal-state-panel" aria-labelledby="terminal-state-title">
      <span className="section-kicker">Durum</span>
      <h2 id="terminal-state-title">{nextTask(deal)}</h2>
      <p>
        {settlementReadOnly
          ? "Bu aşamada yeni işlem başlatılamaz; mevcut kayıtlar yalnızca görüntülenir."
          : "Bu anlaşma için yeni işlem sunulmuyor; mevcut kayıtlar aşağıda görüntülenir."}
      </p>
    </section>
  );
}

function nextTask(deal: DealDetail): string {
  if (deal.lifecycle === "SETTLEMENT") return "Kapanış işlemi izleniyor";
  if (deal.lifecycle === "COMPLETED") {
    return "Anlaşma kapatıldı (simüle kapanış tamamlandı)";
  }
  if (deal.lifecycle === "CANCELLED") return "Anlaşma iptal edildi";
  if (deal.lifecycle === "ARCHIVED") return "Anlaşma arşivde";
  if (deal.availableActions.canCreateDocumentUploadIntent) return "Sözleşme belgesini ekleyin";
  if (deal.availableActions.canRequestAnalysis) return "Belge analizini başlatın";
  if (deal.availableActions.canReviewExtraction) return "Çıkarımı manuel olarak inceleyin";
  if (deal.availableActions.canCreateRatificationPackage || deal.availableActions.canApproveRatification) return "Onay paketini gözden geçirin";
  if (deal.availableActions.canCreateFundingPlan || deal.availableActions.canInitiateFunding) return "Ödemeyi güvenceye alın";
  if (deal.availableActions.canStartFulfillment || deal.availableActions.canUploadEvidence || deal.availableActions.canAcceptEvidence) {
    return "Teslimat kanıtını yönetin";
  }
  if (deal.availableActions.canRequestRelease || deal.availableActions.canReconcileRelease) {
    return "Kapanış adımını tamamlayın";
  }
  return "Güncel durum sunucudan takip ediliyor";
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
      setClientTitleError("Anlaşma başlığını girin.");
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
        <h2>Anlaşmayı düzenle</h2>
        <p>Değişiklikler güncel kayıtla güvenli biçimde kaydedilir.</p>
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
  const [activeArea, setActiveArea] = useState<WorkspaceArea>("agreement");
  const detailQuery = useQuery({
    ...dealDetailQueryOptions(selectedLegalEntityId, dealId),
    refetchInterval: (query) => {
      const status = query.state.data?.fulfillment?.status;
      return status && FULFILLMENT_LIVE_POLL_STATUSES.has(status)
        ? FULFILLMENT_POLL_INTERVAL_MS
        : false;
    },
  });
  const invalidSelection = isInvalidLegalEntitySelection(detailQuery.error);
  const settlementReadOnly = detailQuery.data?.lifecycle === "SETTLEMENT";
  const closureTerminalLifecycle = ["SETTLEMENT", "COMPLETED"]
    .includes(detailQuery.data?.lifecycle ?? "");
  const agreementTerminalLifecycle = ["CANCELLED", "ARCHIVED"]
    .includes(detailQuery.data?.lifecycle ?? "");

  useEffect(() => {
    if (invalidSelection) {
      clearInvalidSelection();
    }
  }, [clearInvalidSelection, invalidSelection]);

  useEffect(() => {
    if (detailQuery.data) {
      setActiveArea(workspaceAreaForLifecycle(detailQuery.data.lifecycle));
    }
  }, [detailQuery.data?.lifecycle]);

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
          <span className="section-kicker">Anlaşma</span>
          <h1>Aktif kuruluşu seçin.</h1>
          <p className="workspace-lead">
            Bu anlaşmayı görüntülemek için üst menüden bir kuruluş seçin.
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
            <h2>Anlaşma yükleniyor</h2>
            <p>Güncel bilgiler ve izin verilen işlemler alınıyor.</p>
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
            <h2>Anlaşma bulunamadı</h2>
            <p>
              Kayıt mevcut olmayabilir veya {selectedMembership.legalName} bu
              anlaşmanın katılımcısı değildir.
            </p>
            <Link className="primary-link-button" to="/app/deals">
              Anlaşmalara dön
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
            <h2>Anlaşma bilgileri alınamadı</h2>
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
          ← Anlaşmalara dön
        </Link>
        <div className="deal-detail-heading">
          <div>
            <span className="deal-reference">{deal.reference}</span>
            <h1>{deal.title}</h1>
            <p className="workspace-lead">
              {deal.description ?? "Bu anlaşma için açıklama girilmemiş."}
            </p>
          </div>
          <div className="deal-status-stack">
            <span className="status-badge" data-status={deal.status}>
              {lifecycleLabel(deal.lifecycle)}
            </span>
            {deal.status === "COMPLETED" ? (
              <span className="settlement-simulation-notice deal-header-simulation-notice" role="status">
                Demo simülasyonu — gerçek para hareketi yok
              </span>
            ) : null}
            {deal.fulfillment?.status === "COMPLETED" && deal.status === "ACTIVE" ? (
              <span className="deal-closure-separation-note" role="status">
                Teslimat tamamlandı; anlaşma henüz kapanmadı
              </span>
            ) : null}
            <span>{formatDate(deal.updatedAt)} tarihinde güncellendi</span>
          </div>
        </div>

        <section className="deal-stage-card" aria-labelledby="deal-stage-title">
          <div>
            <span className="section-kicker">Mevcut aşama</span>
            <h2 id="deal-stage-title">{nextTask(deal)}</h2>
            <p>İlerleme ve izin verilen işlemler sunucunun güncel proje alanına göre gösterilir.</p>
          </div>
          <ol className="deal-stage-list" aria-label="Anlaşma aşamaları">
            {WORKSPACE_AREAS.map((area) => {
              const current = area.id === workspaceAreaForLifecycle(deal.lifecycle);
              return (
                <li key={area.id} aria-current={current ? "step" : undefined}>
                  <button
                    className="deal-stage-button"
                    data-active={area.id === activeArea}
                    type="button"
                    onClick={() => setActiveArea(area.id)}
                  >
                    {area.label}
                  </button>
                </li>
              );
            })}
          </ol>
        </section>

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

        {activeArea === "agreement" ? (
          <>
        {agreementTerminalLifecycle ? (
          <TerminalStatePanel deal={deal} settlementReadOnly={false} />
        ) : null}
        <div className="agreement-stage-layout">
          <aside className="agreement-supporting-rail" aria-label="Anlaşma bilgileri">
        <dl className="deal-facts">
          <div>
            <dt>Referans</dt>
            <dd>{deal.reference}</dd>
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

        <details className="deal-technical-details">
          <summary>Teknik ayrıntılar</summary>
          <dl>
            <div><dt>Durum</dt><dd>{deal.status}</dd></div>
            <div><dt>Lifecycle</dt><dd>{deal.lifecycle}</dd></div>
            <div><dt>Sürüm</dt><dd>{deal.version}</dd></div>
            <div>
              <dt>İzin verilen işlemler</dt>
              <dd>
                {Object.entries(deal.availableActions)
                  .filter(([, available]) => available)
                  .map(([action]) => action)
                  .join(", ") || "Yok"}
              </dd>
            </div>
          </dl>
        </details>

        <section className="workspace-panel deal-participants-panel">
          <div className="panel-heading">
            <span className="section-kicker">Katılımcılar</span>
            <h2>Katılımcı kuruluşlar</h2>
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
          </aside>

          <div className="agreement-stage-main">

        <section className="workspace-panel deal-parties-panel">
          <div className="panel-heading">
            <span className="section-kicker">Taraflar</span>
            <h2>Alıcı ve satıcı atamaları</h2>
            <p>Taraf rolleri tek başına ticari onay anlamına gelmez.</p>
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
                  Sunucunun güncel izni bu anlaşma için düzenlemeye izin vermiyor.
                </p>
              </div>
            </section>
          )}

          <aside className="workspace-panel deal-actions-panel">
            <div className="panel-heading">
              <span className="section-kicker">İşlemler</span>
              <h2>Anlaşma işlemleri</h2>
              <p>
                Kontroller sunucunun güncel izinlerine göre gösterilir.
              </p>
            </div>

            {deal.availableActions.canCancel ? (
              <button
                className="danger-button"
                type="button"
                onClick={() => setCancelConfirmationOpen(true)}
              >
                Anlaşmayı iptal et
              </button>
            ) : (
              <p className="muted-copy">
                Bu anlaşma için kullanılabilir iptal işlemi yok.
              </p>
            )}

            {cancelConfirmationOpen ? (
              <div
                className="confirmation-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="cancel-deal-title"
              >
                <h3 id="cancel-deal-title">Anlaşma iptal edilsin mi?</h3>
                <p>
                  Bu işlem anlaşmayı iptal eder ve düzenleme işlemlerini kapatır.
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
        <DealInvitationManagement
          deal={deal}
          legalEntityId={selectedLegalEntityId}
        />
          </div>
        </div>
          </>
        ) : null}

        {activeArea === "review" ? (
          <>
        <DealContractAnalysis
          deal={deal}
          legalEntityId={selectedLegalEntityId}
        />
        <DealReviewWorkspace deal={deal} legalEntityId={selectedLegalEntityId} />
          </>
        ) : null}

        {activeArea === "approval" ? (
        <DealRatificationPanel deal={deal} legalEntityId={selectedLegalEntityId} />
        ) : null}

        {activeArea === "payment" ? (
        <DealFundingPanel
          key={`${selectedLegalEntityId}:${deal.id}`}
          deal={deal}
          legalEntityId={selectedLegalEntityId}
        />
        ) : null}

        {activeArea === "delivery" ? (
          <>
        <DealFulfillmentPanel
          deal={deal}
          legalEntityId={selectedLegalEntityId}
          readOnly={settlementReadOnly}
          onNavigateToClosure={() => setActiveArea("closure")}
        />
        {!settlementReadOnly ? <DealCaseworkPanel deal={deal} legalEntityId={selectedLegalEntityId} /> : null}
          </>
        ) : null}

        {activeArea === "closure" ? (
          <>
            {closureTerminalLifecycle ? (
              <TerminalStatePanel deal={deal} settlementReadOnly={settlementReadOnly} />
            ) : null}
            <DealSettlementPanel deal={deal} legalEntityId={selectedLegalEntityId} />
          </>
        ) : null}
      </div>
    </main>
  );
}

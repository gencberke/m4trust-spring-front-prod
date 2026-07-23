import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";

import type { DealDetail } from "../deals/dealApi";
import { dealDetailQueryKey } from "../deals/dealQueries";
import { getFundingPlan } from "../funding/fundingApi";
import { fundingPlanQueryKey } from "../funding/fundingQueries";
import { getFulfillment } from "../fulfillment/fulfillmentApi";
import { fulfillmentDetailQueryKey } from "../fulfillment/fulfillmentQueries";
import {
  reconcileReleaseOperation,
  requestSettlementRelease,
  type ReleaseOperation,
  type SettlementDetail,
} from "./settlementApi";
import {
  getSettlementErrorMessage,
  isSettlementNotFound,
  shouldRefetchAfterReconcileError,
  shouldRefetchAfterReleaseError,
  shouldResetSettlementIdempotencyKey,
} from "./settlementErrors";
import {
  RELEASE_OPERATION_LIVE_POLL_STATUSES,
  SETTLEMENT_POLL_INTERVAL_MS,
  releaseOperationQueryKey,
  releaseOperationQueryOptions,
  settlementQueryKey,
  settlementQueryOptions,
} from "./settlementQueries";

const DATE_FORMATTER = new Intl.DateTimeFormat("tr-TR", {
  dateStyle: "long",
  timeStyle: "short",
});

const RELATIVE_FORMATTER = new Intl.RelativeTimeFormat("tr-TR", {
  numeric: "auto",
});

function formatDate(value: string): string {
  return DATE_FORMATTER.format(new Date(value));
}

function formatDeadlineCountdown(releaseEligibleAt: string): string {
  const targetMs = new Date(releaseEligibleAt).getTime();
  const diffMs = targetMs - Date.now();
  if (diffMs <= 0) {
    return `Son tarih: ${formatDate(releaseEligibleAt)} (geçti)`;
  }
  const totalMinutes = Math.ceil(diffMs / 60_000);
  if (totalMinutes < 60) {
    return `Son tarih: ${formatDate(releaseEligibleAt)} (${RELATIVE_FORMATTER.format(totalMinutes, "minute")})`;
  }
  const totalHours = Math.ceil(diffMs / 3_600_000);
  if (totalHours < 48) {
    return `Son tarih: ${formatDate(releaseEligibleAt)} (${RELATIVE_FORMATTER.format(totalHours, "hour")})`;
  }
  const totalDays = Math.ceil(diffMs / 86_400_000);
  return `Son tarih: ${formatDate(releaseEligibleAt)} (${RELATIVE_FORMATTER.format(totalDays, "day")})`;
}

const SETTLEMENT_STATUS_LABELS: Record<string, string> = {
  NOT_READY: "Henüz hazır değil",
  READY: "Kapanışa hazır",
  PROCESSING: "Kapanış işleniyor",
  ON_HOLD: "Beklemede",
  SIMULATED_SETTLED: "Kapanış (simüle) tamamlandı",
  FAILED: "Başarısız",
};

const RELEASE_OPERATION_STATUS_LABELS: Record<string, string> = {
  QUEUED: "Sırada — sonuç bekleniyor",
  PROCESSING: "İşleniyor",
  RECONCILIATION_REQUIRED: "Doğrulanıyor — sonuç bekleniyor",
  SIMULATED_SETTLED: "Kapanış (simüle) tamamlandı",
  SIMULATED_DECLINED: "Reddedildi (simüle)",
  FAILED_BEFORE_DISPATCH: "Gönderim öncesi başarısız",
};

function settlementStatusLabel(status: string): string {
  return SETTLEMENT_STATUS_LABELS[status] ?? status;
}

function releaseOperationStatusLabel(status: string): string {
  return RELEASE_OPERATION_STATUS_LABELS[status] ?? status;
}

interface Props {
  deal: DealDetail;
  legalEntityId: string;
}

export function DealSettlementPanel({ deal, legalEntityId }: Props) {
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<string>();
  const releaseKeyRef = useRef<string | undefined>(undefined);
  const reconcileKeyRef = useRef<string | undefined>(undefined);
  const reconcileDispatchedRef = useRef(false);
  const previousOperationStatusRef = useRef<string | undefined>(undefined);

  const settlementHint =
    Boolean(deal.settlement) ||
    deal.fulfillment?.status === "COMPLETED" ||
    deal.lifecycle === "COMPLETED";

  const settlementQuery = useQuery(
    settlementQueryOptions(legalEntityId, deal.id, settlementHint),
  );
  const settlement = settlementQuery.data;

  const [operationId, setOperationId] = useState<string | undefined>(undefined);
  const summaryOperationId =
    settlement?.currentReleaseOperation?.id ??
    deal.settlement?.currentReleaseOperationId ??
    undefined;

  useEffect(() => {
    if (summaryOperationId) setOperationId(summaryOperationId);
  }, [summaryOperationId]);

  useEffect(() => {
    previousOperationStatusRef.current = undefined;
    reconcileDispatchedRef.current = false;
  }, [operationId]);

  const operationQuery = useQuery({
    ...releaseOperationQueryOptions(legalEntityId, operationId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (!status) return false;
      if (status === "QUEUED" || status === "PROCESSING") {
        return SETTLEMENT_POLL_INTERVAL_MS;
      }
      if (status === "RECONCILIATION_REQUIRED" && reconcileDispatchedRef.current) {
        return SETTLEMENT_POLL_INTERVAL_MS;
      }
      return false;
    },
  });

  const mayRequestRelease =
    deal.availableActions.canRequestRelease === true &&
    settlement?.availableActions.canRequestRelease === true;

  const fulfillmentBootstrapQuery = useQuery({
    queryKey: [...fulfillmentDetailQueryKey(legalEntityId, deal.id), "release-bootstrap"],
    queryFn: ({ signal }) => getFulfillment(legalEntityId, deal.id, signal),
    enabled: mayRequestRelease && Boolean(deal.fulfillment?.fulfillmentId),
  });

  const fundingBootstrapQuery = useQuery({
    queryKey: [...fundingPlanQueryKey(legalEntityId, deal.id), "release-bootstrap"],
    queryFn: ({ signal }) => getFundingPlan(legalEntityId, deal.id, signal),
    enabled:
      mayRequestRelease &&
      Boolean(deal.funding && deal.funding.fundingStatus !== "NOT_CONFIGURED"),
  });

  function refreshAfterMutation() {
    void queryClient.invalidateQueries({
      queryKey: dealDetailQueryKey(legalEntityId, deal.id),
    });
    void queryClient.invalidateQueries({
      queryKey: settlementQueryKey(legalEntityId, deal.id),
    });
  }

  useEffect(() => {
    const status = operationQuery.data?.status;
    if (!status) return;
    const previous = previousOperationStatusRef.current;
    if (previous !== undefined && previous !== status) {
      refreshAfterMutation();
      if (
        status === "SIMULATED_SETTLED" ||
        status === "SIMULATED_DECLINED" ||
        status === "FAILED_BEFORE_DISPATCH"
      ) {
        reconcileDispatchedRef.current = false;
      }
    }
    previousOperationStatusRef.current = status;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [operationQuery.data?.status]);

  const releaseMutation = useMutation({
    mutationFn: (input: {
      settlement: SettlementDetail;
      expectedFulfillmentVersion: number;
      expectedFundingUnitVersion: number;
    }) => {
      releaseKeyRef.current ??= crypto.randomUUID();
      return requestSettlementRelease(
        legalEntityId,
        deal.id,
        {
          expectedDealVersion: deal.version,
          expectedSettlementVersion: input.settlement.version,
          expectedFulfillmentVersion: input.expectedFulfillmentVersion,
          expectedFundingUnitVersion: input.expectedFundingUnitVersion,
        },
        releaseKeyRef.current,
      );
    },
    onSuccess: (created) => {
      releaseKeyRef.current = undefined;
      setOperationId(created.id);
      queryClient.setQueryData(
        releaseOperationQueryKey(legalEntityId, created.id),
        created,
      );
      setNotice("Kapanış başlatıldı; sonuç izleniyor.");
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetSettlementIdempotencyKey(error, "release")) {
        releaseKeyRef.current = undefined;
      }
      if (shouldRefetchAfterReleaseError(error)) refreshAfterMutation();
    },
  });

  const reconcileMutation = useMutation({
    mutationFn: (operation: ReleaseOperation) => {
      reconcileKeyRef.current ??= crypto.randomUUID();
      return reconcileReleaseOperation(
        legalEntityId,
        operation.id,
        { expectedVersion: operation.version },
        reconcileKeyRef.current,
      );
    },
    onSuccess: (updated) => {
      reconcileKeyRef.current = undefined;
      reconcileDispatchedRef.current = true;
      queryClient.setQueryData(
        releaseOperationQueryKey(legalEntityId, updated.id),
        updated,
      );
      setNotice("Doğrulama isteği gönderildi; sonuç izleniyor.");
    },
    onError: (error) => {
      if (shouldResetSettlementIdempotencyKey(error, "reconcile")) {
        reconcileKeyRef.current = undefined;
      }
      if (shouldRefetchAfterReconcileError(error)) refreshAfterMutation();
    },
  });

  if (!settlementHint) {
    return null;
  }

  if (settlementQuery.isPending) {
    return (
      <section
        className="workspace-panel settlement-panel"
        aria-labelledby="settlement-title"
      >
        <div className="panel-heading">
          <span className="section-kicker">Kapanış</span>
          <h2 id="settlement-title">Kapanış durumu</h2>
        </div>
        <p className="inline-state" role="status">
          <span className="loading-line" aria-hidden="true" />
          Kapanış bilgisi yükleniyor…
        </p>
      </section>
    );
  }

  if (settlementQuery.isError && !isSettlementNotFound(settlementQuery.error)) {
    return (
      <section
        className="workspace-panel settlement-panel"
        aria-labelledby="settlement-title"
      >
        <div className="panel-heading">
          <span className="section-kicker">Kapanış</span>
          <h2 id="settlement-title">Kapanış durumu</h2>
        </div>
        <div className="form-alert panel-alert" role="alert">
          <p>{getSettlementErrorMessage(settlementQuery.error)}</p>
          <button
            className="secondary-button"
            type="button"
            onClick={() => void settlementQuery.refetch()}
            disabled={settlementQuery.isFetching}
          >
            {settlementQuery.isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
          </button>
        </div>
      </section>
    );
  }

  if (!settlement) {
    return (
      <section
        className="workspace-panel settlement-panel"
        aria-labelledby="settlement-title"
      >
        <div className="panel-heading">
          <span className="section-kicker">Kapanış</span>
          <h2 id="settlement-title">Kapanış durumu</h2>
          <p>
            Teslimat tamamlandıktan sonra kapanış bilgisi burada görüntülenir.
            Anlaşma kapanışı, teslimat tamamlanmasından ayrı bir adımdır.
          </p>
        </div>
        <p className="muted-copy" role="status">
          Kapanış kaydı henüz sunulmuyor; sunucu güncellemesi bekleniyor.
        </p>
      </section>
    );
  }

  const operationSummary = settlement.currentReleaseOperation;
  const currentOperation = operationQuery.data ?? null;

  const mayReconcile =
    deal.availableActions.canReconcileRelease === true &&
    settlement.availableActions.canReconcileRelease === true &&
    currentOperation?.availableActions.canReconcile === true;

  const isSimulated = settlement.mode === "DEMO_SIMULATED";
  const reconciliationRequired =
    currentOperation?.reconciliationRequired === true ||
    operationSummary?.reconciliationRequired === true;
  const operationStatus =
    currentOperation?.status ?? operationSummary?.status ?? null;
  const operationPolling =
    operationStatus !== null &&
    RELEASE_OPERATION_LIVE_POLL_STATUSES.has(operationStatus);

  function handleRelease() {
    if (!settlement) return;
    const fulfillmentVersion = fulfillmentBootstrapQuery.data?.version;
    const fundingUnitVersion = fundingBootstrapQuery.data?.fundingUnit.version;
    if (fulfillmentVersion === undefined || fundingUnitVersion === undefined) {
      setNotice(undefined);
      void fulfillmentBootstrapQuery.refetch();
      void fundingBootstrapQuery.refetch();
      return;
    }
    setNotice(undefined);
    releaseMutation.mutate({
      settlement,
      expectedFulfillmentVersion: fulfillmentVersion,
      expectedFundingUnitVersion: fundingUnitVersion,
    });
  }

  const releaseBootstrapPending =
    mayRequestRelease &&
    (fulfillmentBootstrapQuery.isPending || fundingBootstrapQuery.isPending);

  return (
    <section
      className="workspace-panel settlement-panel"
      aria-labelledby="settlement-title"
    >
      <div className="panel-heading">
        <span className="section-kicker">Kapanış</span>
        <h2 id="settlement-title">Kapanış</h2>
        <p>
          Teslimat tamamlandıktan sonra anlaşma burada kapanır. Bu adım,
          teslimatın tamamlanmasından ayrıdır; anlaşma yalnızca simüle
          kapanış doğrulandığında sonlanır.
        </p>
      </div>

      {isSimulated ? (
        <p className="settlement-simulation-notice" role="status">
          Demo simülasyonu — gerçek para hareketi yok
        </p>
      ) : null}

      <div className="settlement-summary" role="status">
        <span
          className="settlement-status-badge"
          data-status={settlement.status}
        >
          {settlementStatusLabel(settlement.status)}
        </span>
        {deal.status === "COMPLETED" ? (
          <span className="settlement-closure-badge" role="status">
            Anlaşma kapatıldı
          </span>
        ) : deal.fulfillment?.status === "COMPLETED" ? (
          <span className="settlement-fulfillment-note">
            Teslimat tamamlandı — anlaşma hâlâ aktif
          </span>
        ) : null}
      </div>

      {notice ? (
        <p className="success-notice workspace-notice" role="status">
          {notice}
        </p>
      ) : null}

      <div className="settlement-plan-card">
        <dl className="settlement-summary-list">
          {settlement.disputeWindowDays !== null ? (
            <div>
              <dt>İtiraz penceresi</dt>
              <dd>
                {settlement.disputeWindowDays === 0
                  ? "Yok (0 gün)"
                  : `${settlement.disputeWindowDays} gün`}
              </dd>
            </div>
          ) : null}
          {settlement.releaseEligibleAt ? (
            <div>
              <dt>Kapanışa uygunluk</dt>
              <dd>
                <time dateTime={settlement.releaseEligibleAt}>
                  {formatDeadlineCountdown(settlement.releaseEligibleAt)}
                </time>
              </dd>
            </div>
          ) : null}
          <div>
            <dt>Kapanış durumu</dt>
            <dd>
              <span
                className="settlement-status-badge"
                data-status={settlement.status}
              >
                {settlementStatusLabel(settlement.status)}
              </span>
            </dd>
          </div>
          <div>
            <dt>Güncellendi</dt>
            <dd>{formatDate(settlement.updatedAt)}</dd>
          </div>
        </dl>

        {operationSummary || currentOperation ? (
          <div className="settlement-operation-card">
            <div className="settlement-operation-heading">
              <span>Kapanış işlemi</span>
              {operationStatus ? (
                <span
                  className="settlement-operation-status-badge"
                  data-status={operationStatus}
                >
                  {releaseOperationStatusLabel(operationStatus)}
                </span>
              ) : null}
            </div>
            {operationQuery.isPending && !currentOperation ? (
              <p className="inline-state" role="status">
                <span className="loading-line" aria-hidden="true" />
                İşlem ayrıntıları yükleniyor…
              </p>
            ) : currentOperation ? (
              <dl className="settlement-summary-list">
                <div>
                  <dt>Başlatıldı</dt>
                  <dd>{formatDate(currentOperation.createdAt)}</dd>
                </div>
                <div>
                  <dt>Güncellendi</dt>
                  <dd>{formatDate(currentOperation.updatedAt)}</dd>
                </div>
              </dl>
            ) : null}

            {reconciliationRequired ? (
              <p className="settlement-reconciliation-notice" role="status">
                Kapanış sonucu henüz kesinleşmedi; bu{" "}
                <strong>başarısızlık değildir</strong>. Aynı işlem için
                doğrulama sonucu bekleniyor.
              </p>
            ) : null}

            {operationPolling ? (
              <p className="muted-copy settlement-poll-hint" role="status">
                Sonuç otomatik izleniyor…
              </p>
            ) : null}
          </div>
        ) : (
          <p className="muted-copy">
            Henüz kapanış işlemi başlatılmadı.
          </p>
        )}

        {releaseMutation.isError ? (
          <p className="form-alert workspace-notice" role="alert">
            {getSettlementErrorMessage(releaseMutation.error)}
          </p>
        ) : null}
        {reconcileMutation.isError ? (
          <p className="form-alert workspace-notice" role="alert">
            {getSettlementErrorMessage(reconcileMutation.error)}
          </p>
        ) : null}

        <div className="settlement-actions">
          {mayRequestRelease ? (
            <button
              className="primary-button"
              type="button"
              disabled={releaseMutation.isPending || releaseBootstrapPending}
              onClick={handleRelease}
            >
              {releaseMutation.isPending || releaseBootstrapPending
                ? "Hazırlanıyor…"
                : "Simüle kapanışı başlat"}
            </button>
          ) : null}
          {mayReconcile && currentOperation ? (
            <button
              className="secondary-button"
              type="button"
              disabled={reconcileMutation.isPending}
              onClick={() => {
                setNotice(undefined);
                reconcileMutation.mutate(currentOperation);
              }}
            >
              {reconcileMutation.isPending ? "Gönderiliyor…" : "İşlemi doğrula"}
            </button>
          ) : null}
        </div>
      </div>
    </section>
  );
}

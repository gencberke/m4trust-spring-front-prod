import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";

import { decimalFromMinor, formatDate, StatusBadge } from "@/shared";
import styles from "./Funding.module.css";
import type { DealDetail } from "../deals/dealApi";
import { dealDetailQueryKey } from "../deals/dealQueries";
import {
  createFundingPlan,
  initiatePaymentOperation,
  reconcilePaymentOperation,
  type FundingPlanDetail,
  type FundingUnit,
  type PaymentOperation,
} from "./fundingApi";
import {
  getFundingErrorMessage,
  isFundingPlanNotFound,
  shouldRefetchAfterCreatePlanError,
  shouldRefetchAfterInitiateError,
  shouldRefetchAfterReconcileError,
  shouldResetFundingIdempotencyKey,
} from "./fundingErrors";
import {
  fundingPlanQueryKey,
  fundingPlanQueryOptions,
  paymentOperationQueryKey,
  paymentOperationQueryOptions,
} from "./fundingQueries";

const FUNDING_STATUS_LABELS: Record<string, string> = {
  NOT_CONFIGURED: "Yapılandırılmadı",
  PLANNED: "Planlandı",
  PENDING: "Ödeme bekleniyor",
  PARTIALLY_FUNDED: "Kısmen fonlandı",
  FUNDED: "Fonlandı",
};

const FUNDING_UNIT_STATUS_LABELS: Record<string, string> = {
  PLANNED: "Planlandı",
  PENDING: "Ödeme bekleniyor",
  FUNDED: "Fonlandı",
  FAILED: "Başarısız — yeniden denenebilir",
};

const PAYMENT_OPERATION_STATUS_LABELS: Record<string, string> = {
  CREATED: "Oluşturuldu — sonuç bekleniyor",
  SUCCEEDED: "Başarılı",
  DECLINED: "Reddedildi",
  UNCONFIRMED: "Doğrulanıyor — sonuç bekleniyor",
};

function fundingStatusLabel(status: string): string {
  return FUNDING_STATUS_LABELS[status] ?? status;
}

function fundingUnitStatusLabel(status: string): string {
  return FUNDING_UNIT_STATUS_LABELS[status] ?? status;
}

function paymentOperationStatusLabel(status: string): string {
  return PAYMENT_OPERATION_STATUS_LABELS[status] ?? status;
}

interface Props {
  deal: DealDetail;
  legalEntityId: string;
}

export function DealFundingPanel({ deal, legalEntityId }: Props) {
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<string>();
  const [createdPlan, setCreatedPlan] = useState<FundingPlanDetail>();

  const createKeyRef = useRef<string | undefined>(undefined);
  const initiateKeyRef = useRef<string | undefined>(undefined);
  const reconcileKeyRef = useRef<string | undefined>(undefined);
  const reconcileDispatchedRef = useRef(false);
  const previousOperationStatusRef = useRef<string | undefined>(undefined);

  const funding = deal.funding ?? null;
  const hasPlan = Boolean(
    createdPlan || (funding && funding.fundingStatus !== "NOT_CONFIGURED"),
  );

  const planQuery = useQuery(
    fundingPlanQueryOptions(legalEntityId, deal.id, hasPlan),
  );
  const plan = planQuery.data ?? createdPlan;

  const [initiatedOperationId, setInitiatedOperationId] = useState<
    string | undefined
  >(undefined);
  const planOperationId = plan?.fundingUnit.currentOperation?.id;
  const operationId = initiatedOperationId ?? planOperationId;

  useEffect(() => {
    previousOperationStatusRef.current = undefined;
    reconcileDispatchedRef.current = false;
  }, [operationId]);

  const operationQuery = useQuery({
    ...paymentOperationQueryOptions(legalEntityId, operationId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === "CREATED") return 3000;
      if (status === "UNCONFIRMED" && reconcileDispatchedRef.current)
        return 3000;
      return false;
    },
  });

  function refreshAfterMutation() {
    void queryClient.invalidateQueries({
      queryKey: dealDetailQueryKey(legalEntityId, deal.id),
    });
    void queryClient.invalidateQueries({
      queryKey: fundingPlanQueryKey(legalEntityId, deal.id),
    });
  }

  useEffect(() => {
    const status = operationQuery.data?.status;
    if (!status) return;
    const previous = previousOperationStatusRef.current;
    if (previous !== undefined && previous !== status) {
      refreshAfterMutation();
      if (status === "SUCCEEDED" || status === "DECLINED") {
        reconcileDispatchedRef.current = false;
      }
    }
    previousOperationStatusRef.current = status;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [operationQuery.data?.status]);

  const createMutation = useMutation({
    mutationFn: () => {
      createKeyRef.current ??= crypto.randomUUID();
      return createFundingPlan(
        legalEntityId,
        deal.id,
        { expectedVersion: deal.version },
        createKeyRef.current,
      );
    },
    onSuccess: (created) => {
      createKeyRef.current = undefined;
      setCreatedPlan(created);
      queryClient.setQueryData(
        fundingPlanQueryKey(legalEntityId, deal.id),
        created,
      );
      setNotice("Ödeme hazırlığı tamamlandı.");
      refreshAfterMutation();
      if (created.fundingUnit.availableActions.canInitiatePayment === true) {
        initiateMutation.mutate(created.fundingUnit);
      } else {
        setNotice(
          "Ödeme hazırlığı tamamlandı; işlem başlatma izni sunucudan bekleniyor.",
        );
      }
    },
    onError: (error) => {
      if (shouldResetFundingIdempotencyKey(error, "create"))
        createKeyRef.current = undefined;
      if (shouldRefetchAfterCreatePlanError(error)) refreshAfterMutation();
    },
  });

  const initiateMutation = useMutation({
    mutationFn: (unit: FundingUnit) => {
      initiateKeyRef.current ??= crypto.randomUUID();
      return initiatePaymentOperation(
        legalEntityId,
        unit.id,
        { expectedVersion: unit.version },
        initiateKeyRef.current,
      );
    },
    onSuccess: (created) => {
      initiateKeyRef.current = undefined;
      setInitiatedOperationId(created.id);
      queryClient.setQueryData(
        paymentOperationQueryKey(legalEntityId, created.id),
        created,
      );
      setNotice("Ödeme başlatıldı; sonuç izleniyor.");
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetFundingIdempotencyKey(error, "initiate"))
        initiateKeyRef.current = undefined;
      if (shouldRefetchAfterInitiateError(error)) refreshAfterMutation();
    },
  });

  const reconcileMutation = useMutation({
    mutationFn: (operation: PaymentOperation) => {
      reconcileKeyRef.current ??= crypto.randomUUID();
      return reconcilePaymentOperation(
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
        paymentOperationQueryKey(legalEntityId, updated.id),
        updated,
      );
      setNotice("Doğrulama isteği gönderildi; sonuç izleniyor.");
    },
    onError: (error) => {
      if (shouldResetFundingIdempotencyKey(error, "reconcile"))
        reconcileKeyRef.current = undefined;
      if (shouldRefetchAfterReconcileError(error)) refreshAfterMutation();
    },
  });

  if (!funding) {
    return (
      <section
        className={`workspace-panel ${styles.fundingPanel}`}
        aria-labelledby="funding-title"
      >
        <div className="panel-heading">
          <span className="section-kicker">Ödeme</span>
          <h2 id="funding-title">Ödeme durumu</h2>
        </div>
        <p className="muted-copy">
          Bu anlaşma için ödeme bilgisi şu anda sunulmuyor; bölüm salt okunur
          kabul edilir.
        </p>
      </section>
    );
  }

  const mayCreatePlan = deal.availableActions.canCreateFundingPlan === true;
  const currentOperation =
    operationQuery.data ?? plan?.fundingUnit.currentOperation ?? null;
  const unit = plan?.fundingUnit;
  const mayInitiate =
    deal.availableActions.canInitiateFunding === true &&
    unit?.availableActions.canInitiatePayment === true;
  const mayReconcile =
    deal.availableActions.canReconcilePaymentOperation === true &&
    currentOperation?.availableActions.canReconcile === true;

  return (
    <section
      className={`workspace-panel ${styles.fundingPanel}`}
      aria-labelledby="funding-title"
    >
      <div className="panel-heading">
        <span className="section-kicker">Ödeme</span>
        <h2 id="funding-title">Ödemeyi güvenceye al</h2>
        <p>
          Tutar, iki tarafın onayladığı koşullardan gelir ve burada
          değiştirilemez. Sonuç, sunucunun güncel ödeme kaydından izlenir.
        </p>
      </div>

      <div className={styles.fundingSummary} role="status">
        <StatusBadge
          domain="funding"
          status={funding.fundingStatus}
          label={fundingStatusLabel(funding.fundingStatus)}
        />
        {funding.amountMinor !== null && funding.currency !== null ? (
          <strong>
            {decimalFromMinor(funding.amountMinor)} {funding.currency}
          </strong>
        ) : null}
      </div>

      {notice ? (
        <p className="success-notice workspace-notice" role="status">
          {notice}
        </p>
      ) : null}

      {!hasPlan ? (
        <div className={styles.fundingNotConfigured}>
          <p className="muted-copy">Ödeme hazırlığı henüz başlatılmadı.</p>
          {mayCreatePlan ? (
            <>
              {createMutation.isError ? (
                <p className="form-alert workspace-notice" role="alert">
                  {getFundingErrorMessage(createMutation.error)}
                </p>
              ) : null}
              <button
                className="primary-button"
                type="button"
                disabled={createMutation.isPending}
                onClick={() => {
                  setNotice(undefined);
                  createMutation.mutate();
                }}
              >
                {createMutation.isPending
                  ? "Hazırlanıyor…"
                  : "Ödemeyi güvenceye al"}
              </button>
            </>
          ) : null}
        </div>
      ) : (
        <FundingPlanSection
          planQuery={planQuery}
          plan={plan}
          currentOperation={currentOperation}
          mayInitiate={mayInitiate}
          mayReconcile={mayReconcile}
          initiatePending={initiateMutation.isPending}
          reconcilePending={reconcileMutation.isPending}
          initiateError={initiateMutation.error}
          reconcileError={reconcileMutation.error}
          onInitiate={() => {
            if (!unit) return;
            setNotice(undefined);
            initiateMutation.mutate(unit);
          }}
          onReconcile={() => {
            if (!currentOperation) return;
            setNotice(undefined);
            reconcileMutation.mutate(currentOperation);
          }}
          onRetryPlanLoad={() => void planQuery.refetch()}
        />
      )}
    </section>
  );
}

interface FundingPlanSectionProps {
  planQuery: {
    isPending: boolean;
    isError: boolean;
    error: unknown;
    isFetching: boolean;
  };
  plan: FundingPlanDetail | undefined;
  currentOperation: PaymentOperation | null;
  mayInitiate: boolean;
  mayReconcile: boolean;
  initiatePending: boolean;
  reconcilePending: boolean;
  initiateError: unknown;
  reconcileError: unknown;
  onInitiate: () => void;
  onReconcile: () => void;
  onRetryPlanLoad: () => void;
}

function FundingPlanSection({
  planQuery,
  plan,
  currentOperation,
  mayInitiate,
  mayReconcile,
  initiatePending,
  reconcilePending,
  initiateError,
  reconcileError,
  onInitiate,
  onReconcile,
  onRetryPlanLoad,
}: FundingPlanSectionProps) {
  if (planQuery.isPending) {
    return (
      <p className="inline-state" role="status">
        <span className="loading-line" aria-hidden="true" />
        Ödeme hazırlığı yükleniyor…
      </p>
    );
  }

  if (planQuery.isError && !isFundingPlanNotFound(planQuery.error)) {
    return (
      <div className="form-alert panel-alert" role="alert">
        <p>{getFundingErrorMessage(planQuery.error)}</p>
        <button
          className="secondary-button"
          type="button"
          onClick={onRetryPlanLoad}
          disabled={planQuery.isFetching}
        >
          {planQuery.isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
        </button>
      </div>
    );
  }

  if (!plan) {
    return <p className="muted-copy">Ödeme hazırlığı henüz bulunamadı.</p>;
  }

  const unit = plan.fundingUnit;
  const isRetry = unit.status === "FAILED";
  const reconciliationRequired =
    currentOperation?.reconciliationRequired === true;
  const isSimulated =
    plan.mode === "DEMO_SIMULATED" ||
    currentOperation?.mode === "DEMO_SIMULATED";

  return (
    <div className={styles.fundingPlanCard}>
      {isSimulated ? (
        <p className={styles.fundingSimulationNotice} role="status">
          Demo simülasyonu — gerçek para hareketi yok
        </p>
      ) : null}

      <dl className={styles.fundingSummaryList}>
        <div>
          <dt>Tutar</dt>
          <dd>
            <strong>
              {decimalFromMinor(plan.amountMinor)} {plan.currency}
            </strong>
          </dd>
        </div>
        <div>
          <dt>Ödeme durumu</dt>
          <dd>
            <StatusBadge
              domain="funding"
              status={plan.fundingStatus}
              label={fundingStatusLabel(plan.fundingStatus)}
            />
          </dd>
        </div>
        <div>
          <dt>Oluşturuldu</dt>
          <dd>{formatDate(plan.createdAt)}</dd>
        </div>
        <div>
          <dt>Güncellendi</dt>
          <dd>{formatDate(plan.updatedAt)}</dd>
        </div>
      </dl>

      <div className={styles.fundingUnitCard}>
        <div className={styles.fundingUnitHeading}>
          <span>Ödeme adımı #{unit.sequenceNo}</span>
          <StatusBadge
            domain="fundingUnit"
            status={unit.status}
            label={fundingUnitStatusLabel(unit.status)}
          />
        </div>

        {currentOperation ? (
          <div className={styles.fundingOperationCard}>
            <div className={styles.fundingOperationHeading}>
              <span>Son ödeme işlemi</span>
              <StatusBadge
                domain="fundingOperation"
                status={currentOperation.status}
                label={paymentOperationStatusLabel(currentOperation.status)}
              />
            </div>
            <dl className={styles.fundingSummaryList}>
              <div>
                <dt>Başlatıldı</dt>
                <dd>{formatDate(currentOperation.createdAt)}</dd>
              </div>
              <div>
                <dt>Güncellendi</dt>
                <dd>{formatDate(currentOperation.updatedAt)}</dd>
              </div>
            </dl>

            {currentOperation.providerReference ? (
              <details className="funding-technical-details">
                <summary>Teknik işlem ayrıntıları</summary>
                <p>
                  <code>{currentOperation.providerReference}</code>
                </p>
              </details>
            ) : null}

            {reconciliationRequired ? (
              <p className={styles.fundingReconciliationNotice} role="status">
                Ödeme sonucu henüz kesinleşmedi; bu{" "}
                <strong>başarısızlık değildir</strong>. Aynı işlem için
                doğrulama sonucu bekleniyor.
              </p>
            ) : null}
          </div>
        ) : (
          <p className="muted-copy">
            Bu ödeme adımı için henüz işlem başlatılmadı.
          </p>
        )}

        {initiateError ? (
          <p className="form-alert workspace-notice" role="alert">
            {getFundingErrorMessage(initiateError)}
          </p>
        ) : null}
        {reconcileError ? (
          <p className="form-alert workspace-notice" role="alert">
            {getFundingErrorMessage(reconcileError)}
          </p>
        ) : null}

        <div className={styles.fundingUnitActions}>
          {mayInitiate ? (
            <button
              className="primary-button"
              type="button"
              disabled={initiatePending}
              onClick={onInitiate}
            >
              {initiatePending
                ? "Başlatılıyor…"
                : isRetry
                  ? "Ödemeyi güvenceye al"
                  : "Ödemeyi güvenceye al"}
            </button>
          ) : null}
          {mayReconcile ? (
            <button
              className="secondary-button"
              type="button"
              disabled={reconcilePending}
              onClick={onReconcile}
            >
              {reconcilePending ? "Gönderiliyor…" : "İşlemi doğrula"}
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
}

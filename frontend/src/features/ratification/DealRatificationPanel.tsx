import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState, type FormEvent } from "react";

import type { components } from "../../generated/core-api";
import type { DealDetail } from "../deals/dealApi";
import { dealDetailQueryKey } from "../deals/dealQueries";
import { getRuleSetVersion } from "../review/reviewApi";
import {
  approveRatificationPackage,
  createRatificationPackage,
  rejectRatificationPackage,
  type RatificationCommercialTerms,
  type RatificationPackageDetail,
} from "./ratificationApi";
import {
  getRatificationErrorMessage,
  getRatificationFieldErrors,
  shouldRefetchAfterActionError,
  shouldRefetchAfterCreateError,
  shouldResetRatificationIdempotencyKey,
  type RatificationFieldError,
} from "./ratificationErrors";
import { ratificationPackageHistoryQueryKey, ratificationPackageHistoryQueryOptions } from "./ratificationQueries";

type StructuredValue = components["schemas"]["RuleSetStructuredValue"];

const DATE_FORMATTER = new Intl.DateTimeFormat("tr-TR", {
  dateStyle: "long",
  timeStyle: "short",
});

function formatDate(value: string): string {
  return DATE_FORMATTER.format(new Date(value));
}

const PACKAGE_STATUS_LABELS: Record<string, string> = {
  PENDING: "Onay bekliyor",
  RATIFIED: "Onaylandı (RATIFIED)",
  REJECTED: "Reddedildi",
  SUPERSEDED: "Yerini aldı (superseded)",
};

function packageStatusLabel(status: string): string {
  return PACKAGE_STATUS_LABELS[status] ?? status;
}

/** Converts amountMinor (integer minor units) to a decimal display string; no binary float involved. */
function decimalFromMinor(amountMinor: number): string {
  const digits = String(amountMinor).padStart(3, "0");
  return `${digits.slice(0, -2)}.${digits.slice(-2)}`;
}

/** Converts decimal text to a positive integer minor-unit amount using BigInt; float never touches the wire. */
function decimalToMinor(text: string): number | undefined {
  const normalized = text.trim().replace(",", ".");
  if (!/^\d+(?:\.\d+)?$/.test(normalized)) return undefined;
  const [wholeRaw, fractionRaw = ""] = normalized.split(".");
  if (fractionRaw.length > 2) return undefined;
  const integer = BigInt(`${wholeRaw}${fractionRaw.padEnd(2, "0")}`);
  if (integer < 1n || integer > BigInt(Number.MAX_SAFE_INTEGER)) return undefined;
  return Number(integer);
}

function formatStructuredValue(value: StructuredValue): string {
  switch (value.type) {
    case "TEXT":
      return value.value;
    case "MONEY":
      return `${decimalFromMinor(value.amountMinor)} ${value.currency}`;
    case "PERCENTAGE":
      return `%${decimalFromMinor(value.basisPoints)}`;
    case "DURATION":
      return `${value.valueSeconds} saniye`;
    case "DATE":
      return value.value;
    case "BOOLEAN":
      return value.value ? "Evet" : "Hayır";
    case "QUANTITY":
      return `${value.value} ${value.unit}`;
    default:
      return "Bilinmeyen değer";
  }
}

function truncateHex(value: string, head = 10, tail = 8): string {
  return value.length > head + tail + 1
    ? `${value.slice(0, head)}…${value.slice(-tail)}`
    : value;
}

function HexValue({ value, label }: { value: string; label: string }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <span className="ratification-hex">
      <code>{expanded ? value : truncateHex(value)}</code>
      <button
        className="text-button"
        type="button"
        onClick={() => setExpanded((current) => !current)}
      >
        {expanded ? "Kısalt" : `Tam ${label} göster`}
      </button>
    </span>
  );
}

interface MoneySuggestion {
  ruleReference: string;
  title: string;
  amountMinor: number;
  currency: string;
}

interface MoneySourceRule {
  ruleReference: string;
  title: string;
  structuredValue: StructuredValue;
}

function extractMoneySuggestions(rules: MoneySourceRule[] | undefined): MoneySuggestion[] {
  if (!rules) return [];
  return rules
    .filter((rule) => rule.structuredValue.type === "MONEY")
    .map((rule) => {
      const value = rule.structuredValue as Extract<StructuredValue, { type: "MONEY" }>;
      return {
        ruleReference: rule.ruleReference,
        title: rule.title,
        amountMinor: value.amountMinor,
        currency: value.currency,
      };
    });
}

interface Props {
  deal: DealDetail;
  legalEntityId: string;
}

export function DealRatificationPanel({ deal, legalEntityId }: Props) {
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<string>();
  const [confirmAction, setConfirmAction] = useState<"approve" | "reject" | undefined>();

  const createKeyRef = useRef<string | undefined>(undefined);
  const approveKeyRef = useRef<string | undefined>(undefined);
  const rejectKeyRef = useRef<string | undefined>(undefined);

  const historyQuery = useQuery(
    ratificationPackageHistoryQueryOptions(legalEntityId, deal.id),
  );

  const ruleSetSummary = deal.currentRuleSet;
  const ruleSetVersionQuery = useQuery({
    queryKey: ["rule-set", legalEntityId, deal.id, ruleSetSummary?.id],
    queryFn: ({ signal }) =>
      getRuleSetVersion(legalEntityId, deal.id, ruleSetSummary!.id, signal),
    enabled: Boolean(ruleSetSummary) && deal.availableActions.canCreateRatificationPackage === true,
  });
  const moneySuggestions = extractMoneySuggestions(ruleSetVersionQuery.data?.rules);

  function refreshAfterMutation() {
    void queryClient.invalidateQueries({
      queryKey: dealDetailQueryKey(legalEntityId, deal.id),
    });
    void queryClient.invalidateQueries({
      queryKey: ratificationPackageHistoryQueryKey(legalEntityId, deal.id),
    });
  }

  const createMutation = useMutation({
    mutationFn: (commercialTerms: RatificationCommercialTerms) => {
      createKeyRef.current ??= crypto.randomUUID();
      return createRatificationPackage(
        legalEntityId,
        deal.id,
        { expectedVersion: deal.version, commercialTerms },
        createKeyRef.current,
      );
    },
    onSuccess: (created) => {
      createKeyRef.current = undefined;
      setNotice(`Package oluşturuldu (sürüm ${created.version}); taraf onayı bekleniyor.`);
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetRatificationIdempotencyKey(error)) createKeyRef.current = undefined;
      if (shouldRefetchAfterCreateError(error)) refreshAfterMutation();
    },
  });

  const approveMutation = useMutation({
    mutationFn: (targetPackage: RatificationPackageDetail) => {
      approveKeyRef.current ??= crypto.randomUUID();
      return approveRatificationPackage(
        legalEntityId,
        deal.id,
        targetPackage.id,
        { expectedPackageVersion: targetPackage.version },
        approveKeyRef.current,
      );
    },
    onSuccess: (updated) => {
      approveKeyRef.current = undefined;
      setConfirmAction(undefined);
      setNotice(
        updated.status === "RATIFIED"
          ? "Package RATIFIED oldu; Deal ACTIVE durumuna geçti."
          : "Onayınız kaydedildi; diğer tarafın onayı bekleniyor.",
      );
      refreshAfterMutation();
    },
    onError: (error) => {
      setConfirmAction(undefined);
      if (shouldResetRatificationIdempotencyKey(error)) approveKeyRef.current = undefined;
      if (shouldRefetchAfterActionError(error)) refreshAfterMutation();
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (targetPackage: RatificationPackageDetail) => {
      rejectKeyRef.current ??= crypto.randomUUID();
      return rejectRatificationPackage(
        legalEntityId,
        deal.id,
        targetPackage.id,
        { expectedPackageVersion: targetPackage.version },
        rejectKeyRef.current,
      );
    },
    onSuccess: () => {
      rejectKeyRef.current = undefined;
      setConfirmAction(undefined);
      setNotice("Package reddedildi. Devam etmek için yeni bir package oluşturulmalı.");
      refreshAfterMutation();
    },
    onError: (error) => {
      setConfirmAction(undefined);
      if (shouldResetRatificationIdempotencyKey(error)) rejectKeyRef.current = undefined;
      if (shouldRefetchAfterActionError(error)) refreshAfterMutation();
    },
  });

  const ratification = deal.ratification ?? null;
  if (!ratification) {
    return (
      <section className="workspace-panel ratification-panel" aria-labelledby="ratification-title">
        <div className="panel-heading">
          <span className="section-kicker">Ratification</span>
          <h2 id="ratification-title">Ticari onay (ratification)</h2>
        </div>
        <p className="muted-copy">
          Bu Deal için ratification projeksiyonu şu anda sunulmuyor; bölüm salt
          okunur kabul edilir.
        </p>
      </section>
    );
  }

  const readiness = ratification.readiness;
  const currentPackage = ratification.currentPackage;
  const mayCreate = deal.availableActions.canCreateRatificationPackage === true;
  const mayApprove =
    deal.availableActions.canApproveRatification === true &&
    currentPackage?.availableActions.canApprove === true;
  const mayReject =
    deal.availableActions.canRejectRatification === true &&
    currentPackage?.availableActions.canReject === true;

  return (
    <section className="workspace-panel ratification-panel" aria-labelledby="ratification-title">
      <div className="panel-heading">
        <span className="section-kicker">Ratification</span>
        <h2 id="ratification-title">Ticari onay (ratification)</h2>
        <p>
          İki tarafın ADMIN kullanıcısı aynı immutable package'ı onayladığında
          Deal ACTIVE olur. Onay, şirketi bağlayan rızadır.
        </p>
      </div>

      <div className="ratification-readiness" role="status" data-ready={readiness === "READY"}>
        <strong>
          {readiness === "READY" ? "Hazır" : "Hazır değil"}
        </strong>
        <span>
          {readiness === "READY"
            ? "Taraflar, kabul edilmiş rule-set ve güncel belge mevcut."
            : "Package oluşturmak için taraflar, kabul edilmiş rule-set ve güncel bir belge gereklidir."}
        </span>
      </div>

      {notice ? (
        <p className="success-notice workspace-notice" role="status">
          {notice}
        </p>
      ) : null}
      {approveMutation.isError ? (
        <p className="form-alert workspace-notice" role="alert">
          {getRatificationErrorMessage(approveMutation.error)}
        </p>
      ) : null}
      {rejectMutation.isError ? (
        <p className="form-alert workspace-notice" role="alert">
          {getRatificationErrorMessage(rejectMutation.error)}
        </p>
      ) : null}

      {currentPackage ? (
        <CurrentPackage
          pkg={currentPackage}
          mayApprove={mayApprove}
          mayReject={mayReject}
          onApprove={() => setConfirmAction("approve")}
          onReject={() => setConfirmAction("reject")}
        />
      ) : (
        <p className="muted-copy">Bu Deal için henüz bir ratification package oluşturulmadı.</p>
      )}

      {confirmAction === "approve" && currentPackage ? (
        <ApproveConfirmation
          pkg={currentPackage}
          pending={approveMutation.isPending}
          onCancel={() => setConfirmAction(undefined)}
          onConfirm={() => approveMutation.mutate(currentPackage)}
        />
      ) : null}
      {confirmAction === "reject" && currentPackage ? (
        <RejectConfirmation
          pkg={currentPackage}
          pending={rejectMutation.isPending}
          onCancel={() => setConfirmAction(undefined)}
          onConfirm={() => rejectMutation.mutate(currentPackage)}
        />
      ) : null}

      {mayCreate ? (
        <CreatePackageForm
          hasCurrentPackage={Boolean(currentPackage)}
          ready={readiness === "READY"}
          suggestions={moneySuggestions}
          suggestionsLoading={ruleSetVersionQuery.isPending && Boolean(ruleSetSummary)}
          pending={createMutation.isPending}
          error={createMutation.error}
          onSubmit={(commercialTerms) => {
            setNotice(undefined);
            createMutation.mutate(commercialTerms);
          }}
        />
      ) : null}

      <PackageHistory
        items={historyQuery.data?.items ?? []}
        loading={historyQuery.isPending}
        error={historyQuery.error}
        currentPackageId={currentPackage?.id}
        onRetry={() => void historyQuery.refetch()}
      />
    </section>
  );
}

function CurrentPackage({
  pkg,
  mayApprove,
  mayReject,
  onApprove,
  onReject,
}: {
  pkg: RatificationPackageDetail;
  mayApprove: boolean;
  mayReject: boolean;
  onApprove: () => void;
  onReject: () => void;
}) {
  const snapshot = pkg.snapshot;
  return (
    <div className="ratification-current">
      <div className="ratification-current-heading">
        <span
          className="ratification-status-badge"
          data-status={pkg.status}
        >
          {packageStatusLabel(pkg.status)}
        </span>
        <span className="muted-copy">
          Sürüm {pkg.version} · Oluşturuldu: {formatDate(pkg.createdAt)}
        </span>
      </div>

      <dl className="ratification-summary-list">
        <div>
          <dt>Deal</dt>
          <dd>{snapshot.dealReference} · {snapshot.dealTitle}</dd>
        </div>
        <div>
          <dt>Alıcı</dt>
          <dd>{snapshot.buyer.legalName}</dd>
        </div>
        <div>
          <dt>Satıcı</dt>
          <dd>{snapshot.seller.legalName}</dd>
        </div>
        <div>
          <dt>Sözleşme bedeli</dt>
          <dd>
            <strong>
              {decimalFromMinor(snapshot.commercialTerms.amountMinor)}{" "}
              {snapshot.commercialTerms.currency}
            </strong>
          </dd>
        </div>
        <div>
          <dt>Rule-set</dt>
          <dd>
            v{snapshot.ruleSet.version} · {snapshot.ruleSet.rules.length} kural
          </dd>
        </div>
        <div>
          <dt>Belge</dt>
          <dd>
            objectVersion {snapshot.document.objectVersion} · sha256{" "}
            <HexValue value={snapshot.document.sha256} label="sha256" />
          </dd>
        </div>
        <div>
          <dt>Content hash</dt>
          <dd>
            <HexValue value={pkg.contentHash} label="hash" />
          </dd>
        </div>
      </dl>

      <details className="ratification-rules-detail">
        <summary>Package içindeki kurallar ({snapshot.ruleSet.rules.length})</summary>
        <ul>
          {snapshot.ruleSet.rules.map((rule) => (
            <li key={rule.ruleReference}>
              <strong>{rule.title}</strong>
              <span>{formatStructuredValue(rule.structuredValue)}</span>
            </li>
          ))}
        </ul>
      </details>

      <h3>Taraf onayları</h3>
      <ul className="ratification-approval-list">
        {pkg.approvals.map((approval) => (
          <li key={approval.legalEntityId}>
            <div>
              <strong>{approval.legalName}</strong>
              <span>
                {approval.status === "APPROVED" && approval.approvedAt
                  ? `Onaylandı: ${formatDate(approval.approvedAt)}`
                  : "Onay bekliyor"}
              </span>
              {approval.approverUserId ? (
                <span className="muted-copy">
                  Onaylayan kullanıcı: {approval.approverUserId.slice(0, 8)}…
                </span>
              ) : null}
            </div>
            <span
              className="ratification-approval-badge"
              data-status={approval.status}
            >
              {approval.status === "APPROVED" ? "Onaylandı" : "Bekliyor"}
            </span>
          </li>
        ))}
      </ul>

      {mayApprove || mayReject ? (
        <div className="ratification-current-actions">
          {mayApprove ? (
            <button className="primary-button" type="button" onClick={onApprove}>
              Package'ı onayla
            </button>
          ) : null}
          {mayReject ? (
            <button className="danger-button" type="button" onClick={onReject}>
              Package'ı reddet
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function ApproveConfirmation({
  pkg,
  pending,
  onCancel,
  onConfirm,
}: {
  pkg: RatificationPackageDetail;
  pending: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div
      className="confirmation-dialog ratification-confirmation"
      role="dialog"
      aria-modal="true"
      aria-labelledby="ratification-approve-title"
    >
      <h3 id="ratification-approve-title">Package'ı onaylıyor musunuz?</h3>
      <p>
        Şirketiniz adına bu package içeriğini bağlayıcı olarak
        onaylıyorsunuz. Bu, ticari bir taahhüttür ve geri alınamaz.
      </p>
      <p>
        Sözleşme bedeli:{" "}
        <strong>
          {decimalFromMinor(pkg.snapshot.commercialTerms.amountMinor)}{" "}
          {pkg.snapshot.commercialTerms.currency}
        </strong>
      </p>
      <p>
        Content hash: <HexValue value={pkg.contentHash} label="hash" />
      </p>
      <div>
        <button className="text-button" type="button" onClick={onCancel} disabled={pending}>
          Vazgeç
        </button>
        <button className="primary-button" type="button" onClick={onConfirm} disabled={pending}>
          {pending ? "Onaylanıyor…" : "Bağlayıcı olarak onayla"}
        </button>
      </div>
    </div>
  );
}

function RejectConfirmation({
  pkg,
  pending,
  onCancel,
  onConfirm,
}: {
  pkg: RatificationPackageDetail;
  pending: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div
      className="confirmation-dialog ratification-confirmation"
      role="dialog"
      aria-modal="true"
      aria-labelledby="ratification-reject-title"
    >
      <h3 id="ratification-reject-title">Package'ı reddediyor musunuz?</h3>
      <p>
        Şirketiniz adına bu package'ı reddediyorsunuz. Devam etmek için
        başlatıcı tarafın farklı veya aynı şartlarla yeni bir package
        oluşturması gerekir; mevcut onaylar yeni package'a taşınmaz.
      </p>
      <p>
        Content hash: <HexValue value={pkg.contentHash} label="hash" />
      </p>
      <div>
        <button className="text-button" type="button" onClick={onCancel} disabled={pending}>
          Vazgeç
        </button>
        <button className="danger-button" type="button" onClick={onConfirm} disabled={pending}>
          {pending ? "Reddediliyor…" : "Package'ı reddet"}
        </button>
      </div>
    </div>
  );
}

function CreatePackageForm({
  hasCurrentPackage,
  ready,
  suggestions,
  suggestionsLoading,
  pending,
  error,
  onSubmit,
}: {
  hasCurrentPackage: boolean;
  ready: boolean;
  suggestions: MoneySuggestion[];
  suggestionsLoading: boolean;
  pending: boolean;
  error: unknown;
  onSubmit: (terms: RatificationCommercialTerms) => void;
}) {
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("");
  const [clientError, setClientError] = useState<string>();
  const fieldErrors: RatificationFieldError = getRatificationFieldErrors(error);
  const amountError = clientError ?? fieldErrors["commercialTerms.amountMinor"];
  const currencyError = fieldErrors["commercialTerms.currency"];

  function applySuggestion(suggestion: MoneySuggestion) {
    setAmount(decimalFromMinor(suggestion.amountMinor));
    setCurrency(suggestion.currency);
    setClientError(undefined);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const amountMinor = decimalToMinor(amount);
    const normalizedCurrency = currency.trim().toUpperCase();
    if (amountMinor === undefined) {
      setClientError("Geçerli bir pozitif tutar girin.");
      return;
    }
    if (!/^[A-Z]{3}$/.test(normalizedCurrency)) {
      setClientError("Para birimi 3 harfli ISO 4217 kodu olmalıdır (ör. TRY, USD).");
      return;
    }
    setClientError(undefined);
    onSubmit({ amountMinor, currency: normalizedCurrency });
  }

  return (
    <div className="ratification-create">
      <h3>{hasCurrentPackage ? "Şartları değiştirip yeni package oluştur" : "Ratification package oluştur"}</h3>
      {hasCurrentPackage ? (
        <p className="field-hint">
          Farklı bir exact tutar ile yeni package oluşturmak, mevcut PENDING
          package'ı SUPERSEDED yapar; eski onaylar yeni package'a taşınmaz.
        </p>
      ) : null}
      {!ready ? (
        <p className="field-hint">
          Package şu anda oluşturulamaz: taraflar, kabul edilmiş rule-set ve
          güncel bir belge gereklidir.
        </p>
      ) : null}

      {suggestionsLoading ? (
        <p className="inline-state" role="status">
          <span className="loading-line" aria-hidden="true" />
          MONEY kural önerileri yükleniyor…
        </p>
      ) : null}
      {!suggestionsLoading && suggestions.length ? (
        <div className="ratification-suggestions">
          <span className="field-hint">
            Kabul edilmiş rule-set'teki MONEY kuralları (yalnızca öneri; hiçbiri
            otomatik seçilmez):
          </span>
          <ul>
            {suggestions.map((suggestion) => (
              <li key={suggestion.ruleReference}>
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => applySuggestion(suggestion)}
                >
                  {suggestion.title}: {decimalFromMinor(suggestion.amountMinor)}{" "}
                  {suggestion.currency}
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {error ? (
        <p className="form-alert" role="alert">
          {getRatificationErrorMessage(error)}
        </p>
      ) : null}

      <form className="auth-form ratification-create-form" onSubmit={handleSubmit}>
        <div className="field-group">
          <label htmlFor="ratification-amount">Sözleşme bedeli (ondalık)</label>
          <input
            id="ratification-amount"
            value={amount}
            onChange={(event) => {
              setAmount(event.target.value);
              setClientError(undefined);
            }}
            placeholder="ör. 125000.00"
            aria-invalid={Boolean(amountError)}
          />
          {amountError ? <span className="field-error">{amountError}</span> : null}
        </div>
        <div className="field-group">
          <label htmlFor="ratification-currency">Para birimi (ISO 4217)</label>
          <input
            id="ratification-currency"
            value={currency}
            maxLength={3}
            onChange={(event) => {
              setCurrency(event.target.value.toUpperCase());
              setClientError(undefined);
            }}
            placeholder="TRY"
            aria-invalid={Boolean(currencyError)}
          />
          {currencyError ? <span className="field-error">{currencyError}</span> : null}
        </div>
        <button className="primary-button" type="submit" disabled={pending || !ready}>
          {pending ? "Oluşturuluyor…" : "Exact tutarı teyit et ve package oluştur"}
        </button>
      </form>
    </div>
  );
}

function PackageHistory({
  items,
  loading,
  error,
  currentPackageId,
  onRetry,
}: {
  items: RatificationPackageDetail[];
  loading: boolean;
  error: unknown;
  currentPackageId: string | undefined;
  onRetry: () => void;
}) {
  const historical = items.filter((item) => item.id !== currentPackageId);
  return (
    <section className="ratification-history">
      <h3>Package geçmişi</h3>
      {loading ? (
        <p className="inline-state" role="status">
          <span className="loading-line" aria-hidden="true" />
          Geçmiş yükleniyor…
        </p>
      ) : null}
      {error ? (
        <div className="form-alert panel-alert" role="alert">
          <p>{getRatificationErrorMessage(error)}</p>
          <button className="secondary-button" type="button" onClick={onRetry}>
            Yeniden dene
          </button>
        </div>
      ) : null}
      {!loading && !error && historical.length === 0 ? (
        <p className="muted-copy">Geçersizleşmiş veya reddedilmiş bir package yok.</p>
      ) : null}
      {historical.length ? (
        <ul>
          {historical.map((item) => (
            <li key={item.id}>
              <div>
                <strong>
                  Sürüm {item.version} ·{" "}
                  {decimalFromMinor(item.snapshot.commercialTerms.amountMinor)}{" "}
                  {item.snapshot.commercialTerms.currency}
                </strong>
                <span>
                  {formatDate(item.createdAt)} · <HexValue value={item.contentHash} label="hash" />
                </span>
              </div>
              <span className="ratification-status-badge" data-status={item.status}>
                {packageStatusLabel(item.status)}
              </span>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

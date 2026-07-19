import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useRef, useState } from "react";
import type { components } from "../../generated/core-api";
import type { DealDetail } from "../deals/dealApi";
import {
  acceptExtractionReview,
  getExtractionReview,
  getRuleSetVersion,
  listRuleSets,
} from "./reviewApi";
import {
  getReviewErrorMessage,
  getReviewFieldErrors,
  shouldRefetchReview,
  shouldResetReviewIdempotencyKey,
  type ReviewFieldError,
} from "./reviewErrors";

type ExtractedRule = components["schemas"]["ExtractedRule"];
type RuleValue = components["schemas"]["RuleSetStructuredValue"];
type Category = components["schemas"]["RuleCategory"];
type Draft = {
  category: Category;
  title: string;
  description: string;
  value: ValueDraft;
  excluded: boolean;
};
type ValueDraft = {
  type: RuleValue["type"];
  first: string;
  second: string;
  flag: boolean;
};

const CATEGORIES: Category[] = [
  "PAYMENT",
  "DELIVERY",
  "QUALITY",
  "PENALTY",
  "TERMINATION",
  "DISPUTE",
  "OTHER",
  "UNKNOWN",
];
const VALUE_TYPES: RuleValue["type"][] = [
  "TEXT",
  "MONEY",
  "PERCENTAGE",
  "DURATION",
  "DATE",
  "BOOLEAN",
  "QUANTITY",
];
const CATEGORY_LABELS: Record<Category, string> = {
  PAYMENT: "Ödeme",
  DELIVERY: "Teslimat",
  QUALITY: "Kalite",
  PENALTY: "Ceza",
  TERMINATION: "Fesih",
  DISPUTE: "Uyuşmazlık",
  OTHER: "Diğer",
  UNKNOWN: "Belirsiz",
};
const dateFormat = new Intl.DateTimeFormat("tr-TR", {
  dateStyle: "medium",
  timeStyle: "short",
});

function decimalFromMinor(value: number): string {
  const sign = value < 0 ? "-" : "";
  const digits = String(Math.abs(value)).padStart(3, "0");
  return `${sign}${digits.slice(0, -2)}.${digits.slice(-2)}`;
}

/** Converts only decimal text using BigInt: no binary floating point enters the request. */
function decimalToInteger(
  text: string,
  scale: number,
  minimum = 0,
): number | undefined {
  const normalized = text.trim().replace(",", ".");
  if (!/^-?\d+(?:\.\d+)?$/.test(normalized)) return undefined;
  const negative = normalized.startsWith("-");
  const [wholeRaw, fractionRaw = ""] = (
    negative ? normalized.slice(1) : normalized
  ).split(".");
  if (fractionRaw.length > scale) return undefined;
  const integer = BigInt(
    `${negative ? "-" : ""}${wholeRaw}${fractionRaw.padEnd(scale, "0")}`,
  );
  if (integer < BigInt(minimum) || integer > BigInt(Number.MAX_SAFE_INTEGER))
    return undefined;
  return Number(integer);
}

function decimalToFiniteNumber(text: string): number | undefined {
  const normalized = text.trim().replace(",", ".");
  if (!/^-?\d+(?:\.\d+)?$/.test(normalized)) return undefined;
  const value = Number(normalized);
  return Number.isFinite(value) ? value : undefined;
}

function draftValue(value: ExtractedRule["structuredValue"]): ValueDraft {
  switch (value.type) {
    case "TEXT":
      return { type: "TEXT", first: value.value, second: "", flag: false };
    case "MONEY":
      return {
        type: "MONEY",
        first: decimalFromMinor(value.amountMinor),
        second: value.currency,
        flag: false,
      };
    case "PERCENTAGE":
      return {
        type: "PERCENTAGE",
        first: decimalFromMinor(value.basisPoints),
        second: "",
        flag: false,
      };
    case "DURATION":
      return {
        type: "DURATION",
        first: String(value.valueSeconds),
        second: "",
        flag: false,
      };
    case "DATE":
      return { type: "DATE", first: value.value, second: "", flag: false };
    case "BOOLEAN":
      return { type: "BOOLEAN", first: "", second: "", flag: value.value };
    case "QUANTITY":
      return {
        type: "QUANTITY",
        first: String(value.value),
        second: value.unit,
        flag: false,
      };
  }
}

function toRuleValue(value: ValueDraft): RuleValue | undefined {
  switch (value.type) {
    case "TEXT":
      return value.first.trim()
        ? { type: "TEXT", value: value.first.trim() }
        : undefined;
    case "MONEY": {
      const amountMinor = decimalToInteger(value.first, 2);
      return amountMinor === undefined ||
        !/^[A-Z]{3}$/.test(value.second.trim())
        ? undefined
        : { type: "MONEY", amountMinor, currency: value.second.trim() };
    }
    case "PERCENTAGE": {
      const basisPoints = decimalToInteger(value.first, 2);
      return basisPoints === undefined || basisPoints > 10_000
        ? undefined
        : { type: "PERCENTAGE", basisPoints };
    }
    case "DURATION": {
      const valueSeconds = decimalToInteger(value.first, 0);
      return valueSeconds === undefined
        ? undefined
        : { type: "DURATION", valueSeconds };
    }
    case "DATE":
      return /^\d{4}-\d{2}-\d{2}$/.test(value.first)
        ? { type: "DATE", value: value.first }
        : undefined;
    case "BOOLEAN":
      return { type: "BOOLEAN", value: value.flag };
    case "QUANTITY": {
      const amount = decimalToFiniteNumber(value.first);
      return amount === undefined || !value.second.trim()
        ? undefined
        : { type: "QUANTITY", value: amount, unit: value.second.trim() };
    }
  }
}

function initialDraft(rule: ExtractedRule): Draft {
  return {
    category: rule.category,
    title: rule.title,
    description: rule.description,
    value: draftValue(rule.structuredValue),
    excluded: false,
  };
}
function changed(rule: ExtractedRule, draft: Draft): boolean {
  return (
    draft.category !== rule.category ||
    draft.title !== rule.title ||
    draft.description !== rule.description ||
    JSON.stringify(toRuleValue(draft.value)) !==
      JSON.stringify(rule.structuredValue)
  );
}
function formatValue(value: RuleValue): string {
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
  }
}
function legalBasis(rule: {
  legalBasis?: components["schemas"]["AdvisoryLegalBasis"] | null;
  legalBasisProvenance?: string;
}) {
  return rule.legalBasis
    ? `${rule.legalBasis.source} · Md. ${rule.legalBasis.articleNo}${rule.legalBasisProvenance ? ` (${rule.legalBasisProvenance})` : ""}`
    : "Hukuki dayanak belirtilmedi";
}

function getDecisionErrors(
  errors: ReviewFieldError,
  decisionIndex: number,
): ReviewFieldError {
  const prefix = `decisions[${decisionIndex}].`;
  return Object.entries(errors).reduce<ReviewFieldError>(
    (result, [field, message]) => {
      if (field.startsWith(prefix))
        result[field.slice(prefix.length)] = message;
      return result;
    },
    {},
  );
}

interface Props {
  deal: DealDetail;
  legalEntityId: string;
}

export function DealReviewWorkspace({ deal, legalEntityId }: Props) {
  const queryClient = useQueryClient();
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [added, setAdded] = useState<Draft[]>([]);
  const [confirmationOpen, setConfirmationOpen] = useState(false);
  const requestKey = useRef<string | undefined>(undefined);
  const reviewEnabled = deal.analysis.status === "REVIEW_REQUIRED";
  const mayReview = deal.availableActions.canReviewExtraction === true;
  const review = useQuery({
    queryKey: ["review", legalEntityId, deal.id],
    queryFn: ({ signal }) =>
      getExtractionReview(legalEntityId, deal.id, signal),
    enabled: reviewEnabled,
  });
  const history = useQuery({
    queryKey: ["rule-sets", legalEntityId, deal.id],
    queryFn: ({ signal }) => listRuleSets(legalEntityId, deal.id, signal),
  });
  const [selectedVersion, setSelectedVersion] = useState<string>();
  const version = useQuery({
    queryKey: ["rule-set", legalEntityId, deal.id, selectedVersion],
    queryFn: ({ signal }) =>
      getRuleSetVersion(legalEntityId, deal.id, selectedVersion!, signal),
    enabled: !!selectedVersion,
  });

  const allDrafts = useMemo(
    () =>
      review.data?.rules.map((rule) => ({
        rule,
        draft: drafts[rule.ruleReference] ?? initialDraft(rule),
      })) ?? [],
    [drafts, review.data],
  );
  const invalid =
    allDrafts.some(
      ({ rule, draft }) =>
        !draft.excluded && changed(rule, draft) && !toRuleValue(draft.value),
    ) ||
    added.some(
      (draft) =>
        !draft.title.trim() ||
        !draft.description.trim() ||
        !toRuleValue(draft.value),
    );
  const accept = useMutation({
    mutationFn: () => {
      if (!review.data) throw new Error("Review is unavailable");
      requestKey.current ??= crypto.randomUUID();
      const decisions: components["schemas"]["ReviewRuleDecision"][] =
        review.data.rules.map((rule) => {
          const draft = drafts[rule.ruleReference] ?? initialDraft(rule);
          if (draft.excluded)
            return {
              decision: "EXCLUDED" as const,
              ruleReference: rule.ruleReference,
            };
          const structuredValue = toRuleValue(draft.value);
          return changed(rule, draft) && structuredValue
            ? {
                decision: "MODIFIED" as const,
                ruleReference: rule.ruleReference,
                category: draft.category,
                title: draft.title.trim(),
                description: draft.description.trim(),
                structuredValue,
              }
            : { decision: "KEPT" as const, ruleReference: rule.ruleReference };
        });
      for (const draft of added) {
        const structuredValue = toRuleValue(draft.value);
        if (!structuredValue) throw new Error("Invalid added rule");
        decisions.push({
          decision: "ADDED",
          category: draft.category,
          title: draft.title.trim(),
          description: draft.description.trim(),
          structuredValue,
        });
      }
      return acceptExtractionReview(
        legalEntityId,
        deal.id,
        {
          analysisId: review.data.analysisId,
          expectedVersion: deal.version,
          decisions,
        },
        requestKey.current,
      );
    },
    onSuccess: async () => {
      requestKey.current = undefined;
      setConfirmationOpen(false);
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ["review", legalEntityId, deal.id],
        }),
        queryClient.invalidateQueries({
          queryKey: ["rule-sets", legalEntityId, deal.id],
        }),
        queryClient.invalidateQueries({
          queryKey: ["deals", legalEntityId, "detail", deal.id],
        }),
      ]);
    },
    onError: async (error) => {
      if (shouldResetReviewIdempotencyKey(error))
        requestKey.current = undefined;
      if (shouldRefetchReview(error))
        await Promise.all([
          queryClient.invalidateQueries({
            queryKey: ["review", legalEntityId, deal.id],
          }),
          queryClient.invalidateQueries({
            queryKey: ["rule-sets", legalEntityId, deal.id],
          }),
          queryClient.invalidateQueries({
            queryKey: ["deals", legalEntityId, "detail", deal.id],
          }),
        ]);
    },
  });
  const fieldErrors = getReviewFieldErrors(accept.error);
  const update = (reference: string, next: Partial<Draft>) => {
    requestKey.current = undefined;
    setDrafts((current) => ({
      ...current,
      [reference]: {
        ...(current[reference] ??
          initialDraft(
            review.data!.rules.find(
              (rule) => rule.ruleReference === reference,
            )!,
          )),
        ...next,
      },
    }));
  };
  const updateAdded = (index: number, next: Partial<Draft>) => {
    requestKey.current = undefined;
    setAdded((items) =>
      items.map((item, itemIndex) =>
        itemIndex === index ? { ...item, ...next } : item,
      ),
    );
  };
  const restore = (reference: string) => {
    requestKey.current = undefined;
    setDrafts((current) => {
      const { [reference]: _discarded, ...remaining } = current;
      return remaining;
    });
  };

  return (
    <section
      className="workspace-panel review-panel"
      aria-labelledby="review-title"
    >
      <div className="panel-heading">
        <span className="section-kicker">Kural incelemesi</span>
        <h2 id="review-title">Rule-set çalışma alanı</h2>
        <p>
          Hukuki dayanak ve güven bilgisi danışma amaçlıdır. Bu işlem, sonraki
          ratification için kural-set temelini oluşturur; sözleşme veya
          ratification onayı değildir.
        </p>
      </div>
      {deal.currentRuleSet ? (
        <CurrentRuleSet
          summary={deal.currentRuleSet}
          onOpen={() => setSelectedVersion(deal.currentRuleSet!.id)}
        />
      ) : (
        <p className="review-current-empty">
          Güncel kabul edilmiş rule-set yok. Geçmiş sürümler yalnızca
          okunabilir.
        </p>
      )}
      {reviewEnabled && review.isPending ? (
        <p className="inline-state" role="status">
          İnceleme yükleniyor…
        </p>
      ) : null}
      {reviewEnabled && review.isError ? (
        <p className="form-alert" role="alert">
          İnceleme verisi alınamadı.{" "}
          <button
            className="text-button"
            type="button"
            onClick={() => void review.refetch()}
          >
            Yeniden dene
          </button>
        </p>
      ) : null}
      {reviewEnabled && review.data ? (
        <div className="review-workspace">
          <div className="review-notice" role="status">
            <strong>
              {mayReview
                ? "Değişikliklerinizi toplu olarak gözden geçirin."
                : "Bu katılımcı için inceleme salt okunurdur."}
            </strong>
            <span>{review.data.rules.length} çıkarılmış kural</span>
          </div>
          <ul className="review-rule-list">
            {allDrafts.map(({ rule, draft }, index) => (
              <ReviewRule
                key={rule.ruleReference}
                rule={rule}
                draft={draft}
                editable={mayReview}
                errors={getDecisionErrors(fieldErrors, index)}
                onChange={(next) => update(rule.ruleReference, next)}
                onRestore={() => restore(rule.ruleReference)}
              />
            ))}
          </ul>
          {mayReview ? (
            <>
              <div className="review-added">
                <h3>Elle eklenen kurallar</h3>
                {added.map((draft, index) => (
                  <ManualRule
                    key={index}
                    draft={draft}
                    errors={getDecisionErrors(
                      fieldErrors,
                      review.data.rules.length + index,
                    )}
                    idPrefix={`review-manual-${index}`}
                    onChange={(next) => updateAdded(index, next)}
                    onRemove={() => {
                      requestKey.current = undefined;
                      setAdded((items) =>
                        items.filter((_, itemIndex) => itemIndex !== index),
                      );
                    }}
                  />
                ))}
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => {
                    requestKey.current = undefined;
                    setAdded((items) => [
                      ...items,
                      {
                        category: "OTHER",
                        title: "",
                        description: "",
                        value: {
                          type: "TEXT",
                          first: "",
                          second: "",
                          flag: false,
                        },
                        excluded: false,
                      },
                    ]);
                  }}
                >
                  Kural ekle
                </button>
              </div>
              {accept.isError ? (
                <p className="form-alert" role="alert">
                  {getReviewErrorMessage(accept.error)}
                </p>
              ) : null}
              <button
                className="primary-button"
                type="button"
                disabled={accept.isPending || invalid}
                onClick={() => setConfirmationOpen(true)}
              >
                İncelemeyi kabul etmeye devam et
              </button>
            </>
          ) : null}
        </div>
      ) : null}
      <RuleSetHistory
        history={history.data?.items ?? []}
        onOpen={setSelectedVersion}
      />
      {selectedVersion ? (
        <RuleSetDetail
          version={version.data}
          loading={version.isPending}
          onClose={() => setSelectedVersion(undefined)}
        />
      ) : null}
      {confirmationOpen ? (
        <Confirmation
          pending={accept.isPending}
          onCancel={() => setConfirmationOpen(false)}
          onConfirm={() => accept.mutate()}
        />
      ) : null}
    </section>
  );
}

function ReviewRule({
  rule,
  draft,
  editable,
  errors,
  onChange,
  onRestore,
}: {
  rule: ExtractedRule;
  draft: Draft;
  editable: boolean;
  errors: ReviewFieldError;
  onChange: (next: Partial<Draft>) => void;
  onRestore: () => void;
}) {
  return (
    <li className={draft.excluded ? "review-rule excluded" : "review-rule"}>
      <div className="review-rule-meta">
        <span className="analysis-category-badge">
          {CATEGORY_LABELS[rule.category]}
        </span>
        <span>Güven %{Math.round(rule.confidence * 100)}</span>
      </div>
      {editable ? (
        <RuleEditor
          draft={draft}
          errors={errors}
          idPrefix={`review-rule-${rule.ruleReference}`}
          onChange={onChange}
        />
      ) : (
        <>
          <h3>{rule.title}</h3>
          <p>{rule.description}</p>
          <p>
            <strong>Değer:</strong> {formatValue(rule.structuredValue)}
          </p>
        </>
      )}
      <p className="analysis-source-copy">
        Kaynak:{" "}
        {rule.sourceReferences
          .map((source) => `sayfa ${source.page}`)
          .join(", ") || "belirtilmedi"}
      </p>
      <p className="review-legal-basis">{legalBasis(rule)}</p>
      {editable ? (
        <div className="review-rule-actions">
          <label className="review-exclude">
            <input
              type="checkbox"
              checked={draft.excluded}
              onChange={(event) => onChange({ excluded: event.target.checked })}
            />{" "}
            Bu kuralı hariç tut
          </label>
          <button className="text-button" type="button" onClick={onRestore}>
            Orijinale geri yükle
          </button>
        </div>
      ) : null}
    </li>
  );
}
function ManualRule({
  draft,
  errors,
  idPrefix,
  onChange,
  onRemove,
}: {
  draft: Draft;
  errors: ReviewFieldError;
  idPrefix: string;
  onChange: (next: Partial<Draft>) => void;
  onRemove: () => void;
}) {
  return (
    <div className="manual-rule">
      <RuleEditor
        draft={draft}
        errors={errors}
        idPrefix={idPrefix}
        onChange={onChange}
      />
      <p className="review-legal-basis">
        Manuel eklenen kuralların hukuki dayanağı yoktur; kaynak işareti
        MANUALLY_ADDED olur.
      </p>
      <button className="text-button" type="button" onClick={onRemove}>
        Kuralı kaldır
      </button>
    </div>
  );
}
function RuleEditor({
  draft,
  errors,
  idPrefix,
  onChange,
}: {
  draft: Draft;
  errors: ReviewFieldError;
  idPrefix: string;
  onChange: (next: Partial<Draft>) => void;
}) {
  const value = draft.value;
  const titleErrorId = `${idPrefix}-title-error`;
  const descriptionErrorId = `${idPrefix}-description-error`;
  const changeValue = (next: Partial<ValueDraft>) =>
    onChange({ value: { ...value, ...next } });
  return (
    <fieldset className="review-editor" disabled={draft.excluded}>
      <label>
        Kategori
        <select
          value={draft.category}
          onChange={(event) =>
            onChange({ category: event.target.value as Category })
          }
        >
          {CATEGORIES.map((category) => (
            <option key={category} value={category}>
              {CATEGORY_LABELS[category]}
            </option>
          ))}
        </select>
      </label>
      <label>
        Başlık
        <input
          value={draft.title}
          aria-invalid={!!errors.title}
          aria-describedby={errors.title ? titleErrorId : undefined}
          onChange={(event) => onChange({ title: event.target.value })}
        />
        <InputError id={titleErrorId} message={errors.title} />
      </label>
      <label className="review-wide">
        Açıklama
        <textarea
          value={draft.description}
          aria-invalid={!!errors.description}
          aria-describedby={errors.description ? descriptionErrorId : undefined}
          onChange={(event) => onChange({ description: event.target.value })}
        />
        <InputError id={descriptionErrorId} message={errors.description} />
      </label>
      <label>
        Değer tipi
        <select
          value={value.type}
          onChange={(event) =>
            changeValue({
              type: event.target.value as ValueDraft["type"],
              first: "",
              second: "",
              flag: false,
            })
          }
        >
          {VALUE_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </label>
      <ValueFields
        value={value}
        errors={errors}
        idPrefix={idPrefix}
        onChange={changeValue}
      />
    </fieldset>
  );
}
function ValueFields({
  value,
  errors,
  idPrefix,
  onChange,
}: {
  value: ValueDraft;
  errors: ReviewFieldError;
  idPrefix: string;
  onChange: (next: Partial<ValueDraft>) => void;
}) {
  const primaryErrorField =
    value.type === "MONEY"
      ? "structuredValue.amountMinor"
      : value.type === "PERCENTAGE"
        ? "structuredValue.basisPoints"
        : "structuredValue.value";
  const primaryError = errors[primaryErrorField];
  const primaryErrorId = `${idPrefix}-value-error`;
  if (value.type === "BOOLEAN")
    return (
      <label>
        Değer
        <select
          value={String(value.flag)}
          aria-invalid={!!primaryError}
          aria-describedby={primaryError ? primaryErrorId : undefined}
          onChange={(event) =>
            onChange({ flag: event.target.value === "true" })
          }
        >
          <option value="true">Evet</option>
          <option value="false">Hayır</option>
        </select>
        <InputError id={primaryErrorId} message={primaryError} />
      </label>
    );
  const primary =
    value.type === "MONEY"
      ? "Tutar (ondalık)"
      : value.type === "PERCENTAGE"
        ? "Yüzde (ondalık)"
        : value.type === "QUANTITY"
          ? "Miktar"
          : "Değer";
  return (
    <>
      <label>
        {primary}
        <input
          type="text"
          value={value.first}
          aria-invalid={!!primaryError}
          aria-describedby={primaryError ? primaryErrorId : undefined}
          onChange={(event) => onChange({ first: event.target.value })}
        />
        <InputError id={primaryErrorId} message={primaryError} />
      </label>
      {value.type === "MONEY" ? (
        <label>
          Para birimi
          <input
            value={value.second}
            maxLength={3}
            aria-invalid={!!errors["structuredValue.currency"]}
            aria-describedby={
              errors["structuredValue.currency"]
                ? `${idPrefix}-currency-error`
                : undefined
            }
            onChange={(event) =>
              onChange({ second: event.target.value.toUpperCase() })
            }
          />
          <InputError
            id={`${idPrefix}-currency-error`}
            message={errors["structuredValue.currency"]}
          />
        </label>
      ) : null}
      {value.type === "QUANTITY" ? (
        <label>
          Birim
          <input
            value={value.second}
            aria-invalid={!!errors["structuredValue.unit"]}
            aria-describedby={
              errors["structuredValue.unit"]
                ? `${idPrefix}-unit-error`
                : undefined
            }
            onChange={(event) => onChange({ second: event.target.value })}
          />
          <InputError
            id={`${idPrefix}-unit-error`}
            message={errors["structuredValue.unit"]}
          />
        </label>
      ) : null}
    </>
  );
}

function InputError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <small className="field-error" id={id}>
      {message}
    </small>
  ) : null;
}
function CurrentRuleSet({
  summary,
  onOpen,
}: {
  summary: components["schemas"]["RuleSetVersionSummary"];
  onOpen: () => void;
}) {
  return (
    <div className="review-current">
      <div>
        <strong>Güncel rule-set: v{summary.version}</strong>
        <span>
          {summary.ruleCount} kural ·{" "}
          {dateFormat.format(new Date(summary.createdAt))}
        </span>
      </div>
      <button className="secondary-button" type="button" onClick={onOpen}>
        İçeriği aç
      </button>
    </div>
  );
}
function RuleSetHistory({
  history,
  onOpen,
}: {
  history: components["schemas"]["RuleSetVersionSummary"][];
  onOpen: (id: string) => void;
}) {
  return (
    <section className="review-history">
      <h3>Rule-set geçmişi</h3>
      {history.length ? (
        <ul>
          {history.map((item) => (
            <li key={item.id}>
              <div>
                <strong>v{item.version}</strong>
                <span>
                  {item.ruleCount} kural ·{" "}
                  {dateFormat.format(new Date(item.createdAt))}
                </span>
              </div>
              <button
                className="text-button"
                type="button"
                onClick={() => onOpen(item.id)}
              >
                İçeriği aç
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted-copy">Henüz kabul edilmiş sürüm yok.</p>
      )}
    </section>
  );
}
function RuleSetDetail({
  version,
  loading,
  onClose,
}: {
  version?: components["schemas"]["RuleSetVersion"];
  loading: boolean;
  onClose: () => void;
}) {
  return (
    <section className="review-detail" aria-live="polite">
      <div>
        <h3>Immutable sürüm detayı</h3>
        <button className="text-button" type="button" onClick={onClose}>
          Kapat
        </button>
      </div>
      {loading ? (
        <p>Yükleniyor…</p>
      ) : version ? (
        <>
          <p>
            v{version.version} · {version.ruleCount} kural · Kaynak extraction:{" "}
            {version.sourceExtractionResultVersionId}
          </p>
          <ul>
            {version.rules.map((rule) => (
              <li key={rule.ruleReference}>
                <strong>{rule.title}</strong>
                <span>
                  {CATEGORY_LABELS[rule.category]} ·{" "}
                  {formatValue(rule.structuredValue)}
                </span>
                <small>{legalBasis(rule)}</small>
              </li>
            ))}
          </ul>
          {version.excludedRuleReferences.length ? (
            <p>
              Hariç tutulan çıkarılmış kurallar:{" "}
              {version.excludedRuleReferences.join(", ")}
            </p>
          ) : null}
        </>
      ) : (
        <p className="form-alert" role="alert">
          Sürüm detayı alınamadı.
        </p>
      )}
    </section>
  );
}
function Confirmation({
  pending,
  onCancel,
  onConfirm,
}: {
  pending: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div
      className="confirmation-dialog review-confirmation"
      role="dialog"
      aria-modal="true"
      aria-labelledby="review-confirm-title"
    >
      <h3 id="review-confirm-title">İncelemeyi kabul etmek istiyor musunuz?</h3>
      <p>
        Bu işlem, sonraki ratification için rule-set temelini oluşturur.
        Sözleşme onayı veya ratification onayı değildir.
      </p>
      <div>
        <button
          className="text-button"
          type="button"
          onClick={onCancel}
          disabled={pending}
        >
          Vazgeç
        </button>
        <button
          className="primary-button"
          type="button"
          onClick={onConfirm}
          disabled={pending}
        >
          {pending ? "Kaydediliyor…" : "Rule-set’i oluştur"}
        </button>
      </div>
    </div>
  );
}

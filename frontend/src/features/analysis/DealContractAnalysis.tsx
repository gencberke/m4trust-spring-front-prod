import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";

import type { DealDetail } from "../deals/dealApi";
import { dealDetailQueryKey } from "../deals/dealQueries";
import {
  requestDealDocumentAnalysis,
  type DealDocumentAnalysis,
  type ExtractedRuleValue,
  type ExtractionSourceReference,
} from "./analysisApi";
import {
  getAnalysisFailureMessage,
  getAnalysisReadErrorMessage,
  getAnalysisRequestErrorMessage,
  shouldRefetchAfterAnalysisRequestError,
} from "./analysisErrors";
import {
  dealDocumentAnalysisQueryKey,
  dealDocumentAnalysisQueryOptions,
} from "./analysisQueries";

const DATE_FORMATTER = new Intl.DateTimeFormat("tr-TR", {
  dateStyle: "medium",
  timeStyle: "short",
});
const NUMBER_FORMATTER = new Intl.NumberFormat("tr-TR", {
  maximumFractionDigits: 2,
});
const PERCENT_FORMATTER = new Intl.NumberFormat("tr-TR", {
  style: "percent",
  maximumFractionDigits: 0,
});
const MONEY_FORMATTER_CACHE = new Map<string, Intl.NumberFormat>();

const STATUS_LABELS: Readonly<Partial<Record<DealDocumentAnalysis["status"], string>>> = {
  NOT_REQUESTED: "Talep edilmedi",
  QUEUED: "Sırada",
  PROCESSING: "İşleniyor",
  REVIEW_REQUIRED: "İnceleme bekliyor",
  FAILED: "Tamamlanamadı",
};

const LEGAL_BASIS_LABELS: Readonly<Record<string, string>> = {
  "tbk-6098": "TBK 6098",
  "kvkk-6698": "KVKK 6698",
  "odeme-hizmetleri-6493": "Ödeme Hizmetleri Kanunu 6493",
  "aml-5549": "5549 sayılı Kanun",
  "odeme-hizmetleri-yonetmelik": "Ödeme Hizmetleri Yönetmeliği",
  "odeme-hizmetleri-tebligi": "Ödeme Hizmetleri Tebliği",
};

const PARTY_ROLE_LABELS: Readonly<Record<string, string>> = {
  BUYER: "Alıcı",
  SELLER: "Satıcı",
  OTHER: "Diğer",
  UNKNOWN: "Belirsiz",
};

const RULE_CATEGORY_LABELS: Readonly<Record<string, string>> = {
  PAYMENT: "Ödeme",
  DELIVERY: "Teslimat",
  QUALITY: "Kalite",
  PENALTY: "Ceza",
  TERMINATION: "Fesih",
  DISPUTE: "Uyuşmazlık",
  OTHER: "Diğer",
  UNKNOWN: "Belirsiz",
};

const EVIDENCE_TYPE_LABELS: Readonly<Record<string, string>> = {
  DELIVERY_NOTE: "İrsaliye",
  INVOICE: "Fatura",
  VIDEO: "Video",
  PHOTO: "Fotoğraf",
  SIGNED_DOCUMENT: "İmzalı belge",
  OTHER: "Diğer",
  UNKNOWN: "Belirsiz",
};

function formatDate(value: string | null): string | undefined {
  return value ? DATE_FORMATTER.format(new Date(value)) : undefined;
}

function formatMoney(amountMinor: number, currency: string): string {
  let formatter = MONEY_FORMATTER_CACHE.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat("tr-TR", {
      style: "currency",
      currency,
    });
    MONEY_FORMATTER_CACHE.set(currency, formatter);
  }
  return formatter.format(amountMinor / 100);
}

function formatRuleValue(value: ExtractedRuleValue): string {
  switch (value.type) {
    case "TEXT":
      return value.value;
    case "MONEY":
      return formatMoney(value.amountMinor, value.currency);
    case "PERCENTAGE":
      return PERCENT_FORMATTER.format(value.basisPoints / 10_000);
    case "DURATION": {
      const days = value.valueSeconds / 86_400;
      return Number.isInteger(days)
        ? `${days} gün`
        : `${NUMBER_FORMATTER.format(value.valueSeconds)} saniye`;
    }
    case "DATE":
      return new Intl.DateTimeFormat("tr-TR", { dateStyle: "long" })
        .format(new Date(`${value.value}T00:00:00Z`));
    case "BOOLEAN":
      return value.value ? "Evet" : "Hayır";
    case "QUANTITY":
      return `${NUMBER_FORMATTER.format(value.value)} ${value.unit}`;
  }
}

function formatSourcePages(references: ExtractionSourceReference[]): string {
  const pages = [...new Set(references.map((reference) => reference.page))]
    .sort((left, right) => left - right);
  return pages.length ? `Kaynak: sayfa ${pages.join(", ")}` : "Kaynak sayfa belirtilmedi";
}

interface DealContractAnalysisProps {
  deal: DealDetail;
  legalEntityId: string;
}

export function DealContractAnalysis({
  deal,
  legalEntityId,
}: DealContractAnalysisProps) {
  const queryClient = useQueryClient();
  const analysisQuery = useQuery(
    dealDocumentAnalysisQueryOptions(legalEntityId, deal.id),
  );
  const previousStatusRef = useRef(analysisQuery.data?.status);

  useEffect(() => {
    const previousStatus = previousStatusRef.current;
    const nextStatus = analysisQuery.data?.status;
    previousStatusRef.current = nextStatus;
    if (
      previousStatus
      && (previousStatus === "QUEUED" || previousStatus === "PROCESSING")
      && nextStatus
      && nextStatus !== "QUEUED"
      && nextStatus !== "PROCESSING"
    ) {
      void queryClient.invalidateQueries({
        queryKey: dealDetailQueryKey(legalEntityId, deal.id),
      });
    }
  }, [analysisQuery.data?.status, deal.id, legalEntityId, queryClient]);

  const requestMutation = useMutation({
    mutationFn: () => requestDealDocumentAnalysis(
      legalEntityId,
      deal.id,
      crypto.randomUUID(),
    ),
    onSuccess: async (analysis) => {
      queryClient.setQueryData(
        dealDocumentAnalysisQueryKey(legalEntityId, deal.id),
        analysis,
      );
      await queryClient.invalidateQueries({
        queryKey: dealDetailQueryKey(legalEntityId, deal.id),
      });
    },
    onError: async (error) => {
      if (shouldRefetchAfterAnalysisRequestError(error)) {
        await Promise.all([
          queryClient.invalidateQueries({
            queryKey: dealDocumentAnalysisQueryKey(legalEntityId, deal.id),
          }),
          queryClient.invalidateQueries({
            queryKey: dealDetailQueryKey(legalEntityId, deal.id),
          }),
        ]);
      }
    },
  });

  const analysis = analysisQuery.data;
  const requestedAt = formatDate(analysis?.requestedAt ?? null);
  const completedAt = formatDate(analysis?.completedAt ?? analysis?.failedAt ?? null);

  return (
    <section className="workspace-panel analysis-panel" aria-labelledby="contract-analysis-title">
      <div className="analysis-heading">
        <div className="panel-heading">
          <span className="section-kicker">Yapay zekâ destekli çıkarım</span>
          <h2 id="contract-analysis-title">Sözleşme analizi</h2>
          <p>
            Çıkarılan bilgiler karar vermez; sonuçlar kabul edilmeden önce insan
            incelemesi gerektirir.
          </p>
        </div>
        {analysis ? (
          <span className="analysis-status-badge" data-status={analysis.status}>
            {STATUS_LABELS[analysis.status]}
          </span>
        ) : null}
      </div>

      {analysisQuery.isPending ? (
        <div className="inline-state" role="status">
          <span className="loading-line" aria-hidden="true" />
          Analiz durumu yükleniyor…
        </div>
      ) : null}

      {analysisQuery.isError ? (
        <div className="form-alert panel-alert" role="alert">
          <p>{getAnalysisReadErrorMessage(analysisQuery.error)}</p>
          <button
            className="secondary-button"
            type="button"
            onClick={() => void analysisQuery.refetch()}
            disabled={analysisQuery.isFetching}
          >
            {analysisQuery.isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
          </button>
        </div>
      ) : null}

      {analysis ? (
        <>
          {(requestedAt || completedAt) ? (
            <dl className="analysis-timeline">
              {requestedAt ? (
                <div>
                  <dt>Talep</dt>
                  <dd>{requestedAt}</dd>
                </div>
              ) : null}
              {completedAt ? (
                <div>
                  <dt>{analysis.status === "FAILED" ? "Son deneme" : "Sonuç"}</dt>
                  <dd>{completedAt}</dd>
                </div>
              ) : null}
            </dl>
          ) : null}

          {analysis.status === "NOT_REQUESTED" ? (
            <p className="analysis-empty-copy">
              Güncel belge için henüz analiz talep edilmedi.
            </p>
          ) : null}

          {analysis.status === "QUEUED" || analysis.status === "PROCESSING" ? (
            <div className="analysis-progress" role="status" aria-live="polite">
              <span className="analysis-progress-mark" aria-hidden="true" />
              <div>
                <strong>
                  {analysis.status === "QUEUED"
                    ? "Analiz sırada bekliyor"
                    : "Belge işleniyor"}
                </strong>
                <p>
                  Bu ekran otomatik yenilenir. Analiz tamamlanana kadar Deal’i
                  kullanmaya devam edebilirsiniz.
                </p>
              </div>
            </div>
          ) : null}

          {analysis.status === "FAILED" && analysis.failure ? (
            <div className="analysis-failure" role="alert">
              <span className="analysis-failure-code">{analysis.failure.code}</span>
              <h3>Analiz tamamlanamadı</h3>
              <p>{getAnalysisFailureMessage(analysis.failure.code)}</p>
              {analysis.failure.retryRecommended ? (
                <p className="analysis-advisory">Yeni talep ayrı bir analiz işi oluşturur.</p>
              ) : null}
            </div>
          ) : null}

          {analysis.status === "REVIEW_REQUIRED" && analysis.result ? (
            <AnalysisResultView analysis={analysis} />
          ) : null}

          {requestMutation.isError ? (
            <p className="form-alert panel-alert analysis-request-alert" role="alert">
              {getAnalysisRequestErrorMessage(requestMutation.error)}
            </p>
          ) : null}

          {deal.availableActions.canRequestAnalysis ? (
            <div className="analysis-action-row">
              <button
                className="primary-button"
                type="button"
                onClick={() => {
                  requestMutation.reset();
                  requestMutation.mutate();
                }}
                disabled={requestMutation.isPending}
              >
                {requestMutation.isPending
                  ? "Talep gönderiliyor…"
                  : analysis.status === "FAILED"
                    ? "Yeni analiz talep et"
                    : "Analiz talep et"}
              </button>
              <p>Her tıklama yeni ve benzersiz bir analiz talebidir.</p>
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

function AnalysisResultView({ analysis }: { analysis: DealDocumentAnalysis }) {
  const result = analysis.result;
  if (!result) return null;

  return (
    <div className="analysis-result">
      <div className="analysis-review-notice" role="status">
        <span>İnceleme bekliyor</span>
        <div>
          <strong>Teknik çıkarım tamamlandı; sonuç henüz kabul edilmedi.</strong>
          <p>Taraflar, kurallar ve gereksinimler insan incelemesinden geçmelidir.</p>
        </div>
      </div>

      <section className="analysis-result-section" aria-labelledby="analysis-parties-title">
        <div className="analysis-section-heading">
          <h3 id="analysis-parties-title">Çıkarılan taraflar</h3>
          <span>{result.parties.length} kayıt</span>
        </div>
        {result.parties.length ? (
          <ul className="analysis-card-list">
            {result.parties.map((party) => (
              <li key={party.partyReference}>
                <div className="analysis-card-heading">
                  <strong>{party.legalName.value}</strong>
                  <span>{PARTY_ROLE_LABELS[party.role] ?? party.role}</span>
                </div>
                <p>
                  Güven {PERCENT_FORMATTER.format(party.legalName.confidence)} · {formatSourcePages(party.sourceReferences)}
                </p>
                <p>
                  Vergi kimliği: {party.taxIdentifier.masked
                    ? "Maskelenmiş"
                    : party.taxIdentifier.value ?? "Belirtilmedi"}
                </p>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted-copy">Belgede taraf bilgisi çıkarılamadı.</p>
        )}
      </section>

      <section className="analysis-result-section" aria-labelledby="analysis-rules-title">
        <div className="analysis-section-heading">
          <h3 id="analysis-rules-title">Çıkarılan kurallar</h3>
          <span>{result.rules.length} kayıt</span>
        </div>
        {result.rules.length ? (
          <ul className="analysis-rule-list">
            {result.rules.map((rule) => (
              <li key={rule.ruleReference}>
                <div className="analysis-rule-topline">
                  <span className="analysis-category-badge">
                    {RULE_CATEGORY_LABELS[rule.category] ?? rule.category}
                  </span>
                  <span>Güven {PERCENT_FORMATTER.format(rule.confidence)}</span>
                </div>
                <h4>{rule.title}</h4>
                <p>{rule.description}</p>
                <dl className="analysis-rule-value">
                  <dt>Yapılandırılmış değer</dt>
                  <dd>{formatRuleValue(rule.structuredValue)}</dd>
                </dl>
                <p className="analysis-source-copy">{formatSourcePages(rule.sourceReferences)}</p>
                {rule.legalBasis ? (
                  <div className="analysis-legal-basis">
                    <span>
                      {LEGAL_BASIS_LABELS[rule.legalBasis.source] ?? rule.legalBasis.source}
                      {" · Md. "}{rule.legalBasis.articleNo}
                    </span>
                    <p>Bilgilendirme amaçlı mevzuat referansıdır; hukuki onay değildir.</p>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted-copy">Belgede kural çıkarılamadı.</p>
        )}
      </section>

      <section className="analysis-result-section" aria-labelledby="analysis-delivery-title">
        <div className="analysis-section-heading">
          <h3 id="analysis-delivery-title">Teslimat gereksinimleri</h3>
          <span>{result.deliveryRequirements.length} kayıt</span>
        </div>
        {result.deliveryRequirements.length ? (
          <ul className="analysis-delivery-list">
            {result.deliveryRequirements.map((requirement) => (
              <li key={requirement.requirementReference}>
                <div>
                  <strong>{EVIDENCE_TYPE_LABELS[requirement.evidenceType] ?? requirement.evidenceType}</strong>
                  <span>{requirement.required ? "Zorunlu" : "İsteğe bağlı"}</span>
                </div>
                <p>
                  Güven {PERCENT_FORMATTER.format(requirement.confidence)} · {formatSourcePages(requirement.sourceReferences)}
                </p>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted-copy">Teslimat gereksinimi çıkarılmadı.</p>
        )}
      </section>

      <section className="analysis-result-section analysis-summary" aria-labelledby="analysis-summary-title">
        <div className="analysis-section-heading">
          <h3 id="analysis-summary-title">İnceleme özeti</h3>
        </div>
        <p>
          {result.summary.requiresManualReview
            ? "Çıkarım sonucu ayrıca manuel inceleme öneriyor."
            : "Çıkarım ek bir teknik uyarı üretmedi; business kabul için insan incelemesi yine zorunludur."}
        </p>
        {result.summary.reviewReasons.length ? (
          <ul>
            {result.summary.reviewReasons.map((reason) => (
              <li key={reason}>{reason}</li>
            ))}
          </ul>
        ) : (
          <p className="muted-copy">Ek inceleme nedeni bildirilmedi.</p>
        )}
      </section>
    </div>
  );
}

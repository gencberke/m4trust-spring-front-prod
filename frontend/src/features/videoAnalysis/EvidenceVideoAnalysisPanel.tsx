import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef } from "react";

import {
  requestVideoAnalysis,
  type VideoAnalysisDetail,
  type VideoAnalysisFailureSummary,
  type VideoAnalysisResult,
} from "./videoAnalysisApi";
import {
  getVideoAnalysisReadErrorMessage,
  getVideoAnalysisRequestErrorMessage,
  shouldRefetchAfterVideoAnalysisRequestError,
} from "./videoAnalysisErrors";
import {
  formatDurationMs,
  formatTimeRange,
  labelAdvisoryOutcome,
  labelAnomalySeverity,
  labelFailureCode,
  labelObservationType,
  labelReviewReason,
  labelWarningCode,
  PERCENT_FORMATTER,
  presentWarningMessage,
  shouldShowWarningCode,
  WARNING_SEVERITY_LABELS,
} from "./videoAnalysisLabels";
import {
  videoAnalysisQueryKey,
  videoAnalysisQueryOptions,
} from "./videoAnalysisQueries";

interface Props {
  legalEntityId: string;
  dealId: string;
  evidenceSubmissionId: string;
  expectedEvidenceVersion: number;
  readOnly?: boolean;
}

export function EvidenceVideoAnalysisPanel({
  legalEntityId,
  dealId,
  evidenceSubmissionId,
  expectedEvidenceVersion,
  readOnly = false,
}: Props) {
  const queryClient = useQueryClient();
  const requestKeyRef = useRef<string | undefined>(undefined);

  const analysisQuery = useQuery(
    videoAnalysisQueryOptions(legalEntityId, dealId, evidenceSubmissionId),
  );
  const analysis = analysisQuery.data;

  const requestMutation = useMutation({
    mutationFn: () =>
      requestVideoAnalysis(
        legalEntityId,
        dealId,
        evidenceSubmissionId,
        { expectedEvidenceVersion },
        requestKeyRef.current!,
      ),
    onSuccess: (result) => {
      queryClient.setQueryData(
        videoAnalysisQueryKey(legalEntityId, dealId, evidenceSubmissionId),
        result,
      );
    },
    onError: (error) => {
      if (shouldRefetchAfterVideoAnalysisRequestError(error)) {
        void queryClient.invalidateQueries({
          queryKey: videoAnalysisQueryKey(legalEntityId, dealId, evidenceSubmissionId),
        });
      }
    },
  });

  function freshIdempotencyKey() {
    return crypto.randomUUID();
  }

  function handleRequest() {
    requestKeyRef.current = freshIdempotencyKey();
    requestMutation.mutate();
  }

  if (analysisQuery.isLoading) {
    return (
      <div className="video-analysis-panel" aria-live="polite">
        <h4>Video analizi</h4>
        <p>Yükleniyor…</p>
      </div>
    );
  }

  if (analysisQuery.isError) {
    return (
      <div className="video-analysis-panel" aria-live="polite">
        <h4>Video analizi</h4>
        <p className="error-text" role="alert">
          {getVideoAnalysisReadErrorMessage(analysisQuery.error)}
        </p>
        <button
          type="button"
          className="secondary-button"
          onClick={() =>
            void queryClient.invalidateQueries({
              queryKey: videoAnalysisQueryKey(legalEntityId, dealId, evidenceSubmissionId),
            })
          }
        >
          Yeniden dene
        </button>
      </div>
    );
  }

  if (!analysis) {
    return null;
  }

  return (
    <div className="video-analysis-panel" aria-live="polite">
      <h4>Video analizi</h4>
      <VideoAnalysisBody analysis={analysis} />
      {!readOnly && analysis.availableActions?.canRequest === true ? (
        <button
          type="button"
          className="secondary-button"
          onClick={handleRequest}
          disabled={requestMutation.isPending}
        >
          {requestMutation.isPending
            ? "Talep gönderiliyor…"
            : analysis.status === "FAILED"
              ? "Analizi yeniden talep et"
              : "Video analizi talep et"}
        </button>
      ) : null}
      {requestMutation.isError ? (
        <p className="form-alert panel-alert" role="alert">
          {getVideoAnalysisRequestErrorMessage(requestMutation.error)}
        </p>
      ) : null}
    </div>
  );
}

function VideoAnalysisBody({ analysis }: { analysis: VideoAnalysisDetail }) {
  if (analysis.status === "NOT_REQUESTED") {
    return (
      <p className="video-analysis-empty-copy">
        Bu teslimat kanıtı için henüz video analizi talep edilmedi.
      </p>
    );
  }

  if (analysis.status === "QUEUED") {
    return (
      <div className="analysis-progress" role="status" aria-live="polite">
        <span className="analysis-progress-mark" aria-hidden="true" />
        <div>
          <strong>Video analizi sırada bekliyor</strong>
          <p>
            Bu bölüm otomatik yenilenir. Analiz tamamlanana kadar teslimat kanıtı
            incelemesine devam edebilirsiniz.
          </p>
        </div>
      </div>
    );
  }

  if (analysis.status === "FAILED" && analysis.failure) {
    return <VideoAnalysisFailureView failure={analysis.failure} />;
  }

  if (analysis.status === "RESULT_AVAILABLE" && analysis.result) {
    return <VideoAnalysisResultView result={analysis.result} />;
  }

  return null;
}

function VideoAnalysisFailureView({
  failure,
}: {
  failure: VideoAnalysisFailureSummary;
}) {
  return (
    <div className="analysis-failure" role="alert">
      <span className="analysis-failure-code">{failure.code}</span>
      <h5>Video analizi tamamlanamadı</h5>
      <p>{labelFailureCode(failure.code)}</p>
      <p className="analysis-advisory">
        Bu sonuç yalnızca danışmanlık amaçlıdır; teslimat kanıtı kabul veya red kararı
        vermez.
      </p>
      {failure.retryRecommended ? (
        <p className="muted-copy">
          Teknik olarak yeniden denenebilir. Yeni talep, yalnızca backend izin
          verdiğinde gönderilebilir.
        </p>
      ) : (
        <p className="muted-copy">
          Bu hata otomatik yeniden deneme için uygun görünmüyor.
        </p>
      )}
    </div>
  );
}

function VideoAnalysisResultView({ result }: { result: VideoAnalysisResult }) {
  return (
    <div className="analysis-result">
      <div className="analysis-review-notice" role="status">
        <span>Danışmanlık sonucu</span>
        <div>
          <strong>
            Video analizi tamamlandı; sonuç yalnızca danışmanlık amaçlıdır.
          </strong>
          <p>
            Teslimat onayı, ödeme serbest bırakma veya teslimat kanıtı kabul/red
            kararları bu sonuçtan otomatik türetilmez.
          </p>
        </div>
      </div>

      <section className="analysis-result-section" aria-labelledby="video-summary-title">
        <div className="analysis-section-heading">
          <h3 id="video-summary-title">Özet</h3>
        </div>
        <p>
          <strong>{labelAdvisoryOutcome(result.summary.advisoryOutcome)}</strong>
        </p>
        <p className="muted-copy">
          Süre: {formatDurationMs(result.durationMs)}
        </p>
        {result.summary.reviewReasons.length ? (
          <ul className="analysis-card-list">
            {result.summary.reviewReasons.map((reason) => (
              <li key={reason}>{labelReviewReason(reason)}</li>
            ))}
          </ul>
        ) : (
          <p className="muted-copy">Ek inceleme nedeni bildirilmedi.</p>
        )}
      </section>

      <section
        className="analysis-result-section"
        aria-labelledby="video-observations-title"
      >
        <div className="analysis-section-heading">
          <h3 id="video-observations-title">Gözlemler</h3>
          <span>{result.observations.length} kayıt</span>
        </div>
        {result.observations.length ? (
          <ul className="analysis-card-list">
            {result.observations.map((observation) => (
              <li key={observation.observationReference}>
                <div className="analysis-card-heading">
                  <strong>{observation.label}</strong>
                  <span>{labelObservationType(observation.type)}</span>
                </div>
                <p>
                  Değer: {String(observation.observedValue)} · Güven{" "}
                  {PERCENT_FORMATTER.format(observation.confidence)}
                </p>
                <p className="analysis-source-copy">
                  {formatTimeRange(
                    observation.timeRange.startMs,
                    observation.timeRange.endMs,
                  )}
                </p>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted-copy">Gözlem kaydı yok.</p>
        )}
      </section>

      <section
        className="analysis-result-section"
        aria-labelledby="video-anomalies-title"
      >
        <div className="analysis-section-heading">
          <h3 id="video-anomalies-title">Anomaliler</h3>
          <span>{result.anomalies.length} kayıt</span>
        </div>
        {result.anomalies.length ? (
          <ul className="analysis-rule-list">
            {result.anomalies.map((anomaly) => (
              <li key={anomaly.anomalyReference}>
                <div className="analysis-rule-topline">
                  <span className="analysis-category-badge">
                    {labelAnomalySeverity(anomaly.severity)}
                  </span>
                  <span>Güven {PERCENT_FORMATTER.format(anomaly.confidence)}</span>
                </div>
                <h4>{anomaly.type}</h4>
                <p>{anomaly.description}</p>
                <p className="analysis-source-copy">
                  {formatTimeRange(anomaly.timeRange.startMs, anomaly.timeRange.endMs)}
                </p>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted-copy">Anomali kaydı yok.</p>
        )}
      </section>

      {result.warnings.length ? (
        <section
          className="analysis-result-section"
          aria-labelledby="video-warnings-title"
        >
          <div className="analysis-section-heading">
            <h3 id="video-warnings-title">Uyarılar</h3>
            <span>{result.warnings.length} kayıt</span>
          </div>
          <ul className="analysis-card-list">
            {result.warnings.map((warning) => (
              <li key={`${warning.code}-${warning.path ?? "root"}`}>
                <div className="analysis-card-heading">
                  <strong>{presentWarningMessage(warning.code, warning.message)}</strong>
                  <span>
                    {WARNING_SEVERITY_LABELS[warning.severity] ?? warning.severity}
                  </span>
                </div>
                {shouldShowWarningCode(warning.code) ? (
                  <p className="muted-copy">{labelWarningCode(warning.code)}</p>
                ) : null}
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

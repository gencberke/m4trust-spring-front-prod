import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef } from "react";

import {
  requestVideoAnalysis,
  type VideoAnalysisDetail,
} from "./videoAnalysisApi";
import {
  getVideoAnalysisReadErrorMessage,
  getVideoAnalysisRequestErrorMessage,
  shouldRefetchAfterVideoAnalysisRequestError,
} from "./videoAnalysisErrors";
import {
  videoAnalysisQueryKey,
  videoAnalysisQueryOptions,
} from "./videoAnalysisQueries";

interface Props {
  legalEntityId: string;
  dealId: string;
  evidenceSubmissionId: string;
  expectedEvidenceVersion: number;
}

export function EvidenceVideoAnalysisPanel({
  legalEntityId,
  dealId,
  evidenceSubmissionId,
  expectedEvidenceVersion,
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
      {analysis.availableActions?.canRequest === true ? (
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
        Bu evidence için henüz video analizi talep edilmedi.
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
            Bu bölüm otomatik yenilenir. Analiz tamamlanana kadar evidence
            incelemesine devam edebilirsiniz.
          </p>
        </div>
      </div>
    );
  }

  if (analysis.status === "FAILED" && analysis.failure) {
    return (
      <div className="analysis-failure" role="alert">
        <span className="analysis-failure-code">{analysis.failure.code}</span>
        <h5>Video analizi tamamlanamadı</h5>
        <p>Teknik bir nedenle analiz tamamlanamadı.</p>
      </div>
    );
  }

  if (analysis.status === "RESULT_AVAILABLE") {
    return (
      <p className="success-text">
        Video analizi tamamlandı. Sonuçlar yalnızca danışmanlık amaçlıdır.
      </p>
    );
  }

  return null;
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState, type FormEvent } from "react";

import type { components } from "../../generated/core-api";
import type { DealDetail } from "../deals/dealApi";
import { dealDetailQueryKey } from "../deals/dealQueries";
import { createEvidenceDownloadLink } from "../fulfillment/fulfillmentApi";
import { fulfillmentDetailQueryOptions } from "../fulfillment/fulfillmentQueries";
import { labelAdvisoryOutcome } from "../videoAnalysis/videoAnalysisLabels";
import {
  acknowledgeDispute,
  createDisputeComment,
  openDispute,
  type DisputeDetail,
  type DisputeReasonCode,
  withdrawDispute,
} from "./caseworkApi";
import {
  getCaseworkErrorMessage,
  getCaseworkFieldErrors,
  shouldRefetchAfterMutationError,
  shouldRefetchAfterOpenError,
  shouldResetCaseworkIdempotencyKey,
} from "./caseworkErrors";
import {
  disputeCommentsQueryOptions,
  disputeDetailQueryKey,
  disputeDetailQueryOptions,
  disputeHistoryQueryKey,
  disputeHistoryQueryOptions,
} from "./caseworkQueries";

type VideoAnalysisResult = components["schemas"]["VideoAnalysisResult"];
type DisputeEvidenceSnapshotEntry =
  components["schemas"]["DisputeEvidenceSnapshotEntry"];

const DATE_FORMATTER = new Intl.DateTimeFormat("tr-TR", {
  dateStyle: "long",
  timeStyle: "short",
});

const DISPUTE_STATUS_LABELS: Record<string, string> = {
  OPEN: "Açık",
  UNDER_REVIEW: "İncelemede",
  WITHDRAWN: "Geri çekildi",
  RESOLVED: "Çözüldü",
};

const REASON_CODE_LABELS: Record<DisputeReasonCode, string> = {
  NON_DELIVERY: "Teslimat yapılmadı",
  EVIDENCE_QUALITY: "Evidence kalitesi",
  EVIDENCE_REJECTION: "Evidence reddi",
  CONTRACT_NON_CONFORMANCE: "Sözleşmeye aykırılık",
  OTHER: "Diğer",
};

function formatDate(value: string): string {
  return DATE_FORMATTER.format(new Date(value));
}

function truncateHex(value: string, head = 10, tail = 8): string {
  return value.length > head + tail + 1
    ? `${value.slice(0, head)}…${value.slice(-tail)}`
    : value;
}

interface Props {
  deal: DealDetail;
  legalEntityId: string;
}

export function DealCaseworkPanel({ deal, legalEntityId }: Props) {
  const showPanel =
    deal.casework != null || deal.availableActions.canOpenDispute === true;
  if (!showPanel) return null;

  const queryClient = useQueryClient();
  const activeSummary = deal.casework ?? null;
  const activeDisputeId = activeSummary?.disputeId;
  const canOpen = deal.availableActions.canOpenDispute === true;

  const [notice, setNotice] = useState<string>();
  const [formError, setFormError] = useState<string>();
  const [commentError, setCommentError] = useState<string>();
  const [actionError, setActionError] = useState<string>();
  const [downloadError, setDownloadError] = useState<string>();
  const [downloadingEvidenceId, setDownloadingEvidenceId] = useState<string>();
  const [confirmWithdraw, setConfirmWithdraw] = useState(false);
  const [commentPage, setCommentPage] = useState(0);

  const [reasonCode, setReasonCode] = useState<DisputeReasonCode>("NON_DELIVERY");
  const [subject, setSubject] = useState("");
  const [statement, setStatement] = useState("");
  const [commentBody, setCommentBody] = useState("");

  const openKeyRef = useRef<string | undefined>(undefined);
  const commentKeyRef = useRef<string | undefined>(undefined);
  const acknowledgeKeyRef = useRef<string | undefined>(undefined);
  const withdrawKeyRef = useRef<string | undefined>(undefined);
  const openIntentRef = useRef("");

  const fulfillmentQuery = useQuery(
    fulfillmentDetailQueryOptions(legalEntityId, deal.id, canOpen),
  );
  const historyQuery = useQuery(
    disputeHistoryQueryOptions(legalEntityId, deal.id, true),
  );
  const detailQuery = useQuery(
    disputeDetailQueryOptions(
      legalEntityId,
      deal.id,
      activeDisputeId,
      Boolean(activeDisputeId),
    ),
  );
  const commentsQuery = useQuery(
    disputeCommentsQueryOptions(
      legalEntityId,
      deal.id,
      activeDisputeId,
      Boolean(activeDisputeId),
      commentPage,
    ),
  );

  const dispute = detailQuery.data;
  const withdrawnHistory =
    historyQuery.data?.items.filter((item) => item.status === "WITHDRAWN") ?? [];

  function refreshCasework(disputeId?: string) {
    void queryClient.invalidateQueries({
      queryKey: dealDetailQueryKey(legalEntityId, deal.id),
    });
    void queryClient.invalidateQueries({
      queryKey: disputeHistoryQueryKey(legalEntityId, deal.id),
    });
    if (disputeId) {
      void queryClient.invalidateQueries({
        queryKey: disputeDetailQueryKey(legalEntityId, deal.id, disputeId),
      });
      void queryClient.invalidateQueries({
        queryKey: ["casework", legalEntityId, deal.id, "comments", disputeId],
      });
    }
  }

  useEffect(() => {
    const intent = `${reasonCode}\n${subject}\n${statement}`;
    if (openIntentRef.current && openIntentRef.current !== intent) {
      openKeyRef.current = undefined;
    }
    openIntentRef.current = intent;
  }, [reasonCode, subject, statement]);

  const openMutation = useMutation({
    mutationFn: () => {
      openKeyRef.current ??= crypto.randomUUID();
      const fulfillmentVersion = fulfillmentQuery.data?.version;
      if (fulfillmentVersion === undefined) {
        throw new Error("Fulfillment version is unavailable");
      }
      return openDispute(
        legalEntityId,
        deal.id,
        {
          reasonCode,
          subject: subject.trim(),
          statement: statement.trim(),
          expectedDealVersion: deal.version,
          expectedFulfillmentVersion: fulfillmentVersion,
        },
        openKeyRef.current,
      );
    },
    onSuccess: (created) => {
      openKeyRef.current = undefined;
      setFormError(undefined);
      setNotice("Uyuşmazlık açıldı.");
      queryClient.setQueryData(
        disputeDetailQueryKey(legalEntityId, deal.id, created.id),
        created,
      );
      refreshCasework(created.id);
    },
    onError: (error) => {
      if (shouldResetCaseworkIdempotencyKey(error, "open")) {
        openKeyRef.current = undefined;
      }
      if (shouldRefetchAfterOpenError(error)) refreshCasework();
      setFormError(getCaseworkErrorMessage(error));
    },
  });

  const commentMutation = useMutation({
    mutationFn: (expectedVersion: number) => {
      commentKeyRef.current ??= crypto.randomUUID();
      return createDisputeComment(
        legalEntityId,
        deal.id,
        activeDisputeId!,
        { body: commentBody.trim(), expectedVersion },
        commentKeyRef.current,
      );
    },
    onSuccess: () => {
      commentKeyRef.current = undefined;
      setCommentBody("");
      setCommentError(undefined);
      setNotice("Yorum eklendi.");
      refreshCasework(activeDisputeId);
    },
    onError: (error) => {
      if (shouldResetCaseworkIdempotencyKey(error, "comment")) {
        commentKeyRef.current = undefined;
      }
      if (shouldRefetchAfterMutationError(error)) refreshCasework(activeDisputeId);
      setCommentError(getCaseworkErrorMessage(error));
    },
  });

  const acknowledgeMutation = useMutation({
    mutationFn: (expectedVersion: number) => {
      acknowledgeKeyRef.current ??= crypto.randomUUID();
      return acknowledgeDispute(
        legalEntityId,
        deal.id,
        activeDisputeId!,
        { expectedVersion },
        acknowledgeKeyRef.current,
      );
    },
    onSuccess: (updated) => {
      acknowledgeKeyRef.current = undefined;
      setActionError(undefined);
      setNotice("Uyuşmazlık onaylandı.");
      queryClient.setQueryData(
        disputeDetailQueryKey(legalEntityId, deal.id, updated.id),
        updated,
      );
      refreshCasework(updated.id);
    },
    onError: (error) => {
      if (shouldResetCaseworkIdempotencyKey(error, "acknowledge")) {
        acknowledgeKeyRef.current = undefined;
      }
      if (shouldRefetchAfterMutationError(error)) refreshCasework(activeDisputeId);
      setActionError(getCaseworkErrorMessage(error));
    },
  });

  const withdrawMutation = useMutation({
    mutationFn: (expectedVersion: number) => {
      withdrawKeyRef.current ??= crypto.randomUUID();
      return withdrawDispute(
        legalEntityId,
        deal.id,
        activeDisputeId!,
        { expectedVersion },
        withdrawKeyRef.current,
      );
    },
    onSuccess: () => {
      withdrawKeyRef.current = undefined;
      setConfirmWithdraw(false);
      setActionError(undefined);
      setNotice("Uyuşmazlık geri çekildi.");
      refreshCasework(activeDisputeId);
    },
    onError: (error) => {
      if (shouldResetCaseworkIdempotencyKey(error, "withdraw")) {
        withdrawKeyRef.current = undefined;
      }
      if (shouldRefetchAfterMutationError(error)) refreshCasework(activeDisputeId);
      setActionError(getCaseworkErrorMessage(error));
    },
  });

  function handleOpenSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(undefined);
    openMutation.mutate();
  }

  function handleCommentSubmit(event: FormEvent) {
    event.preventDefault();
    if (!dispute) return;
    setCommentError(undefined);
    commentMutation.mutate(dispute.version);
  }

  async function handleEvidenceDownload(evidence: DisputeEvidenceSnapshotEntry) {
    setDownloadError(undefined);
    setDownloadingEvidenceId(evidence.evidenceSubmissionId);
    try {
      const link = await createEvidenceDownloadLink(
        legalEntityId,
        deal.id,
        evidence.evidenceSubmissionId,
      );
      window.open(link.downloadUrl, "_blank", "noopener,noreferrer");
    } catch (error) {
      setDownloadError(getCaseworkErrorMessage(error));
    } finally {
      setDownloadingEvidenceId(undefined);
    }
  }

  const openFieldErrors = openMutation.isError
    ? getCaseworkFieldErrors(openMutation.error)
    : {};

  return (
    <section className="workspace-panel casework-panel" aria-labelledby="casework-title">
      <div className="panel-heading">
        <p className="section-kicker">Casework</p>
        <h2 id="casework-title">Uyuşmazlık</h2>
      </div>

      {notice ? <p className="success-notice workspace-notice">{notice}</p> : null}

      {canOpen ? (
        <form className="casework-open-form" onSubmit={handleOpenSubmit}>
          <h3>Yeni uyuşmazlık aç</h3>
          <p className="muted-copy">
            Bu işlem yalnızca backend izin verdiğinde görünür. Açılış anındaki
            fulfillment ve evidence durumu değişmez şekilde sabitlenir.
          </p>
          {fulfillmentQuery.isLoading ? (
            <p>Yükleniyor…</p>
          ) : fulfillmentQuery.isError ? (
            <p className="form-alert" role="alert">
              {getCaseworkErrorMessage(fulfillmentQuery.error)}
            </p>
          ) : (
            <>
              <label>
                Neden
                <select
                  value={reasonCode}
                  onChange={(event) =>
                    setReasonCode(event.target.value as DisputeReasonCode)
                  }
                >
                  {(Object.keys(REASON_CODE_LABELS) as DisputeReasonCode[]).map(
                    (code) => (
                      <option key={code} value={code}>
                        {REASON_CODE_LABELS[code]}
                      </option>
                    ),
                  )}
                </select>
              </label>
              <label>
                Konu
                <input
                  value={subject}
                  maxLength={200}
                  onChange={(event) => setSubject(event.target.value)}
                  required
                />
                {openFieldErrors.subject ? (
                  <span className="field-error">{openFieldErrors.subject}</span>
                ) : null}
              </label>
              <label>
                Açıklama
                <textarea
                  value={statement}
                  maxLength={4000}
                  rows={5}
                  onChange={(event) => setStatement(event.target.value)}
                  required
                />
                {openFieldErrors.statement ? (
                  <span className="field-error">{openFieldErrors.statement}</span>
                ) : null}
              </label>
              {formError ? (
                <p className="form-alert" role="alert">
                  {formError}
                </p>
              ) : null}
              <button
                type="submit"
                className="primary-button"
                disabled={openMutation.isPending || !fulfillmentQuery.data}
              >
                {openMutation.isPending ? "Açılıyor…" : "Uyuşmazlığı aç"}
              </button>
            </>
          )}
        </form>
      ) : null}

      {activeSummary ? (
        <div className="casework-active-card">
          <div className="casework-active-heading">
            <h3>{activeSummary.subject}</h3>
            <span className="casework-status-badge" data-status={activeSummary.status}>
              {DISPUTE_STATUS_LABELS[activeSummary.status] ?? activeSummary.status}
            </span>
          </div>
          <p className="muted-copy">
            {REASON_CODE_LABELS[activeSummary.reasonCode]} ·{" "}
            {activeSummary.openingLegalEntity.legalName} ·{" "}
            {formatDate(activeSummary.openedAt)}
          </p>

          {detailQuery.isLoading ? <p>Yükleniyor…</p> : null}
          {detailQuery.isError ? (
            <p className="form-alert" role="alert">
              {getCaseworkErrorMessage(detailQuery.error)}
              <button
                type="button"
                className="secondary-button"
                onClick={() => refreshCasework(activeDisputeId)}
              >
                Yeniden dene
              </button>
            </p>
          ) : null}

          {dispute ? (
            <>
              <p className="casework-statement">{dispute.statement}</p>
              <SnapshotSection
                dispute={dispute}
                downloadingEvidenceId={downloadingEvidenceId}
                onDownload={handleEvidenceDownload}
              />
              {downloadError ? (
                <p className="form-alert" role="alert">
                  {downloadError}
                </p>
              ) : null}

              <section className="casework-comments" aria-labelledby="casework-comments-title">
                <h4 id="casework-comments-title">Yorumlar</h4>
                {commentsQuery.isLoading ? <p>Yükleniyor…</p> : null}
                {commentsQuery.isError ? (
                  <p className="form-alert" role="alert">
                    {getCaseworkErrorMessage(commentsQuery.error)}
                  </p>
                ) : null}
                {commentsQuery.data ? (
                  <>
                    <ul className="casework-comment-list">
                      {commentsQuery.data.items.map((comment) => (
                        <li key={comment.id}>
                          <div className="casework-comment-meta">
                            <strong>{comment.authorAttribution.displayName}</strong>
                            <span>{comment.authorAttribution.legalName}</span>
                            <time dateTime={comment.createdAt}>
                              {formatDate(comment.createdAt)}
                            </time>
                          </div>
                          <p>{comment.body}</p>
                        </li>
                      ))}
                    </ul>
                    {commentsQuery.data.totalPages > 1 ? (
                      <div className="casework-pagination">
                        <button
                          type="button"
                          className="secondary-button"
                          disabled={commentPage <= 0}
                          onClick={() => setCommentPage((page) => page - 1)}
                        >
                          Önceki
                        </button>
                        <span>
                          Sayfa {commentsQuery.data.page + 1} /{" "}
                          {commentsQuery.data.totalPages}
                        </span>
                        <button
                          type="button"
                          className="secondary-button"
                          disabled={commentPage + 1 >= commentsQuery.data.totalPages}
                          onClick={() => setCommentPage((page) => page + 1)}
                        >
                          Sonraki
                        </button>
                      </div>
                    ) : null}
                  </>
                ) : null}

                {dispute.availableActions.canComment === true ? (
                  <form className="casework-comment-form" onSubmit={handleCommentSubmit}>
                    <label>
                      Yeni yorum
                      <textarea
                        value={commentBody}
                        maxLength={4000}
                        rows={4}
                        required
                        onChange={(event) => setCommentBody(event.target.value)}
                      />
                    </label>
                    {commentError ? (
                      <p className="form-alert" role="alert">
                        {commentError}
                      </p>
                    ) : null}
                    <button
                      type="submit"
                      className="secondary-button"
                      disabled={commentMutation.isPending}
                    >
                      {commentMutation.isPending ? "Gönderiliyor…" : "Yorum ekle"}
                    </button>
                  </form>
                ) : null}
              </section>

              <div className="casework-actions">
                {dispute.availableActions.canAcknowledge === true ? (
                  <button
                    type="button"
                    className="primary-button"
                    disabled={acknowledgeMutation.isPending}
                    onClick={() => acknowledgeMutation.mutate(dispute.version)}
                  >
                    {acknowledgeMutation.isPending ? "Onaylanıyor…" : "Onayla"}
                  </button>
                ) : null}
                {dispute.availableActions.canWithdraw === true ? (
                  <button
                    type="button"
                    className="danger-button"
                    disabled={withdrawMutation.isPending}
                    onClick={() => setConfirmWithdraw(true)}
                  >
                    Geri çek
                  </button>
                ) : null}
              </div>

              {confirmWithdraw ? (
                <div className="casework-confirm" role="dialog" aria-modal="true">
                  <p>Bu uyuşmazlığı geri çekmek istediğinize emin misiniz?</p>
                  <div className="casework-actions">
                    <button
                      type="button"
                      className="danger-button"
                      disabled={withdrawMutation.isPending}
                      onClick={() => withdrawMutation.mutate(dispute.version)}
                    >
                      {withdrawMutation.isPending ? "Geri çekiliyor…" : "Evet, geri çek"}
                    </button>
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => setConfirmWithdraw(false)}
                    >
                      Vazgeç
                    </button>
                  </div>
                </div>
              ) : null}

              {actionError ? (
                <p className="form-alert" role="alert">
                  {actionError}
                </p>
              ) : null}
            </>
          ) : null}
        </div>
      ) : null}

      {withdrawnHistory.length > 0 ? (
        <section className="casework-history" aria-labelledby="casework-history-title">
          <h3 id="casework-history-title">Geri çekilmiş geçmiş</h3>
          <ul>
            {withdrawnHistory.map((item) => (
              <li key={item.id}>
                <strong>{item.subject}</strong>
                <span className="casework-status-badge" data-status={item.status}>
                  {DISPUTE_STATUS_LABELS[item.status] ?? item.status}
                </span>
                <p className="muted-copy">
                  {REASON_CODE_LABELS[item.reasonCode]} ·{" "}
                  {item.openingLegalEntity.legalName} · {formatDate(item.openedAt)}
                  {item.withdrawnAt ? ` · Geri çekildi: ${formatDate(item.withdrawnAt)}` : ""}
                </p>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </section>
  );
}

function SnapshotSection({
  dispute,
  downloadingEvidenceId,
  onDownload,
}: {
  dispute: DisputeDetail;
  downloadingEvidenceId?: string;
  onDownload: (evidence: DisputeEvidenceSnapshotEntry) => void;
}) {
  const snapshot = dispute.openingSnapshot;
  return (
    <section className="casework-snapshot" aria-labelledby="casework-snapshot-title">
      <h4 id="casework-snapshot-title">Açılış anındaki snapshot</h4>
      <dl className="casework-snapshot-meta">
        <div>
          <dt>Fulfillment durumu</dt>
          <dd>{snapshot.fulfillmentStatusAtOpen}</dd>
        </div>
        <div>
          <dt>Evidence sayısı</dt>
          <dd>{snapshot.evidence.length}</dd>
        </div>
      </dl>
      <ul className="casework-evidence-list">
        {snapshot.evidence.map((evidence) => (
          <li key={evidence.evidenceSubmissionId}>
            <div>
              <strong>{evidence.fileName}</strong>
              <span>{evidence.evidenceType}</span>
              <span>{evidence.statusAtOpen}</span>
            </div>
            <p className="muted-copy">
              SHA-256: <code>{truncateHex(evidence.verifiedSha256)}</code>
            </p>
            <button
              type="button"
              className="secondary-button"
              disabled={downloadingEvidenceId === evidence.evidenceSubmissionId}
              onClick={() => onDownload(evidence)}
            >
              {downloadingEvidenceId === evidence.evidenceSubmissionId
                ? "Hazırlanıyor…"
                : "İndir"}
            </button>
          </li>
        ))}
      </ul>
      {snapshot.videoAnalysis.map((entry) => (
        <div key={entry.jobId} className="casework-pinned-video">
          <h5>Sabitlenmiş video analizi</h5>
          <p className="muted-copy">
            Evidence: <code>{entry.evidenceSubmissionId}</code>
          </p>
          <PinnedVideoResult result={entry.result} />
        </div>
      ))}
    </section>
  );
}

function PinnedVideoResult({ result }: { result: VideoAnalysisResult }) {
  const advisory = result.summary?.advisoryOutcome;
  return (
    <div className="casework-pinned-video-result">
      <p className="analysis-advisory">
        Bu sonuç açılış anında sabitlenmiş danışmanlık verisidir; evidence veya
        ödeme kararı vermez.
      </p>
      {advisory ? (
        <p>
          <strong>Danışmanlık özeti:</strong> {labelAdvisoryOutcome(advisory)}
        </p>
      ) : null}
    </div>
  );
}

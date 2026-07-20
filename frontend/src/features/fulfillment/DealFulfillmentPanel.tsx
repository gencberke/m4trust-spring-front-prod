import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState, type ChangeEvent } from "react";

import type { DealDetail } from "../deals/dealApi";
import { dealDetailQueryKey } from "../deals/dealQueries";
import {
  acceptEvidence,
  createEvidenceDownloadLink,
  createEvidenceUploadIntent,
  finalizeEvidenceUpload,
  getFulfillment,
  rejectEvidence,
  startFulfillment,
  type EvidenceMediaType,
  type EvidenceSubmission,
  type EvidenceType,
  type EvidenceUploadIntent,
  type FulfillmentDetail,
} from "./fulfillmentApi";
import {
  getFulfillmentErrorMessage,
  isEvidenceUploadExpired,
  isFulfillmentNotFound,
  shouldResetFulfillmentIdempotencyKey,
} from "./fulfillmentErrors";
import {
  fulfillmentDetailQueryKey,
  fulfillmentDetailQueryOptions,
} from "./fulfillmentQueries";
import {
  ACCEPTED_EVIDENCE_FILE_INPUT_ACCEPT,
  computeSha256Hex,
  DirectUploadError,
  inferEvidenceMediaType,
  isLikelyExpiredUploadStatus,
  putEvidenceBytes,
} from "./evidenceUpload";

const DATE_FORMATTER = new Intl.DateTimeFormat("tr-TR", {
  dateStyle: "long",
  timeStyle: "short",
});

function formatDate(value: string): string {
  return DATE_FORMATTER.format(new Date(value));
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const units = ["KB", "MB", "GB"];
  let value = bytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unitIndex]}`;
}

const FULFILLMENT_STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: "Başlatılmadı",
  IN_PROGRESS: "Devam ediyor",
  EVIDENCE_REQUIRED: "Evidence bekleniyor",
  REVIEW_REQUIRED: "İnceleme bekleniyor",
  COMPLETED: "Tamamlandı",
  CANCELLED: "İptal edildi",
};

const EVIDENCE_STATUS_LABELS: Record<string, string> = {
  PENDING_UPLOAD: "Yükleme bekleniyor",
  SUBMITTED: "Sunuldu",
  ACCEPTED: "Onaylandı",
  REJECTED: "Reddedildi",
};

const EVIDENCE_TYPE_LABELS: Record<string, string> = {
  DELIVERY_NOTE: "Teslimat notu",
  INVOICE: "Fatura",
  VIDEO: "Video",
  PHOTO: "Fotoğraf",
  SIGNED_DOCUMENT: "İmzalı belge",
  OTHER: "Diğer",
};

function fulfillmentStatusLabel(status: string): string {
  return FULFILLMENT_STATUS_LABELS[status] ?? status;
}

function evidenceStatusLabel(status: string): string {
  return EVIDENCE_STATUS_LABELS[status] ?? status;
}

function evidenceTypeLabel(type: string): string {
  return EVIDENCE_TYPE_LABELS[type] ?? type;
}

type UploadStage =
  | "idle"
  | "hashing"
  | "creating-intent"
  | "uploading"
  | "finalizing"
  | "done"
  | "failed";

type FailedStage = "hashing" | "intent" | "upload" | "finalize";

interface UploadState {
  stage: UploadStage;
  file?: File;
  evidenceType?: EvidenceType;
  mediaType?: EvidenceMediaType;
  sha256?: string;
  intent?: EvidenceUploadIntent;
  progress?: number;
  failedStage?: FailedStage;
  expired?: boolean;
  errorMessage?: string;
}

const BUSY_STAGES: readonly UploadStage[] = [
  "hashing",
  "creating-intent",
  "uploading",
  "finalizing",
];

function isIntentExpired(intent: EvidenceUploadIntent | undefined): boolean {
  if (!intent) {
    return false;
  }
  return new Date(intent.expiresAt).getTime() <= Date.now();
}

interface Props {
  deal: DealDetail;
  legalEntityId: string;
}

export function DealFulfillmentPanel({ deal, legalEntityId }: Props) {
  const queryClient = useQueryClient();
  const startKeyRef = useRef<string | undefined>(undefined);
  const finalizeKeyRef = useRef<string | undefined>(undefined);
  const reviewKeyRef = useRef<string | undefined>(undefined);
  const attemptIdRef = useRef(0);

  const fulfillmentId = deal.fulfillment?.fulfillmentId;
  const hasFulfillment = Boolean(fulfillmentId);

  const fulfillmentQuery = useQuery(
    fulfillmentDetailQueryOptions(legalEntityId, deal.id, hasFulfillment),
  );
  const fulfillment = fulfillmentQuery.data;

  const [notice, setNotice] = useState<string>();
  const [uploadState, setUploadState] = useState<UploadState>({ stage: "idle" });
  const [downloadError, setDownloadError] = useState<string>();
  const [downloadingId, setDownloadingId] = useState<string>();
  const [rejectionReason, setRejectionReason] = useState("");
  const [reviewError, setReviewError] = useState<string>();

  const isUploadBusy = BUSY_STAGES.includes(uploadState.stage);

  function refreshAfterMutation() {
    void queryClient.invalidateQueries({
      queryKey: dealDetailQueryKey(legalEntityId, deal.id),
    });
    void queryClient.invalidateQueries({
      queryKey: fulfillmentDetailQueryKey(legalEntityId, deal.id),
    });
  }

  function freshIdempotencyKey() {
    return crypto.randomUUID();
  }

  function resetStartKey() {
    startKeyRef.current = freshIdempotencyKey();
  }

  function resetFinalizeKey() {
    finalizeKeyRef.current = freshIdempotencyKey();
  }

  function resetReviewKey() {
    reviewKeyRef.current = freshIdempotencyKey();
  }

  const startMutation = useMutation({
    mutationFn: () =>
      startFulfillment(
        legalEntityId,
        deal.id,
        { expectedVersion: deal.version },
        startKeyRef.current!,
      ),
    onSuccess: () => {
      setNotice(undefined);
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetFulfillmentIdempotencyKey(error, "start")) {
        resetStartKey();
        refreshAfterMutation();
      }
      setNotice(getFulfillmentErrorMessage(error));
    },
  });

  const finalizeMutation = useMutation({
    mutationFn: (variables: {
      intent: EvidenceUploadIntent;
      file: File;
      sha256: string;
    }) =>
      finalizeEvidenceUpload(
        legalEntityId,
        deal.id,
        variables.intent.evidence.id,
        { sizeBytes: variables.file.size, sha256: variables.sha256 },
        finalizeKeyRef.current!,
      ),
    onSuccess: () => {
      setUploadState({ stage: "done" });
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetFulfillmentIdempotencyKey(error, "upload")) {
        resetFinalizeKey();
        refreshAfterMutation();
      }
      setUploadState((previous) => ({
        ...previous,
        stage: "failed",
        failedStage: "finalize",
        expired:
          isEvidenceUploadExpired(error) ||
          isIntentExpired(previous.intent),
        errorMessage: getFulfillmentErrorMessage(error),
      }));
    },
  });

  async function performUpload(
    intent: EvidenceUploadIntent,
    file: File,
    sha256: string,
    attemptId: number,
  ) {
    setUploadState({ stage: "uploading", file, sha256, intent, progress: 0 });
    try {
      await putEvidenceBytes(intent.uploadUrl, intent.uploadHeaders, file, (fraction) => {
        if (attemptIdRef.current !== attemptId) return;
        setUploadState((previous) =>
          previous.stage === "uploading"
            ? { ...previous, progress: fraction }
            : previous,
        );
      });
    } catch (error) {
      if (attemptIdRef.current !== attemptId) return;
      const status = error instanceof DirectUploadError ? error.status : 0;
      setUploadState((previous) => ({
        ...previous,
        stage: "failed",
        failedStage: "upload",
        expired: isLikelyExpiredUploadStatus(status) || isIntentExpired(intent),
        errorMessage:
          "Dosya depolama alanına yüklenemedi. Bağlantınızı kontrol edip yeniden deneyin.",
      }));
      return;
    }
    if (attemptIdRef.current !== attemptId) return;
    await finalizeMutation.mutateAsync({ intent, file, sha256 });
  }

  async function createIntentAndUpload(
    file: File,
    evidenceType: EvidenceType,
    mediaType: EvidenceMediaType,
    sha256: string,
    attemptId: number,
  ) {
    setUploadState({
      stage: "creating-intent",
      file,
      evidenceType,
      mediaType,
      sha256,
    });
    let intent: EvidenceUploadIntent;
    try {
      intent = await createEvidenceUploadIntent(legalEntityId, deal.id, {
        evidenceType,
        mediaType,
        fileName: file.name,
        sizeBytes: file.size,
        sha256,
      });
    } catch (error) {
      if (attemptIdRef.current !== attemptId) return;
      if (shouldResetFulfillmentIdempotencyKey(error, "upload")) {
        refreshAfterMutation();
      }
      setUploadState((previous) => ({
        ...previous,
        stage: "failed",
        failedStage: "intent",
        expired: isEvidenceUploadExpired(error),
        errorMessage: getFulfillmentErrorMessage(error),
      }));
      return;
    }
    if (attemptIdRef.current !== attemptId) return;
    await performUpload(intent, file, sha256, attemptId);
  }

  async function handleFileSelected(
    event: ChangeEvent<HTMLInputElement>,
    evidenceType: EvidenceType,
  ) {
    const file = event.target.files?.[0];
    if (!file) return;
    const mediaType = inferEvidenceMediaType(file);
    if (!mediaType) {
      setUploadState({
        stage: "failed",
        failedStage: "hashing",
        errorMessage:
          "Desteklenmeyen dosya biçimi. PDF, DOCX, JPEG, PNG veya MP4 yükleyin.",
      });
      return;
    }
    const attemptId = ++attemptIdRef.current;
    resetFinalizeKey();
    setUploadState({ stage: "hashing", file, evidenceType, mediaType });
    let sha256: string;
    try {
      sha256 = await computeSha256Hex(file);
    } catch {
      if (attemptIdRef.current !== attemptId) return;
      setUploadState({
        stage: "failed",
        failedStage: "hashing",
        errorMessage:
          "Dosya doğrulanamadı. Başka bir dosya ile yeniden deneyin.",
      });
      return;
    }
    if (attemptIdRef.current !== attemptId) return;
    await createIntentAndUpload(file, evidenceType, mediaType, sha256, attemptId);
  }

  function handleRetry() {
    const { file, evidenceType, mediaType, sha256, intent, failedStage } =
      uploadState;
    if (!file || !evidenceType || !mediaType || !sha256) return;
    const attemptId = ++attemptIdRef.current;
    if (failedStage === "upload" && intent && !isIntentExpired(intent)) {
      resetFinalizeKey();
      void performUpload(intent, file, sha256, attemptId);
      return;
    }
    resetFinalizeKey();
    void createIntentAndUpload(file, evidenceType, mediaType, sha256, attemptId);
  }

  async function handleDownload(submission: EvidenceSubmission) {
    setDownloadError(undefined);
    setDownloadingId(submission.id);
    try {
      const link = await createEvidenceDownloadLink(
        legalEntityId,
        deal.id,
        submission.id,
      );
      window.open(link.downloadUrl, "_blank", "noopener,noreferrer");
    } catch (error) {
      setDownloadError(getFulfillmentErrorMessage(error));
    } finally {
      setDownloadingId(undefined);
    }
  }

  const acceptMutation = useMutation({
    mutationFn: (evidence: EvidenceSubmission) =>
      acceptEvidence(
        legalEntityId,
        deal.id,
        evidence.id,
        {
          expectedVersion: deal.version,
          expectedEvidenceVersion: evidence.version,
        },
        reviewKeyRef.current!,
      ),
    onSuccess: () => {
      setReviewError(undefined);
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetFulfillmentIdempotencyKey(error, "review")) {
        resetReviewKey();
        refreshAfterMutation();
      }
      setReviewError(getFulfillmentErrorMessage(error));
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (variables: { evidence: EvidenceSubmission; reason: string }) =>
      rejectEvidence(
        legalEntityId,
        deal.id,
        variables.evidence.id,
        {
          expectedVersion: deal.version,
          expectedEvidenceVersion: variables.evidence.version,
          reason: variables.reason,
        },
        reviewKeyRef.current!,
      ),
    onSuccess: () => {
      setReviewError(undefined);
      setRejectionReason("");
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetFulfillmentIdempotencyKey(error, "review")) {
        resetReviewKey();
        refreshAfterMutation();
      }
      setReviewError(getFulfillmentErrorMessage(error));
    },
  });

  function handleAccept(evidence: EvidenceSubmission) {
    resetReviewKey();
    acceptMutation.mutate(evidence);
  }

  function handleReject(evidence: EvidenceSubmission) {
    const reason = rejectionReason.trim();
    if (reason.length === 0 || reason.length > 1000) {
      setReviewError("Reddetme sebebi 1–1000 karakter arasında olmalıdır.");
      return;
    }
    resetReviewKey();
    rejectMutation.mutate({ evidence, reason });
  }

  const canStart = deal.availableActions.canStartFulfillment;
  const canUpload = fulfillment?.milestone.availableActions.canUpload ?? false;
  const canAccept = deal.availableActions.canAcceptEvidence;
  const canReject = deal.availableActions.canRejectEvidence;

  if (fulfillmentQuery.isLoading) {
    return (
      <section className="panel" aria-live="polite">
        <h2>Fulfillment</h2>
        <p>Yükleniyor…</p>
      </section>
    );
  }

  if (fulfillmentQuery.isError && !isFulfillmentNotFound(fulfillmentQuery.error)) {
    return (
      <section className="panel" aria-live="polite">
        <h2>Fulfillment</h2>
        <p className="error-text" role="alert">
          {getFulfillmentErrorMessage(fulfillmentQuery.error)}
        </p>
        <button
          type="button"
          className="primary-button"
          onClick={() =>
            void queryClient.invalidateQueries({
              queryKey: fulfillmentDetailQueryKey(legalEntityId, deal.id),
            })
          }
        >
          Yeniden dene
        </button>
      </section>
    );
  }

  const currentEvidence = fulfillment?.currentEvidence;

  return (
    <section className="panel" aria-live="polite">
      <h2>Fulfillment</h2>

      {!fulfillment && canStart && (
        <div className="fulfillment-start">
          <p>Fulfillment henüz başlatılmadı.</p>
          {notice && (
            <p className="error-text" role="alert">
              {notice}
            </p>
          )}
          <button
            type="button"
            className="primary-button"
            onClick={() => {
              resetStartKey();
              startMutation.mutate();
            }}
            disabled={startMutation.isPending}
          >
            {startMutation.isPending ? "Başlatılıyor…" : "Fulfillment başlat"}
          </button>
        </div>
      )}

      {!fulfillment && !canStart && (
        <p>Fulfillment henüz başlatılmadı.</p>
      )}

      {fulfillment && (
        <div className="fulfillment-detail">
          <div className="fulfillment-status">
            <strong>Durum:</strong>{" "}
            {fulfillmentStatusLabel(fulfillment.status)}
          </div>

          <div className="milestone-card">
            <h3>{fulfillment.milestone.title}</h3>
            {fulfillment.milestone.description && (
              <p>{fulfillment.milestone.description}</p>
            )}
            {fulfillment.milestone.ruleReferences.length > 0 && (
              <div className="rule-references">
                <strong>İlişkili kurallar:</strong>
                <ul>
                  {fulfillment.milestone.ruleReferences.map((ref) => (
                    <li key={ref.ruleReference}>
                      {ref.ruleReference}{" "}
                      <span className="rule-category">({ref.category})</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          {canUpload && (
            <div className="evidence-upload">
              <h4>Yeni evidence yükle</h4>
              {uploadState.stage === "idle" && (
                <EvidenceUploadForm onFileSelected={handleFileSelected} />
              )}
              {uploadState.stage === "hashing" && (
                <p>Dosya doğrulanıyor…</p>
              )}
              {uploadState.stage === "creating-intent" && (
                <p>Yükleme hazırlanıyor…</p>
              )}
              {uploadState.stage === "uploading" && (
                <div>
                  <p>Dosya yükleniyor…</p>
                  <progress value={uploadState.progress ?? 0} max={1} />
                </div>
              )}
              {uploadState.stage === "finalizing" && (
                <p>Yükleme sonlandırılıyor…</p>
              )}
              {uploadState.stage === "done" && (
                <p className="success-text">
                  Evidence yüklendi ve incelemeye gönderildi.
                </p>
              )}
              {uploadState.stage === "failed" && (
                <div className="upload-failure" role="alert">
                  <p className="error-text">
                    {uploadState.errorMessage ?? "Yükleme başarısız oldu."}
                  </p>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={handleRetry}
                    disabled={isUploadBusy}
                  >
                    Yeniden dene
                  </button>
                  <button
                    type="button"
                    className="text-button"
                    onClick={() => setUploadState({ stage: "idle" })}
                  >
                    Vazgeç
                  </button>
                </div>
              )}
            </div>
          )}

          {currentEvidence && currentEvidence.status === "SUBMITTED" && (
            <div className="current-evidence">
              <h4>Mevcut evidence</h4>
              <EvidenceSummary submission={currentEvidence} />
              {canAccept && (
                <div className="review-actions">
                  {reviewError && (
                    <p className="error-text" role="alert">
                      {reviewError}
                    </p>
                  )}
                  <button
                    type="button"
                    className="primary-button"
                    onClick={() => handleAccept(currentEvidence)}
                    disabled={acceptMutation.isPending || rejectMutation.isPending}
                  >
                    {acceptMutation.isPending
                      ? "Onaylanıyor…"
                      : "Evidence’i onayla"}
                  </button>
                  <div className="reject-form">
                    <label htmlFor="rejection-reason">Reddetme sebebi</label>
                    <textarea
                      id="rejection-reason"
                      rows={3}
                      maxLength={1000}
                      value={rejectionReason}
                      onChange={(event) => setRejectionReason(event.target.value)}
                      disabled={rejectMutation.isPending}
                    />
                    <button
                      type="button"
                      className="danger-button"
                      onClick={() => handleReject(currentEvidence)}
                      disabled={
                        rejectMutation.isPending ||
                        acceptMutation.isPending ||
                        rejectionReason.trim().length === 0
                      }
                    >
                      {rejectMutation.isPending
                        ? "Reddediliyor…"
                        : "Evidence’i reddet"}
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {fulfillment.history.length > 0 && (
            <div className="evidence-history">
              <h4>Evidence geçmişi</h4>
              {downloadError && (
                <p className="error-text" role="alert">
                  {downloadError}
                </p>
              )}
              <ul>
                {fulfillment.history.map((submission) => (
                  <li key={submission.id}>
                    <EvidenceSummary submission={submission} />
                    {submission.availableActions.canDownload && (
                      <button
                        type="button"
                        className="text-button"
                        onClick={() => handleDownload(submission)}
                        disabled={downloadingId === submission.id}
                      >
                        {downloadingId === submission.id
                          ? "Hazırlanıyor…"
                          : "İndir"}
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function EvidenceUploadForm({
  onFileSelected,
}: {
  onFileSelected: (
    event: ChangeEvent<HTMLInputElement>,
    evidenceType: EvidenceType,
  ) => void;
}) {
  const [evidenceType, setEvidenceType] = useState<EvidenceType>("DELIVERY_NOTE");

  return (
    <div className="evidence-upload-form">
      <label htmlFor="evidence-type">Evidence türü</label>
      <select
        id="evidence-type"
        value={evidenceType}
        onChange={(event) =>
          setEvidenceType(event.target.value as EvidenceType)
        }
      >
        <option value="DELIVERY_NOTE">{evidenceTypeLabel("DELIVERY_NOTE")}</option>
        <option value="INVOICE">{evidenceTypeLabel("INVOICE")}</option>
        <option value="VIDEO">{evidenceTypeLabel("VIDEO")}</option>
        <option value="PHOTO">{evidenceTypeLabel("PHOTO")}</option>
        <option value="SIGNED_DOCUMENT">
          {evidenceTypeLabel("SIGNED_DOCUMENT")}
        </option>
        <option value="OTHER">{evidenceTypeLabel("OTHER")}</option>
      </select>

      <label htmlFor="evidence-file">Dosya</label>
      <input
        id="evidence-file"
        type="file"
        accept={ACCEPTED_EVIDENCE_FILE_INPUT_ACCEPT}
        onChange={(event) => onFileSelected(event, evidenceType)}
      />
      <p className="hint">
        PDF, DOCX, JPEG, PNG veya MP4; boyut sınırı sunucu tarafından belirlenir.
      </p>
    </div>
  );
}

function EvidenceSummary({ submission }: { submission: EvidenceSubmission }) {
  return (
    <div className="evidence-summary">
      <div>
        <strong>{submission.fileName}</strong>
        <span className="evidence-type">
          {" "}
          ({evidenceTypeLabel(submission.evidenceType)})
        </span>
      </div>
      <div className="evidence-meta">
        <span>Durum: {evidenceStatusLabel(submission.status)}</span>
        {" | "}
        <span>Medya: {submission.mediaType}</span>
        {" | "}
        <span>
          Boyut:{" "}
          {submission.status === "PENDING_UPLOAD"
            ? formatBytes(submission.clientSizeBytes)
            : formatBytes(submission.verifiedSizeBytes)}
        </span>
        {" | "}
        <span>Oluşturulma: {formatDate(submission.createdAt)}</span>
        {submission.status === "SUBMITTED" && submission.submittedAt && (
          <span> | Sunulma: {formatDate(submission.submittedAt)}</span>
        )}
        {submission.status === "ACCEPTED" && submission.acceptedAt && (
          <span> | Onay: {formatDate(submission.acceptedAt)}</span>
        )}
        {submission.status === "REJECTED" && (
          <>
            {submission.rejectedAt && (
              <span> | Red: {formatDate(submission.rejectedAt)}</span>
            )}
            <p className="rejection-reason">
              Sebep: {submission.rejectionReason}
            </p>
          </>
        )}
      </div>
    </div>
  );
}

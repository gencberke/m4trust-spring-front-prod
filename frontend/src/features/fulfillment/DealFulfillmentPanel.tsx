import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState, type ChangeEvent } from "react";

import { StatusBadge } from "@/shared";
import styles from "./Fulfillment.module.css";

import type { DealDetail } from "../deals/dealApi";
import { dealDetailQueryKey } from "../deals/dealQueries";
import {
  acceptEvidence,
  acceptFulfillmentWithoutEvidence,
  cancelEvidenceUpload,
  createEvidenceDownloadLink,
  createEvidenceUploadIntent,
  finalizeEvidenceUpload,
  rejectEvidence,
  startFulfillment,
  type EvidenceMediaType,
  type EvidenceSubmission,
  type EvidenceType,
  type EvidenceUploadIntent,
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
import { EvidenceHistory } from "./components/EvidenceHistory";
import { EvidenceReviewSection } from "./components/EvidenceReviewSection";
import { EvidenceSummary } from "./components/EvidenceSummary";
import {
  EVIDENCE_POLICY_LABELS,
  canCancelEvidenceUpload,
  deriveTurnBanner,
  evidenceTypeLabel,
  findCancellablePendingEvidence,
  fulfillmentStatusLabel,
  sortEvidenceChronologically,
} from "./components/fulfillmentPresentation";

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
  readOnly?: boolean;
  onNavigateToClosure?: () => void;
}

export function DealFulfillmentPanel({
  deal,
  legalEntityId,
  readOnly = false,
  onNavigateToClosure,
}: Props) {
  const queryClient = useQueryClient();
  const startKeyRef = useRef<string | undefined>(undefined);
  const finalizeKeyRef = useRef<string | undefined>(undefined);
  const cancelKeyRef = useRef<string | undefined>(undefined);
  const cancelInFlightRef = useRef(false);
  const reviewKeyRef = useRef<string | undefined>(undefined);
  const acceptWithoutEvidenceKeyRef = useRef<string | undefined>(undefined);
  const attemptIdRef = useRef(0);

  const fulfillmentId = deal.fulfillment?.fulfillmentId;
  const hasFulfillment = Boolean(fulfillmentId);

  const fulfillmentQuery = useQuery(
    fulfillmentDetailQueryOptions(legalEntityId, deal.id, hasFulfillment),
  );
  const fulfillment = fulfillmentQuery.data;

  const [notice, setNotice] = useState<string>();
  const [feedbackNotice, setFeedbackNotice] = useState<string>();
  const [uploadState, setUploadState] = useState<UploadState>({
    stage: "idle",
  });
  const [downloadError, setDownloadError] = useState<string>();
  const [downloadingId, setDownloadingId] = useState<string>();
  const [rejectionReason, setRejectionReason] = useState("");
  const [reviewError, setReviewError] = useState<string>();
  const [cancelError, setCancelError] = useState<string>();

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

  function ensureCancelKey() {
    if (!cancelKeyRef.current) {
      cancelKeyRef.current = freshIdempotencyKey();
    }
    return cancelKeyRef.current;
  }

  function clearCancelKey() {
    cancelKeyRef.current = undefined;
  }

  function resetCancelKey() {
    cancelKeyRef.current = freshIdempotencyKey();
  }

  function resetReviewKey() {
    reviewKeyRef.current = freshIdempotencyKey();
  }

  function resetAcceptWithoutEvidenceKey() {
    acceptWithoutEvidenceKeyRef.current = freshIdempotencyKey();
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
      setFeedbackNotice(undefined);
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
      setFeedbackNotice("Kanıt gönderildi — alıcının incelemesi bekleniyor");
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
          isEvidenceUploadExpired(error) || isIntentExpired(previous.intent),
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
      await putEvidenceBytes(
        intent.uploadUrl,
        intent.uploadHeaders,
        file,
        (fraction) => {
          if (attemptIdRef.current !== attemptId) return;
          setUploadState((previous) =>
            previous.stage === "uploading"
              ? { ...previous, progress: fraction }
              : previous,
          );
        },
      );
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
    await createIntentAndUpload(
      file,
      evidenceType,
      mediaType,
      sha256,
      attemptId,
    );
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
    void createIntentAndUpload(
      file,
      evidenceType,
      mediaType,
      sha256,
      attemptId,
    );
  }

  const cancelMutation = useMutation({
    mutationFn: (evidence: EvidenceSubmission) =>
      cancelEvidenceUpload(
        legalEntityId,
        deal.id,
        evidence.id,
        { expectedEvidenceVersion: evidence.version },
        ensureCancelKey(),
      ),
    onSuccess: () => {
      clearCancelKey();
      setCancelError(undefined);
      setUploadState({ stage: "idle" });
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetFulfillmentIdempotencyKey(error, "upload")) {
        resetCancelKey();
        refreshAfterMutation();
      }
      const message = getFulfillmentErrorMessage(error);
      setCancelError(message);
      setUploadState((previous) =>
        previous.stage === "failed"
          ? { ...previous, errorMessage: message }
          : previous,
      );
    },
    onSettled: () => {
      cancelInFlightRef.current = false;
    },
  });

  /**
   * Shared cancel path for in-memory failed upload and server-projected pending.
   * Before an intent exists: local reset only. After intent: cancel API + stable key.
   */
  function handleCancelUpload() {
    if (cancelInFlightRef.current || cancelMutation.isPending) {
      return;
    }
    attemptIdRef.current += 1;

    const localEvidence = uploadState.intent?.evidence;
    const serverCancellable = findCancellablePendingEvidence(
      fulfillment?.currentEvidence,
      fulfillment?.history,
    );
    const cancelTarget =
      serverCancellable ??
      (canCancelEvidenceUpload(localEvidence) ? localEvidence : undefined);

    if (!cancelTarget) {
      clearCancelKey();
      setCancelError(undefined);
      setUploadState({ stage: "idle" });
      return;
    }

    cancelInFlightRef.current = true;
    cancelMutation.mutate(cancelTarget);
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
      setFeedbackNotice(
        "Teslimat tamamlandı — kapanış için Kapanış bölümüne geçin",
      );
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
      setFeedbackNotice(
        "Kanıt reddedildi — satıcının yerine yeni bir yükleme yapması bekleniyor",
      );
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

  const acceptWithoutEvidenceMutation = useMutation({
    mutationFn: () =>
      acceptFulfillmentWithoutEvidence(
        legalEntityId,
        deal.id,
        {
          expectedDealVersion: deal.version,
          expectedFulfillmentVersion: fulfillment!.version,
        },
        acceptWithoutEvidenceKeyRef.current!,
      ),
    onSuccess: () => {
      setReviewError(undefined);
      setFeedbackNotice(
        "Teslimat kanıtsız kabul edildi — kapanış için Kapanış bölümüne geçin",
      );
      refreshAfterMutation();
    },
    onError: (error) => {
      if (
        shouldResetFulfillmentIdempotencyKey(error, "acceptWithoutEvidence")
      ) {
        resetAcceptWithoutEvidenceKey();
        refreshAfterMutation();
      }
      setReviewError(getFulfillmentErrorMessage(error));
    },
  });

  const canStart = !readOnly && deal.availableActions.canStartFulfillment;
  const canUpload =
    !readOnly && (fulfillment?.milestone.availableActions.canUpload ?? false);
  const canAccept = !readOnly && deal.availableActions.canAcceptEvidence;
  const canReject = !readOnly && deal.availableActions.canRejectEvidence;
  const canAcceptWithoutEvidence =
    !readOnly &&
    (deal.availableActions.canAcceptWithoutEvidence === true ||
      fulfillment?.availableActions.canAcceptWithoutEvidence === true);
  const evidencePolicy =
    fulfillment?.evidencePolicy ?? deal.fulfillment?.evidencePolicy;
  const serverCancellablePending = !readOnly
    ? findCancellablePendingEvidence(
        fulfillment?.currentEvidence,
        fulfillment?.history,
      )
    : undefined;
  const turnBanner = deriveTurnBanner({
    status: fulfillment?.status ?? (hasFulfillment ? undefined : "NOT_STARTED"),
    evidencePolicy,
    canStart: Boolean(canStart),
    canUpload: Boolean(canUpload),
    canAccept: Boolean(canAccept),
    canReject: Boolean(canReject),
    canAcceptWithoutEvidence: Boolean(canAcceptWithoutEvidence),
  });
  const chronologicalHistory = fulfillment
    ? sortEvidenceChronologically(fulfillment.history)
    : [];
  const isCancelPending = cancelMutation.isPending;
  if (fulfillmentQuery.isLoading) {
    return (
      <section className="panel" aria-live="polite">
        <h2>Teslimat</h2>
        <p>Yükleniyor…</p>
      </section>
    );
  }

  if (
    fulfillmentQuery.isError &&
    !isFulfillmentNotFound(fulfillmentQuery.error)
  ) {
    return (
      <section className="panel" aria-live="polite">
        <h2>Teslimat</h2>
        <p className="form-alert" role="alert">
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
    <section className={`panel ${styles.fulfillmentPanel}`} aria-live="polite">
      <h2>Teslimat</h2>

      {turnBanner ? (
        <p className={styles.fulfillmentTurnBanner} role="status">
          {turnBanner}
        </p>
      ) : null}

      {feedbackNotice ? (
        <p
          className={`success-notice ${styles.fulfillmentFeedback}`}
          role="status"
        >
          {onNavigateToClosure ? (
            <button
              type="button"
              className={`text-button ${styles.fulfillmentClosureNav}`}
              onClick={onNavigateToClosure}
            >
              {feedbackNotice}
            </button>
          ) : (
            feedbackNotice
          )}
        </p>
      ) : null}

      {readOnly ? (
        <p className="muted-copy">
          Bu aşama salt okunurdur; teslimat geçmişi aşağıda korunur.
        </p>
      ) : null}

      {!fulfillment && canStart && (
        <div className="fulfillment-start">
          <p>Teslimat henüz başlatılmadı.</p>
          {notice && (
            <p className="form-alert" role="alert">
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
            {startMutation.isPending ? "Başlatılıyor…" : "Teslimatı başlat"}
          </button>
        </div>
      )}

      {!fulfillment && !canStart && <p>Teslimat henüz başlatılmadı.</p>}

      {fulfillment && (
        <div className="fulfillment-detail">
          <div className="fulfillment-status">
            <strong>Durum:</strong>{" "}
            <StatusBadge
              domain="fulfillment"
              status={fulfillment.status}
              label={fulfillmentStatusLabel(fulfillment.status)}
            />
          </div>

          {fulfillment.evidencePolicy ? (
            <p className="muted-copy">
              Kanıt politikası:{" "}
              {EVIDENCE_POLICY_LABELS[fulfillment.evidencePolicy]}
            </p>
          ) : null}

          {fulfillment.evidencePolicy === "NOT_REQUIRED" &&
          fulfillment.status === "IN_PROGRESS" &&
          !canAcceptWithoutEvidence ? (
            <p className="muted-copy">
              Bu teslimatta dosya yüklenmez; alıcı kuruluş yöneticisinin
              kanıtsız onayı bekleniyor.
            </p>
          ) : null}

          {canAcceptWithoutEvidence ? (
            <div className="fulfillment-accept-without-evidence">
              {reviewError ? (
                <p className="form-alert" role="alert">
                  {reviewError}
                </p>
              ) : null}
              <button
                type="button"
                className="primary-button"
                onClick={() => {
                  resetAcceptWithoutEvidenceKey();
                  acceptWithoutEvidenceMutation.mutate();
                }}
                disabled={acceptWithoutEvidenceMutation.isPending}
              >
                {acceptWithoutEvidenceMutation.isPending
                  ? "Kabul ediliyor…"
                  : "Teslimatı kanıtsız kabul et"}
              </button>
            </div>
          ) : null}

          {serverCancellablePending && uploadState.stage === "idle" ? (
            <div className={styles.pendingUploadRecovery}>
              {cancelError ? (
                <p className="form-alert" role="alert">
                  {cancelError}
                </p>
              ) : null}
              <p>
                Yarım kalmış bir yükleme var. Vazgeçerek iptal edip yeni bir
                kanıt yükleyebilirsiniz.
              </p>
              <EvidenceSummary submission={serverCancellablePending} />
              <button
                type="button"
                className="secondary-button"
                onClick={handleCancelUpload}
                disabled={isCancelPending}
              >
                {isCancelPending ? "İptal ediliyor…" : "Vazgeç"}
              </button>
            </div>
          ) : null}

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
              <h4>Yeni teslimat kanıtı yükle</h4>
              {uploadState.stage === "idle" && (
                <EvidenceUploadForm onFileSelected={handleFileSelected} />
              )}
              {uploadState.stage === "hashing" && <p>Dosya doğrulanıyor…</p>}
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
                <p className="success-notice" role="status">
                  Kanıt gönderildi — alıcının incelemesi bekleniyor
                </p>
              )}
              {uploadState.stage === "failed" && (
                <div className="upload-failure" role="alert">
                  <p className="form-alert">
                    {uploadState.errorMessage ?? "Yükleme başarısız oldu."}
                  </p>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={handleRetry}
                    disabled={isUploadBusy || isCancelPending}
                  >
                    Yeniden dene
                  </button>
                  <button
                    type="button"
                    className="text-button"
                    onClick={handleCancelUpload}
                    disabled={isUploadBusy || isCancelPending}
                  >
                    {isCancelPending ? "İptal ediliyor…" : "Vazgeç"}
                  </button>
                </div>
              )}
            </div>
          )}

          {currentEvidence && (
            <EvidenceReviewSection
              legalEntityId={legalEntityId}
              dealId={deal.id}
              evidence={currentEvidence}
              readOnly={readOnly}
              canAccept={Boolean(canAccept)}
              isAccepting={acceptMutation.isPending}
              isRejecting={rejectMutation.isPending}
              reviewError={reviewError}
              rejectionReason={rejectionReason}
              onRejectionReasonChange={setRejectionReason}
              onAccept={handleAccept}
              onReject={handleReject}
            />
          )}

          <EvidenceHistory
            legalEntityId={legalEntityId}
            dealId={deal.id}
            history={chronologicalHistory}
            currentEvidence={currentEvidence}
            readOnly={readOnly}
            downloadError={downloadError}
            downloadingId={downloadingId}
            onDownload={handleDownload}
          />
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
  const [evidenceType, setEvidenceType] =
    useState<EvidenceType>("DELIVERY_NOTE");

  return (
    <div className="evidence-upload-form">
      <label htmlFor="evidence-type">Kanıt türü</label>
      <select
        id="evidence-type"
        value={evidenceType}
        onChange={(event) =>
          setEvidenceType(event.target.value as EvidenceType)
        }
      >
        <option value="DELIVERY_NOTE">
          {evidenceTypeLabel("DELIVERY_NOTE")}
        </option>
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
        PDF, DOCX, JPEG, PNG veya MP4; boyut sınırı sunucu tarafından
        belirlenir.
      </p>
    </div>
  );
}

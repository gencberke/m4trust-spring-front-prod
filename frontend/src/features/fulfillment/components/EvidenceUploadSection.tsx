import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRef, useState, type ChangeEvent } from "react";

import type { DealDetail } from "../../deals";
import { dealDetailQueryKey } from "../../deals";
import {
  cancelEvidenceUpload,
  createEvidenceUploadIntent,
  finalizeEvidenceUpload,
  type EvidenceMediaType,
  type EvidenceSubmission,
  type EvidenceType,
  type EvidenceUploadIntent,
  type FulfillmentDetail,
} from "../fulfillmentApi";
import {
  getFulfillmentErrorMessage,
  isEvidenceUploadExpired,
  shouldResetFulfillmentIdempotencyKey,
} from "../fulfillmentErrors";
import { fulfillmentDetailQueryKey } from "../fulfillmentQueries";
import {
  ACCEPTED_EVIDENCE_FILE_INPUT_ACCEPT,
  computeSha256Hex,
  DirectUploadError,
  inferEvidenceMediaType,
  isLikelyExpiredUploadStatus,
  putEvidenceBytes,
} from "../evidenceUpload";
import { EvidenceSummary } from "./EvidenceSummary";
import {
  canCancelEvidenceUpload,
  evidenceTypeLabel,
  findCancellablePendingEvidence,
} from "./fulfillmentPresentation";
import styles from "../Fulfillment.module.css";

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
  if (!intent) return false;
  return new Date(intent.expiresAt).getTime() <= Date.now();
}

interface EvidenceUploadSectionProps {
  legalEntityId: string;
  deal: DealDetail;
  fulfillment: FulfillmentDetail;
  canUpload: boolean;
  readOnly: boolean;
  onFeedbackNotice: (message: string) => void;
}

export function EvidenceUploadSection({
  legalEntityId,
  deal,
  fulfillment,
  canUpload,
  readOnly,
  onFeedbackNotice,
}: EvidenceUploadSectionProps) {
  const queryClient = useQueryClient();
  const finalizeKeyRef = useRef<string | undefined>(undefined);
  const cancelKeyRef = useRef<string | undefined>(undefined);
  const cancelInFlightRef = useRef(false);
  const attemptIdRef = useRef(0);

  const [uploadState, setUploadState] = useState<UploadState>({
    stage: "idle",
  });
  const [cancelError, setCancelError] = useState<string>();

  function refreshAfterMutation() {
    void queryClient.invalidateQueries({
      queryKey: dealDetailQueryKey(legalEntityId, deal.id),
    });
    void queryClient.invalidateQueries({
      queryKey: fulfillmentDetailQueryKey(legalEntityId, deal.id),
    });
  }

  function resetFinalizeKey() {
    finalizeKeyRef.current = crypto.randomUUID();
  }

  function ensureCancelKey() {
    if (!cancelKeyRef.current) {
      cancelKeyRef.current = crypto.randomUUID();
    }
    return cancelKeyRef.current;
  }

  function clearCancelKey() {
    cancelKeyRef.current = undefined;
  }

  function resetCancelKey() {
    cancelKeyRef.current = crypto.randomUUID();
  }

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
      onFeedbackNotice("Kanıt gönderildi — alıcının incelemesi bekleniyor");
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

  const isCancelPending = cancelMutation.isPending;
  const isUploadBusy = BUSY_STAGES.includes(uploadState.stage);

  const serverCancellablePending = !readOnly
    ? findCancellablePendingEvidence(
        fulfillment.currentEvidence,
        fulfillment.history,
      )
    : undefined;

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

  function handleCancelUpload() {
    if (cancelInFlightRef.current || cancelMutation.isPending) {
      return;
    }
    attemptIdRef.current += 1;

    const localEvidence = uploadState.intent?.evidence;
    const serverCancellable = findCancellablePendingEvidence(
      fulfillment.currentEvidence,
      fulfillment.history,
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

  return (
    <>
      {serverCancellablePending && uploadState.stage === "idle" ? (
        <div className={styles.pendingUploadRecovery}>
          {cancelError ? (
            <p className="form-alert" role="alert">
              {cancelError}
            </p>
          ) : null}
          <p>
            Yarım kalmış bir yükleme var. Vazgeçerek iptal edip yeni bir kanıt
            yükleyebilirsiniz.
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
    </>
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

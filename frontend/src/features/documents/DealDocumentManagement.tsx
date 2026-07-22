import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState, type ChangeEvent } from "react";

import type { DealDetail } from "../deals/dealApi";
import { dealDetailQueryKey } from "../deals/dealQueries";
import {
  createDealDocumentUploadIntent,
  createDocumentDownloadLink,
  finalizeDocumentUpload,
  type AvailableDealDocument,
  type DocumentMediaType,
  type DocumentStatus,
  type DocumentUploadIntent,
} from "./documentApi";
import { getDocumentErrorMessage, isDocumentUploadExpired } from "./documentErrors";
import {
  dealDocumentHistoryQueryKey,
  dealDocumentHistoryQueryOptions,
} from "./documentQueries";
import {
  ACCEPTED_DOCUMENT_FILE_INPUT_ACCEPT,
  computeSha256Hex,
  DirectUploadError,
  inferDocumentMediaType,
  isLikelyExpiredUploadStatus,
  putDocumentBytes,
} from "./documentUpload";

const DOCUMENT_DATE_FORMATTER = new Intl.DateTimeFormat("tr-TR", {
  dateStyle: "long",
  timeStyle: "short",
});

function formatDocumentDate(value: string): string {
  return DOCUMENT_DATE_FORMATTER.format(new Date(value));
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

const DOCUMENT_STATUS_LABELS: Record<DocumentStatus, string> = {
  PENDING_UPLOAD: "Yükleme bekleniyor",
  AVAILABLE: "Kullanılabilir",
  SUPERSEDED: "Yerini aldı",
};

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
  mediaType?: DocumentMediaType;
  sha256?: string;
  intent?: DocumentUploadIntent;
  progress?: number;
  failedStage?: FailedStage;
  expired?: boolean;
  errorMessage?: string;
  result?: AvailableDealDocument;
}

const BUSY_STAGES: readonly UploadStage[] = [
  "hashing",
  "creating-intent",
  "uploading",
  "finalizing",
];

function isIntentExpired(intent: DocumentUploadIntent | undefined): boolean {
  if (!intent) {
    return false;
  }
  return new Date(intent.expiresAt).getTime() <= Date.now();
}

interface DealDocumentManagementProps {
  deal: DealDetail;
  legalEntityId: string;
}

export function DealDocumentManagement({
  deal,
  legalEntityId,
}: DealDocumentManagementProps) {
  const queryClient = useQueryClient();
  const attemptIdRef = useRef(0);
  const idempotencyKeyRef = useRef<string | undefined>(undefined);
  const [uploadState, setUploadState] = useState<UploadState>({ stage: "idle" });
  const [downloadError, setDownloadError] = useState<string>();
  const [downloadingDocumentId, setDownloadingDocumentId] = useState<string>();

  const historyQuery = useQuery(
    dealDocumentHistoryQueryOptions(legalEntityId, deal.id),
  );

  const currentDocument = deal.currentDocument;
  const canUpload = deal.availableActions.canCreateDocumentUploadIntent;
  const isBusy = BUSY_STAGES.includes(uploadState.stage);

  function refreshAfterFinalize() {
    void queryClient.invalidateQueries({
      queryKey: dealDetailQueryKey(legalEntityId, deal.id),
    });
    void queryClient.invalidateQueries({
      queryKey: dealDocumentHistoryQueryKey(legalEntityId, deal.id),
    });
  }

  async function performFinalize(
    intent: DocumentUploadIntent,
    file: File,
    mediaType: DocumentMediaType,
    sha256: string,
    attemptId: number,
  ) {
    setUploadState({ stage: "finalizing", file, mediaType, sha256, intent });
    try {
      const document = await finalizeDocumentUpload(
        legalEntityId,
        intent.document.id,
        { sizeBytes: file.size, sha256 },
        idempotencyKeyRef.current!,
      );
      if (attemptIdRef.current !== attemptId) return;
      setUploadState({ stage: "done", result: document });
      refreshAfterFinalize();
    } catch (error) {
      if (attemptIdRef.current !== attemptId) return;
      setUploadState({
        stage: "failed",
        file,
        mediaType,
        sha256,
        intent,
        failedStage: "finalize",
        expired: isDocumentUploadExpired(error) || isIntentExpired(intent),
        errorMessage: getDocumentErrorMessage(error),
      });
    }
  }

  async function performUpload(
    intent: DocumentUploadIntent,
    file: File,
    mediaType: DocumentMediaType,
    sha256: string,
    attemptId: number,
  ) {
    setUploadState({ stage: "uploading", file, mediaType, sha256, intent, progress: 0 });
    try {
      await putDocumentBytes(intent.uploadUrl, intent.uploadHeaders, file, (fraction) => {
        if (attemptIdRef.current !== attemptId) return;
        setUploadState((previous) =>
          previous.stage === "uploading" ? { ...previous, progress: fraction } : previous,
        );
      });
    } catch (error) {
      if (attemptIdRef.current !== attemptId) return;
      const status = error instanceof DirectUploadError ? error.status : 0;
      setUploadState({
        stage: "failed",
        file,
        mediaType,
        sha256,
        intent,
        failedStage: "upload",
        expired: isLikelyExpiredUploadStatus(status) || isIntentExpired(intent),
        errorMessage:
          "Dosya depolama alanına yüklenemedi. Bağlantınızı kontrol edip yeniden deneyin.",
      });
      return;
    }
    if (attemptIdRef.current !== attemptId) return;
    await performFinalize(intent, file, mediaType, sha256, attemptId);
  }

  async function createIntentAndUpload(
    file: File,
    mediaType: DocumentMediaType,
    sha256: string,
    attemptId: number,
  ) {
    setUploadState({ stage: "creating-intent", file, mediaType, sha256 });
    let intent: DocumentUploadIntent;
    try {
      intent = await createDealDocumentUploadIntent(legalEntityId, deal.id, {
        fileName: file.name,
        mediaType,
        sizeBytes: file.size,
        sha256,
      });
    } catch (error) {
      if (attemptIdRef.current !== attemptId) return;
      setUploadState({
        stage: "failed",
        file,
        mediaType,
        sha256,
        failedStage: "intent",
        expired: false,
        errorMessage: getDocumentErrorMessage(error),
      });
      return;
    }
    if (attemptIdRef.current !== attemptId) return;
    // A newly created intent always represents a brand-new upload attempt, so
    // it gets its own Idempotency-Key. Retries below reuse this same key.
    idempotencyKeyRef.current = crypto.randomUUID();
    await performUpload(intent, file, mediaType, sha256, attemptId);
  }

  async function startUpload(file: File) {
    const attemptId = ++attemptIdRef.current;
    idempotencyKeyRef.current = undefined;
    const mediaType = inferDocumentMediaType(file);
    if (!mediaType) {
      setUploadState({
        stage: "failed",
        file,
        failedStage: "hashing",
        expired: false,
        errorMessage: "Yalnızca PDF veya DOCX dosyaları yüklenebilir.",
      });
      return;
    }
    setUploadState({ stage: "hashing", file, mediaType });
    let sha256: string;
    try {
      sha256 = await computeSha256Hex(file);
    } catch {
      if (attemptIdRef.current !== attemptId) return;
      setUploadState({
        stage: "failed",
        file,
        mediaType,
        failedStage: "hashing",
        expired: false,
        errorMessage: "Dosya bütünlük kontrolü hesaplanamadı.",
      });
      return;
    }
    if (attemptIdRef.current !== attemptId) return;
    await createIntentAndUpload(file, mediaType, sha256, attemptId);
  }

  function handleFileInputChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    void startUpload(file);
  }

  function handleRetry() {
    if (uploadState.stage !== "failed" || !uploadState.file) return;
    const attemptId = attemptIdRef.current;
    const { file, mediaType, sha256, intent, failedStage, expired } = uploadState;

    if (failedStage === "hashing" || failedStage === "intent" || !mediaType || !sha256) {
      void startUpload(file);
      return;
    }
    if (expired || isIntentExpired(intent)) {
      void createIntentAndUpload(file, mediaType, sha256, attemptId);
      return;
    }
    if (failedStage === "upload" && intent) {
      void performUpload(intent, file, mediaType, sha256, attemptId);
      return;
    }
    if (failedStage === "finalize" && intent) {
      void performFinalize(intent, file, mediaType, sha256, attemptId);
    }
  }

  async function handleDownload(documentId: string) {
    setDownloadError(undefined);
    setDownloadingDocumentId(documentId);
    try {
      const link = await createDocumentDownloadLink(legalEntityId, documentId);
      window.open(link.downloadUrl, "_blank", "noopener,noreferrer");
    } catch (error) {
      setDownloadError(getDocumentErrorMessage(error));
    } finally {
      setDownloadingDocumentId(undefined);
    }
  }

  return (
    <section className="workspace-panel document-management-panel">
      <div className="panel-heading">
        <span className="section-kicker">Belgeler</span>
        <h2>Anlaşma belgesi</h2>
        <p>Güncel sözleşme belgesi ve geçmiş sürümler burada listelenir.</p>
      </div>

      <div className="document-section">
        <h3>Güncel belge</h3>
        {currentDocument ? (
          <div className="document-current-card">
            <div>
              <strong>{currentDocument.fileName}</strong>
              <span>
                {formatBytes(currentDocument.verifiedSizeBytes)} ·{" "}
                {formatDocumentDate(currentDocument.availableAt)}
              </span>
            </div>
            {currentDocument.availableActions.canDownload ? (
              <button
                className="secondary-button"
                type="button"
                onClick={() => void handleDownload(currentDocument.id)}
                disabled={downloadingDocumentId === currentDocument.id}
              >
                {downloadingDocumentId === currentDocument.id
                  ? "Bağlantı hazırlanıyor…"
                  : "İndir"}
              </button>
            ) : null}
          </div>
        ) : (
          <p className="muted-copy">Bu anlaşma için henüz onaylanmış bir belge yok.</p>
        )}
      </div>

      <div className="document-section">
        {canUpload ? (
          <>
            <h3>Belge yükle</h3>
            <p className="muted-copy">
              Kabul edilen biçimler: PDF, DOCX. Dosya yüklenmeden önce tarayıcıda
              özetlenir (SHA-256) ve sunucu tarafında doğrulanır.
            </p>

            <input
              id="document-file-input"
              type="file"
              accept={ACCEPTED_DOCUMENT_FILE_INPUT_ACCEPT}
              onChange={handleFileInputChange}
              disabled={isBusy}
            />

            {uploadState.stage === "hashing" ? (
              <div className="inline-state" role="status">
                <span className="loading-line" aria-hidden="true" />
                Dosya özeti hesaplanıyor…
              </div>
            ) : null}
            {uploadState.stage === "creating-intent" ? (
              <div className="inline-state" role="status">
                <span className="loading-line" aria-hidden="true" />
                Yükleme başlatılıyor…
              </div>
            ) : null}
            {uploadState.stage === "uploading" ? (
              <div className="document-upload-progress" role="status">
                <div className="document-upload-progress-track">
                  <div
                    className="document-upload-progress-fill"
                    style={{ width: `${Math.round((uploadState.progress ?? 0) * 100)}%` }}
                  />
                </div>
                <span>{Math.round((uploadState.progress ?? 0) * 100)}% yüklendi</span>
              </div>
            ) : null}
            {uploadState.stage === "finalizing" ? (
              <div className="inline-state" role="status">
                <span className="loading-line" aria-hidden="true" />
                Yükleme tamamlanıyor…
              </div>
            ) : null}
            {uploadState.stage === "done" && uploadState.result ? (
              <p className="success-notice">
                {uploadState.result.fileName} başarıyla yüklendi ve onaylandı.
              </p>
            ) : null}
            {uploadState.stage === "failed" ? (
              <div className="form-alert panel-alert" role="alert">
                <p>{uploadState.errorMessage}</p>
                {uploadState.expired ? (
                  <p className="field-hint">
                    Yükleme bağlantısının süresi doldu. Aynı dosya için yeni bir
                    yükleme başlatabilirsiniz.
                  </p>
                ) : null}
                <button className="secondary-button" type="button" onClick={handleRetry}>
                  {uploadState.expired ? "Yeni yükleme başlat" : "Yeniden dene"}
                </button>
              </div>
            ) : null}
          </>
        ) : (
          <p className="muted-copy">
            Belge yükleme yalnızca bu anlaşmayı başlatan taraf tarafından yapılabilir.
          </p>
        )}
      </div>

      <div className="document-section">
        <div className="invitation-list-heading">
          <h3>Belge geçmişi</h3>
          {historyQuery.data ? <span>{historyQuery.data.items.length} kayıt</span> : null}
        </div>

        {historyQuery.isPending ? (
          <div className="inline-state" role="status">
            <span className="loading-line" aria-hidden="true" />
            Belge geçmişi yükleniyor…
          </div>
        ) : null}
        {historyQuery.isError ? (
          <div className="form-alert panel-alert" role="alert">
            <p>{getDocumentErrorMessage(historyQuery.error)}</p>
            <button
              className="secondary-button"
              type="button"
              onClick={() => void historyQuery.refetch()}
              disabled={historyQuery.isFetching}
            >
              {historyQuery.isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
            </button>
          </div>
        ) : null}
        {historyQuery.data?.items.length === 0 ? (
          <p className="muted-copy invitation-empty">Henüz yüklenmiş bir belge yok.</p>
        ) : null}
        {historyQuery.data?.items.length ? (
          <ul className="invitation-list">
            {historyQuery.data.items.map((item) => (
              <li key={item.id}>
                <div>
                  <strong>{item.fileName}</strong>
                  <span>
                    {DOCUMENT_STATUS_LABELS[item.status]} ·{" "}
                    {item.status === "PENDING_UPLOAD"
                      ? formatBytes(item.clientSizeBytes)
                      : formatBytes(item.verifiedSizeBytes)}{" "}
                    · {formatDocumentDate(item.createdAt)}
                  </span>
                </div>
                {item.availableActions.canDownload ? (
                  <button
                    className="secondary-button"
                    type="button"
                    onClick={() => void handleDownload(item.id)}
                    disabled={downloadingDocumentId === item.id}
                  >
                    {downloadingDocumentId === item.id
                      ? "Bağlantı hazırlanıyor…"
                      : "İndir"}
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        ) : null}
        {downloadError ? (
          <p className="form-alert panel-alert" role="alert">
            {downloadError}
          </p>
        ) : null}
      </div>
    </section>
  );
}

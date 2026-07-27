import type { EvidencePolicy, EvidenceSubmission } from "../fulfillmentApi";

const FULFILLMENT_STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: "Başlatılmadı",
  IN_PROGRESS: "Devam ediyor",
  EVIDENCE_REQUIRED: "Teslimat kanıtı bekleniyor",
  REVIEW_REQUIRED: "İnceleme bekleniyor",
  COMPLETED: "Tamamlandı",
  CANCELLED: "İptal edildi",
};

const EVIDENCE_STATUS_LABELS: Record<string, string> = {
  PENDING_UPLOAD: "Yüklenecek",
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

export const EVIDENCE_POLICY_LABELS: Record<EvidencePolicy, string> = {
  REQUIRED: "Kanıt gerekli",
  NOT_REQUIRED: "Kanıt gerekli değil",
};

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB"];
  let value = bytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unitIndex]}`;
}

export function fulfillmentStatusLabel(status: string): string {
  return FULFILLMENT_STATUS_LABELS[status] ?? status;
}

export function evidenceStatusLabel(submission: EvidenceSubmission): string {
  if (submission.status === "PENDING_UPLOAD" && submission.cancelledAt) {
    return "Yükleme iptal edildi";
  }
  return EVIDENCE_STATUS_LABELS[submission.status] ?? submission.status;
}

export function evidenceTypeLabel(type: string): string {
  return EVIDENCE_TYPE_LABELS[type] ?? type;
}

export function isCancelledPending(submission: EvidenceSubmission): boolean {
  return (
    submission.status === "PENDING_UPLOAD" && Boolean(submission.cancelledAt)
  );
}

export function canCancelEvidenceUpload(
  submission: EvidenceSubmission | undefined,
): boolean {
  return submission?.availableActions.canCancelUpload === true;
}

export function findCancellablePendingEvidence(
  currentEvidence: EvidenceSubmission | null | undefined,
  history: readonly EvidenceSubmission[] | undefined,
): EvidenceSubmission | undefined {
  if (canCancelEvidenceUpload(currentEvidence ?? undefined))
    return currentEvidence!;
  return history?.find((submission) => canCancelEvidenceUpload(submission));
}

export function isAnalysisEligibleEvidence(
  submission: EvidenceSubmission,
): boolean {
  return (
    (submission.evidenceType === "VIDEO" &&
      submission.mediaType === "video/mp4") ||
    (submission.evidenceType === "PHOTO" &&
      (submission.mediaType === "image/jpeg" ||
        submission.mediaType === "image/png"))
  );
}

export function evidenceEventAt(submission: EvidenceSubmission): string {
  if (submission.status === "PENDING_UPLOAD" && submission.cancelledAt)
    return submission.cancelledAt;
  if (submission.status === "ACCEPTED" && submission.acceptedAt)
    return submission.acceptedAt;
  if (submission.status === "REJECTED" && submission.rejectedAt)
    return submission.rejectedAt;
  if (submission.status === "SUBMITTED" && submission.submittedAt)
    return submission.submittedAt;
  return submission.createdAt;
}

export function sortEvidenceChronologically(
  history: readonly EvidenceSubmission[],
): EvidenceSubmission[] {
  return [...history].sort((left, right) => {
    const delta =
      new Date(evidenceEventAt(left)).getTime() -
      new Date(evidenceEventAt(right)).getTime();
    return delta !== 0 ? delta : left.id.localeCompare(right.id);
  });
}

export function deriveTurnBanner(input: {
  status: string | undefined;
  evidencePolicy: EvidencePolicy | undefined;
  canStart: boolean;
  canUpload: boolean;
  canAccept: boolean;
  canReject: boolean;
  canAcceptWithoutEvidence: boolean;
}): string | undefined {
  const {
    status,
    evidencePolicy,
    canStart,
    canUpload,
    canAccept,
    canReject,
    canAcceptWithoutEvidence,
  } = input;
  if (!status || status === "COMPLETED" || status === "CANCELLED")
    return undefined;
  if (canStart) return "Sıra sizde: teslimatı başlatın";
  if (canAcceptWithoutEvidence)
    return "Sıra sizde: teslimatı kanıtsız kabul edin";
  if (canUpload) return "Sıra sizde: teslimat kanıtı yükleyin";
  if (canAccept || canReject) return "Sıra sizde: kanıtı inceleyin";
  if (status === "REVIEW_REQUIRED")
    return "Sıra karşı tarafta: alıcı kanıtı inceliyor";
  if (evidencePolicy === "NOT_REQUIRED" && status === "IN_PROGRESS")
    return "Alıcı onayını bekliyor: teslimat kanıtsız kabul edilecek";
  if (status === "EVIDENCE_REQUIRED" || status === "IN_PROGRESS")
    return "Sıra karşı tarafta: satıcı kanıt yüklüyor";
  if (status === "NOT_STARTED")
    return "Sıra karşı tarafta: satıcı teslimatı başlatacak";
  return undefined;
}

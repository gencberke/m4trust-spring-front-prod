import type { components } from "../../generated/core-api";

type AdvisoryOutcome = components["schemas"]["VideoAnalysisAdvisoryOutcome"];
type ObservationType = components["schemas"]["VideoAnalysisObservationType"];
type AnomalySeverity = components["schemas"]["VideoAnalysisAnomalySeverity"];
type WarningSeverity = components["schemas"]["VideoAnalysisWarningSeverity"];

export const ADVISORY_OUTCOME_LABELS: Record<AdvisoryOutcome, string> = {
  NO_ISSUE_DETECTED: "Belirgin sorun tespit edilmedi",
  REVIEW_SUGGESTED: "İnceleme önerilir",
  INSUFFICIENT_EVIDENCE: "Yetersiz kanıt",
  UNKNOWN: "Belirsiz danışmanlık sonucu",
};

export const OBSERVATION_TYPE_LABELS: Record<ObservationType, string> = {
  OBJECT_COUNT: "Nesne sayımı",
  OBJECT_PRESENCE: "Nesne varlığı",
  SEQUENCE: "Sıra",
  VISIBILITY: "Görünürlük",
  OTHER: "Diğer",
  UNKNOWN: "Bilinmeyen gözlem",
};

export const ANOMALY_SEVERITY_LABELS: Record<AnomalySeverity, string> = {
  LOW: "Düşük",
  MEDIUM: "Orta",
  HIGH: "Yüksek",
  UNKNOWN: "Bilinmeyen önem",
};

export const WARNING_SEVERITY_LABELS: Record<WarningSeverity, string> = {
  INFO: "Bilgi",
  WARNING: "Uyarı",
};

export function labelAdvisoryOutcome(value: string): string {
  return ADVISORY_OUTCOME_LABELS[value as AdvisoryOutcome] ?? "Danışmanlık sonucu";
}

export function labelObservationType(value: string): string {
  return OBSERVATION_TYPE_LABELS[value as ObservationType] ?? "Gözlem";
}

export function labelAnomalySeverity(value: string): string {
  return ANOMALY_SEVERITY_LABELS[value as AnomalySeverity] ?? "Önem";
}

export function labelReviewReason(code: string): string {
  return REVIEW_REASON_LABELS[code] ?? "Ek inceleme nedeni";
}

export function labelFailureCode(code: string): string {
  return FAILURE_CODE_LABELS[code] ?? "Teknik analiz hatası";
}

export function labelWarningCode(code: string): string {
  return WARNING_CODE_LABELS[code] ?? "";
}

export function presentWarningMessage(code: string, message: string): string {
  return WARNING_CODE_LABELS[code] ? message : "Ek danışmanlık uyarısı";
}

export function shouldShowWarningCode(code: string): boolean {
  return code in WARNING_CODE_LABELS;
}

const REVIEW_REASON_LABELS: Record<string, string> = {
  VIDEO_VISIBILITY_GAP: "Video görünürlük boşluğu",
};

const WARNING_CODE_LABELS: Record<string, string> = {
  VIDEO_VISIBILITY_GAP: "Video görünürlük boşluğu",
  LOW_CONFIDENCE_RESULT: "Düşük güven uyarısı",
};

const FAILURE_CODE_LABELS: Record<string, string> = {
  OBJECT_STORAGE_TEMPORARILY_UNAVAILABLE: "Geçici depolama erişim sorunu",
  CONTENT_HASH_MISMATCH: "Dosya bütünlük uyuşmazlığı",
  INVALID_EXPECTED_OBJECT: "Geçersiz evidence girdisi",
  INVALID_DEADLINE: "İşlem süresi doldu",
  UNSUPPORTED_SCHEMA_VERSION: "Desteklenmeyen şema sürümü",
};

export function formatDurationMs(durationMs: number): string {
  const totalSeconds = Math.max(0, Math.round(durationMs / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export function formatTimeRange(startMs: number, endMs: number): string {
  return `${formatDurationMs(startMs)} – ${formatDurationMs(endMs)}`;
}

export const PERCENT_FORMATTER = new Intl.NumberFormat("tr-TR", {
  style: "percent",
  maximumFractionDigits: 0,
});

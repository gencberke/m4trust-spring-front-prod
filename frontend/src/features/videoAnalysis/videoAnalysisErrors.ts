import { ApiError, type ApiErrorCode } from "../../app/coreApi";

export function getVideoAnalysisReadErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Video analizi bilgisine ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  }
  const code: ApiErrorCode | undefined = error.code;
  switch (code) {
    case "EVIDENCE_NOT_FOUND":
    case "DEAL_NOT_FOUND":
    case "FULFILLMENT_NOT_FOUND":
      return "Bu evidence için video analizi görüntülenemiyor.";
    case "LEGAL_ENTITY_ACCESS_DENIED":
    case "LEGAL_ENTITY_NOT_FOUND":
      return "Aktif legal entity bağlamı doğrulanamadı.";
    default:
      return "Video analizi bilgisi şu anda alınamıyor. Lütfen yeniden deneyin.";
  }
}

export function getVideoAnalysisRequestErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Video analizi talebi gönderilemedi. Bağlantınızı kontrol edip yeniden deneyin.";
  }
  const code: ApiErrorCode | undefined = error.code;
  switch (code) {
    case "VIDEO_ANALYSIS_ACTIVE_JOB_EXISTS":
      return "Bu evidence için bir video analizi zaten sırada. Güncel durum yeniden yükleniyor.";
    case "VIDEO_ANALYSIS_ALREADY_COMPLETED":
      return "Bu evidence için başarılı bir video analizi sonucu zaten mevcut.";
    case "VIDEO_ANALYSIS_EVIDENCE_NOT_ELIGIBLE":
      return "Evidence şu anda video analizi için uygun değil.";
    case "EVIDENCE_STALE_VERSION":
      return "Evidence sürümü değişti. Güncel durumu yenileyip tekrar deneyin.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Talep güvenli biçimde yinelenemedi. Lütfen yeniden deneyin.";
    case "VIDEO_ANALYSIS_REQUEST_FORBIDDEN":
      return "Bu legal entity video analizi talebi oluşturamaz.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    default:
      return "Video analizi talebi kabul edilemedi. Güncel evidence durumunu kontrol edip yeniden deneyin.";
  }
}

export function shouldRefetchAfterVideoAnalysisRequestError(error: unknown): boolean {
  return error instanceof ApiError && (
    error.code === "VIDEO_ANALYSIS_ACTIVE_JOB_EXISTS"
    || error.code === "VIDEO_ANALYSIS_ALREADY_COMPLETED"
    || error.code === "VIDEO_ANALYSIS_EVIDENCE_NOT_ELIGIBLE"
    || error.code === "EVIDENCE_STALE_VERSION"
  );
}

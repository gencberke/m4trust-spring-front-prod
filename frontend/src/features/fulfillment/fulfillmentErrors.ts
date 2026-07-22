import { ApiError, type ApiErrorCode } from "../../app/coreApi";

const FULFILLMENT_NOT_FOUND_CODES: ReadonlySet<ApiErrorCode> = new Set([
  "FULFILLMENT_NOT_FOUND",
  "EVIDENCE_NOT_FOUND",
  "DEAL_NOT_FOUND",
  "LEGAL_ENTITY_NOT_FOUND",
]);

export function isFulfillmentNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code != null
    && FULFILLMENT_NOT_FOUND_CODES.has(error.code);
}

export function isEvidenceUploadExpired(error: unknown): boolean {
  return error instanceof ApiError && error.code === "EVIDENCE_UPLOAD_EXPIRED";
}

export function getFulfillmentErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  }
  const code: ApiErrorCode | undefined = error.code;
  switch (code) {
    case "VALIDATION_FAILED":
      return "İstek sunucu tarafından reddedildi. Lütfen alanları ve güncel durumu kontrol edin.";
    case "MALFORMED_REQUEST":
      return "İstek biçimi geçersiz. Lütfen sayfayı yenileyip tekrar deneyin.";
    case "DEAL_STALE_VERSION":
      return "Deal başka bir işlemle değişti. Güncel veri yenilendi; lütfen tekrar deneyin.";
    case "DEAL_STATE_CONFLICT":
      return "Deal artık bu işlem için uygun durumda değil. Güncel veri yenilendi.";
    case "FULFILLMENT_START_FORBIDDEN":
      return "Bu işlemi yalnızca satıcı (seller) tarafı yapabilir.";
    case "FULFILLMENT_ALREADY_EXISTS":
      return "Bu Deal için fulfillment zaten başlatılmış. Güncel durum yenilendi.";
    case "FULFILLMENT_STATE_CONFLICT":
      return "Fulfillment artık bu işlem için uygun durumda değil.";
    case "FULFILLMENT_NOT_FOUND":
      return "Bu Deal için fulfillment kaydı bulunamadı.";
    case "DEAL_NOT_FOUND":
    case "LEGAL_ENTITY_NOT_FOUND":
      return "İstenen kayıt bulunamadı veya bu legal entity için görünür değil.";
    case "EVIDENCE_NOT_FOUND":
      return "İstenen evidence bulunamadı veya bu Deal için görünür değil.";
    case "FULFILLMENT_COMPLETED":
      return "Fulfillment zaten tamamlanmış.";
    case "EVIDENCE_UPLOAD_FORBIDDEN":
      return "Evidence yüklemeye yalnızca satıcı (seller) tarafı yetkilidir.";
    case "EVIDENCE_ALREADY_SUBMITTED":
      return "Bu milestone için zaten inceleme bekleyen evidence var.";
    case "EVIDENCE_UPLOAD_EXPIRED":
      return "Evidence yükleme süresi doldu. Yeni bir yükleme başlatın.";
    case "EVIDENCE_UPLOAD_STATE_CONFLICT":
    case "EVIDENCE_MILESTONE_CONFLICT":
      return "Evidence artık bu milestone için tamamlanamıyor.";
    case "EVIDENCE_VERIFICATION_FAILED":
      return "Yüklenen dosyanın boyut, checksum veya media type doğrulaması başarısız.";
    case "EVIDENCE_REVIEW_FORBIDDEN":
      return "Evidence onaylama/reddetme yalnızca alıcı (buyer) ADMIN kullanıcısına açıktır.";
    case "EVIDENCE_STALE_VERSION":
      return "Evidence başka bir işlemle değişti. Güncel durum yenilendi; lütfen tekrar deneyin.";
    case "EVIDENCE_STATE_CONFLICT":
      return "Evidence artık incelenmek için uygun durumda değil.";
    case "EVIDENCE_DOWNLOAD_NOT_AVAILABLE":
      return "Bu evidence için indirme bağlantısı şu anda kullanılamıyor.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Bu istek anahtarı farklı bir içerikle kullanılmış. Güncel durumu gözden geçirip yeniden deneyin.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    case "LEGAL_ENTITY_ACCESS_DENIED":
      return "Aktif legal entity bağlamı doğrulanamadı. Lütfen yeniden seçim yapın.";
    default:
      return "İşlem tamamlanamadı. Lütfen güncel Deal ve fulfillment durumunu kontrol edin.";
  }
}

export function shouldRefetchAfterStartError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "DEAL_STALE_VERSION" ||
      error.code === "DEAL_STATE_CONFLICT" ||
      error.code === "FULFILLMENT_ALREADY_EXISTS" ||
      error.code === "FULFILLMENT_STATE_CONFLICT")
  );
}

export function shouldRefetchAfterUploadError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "DEAL_STALE_VERSION" ||
      error.code === "DEAL_STATE_CONFLICT" ||
      error.code === "EVIDENCE_ALREADY_SUBMITTED" ||
      error.code === "EVIDENCE_UPLOAD_EXPIRED" ||
      error.code === "EVIDENCE_UPLOAD_STATE_CONFLICT" ||
      error.code === "EVIDENCE_MILESTONE_CONFLICT" ||
      error.code === "EVIDENCE_VERIFICATION_FAILED" ||
      error.code === "FULFILLMENT_STATE_CONFLICT")
  );
}

export function shouldRefetchAfterReviewError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "DEAL_STALE_VERSION" ||
      error.code === "EVIDENCE_STALE_VERSION" ||
      error.code === "DEAL_STATE_CONFLICT" ||
      error.code === "EVIDENCE_STATE_CONFLICT" ||
      error.code === "FULFILLMENT_COMPLETED")
  );
}

export function shouldResetFulfillmentIdempotencyKey(
  error: unknown,
  kind: "start" | "upload" | "review",
): boolean {
  if (!(error instanceof ApiError)) return false;
  if (error.code === "IDEMPOTENCY_KEY_REUSED") return true;
  if (kind === "start") return shouldRefetchAfterStartError(error);
  if (kind === "upload") return shouldRefetchAfterUploadError(error);
  return shouldRefetchAfterReviewError(error);
}

import { ApiError } from "../../app/coreApi";

export function isFundingPlanNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code === "FUNDING_PLAN_NOT_FOUND";
}

export function getFundingErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError))
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  switch (error.code) {
    case "VALIDATION_FAILED":
      return "İstek sunucu tarafından reddedildi. Lütfen güncel Deal ve funding durumunu kontrol edin.";
    case "DEAL_STALE_VERSION":
      return "Deal başka bir işlemle değişti. Güncel Deal verisi yenilendi; lütfen tekrar deneyin.";
    case "DEAL_STATE_CONFLICT":
      return "Deal artık ACTIVE durumunda değil; bu funding işlemi kapalı. Güncel Deal verisi yenilendi.";
    case "FUNDING_PLAN_ALREADY_EXISTS":
      return "Bu Deal için funding planı zaten oluşturulmuş. Güncel plan yenilendi.";
    case "FUNDING_PLAN_NOT_FOUND":
      return "Bu Deal için henüz bir funding planı oluşturulmadı.";
    case "FUNDING_UNIT_NOT_FOUND":
      return "Funding unit bulunamadı veya artık erişilebilir değil.";
    case "FUNDING_UNIT_STALE_VERSION":
      return "Funding unit başka bir işlemle değişti. Güncel durum yenilendi; lütfen tekrar deneyin.";
    case "FUNDING_UNIT_ALREADY_FUNDED":
      return "Bu funding unit zaten FUNDED durumunda; yeni bir ödeme başlatılamaz. Güncel durum yenilendi.";
    case "PAYMENT_OPERATION_IN_FLIGHT":
      return "Bu unit için zaten devam eden bir ödeme işlemi var. Güncel durum yenilendi.";
    case "PAYMENT_OPERATION_NOT_FOUND":
      return "Bu ödeme işlemi bulunamadı veya artık erişilebilir değil.";
    case "PAYMENT_OPERATION_STALE_VERSION":
      return "Ödeme işlemi başka bir işlemle değişti. Güncel durum yenilendi; lütfen tekrar deneyin.";
    case "PAYMENT_OPERATION_STATE_CONFLICT":
      return "Bu ödeme işlemi artık doğrulama (reconciliation) için uygun değil; sonuç zaten kesinleşmiş. Güncel durum yenilendi.";
    case "FUNDING_MUTATION_FORBIDDEN":
      return "Bu işlem yalnızca alıcı (buyer) tarafının ADMIN kullanıcısına açıktır.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Bu istek anahtarı farklı bir içerikle kullanılmış. Güncel durumu gözden geçirip yeniden deneyin.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    case "LEGAL_ENTITY_ACCESS_DENIED":
      return "Aktif legal entity bağlamı doğrulanamadı. Lütfen yeniden seçim yapın.";
    default:
      return "İşlem tamamlanamadı. Lütfen güncel Deal ve funding durumunu kontrol edin.";
  }
}

/** A stale Deal version, a Deal that left ACTIVE, or a plan that already exists requires a fresh reload before retrying. */
export function shouldRefetchAfterCreatePlanError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "DEAL_STALE_VERSION" ||
      error.code === "DEAL_STATE_CONFLICT" ||
      error.code === "FUNDING_PLAN_ALREADY_EXISTS")
  );
}

/** A stale unit version, a Deal that left ACTIVE, an already-FUNDED unit, or an in-flight operation requires a fresh reload before retrying. */
export function shouldRefetchAfterInitiateError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "FUNDING_UNIT_STALE_VERSION" ||
      error.code === "DEAL_STATE_CONFLICT" ||
      error.code === "FUNDING_UNIT_ALREADY_FUNDED" ||
      error.code === "PAYMENT_OPERATION_IN_FLIGHT")
  );
}

/** A stale operation version or a terminal operation that can no longer be reconciled requires a fresh reload before retrying. */
export function shouldRefetchAfterReconcileError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "PAYMENT_OPERATION_STALE_VERSION" ||
      error.code === "PAYMENT_OPERATION_STATE_CONFLICT")
  );
}

export function shouldResetFundingIdempotencyKey(
  error: unknown,
  kind: "create" | "initiate" | "reconcile",
): boolean {
  if (!(error instanceof ApiError)) return false;
  if (error.code === "IDEMPOTENCY_KEY_REUSED") return true;
  if (kind === "create") return shouldRefetchAfterCreatePlanError(error);
  if (kind === "initiate") return shouldRefetchAfterInitiateError(error);
  return shouldRefetchAfterReconcileError(error);
}

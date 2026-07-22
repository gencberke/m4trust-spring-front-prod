import { ApiError } from "../../app/coreApi";

export function isFundingPlanNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code === "FUNDING_PLAN_NOT_FOUND";
}

export function getFundingErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError))
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  switch (error.code) {
    case "VALIDATION_FAILED":
      return "İstek sunucu tarafından reddedildi. Lütfen güncel anlaşma ve ödeme durumunu kontrol edin.";
    case "DEAL_STALE_VERSION":
      return "Anlaşma başka bir işlemle değişti. Güncel bilgiler yenilendi; lütfen tekrar deneyin.";
    case "DEAL_STATE_CONFLICT":
      return "Anlaşma artık bu ödeme işlemi için uygun değil. Güncel bilgiler yenilendi.";
    case "FUNDING_PLAN_ALREADY_EXISTS":
      return "Bu anlaşma için ödeme planı zaten oluşturulmuş. Güncel plan yenilendi.";
    case "FUNDING_PLAN_NOT_FOUND":
      return "Bu anlaşma için henüz bir ödeme planı oluşturulmadı.";
    case "FUNDING_UNIT_NOT_FOUND":
      return "Ödeme adımı bulunamadı veya artık erişilebilir değil.";
    case "FUNDING_UNIT_STALE_VERSION":
      return "Ödeme adımı başka bir işlemle değişti. Güncel durum yenilendi; lütfen tekrar deneyin.";
    case "FUNDING_UNIT_ALREADY_FUNDED":
      return "Bu ödeme adımı zaten tamamlandı; yeni bir ödeme başlatılamaz. Güncel durum yenilendi.";
    case "PAYMENT_OPERATION_IN_FLIGHT":
      return "Bu ödeme adımı için zaten devam eden bir işlem var. Güncel durum yenilendi.";
    case "PAYMENT_OPERATION_NOT_FOUND":
      return "Bu ödeme işlemi bulunamadı veya artık erişilebilir değil.";
    case "PAYMENT_OPERATION_STALE_VERSION":
      return "Ödeme işlemi başka bir işlemle değişti. Güncel durum yenilendi; lütfen tekrar deneyin.";
    case "PAYMENT_OPERATION_STATE_CONFLICT":
      return "Bu ödeme işlemi artık doğrulama (reconciliation) için uygun değil; sonuç zaten kesinleşmiş. Güncel durum yenilendi.";
    case "FUNDING_MUTATION_FORBIDDEN":
      return "Bu işlem yalnızca alıcı kuruluşun yetkili yöneticisine açıktır.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Bu istek anahtarı farklı bir içerikle kullanılmış. Güncel durumu gözden geçirip yeniden deneyin.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    case "LEGAL_ENTITY_ACCESS_DENIED":
      return "Aktif kuruluş bağlamı doğrulanamadı. Lütfen yeniden seçim yapın.";
    default:
      return "İşlem tamamlanamadı. Lütfen güncel anlaşma ve ödeme durumunu kontrol edin.";
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

import { ApiError } from "../../app/coreApi";

export type RatificationFieldError = Record<string, string>;

export function getRatificationFieldErrors(
  error: unknown,
): RatificationFieldError {
  if (!(error instanceof ApiError) || error.code !== "VALIDATION_FAILED")
    return {};
  return (error.problem?.errors ?? []).reduce<RatificationFieldError>(
    (result, fieldError) => {
      if (!result[fieldError.field]) result[fieldError.field] = fieldError.message;
      return result;
    },
    {},
  );
}

export function getRatificationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError))
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  switch (error.code) {
    case "VALIDATION_FAILED":
      return "Lütfen işaretli alanlardaki değerleri kontrol edin.";
    case "DEAL_STALE_VERSION":
      return "Anlaşma başka bir işlemle değişti. Güncel bilgiler yenilendi; değerlerinizi gözden geçirip yeniden deneyin.";
    case "DEAL_STATE_CONFLICT":
      return "Anlaşma artık bu işlem için uygun değil. Güncel bilgiler yenilendi.";
    case "RATIFICATION_NOT_READY":
      return "Taraflar, kabul edilmiş ticari koşullar veya güncel belge eksik; koşullar onaya sunulamaz. Güncel bilgiler yenilendi.";
    case "RATIFICATION_PACKAGE_CREATE_FORBIDDEN":
      return "Yalnızca anlaşmayı başlatan kuruluş ticari koşulları onaya sunabilir.";
    case "RATIFICATION_STALE_PACKAGE":
      return "Bu ticari koşullar başka bir işlemle değişti veya yerini yeni koşullar aldı. Güncel bilgiler yenilendi; lütfen tekrar kontrol edin.";
    case "RATIFICATION_PACKAGE_STATE_CONFLICT":
      return "Bu ticari koşullar artık onaylanamaz veya reddedilemez durumda. Güncel bilgiler yenilendi.";
    case "RATIFICATION_APPROVAL_FORBIDDEN":
      return "Aktif kuruluş bu ticari koşulları onaylamaya veya reddetmeye yetkili değil.";
    case "RATIFICATION_PACKAGE_NOT_FOUND":
      return "Bu ticari koşullar bulunamadı veya artık erişilebilir değil.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Bu istek anahtarı farklı bir içerikle kullanılmış. Değişikliklerinizi gözden geçirip yeniden deneyin.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    case "LEGAL_ENTITY_ACCESS_DENIED":
      return "Aktif kuruluş bağlamı doğrulanamadı. Lütfen yeniden seçim yapın.";
    default:
      return "İşlem tamamlanamadı. Lütfen güncel anlaşma ve onay durumunu kontrol edin.";
  }
}

/** A stale Deal/package version or readiness loss requires a fresh reload before retrying. */
export function shouldRefetchAfterCreateError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "DEAL_STALE_VERSION" ||
      error.code === "DEAL_STATE_CONFLICT" ||
      error.code === "RATIFICATION_NOT_READY")
  );
}

/** A stale/terminal package or a Deal that left DRAFT requires a fresh reload before retrying. */
export function shouldRefetchAfterActionError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "RATIFICATION_STALE_PACKAGE" ||
      error.code === "RATIFICATION_PACKAGE_STATE_CONFLICT" ||
      error.code === "DEAL_STATE_CONFLICT")
  );
}

/** A stale server state or a reused key changes the canonical request on recovery. */
export function shouldResetRatificationIdempotencyKey(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (shouldRefetchAfterCreateError(error) ||
      shouldRefetchAfterActionError(error) ||
      error.code === "IDEMPOTENCY_KEY_REUSED")
  );
}

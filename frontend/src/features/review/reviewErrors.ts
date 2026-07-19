import { ApiError } from "../../app/coreApi";

export type ReviewFieldError = Record<string, string>;

export function getReviewFieldErrors(error: unknown): ReviewFieldError {
  if (!(error instanceof ApiError) || error.code !== "VALIDATION_FAILED")
    return {};
  return (error.problem?.errors ?? []).reduce<ReviewFieldError>(
    (result, fieldError) => {
      if (!result[fieldError.field])
        result[fieldError.field] = fieldError.message;
      return result;
    },
    {},
  );
}

export function getReviewErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError))
    return "İnceleme kaydedilemedi. Bağlantınızı kontrol edip yeniden deneyin.";
  switch (error.code) {
    case "VALIDATION_FAILED":
      return "Lütfen işaretli alanlardaki değerleri kontrol edin.";
    case "DEAL_STALE_VERSION":
      return "Deal başka bir işlemle değişti. Güncel inceleme ve Deal verisi yenilendi; değişikliklerinizi gözden geçirip yeniden deneyin.";
    case "DEAL_STATE_CONFLICT":
      return "Bu extraction artık kabul edilemez. Güncel Deal ve inceleme verisi yenilendi.";
    case "DEAL_REVIEW_ACCEPTANCE_FORBIDDEN":
      return "Aktif legal entity bu incelemeyi kabul etmeye yetkili değil.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Bu istek anahtarı farklı bir içerikle kullanılmış. Değişikliklerinizi gözden geçirip yeniden deneyin.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    default:
      return "İnceleme şu anda kaydedilemedi. Lütfen güncel Deal durumunu kontrol edin.";
  }
}

export function shouldRefetchReview(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "DEAL_STALE_VERSION" ||
      error.code === "DEAL_STATE_CONFLICT")
  );
}

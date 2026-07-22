import { ApiError } from "../../app/coreApi";

export type CaseworkField =
  | "reasonCode"
  | "subject"
  | "statement"
  | "body"
  | "expectedVersion";

function isCaseworkField(field: string): field is CaseworkField {
  return (
    field === "reasonCode" ||
    field === "subject" ||
    field === "statement" ||
    field === "body" ||
    field === "expectedVersion"
  );
}

function validationMessage(code: string): string {
  switch (code) {
    case "REQUIRED":
      return "Bu alan zorunludur.";
    case "OUT_OF_RANGE":
      return "Bu alanın uzunluğunu kontrol edin.";
    case "INVALID_ENUM":
      return "Seçilen değer desteklenmiyor.";
    default:
      return "Bu alanın değerini kontrol edin.";
  }
}

export type CaseworkFieldError = Partial<Record<CaseworkField, string>>;

export function isCaseworkNotFound(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "CASEWORK_NOT_FOUND_OR_HIDDEN" ||
      error.code === "DISPUTE_NOT_FOUND_OR_HIDDEN")
  );
}

export function getCaseworkFieldErrors(error: unknown): CaseworkFieldError {
  if (!(error instanceof ApiError) || error.code !== "VALIDATION_FAILED") {
    return {};
  }
  const result: CaseworkFieldError = {};
  for (const fieldError of error.problem?.errors ?? []) {
    if (!isCaseworkField(fieldError.field) || result[fieldError.field]) {
      continue;
    }
    result[fieldError.field] = validationMessage(fieldError.code);
  }
  return result;
}

export function getCaseworkErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError))
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  switch (error.code) {
    case "VALIDATION_FAILED":
      return "Lütfen işaretli alanlardaki değerleri kontrol edin.";
    case "DEAL_STALE_VERSION":
      return "Deal başka bir işlemle değişti. Güncel Deal verisi yenilendi; lütfen tekrar deneyin.";
    case "FULFILLMENT_STALE_VERSION":
      return "Teslimat süreci başka bir işlemle değişti. Güncel bilgiler yenilendi; lütfen tekrar deneyin.";
    case "DEAL_STATE_CONFLICT":
    case "FULFILLMENT_STATE_CONFLICT":
      return "Anlaşma veya teslimat süreci artık bu işlem için uygun değil. Güncel bilgiler yenilendi.";
    case "DISPUTE_ACTIVE_CASE_EXISTS":
      return "Bu Deal için zaten aktif bir uyuşmazlık var. Güncel veri yenilendi.";
    case "DISPUTE_STALE_VERSION":
      return "Uyuşmazlık başka bir işlemle değişti. Güncel veri yenilendi; lütfen tekrar deneyin.";
    case "DISPUTE_STATE_CONFLICT":
      return "Uyuşmazlık artık bu işlem için uygun durumda değil. Güncel veri yenilendi.";
    case "DISPUTE_OPEN_FORBIDDEN":
      return "Bu işlem yalnızca alıcı veya satıcı kuruluşun yetkili yöneticisine açıktır.";
    case "DISPUTE_COMMENT_FORBIDDEN":
      return "Bu yorum işlemi için yetkiniz yok.";
    case "DISPUTE_ACKNOWLEDGE_FORBIDDEN":
      return "Uyuşmazlığı yalnızca karşı tarafın yetkili yöneticisi onaylayabilir.";
    case "DISPUTE_WITHDRAW_FORBIDDEN":
      return "Geri çekme yalnızca kaydı açan tarafın yetkili yöneticisine açıktır.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Bu istek anahtarı farklı bir içerikle kullanılmış. Güncel durumu gözden geçirip yeniden deneyin.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    case "LEGAL_ENTITY_ACCESS_DENIED":
      return "Aktif legal entity bağlamı doğrulanamadı. Lütfen yeniden seçim yapın.";
    default:
      return "İşlem tamamlanamadı. Lütfen güncel Deal ve uyuşmazlık durumunu kontrol edin.";
  }
}

export function shouldRefetchAfterOpenError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "DEAL_STALE_VERSION" ||
      error.code === "FULFILLMENT_STALE_VERSION" ||
      error.code === "DEAL_STATE_CONFLICT" ||
      error.code === "FULFILLMENT_STATE_CONFLICT" ||
      error.code === "DISPUTE_ACTIVE_CASE_EXISTS")
  );
}

export function shouldRefetchAfterMutationError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "DISPUTE_STALE_VERSION" || error.code === "DISPUTE_STATE_CONFLICT")
  );
}

export function shouldResetCaseworkIdempotencyKey(
  error: unknown,
  kind: "open" | "comment" | "acknowledge" | "withdraw",
): boolean {
  if (!(error instanceof ApiError)) return false;
  if (error.code === "IDEMPOTENCY_KEY_REUSED") return true;
  if (kind === "open") return shouldRefetchAfterOpenError(error);
  return shouldRefetchAfterMutationError(error);
}

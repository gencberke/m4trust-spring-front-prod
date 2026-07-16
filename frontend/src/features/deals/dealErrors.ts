import { ApiError } from "../../app/coreApi";

export type DealField = "title" | "description";

function isDealField(field: string): field is DealField {
  return field === "title" || field === "description";
}

export function getDealFieldErrors(
  error: unknown,
): Partial<Record<DealField, string>> {
  if (!(error instanceof ApiError) || error.code !== "VALIDATION_FAILED") {
    return {};
  }

  const result: Partial<Record<DealField, string>> = {};
  for (const fieldError of error.problem?.errors ?? []) {
    if (!isDealField(fieldError.field) || result[fieldError.field]) {
      continue;
    }
    result[fieldError.field] =
      fieldError.code === "REQUIRED"
        ? "Bu alan zorunludur."
        : "Bu alanın uzunluğunu ve biçimini kontrol edin.";
  }
  return result;
}

export function isDealNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code === "DEAL_NOT_FOUND";
}

export function isDealStaleVersion(error: unknown): boolean {
  return error instanceof ApiError && error.code === "DEAL_STALE_VERSION";
}

export function getDealErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  }

  switch (error.code) {
    case "VALIDATION_FAILED":
      return "Lütfen işaretli alanları kontrol edin.";
    case "DEAL_STALE_VERSION":
      return "Bu kayıt başka bir işlemde değiştirildi. Değerlerinizi koruduk; güncel veriyi yükleyip değişikliklerinizi yeniden değerlendirin.";
    case "DEAL_STATE_CONFLICT":
      return "Deal’in güncel durumu bu işleme izin vermiyor.";
    case "DEAL_NOT_FOUND":
      return "Bu Deal bulunamadı veya aktif legal entity için görünür değil.";
    case "LEGAL_ENTITY_ACCESS_DENIED":
      return "Aktif legal entity bağlamı doğrulanamadı. Lütfen yeniden seçim yapın.";
    case "LEGAL_ENTITY_NOT_FOUND":
      return "Aktif legal entity bulunamadı veya artık erişiminiz yok.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    default:
      return "İşlem tamamlanamadı. Lütfen daha sonra yeniden deneyin.";
  }
}

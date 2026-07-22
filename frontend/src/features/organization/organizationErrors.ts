import { ApiError } from "../../app/coreApi";

export type LegalEntityField = "legalName" | "registrationNumber";

const REQUIRED_MESSAGES: Record<LegalEntityField, string> = {
  legalName: "Kuruluş adını girin.",
  registrationNumber: "Kayıt numarasını girin.",
};

function isLegalEntityField(field: string): field is LegalEntityField {
  return field === "legalName" || field === "registrationNumber";
}

export function getLegalEntityFieldErrors(
  error: unknown,
): Partial<Record<LegalEntityField, string>> {
  if (!(error instanceof ApiError) || error.code !== "VALIDATION_FAILED") {
    return {};
  }

  const result: Partial<Record<LegalEntityField, string>> = {};
  for (const fieldError of error.problem?.errors ?? []) {
    if (!isLegalEntityField(fieldError.field) || result[fieldError.field]) {
      continue;
    }

    result[fieldError.field] =
      fieldError.code === "REQUIRED"
        ? REQUIRED_MESSAGES[fieldError.field]
        : "Bu alanın uzunluğunu ve biçimini kontrol edin.";
  }
  return result;
}

export function isInvalidLegalEntitySelection(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "LEGAL_ENTITY_ACCESS_DENIED" ||
      error.code === "LEGAL_ENTITY_NOT_FOUND")
  );
}

export function getOrganizationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  }

  switch (error.code) {
    case "VALIDATION_FAILED":
      return "Lütfen işaretli alanları kontrol edin.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    case "LEGAL_ENTITY_ACCESS_DENIED":
      return "Seçili kuruluş için erişim doğrulanamadı. Lütfen yeniden seçim yapın.";
    case "LEGAL_ENTITY_NOT_FOUND":
      return "Seçili kuruluş bulunamadı veya artık erişiminiz yok.";
    default:
      return "İşlem tamamlanamadı. Lütfen daha sonra yeniden deneyin.";
  }
}

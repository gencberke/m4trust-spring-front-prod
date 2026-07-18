import { ApiError } from "../../app/coreApi";

export function isDocumentUploadExpired(error: unknown): boolean {
  return error instanceof ApiError && error.code === "DOCUMENT_UPLOAD_EXPIRED";
}

export function getDocumentErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  }

  switch (error.code) {
    case "VALIDATION_FAILED":
      return "Dosya bilgileri geçerli değil. Boyutu ve biçimi kontrol edin.";
    case "DEAL_DOCUMENT_MUTATION_FORBIDDEN":
      return "Bu işlemi yalnızca Deal’i oluşturan taraf yapabilir.";
    case "DEAL_DOCUMENT_UPLOAD_NOT_ALLOWED":
      return "Bu Deal’in güncel durumu belge yüklemeye izin vermiyor.";
    case "DEAL_DOCUMENT_NOT_FOUND":
      return "Belge bulunamadı veya artık erişilebilir değil.";
    case "DOCUMENT_UPLOAD_EXPIRED":
      return "Yükleme süresi doldu. Yeniden deneyerek yeni bir yükleme başlatabilirsiniz.";
    case "DOCUMENT_UPLOAD_STATE_CONFLICT":
      return "Bu belge zaten tamamlanmış veya artık bekleyen bir yükleme değil.";
    case "DOCUMENT_VERIFICATION_FAILED":
      return "Yüklenen dosya doğrulanamadı; boyut veya içerik uyuşmuyor. Yeniden deneyin.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Bu yükleme isteği başka bir istekle çakıştı. Yeniden deneyin.";
    case "DOCUMENT_DOWNLOAD_NOT_AVAILABLE":
      return "Bu belge için indirme bağlantısı şu anda kullanılamıyor.";
    case "DEAL_NOT_FOUND":
      return "Bu Deal bulunamadı veya aktif legal entity için görünür değil.";
    case "LEGAL_ENTITY_NOT_FOUND":
      return "Seçilen legal entity bulunamadı veya artık üyeliğiniz yok.";
    case "LEGAL_ENTITY_ACCESS_DENIED":
      return "Aktif legal entity bağlamı doğrulanamadı. Lütfen yeniden seçim yapın.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    default:
      return "İşlem tamamlanamadı. Lütfen daha sonra yeniden deneyin.";
  }
}

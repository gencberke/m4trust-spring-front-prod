import { ApiError } from "../../app/coreApi";

const FAILURE_MESSAGES: Readonly<Record<string, string>> = {
  MODEL_PROVIDER_TIMEOUT:
    "Belge analizi zaman aşımına uğradı. Yeni bir analiz talebi oluşturabilirsiniz.",
  MODEL_PROVIDER_UNAVAILABLE:
    "Analiz hizmetine geçici olarak ulaşılamadı. Bir süre sonra yeniden deneyebilirsiniz.",
  OBJECT_STORAGE_TEMPORARILY_UNAVAILABLE:
    "Belge analiz için alınamadı. Belge erişimini kontrol edip yeniden deneyebilirsiniz.",
  RETRIEVAL_SERVICE_UNAVAILABLE:
    "Belge içeriğini hazırlayan hizmete geçici olarak ulaşılamadı. Bir süre sonra yeniden deneyebilirsiniz.",
  INTERNAL_DEPENDENCY_TIMEOUT:
    "Analiz hizmetlerinden biri zaman aşımına uğradı. Yeni bir analiz talebi oluşturabilirsiniz.",
  UNSUPPORTED_MEDIA_TYPE:
    "Belgenin biçimi analiz hizmeti tarafından desteklenmiyor.",
  UNSUPPORTED_SCHEMA_VERSION:
    "Analiz hizmeti gereken sonuç biçimini desteklemiyor. Lütfen daha sonra yeniden deneyin.",
  FILE_TOO_LARGE:
    "Belge analiz hizmetinin boyut sınırını aşıyor.",
  ENCRYPTED_DOCUMENT_UNSUPPORTED:
    "Şifrelenmiş belgeler analiz edilemiyor. Şifresiz bir belge sürümü yükleyin.",
  CORRUPTED_FILE:
    "Belge içeriği analiz edilemedi. Geçerli bir belge sürümü yükleyip yeniden deneyin.",
  CONTENT_HASH_MISMATCH:
    "Belgenin bütünlük bilgisi doğrulanamadı. Güncel belgeyi yeniden yükleyin.",
  MISSING_REQUIRED_FIELD:
    "Analiz talebinin güvenli girdi bilgileri doğrulanamadı. Belgeyi ve anlaşma durumunu kontrol edin.",
  INVALID_DEADLINE:
    "Analiz talebinin zaman sınırı doğrulanamadı. Lütfen yeni bir analiz talebi oluşturun.",
  INVALID_DOWNLOAD_REFERENCE:
    "Belgeye güvenli erişim bilgisi doğrulanamadı. Belgeyi ve anlaşma durumunu kontrol edin.",
  INVALID_PROCESSING_PROFILE:
    "Analiz işleme profili doğrulanamadı. Lütfen daha sonra yeniden deneyin.",
  INVALID_EXPECTED_OBJECT:
    "Analiz talebindeki belge sürümü doğrulanamadı. Güncel belgeyi kontrol edin.",
};

export function getAnalysisFailureMessage(code: string): string {
  return FAILURE_MESSAGES[code]
    ?? "Belge analizi teknik bir nedenle tamamlanamadı. Hata ayrıntıları güvenli biçimde gizlendi.";
}

export function getAnalysisReadErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Analiz bilgisine ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  }
  switch (error.code) {
    case "DEAL_NOT_FOUND":
      return "Bu anlaşma bulunamadı veya aktif kuruluş için görünür değil.";
    case "LEGAL_ENTITY_ACCESS_DENIED":
    case "LEGAL_ENTITY_NOT_FOUND":
      return "Aktif kuruluş bağlamı doğrulanamadı.";
    default:
      return "Analiz bilgisi şu anda alınamıyor. Lütfen yeniden deneyin.";
  }
}

export function getAnalysisRequestErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Analiz talebi gönderilemedi. Bağlantınızı kontrol edip yeniden deneyin.";
  }
  switch (error.code) {
    case "DEAL_DOCUMENT_ANALYSIS_ACTIVE_JOB_EXISTS":
      return "Bu belge için bir analiz zaten devam ediyor. Güncel durum yeniden yükleniyor.";
    case "DEAL_DOCUMENT_ANALYSIS_DOCUMENT_NOT_AVAILABLE":
      return "Analiz için güncel ve kullanılabilir bir belge bulunamadı.";
    case "DEAL_STATE_CONFLICT":
      return "Anlaşmanın güncel durumu yeni analiz talebine izin vermiyor.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Talep güvenli biçimde yinelenemedi. Lütfen yeniden deneyin.";
    case "DEAL_ANALYSIS_REQUEST_FORBIDDEN":
      return "Bu kuruluş analiz talebi oluşturamaz.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    default:
      return "Analiz talebi kabul edilemedi. Güncel anlaşma durumunu kontrol edip yeniden deneyin.";
  }
}

export function shouldRefetchAfterAnalysisRequestError(error: unknown): boolean {
  return error instanceof ApiError && (
    error.code === "DEAL_DOCUMENT_ANALYSIS_ACTIVE_JOB_EXISTS"
    || error.code === "DEAL_DOCUMENT_ANALYSIS_DOCUMENT_NOT_AVAILABLE"
    || error.code === "DEAL_STATE_CONFLICT"
  );
}

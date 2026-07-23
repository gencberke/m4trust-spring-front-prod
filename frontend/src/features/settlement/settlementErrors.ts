import { ApiError } from "../../app/coreApi";

export function isSettlementNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code === "SETTLEMENT_NOT_FOUND";
}

export function isReleaseOperationNotFound(error: unknown): boolean {
  return (
    error instanceof ApiError && error.code === "RELEASE_OPERATION_NOT_FOUND"
  );
}

export function getSettlementErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError))
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  switch (error.code) {
    case "VALIDATION_FAILED":
      return "İstek sunucu tarafından reddedildi. Lütfen güncel anlaşma ve kapanış durumunu kontrol edin.";
    case "DEAL_STALE_VERSION":
      return "Anlaşma başka bir işlemle değişti. Güncel bilgiler yenilendi; lütfen tekrar deneyin.";
    case "DEAL_STATE_CONFLICT":
      return "Anlaşma artık bu kapanış işlemi için uygun değil. Güncel bilgiler yenilendi.";
    case "SETTLEMENT_NOT_FOUND":
      return "Bu anlaşma için henüz kapanış bilgisi sunulmuyor.";
    case "SETTLEMENT_STALE_VERSION":
      return "Kapanış kaydı başka bir işlemle değişti. Güncel durum yenilendi; lütfen tekrar deneyin.";
    case "FULFILLMENT_STALE_VERSION":
      return "Teslimat kaydı başka bir işlemle değişti. Güncel durum yenilendi; lütfen tekrar deneyin.";
    case "FUNDING_UNIT_STALE_VERSION":
      return "Ödeme adımı başka bir işlemle değişti. Güncel durum yenilendi; lütfen tekrar deneyin.";
    case "SETTLEMENT_CONTRACTUAL_WINDOW_MISSING":
      return "Onay paketi kapanışa uygun değil (eski şema veya itiraz penceresi tanımsız).";
    case "SETTLEMENT_DISPUTE_WINDOW_NOT_ELAPSED":
      return "İtiraz penceresi henüz dolmadı. Sunucunun belirttiği tarihe kadar bekleyin.";
    case "SETTLEMENT_ACTIVE_DISPUTE":
      return "Açık bir uyuşmazlık kapanışı engelliyor. Güncel durum yenilendi.";
    case "SETTLEMENT_ALREADY_TERMINAL":
      return "Kapanış zaten sonuçlanmış; yeni bir işlem başlatılamaz.";
    case "RELEASE_OPERATION_ALREADY_EXISTS":
      return "Bu anlaşma için zaten bir kapanış işlemi var. Güncel durum yenilendi.";
    case "RELEASE_OPERATION_NOT_FOUND":
      return "Kapanış işlemi bulunamadı veya artık erişilebilir değil.";
    case "RELEASE_OPERATION_STALE_VERSION":
      return "Kapanış işlemi başka bir işlemle değişti. Güncel durum yenilendi; lütfen tekrar deneyin.";
    case "RELEASE_RECONCILIATION_UNAVAILABLE":
      return "Bu kapanış işlemi artık doğrulama için uygun değil; sonuç zaten kesinleşmiş olabilir.";
    case "RELEASE_OUTCOME_UNKNOWN":
      return "Kapanış sonucu henüz doğrulanamadı. Lütfen kısa süre sonra yeniden deneyin.";
    case "SETTLEMENT_MUTATION_FORBIDDEN":
      return "Bu işlem yalnızca alıcı kuruluşun yetkili yöneticisine açıktır.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Bu istek anahtarı farklı bir içerikle kullanılmış. Güncel durumu gözden geçirip yeniden deneyin.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    case "LEGAL_ENTITY_ACCESS_DENIED":
      return "Aktif kuruluş bağlamı doğrulanamadı. Lütfen yeniden seçim yapın.";
    default:
      return "İşlem tamamlanamadı. Lütfen güncel anlaşma ve kapanış durumunu kontrol edin.";
  }
}

export function shouldRefetchAfterReleaseError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "DEAL_STALE_VERSION" ||
      error.code === "DEAL_STATE_CONFLICT" ||
      error.code === "SETTLEMENT_STALE_VERSION" ||
      error.code === "FULFILLMENT_STALE_VERSION" ||
      error.code === "FUNDING_UNIT_STALE_VERSION" ||
      error.code === "SETTLEMENT_ACTIVE_DISPUTE" ||
      error.code === "SETTLEMENT_ALREADY_TERMINAL" ||
      error.code === "RELEASE_OPERATION_ALREADY_EXISTS")
  );
}

export function shouldRefetchAfterReconcileError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "RELEASE_OPERATION_STALE_VERSION" ||
      error.code === "RELEASE_RECONCILIATION_UNAVAILABLE" ||
      error.code === "SETTLEMENT_ALREADY_TERMINAL")
  );
}

export function shouldResetSettlementIdempotencyKey(
  error: unknown,
  kind: "release" | "reconcile",
): boolean {
  if (!(error instanceof ApiError)) return false;
  if (error.code === "IDEMPOTENCY_KEY_REUSED") return true;
  if (kind === "release") return shouldRefetchAfterReleaseError(error);
  return shouldRefetchAfterReconcileError(error);
}

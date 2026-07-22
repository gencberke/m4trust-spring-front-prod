import { ApiError } from "../../app/coreApi";

export function isInvitationRefreshRequired(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === "DEAL_INVITATION_NOT_FOUND" ||
      error.code === "DEAL_INVITATION_STALE_VERSION" ||
      error.code === "DEAL_INVITATION_STATE_CONFLICT")
  );
}

export function getInvitationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  }

  switch (error.code) {
    case "VALIDATION_FAILED":
      return "Lütfen işaretli alanları kontrol edin.";
    case "DEAL_INVITATION_NOT_FOUND":
      return "Davet bulunamadı veya bu işlem için erişilebilir değil.";
    case "DEAL_INVITATION_STALE_VERSION":
      return "Davet başka bir işlemde değiştirildi. Güncel durum yenilendi.";
    case "DEAL_INVITATION_STATE_CONFLICT":
      return "Davetin güncel durumu bu işleme izin vermiyor.";
    case "DEAL_INVITATION_ACCEPTED_BY_OTHER_ENTITY":
      return "Bu davet daha önce başka bir kuruluşla kabul edildi.";
    case "DEAL_INVITATION_PENDING_EXISTS":
      return "Bu e-posta için zaten bekleyen bir davet var.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Bu gönderim anahtarı başka bir davet isteğiyle kullanılmış. Formu sıfırlayıp yeniden deneyin.";
    case "DEAL_INVITATION_FORBIDDEN":
      return "Bu anlaşma için davet işlemi yapma yetkiniz yok.";
    case "LEGAL_ENTITY_NOT_FOUND":
      return "Seçilen kuruluş bulunamadı veya artık üyeliğiniz yok.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    default:
      return "İşlem tamamlanamadı. Lütfen daha sonra yeniden deneyin.";
  }
}

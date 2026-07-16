import { ApiError } from "./authApi";

export type AuthField = "displayName" | "email" | "password";
export type AuthOperation = "login" | "logout" | "register";

const REQUIRED_MESSAGES: Record<AuthField, string> = {
  displayName: "Adınızı girin.",
  email: "E-posta adresinizi girin.",
  password: "Parolanızı girin.",
};

function validationMessage(field: AuthField, code: string): string {
  if (code === "REQUIRED") {
    return REQUIRED_MESSAGES[field];
  }

  if (field === "email" && code === "INVALID_FORMAT") {
    return "Geçerli bir e-posta adresi girin.";
  }

  if (field === "password" && code === "PASSWORD_TOO_COMMON") {
    return "Daha az tahmin edilebilir bir parola seçin.";
  }

  if (code === "INVALID_LENGTH") {
    return field === "password"
      ? "Parola uzunluğu gereksinimlerini kontrol edin."
      : "Bu alanın uzunluğunu kontrol edin.";
  }

  return "Bu alanı kontrol edin.";
}

function isAuthField(field: string): field is AuthField {
  return field === "displayName" || field === "email" || field === "password";
}

export function getFieldErrors(error: unknown): Partial<Record<AuthField, string>> {
  if (!(error instanceof ApiError) || error.code !== "VALIDATION_FAILED") {
    return {};
  }

  const result: Partial<Record<AuthField, string>> = {};
  for (const fieldError of error.problem?.errors ?? []) {
    if (isAuthField(fieldError.field) && !result[fieldError.field]) {
      result[fieldError.field] = validationMessage(fieldError.field, fieldError.code);
    }
  }
  return result;
}

export function getAuthErrorMessage(error: unknown, operation: AuthOperation): string {
  if (!(error instanceof ApiError)) {
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip yeniden deneyin.";
  }

  switch (error.code) {
    case "AUTH_INVALID_CREDENTIALS":
      return "E-posta veya parola geçersiz.";
    case "AUTH_EMAIL_ALREADY_EXISTS":
      return "Bu e-posta adresiyle daha önce bir hesap oluşturulmuş.";
    case "VALIDATION_FAILED":
      return "Lütfen işaretli alanları kontrol edin.";
    case "CSRF_TOKEN_INVALID":
      return "Güvenlik doğrulaması yenilenemedi. Lütfen tekrar deneyin.";
    case "AUTH_SESSION_EXPIRED":
      return "Oturumunuz sona erdi. Yeniden giriş yapın.";
    default:
      return operation === "logout"
        ? "Çıkış yapılamadı. Oturumunuz açık kalmaya devam ediyor."
        : "İşlem tamamlanamadı. Lütfen daha sonra yeniden deneyin.";
  }
}

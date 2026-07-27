import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router";

import { register, type RegisterRequest } from "../authApi";
import { getAuthErrorMessage, getFieldErrors } from "../authErrors";
import { refreshCurrentUserAfterAuthentication } from "../useCurrentUser";
import styles from "../Auth.module.css";

export function RegisterScreen() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: async (request: RegisterRequest) => {
      await register(request);
      return refreshCurrentUserAfterAuthentication(queryClient);
    },
    onSuccess: () => {
      navigate("/app", { replace: true });
    },
  });
  const fieldErrors = getFieldErrors(mutation.error);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const request: RegisterRequest = {
      displayName: String(formData.get("displayName") ?? ""),
      email: String(formData.get("email") ?? ""),
      password: String(formData.get("password") ?? ""),
    };
    mutation.mutate(request);
  }

  return (
    <div className={`${styles.shell} ${styles.entry}`}>
      <header className={styles.header}>
        <Link
          className="brand brand-link"
          to="/"
          aria-label="M4Trust ana sayfa"
        >
          M4Trust
        </Link>
        <span className={styles.headerCaption}>Güvenli hesap erişimi</span>
      </header>

      <main className={styles.main}>
        <section
          className={styles.introduction}
          aria-labelledby="register-title"
        >
          <span className="section-kicker">M4Trust’a katılın</span>
          <h1 id="register-title">Güvenle çalışmaya başlayın.</h1>
          <p>
            Hesabınızı oluşturun; güvenli oturumunuz otomatik olarak açılsın ve
            çalışma alanınıza doğrudan geçin.
          </p>
          <div className={styles.trustNote}>
            <span className={styles.trustMark} aria-hidden="true">
              ✓
            </span>
            <p>
              Uzun ve hatırlanabilir bir parola kullanın; en az 15 karakter.
            </p>
          </div>
        </section>

        <section className={styles.card} aria-label="Kayıt formu">
          <div className={styles.cardHeading}>
            <span>Yeni hesap</span>
            <h2>Hesap oluştur</h2>
          </div>

          {mutation.isError ? (
            <p className="form-alert" role="alert">
              {getAuthErrorMessage(mutation.error, "register")}
            </p>
          ) : null}

          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="field-group">
              <label htmlFor="register-name">Adınız</label>
              <input
                id="register-name"
                name="displayName"
                type="text"
                autoComplete="name"
                required
                maxLength={200}
                aria-invalid={Boolean(fieldErrors.displayName)}
                aria-describedby={
                  fieldErrors.displayName ? "register-name-error" : undefined
                }
              />
              {fieldErrors.displayName ? (
                <span className="field-error" id="register-name-error">
                  {fieldErrors.displayName}
                </span>
              ) : null}
            </div>

            <div className="field-group">
              <label htmlFor="register-email">E-posta adresi</label>
              <input
                id="register-email"
                name="email"
                type="email"
                autoComplete="email"
                inputMode="email"
                required
                minLength={3}
                maxLength={320}
                aria-invalid={Boolean(fieldErrors.email)}
                aria-describedby={
                  fieldErrors.email ? "register-email-error" : undefined
                }
              />
              {fieldErrors.email ? (
                <span className="field-error" id="register-email-error">
                  {fieldErrors.email}
                </span>
              ) : null}
            </div>

            <div className="field-group">
              <div className="field-label-row">
                <label htmlFor="register-password">Parola</label>
                <span>15–128 karakter</span>
              </div>
              <input
                id="register-password"
                name="password"
                type="password"
                autoComplete="new-password"
                required
                minLength={15}
                maxLength={128}
                aria-invalid={Boolean(fieldErrors.password)}
                aria-describedby={
                  fieldErrors.password
                    ? "register-password-error"
                    : "password-hint"
                }
              />
              {fieldErrors.password ? (
                <span className="field-error" id="register-password-error">
                  {fieldErrors.password}
                </span>
              ) : (
                <span className="field-hint" id="password-hint">
                  Tahmin edilmesi zor, uzun bir ifade tercih edin.
                </span>
              )}
            </div>

            <button
              className="primary-button"
              type="submit"
              disabled={mutation.isPending}
            >
              {mutation.isPending ? "Hesap oluşturuluyor…" : "Hesap oluştur"}
            </button>
          </form>

          <p className={styles.switch}>
            Zaten hesabınız var mı? <Link to="/login">Giriş yapın</Link>
          </p>
        </section>
      </main>
    </div>
  );
}

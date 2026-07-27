import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router";

import { login, type LoginRequest } from "../authApi";
import { getAuthErrorMessage, getFieldErrors } from "../authErrors";
import { refreshCurrentUserAfterAuthentication } from "../useCurrentUser";
import styles from "../Auth.module.css";

function getRouteNotice(state: unknown): string | undefined {
  if (typeof state !== "object" || state === null || !("reason" in state)) {
    return undefined;
  }

  if (state.reason === "session-expired") {
    return "Oturumunuz sona erdi. Devam etmek için yeniden giriş yapın.";
  }

  if (state.reason === "logged-out") {
    return "Güvenli çıkış tamamlandı.";
  }

  return undefined;
}

export function LoginScreen() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: async (request: LoginRequest) => {
      await login(request);
      return refreshCurrentUserAfterAuthentication(queryClient);
    },
    onSuccess: () => {
      navigate("/app", { replace: true });
    },
  });
  const fieldErrors = getFieldErrors(mutation.error);
  const routeNotice = getRouteNotice(location.state);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const request: LoginRequest = {
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
        <section className={styles.introduction} aria-labelledby="login-title">
          <span className="section-kicker">Tekrar hoş geldiniz</span>
          <h1 id="login-title">Hesabınıza giriş yapın.</h1>
          <p>
            M4Trust çalışma alanınıza güvenli, sunucu tarafından yönetilen
            oturumla devam edin.
          </p>
          <div className={styles.trustNote}>
            <span className={styles.trustMark} aria-hidden="true">
              ✓
            </span>
            <p>Oturum bilgileri bu tarayıcının depolama alanında tutulmaz.</p>
          </div>
        </section>

        <section className={styles.card} aria-label="Giriş formu">
          <div className={styles.cardHeading}>
            <span>Hesap erişimi</span>
            <h2>Giriş yap</h2>
          </div>

          {routeNotice ? (
            <p className="form-notice" role="status">
              {routeNotice}
            </p>
          ) : null}

          {mutation.isError ? (
            <p className="form-alert" role="alert">
              {getAuthErrorMessage(mutation.error, "login")}
            </p>
          ) : null}

          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="field-group">
              <label htmlFor="login-email">E-posta adresi</label>
              <input
                id="login-email"
                name="email"
                type="email"
                autoComplete="username"
                inputMode="email"
                required
                minLength={3}
                maxLength={320}
                aria-invalid={Boolean(fieldErrors.email)}
                aria-describedby={
                  fieldErrors.email ? "login-email-error" : undefined
                }
              />
              {fieldErrors.email ? (
                <span className="field-error" id="login-email-error">
                  {fieldErrors.email}
                </span>
              ) : null}
            </div>

            <div className="field-group">
              <label htmlFor="login-password">Parola</label>
              <input
                id="login-password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                minLength={1}
                maxLength={128}
                aria-invalid={Boolean(fieldErrors.password)}
                aria-describedby={
                  fieldErrors.password ? "login-password-error" : undefined
                }
              />
              {fieldErrors.password ? (
                <span className="field-error" id="login-password-error">
                  {fieldErrors.password}
                </span>
              ) : null}
            </div>

            <button
              className="primary-button"
              type="submit"
              disabled={mutation.isPending}
            >
              {mutation.isPending ? "Giriş yapılıyor…" : "Giriş yap"}
            </button>
          </form>

          <p className={styles.switch}>
            Henüz hesabınız yok mu? <Link to="/register">Hesap oluşturun</Link>
          </p>
        </section>
      </main>
    </div>
  );
}

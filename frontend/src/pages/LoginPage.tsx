import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router";

import { login, type LoginRequest } from "../features/auth/authApi";
import { getAuthErrorMessage, getFieldErrors } from "../features/auth/authErrors";
import { refreshCurrentUserAfterAuthentication } from "../features/auth/useCurrentUser";

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

export function LoginPage() {
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
    <div className="auth-shell auth-shell--entry">
      <header className="auth-header">
        <Link className="brand brand-link" to="/" aria-label="M4Trust ana sayfa">
          M4Trust
        </Link>
        <span className="auth-header__caption">Güvenli hesap erişimi</span>
      </header>

      <main className="auth-main">
        <section className="auth-introduction auth-introduction--login" aria-labelledby="login-title">
          <span className="section-kicker">Tekrar hoş geldiniz</span>
          <h1 id="login-title">Hesabınıza giriş yapın.</h1>
          <p>
            M4Trust çalışma alanınıza güvenli, sunucu tarafından yönetilen oturumla
            devam edin.
          </p>
          <div className="trust-note">
            <span className="trust-note__mark" aria-hidden="true">✓</span>
            <p>Oturum bilgileri bu tarayıcının depolama alanında tutulmaz.</p>
          </div>
        </section>

        <section className="auth-card" aria-label="Giriş formu">
          <div className="auth-card__heading">
            <span>Hesap erişimi</span>
            <h2>Giriş yap</h2>
          </div>

          {routeNotice ? (
            <p className="form-notice" role="status">{routeNotice}</p>
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
                aria-describedby={fieldErrors.email ? "login-email-error" : undefined}
              />
              {fieldErrors.email ? (
                <span className="field-error" id="login-email-error">{fieldErrors.email}</span>
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
                aria-describedby={fieldErrors.password ? "login-password-error" : undefined}
              />
              {fieldErrors.password ? (
                <span className="field-error" id="login-password-error">{fieldErrors.password}</span>
              ) : null}
            </div>

            <button className="primary-button" type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? "Giriş yapılıyor…" : "Giriş yap"}
            </button>
          </form>

          <p className="auth-switch">
            Henüz hesabınız yok mu? <Link to="/register">Hesap oluşturun</Link>
          </p>
        </section>
      </main>
    </div>
  );
}

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useOutletContext } from "react-router";

import { logout, type PublicUser } from "../features/auth/authApi";
import { getAuthErrorMessage } from "../features/auth/authErrors";
import { CURRENT_USER_QUERY_KEY } from "../features/auth/useCurrentUser";

export function AuthenticatedAppPage() {
  const user = useOutletContext<PublicUser>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  async function clearVerifiedSession() {
    await queryClient.cancelQueries({ queryKey: CURRENT_USER_QUERY_KEY });
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, null);
    navigate("/login", { replace: true, state: { reason: "logged-out" } });
  }

  const mutation = useMutation({
    mutationFn: logout,
    onSuccess: clearVerifiedSession,
  });

  return (
    <div className="app-shell authenticated-shell">
      <header className="site-header authenticated-header">
        <span className="brand" aria-label="M4Trust">M4Trust</span>
        <div className="account-summary" aria-label="Aktif hesap">
          <span>{user.displayName}</span>
          <span>{user.email}</span>
        </div>
      </header>

      <main className="workspace-main">
        <div className="workspace-column">
          <span className="section-kicker">Güvenli çalışma alanı</span>
          <h1>Hoş geldiniz, {user.displayName}.</h1>
          <p className="workspace-lead">
            Oturumunuz Spring tarafından doğrulandı. M4Trust’ın sonraki çalışma alanları
            burada yer alacak.
          </p>

          <section className="session-card" aria-labelledby="session-title">
            <div>
              <span className="session-card__status">Aktif</span>
              <h2 id="session-title">Güvenli oturum</h2>
              <p>Bu cihazdaki sunucu oturumunu sonlandırmak için güvenli çıkışı kullanın.</p>
            </div>
            <button
              className="secondary-button"
              type="button"
              onClick={() => mutation.mutate()}
              disabled={mutation.isPending}
            >
              {mutation.isPending ? "Çıkış yapılıyor…" : "Güvenli çıkış"}
            </button>
          </section>

          {mutation.isError ? (
            <p className="form-alert workspace-alert" role="alert">
              {getAuthErrorMessage(mutation.error, "logout")}
            </p>
          ) : null}
        </div>
      </main>

      <footer className="site-footer">
        <p>Oturum yetkisi yalnızca M4Trust Core API tarafından belirlenir.</p>
      </footer>
    </div>
  );
}

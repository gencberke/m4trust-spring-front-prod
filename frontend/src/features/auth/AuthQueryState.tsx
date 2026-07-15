import type { UseQueryResult } from "@tanstack/react-query";

import type { PublicUser } from "./authApi";

interface AuthQueryStateProps {
  query: UseQueryResult<PublicUser | null>;
}

export function AuthQueryState({ query }: AuthQueryStateProps) {
  const hasError = query.isError;

  return (
    <div className="app-shell">
      <header className="site-header">
        <span className="brand">M4Trust</span>
      </header>
      <main className="state-page" aria-busy={!hasError}>
        <section
          className="state-card"
          role={hasError ? "alert" : "status"}
          aria-live={hasError ? "assertive" : "polite"}
        >
          <span className="state-card__eyebrow">Güvenli oturum</span>
          <h1>{hasError ? "Bağlantı kurulamadı" : "Oturum doğrulanıyor"}</h1>
          <p>
            {hasError
              ? "Oturum bilgisi doğrulanamadı. Bağlantınızı kontrol edip yeniden deneyin."
              : "Güvenli alan hazırlanırken kısa bir süre bekleyin."}
          </p>
          {hasError ? (
            <button
              className="primary-button state-card__action"
              type="button"
              onClick={() => void query.refetch()}
              disabled={query.isFetching}
            >
              {query.isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
            </button>
          ) : (
            <span className="loading-line" aria-hidden="true" />
          )}
        </section>
      </main>
    </div>
  );
}

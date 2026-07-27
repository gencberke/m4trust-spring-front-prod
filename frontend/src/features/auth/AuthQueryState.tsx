import type { UseQueryResult } from "@tanstack/react-query";

import type { CurrentUser } from "./authApi";
import styles from "./Auth.module.css";

interface AuthQueryStateProps {
  query: UseQueryResult<CurrentUser | null>;
}

export function AuthQueryState({ query }: AuthQueryStateProps) {
  const hasError = query.isError;

  return (
    <div className="app-shell">
      <header className="site-header">
        <span className="brand">M4Trust</span>
      </header>
      <main className={styles.statePage} aria-busy={!hasError}>
        <section
          className={styles.stateCard}
          role={hasError ? "alert" : "status"}
          aria-live={hasError ? "assertive" : "polite"}
        >
          <span className={styles.stateEyebrow}>Güvenli oturum</span>
          <h1>{hasError ? "Bağlantı kurulamadı" : "Oturum doğrulanıyor"}</h1>
          <p>
            {hasError
              ? "Oturum bilgisi doğrulanamadı. Bağlantınızı kontrol edip yeniden deneyin."
              : "Güvenli alan hazırlanırken kısa bir süre bekleyin."}
          </p>
          {hasError ? (
            <button
              className={`primary-button ${styles.stateAction}`}
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

import { ReadinessStatus } from "../features/readiness/ReadinessStatus";

export function PlatformStatusPage() {
  return (
    <div className="app-shell">
      <header className="site-header">
        <span className="brand" aria-label="M4Trust ana sayfa">
          M4Trust
        </span>
      </header>

      <main className="main-content">
        <div className="content-column">
          <div className="page-introduction">
            <h1>Platform durumu</h1>
            <p>Yerel geliştirme ortamının Core API bağlantısını izleyin.</p>
          </div>

          <ReadinessStatus />
        </div>
      </main>

      <footer className="site-footer">
        <p>Bu ekran yalnızca platform bağlantısını doğrular.</p>
      </footer>
    </div>
  );
}

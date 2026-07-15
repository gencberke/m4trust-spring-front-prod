import { StatusMark, type ReadinessViewState } from "./StatusMark";
import { useReadiness } from "./useReadiness";

interface StatusContent {
  title: string;
  detail: string;
}

const STATUS_CONTENT: Record<ReadinessViewState, StatusContent> = {
  loading: {
    title: "Bağlantı kontrol ediliyor",
    detail: "Spring Core API hazır olana kadar kısa bir süre bekleyin.",
  },
  healthy: {
    title: "Bağlantı sağlıklı",
    detail: "Spring Core API istek kabul etmeye hazır.",
  },
  error: {
    title: "Core API bağlantısı kurulamadı",
    detail: "Core API erişilemiyor. Bağlantıyı doğrulayıp yeniden deneyin.",
  },
};

export function ReadinessStatus() {
  const readiness = useReadiness();
  const state: ReadinessViewState = readiness.isPending || readiness.isFetching
    ? "loading"
    : readiness.isSuccess
      ? "healthy"
      : "error";
  const content = STATUS_CONTENT[state];

  return (
    <section className="status-region" aria-labelledby="core-api-label">
      <div
        className="status-panel"
        data-state={state}
        role={state === "error" ? "alert" : "status"}
        aria-live={state === "error" ? "assertive" : "polite"}
        aria-atomic="true"
        aria-busy={state === "loading"}
      >
        <StatusMark state={state} />
        <div className="status-copy">
          <h2 id="core-api-label">Core API</h2>
          <p className="status-title">{content.title}</p>
          <p className="status-detail">{content.detail}</p>
        </div>
      </div>

      <button
        className="retry-button"
        type="button"
        onClick={() => void readiness.refetch()}
        disabled={state === "loading"}
      >
        Yeniden kontrol et
      </button>
    </section>
  );
}

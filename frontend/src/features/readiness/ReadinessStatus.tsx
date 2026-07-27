import { StatusMark, type ReadinessViewState } from "./StatusMark";
import { useReadiness } from "./useReadiness";
import styles from "./Readiness.module.css";

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
  const state: ReadinessViewState =
    readiness.isPending || readiness.isFetching
      ? "loading"
      : readiness.isSuccess
        ? "healthy"
        : "error";
  const content = STATUS_CONTENT[state];

  return (
    <section className={styles.region} aria-labelledby="core-api-label">
      <div
        className={styles.panel}
        data-state={state}
        role={state === "error" ? "alert" : "status"}
        aria-live={state === "error" ? "assertive" : "polite"}
        aria-atomic="true"
        aria-busy={state === "loading"}
      >
        <StatusMark state={state} />
        <div className={styles.copy}>
          <h2 id="core-api-label">Core API</h2>
          <p className={styles.title}>{content.title}</p>
          <p className={styles.detail}>{content.detail}</p>
        </div>
      </div>

      <button
        className={styles.retry}
        type="button"
        onClick={() => void readiness.refetch()}
        disabled={state === "loading"}
      >
        Yeniden kontrol et
      </button>
    </section>
  );
}

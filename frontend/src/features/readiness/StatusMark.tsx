export type ReadinessViewState = "loading" | "healthy" | "error";

interface StatusMarkProps {
  state: ReadinessViewState;
}

export function StatusMark({ state }: StatusMarkProps) {
  return (
    <span
      className={`${styles.mark} ${state === "loading" ? styles.loading : ""}`}
      aria-hidden="true"
    >
      <svg viewBox="0 0 96 96" focusable="false">
        <circle className={styles.circle} cx="48" cy="48" r="44" />
        {state === "healthy" ? (
          <path className={styles.symbol} d="m29 49 12 12 27-29" />
        ) : state === "loading" ? (
          <path className={styles.symbol} d="M48 22a26 26 0 0 1 26 26" />
        ) : (
          <>
            <path className={styles.symbol} d="M48 27v27" />
            <circle className={styles.dot} cx="48" cy="68" r="2.5" />
          </>
        )}
      </svg>
    </span>
  );
}
import styles from "./Readiness.module.css";

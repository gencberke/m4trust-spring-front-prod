export type ReadinessViewState = "loading" | "healthy" | "error";

interface StatusMarkProps {
  state: ReadinessViewState;
}

export function StatusMark({ state }: StatusMarkProps) {
  return (
    <span className={`status-mark status-mark--${state}`} aria-hidden="true">
      <svg viewBox="0 0 96 96" focusable="false">
        <circle className="status-mark__circle" cx="48" cy="48" r="44" />
        {state === "healthy" ? (
          <path className="status-mark__symbol" d="m29 49 12 12 27-29" />
        ) : state === "loading" ? (
          <path className="status-mark__symbol" d="M48 22a26 26 0 0 1 26 26" />
        ) : (
          <>
            <path className="status-mark__symbol" d="M48 27v27" />
            <circle className="status-mark__dot" cx="48" cy="68" r="2.5" />
          </>
        )}
      </svg>
    </span>
  );
}

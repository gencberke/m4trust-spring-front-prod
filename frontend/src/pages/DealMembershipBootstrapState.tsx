import { getOrganizationErrorMessage } from "../features/organization/organizationErrors";

interface DealMembershipBootstrapStateProps {
  error?: unknown;
  isFetching: boolean;
  onRetry: () => void;
}

export function DealMembershipBootstrapState({
  error,
  isFetching,
  onRetry,
}: DealMembershipBootstrapStateProps) {
  return (
    <main className="workspace-main deal-workspace">
      <div className="workspace-column">
        <section
          className="workspace-panel workspace-state"
          role={error ? "alert" : "status"}
        >
          {error ? (
            <>
              <h2>Kuruluş bilgisi alınamadı</h2>
              <p>{getOrganizationErrorMessage(error)}</p>
              <button
                className="secondary-button"
                type="button"
                onClick={onRetry}
                disabled={isFetching}
              >
                {isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
              </button>
            </>
          ) : (
            <>
              <span className="loading-line" aria-hidden="true" />
              <h2>Kuruluş bilgisi yükleniyor</h2>
              <p>Anlaşma çalışma alanı için üyelikleriniz hazırlanıyor.</p>
            </>
          )}
        </section>
      </div>
    </main>
  );
}

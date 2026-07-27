import { decimalFromMinor, formatDate, StatusBadge } from "@/shared";
import type { RatificationPackageDetail } from "../ratificationApi";
import { getRatificationErrorMessage } from "../ratificationErrors";
import styles from "../Ratification.module.css";
import {
  EVIDENCE_POLICY_LABELS,
  effectiveEvidencePolicy,
  packageStatusLabel,
} from "../ratificationPresentation";
import { HexValue } from "./CurrentPackage";

export function PackageHistory({
  items,
  loading,
  error,
  currentPackageId,
  onRetry,
}: {
  items: RatificationPackageDetail[];
  loading: boolean;
  error: unknown;
  currentPackageId: string | undefined;
  onRetry: () => void;
}) {
  const historical = items.filter((item) => item.id !== currentPackageId);
  return (
    <section className={styles.ratificationHistory}>
      <h3>Onay geçmişi</h3>
      {loading ? (
        <p className="inline-state" role="status">
          <span className="loading-line" aria-hidden="true" />
          Geçmiş yükleniyor…
        </p>
      ) : null}
      {error ? (
        <div className="form-alert panel-alert" role="alert">
          <p>{getRatificationErrorMessage(error)}</p>
          <button className="secondary-button" type="button" onClick={onRetry}>
            Yeniden dene
          </button>
        </div>
      ) : null}
      {!loading && !error && historical.length === 0 ? (
        <p className="muted-copy">
          Önceki veya reddedilmiş bir onay kaydı yok.
        </p>
      ) : null}
      {historical.length ? (
        <ul>
          {historical.map((item) => (
            <li key={item.id}>
              <div>
                <strong>
                  Sürüm {item.version} ·{" "}
                  {decimalFromMinor(item.snapshot.commercialTerms.amountMinor)}{" "}
                  {item.snapshot.commercialTerms.currency}
                </strong>
                <span>
                  {formatDate(item.createdAt)} ·{" "}
                  {
                    EVIDENCE_POLICY_LABELS[
                      effectiveEvidencePolicy(item.snapshot)
                    ]
                  }{" "}
                  · <HexValue value={item.contentHash} label="hash" />
                </span>
              </div>
              <StatusBadge
                domain="ratification"
                status={item.status}
                label={packageStatusLabel(item.status)}
              />
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

import { formatDate } from "@/shared";
import type { components } from "../../../generated/core-api";
import styles from "../Review.module.css";
import { CATEGORY_LABELS, formatValue, legalBasis } from "../reviewTypes";

export function CurrentRuleSet({
  summary,
  onOpen,
}: {
  summary: components["schemas"]["RuleSetVersionSummary"];
  onOpen: () => void;
}) {
  return (
    <div className={styles.reviewCurrent}>
      <div>
        <strong>Güncel ticari koşullar: sürüm {summary.version}</strong>
        <span>
          {summary.ruleCount} kural ·{" "}
          {formatDate(summary.createdAt, "medium-short")}
        </span>
      </div>
      <button className="secondary-button" type="button" onClick={onOpen}>
        İçeriği aç
      </button>
    </div>
  );
}

export function RuleSetHistory({
  history,
  onOpen,
}: {
  history: components["schemas"]["RuleSetVersionSummary"][];
  onOpen: (id: string) => void;
}) {
  return (
    <section className={styles.reviewHistory}>
      <h3>Ticari koşul geçmişi</h3>
      {history.length ? (
        <ul>
          {history.map((item) => (
            <li key={item.id}>
              <div>
                <strong>v{item.version}</strong>
                <span>
                  {item.ruleCount} kural ·{" "}
                  {formatDate(item.createdAt, "medium-short")}
                </span>
              </div>
              <button
                className="text-button"
                type="button"
                onClick={() => onOpen(item.id)}
              >
                İçeriği aç
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted-copy">Henüz kabul edilmiş sürüm yok.</p>
      )}
    </section>
  );
}

export function RuleSetDetail({
  version,
  loading,
  onClose,
}: {
  version?: components["schemas"]["RuleSetVersion"];
  loading: boolean;
  onClose: () => void;
}) {
  return (
    <section className={styles.reviewDetail} aria-live="polite">
      <div>
        <h3>Kaydedilmiş sürüm detayı</h3>
        <button className="text-button" type="button" onClick={onClose}>
          Kapat
        </button>
      </div>
      {loading ? (
        <p>Yükleniyor…</p>
      ) : version ? (
        <>
          <p>
            Sürüm {version.version} · {version.ruleCount} kural
          </p>
          <ul>
            {version.rules.map((rule) => (
              <li key={rule.ruleReference}>
                <strong>{rule.title}</strong>
                <span>
                  {CATEGORY_LABELS[rule.category]} ·{" "}
                  {formatValue(rule.structuredValue)}
                </span>
                <small>{legalBasis(rule)}</small>
              </li>
            ))}
          </ul>
          {version.excludedRuleReferences.length ? (
            <p>Hariç tutulan çıkarılmış kurallar bulunuyor.</p>
          ) : null}
        </>
      ) : (
        <p className="form-alert" role="alert">
          Sürüm detayı alınamadı.
        </p>
      )}
    </section>
  );
}

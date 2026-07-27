import type { ReviewFieldError } from "../reviewErrors";
import styles from "../Review.module.css";
import {
  CATEGORY_LABELS,
  formatValue,
  legalBasis,
  type Draft,
  type ExtractedRule,
} from "../reviewTypes";
import { RuleEditor } from "./RuleEditor";

export function ReviewRule({
  rule,
  draft,
  editable,
  errors,
  onChange,
  onRestore,
}: {
  rule: ExtractedRule;
  draft: Draft;
  editable: boolean;
  errors: ReviewFieldError;
  onChange: (next: Partial<Draft>) => void;
  onRestore: () => void;
}) {
  return (
    <li
      className={`${styles.reviewRule} ${draft.excluded ? styles.excluded : ""}`}
    >
      <div className={styles.reviewRuleMeta}>
        <span className={styles.categoryBadge}>
          {CATEGORY_LABELS[rule.category]}
        </span>
        <span>Güven %{Math.round(rule.confidence * 100)}</span>
      </div>
      {editable ? (
        <RuleEditor
          draft={draft}
          errors={errors}
          idPrefix={`review-rule-${rule.ruleReference}`}
          onChange={onChange}
        />
      ) : (
        <>
          <h3>{rule.title}</h3>
          <p>{rule.description}</p>
          <p>
            <strong>Değer:</strong> {formatValue(rule.structuredValue)}
          </p>
        </>
      )}
      <p className={styles.sourceCopy}>
        Kaynak:{" "}
        {rule.sourceReferences
          .map((source) => `sayfa ${source.page}`)
          .join(", ") || "belirtilmedi"}
      </p>
      <p className={styles.reviewLegalBasis}>{legalBasis(rule)}</p>
      {editable ? (
        <div className={styles.reviewRuleActions}>
          <label className={styles.reviewExclude}>
            <input
              type="checkbox"
              checked={draft.excluded}
              onChange={(event) => onChange({ excluded: event.target.checked })}
            />{" "}
            Bu kuralı hariç tut
          </label>
          <button className="text-button" type="button" onClick={onRestore}>
            Orijinale geri yükle
          </button>
        </div>
      ) : null}
    </li>
  );
}

export function ManualRule({
  draft,
  errors,
  idPrefix,
  onChange,
  onRemove,
}: {
  draft: Draft;
  errors: ReviewFieldError;
  idPrefix: string;
  onChange: (next: Partial<Draft>) => void;
  onRemove: () => void;
}) {
  return (
    <div className={styles.manualRule}>
      <RuleEditor
        draft={draft}
        errors={errors}
        idPrefix={idPrefix}
        onChange={onChange}
      />
      <p className={styles.reviewLegalBasis}>
        Manuel eklenen kuralların hukuki dayanağı yoktur; kaynak işareti
        MANUALLY_ADDED olur.
      </p>
      <button className="text-button" type="button" onClick={onRemove}>
        Kuralı kaldır
      </button>
    </div>
  );
}

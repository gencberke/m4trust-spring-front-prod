/**
 * Shared status badge.
 *
 * Eleven near-identical `<span className="X-status-badge" data-status={...}>`
 * patterns lived across eight files. This component owns the markup while the
 * per-domain CSS class is kept intact on purpose: the same `data-status` value
 * carries different colours in different domains (`PENDING` is amber under
 * funding but blue under ratification), so collapsing every domain onto one
 * class would make those rules fight each other. Domain-specific classes stay
 * until the Faz 3 CSS Modules pass gives each feature its own scope.
 *
 * The component never resolves labels — each feature owns its own enum
 * vocabulary and passes the resolved text in.
 */

export type StatusBadgeDomain =
  | "deal"
  | "analysis"
  | "ratification"
  | "ratificationApproval"
  | "casework"
  | "funding"
  | "fundingUnit"
  | "fundingOperation"
  | "settlement"
  | "settlementOperation"
  | "fulfillment"
  | "evidence";

const CLASS_BY_DOMAIN: Record<StatusBadgeDomain, string> = {
  deal: "status-badge",
  analysis: "analysis-status-badge",
  ratification: "ratification-status-badge",
  ratificationApproval: "ratification-approval-badge",
  casework: "casework-status-badge",
  funding: "funding-status-badge",
  fundingUnit: "funding-unit-status-badge",
  fundingOperation: "funding-operation-status-badge",
  settlement: "settlement-status-badge",
  settlementOperation: "settlement-operation-status-badge",
  fulfillment: "fulfillment-status-badge",
  evidence: "evidence-status-badge",
};

export interface StatusBadgeProps {
  domain: StatusBadgeDomain;
  /** Raw enum value; drives the `[data-status]` colour rules. */
  status: string;
  /** Display text, already resolved by the calling feature. */
  label: string;
  /** Evidence submissions only: dims a cancelled pending upload. */
  cancelled?: boolean;
}

export function StatusBadge({
  domain,
  status,
  label,
  cancelled,
}: StatusBadgeProps) {
  return (
    <span
      className={`${styles.statusBadge} ${CLASS_BY_DOMAIN[domain]}`}
      data-status={status}
      data-cancelled={cancelled ? "true" : undefined}
    >
      {label}
    </span>
  );
}
import styles from "./StatusBadge.module.css";

import { decimalFromMinor, formatDate, StatusBadge } from "@/shared";
import { useState } from "react";
import type { RatificationPackageDetail } from "../ratificationApi";
import styles from "../Ratification.module.css";
import {
  EVIDENCE_POLICY_LABELS,
  effectiveEvidencePolicy,
  formatStructuredValue,
  hasDisputeWindow,
  packageStatusLabel,
  truncateHex,
} from "../ratificationPresentation";

export function CurrentPackage({
  pkg,
  mayApprove,
  mayReject,
  onApprove,
  onReject,
}: {
  pkg: RatificationPackageDetail;
  mayApprove: boolean;
  mayReject: boolean;
  onApprove: () => void;
  onReject: () => void;
}) {
  const snapshot = pkg.snapshot;
  return (
    <div className={styles.ratificationCurrent}>
      <div className={styles.ratificationCurrentHeading}>
        <StatusBadge
          domain="ratification"
          status={pkg.status}
          label={packageStatusLabel(pkg.status)}
        />
        <span className="muted-copy">
          Sürüm {pkg.version} · Oluşturuldu: {formatDate(pkg.createdAt)}
        </span>
      </div>
      <dl className={styles.ratificationSummaryList}>
        <div>
          <dt>Anlaşma</dt>
          <dd>
            {snapshot.dealReference} · {snapshot.dealTitle}
          </dd>
        </div>
        <div>
          <dt>Alıcı</dt>
          <dd>{snapshot.buyer.legalName}</dd>
        </div>
        <div>
          <dt>Satıcı</dt>
          <dd>{snapshot.seller.legalName}</dd>
        </div>
        <div>
          <dt>İtiraz penceresi</dt>
          <dd>
            {hasDisputeWindow(snapshot) ? (
              <>{snapshot.disputeWindowDays} gün</>
            ) : (
              <span className="muted-copy">
                Kapanışa uygun değil (eski şema)
              </span>
            )}
          </dd>
        </div>
        <div>
          <dt>Teslimat kanıtı politikası</dt>
          <dd>{EVIDENCE_POLICY_LABELS[effectiveEvidencePolicy(snapshot)]}</dd>
        </div>
        <div>
          <dt>Sözleşme bedeli</dt>
          <dd>
            <strong>
              {decimalFromMinor(snapshot.commercialTerms.amountMinor)}{" "}
              {snapshot.commercialTerms.currency}
            </strong>
          </dd>
        </div>
      </dl>
      <details>
        <summary>Teknik ayrıntılar</summary>
        <p>
          Koşul sürümü: {snapshot.ruleSet.version} · Belge sürümü:{" "}
          {snapshot.document.objectVersion}
        </p>
        <p>
          Belge özeti:{" "}
          <HexValue value={snapshot.document.sha256} label="sha256" />
        </p>
        <p>
          İçerik özeti: <HexValue value={pkg.contentHash} label="hash" />
        </p>
      </details>
      <details className={styles.ratificationRulesDetail}>
        <summary>Koşul ayrıntıları ({snapshot.ruleSet.rules.length})</summary>
        <ul>
          {snapshot.ruleSet.rules.map((rule) => (
            <li key={rule.ruleReference}>
              <strong>{rule.title}</strong>
              <span>{formatStructuredValue(rule.structuredValue)}</span>
            </li>
          ))}
        </ul>
      </details>
      <h3>Taraf onayları</h3>
      <ul className={styles.ratificationApprovalList}>
        {pkg.approvals.map((approval) => (
          <li key={approval.legalEntityId}>
            <div>
              <strong>{approval.legalName}</strong>
              <span>
                {approval.status === "APPROVED" && approval.approvedAt
                  ? `Onaylandı: ${formatDate(approval.approvedAt)}`
                  : "Onay bekliyor"}
              </span>
              {approval.approverUserId ? (
                <span className="muted-copy">
                  Onaylayan kullanıcı: {approval.approverUserId.slice(0, 8)}…
                </span>
              ) : null}
            </div>
            <StatusBadge
              domain="ratificationApproval"
              status={approval.status}
              label={approval.status === "APPROVED" ? "Onaylandı" : "Bekliyor"}
            />
          </li>
        ))}
      </ul>
      {mayApprove || mayReject ? (
        <div className={styles.ratificationCurrentActions}>
          {mayApprove ? (
            <button
              className="primary-button"
              type="button"
              onClick={onApprove}
            >
              Ticari koşulları onayla
            </button>
          ) : null}
          {mayReject ? (
            <button className="danger-button" type="button" onClick={onReject}>
              Koşulları reddet
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

export function HexValue({ value, label }: { value: string; label: string }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <span className={styles.ratificationHex}>
      <code>{expanded ? value : truncateHex(value)}</code>
      <button
        className="text-button"
        type="button"
        onClick={() => setExpanded((current) => !current)}
      >
        {expanded ? "Kısalt" : `Tam ${label} göster`}
      </button>
    </span>
  );
}

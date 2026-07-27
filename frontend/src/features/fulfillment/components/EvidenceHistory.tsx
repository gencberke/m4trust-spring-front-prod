import { formatDate, StatusBadge } from "@/shared";
import styles from "../Fulfillment.module.css";

import { EvidenceVideoAnalysisPanel } from "../../videoAnalysis";
import type { EvidenceSubmission } from "../fulfillmentApi";
import { EvidenceSummary } from "./EvidenceSummary";
import {
  evidenceEventAt,
  evidenceStatusLabel,
  isAnalysisEligibleEvidence,
  isCancelledPending,
} from "./fulfillmentPresentation";

interface Props {
  legalEntityId: string;
  dealId: string;
  history: readonly EvidenceSubmission[];
  currentEvidence: EvidenceSubmission | null | undefined;
  readOnly: boolean;
  downloadError: string | undefined;
  downloadingId: string | undefined;
  onDownload: (submission: EvidenceSubmission) => void;
}

export function EvidenceHistory({
  legalEntityId,
  dealId,
  history,
  currentEvidence,
  readOnly,
  downloadError,
  downloadingId,
  onDownload,
}: Props) {
  if (history.length === 0) return null;
  return (
    <div className="evidence-history">
      <h4>Teslimat kanıtı geçmişi</h4>
      {downloadError && (
        <p className="form-alert" role="alert">
          {downloadError}
        </p>
      )}
      <ol className={styles.evidenceTimeline}>
        {history.map((submission) => (
          <li
            key={submission.id}
            className={styles.evidenceTimelineItem}
            data-status={submission.status}
            data-cancelled={isCancelledPending(submission) ? "true" : undefined}
          >
            <div className={styles.evidenceTimelineMeta}>
              <time dateTime={evidenceEventAt(submission)}>
                {formatDate(evidenceEventAt(submission))}
              </time>
              <StatusBadge
                domain="evidence"
                status={submission.status}
                label={evidenceStatusLabel(submission)}
                cancelled={isCancelledPending(submission)}
              />
            </div>
            <EvidenceSummary submission={submission} />
            {isAnalysisEligibleEvidence(submission) &&
              submission.id !== currentEvidence?.id && (
                <EvidenceVideoAnalysisPanel
                  legalEntityId={legalEntityId}
                  dealId={dealId}
                  evidenceSubmissionId={submission.id}
                  expectedEvidenceVersion={submission.version}
                  readOnly={readOnly}
                />
              )}
            {submission.availableActions.canDownload && (
              <button
                type="button"
                className="text-button"
                onClick={() => onDownload(submission)}
                disabled={downloadingId === submission.id}
              >
                {downloadingId === submission.id ? "Hazırlanıyor…" : "İndir"}
              </button>
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}

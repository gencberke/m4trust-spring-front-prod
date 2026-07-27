import { EvidenceVideoAnalysisPanel } from "../../videoAnalysis";
import type { EvidenceSubmission } from "../fulfillmentApi";
import { EvidenceSummary } from "./EvidenceSummary";
import { isAnalysisEligibleEvidence } from "./fulfillmentPresentation";

interface Props {
  legalEntityId: string;
  dealId: string;
  evidence: EvidenceSubmission;
  readOnly: boolean;
  canAccept: boolean;
  isAccepting: boolean;
  isRejecting: boolean;
  reviewError: string | undefined;
  rejectionReason: string;
  onRejectionReasonChange: (value: string) => void;
  onAccept: (evidence: EvidenceSubmission) => void;
  onReject: (evidence: EvidenceSubmission) => void;
}

export function EvidenceReviewSection({
  legalEntityId,
  dealId,
  evidence,
  readOnly,
  canAccept,
  isAccepting,
  isRejecting,
  reviewError,
  rejectionReason,
  onRejectionReasonChange,
  onAccept,
  onReject,
}: Props) {
  if (evidence.status !== "SUBMITTED") return null;
  return (
    <div className="current-evidence">
      <h4>Mevcut teslimat kanıtı</h4>
      <EvidenceSummary submission={evidence} />
      {isAnalysisEligibleEvidence(evidence) && (
        <EvidenceVideoAnalysisPanel
          legalEntityId={legalEntityId}
          dealId={dealId}
          evidenceSubmissionId={evidence.id}
          expectedEvidenceVersion={evidence.version}
          readOnly={readOnly}
        />
      )}
      {canAccept && (
        <div className="review-actions">
          {reviewError && (
            <p className="form-alert" role="alert">
              {reviewError}
            </p>
          )}
          <button
            type="button"
            className="primary-button"
            onClick={() => onAccept(evidence)}
            disabled={isAccepting || isRejecting}
          >
            {isAccepting ? "Onaylanıyor…" : "Teslimat kanıtını onayla"}
          </button>
          <div className="reject-form">
            <label htmlFor="rejection-reason">Reddetme sebebi</label>
            <textarea
              id="rejection-reason"
              rows={3}
              maxLength={1000}
              value={rejectionReason}
              onChange={(event) => onRejectionReasonChange(event.target.value)}
              disabled={isRejecting}
            />
            <button
              type="button"
              className="danger-button"
              onClick={() => onReject(evidence)}
              disabled={
                isRejecting ||
                isAccepting ||
                rejectionReason.trim().length === 0
              }
            >
              {isRejecting ? "Reddediliyor…" : "Teslimat kanıtını reddet"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

import { formatDate } from "@/shared";

import type { EvidenceSubmission } from "../fulfillmentApi";
import {
  evidenceStatusLabel,
  evidenceTypeLabel,
  formatBytes,
} from "./fulfillmentPresentation";

export function EvidenceSummary({
  submission,
}: {
  submission: EvidenceSubmission;
}) {
  return (
    <div className="evidence-summary">
      <div>
        <strong>{submission.fileName}</strong>
        <span className="evidence-type">
          {" "}
          ({evidenceTypeLabel(submission.evidenceType)})
        </span>
      </div>
      <div className="evidence-meta">
        <span>Durum: {evidenceStatusLabel(submission)}</span>
        {" | "}
        <span>Dosya türü: {submission.mediaType}</span>
        {" | "}
        <span>
          Boyut:{" "}
          {formatBytes(
            submission.status === "PENDING_UPLOAD"
              ? submission.clientSizeBytes
              : submission.verifiedSizeBytes,
          )}
        </span>
        {" | "}
        <span>Oluşturulma: {formatDate(submission.createdAt)}</span>
        {submission.status === "PENDING_UPLOAD" && submission.cancelledAt && (
          <span> | İptal: {formatDate(submission.cancelledAt)}</span>
        )}
        {submission.status === "SUBMITTED" && submission.submittedAt && (
          <span> | Sunulma: {formatDate(submission.submittedAt)}</span>
        )}
        {submission.status === "ACCEPTED" && submission.acceptedAt && (
          <span> | Onay: {formatDate(submission.acceptedAt)}</span>
        )}
        {submission.status === "REJECTED" && (
          <>
            {submission.rejectedAt && (
              <span> | Red: {formatDate(submission.rejectedAt)}</span>
            )}
            <p className="rejection-reason">
              Sebep: {submission.rejectionReason}
            </p>
          </>
        )}
      </div>
    </div>
  );
}

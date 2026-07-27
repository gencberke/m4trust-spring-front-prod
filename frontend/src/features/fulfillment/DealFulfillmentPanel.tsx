import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";

import { StatusBadge } from "@/shared";
import styles from "./Fulfillment.module.css";

import type { DealDetail } from "../deals";
import { createEvidenceDownloadLink } from "./fulfillmentApi";
import type { EvidenceSubmission } from "./fulfillmentApi";
import {
  getFulfillmentErrorMessage,
  isFulfillmentNotFound,
} from "./fulfillmentErrors";
import {
  fulfillmentDetailQueryKey,
  fulfillmentDetailQueryOptions,
  useFulfillmentMutations,
} from "./fulfillmentQueries";
import { EvidenceHistory } from "./components/EvidenceHistory";
import { EvidenceReviewSection } from "./components/EvidenceReviewSection";
import { EvidenceUploadSection } from "./components/EvidenceUploadSection";
import {
  EVIDENCE_POLICY_LABELS,
  deriveTurnBanner,
  fulfillmentStatusLabel,
  sortEvidenceChronologically,
} from "./components/fulfillmentPresentation";

interface Props {
  deal: DealDetail;
  legalEntityId: string;
  readOnly?: boolean;
  onNavigateToClosure?: () => void;
}

export function DealFulfillmentPanel({
  deal,
  legalEntityId,
  readOnly = false,
  onNavigateToClosure,
}: Props) {
  const queryClient = useQueryClient();
  const startKeyRef = useRef<string | undefined>(undefined);
  const reviewKeyRef = useRef<string | undefined>(undefined);
  const acceptWithoutEvidenceKeyRef = useRef<string | undefined>(undefined);

  const fulfillmentId = deal.fulfillment?.fulfillmentId;
  const hasFulfillment = Boolean(fulfillmentId);

  const fulfillmentQuery = useQuery(
    fulfillmentDetailQueryOptions(legalEntityId, deal.id, hasFulfillment),
  );
  const fulfillment = fulfillmentQuery.data;

  const [notice, setNotice] = useState<string>();
  const [feedbackNotice, setFeedbackNotice] = useState<string>();
  const [downloadError, setDownloadError] = useState<string>();
  const [downloadingId, setDownloadingId] = useState<string>();
  const [rejectionReason, setRejectionReason] = useState("");
  const [reviewError, setReviewError] = useState<string>();

  const {
    startMutation,
    acceptMutation,
    rejectMutation,
    acceptWithoutEvidenceMutation,
  } = useFulfillmentMutations({
    legalEntityId,
    deal,
    fulfillment,
    startKeyRef,
    reviewKeyRef,
    acceptWithoutEvidenceKeyRef,
    onStartSuccess: () => {
      setNotice(undefined);
      setFeedbackNotice(undefined);
    },
    onStartError: setNotice,
    onFeedbackNotice: setFeedbackNotice,
    onReviewError: setReviewError,
    onClearRejectionReason: () => setRejectionReason(""),
  });

  async function handleDownload(submission: EvidenceSubmission) {
    setDownloadError(undefined);
    setDownloadingId(submission.id);
    try {
      const link = await createEvidenceDownloadLink(
        legalEntityId,
        deal.id,
        submission.id,
      );
      window.open(link.downloadUrl, "_blank", "noopener,noreferrer");
    } catch (error) {
      setDownloadError(getFulfillmentErrorMessage(error));
    } finally {
      setDownloadingId(undefined);
    }
  }

  function handleAccept(evidence: EvidenceSubmission) {
    acceptMutation.mutate(evidence);
  }

  function handleReject(evidence: EvidenceSubmission) {
    const reason = rejectionReason.trim();
    if (reason.length === 0 || reason.length > 1000) {
      setReviewError("Reddetme sebebi 1–1000 karakter arasında olmalıdır.");
      return;
    }
    rejectMutation.mutate({ evidence, reason });
  }

  const canStart = !readOnly && deal.availableActions.canStartFulfillment;
  const canUpload =
    !readOnly && (fulfillment?.milestone.availableActions.canUpload ?? false);
  const canAccept = !readOnly && deal.availableActions.canAcceptEvidence;
  const canAcceptWithoutEvidence =
    !readOnly &&
    (deal.availableActions.canAcceptWithoutEvidence === true ||
      fulfillment?.availableActions.canAcceptWithoutEvidence === true);
  const evidencePolicy =
    fulfillment?.evidencePolicy ?? deal.fulfillment?.evidencePolicy;
  const turnBanner = deriveTurnBanner({
    status: fulfillment?.status ?? (hasFulfillment ? undefined : "NOT_STARTED"),
    evidencePolicy,
    canStart: Boolean(canStart),
    canUpload: Boolean(canUpload),
    canAccept: Boolean(canAccept),
    canReject: !readOnly && Boolean(deal.availableActions.canRejectEvidence),
    canAcceptWithoutEvidence: Boolean(canAcceptWithoutEvidence),
  });
  const chronologicalHistory = fulfillment
    ? sortEvidenceChronologically(fulfillment.history)
    : [];

  if (fulfillmentQuery.isLoading) {
    return (
      <section className="panel" aria-live="polite">
        <h2>Teslimat</h2>
        <p>Yükleniyor…</p>
      </section>
    );
  }

  if (
    fulfillmentQuery.isError &&
    !isFulfillmentNotFound(fulfillmentQuery.error)
  ) {
    return (
      <section className="panel" aria-live="polite">
        <h2>Teslimat</h2>
        <p className="form-alert" role="alert">
          {getFulfillmentErrorMessage(fulfillmentQuery.error)}
        </p>
        <button
          type="button"
          className="primary-button"
          onClick={() =>
            void queryClient.invalidateQueries({
              queryKey: fulfillmentDetailQueryKey(legalEntityId, deal.id),
            })
          }
        >
          Yeniden dene
        </button>
      </section>
    );
  }

  const currentEvidence = fulfillment?.currentEvidence;

  return (
    <section className={`panel ${styles.fulfillmentPanel}`} aria-live="polite">
      <h2>Teslimat</h2>

      {turnBanner ? (
        <p className={styles.fulfillmentTurnBanner} role="status">
          {turnBanner}
        </p>
      ) : null}

      {feedbackNotice ? (
        <p
          className={`success-notice ${styles.fulfillmentFeedback}`}
          role="status"
        >
          {onNavigateToClosure ? (
            <button
              type="button"
              className={`text-button ${styles.fulfillmentClosureNav}`}
              onClick={onNavigateToClosure}
            >
              {feedbackNotice}
            </button>
          ) : (
            feedbackNotice
          )}
        </p>
      ) : null}

      {readOnly ? (
        <p className="muted-copy">
          Bu aşama salt okunurdur; teslimat geçmişi aşağıda korunur.
        </p>
      ) : null}

      {!fulfillment && canStart && (
        <div className="fulfillment-start">
          <p>Teslimat henüz başlatılmadı.</p>
          {notice && (
            <p className="form-alert" role="alert">
              {notice}
            </p>
          )}
          <button
            type="button"
            className="primary-button"
            onClick={() => startMutation.mutate()}
            disabled={startMutation.isPending}
          >
            {startMutation.isPending ? "Başlatılıyor…" : "Teslimatı başlat"}
          </button>
        </div>
      )}

      {!fulfillment && !canStart && <p>Teslimat henüz başlatılmadı.</p>}

      {fulfillment && (
        <div className="fulfillment-detail">
          <div className="fulfillment-status">
            <strong>Durum:</strong>{" "}
            <StatusBadge
              domain="fulfillment"
              status={fulfillment.status}
              label={fulfillmentStatusLabel(fulfillment.status)}
            />
          </div>

          {fulfillment.evidencePolicy ? (
            <p className="muted-copy">
              Kanıt politikası:{" "}
              {EVIDENCE_POLICY_LABELS[fulfillment.evidencePolicy]}
            </p>
          ) : null}

          {fulfillment.evidencePolicy === "NOT_REQUIRED" &&
          fulfillment.status === "IN_PROGRESS" &&
          !canAcceptWithoutEvidence ? (
            <p className="muted-copy">
              Bu teslimatta dosya yüklenmez; alıcı kuruluş yöneticisinin
              kanıtsız onayı bekleniyor.
            </p>
          ) : null}

          {canAcceptWithoutEvidence ? (
            <div className="fulfillment-accept-without-evidence">
              {reviewError ? (
                <p className="form-alert" role="alert">
                  {reviewError}
                </p>
              ) : null}
              <button
                type="button"
                className="primary-button"
                onClick={() => acceptWithoutEvidenceMutation.mutate()}
                disabled={acceptWithoutEvidenceMutation.isPending}
              >
                {acceptWithoutEvidenceMutation.isPending
                  ? "Kabul ediliyor…"
                  : "Teslimatı kanıtsız kabul et"}
              </button>
            </div>
          ) : null}

          <EvidenceUploadSection
            legalEntityId={legalEntityId}
            deal={deal}
            fulfillment={fulfillment}
            canUpload={Boolean(canUpload)}
            readOnly={readOnly}
            onFeedbackNotice={setFeedbackNotice}
          />

          {currentEvidence && (
            <EvidenceReviewSection
              legalEntityId={legalEntityId}
              dealId={deal.id}
              evidence={currentEvidence}
              readOnly={readOnly}
              canAccept={Boolean(canAccept)}
              isAccepting={acceptMutation.isPending}
              isRejecting={rejectMutation.isPending}
              reviewError={reviewError}
              rejectionReason={rejectionReason}
              onRejectionReasonChange={setRejectionReason}
              onAccept={handleAccept}
              onReject={handleReject}
            />
          )}

          <EvidenceHistory
            legalEntityId={legalEntityId}
            dealId={deal.id}
            history={chronologicalHistory}
            currentEvidence={currentEvidence}
            readOnly={readOnly}
            downloadError={downloadError}
            downloadingId={downloadingId}
            onDownload={handleDownload}
          />
        </div>
      )}
    </section>
  );
}

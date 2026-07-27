import { useQuery } from "@tanstack/react-query";
import { useRef, useState } from "react";
import type { DealDetail } from "../deals";
import { CreatePackageForm } from "./components/CreatePackageForm";
import { CurrentPackage } from "./components/CurrentPackage";
import { PackageHistory } from "./components/PackageHistory";
import { RatificationConfirmations } from "./components/RatificationConfirmations";
import { getRatificationErrorMessage } from "./ratificationErrors";
import styles from "./Ratification.module.css";
import { extractMoneySuggestions } from "./ratificationPresentation";
import {
  ratificationPackageHistoryQueryOptions,
  ratificationRuleSetVersionQueryOptions,
  useRatificationMutations,
} from "./ratificationQueries";

interface Props {
  deal: DealDetail;
  legalEntityId: string;
}

export function DealRatificationPanel({ deal, legalEntityId }: Props) {
  const [notice, setNotice] = useState<string>();
  const [confirmAction, setConfirmAction] = useState<"approve" | "reject">();
  const createKeyRef = useRef<string | undefined>(undefined);
  const approveKeyRef = useRef<string | undefined>(undefined);
  const rejectKeyRef = useRef<string | undefined>(undefined);
  const historyQuery = useQuery(
    ratificationPackageHistoryQueryOptions(legalEntityId, deal.id),
  );
  const ruleSetSummary = deal.currentRuleSet;
  const mayCreate = deal.availableActions.canCreateRatificationPackage === true;
  const ruleSetVersionQuery = useQuery(
    ratificationRuleSetVersionQueryOptions(
      legalEntityId,
      deal.id,
      ruleSetSummary?.id,
      Boolean(ruleSetSummary) && mayCreate,
    ),
  );
  const { createMutation, approveMutation, rejectMutation } =
    useRatificationMutations({
      legalEntityId,
      deal,
      createKeyRef,
      approveKeyRef,
      rejectKeyRef,
      onNotice: setNotice,
      onConfirmationClose: () => setConfirmAction(undefined),
    });
  const ratification = deal.ratification ?? null;
  if (!ratification)
    return (
      <section
        className={`workspace-panel ${styles.ratificationPanel}`}
        aria-labelledby="ratification-title"
      >
        <div className="panel-heading">
          <span className="section-kicker">Onay</span>
          <h2 id="ratification-title">Ticari koşullar</h2>
        </div>
        <p className="muted-copy">
          Bu anlaşma için ticari onay bilgisi şu anda sunulmuyor; bölüm salt
          okunur kabul edilir.
        </p>
      </section>
    );
  const currentPackage = ratification.currentPackage;
  const mayApprove =
    deal.availableActions.canApproveRatification === true &&
    currentPackage?.availableActions.canApprove === true;
  const mayReject =
    deal.availableActions.canRejectRatification === true &&
    currentPackage?.availableActions.canReject === true;
  const moneySuggestions = extractMoneySuggestions(
    ruleSetVersionQuery.data?.rules,
  );
  return (
    <section
      className={`workspace-panel ${styles.ratificationPanel}`}
      aria-labelledby="ratification-title"
    >
      <div className="panel-heading">
        <span className="section-kicker">Onay</span>
        <h2 id="ratification-title">Ticari koşulları onaylayın</h2>
        <p>
          Her iki taraf, aynı ticari koşulları şirketi adına onaylamalıdır. Bu
          onay bağlayıcıdır.
        </p>
      </div>
      <div
        className={styles.ratificationReadiness}
        role="status"
        data-ready={ratification.readiness === "READY"}
      >
        <strong>
          {ratification.readiness === "READY" ? "Hazır" : "Hazır değil"}
        </strong>
        <span>
          {ratification.readiness === "READY"
            ? "Taraflar ve güncel sözleşme belgesi hazır."
            : "Onaya sunmak için taraflar ve güncel sözleşme belgesi gereklidir."}
        </span>
      </div>
      {notice ? (
        <p className="success-notice workspace-notice" role="status">
          {notice}
        </p>
      ) : null}
      {approveMutation.isError ? (
        <p className="form-alert workspace-notice" role="alert">
          {getRatificationErrorMessage(approveMutation.error)}
        </p>
      ) : null}
      {rejectMutation.isError ? (
        <p className="form-alert workspace-notice" role="alert">
          {getRatificationErrorMessage(rejectMutation.error)}
        </p>
      ) : null}
      {currentPackage ? (
        <CurrentPackage
          pkg={currentPackage}
          mayApprove={mayApprove}
          mayReject={mayReject}
          onApprove={() => setConfirmAction("approve")}
          onReject={() => setConfirmAction("reject")}
        />
      ) : (
        <p className="muted-copy">
          Ticari koşullar henüz tarafların onayına sunulmadı.
        </p>
      )}
      <RatificationConfirmations
        action={confirmAction}
        pkg={currentPackage}
        approvePending={approveMutation.isPending}
        rejectPending={rejectMutation.isPending}
        onCancel={() => setConfirmAction(undefined)}
        onApprove={() =>
          currentPackage && approveMutation.mutate(currentPackage)
        }
        onReject={() => currentPackage && rejectMutation.mutate(currentPackage)}
      />
      {mayCreate ? (
        <CreatePackageForm
          hasCurrentPackage={Boolean(currentPackage)}
          ready={ratification.readiness === "READY"}
          suggestions={moneySuggestions}
          suggestionsLoading={
            ruleSetVersionQuery.isPending && Boolean(ruleSetSummary)
          }
          pending={createMutation.isPending}
          error={createMutation.error}
          onSubmit={(commercialTerms, disputeWindowDays, evidencePolicy) => {
            setNotice(undefined);
            createMutation.mutate({
              commercialTerms,
              disputeWindowDays,
              evidencePolicy,
            });
          }}
        />
      ) : null}
      <PackageHistory
        items={historyQuery.data?.items ?? []}
        loading={historyQuery.isPending}
        error={historyQuery.error}
        currentPackageId={currentPackage?.id}
        onRetry={() => void historyQuery.refetch()}
      />
    </section>
  );
}

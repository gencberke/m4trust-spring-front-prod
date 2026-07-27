import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Link } from "react-router";

import { DealContractAnalysis } from "../../analysis";
import { DealReviewWorkspace } from "../../review";
import {
  cancelDeal,
  updateDeal,
  updateDealParties,
  type DealDetail,
  type UpdateDealPartiesRequest,
  type UpdateDealRequest,
} from "../dealApi";
import { getDealErrorMessage, isDealNotFound } from "../dealErrors";
import { dealDetailQueryKey, dealDetailQueryOptions } from "../dealQueries";
import { DealDocumentManagement } from "../../documents";
import { DealFundingPanel } from "../../funding";
import {
  DealFulfillmentPanel,
  FULFILLMENT_LIVE_POLL_STATUSES,
  FULFILLMENT_POLL_INTERVAL_MS,
} from "../../fulfillment";
import { DealCaseworkPanel } from "../../casework";
import { DealInvitationManagement } from "../../invitations";
import { DealRatificationPanel } from "../../ratification";
import { DealSettlementPanel } from "../../settlement";
import { isInvalidLegalEntitySelection } from "../../organization";
import { DealMembershipBootstrapState } from "./DealMembershipBootstrapState";
import {
  workspaceAreaForLifecycle,
  type WorkspaceArea,
} from "./dealWorkspaceStages";
import { TerminalStatePanel } from "./TerminalStatePanel";
import { DealPartiesForm, EditDealForm } from "./DealEditForms";
import { DealDetailHeader } from "./DealDetailHeader";
import { DealActionsPanel } from "./DealActionsPanel";
import { DealInformationRail } from "./DealInformationRail";
import styles from "../DealDetail.module.css";
import type { DealWorkspaceContext } from "../types";

export function DealDetailWorkspace({
  dealId,
  selectedLegalEntityId,
  selectedMembership,
  selectionNotice,
  clearInvalidSelection,
  membershipsPending,
  membershipsError,
  membershipsFetching,
  refetchMemberships,
}: DealWorkspaceContext & { dealId: string | undefined }) {
  const queryClient = useQueryClient();
  const [updateNotice, setUpdateNotice] = useState<string>();
  const [cancelConfirmationOpen, setCancelConfirmationOpen] = useState(false);
  const [activeArea, setActiveArea] = useState<WorkspaceArea>("agreement");
  const [activeLifecycle, setActiveLifecycle] = useState<
    DealDetail["lifecycle"] | undefined
  >();
  const detailQuery = useQuery({
    ...dealDetailQueryOptions(selectedLegalEntityId, dealId),
    refetchInterval: (query) => {
      const status = query.state.data?.fulfillment?.status;
      return status && FULFILLMENT_LIVE_POLL_STATUSES.has(status)
        ? FULFILLMENT_POLL_INTERVAL_MS
        : false;
    },
  });
  const invalidSelection = isInvalidLegalEntitySelection(detailQuery.error);
  const settlementReadOnly = detailQuery.data?.lifecycle === "SETTLEMENT";
  const closureTerminalLifecycle = ["SETTLEMENT", "COMPLETED"].includes(
    detailQuery.data?.lifecycle ?? "",
  );
  const agreementTerminalLifecycle = ["CANCELLED", "ARCHIVED"].includes(
    detailQuery.data?.lifecycle ?? "",
  );

  if (
    detailQuery.data?.lifecycle &&
    detailQuery.data.lifecycle !== activeLifecycle
  ) {
    setActiveLifecycle(detailQuery.data.lifecycle);
    setActiveArea(workspaceAreaForLifecycle(detailQuery.data.lifecycle));
  }

  useEffect(() => {
    if (invalidSelection) {
      clearInvalidSelection();
    }
  }, [clearInvalidSelection, invalidSelection]);

  const updateMutation = useMutation({
    mutationFn: (request: UpdateDealRequest) =>
      updateDeal(selectedLegalEntityId!, dealId!, request),
    onSuccess: async (updated) => {
      queryClient.setQueryData(
        dealDetailQueryKey(selectedLegalEntityId!, updated.id),
        updated,
      );
      setUpdateNotice(
        `Değişiklikler sürüm ${updated.version} olarak kaydedildi.`,
      );
      await queryClient.invalidateQueries({
        queryKey: ["deals", selectedLegalEntityId, "list"],
      });
    },
    onError: (error) => {
      if (isInvalidLegalEntitySelection(error)) {
        clearInvalidSelection();
      }
    },
  });

  const partiesMutation = useMutation({
    mutationFn: (request: UpdateDealPartiesRequest) =>
      updateDealParties(selectedLegalEntityId!, dealId!, request),
    onSuccess: async (updated) => {
      queryClient.setQueryData(
        dealDetailQueryKey(selectedLegalEntityId!, updated.id),
        updated,
      );
      setUpdateNotice(`Taraflar sürüm ${updated.version} olarak kaydedildi.`);
      await queryClient.invalidateQueries({
        queryKey: ["deals", selectedLegalEntityId, "list"],
      });
    },
    onError: (error) => {
      if (isInvalidLegalEntitySelection(error)) {
        clearInvalidSelection();
      }
    },
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelDeal(selectedLegalEntityId!, dealId!),
    onSuccess: async (cancelled) => {
      queryClient.setQueryData(
        dealDetailQueryKey(selectedLegalEntityId!, cancelled.id),
        cancelled,
      );
      setCancelConfirmationOpen(false);
      setUpdateNotice(`${cancelled.reference} iptal edildi.`);
      await queryClient.invalidateQueries({
        queryKey: ["deals", selectedLegalEntityId, "list"],
      });
    },
    onError: (error) => {
      if (isInvalidLegalEntitySelection(error)) {
        clearInvalidSelection();
      }
    },
  });

  if (membershipsPending) {
    return (
      <DealMembershipBootstrapState
        isFetching={membershipsFetching}
        onRetry={refetchMemberships}
      />
    );
  }

  if (membershipsError) {
    return (
      <DealMembershipBootstrapState
        error={membershipsError}
        isFetching={membershipsFetching}
        onRetry={refetchMemberships}
      />
    );
  }

  if (!selectedLegalEntityId || !selectedMembership) {
    return (
      <main className={`workspace-main ${styles.workspace}`}>
        <div className="workspace-column">
          <span className="section-kicker">Anlaşma</span>
          <h1>Aktif kuruluşu seçin.</h1>
          <p className="workspace-lead">
            Bu anlaşmayı görüntülemek için üst menüden bir kuruluş seçin.
          </p>
          {selectionNotice ? (
            <p className="form-notice workspace-notice" role="status">
              {selectionNotice}
            </p>
          ) : null}
          <Link className="primary-link-button" to="/app">
            Organizasyonlara dön
          </Link>
        </div>
      </main>
    );
  }

  if (detailQuery.isPending) {
    return (
      <main className={`workspace-main ${styles.workspace}`}>
        <div className="workspace-column">
          <section className="workspace-panel workspace-state" role="status">
            <span className="loading-line" aria-hidden="true" />
            <h2>Anlaşma yükleniyor</h2>
            <p>Güncel bilgiler ve izin verilen işlemler alınıyor.</p>
          </section>
        </div>
      </main>
    );
  }

  if (isDealNotFound(detailQuery.error)) {
    return (
      <main className={`workspace-main ${styles.workspace}`}>
        <div className="workspace-column">
          <section className="workspace-panel workspace-state" role="alert">
            <span className="section-kicker">Bilgi ifşa edilmedi</span>
            <h2>Anlaşma bulunamadı</h2>
            <p>
              Kayıt mevcut olmayabilir veya {selectedMembership.legalName} bu
              anlaşmanın katılımcısı değildir.
            </p>
            <Link className="primary-link-button" to="/app/deals">
              Anlaşmalara dön
            </Link>
          </section>
        </div>
      </main>
    );
  }

  if (detailQuery.isError && !invalidSelection) {
    return (
      <main className={`workspace-main ${styles.workspace}`}>
        <div className="workspace-column">
          <section className="workspace-panel workspace-state" role="alert">
            <h2>Anlaşma bilgileri alınamadı</h2>
            <p>{getDealErrorMessage(detailQuery.error)}</p>
            <button
              className="secondary-button"
              type="button"
              onClick={() => void detailQuery.refetch()}
              disabled={detailQuery.isFetching}
            >
              {detailQuery.isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
            </button>
          </section>
        </div>
      </main>
    );
  }

  const deal = detailQuery.data;
  if (!deal) {
    return null;
  }

  return (
    <main className={`workspace-main ${styles.workspace}`}>
      <div className="workspace-column">
        <Link className={styles.backLink} to="/app/deals">
          ← Anlaşmalara dön
        </Link>
        <DealDetailHeader
          deal={deal}
          activeArea={activeArea}
          onAreaChange={setActiveArea}
        />

        {updateNotice ? (
          <p className="success-notice workspace-notice" role="status">
            {updateNotice}
          </p>
        ) : null}
        {cancelMutation.isError ? (
          <p className="form-alert workspace-notice" role="alert">
            {getDealErrorMessage(cancelMutation.error)}
          </p>
        ) : null}

        {activeArea === "agreement" ? (
          <>
            {agreementTerminalLifecycle ? (
              <TerminalStatePanel deal={deal} settlementReadOnly={false} />
            ) : null}
            <div className={styles.agreementLayout}>
              <DealInformationRail deal={deal} />

              <div className={styles.agreementMain}>
                <section className={`workspace-panel ${styles.partiesPanel}`}>
                  <div className="panel-heading">
                    <span className="section-kicker">Taraflar</span>
                    <h2>Alıcı ve satıcı atamaları</h2>
                    <p>Taraf rolleri tek başına ticari onay anlamına gelmez.</p>
                  </div>
                  <dl className={styles.partyAssignmentList}>
                    <div>
                      <dt>Alıcı</dt>
                      <dd>{deal.buyer?.legalName ?? "Atanmamış"}</dd>
                    </div>
                    <div>
                      <dt>Satıcı</dt>
                      <dd>{deal.seller?.legalName ?? "Atanmamış"}</dd>
                    </div>
                  </dl>
                  {deal.buyer && deal.seller ? (
                    <p className={styles.partyReadinessNotice} role="status">
                      Taraflar ratification için hazır.
                    </p>
                  ) : null}
                  {deal.availableActions.canManageParties ? (
                    <DealPartiesForm
                      key={`${deal.id}:${deal.version}`}
                      deal={deal}
                      error={partiesMutation.error}
                      isPending={partiesMutation.isPending}
                      isReloading={detailQuery.isFetching}
                      onReload={() => {
                        partiesMutation.reset();
                        setUpdateNotice(undefined);
                        void detailQuery.refetch();
                      }}
                      onSubmit={(request) => {
                        setUpdateNotice(undefined);
                        partiesMutation.mutate(request);
                      }}
                    />
                  ) : null}
                </section>

                <div className={styles.editLayout}>
                  {deal.availableActions.canUpdate ? (
                    <EditDealForm
                      key={`${deal.id}:${deal.version}`}
                      deal={deal}
                      error={updateMutation.error}
                      isPending={updateMutation.isPending}
                      isReloading={detailQuery.isFetching}
                      onReload={() => {
                        updateMutation.reset();
                        setUpdateNotice(undefined);
                        void detailQuery.refetch();
                      }}
                      onSubmit={(request) => {
                        setUpdateNotice(undefined);
                        updateMutation.mutate(request);
                      }}
                    />
                  ) : (
                    <section className="workspace-panel">
                      <div className="panel-heading">
                        <span className="section-kicker">Salt okunur</span>
                        <h2>Düzenleme kapalı</h2>
                        <p>
                          Sunucunun güncel izni bu anlaşma için düzenlemeye izin
                          vermiyor.
                        </p>
                      </div>
                    </section>
                  )}

                  <DealActionsPanel
                    canCancel={deal.availableActions.canCancel}
                    confirmationOpen={cancelConfirmationOpen}
                    isPending={cancelMutation.isPending}
                    onOpen={() => setCancelConfirmationOpen(true)}
                    onClose={() => setCancelConfirmationOpen(false)}
                    onConfirm={() => cancelMutation.mutate()}
                  />
                </div>

                <DealDocumentManagement
                  deal={deal}
                  legalEntityId={selectedLegalEntityId}
                />
                <DealInvitationManagement
                  deal={deal}
                  legalEntityId={selectedLegalEntityId}
                />
              </div>
            </div>
          </>
        ) : null}

        {activeArea === "review" ? (
          <>
            <DealContractAnalysis
              deal={deal}
              legalEntityId={selectedLegalEntityId}
            />
            <DealReviewWorkspace
              deal={deal}
              legalEntityId={selectedLegalEntityId}
            />
          </>
        ) : null}

        {activeArea === "approval" ? (
          <DealRatificationPanel
            deal={deal}
            legalEntityId={selectedLegalEntityId}
          />
        ) : null}

        {activeArea === "payment" ? (
          <DealFundingPanel
            key={`${selectedLegalEntityId}:${deal.id}`}
            deal={deal}
            legalEntityId={selectedLegalEntityId}
          />
        ) : null}

        {activeArea === "delivery" ? (
          <>
            <DealFulfillmentPanel
              deal={deal}
              legalEntityId={selectedLegalEntityId}
              readOnly={settlementReadOnly}
              onNavigateToClosure={() => setActiveArea("closure")}
            />
            {!settlementReadOnly ? (
              <DealCaseworkPanel
                deal={deal}
                legalEntityId={selectedLegalEntityId}
              />
            ) : null}
          </>
        ) : null}

        {activeArea === "closure" ? (
          <>
            {closureTerminalLifecycle ? (
              <TerminalStatePanel
                deal={deal}
                settlementReadOnly={settlementReadOnly}
              />
            ) : null}
            <DealSettlementPanel
              deal={deal}
              legalEntityId={selectedLegalEntityId}
            />
          </>
        ) : null}
      </div>
    </main>
  );
}

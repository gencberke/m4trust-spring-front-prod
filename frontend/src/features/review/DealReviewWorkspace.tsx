import { useQuery } from "@tanstack/react-query";
import { useMemo, useRef, useState } from "react";
import type { DealDetail } from "../deals/dealApi";
import { ReviewConfirmationDialog } from "./components/ReviewConfirmationDialog";
import { ManualRule, ReviewRule } from "./components/ReviewRules";
import {
  CurrentRuleSet,
  RuleSetDetail,
  RuleSetHistory,
} from "./components/RuleSetVersions";
import { getReviewErrorMessage, getReviewFieldErrors } from "./reviewErrors";
import styles from "./Review.module.css";
import {
  reviewQueryOptions,
  ruleSetHistoryQueryOptions,
  ruleSetVersionQueryOptions,
  useAcceptExtractionReview,
} from "./reviewQueries";
import {
  changed,
  emptyAddedDraft,
  getDecisionErrors,
  initialDraft,
  toRuleValue,
  type Draft,
} from "./reviewTypes";

interface Props {
  deal: DealDetail;
  legalEntityId: string;
}

export function DealReviewWorkspace({ deal, legalEntityId }: Props) {
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [added, setAdded] = useState<Draft[]>([]);
  const [confirmationOpen, setConfirmationOpen] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState<string>();
  const requestKey = useRef<string | undefined>(undefined);
  const reviewEnabled = deal.analysis.status === "REVIEW_REQUIRED";
  const mayReview = deal.availableActions.canReviewExtraction === true;
  const review = useQuery(
    reviewQueryOptions(legalEntityId, deal.id, reviewEnabled),
  );
  const history = useQuery(ruleSetHistoryQueryOptions(legalEntityId, deal.id));
  const version = useQuery(
    ruleSetVersionQueryOptions(legalEntityId, deal.id, selectedVersion),
  );
  const accept = useAcceptExtractionReview({
    legalEntityId,
    deal,
    review: review.data,
    drafts,
    added,
    requestKeyRef: requestKey,
    onConfirmationClose: () => setConfirmationOpen(false),
  });
  const allDrafts = useMemo(
    () =>
      review.data?.rules.map((rule) => ({
        rule,
        draft: drafts[rule.ruleReference] ?? initialDraft(rule),
      })) ?? [],
    [drafts, review.data],
  );
  const invalid =
    allDrafts.some(
      ({ rule, draft }) =>
        !draft.excluded && changed(rule, draft) && !toRuleValue(draft.value),
    ) ||
    added.some(
      (draft) =>
        !draft.title.trim() ||
        !draft.description.trim() ||
        !toRuleValue(draft.value),
    );
  const fieldErrors = getReviewFieldErrors(accept.error);
  const markRequestChanged = () => {
    requestKey.current = undefined;
    accept.reset();
  };
  const update = (reference: string, next: Partial<Draft>) => {
    markRequestChanged();
    setDrafts((current) => ({
      ...current,
      [reference]: {
        ...(current[reference] ??
          initialDraft(
            review.data!.rules.find(
              (rule) => rule.ruleReference === reference,
            )!,
          )),
        ...next,
      },
    }));
  };
  const updateAdded = (index: number, next: Partial<Draft>) => {
    markRequestChanged();
    setAdded((items) =>
      items.map((item, itemIndex) =>
        itemIndex === index ? { ...item, ...next } : item,
      ),
    );
  };
  const restore = (reference: string) => {
    markRequestChanged();
    setDrafts((current) => {
      const { [reference]: _discarded, ...remaining } = current;
      return remaining;
    });
  };

  return (
    <section
      className={`workspace-panel ${styles.reviewPanel}`}
      aria-labelledby="review-title"
    >
      <div className="panel-heading">
        <span className="section-kicker">Kural incelemesi</span>
        <h2 id="review-title">Ticari koşullar incelemesi</h2>
        <p>
          Hukuki dayanak ve güven bilgisi danışma amaçlıdır. Bu işlem, sonraki
          ticari onay için koşul temelini oluşturur; sözleşme veya ticari onay
          değildir.
        </p>
      </div>
      {deal.currentRuleSet ? (
        <CurrentRuleSet
          summary={deal.currentRuleSet}
          onOpen={() => setSelectedVersion(deal.currentRuleSet!.id)}
        />
      ) : (
        <p className={styles.reviewCurrentEmpty}>
          Güncel kabul edilmiş ticari koşul sürümü yok. Geçmiş sürümler yalnızca
          okunabilir.
        </p>
      )}
      {reviewEnabled && review.isPending ? (
        <p className="inline-state" role="status">
          İnceleme yükleniyor…
        </p>
      ) : null}
      {reviewEnabled && review.isError ? (
        <p className="form-alert" role="alert">
          İnceleme verisi alınamadı.{" "}
          <button
            className="text-button"
            type="button"
            onClick={() => void review.refetch()}
          >
            Yeniden dene
          </button>
        </p>
      ) : null}
      {accept.isError ? (
        <p className="form-alert" role="alert">
          {getReviewErrorMessage(accept.error)}
        </p>
      ) : null}
      {reviewEnabled && review.data ? (
        <div>
          <div className={styles.reviewNotice} role="status">
            <strong>
              {mayReview
                ? "Değişikliklerinizi toplu olarak gözden geçirin."
                : "Bu katılımcı için inceleme salt okunurdur."}
            </strong>
            <span>{review.data.rules.length} çıkarılmış kural</span>
          </div>
          <ul className={styles.reviewRuleList}>
            {allDrafts.map(({ rule, draft }, index) => (
              <ReviewRule
                key={rule.ruleReference}
                rule={rule}
                draft={draft}
                editable={mayReview}
                errors={getDecisionErrors(fieldErrors, index)}
                onChange={(next) => update(rule.ruleReference, next)}
                onRestore={() => restore(rule.ruleReference)}
              />
            ))}
          </ul>
          {mayReview ? (
            <>
              <div className={styles.reviewAdded}>
                <h3>Elle eklenen kurallar</h3>
                {added.map((draft, index) => (
                  <ManualRule
                    key={index}
                    draft={draft}
                    errors={getDecisionErrors(
                      fieldErrors,
                      review.data.rules.length + index,
                    )}
                    idPrefix={`review-manual-${index}`}
                    onChange={(next) => updateAdded(index, next)}
                    onRemove={() => {
                      markRequestChanged();
                      setAdded((items) =>
                        items.filter((_, itemIndex) => itemIndex !== index),
                      );
                    }}
                  />
                ))}
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => {
                    markRequestChanged();
                    setAdded((items) => [...items, emptyAddedDraft()]);
                  }}
                >
                  Kural ekle
                </button>
              </div>
              <button
                className="primary-button"
                type="button"
                disabled={accept.isPending || invalid}
                onClick={() => setConfirmationOpen(true)}
              >
                İncelemeyi kabul etmeye devam et
              </button>
            </>
          ) : null}
        </div>
      ) : null}
      <RuleSetHistory
        history={history.data?.items ?? []}
        onOpen={setSelectedVersion}
      />
      {selectedVersion ? (
        <RuleSetDetail
          version={version.data}
          loading={version.isPending}
          onClose={() => setSelectedVersion(undefined)}
        />
      ) : null}
      {confirmationOpen ? (
        <ReviewConfirmationDialog
          pending={accept.isPending}
          onCancel={() => setConfirmationOpen(false)}
          onConfirm={() => accept.mutate()}
        />
      ) : null}
    </section>
  );
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { DealDetail } from "../deals/dealApi";
import { acceptExtractionReview, getExtractionReview, listRuleSets } from "./reviewApi";

interface Props { deal: DealDetail; legalEntityId: string; }

/** A deliberately conservative workspace: missing action/status is always read-only. */
export function DealReviewWorkspace({ deal, legalEntityId }: Props) {
  const client = useQueryClient();
  const mayReview = deal.availableActions.canReviewExtraction === true;
  const review = useQuery({ queryKey: ["review", legalEntityId, deal.id], queryFn: ({ signal }) => getExtractionReview(legalEntityId, deal.id, signal), enabled: mayReview });
  const history = useQuery({ queryKey: ["rule-sets", legalEntityId, deal.id], queryFn: ({ signal }) => listRuleSets(legalEntityId, deal.id, signal) });
  const accept = useMutation({
    mutationFn: () => {
      if (!review.data) throw new Error("Review is unavailable");
      return acceptExtractionReview(legalEntityId, deal.id, { analysisId: review.data.analysisId, expectedVersion: deal.version, decisions: review.data.rules.map((rule) => ({ decision: "KEPT" as const, ruleReference: rule.ruleReference })) });
    },
    onSuccess: () => void Promise.all([client.invalidateQueries({ queryKey: ["review", legalEntityId, deal.id] }), client.invalidateQueries({ queryKey: ["rule-sets", legalEntityId, deal.id] }), client.invalidateQueries({ queryKey: ["deals", legalEntityId, "detail", deal.id] })]),
  });
  return <section className="workspace-panel">
    <div className="panel-heading"><span className="section-kicker">Kural incelemesi</span><h2>Immutable kural seti</h2><p>Legal basis yalnızca danışma bilgisidir; bu işlem sözleşme veya ratification onayı değildir.</p></div>
    {mayReview && review.data ? <><ul>{review.data.rules.map((rule) => <li key={rule.ruleReference}><strong>{rule.title}</strong> — {rule.description}</li>)}</ul><button className="primary-button" type="button" disabled={accept.isPending} onClick={() => { if (window.confirm("İncelenen kurallar ratification için temel oluşturur; sözleşme onaylanmaz.")) accept.mutate(); }}>{accept.isPending ? "Kaydediliyor…" : "Kuralları kabul et"}</button></> : <p className="muted-copy">İnceleme aksiyonu sunucu tarafından kullanılabilir değil; ekran salt okunurdur.</p>}
    {accept.isError ? <p className="form-alert" role="alert">İnceleme kaydedilemedi. Güncel Deal sürümünü yenileyin.</p> : null}
    <h3>Geçmiş</h3><ul>{history.data?.items.map((item) => <li key={item.id}>Sürüm {item.version} — {item.ruleCount} kural</li>) ?? <li>Henüz kabul edilmiş sürüm yok.</li>}</ul>
  </section>;
}

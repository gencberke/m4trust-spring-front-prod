import type { DealDetail } from "../dealApi";

export type WorkspaceArea =
  "agreement" | "review" | "approval" | "payment" | "delivery" | "closure";

export const WORKSPACE_AREAS: ReadonlyArray<{
  id: WorkspaceArea;
  label: string;
}> = [
  { id: "agreement", label: "Anlaşma" },
  { id: "review", label: "İnceleme" },
  { id: "approval", label: "Onay" },
  { id: "payment", label: "Ödeme" },
  { id: "delivery", label: "Teslimat" },
  { id: "closure", label: "Kapanış" },
];

export function workspaceAreaForLifecycle(
  lifecycle: DealDetail["lifecycle"],
): WorkspaceArea {
  switch (lifecycle) {
    case "CONTRACT_ANALYSIS":
    case "MANUAL_REVIEW":
      return "review";
    case "RATIFICATION":
      return "approval";
    case "FUNDING":
      return "payment";
    case "FULFILLMENT":
    case "DISPUTE":
      return "delivery";
    case "SETTLEMENT":
    case "COMPLETED":
      return "closure";
    default:
      return "agreement";
  }
}

export function lifecycleLabel(lifecycle: DealDetail["lifecycle"]): string {
  const labels: Record<DealDetail["lifecycle"], string> = {
    DRAFT: "Hazırlık",
    CONTRACT_ANALYSIS: "Belge incelemesi",
    MANUAL_REVIEW: "Manuel inceleme",
    RATIFICATION: "Ticari onay",
    FUNDING: "Ödeme",
    FULFILLMENT: "Teslimat",
    SETTLEMENT: "Kapanış",
    DISPUTE: "Uyuşmazlık",
    COMPLETED: "Tamamlandı",
    CANCELLED: "İptal edildi",
    ARCHIVED: "Arşivde",
  };
  return labels[lifecycle];
}

export function nextTask(deal: DealDetail): string {
  if (deal.lifecycle === "SETTLEMENT") return "Kapanış işlemi izleniyor";
  if (deal.lifecycle === "COMPLETED") {
    return "Anlaşma kapatıldı (simüle kapanış tamamlandı)";
  }
  if (deal.lifecycle === "CANCELLED") return "Anlaşma iptal edildi";
  if (deal.lifecycle === "ARCHIVED") return "Anlaşma arşivde";
  if (deal.availableActions.canCreateDocumentUploadIntent)
    return "Sözleşme belgesini ekleyin";
  if (deal.availableActions.canRequestAnalysis)
    return "Belge analizini başlatın";
  if (deal.availableActions.canReviewExtraction)
    return "Çıkarımı manuel olarak inceleyin";
  if (
    deal.availableActions.canCreateRatificationPackage ||
    deal.availableActions.canApproveRatification
  )
    return "Onay paketini gözden geçirin";
  if (
    deal.availableActions.canCreateFundingPlan ||
    deal.availableActions.canInitiateFunding
  )
    return "Ödemeyi güvenceye alın";
  if (deal.availableActions.canAcceptWithoutEvidence)
    return "Teslimatı kanıtsız kabul edin";
  if (
    deal.availableActions.canStartFulfillment ||
    deal.availableActions.canUploadEvidence ||
    deal.availableActions.canAcceptEvidence
  )
    return "Teslimat kanıtını yönetin";
  if (
    deal.availableActions.canRequestRelease ||
    deal.availableActions.canReconcileRelease
  )
    return "Kapanış adımını tamamlayın";
  return "Güncel durum sunucudan takip ediliyor";
}

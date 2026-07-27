import type { DealDetail } from "../dealApi";
import {
  lifecycleLabel,
  nextTask,
  WORKSPACE_AREAS,
  workspaceAreaForLifecycle,
  type WorkspaceArea,
} from "./dealWorkspaceStages";
import { formatDate, StatusBadge } from "@/shared";
import styles from "../DealDetail.module.css";

interface DealDetailHeaderProps {
  deal: DealDetail;
  activeArea: WorkspaceArea;
  onAreaChange: (area: WorkspaceArea) => void;
}

export function DealDetailHeader({
  deal,
  activeArea,
  onAreaChange,
}: DealDetailHeaderProps) {
  return (
    <>
      <div className={styles.heading}>
        <div>
          <span className={styles.reference}>{deal.reference}</span>
          <h1>{deal.title}</h1>
          <p className="workspace-lead">
            {deal.description ?? "Bu anlaşma için açıklama girilmemiş."}
          </p>
        </div>
        <div className={styles.statusStack}>
          <StatusBadge
            domain="deal"
            status={deal.status}
            label={lifecycleLabel(deal.lifecycle)}
          />
          {deal.status === "COMPLETED" ? (
            <span className={styles.simulationNotice} role="status">
              Demo simülasyonu — gerçek para hareketi yok
            </span>
          ) : null}
          {deal.fulfillment?.status === "COMPLETED" &&
          deal.status === "ACTIVE" ? (
            <span className={styles.closureNote} role="status">
              Teslimat tamamlandı; anlaşma henüz kapanmadı
            </span>
          ) : null}
          <span>{formatDate(deal.updatedAt)} tarihinde güncellendi</span>
        </div>
      </div>
      <section className={styles.stageCard} aria-labelledby="deal-stage-title">
        <div>
          <span className="section-kicker">Mevcut aşama</span>
          <h2 id="deal-stage-title">{nextTask(deal)}</h2>
          <p>
            İlerleme ve izin verilen işlemler sunucunun güncel proje alanına
            göre gösterilir.
          </p>
        </div>
        <ol className={styles.stageList} aria-label="Anlaşma aşamaları">
          {WORKSPACE_AREAS.map((area) => {
            const current =
              area.id === workspaceAreaForLifecycle(deal.lifecycle);
            return (
              <li key={area.id} aria-current={current ? "step" : undefined}>
                <button
                  className={styles.stageButton}
                  data-active={area.id === activeArea}
                  type="button"
                  onClick={() => onAreaChange(area.id)}
                >
                  {area.label}
                </button>
              </li>
            );
          })}
        </ol>
      </section>
    </>
  );
}

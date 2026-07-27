import type { DealDetail } from "../dealApi";
import { nextTask } from "./dealWorkspaceStages";
import styles from "../DealDetail.module.css";

export function TerminalStatePanel({
  deal,
  settlementReadOnly,
}: {
  deal: DealDetail;
  settlementReadOnly: boolean;
}) {
  return (
    <section
      className={`workspace-panel ${styles.terminal}`}
      aria-labelledby="terminal-state-title"
    >
      <span className="section-kicker">Durum</span>
      <h2 id="terminal-state-title">{nextTask(deal)}</h2>
      <p>
        {settlementReadOnly
          ? "Bu aşamada yeni işlem başlatılamaz; mevcut kayıtlar yalnızca görüntülenir."
          : "Bu anlaşma için yeni işlem sunulmuyor; mevcut kayıtlar aşağıda görüntülenir."}
      </p>
    </section>
  );
}

import type { DealDetail } from "../dealApi";
import { formatDate } from "@/shared";
import styles from "../DealDetail.module.css";

export function DealInformationRail({ deal }: { deal: DealDetail }) {
  return (
    <aside className={styles.rail} aria-label="Anlaşma bilgileri">
      <dl className={styles.facts}>
        <div>
          <dt>Referans</dt>
          <dd>{deal.reference}</dd>
        </div>
        <div>
          <dt>Oluşturuldu</dt>
          <dd>{formatDate(deal.createdAt)}</dd>
        </div>
        <div>
          <dt>Güncellendi</dt>
          <dd>{formatDate(deal.updatedAt)}</dd>
        </div>
      </dl>
      <details className={styles.technicalDetails}>
        <summary>Teknik ayrıntılar</summary>
        <dl>
          <div>
            <dt>Durum</dt>
            <dd>{deal.status}</dd>
          </div>
          <div>
            <dt>Lifecycle</dt>
            <dd>{deal.lifecycle}</dd>
          </div>
          <div>
            <dt>Sürüm</dt>
            <dd>{deal.version}</dd>
          </div>
          <div>
            <dt>İzin verilen işlemler</dt>
            <dd>
              {Object.entries(deal.availableActions)
                .filter(([, available]) => available)
                .map(([action]) => action)
                .join(", ") || "Yok"}
            </dd>
          </div>
        </dl>
      </details>
      <section className={`workspace-panel ${styles.participantsPanel}`}>
        <div className="panel-heading">
          <span className="section-kicker">Katılımcılar</span>
          <h2>Katılımcı kuruluşlar</h2>
          <p>Katılım, taraf rolü veya sözleşmesel onay anlamına gelmez.</p>
        </div>
        <ul className={styles.participantList}>
          {deal.participants.map((participant) => (
            <li key={participant.legalEntityId}>
              <div>
                <strong>{participant.legalName}</strong>
                <span>Katılım: {formatDate(participant.joinedAt)}</span>
              </div>
              {participant.partyRoles.length ? (
                <div
                  className={styles.roleBadges}
                  aria-label={`${participant.legalName} taraf rolleri`}
                >
                  {participant.partyRoles.map((role) => (
                    <span className={styles.roleBadge} key={role}>
                      {role}
                    </span>
                  ))}
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      </section>
    </aside>
  );
}

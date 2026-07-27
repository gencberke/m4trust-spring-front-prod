import { ConfirmDialog } from "@/shared";
import styles from "../DealForms.module.css";

interface DealActionsPanelProps {
  canCancel: boolean;
  confirmationOpen: boolean;
  isPending: boolean;
  onOpen: () => void;
  onClose: () => void;
  onConfirm: () => void;
}

export function DealActionsPanel({
  canCancel,
  confirmationOpen,
  isPending,
  onOpen,
  onClose,
  onConfirm,
}: DealActionsPanelProps) {
  return (
    <aside className={`workspace-panel ${styles.actionsPanel}`}>
      <div className="panel-heading">
        <span className="section-kicker">İşlemler</span>
        <h2>Anlaşma işlemleri</h2>
        <p>Kontroller sunucunun güncel izinlerine göre gösterilir.</p>
      </div>
      {canCancel ? (
        <button className="danger-button" type="button" onClick={onOpen}>
          Anlaşmayı iptal et
        </button>
      ) : (
        <p className="muted-copy">
          Bu anlaşma için kullanılabilir iptal işlemi yok.
        </p>
      )}
      {confirmationOpen ? (
        <ConfirmDialog
          titleId="cancel-deal-title"
          title="Anlaşma iptal edilsin mi?"
          confirmVariant="danger"
          confirmLabel="İptali onayla"
          confirmPendingLabel="İptal ediliyor…"
          pending={isPending}
          onCancel={onClose}
          onConfirm={onConfirm}
        >
          <p>Bu işlem anlaşmayı iptal eder ve düzenleme işlemlerini kapatır.</p>
        </ConfirmDialog>
      ) : null}
    </aside>
  );
}

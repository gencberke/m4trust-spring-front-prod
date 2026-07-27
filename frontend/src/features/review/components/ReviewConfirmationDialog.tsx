import { ConfirmDialog } from "@/shared";

export function ReviewConfirmationDialog({
  pending,
  onCancel,
  onConfirm,
}: {
  pending: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <ConfirmDialog
      titleId="review-confirm-title"
      title="İncelemeyi kabul etmek istiyor musunuz?"
      variant="warning"
      confirmLabel="Ticari koşulları oluştur"
      confirmPendingLabel="Kaydediliyor…"
      pending={pending}
      onCancel={onCancel}
      onConfirm={onConfirm}
    >
      <p>
        Bu işlem, sonraki ticari onay için koşul temelini oluşturur. Sözleşme
        onayı veya ticari onay değildir.
      </p>
    </ConfirmDialog>
  );
}

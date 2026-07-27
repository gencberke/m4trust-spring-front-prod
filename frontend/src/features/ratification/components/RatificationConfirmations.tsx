import { ConfirmDialog, decimalFromMinor } from "@/shared";
import type { RatificationPackageDetail } from "../ratificationApi";

export function RatificationConfirmations({
  action,
  pkg,
  approvePending,
  rejectPending,
  onCancel,
  onApprove,
  onReject,
}: {
  action: "approve" | "reject" | undefined;
  pkg: RatificationPackageDetail | null | undefined;
  approvePending: boolean;
  rejectPending: boolean;
  onCancel: () => void;
  onApprove: () => void;
  onReject: () => void;
}) {
  if (!pkg) return null;
  if (action === "approve")
    return (
      <ConfirmDialog
        titleId="ratification-approve-title"
        title="Ticari koşulları onaylıyor musunuz?"
        variant="warning"
        confirmLabel="Bağlayıcı olarak onayla"
        confirmPendingLabel="Onaylanıyor…"
        pending={approvePending}
        onCancel={onCancel}
        onConfirm={onApprove}
      >
        <p>
          Şirketiniz adına bu ticari koşulları bağlayıcı olarak onaylıyorsunuz.
          Bu, ticari bir taahhüttür ve geri alınamaz.
        </p>
        <p>
          Sözleşme bedeli:{" "}
          <strong>
            {decimalFromMinor(pkg.snapshot.commercialTerms.amountMinor)}{" "}
            {pkg.snapshot.commercialTerms.currency}
          </strong>
        </p>
      </ConfirmDialog>
    );
  if (action === "reject")
    return (
      <ConfirmDialog
        titleId="ratification-reject-title"
        title="Ticari koşulları reddediyor musunuz?"
        variant="warning"
        confirmVariant="danger"
        confirmLabel="Koşulları reddet"
        confirmPendingLabel="Reddediliyor…"
        pending={rejectPending}
        onCancel={onCancel}
        onConfirm={onReject}
      >
        <p>
          Şirketiniz adına bu ticari koşulları reddediyorsunuz. Devam etmek için
          yeni koşulların yeniden onaya sunulması gerekir; mevcut onaylar yeni
          koşullara taşınmaz.
        </p>
      </ConfirmDialog>
    );
  return null;
}

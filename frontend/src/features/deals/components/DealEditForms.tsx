import { useState, type FormEvent } from "react";

import type {
  DealDetail,
  UpdateDealPartiesRequest,
  UpdateDealRequest,
} from "../dealApi";
import {
  getDealErrorMessage,
  getDealFieldErrors,
  isDealStaleVersion,
} from "../dealErrors";
import styles from "../DealForms.module.css";

interface EditDealFormProps {
  deal: DealDetail;
  isPending: boolean;
  error: unknown;
  isReloading: boolean;
  onReload: () => void;
  onSubmit: (request: UpdateDealRequest) => void;
}

export function EditDealForm({
  deal,
  isPending,
  error,
  isReloading,
  onReload,
  onSubmit,
}: EditDealFormProps) {
  const [title, setTitle] = useState(deal.title);
  const [description, setDescription] = useState(deal.description ?? "");
  const [clientTitleError, setClientTitleError] = useState<string>();
  const serverErrors = getDealFieldErrors(error);
  const stale = isDealStaleVersion(error);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedTitle = title.trim();
    if (!normalizedTitle) {
      setClientTitleError("Anlaşma başlığını girin.");
      return;
    }
    onSubmit({
      title: normalizedTitle,
      description: description.trim() || null,
      expectedVersion: deal.version,
    });
  }

  return (
    <section className="workspace-panel deal-edit-panel">
      <div className="panel-heading">
        <span className="section-kicker">Temel bilgiler</span>
        <h2>Anlaşmayı düzenle</h2>
        <p>Değişiklikler güncel kayıtla güvenli biçimde kaydedilir.</p>
      </div>
      {error ? (
        <div className="form-alert panel-alert" role="alert">
          <p>{getDealErrorMessage(error)}</p>
          {stale ? (
            <button
              className="secondary-button"
              type="button"
              onClick={onReload}
              disabled={isReloading}
            >
              {isReloading ? "Güncel veri yükleniyor…" : "Güncel veriyi yükle"}
            </button>
          ) : null}
        </div>
      ) : null}
      <form className={`auth-form ${styles.form}`} onSubmit={handleSubmit}>
        <div className="field-group">
          <label htmlFor="edit-deal-title">Başlık</label>
          <input
            id="edit-deal-title"
            value={title}
            onChange={(event) => {
              setTitle(event.target.value);
              setClientTitleError(undefined);
            }}
            maxLength={200}
            required
            aria-invalid={Boolean(clientTitleError ?? serverErrors.title)}
          />
          {(clientTitleError ?? serverErrors.title) ? (
            <span className="field-error">
              {clientTitleError ?? serverErrors.title}
            </span>
          ) : null}
        </div>
        <div className="field-group">
          <label htmlFor="edit-deal-description">Açıklama</label>
          <textarea
            id="edit-deal-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={4000}
            rows={6}
          />
          <span className="field-hint">
            Boş gönderildiğinde açıklama açıkça temizlenir.
          </span>
          {serverErrors.description ? (
            <span className="field-error">{serverErrors.description}</span>
          ) : null}
        </div>
        <button className="primary-button" type="submit" disabled={isPending}>
          {isPending ? "Kaydediliyor…" : "Değişiklikleri kaydet"}
        </button>
      </form>
    </section>
  );
}

interface DealPartiesFormProps {
  deal: DealDetail;
  isPending: boolean;
  error: unknown;
  isReloading: boolean;
  onReload: () => void;
  onSubmit: (request: UpdateDealPartiesRequest) => void;
}

export function DealPartiesForm({
  deal,
  isPending,
  error,
  isReloading,
  onReload,
  onSubmit,
}: DealPartiesFormProps) {
  const [buyerLegalEntityId, setBuyerLegalEntityId] = useState(
    deal.buyer?.legalEntityId ?? "",
  );
  const [sellerLegalEntityId, setSellerLegalEntityId] = useState(
    deal.seller?.legalEntityId ?? "",
  );
  const serverErrors = getDealFieldErrors(error);
  const stale = isDealStaleVersion(error);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({
      buyerLegalEntityId: buyerLegalEntityId || null,
      sellerLegalEntityId: sellerLegalEntityId || null,
      expectedVersion: deal.version,
    });
  }

  return (
    <form
      className={`auth-form ${styles.form} ${styles.managementForm}`}
      onSubmit={handleSubmit}
    >
      {error ? (
        <div className="form-alert" role="alert">
          <p>{getDealErrorMessage(error)}</p>
          {stale ? (
            <button
              className="secondary-button"
              type="button"
              onClick={onReload}
              disabled={isReloading}
            >
              {isReloading ? "Güncel veri yükleniyor…" : "Güncel veriyi yükle"}
            </button>
          ) : null}
        </div>
      ) : null}
      <div className={styles.managementFields}>
        <PartySelect
          id="deal-buyer"
          label="Alıcı"
          value={buyerLegalEntityId}
          error={serverErrors.buyerLegalEntityId}
          deal={deal}
          onChange={setBuyerLegalEntityId}
        />
        <PartySelect
          id="deal-seller"
          label="Satıcı"
          value={sellerLegalEntityId}
          error={serverErrors.sellerLegalEntityId}
          deal={deal}
          onChange={setSellerLegalEntityId}
        />
      </div>
      <button className="primary-button" type="submit" disabled={isPending}>
        {isPending ? "Taraflar kaydediliyor…" : "Tarafları kaydet"}
      </button>
    </form>
  );
}

function PartySelect({
  id,
  label,
  value,
  error,
  deal,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  error: string | undefined;
  deal: DealDetail;
  onChange: (value: string) => void;
}) {
  const errorId = `${id}-error`;
  return (
    <div className="field-group">
      <label htmlFor={id}>{label}</label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
      >
        <option value="">Atanmamış</option>
        {deal.participants.map((participant) => (
          <option
            key={participant.legalEntityId}
            value={participant.legalEntityId}
          >
            {participant.legalName}
          </option>
        ))}
      </select>
      {error ? (
        <span className="field-error" id={errorId}>
          {error}
        </span>
      ) : null}
    </div>
  );
}

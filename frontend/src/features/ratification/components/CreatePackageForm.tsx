import { decimalFromMinor, decimalToMinor } from "@/shared";
import { useState, type FormEvent } from "react";
import type { RatificationCommercialTerms } from "../ratificationApi";
import styles from "../Ratification.module.css";
import {
  getRatificationErrorMessage,
  getRatificationFieldErrors,
  type RatificationFieldError,
} from "../ratificationErrors";
import type {
  EvidencePolicy,
  MoneySuggestion,
} from "../ratificationPresentation";

export function CreatePackageForm({
  hasCurrentPackage,
  ready,
  suggestions,
  suggestionsLoading,
  pending,
  error,
  onSubmit,
}: {
  hasCurrentPackage: boolean;
  ready: boolean;
  suggestions: MoneySuggestion[];
  suggestionsLoading: boolean;
  pending: boolean;
  error: unknown;
  onSubmit: (
    terms: RatificationCommercialTerms,
    disputeWindowDays: number,
    evidencePolicy: EvidencePolicy,
  ) => void;
}) {
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("");
  const [disputeWindowDays, setDisputeWindowDays] = useState("");
  const [evidencePolicy, setEvidencePolicy] =
    useState<EvidencePolicy>("REQUIRED");
  const [clientError, setClientError] = useState<string>();
  const fieldErrors: RatificationFieldError = getRatificationFieldErrors(error);
  const amountError = clientError ?? fieldErrors["commercialTerms.amountMinor"];
  const currencyError = fieldErrors["commercialTerms.currency"];
  const disputeWindowError = fieldErrors.disputeWindowDays;
  const evidencePolicyError = fieldErrors.evidencePolicy;
  const applySuggestion = (suggestion: MoneySuggestion) => {
    setAmount(decimalFromMinor(suggestion.amountMinor));
    setCurrency(suggestion.currency);
    setClientError(undefined);
  };
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const amountMinor = decimalToMinor(amount);
    const normalizedCurrency = currency.trim().toUpperCase();
    if (amountMinor === undefined) {
      setClientError("Geçerli bir pozitif tutar girin.");
      return;
    }
    if (!/^[A-Z]{3}$/.test(normalizedCurrency)) {
      setClientError(
        "Para birimi 3 harfli ISO 4217 kodu olmalıdır (ör. TRY, USD).",
      );
      return;
    }
    const trimmedWindow = disputeWindowDays.trim();
    if (!/^\d+$/.test(trimmedWindow)) {
      setClientError(
        "İtiraz penceresi zorunludur ve tam sayı olmalıdır (0–365).",
      );
      return;
    }
    const parsedWindow = Number(trimmedWindow);
    if (parsedWindow < 0 || parsedWindow > 365) {
      setClientError("İtiraz penceresi 0 ile 365 gün arasında olmalıdır.");
      return;
    }
    setClientError(undefined);
    onSubmit(
      { amountMinor, currency: normalizedCurrency },
      parsedWindow,
      evidencePolicy,
    );
  };
  return (
    <div className={styles.ratificationCreate}>
      <h3>
        {hasCurrentPackage
          ? "Koşulları güncelleyip yeniden onaya sun"
          : "Ticari koşulları onaya sun"}
      </h3>
      {hasCurrentPackage ? (
        <p className="field-hint">
          Farklı bir tutarla yeniden onaya sunmak, önceki koşullar için verilen
          onayları geçersiz kılar; eski onaylar yeni koşullara taşınmaz.
        </p>
      ) : null}
      {!ready ? (
        <p className="field-hint">
          Koşullar şu anda onaya sunulamaz: taraflar ve güncel bir belge
          gereklidir.
        </p>
      ) : null}
      {suggestionsLoading ? (
        <p className="inline-state" role="status">
          <span className="loading-line" aria-hidden="true" />
          MONEY kural önerileri yükleniyor…
        </p>
      ) : null}
      {!suggestionsLoading && suggestions.length ? (
        <div className={styles.ratificationSuggestions}>
          <span className="field-hint">
            Kabul edilmiş sözleşme koşullarındaki tutar önerileri (yalnızca
            öneri; hiçbiri otomatik seçilmez):
          </span>
          <ul>
            {suggestions.map((suggestion) => (
              <li key={suggestion.ruleReference}>
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => applySuggestion(suggestion)}
                >
                  {suggestion.title}: {decimalFromMinor(suggestion.amountMinor)}{" "}
                  {suggestion.currency}
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {error ? (
        <p className="form-alert" role="alert">
          {getRatificationErrorMessage(error)}
        </p>
      ) : null}
      <form
        className={`auth-form ${styles.ratificationCreateForm}`}
        onSubmit={handleSubmit}
      >
        <div className="field-group">
          <label htmlFor="ratification-amount">Sözleşme bedeli (ondalık)</label>
          <input
            id="ratification-amount"
            value={amount}
            onChange={(event) => {
              setAmount(event.target.value);
              setClientError(undefined);
            }}
            placeholder="ör. 125000.00"
            aria-invalid={Boolean(amountError)}
          />
          {amountError ? (
            <span className="field-error">{amountError}</span>
          ) : null}
        </div>
        <div className="field-group">
          <label htmlFor="ratification-currency">Para birimi (ISO 4217)</label>
          <input
            id="ratification-currency"
            value={currency}
            maxLength={3}
            onChange={(event) => {
              setCurrency(event.target.value.toUpperCase());
              setClientError(undefined);
            }}
            placeholder="TRY"
            aria-invalid={Boolean(currencyError)}
          />
          {currencyError ? (
            <span className="field-error">{currencyError}</span>
          ) : null}
        </div>
        <div className="field-group">
          <label htmlFor="ratification-dispute-window">
            İtiraz penceresi (gün)
          </label>
          <input
            id="ratification-dispute-window"
            type="number"
            min={0}
            max={365}
            step={1}
            required
            value={disputeWindowDays}
            onChange={(event) => {
              setDisputeWindowDays(event.target.value);
              setClientError(undefined);
            }}
            placeholder="ör. 7"
            aria-invalid={Boolean(disputeWindowError)}
          />
          <span className="field-hint">
            0 = itiraz penceresi yok; kabulden hemen sonra kapanış mümkün
          </span>
          {disputeWindowError ? (
            <span className="field-error">{disputeWindowError}</span>
          ) : null}
        </div>
        <div className="field-group">
          <label htmlFor="ratification-evidence-policy">
            Teslimat kanıtı politikası
          </label>
          <select
            id="ratification-evidence-policy"
            value={evidencePolicy}
            required
            onChange={(event) => {
              setEvidencePolicy(event.target.value as EvidencePolicy);
              setClientError(undefined);
            }}
            aria-invalid={Boolean(evidencePolicyError)}
          >
            <option value="REQUIRED">Kanıt gerekli</option>
            <option value="NOT_REQUIRED">Kanıt gerekli değil</option>
          </select>
          <span className="field-hint">
            Onaylandıktan sonra değişmez; kanıt gerekmeyen anlaşmalarda alıcı
            yönetici teslimatı dosyasız kabul eder.
          </span>
          {evidencePolicyError ? (
            <span className="field-error">{evidencePolicyError}</span>
          ) : null}
        </div>
        <button
          className="primary-button"
          type="submit"
          disabled={pending || !ready}
        >
          {pending ? "Hazırlanıyor…" : "Tutarı teyit et ve onaya sun"}
        </button>
      </form>
    </div>
  );
}

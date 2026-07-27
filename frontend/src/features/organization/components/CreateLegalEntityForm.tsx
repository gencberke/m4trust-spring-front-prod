import { useState, type FormEvent } from "react";

import type { CreateLegalEntityRequest } from "../organizationApi";
import {
  getLegalEntityFieldErrors,
  getOrganizationErrorMessage,
  type LegalEntityField,
} from "../organizationErrors";
import styles from "../Organization.module.css";

interface CreateLegalEntityFormProps {
  isPending: boolean;
  error: unknown;
  onSubmit: (request: CreateLegalEntityRequest, form: HTMLFormElement) => void;
}

export function CreateLegalEntityForm({
  isPending,
  error,
  onSubmit,
}: CreateLegalEntityFormProps) {
  const [clientErrors, setClientErrors] = useState<
    Partial<Record<LegalEntityField, string>>
  >({});
  const serverErrors = getLegalEntityFieldErrors(error);
  const fieldErrors = { ...serverErrors, ...clientErrors };

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const request: CreateLegalEntityRequest = {
      legalName: String(formData.get("legalName") ?? "").trim(),
      registrationNumber: String(
        formData.get("registrationNumber") ?? "",
      ).trim(),
    };
    const validationErrors: Partial<Record<LegalEntityField, string>> = {};

    if (!request.legalName) {
      validationErrors.legalName = "Kuruluş adını girin.";
    }
    if (!request.registrationNumber) {
      validationErrors.registrationNumber = "Kayıt numarasını girin.";
    }
    setClientErrors(validationErrors);

    if (Object.keys(validationErrors).length === 0) {
      onSubmit(request, form);
    }
  }

  return (
    <section
      className={`workspace-panel ${styles.createPanel}`}
      id="create-legal-entity"
      aria-labelledby="create-entity-title"
    >
      <div className="panel-heading">
        <span className="section-kicker">Yeni çalışma alanı</span>
        <h2 id="create-entity-title">Kuruluş oluşturun</h2>
        <p>Resmî adı ve kurum kayıt numarasını girerek başlayın.</p>
      </div>

      {error ? (
        <p className="form-alert panel-alert" role="alert">
          {getOrganizationErrorMessage(error)}
        </p>
      ) : null}

      <form className={`auth-form ${styles.form}`} onSubmit={handleSubmit}>
        <div className="field-group">
          <label htmlFor="legal-entity-name">Kuruluş adı</label>
          <input
            id="legal-entity-name"
            name="legalName"
            type="text"
            required
            maxLength={200}
            aria-invalid={Boolean(fieldErrors.legalName)}
            aria-describedby={
              fieldErrors.legalName ? "legal-entity-name-error" : undefined
            }
            onChange={() =>
              setClientErrors((current) => ({
                ...current,
                legalName: undefined,
              }))
            }
          />
          {fieldErrors.legalName ? (
            <span className="field-error" id="legal-entity-name-error">
              {fieldErrors.legalName}
            </span>
          ) : null}
        </div>

        <div className="field-group">
          <label htmlFor="legal-entity-registration-number">
            Kayıt numarası
          </label>
          <input
            id="legal-entity-registration-number"
            name="registrationNumber"
            type="text"
            required
            maxLength={100}
            aria-invalid={Boolean(fieldErrors.registrationNumber)}
            aria-describedby={
              fieldErrors.registrationNumber
                ? "legal-entity-registration-number-error"
                : undefined
            }
            onChange={() =>
              setClientErrors((current) => ({
                ...current,
                registrationNumber: undefined,
              }))
            }
          />
          {fieldErrors.registrationNumber ? (
            <span
              className="field-error"
              id="legal-entity-registration-number-error"
            >
              {fieldErrors.registrationNumber}
            </span>
          ) : null}
        </div>

        <button className="primary-button" type="submit" disabled={isPending}>
          {isPending ? "Oluşturuluyor…" : "Kuruluş oluştur"}
        </button>
      </form>
    </section>
  );
}

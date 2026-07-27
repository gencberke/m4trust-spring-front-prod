import type { ReviewFieldError } from "../reviewErrors";
import styles from "../Review.module.css";
import {
  CATEGORIES,
  CATEGORY_LABELS,
  VALUE_TYPES,
  type Category,
  type Draft,
  type ValueDraft,
} from "../reviewTypes";

export function RuleEditor({
  draft,
  errors,
  idPrefix,
  onChange,
}: {
  draft: Draft;
  errors: ReviewFieldError;
  idPrefix: string;
  onChange: (next: Partial<Draft>) => void;
}) {
  const value = draft.value;
  const titleErrorId = `${idPrefix}-title-error`;
  const descriptionErrorId = `${idPrefix}-description-error`;
  const changeValue = (next: Partial<ValueDraft>) =>
    onChange({ value: { ...value, ...next } });
  return (
    <fieldset className={styles.reviewEditor} disabled={draft.excluded}>
      <label>
        Kategori
        <select
          value={draft.category}
          onChange={(event) =>
            onChange({ category: event.target.value as Category })
          }
        >
          {CATEGORIES.map((category) => (
            <option key={category} value={category}>
              {CATEGORY_LABELS[category]}
            </option>
          ))}
        </select>
      </label>
      <label>
        Başlık
        <input
          value={draft.title}
          aria-invalid={!!errors.title}
          aria-describedby={errors.title ? titleErrorId : undefined}
          onChange={(event) => onChange({ title: event.target.value })}
        />
        <InputError id={titleErrorId} message={errors.title} />
      </label>
      <label className={styles.reviewWide}>
        Açıklama
        <textarea
          value={draft.description}
          aria-invalid={!!errors.description}
          aria-describedby={errors.description ? descriptionErrorId : undefined}
          onChange={(event) => onChange({ description: event.target.value })}
        />
        <InputError id={descriptionErrorId} message={errors.description} />
      </label>
      <label>
        Değer tipi
        <select
          value={value.type}
          onChange={(event) =>
            changeValue({
              type: event.target.value as ValueDraft["type"],
              first: "",
              second: "",
              flag: false,
            })
          }
        >
          {VALUE_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </label>
      <ValueFields
        value={value}
        errors={errors}
        idPrefix={idPrefix}
        onChange={changeValue}
      />
    </fieldset>
  );
}

function ValueFields({
  value,
  errors,
  idPrefix,
  onChange,
}: {
  value: ValueDraft;
  errors: ReviewFieldError;
  idPrefix: string;
  onChange: (next: Partial<ValueDraft>) => void;
}) {
  const primaryErrorField =
    value.type === "MONEY"
      ? "structuredValue.amountMinor"
      : value.type === "PERCENTAGE"
        ? "structuredValue.basisPoints"
        : value.type === "DURATION"
          ? "structuredValue.valueSeconds"
          : "structuredValue.value";
  const primaryError = errors[primaryErrorField];
  const primaryErrorId = `${idPrefix}-value-error`;
  if (value.type === "BOOLEAN")
    return (
      <label>
        Değer
        <select
          value={String(value.flag)}
          aria-invalid={!!primaryError}
          aria-describedby={primaryError ? primaryErrorId : undefined}
          onChange={(event) =>
            onChange({ flag: event.target.value === "true" })
          }
        >
          <option value="true">Evet</option>
          <option value="false">Hayır</option>
        </select>
        <InputError id={primaryErrorId} message={primaryError} />
      </label>
    );
  const primary =
    value.type === "MONEY"
      ? "Tutar (ondalık)"
      : value.type === "PERCENTAGE"
        ? "Yüzde (ondalık)"
        : value.type === "QUANTITY"
          ? "Miktar"
          : "Değer";
  return (
    <>
      <label>
        {primary}
        <input
          type="text"
          value={value.first}
          aria-invalid={!!primaryError}
          aria-describedby={primaryError ? primaryErrorId : undefined}
          onChange={(event) => onChange({ first: event.target.value })}
        />
        <InputError id={primaryErrorId} message={primaryError} />
      </label>
      {value.type === "MONEY" ? (
        <label>
          Para birimi
          <input
            value={value.second}
            maxLength={3}
            aria-invalid={!!errors["structuredValue.currency"]}
            aria-describedby={
              errors["structuredValue.currency"]
                ? `${idPrefix}-currency-error`
                : undefined
            }
            onChange={(event) =>
              onChange({ second: event.target.value.toUpperCase() })
            }
          />
          <InputError
            id={`${idPrefix}-currency-error`}
            message={errors["structuredValue.currency"]}
          />
        </label>
      ) : null}
      {value.type === "QUANTITY" ? (
        <label>
          Birim
          <input
            value={value.second}
            aria-invalid={!!errors["structuredValue.unit"]}
            aria-describedby={
              errors["structuredValue.unit"]
                ? `${idPrefix}-unit-error`
                : undefined
            }
            onChange={(event) => onChange({ second: event.target.value })}
          />
          <InputError
            id={`${idPrefix}-unit-error`}
            message={errors["structuredValue.unit"]}
          />
        </label>
      ) : null}
    </>
  );
}

function InputError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <small className="field-error" id={id}>
      {message}
    </small>
  ) : null;
}

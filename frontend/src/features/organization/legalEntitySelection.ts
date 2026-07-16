const SELECTED_LEGAL_ENTITY_STORAGE_KEY = "m4trust:selected-legal-entity-id:v1";
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function loadInitialSelectedLegalEntityId(): string | undefined {
  try {
    const value = window.sessionStorage.getItem(SELECTED_LEGAL_ENTITY_STORAGE_KEY);
    if (value && UUID_PATTERN.test(value)) {
      return value;
    }
    if (value) {
      window.sessionStorage.removeItem(SELECTED_LEGAL_ENTITY_STORAGE_KEY);
    }
    return undefined;
  } catch {
    return undefined;
  }
}

let selectedLegalEntityId = loadInitialSelectedLegalEntityId();

export function readSelectedLegalEntityId(): string | undefined {
  return selectedLegalEntityId;
}

export function saveSelectedLegalEntityId(legalEntityId: string): void {
  if (!UUID_PATTERN.test(legalEntityId)) {
    clearSelectedLegalEntityId();
    return;
  }

  selectedLegalEntityId = legalEntityId;
  try {
    window.sessionStorage.setItem(
      SELECTED_LEGAL_ENTITY_STORAGE_KEY,
      legalEntityId,
    );
  } catch {
    // The workspace still works when browser storage is unavailable.
  }
}

export function clearSelectedLegalEntityId(): void {
  selectedLegalEntityId = undefined;
  try {
    window.sessionStorage.removeItem(SELECTED_LEGAL_ENTITY_STORAGE_KEY);
  } catch {
    // The workspace still works when browser storage is unavailable.
  }
}

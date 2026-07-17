const SELECTED_LEGAL_ENTITY_STORAGE_KEY_V1 =
  "m4trust:selected-legal-entity-id:v1";
const SELECTED_LEGAL_ENTITY_STORAGE_KEY_PREFIX =
  "m4trust:selected-legal-entity-id:v2:";
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function storageKeyForUser(userId: string): string {
  return `${SELECTED_LEGAL_ENTITY_STORAGE_KEY_PREFIX}${userId}`;
}

function removeLegacyGlobalSelection(): void {
  try {
    window.sessionStorage.removeItem(SELECTED_LEGAL_ENTITY_STORAGE_KEY_V1);
  } catch {
    // The workspace still works when browser storage is unavailable.
  }
}

function loadSelectionForUser(userId: string): string | undefined {
  try {
    const value = window.sessionStorage.getItem(storageKeyForUser(userId));
    if (value && UUID_PATTERN.test(value)) {
      return value;
    }
    if (value) {
      window.sessionStorage.removeItem(storageKeyForUser(userId));
    }
    return undefined;
  } catch {
    return undefined;
  }
}

// Legacy single-user key removal is best-effort and only needs to run once
// per module load; it does not block per-user selection scoping below.
removeLegacyGlobalSelection();

let currentUserId: string | undefined;
let selectedLegalEntityId: string | undefined;

export function setActiveSelectionUser(userId: string): void {
  if (currentUserId === userId) {
    return;
  }
  currentUserId = userId;
  selectedLegalEntityId = loadSelectionForUser(userId);
}

export function clearActiveSelectionUser(): void {
  currentUserId = undefined;
  selectedLegalEntityId = undefined;
}

export function readSelectedLegalEntityId(): string | undefined {
  return selectedLegalEntityId;
}

export function saveSelectedLegalEntityId(legalEntityId: string): void {
  if (!currentUserId) {
    return;
  }

  if (!UUID_PATTERN.test(legalEntityId)) {
    clearSelectedLegalEntityId();
    return;
  }

  selectedLegalEntityId = legalEntityId;
  try {
    window.sessionStorage.setItem(
      storageKeyForUser(currentUserId),
      legalEntityId,
    );
  } catch {
    // The workspace still works when browser storage is unavailable.
  }
}

export function clearSelectedLegalEntityId(): void {
  if (!currentUserId) {
    return;
  }

  selectedLegalEntityId = undefined;
  try {
    window.sessionStorage.removeItem(storageKeyForUser(currentUserId));
  } catch {
    // The workspace still works when browser storage is unavailable.
  }
}

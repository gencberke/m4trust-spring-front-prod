import type { DocumentMediaType } from "./documentApi";

/**
 * Client-side hint only; the backend remains the authoritative validator of
 * accepted media types.
 */
export const ACCEPTED_DOCUMENT_MEDIA_TYPES: readonly DocumentMediaType[] = [
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
];

export const ACCEPTED_DOCUMENT_FILE_INPUT_ACCEPT =
  ".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document";

export function inferDocumentMediaType(file: File): DocumentMediaType | undefined {
  const lowerName = file.name.toLowerCase();
  if (file.type === "application/pdf" || lowerName.endsWith(".pdf")) {
    return "application/pdf";
  }
  if (
    file.type ===
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
    lowerName.endsWith(".docx")
  ) {
    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  }
  return undefined;
}

/** Lowercase hex-encoded SHA-256 digest, matching the contract's Sha256 schema. */
export async function computeSha256Hex(file: File): Promise<string> {
  const buffer = await file.arrayBuffer();
  const digest = await crypto.subtle.digest("SHA-256", buffer);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

export class DirectUploadError extends Error {
  constructor(readonly status: number) {
    super("Document bytes could not be uploaded to storage.");
    this.name = "DirectUploadError";
  }
}

/** Any non-2xx from the presigned PUT is treated as a possible expiry signal by callers. */
export function isLikelyExpiredUploadStatus(status: number): boolean {
  return status === 400 || status === 403 || status === 404 || status === 410;
}

/**
 * Sends the file bytes directly to private object storage via a plain XHR PUT.
 * This intentionally bypasses coreApi.ts: the target is cross-origin storage,
 * uploadHeaders must be sent unchanged, and no cookies/CSRF/credentials may be
 * attached to this request.
 */
export function putDocumentBytes(
  uploadUrl: string,
  uploadHeaders: Record<string, string>,
  file: File,
  onProgress?: (fraction: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", uploadUrl, true);
    xhr.withCredentials = false;
    for (const [name, value] of Object.entries(uploadHeaders)) {
      xhr.setRequestHeader(name, value);
    }
    xhr.upload.onprogress = (event) => {
      if (onProgress && event.lengthComputable) {
        onProgress(event.loaded / event.total);
      }
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new DirectUploadError(xhr.status));
      }
    };
    xhr.onerror = () => reject(new DirectUploadError(0));
    xhr.onabort = () => reject(new DirectUploadError(0));
    xhr.send(file);
  });
}

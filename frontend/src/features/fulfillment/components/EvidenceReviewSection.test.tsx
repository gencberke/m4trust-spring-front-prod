import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { EvidenceSubmission } from "../fulfillmentApi";
import { EvidenceReviewSection } from "./EvidenceReviewSection";

vi.mock("../../videoAnalysis", () => ({
  EvidenceVideoAnalysisPanel: () => <div data-testid="video-analysis" />,
}));

const submittedEvidence = {
  id: "ev-1",
  dealId: "deal-1",
  milestoneId: "ms-1",
  version: 2,
  status: "SUBMITTED",
  evidenceType: "VIDEO",
  mediaType: "video/mp4",
  fileName: "delivery.mp4",
  verifiedSizeBytes: 1024,
  verifiedSha256: "a".repeat(64),
  objectVersion: 1,
  createdAt: "2026-01-01T12:00:00.000Z",
  submittedAt: "2026-01-02T12:00:00.000Z",
  availableActions: { canCancel: false },
} as unknown as EvidenceSubmission;

describe("EvidenceReviewSection", () => {
  it("renders nothing for non-submitted evidence", () => {
    const { container } = render(
      <EvidenceReviewSection
        legalEntityId="le-1"
        dealId="deal-1"
        evidence={
          { ...submittedEvidence, status: "ACCEPTED" } as EvidenceSubmission
        }
        readOnly={false}
        canAccept
        isAccepting={false}
        isRejecting={false}
        reviewError={undefined}
        rejectionReason=""
        onRejectionReasonChange={vi.fn()}
        onAccept={vi.fn()}
        onReject={vi.fn()}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("wires accept and reject callbacks with validation", async () => {
    const user = userEvent.setup();
    const onAccept = vi.fn();
    const onReject = vi.fn();

    render(
      <EvidenceReviewSection
        legalEntityId="le-1"
        dealId="deal-1"
        evidence={submittedEvidence}
        readOnly={false}
        canAccept
        isAccepting={false}
        isRejecting={false}
        reviewError="Reddetme sebebi 1–1000 karakter arasında olmalıdır."
        rejectionReason="Eksik belge"
        onRejectionReasonChange={vi.fn()}
        onAccept={onAccept}
        onReject={onReject}
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Reddetme sebebi 1–1000 karakter arasında olmalıdır.",
    );
    expect(screen.getByTestId("video-analysis")).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "Teslimat kanıtını onayla" }),
    );
    expect(onAccept).toHaveBeenCalledWith(submittedEvidence);

    await user.click(
      screen.getByRole("button", { name: "Teslimat kanıtını reddet" }),
    );
    expect(onReject).toHaveBeenCalledWith(submittedEvidence);
  });
});

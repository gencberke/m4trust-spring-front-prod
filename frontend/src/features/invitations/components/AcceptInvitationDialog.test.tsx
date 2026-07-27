import { ApiError } from "@/app/coreApi";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { AcceptInvitationDialog } from "./AcceptInvitationDialog";

const invitation = {
  id: "inv-1",
  deal: {
    id: "deal-1",
    reference: "REF-1",
    title: "Örnek anlaşma",
    initiatorLegalName: "Acme A.Ş.",
  },
  status: "PENDING",
  version: 1,
  createdAt: "2026-01-01T00:00:00.000Z",
  updatedAt: "2026-01-01T00:00:00.000Z",
  availableActions: { canAccept: true, canReject: false, canRevoke: false },
} as const satisfies Parameters<typeof AcceptInvitationDialog>[0]["invitation"];

const memberships = [
  {
    legalEntityId: "le-1",
    legalName: "Beta Ltd.",
    registrationNumber: "123",
    role: "ADMIN" as const,
  },
];

describe("AcceptInvitationDialog", () => {
  it("renders deal context and blocks accept until an entity is selected", async () => {
    const user = userEvent.setup();
    const onAccept = vi.fn();

    render(
      <AcceptInvitationDialog
        invitation={invitation}
        memberships={memberships}
        error={undefined}
        isPending={false}
        onAccept={onAccept}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByRole("dialog")).toHaveAttribute("aria-modal", "true");
    expect(screen.getByText("Örnek anlaşma")).toBeInTheDocument();
    expect(screen.getByText("Acme A.Ş.")).toBeInTheDocument();

    const acceptButton = screen.getByRole("button", {
      name: "Katılımı onayla",
    });
    expect(acceptButton).toBeDisabled();

    await user.selectOptions(
      screen.getByLabelText("Katılımcı kuruluş"),
      "le-1",
    );
    expect(acceptButton).toBeEnabled();

    await user.click(acceptButton);
    expect(onAccept).toHaveBeenCalledWith("le-1");
  });

  it("surfaces server errors and empty-membership guard", () => {
    const { rerender } = render(
      <AcceptInvitationDialog
        invitation={invitation}
        memberships={[]}
        error={undefined}
        isPending={false}
        onAccept={vi.fn()}
        onClose={vi.fn()}
      />,
    );
    expect(
      screen.getByText(
        "Daveti kabul etmek için önce üyesi olduğunuz bir kuruluş gerekir.",
      ),
    ).toBeInTheDocument();

    rerender(
      <AcceptInvitationDialog
        invitation={invitation}
        memberships={memberships}
        error={
          new ApiError(403, {
            type: "about:blank",
            title: "Forbidden",
            status: 403,
            detail: "blocked",
            code: "DEAL_INVITATION_FORBIDDEN",
            correlationId: "corr-1",
          })
        }
        isPending={false}
        onAccept={vi.fn()}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});

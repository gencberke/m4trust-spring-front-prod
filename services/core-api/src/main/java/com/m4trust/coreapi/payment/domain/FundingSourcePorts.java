package com.m4trust.coreapi.payment.domain;

import com.m4trust.coreapi.organization.domain.OperationContext;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumer-owned contract toward {@code deal}; the {@code deal} module implements this port without
 * payment ever reading DealRepository. Mirrors {@code RatificationSourcePorts.DealTarget}.
 */
public final class FundingSourcePorts {
  private FundingSourcePorts() {}

  public interface DealTarget {

    /** Read-only visibility + status lookup, used by initiate/reconcile/read paths. */
    Optional<Target> findVisible(OperationContext context, UUID dealId);

    /** Locks the Deal row; used only by FundingPlan create. */
    Optional<Target> lockVisibleForCreate(OperationContext context, UUID dealId);
  }

  /**
   * @param ratifiedAmountMinor amount snapshot from the Deal's current RATIFIED package, or {@code
   *     null} when the Deal has none (never null while ACTIVE).
   * @param ratifiedCurrency currency snapshot from the same RATIFIED package.
   */
  public record Target(
      UUID dealId,
      UUID tenantId,
      String status,
      long version,
      UUID buyerLegalEntityId,
      UUID sellerLegalEntityId,
      UUID ratifiedPackageId,
      Long ratifiedAmountMinor,
      String ratifiedCurrency) {}
}

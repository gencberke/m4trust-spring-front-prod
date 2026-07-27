package com.m4trust.coreapi.payment.api;

import com.m4trust.coreapi.payment.api.dto.*;
import com.m4trust.coreapi.payment.domain.*;
import com.m4trust.coreapi.payment.infra.*;

/**
 * Wire shape of {@code ReconcilePaymentOperationRequest}: carries only the PaymentOperation
 * expectedVersion.
 */
record ReconcilePaymentOperationHttpRequest(long expectedVersion) {}

package com.m4trust.coreapi.integration.payment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.m4trust.coreapi.payment.PaymentProviderMode;
import com.m4trust.coreapi.payment.PaymentProviderPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Provider-independent sandbox: simulates a held-funds provider entirely
 * in-process, never moves real money, and never claims M4Trust holds funds
 * (ADR-010 §2.2, §2.6). Active under the {@code local-sandbox} profile or the
 * separately named {@code staging-simulated} profile (2026-07-22
 * simulation-only decision §2; ADR-014 §2.1) — never under {@code production}
 * (see {@link SandboxPaymentProviderBootstrapGuard}).
 *
 * <p>Exactly one configured scenario is consumed per new provider key
 * (assigned the first time {@link #initiate} sees that key), then resolved
 * deterministically: {@code SUCCESS}/{@code DECLINE} resolve on the first
 * call, {@code TIMEOUT_THEN_SUCCESS} resolves {@code UNCONFIRMED} on the
 * first call and {@code SUCCEEDED} from the second call on — matching the
 * relay's query-first dispatch (queryStatus before any initiate) so a
 * never-before-seen key always answers {@code NOT_FOUND} from queryStatus.
 */
@Component
@Profile({"local-sandbox", "staging-simulated"})
class SandboxPaymentProviderAdapter implements PaymentProviderPort {

    private final SandboxPaymentProviderProperties properties;
    private final Map<String, SandboxOperationState> states = new ConcurrentHashMap<>();
    private final AtomicInteger cursor = new AtomicInteger(0);

    SandboxPaymentProviderAdapter(SandboxPaymentProviderProperties properties) {
        this.properties = properties;
    }

    @Override
    public ProviderResult initiate(ProviderRequest request) {
        SandboxOperationState state = states.computeIfAbsent(request.providerKey(),
                key -> new SandboxOperationState(nextScenario(), "sandbox-" + key));
        return resolve(state);
    }

    @Override
    public ProviderResult queryStatus(ProviderRequest request) {
        SandboxOperationState state = states.get(request.providerKey());
        if (state == null) {
            return new ProviderResult(Outcome.NOT_FOUND, null);
        }
        return resolve(state);
    }

    @Override
    public PaymentProviderMode mode() {
        return PaymentProviderMode.DEMO_SIMULATED;
    }

    @Override
    public ReleaseProviderResult initiateRelease(ProviderRequest request) {
        SandboxOperationState state = releaseStates.computeIfAbsent(request.providerKey(),
                key -> new SandboxOperationState(nextReleaseScenario(), "sandbox-release-" + key));
        return resolveRelease(state);
    }

    @Override
    public ReleaseProviderResult queryReleaseStatus(ProviderRequest request) {
        SandboxOperationState state = releaseStates.get(request.providerKey());
        if (state == null) {
            return new ReleaseProviderResult(ReleaseOutcome.NOT_FOUND, null);
        }
        return resolveRelease(state);
    }

    private final Map<String, SandboxOperationState> releaseStates = new ConcurrentHashMap<>();

    private ProviderResult resolve(SandboxOperationState state) {
        return switch (state.scenario) {
            case SUCCESS -> new ProviderResult(Outcome.SUCCEEDED, state.providerReference);
            case DECLINE -> new ProviderResult(Outcome.DECLINED, state.providerReference);
            case TIMEOUT_THEN_SUCCESS -> state.callCount.incrementAndGet() == 1
                    ? new ProviderResult(Outcome.UNCONFIRMED, null)
                    : new ProviderResult(Outcome.SUCCEEDED, state.providerReference);
        };
    }

    private ReleaseProviderResult resolveRelease(SandboxOperationState state) {
        return switch (state.scenario) {
            case SUCCESS -> new ReleaseProviderResult(ReleaseOutcome.SIMULATED_SETTLED, state.providerReference);
            case DECLINE -> new ReleaseProviderResult(ReleaseOutcome.SIMULATED_DECLINED, state.providerReference);
            case TIMEOUT_THEN_SUCCESS -> state.callCount.incrementAndGet() == 1
                    ? new ReleaseProviderResult(ReleaseOutcome.UNCONFIRMED, null)
                    : new ReleaseProviderResult(ReleaseOutcome.SIMULATED_SETTLED, state.providerReference);
        };
    }

    private SandboxScenario nextReleaseScenario() {
        return nextScenario();
    }

    private SandboxScenario nextScenario() {
        List<SandboxScenario> scenarios = properties.scenarios();
        int index = Math.floorMod(cursor.getAndIncrement(), scenarios.size());
        return scenarios.get(index);
    }

    private static final class SandboxOperationState {
        private final SandboxScenario scenario;
        private final String providerReference;
        private final AtomicInteger callCount = new AtomicInteger(0);

        SandboxOperationState(SandboxScenario scenario, String providerReference) {
            this.scenario = scenario;
            this.providerReference = providerReference;
        }
    }
}

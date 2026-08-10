package com.hx.creditcardflow.common.metrics;

import com.hx.creditcardflow.authorization.dto.AuthorizationResponse;
import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.authorization.service.AuthorizationService;
import com.hx.creditcardflow.clearing.dto.ClearingResponse;
import com.hx.creditcardflow.clearing.entity.ClearingStatus;
import com.hx.creditcardflow.clearing.service.ClearingService;
import com.hx.creditcardflow.reversal.dto.ReversalResponse;
import com.hx.creditcardflow.reversal.entity.ReversalStatus;
import com.hx.creditcardflow.reversal.service.ReversalService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessMetricsAspectTest {

    private SimpleMeterRegistry meterRegistry;
    private BusinessMetricsAspect aspect;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aspect = new BusinessMetricsAspect(meterRegistry);
    }

    @Test
    void approvedAuthorizationIncrementsOnlyApprovedCounterAndPreservesResponse() {
        AuthorizationResponse expected = authorizationResponse(AuthorizationStatus.APPROVED);
        AuthorizationService service = proxiedAuthorizationService(expected);

        AuthorizationResponse actual = service.createAuthorization(null);

        assertThat(actual).isSameAs(expected);
        assertThat(counter("creditcardflow.authorization.approved")).isEqualTo(1.0);
        assertThat(counter("creditcardflow.authorization.declined")).isZero();
    }

    @Test
    void declinedAuthorizationIncrementsOnlyDeclinedCounterAndPreservesResponse() {
        AuthorizationResponse expected = authorizationResponse(AuthorizationStatus.DECLINED);
        AuthorizationService service = proxiedAuthorizationService(expected);

        AuthorizationResponse actual = service.createAuthorization(null);

        assertThat(actual).isSameAs(expected);
        assertThat(counter("creditcardflow.authorization.approved")).isZero();
        assertThat(counter("creditcardflow.authorization.declined")).isEqualTo(1.0);
    }

    @Test
    void failedAuthorizationDoesNotIncrementOutcomeCounters() {
        AuthorizationService target = mock(AuthorizationService.class);
        IllegalStateException failure = new IllegalStateException("creation failed");
        when(target.createAuthorization(null)).thenThrow(failure);
        AuthorizationService service = proxy(target);

        assertThatThrownBy(() -> service.createAuthorization(null)).isSameAs(failure);

        assertThat(counter("creditcardflow.authorization.approved")).isZero();
        assertThat(counter("creditcardflow.authorization.declined")).isZero();
    }

    @Test
    void completedReversalIncrementsCompletedCounterAndPreservesResponse() {
        Instant now = Instant.now();
        ReversalResponse expected = new ReversalResponse(
                1L, "REV-TEST", "AUTH-TEST", new BigDecimal("10.00"),
                ReversalStatus.COMPLETED, now, now
        );
        ReversalService target = mock(ReversalService.class);
        when(target.createReversal("KEY-TEST", null)).thenReturn(expected);
        ReversalService service = proxy(target);

        ReversalResponse actual = service.createReversal("KEY-TEST", null);

        assertThat(actual).isSameAs(expected);
        assertThat(counter("creditcardflow.reversal.completed")).isEqualTo(1.0);
    }

    @Test
    void postedClearingIncrementsPostedCounterAndPreservesResponse() {
        Instant now = Instant.now();
        ClearingResponse expected = new ClearingResponse(
                1L, "CLR-TEST", "AUTH-TEST", new BigDecimal("10.00"),
                "USD", ClearingStatus.POSTED, now, now
        );
        ClearingService target = mock(ClearingService.class);
        when(target.createClearing(null)).thenReturn(expected);
        ClearingService service = proxy(target);

        ClearingResponse actual = service.createClearing(null);

        assertThat(actual).isSameAs(expected);
        assertThat(counter("creditcardflow.clearing.posted")).isEqualTo(1.0);
    }

    private AuthorizationService proxiedAuthorizationService(AuthorizationResponse response) {
        AuthorizationService target = mock(AuthorizationService.class);
        when(target.createAuthorization(null)).thenReturn(response);
        return proxy(target);
    }

    private AuthorizationResponse authorizationResponse(AuthorizationStatus status) {
        Instant now = Instant.now();
        return new AuthorizationResponse(
                1L, "AUTH-TEST", "CARD-TEST", "MERCHANT-TEST",
                new BigDecimal("10.00"), "USD", null, null, status, now, now
        );
    }

    private double counter(String name) {
        return meterRegistry.get(name).counter().count();
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return (T) factory.getProxy();
    }
}

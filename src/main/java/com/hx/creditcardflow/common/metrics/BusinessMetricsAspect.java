package com.hx.creditcardflow.common.metrics;

import com.hx.creditcardflow.authorization.dto.AuthorizationResponse;
import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.clearing.dto.ClearingResponse;
import com.hx.creditcardflow.clearing.entity.ClearingStatus;
import com.hx.creditcardflow.reversal.dto.ReversalResponse;
import com.hx.creditcardflow.reversal.entity.ReversalStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BusinessMetricsAspect {

    private final Counter approvedAuthorizations;
    private final Counter declinedAuthorizations;
    private final Counter completedReversals;
    private final Counter postedClearings;

    public BusinessMetricsAspect(MeterRegistry meterRegistry) {
        approvedAuthorizations = meterRegistry.counter(
                "creditcardflow.authorization.approved");
        declinedAuthorizations = meterRegistry.counter(
                "creditcardflow.authorization.declined");
        completedReversals = meterRegistry.counter(
                "creditcardflow.reversal.completed");
        postedClearings = meterRegistry.counter(
                "creditcardflow.clearing.posted");
    }

    @AfterReturning(
            pointcut = "execution(* com.hx.creditcardflow.authorization.service."
                    + "AuthorizationService.createAuthorization(..))",
            returning = "response"
    )
    public void recordAuthorizationOutcome(AuthorizationResponse response) {
        if (response.status() == AuthorizationStatus.APPROVED) {
            approvedAuthorizations.increment();
        } else if (response.status() == AuthorizationStatus.DECLINED) {
            declinedAuthorizations.increment();
        }
    }

    @AfterReturning(
            pointcut = "execution(* com.hx.creditcardflow.reversal.service."
                    + "ReversalService.createReversal(..))",
            returning = "response"
    )
    public void recordReversalOutcome(ReversalResponse response) {
        if (response.status() == ReversalStatus.COMPLETED) {
            completedReversals.increment();
        }
    }

    @AfterReturning(
            pointcut = "execution(* com.hx.creditcardflow.clearing.service."
                    + "ClearingService.createClearing(..))",
            returning = "response"
    )
    public void recordClearingOutcome(ClearingResponse response) {
        if (response.status() == ClearingStatus.POSTED) {
            postedClearings.increment();
        }
    }
}

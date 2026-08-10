package com.hx.creditcardflow.common.logging;

import com.hx.creditcardflow.merchant.service.MerchantService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationLoggingAspectTest {

    private final ApplicationLoggingAspect aspect = new ApplicationLoggingAspect();
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() {
        joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(MerchantService.class);
        when(signature.getName()).thenReturn("getAllMerchants");
    }

    @Test
    void successfulInvocationExecutesTargetOnceAndPreservesReturnValue() throws Throwable {
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);

        Object actualResult = aspect.logServiceInvocation(joinPoint);

        assertThat(actualResult).isSameAs(expectedResult);
        verify(joinPoint).proceed();
    }

    @Test
    void failedInvocationPropagatesOriginalExceptionUnchanged() throws Throwable {
        IllegalStateException expectedException = new IllegalStateException("target failure");
        when(joinPoint.proceed()).thenThrow(expectedException);

        assertThatThrownBy(() -> aspect.logServiceInvocation(joinPoint))
                .isSameAs(expectedException);
        verify(joinPoint).proceed();
    }
}

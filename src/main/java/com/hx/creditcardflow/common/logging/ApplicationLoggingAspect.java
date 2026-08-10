package com.hx.creditcardflow.common.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class ApplicationLoggingAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationLoggingAspect.class);

    @Around("execution(* com.hx.creditcardflow..service..*(..))")
    public Object logServiceInvocation(ProceedingJoinPoint joinPoint) throws Throwable {
        String serviceClass = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        long startNanos = System.nanoTime();

        LOGGER.info("Service {}.{} started", serviceClass, methodName);

        try {
            Object result = joinPoint.proceed();
            long durationMillis = elapsedMillis(startNanos);
            LOGGER.info("Service {}.{} succeeded in {} ms",
                    serviceClass, methodName, durationMillis);
            return result;
        } catch (Throwable exception) {
            long durationMillis = elapsedMillis(startNanos);
            LOGGER.warn("Service {}.{} failed in {} ms with {}",
                    serviceClass, methodName, durationMillis,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}

package com.koval.trainingplannerbackend.config.audit;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * AOP aspect that intercepts methods annotated with {@link AuditLog}
 * and logs structured audit entries for security-sensitive operations.
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    @Around("@annotation(auditLog)")
    public Object logAudit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        String userId;
        try {
            userId = SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            userId = "anonymous";
        }

        String action = auditLog.action();
        String method = joinPoint.getSignature().toShortString();
        Instant timestamp = Instant.now();

        try {
            Object result = joinPoint.proceed();
            audit.info("action={} user={} method={} status=OK timestamp={}",
                    action, userId, method, timestamp);
            return result;
        } catch (Throwable ex) {
            audit.warn("action={} user={} method={} status=FAILED error={} timestamp={}",
                    action, userId, method, ex.getMessage(), timestamp);
            throw ex;
        }
    }
}

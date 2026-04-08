package com.koval.trainingplannerbackend.config.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * AOP aspect that intercepts methods annotated with {@link AuditLog}
 * and (a) logs structured audit entries to the AUDIT logger and (b) persists
 * an {@link AuditEntry} document for later querying.
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");
    private static final ObjectMapper mapper = new ObjectMapper();

    private final AuditEntryRepository auditEntryRepository;

    public AuditLogAspect(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    @Around("@annotation(auditLog)")
    public Object logAudit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        String userId;
        try {
            userId = SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            userId = "SYSTEM";
        }

        String action = auditLog.action();
        String method = joinPoint.getSignature().toShortString();
        Instant timestamp = Instant.now();
        long start = System.nanoTime();

        try {
            Object result = joinPoint.proceed();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            audit.info("action={} user={} method={} status=OK durationMs={} timestamp={}",
                    action, userId, method, durationMs, timestamp);
            persistAsync(action, userId, method, joinPoint.getArgs(), durationMs, true, null);
            return result;
        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            audit.warn("action={} user={} method={} status=FAILED error={} durationMs={} timestamp={}",
                    action, userId, method, ex.getMessage(), durationMs, timestamp);
            persistAsync(action, userId, method, joinPoint.getArgs(), durationMs, false, ex.getMessage());
            throw ex;
        }
    }

    @Async
    void persistAsync(String action, String userId, String method, Object[] args,
                      long durationMs, boolean success, String errorMessage) {
        try {
            AuditEntry entry = new AuditEntry();
            entry.setActorUserId(userId);
            entry.setAction(action);
            entry.setMethod(method);
            entry.setSuccess(success);
            entry.setDurationMs(durationMs);
            entry.setErrorMessage(errorMessage);
            entry.setArgsJson(serializeArgs(args));
            auditEntryRepository.save(entry);
        } catch (Exception e) {
            // Never let audit persistence affect request flow.
            audit.warn("Failed to persist audit entry for action={}: {}", action, e.getMessage());
        }
    }

    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) return null;
        try {
            // Skip large or sensitive types entirely.
            Object[] safe = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a == null) {
                    safe[i] = null;
                } else if (a instanceof byte[] b) {
                    safe[i] = "<byte[" + b.length + "]>";
                } else if (a.getClass().getName().contains("MultipartFile")) {
                    safe[i] = "<MultipartFile>";
                } else {
                    safe[i] = a;
                }
            }
            return mapper.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            return "<unserializable: " + e.getMessage() + ">";
        }
    }
}

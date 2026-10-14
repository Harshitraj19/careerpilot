package com.placify.common.audit;

import com.placify.common.messaging.EventPublisher;
import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Publishes an {@link Events.AuditEvent} for every {@link Audited} method,
 * whether it succeeds or fails. The analytics service stores the events.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final EventPublisher publisher;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();

    @Value("${spring.application.name:unknown}")
    private String serviceName;

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        String target = resolveTarget(pjp, audited);
        try {
            Object result = pjp.proceed();
            send(audited.action(), target, "OK", true);
            return result;
        } catch (Throwable ex) {
            send(audited.action(), target, ex.getMessage(), false);
            throw ex;
        }
    }

    private String resolveTarget(ProceedingJoinPoint pjp, Audited audited) {
        if (audited.target().isBlank()) {
            return null;
        }
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            var ctx = new MethodBasedEvaluationContext(pjp.getTarget(), sig.getMethod(), pjp.getArgs(), names);
            Object value = parser.parseExpression(audited.target()).getValue(ctx);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private void send(String action, String target, String details, boolean success) {
        Optional<UserPrincipal> user = CurrentUser.find();
        publisher.publish(RoutingKeys.AUDIT, new Events.AuditEvent(
                serviceName,
                user.map(UserPrincipal::userId).orElse("system"),
                user.map(u -> u.role() == null ? "SYSTEM" : u.role().name()).orElse("SYSTEM"),
                action,
                target,
                details == null ? null : details.substring(0, Math.min(details.length(), 500)),
                success,
                LocalDateTime.now()));
    }
}

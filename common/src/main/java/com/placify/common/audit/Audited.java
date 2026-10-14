package com.placify.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose execution must be recorded in the audit log.
 *
 * <pre>
 * &#64;Audited(action = "COMPANY_APPROVED", target = "#companyId")
 * public void approve(String companyId) { ... }
 * </pre>
 *
 * {@code target} is a SpEL expression evaluated against the method arguments.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String action();

    String target() default "";
}

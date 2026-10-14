package com.placify.placement.rules;

/**
 * One rule in the placement policy chain (Strategy pattern). Every rule is a
 * Spring bean; adding a new college rule only means adding a new class.
 */
public interface EligibilityRule {

    /**
     * @return the check result, or {@code null} when the rule does not apply to
     *         this job (e.g. no CGPA criterion set)
     */
    RuleCheck evaluate(EligibilityContext ctx);
}

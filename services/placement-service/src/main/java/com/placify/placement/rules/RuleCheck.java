package com.placify.placement.rules;

/**
 * Outcome of one rule, shown to the student so they know exactly why they can
 * or cannot apply.
 *
 * @param blocking false for informational checks that never stop an application
 */
public record RuleCheck(String code, String label, boolean passed, String message, boolean blocking) {

    public static RuleCheck pass(String code, String label, String message) {
        return new RuleCheck(code, label, true, message, true);
    }

    public static RuleCheck fail(String code, String label, String message) {
        return new RuleCheck(code, label, false, message, true);
    }
}

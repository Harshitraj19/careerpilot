package com.placify.common.messaging;

/**
 * RabbitMQ exchange and routing keys. All services publish to one topic exchange.
 */
public final class RoutingKeys {

    public static final String EXCHANGE = "placify.events";

    public static final String NOTIFICATION = "notification.send";
    public static final String STUDENT_IMPORTED = "student.imported";
    public static final String COMPANY_APPROVED = "company.approved";
    public static final String ASSESSMENT_COMPLETED = "assessment.completed";
    public static final String INTERVIEW_EVALUATED = "interview.evaluated";
    public static final String SUBMISSION_JUDGED = "submission.judged";
    public static final String PROCTORING_TERMINATE = "proctoring.terminate";
    public static final String PROCTORING_WARNING = "proctoring.warning";
    public static final String PROCTORING_FRAME = "proctoring.frame";
    public static final String LEADERBOARD_UPDATED = "leaderboard.updated";
    public static final String STUDENT_PLACED = "student.placed";
    public static final String AUDIT = "audit.log";

    private RoutingKeys() {
    }
}

package com.placify.proctoring.domain;

/**
 * Everything the proctoring system can detect, with its severity and the
 * warning shown to the student.
 */
public enum ViolationType {
    NO_FACE(Severity.HIGH, "Your face is not visible. Stay in front of the camera."),
    MULTIPLE_FACES(Severity.HIGH, "More than one person detected. You must take the test alone."),
    DIFFERENT_PERSON(Severity.HIGH, "The person in front of the camera does not match the registered student."),
    PHONE_DETECTED(Severity.HIGH, "A mobile phone was detected. Keep phones away during the test."),
    CAMERA_BLOCKED(Severity.HIGH, "The camera is covered or too dark."),
    LOOKING_AWAY(Severity.MEDIUM, "Please keep looking at your screen."),
    EXCESSIVE_MOVEMENT(Severity.MEDIUM, "Too much movement detected. Please stay seated."),
    TAB_SWITCH(Severity.MEDIUM, "Switching tabs or windows is not allowed."),
    FULLSCREEN_EXIT(Severity.MEDIUM, "Please stay in full-screen mode."),
    COPY_PASTE(Severity.MEDIUM, "Copy and paste is disabled during the test."),
    SCREEN_SHARE_STOPPED(Severity.HIGH, "Screen sharing stopped. Share your entire screen again to continue."),
    MULTIPLE_MONITORS(Severity.MEDIUM, "More than one monitor is connected. Use a single screen during the test."),
    NOISE(Severity.LOW, "Background voices detected. Please take the test in a quiet place."),
    WINDOW_BLUR(Severity.LOW, "The test window lost focus.");

    public enum Severity { LOW, MEDIUM, HIGH }

    private final Severity severity;
    private final String message;

    ViolationType(Severity severity, String message) {
        this.severity = severity;
        this.message = message;
    }

    public Severity severity() {
        return severity;
    }

    public String message() {
        return message;
    }

    /** LOW-severity events are recorded but do not count toward the violation limit. */
    public boolean countsTowardLimit() {
        return severity != Severity.LOW;
    }

    public boolean isBrowserEvent() {
        return this == TAB_SWITCH || this == FULLSCREEN_EXIT || this == COPY_PASTE || this == WINDOW_BLUR || this == NOISE
                || this == SCREEN_SHARE_STOPPED || this == MULTIPLE_MONITORS;
    }
}

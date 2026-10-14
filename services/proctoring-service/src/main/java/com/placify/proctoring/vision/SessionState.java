package com.placify.proctoring.vision;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * In-memory state of an active proctoring session (previous frame for motion
 * detection, look-away streak, debounce timestamps, cached reference photo).
 */
public class SessionState {

    public Mat previous;
    public Rect lastFace;
    public int lookAwayStreak;
    public int frameCounter;
    public byte[] referencePhoto;
    public final Map<com.placify.proctoring.domain.ViolationType, LocalDateTime> lastRecorded =
            new EnumMap<>(com.placify.proctoring.domain.ViolationType.class);

    public void resetMotion() {
        if (previous != null) {
            previous.release();
            previous = null;
        }
    }

    public void release() {
        resetMotion();
    }
}

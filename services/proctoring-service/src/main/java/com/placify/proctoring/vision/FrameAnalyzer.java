package com.placify.proctoring.vision;

import com.placify.proctoring.config.ProctoringProperties;
import com.placify.proctoring.domain.ViolationType;
import lombok.RequiredArgsConstructor;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyses one webcam frame with OpenCV:
 * <ul>
 *   <li>camera covered / too dark - mean and spread of brightness</li>
 *   <li>no face / multiple faces - Haar frontal-face detector</li>
 *   <li>looking away - profile-face detector, face far off-centre, or no eyes visible
 *       (counted only when it lasts for 2+ consecutive frames)</li>
 *   <li>excessive movement - difference between this frame and the previous one</li>
 * </ul>
 * Identity and phone checks are done by {@link FaceMatcher} and {@link PhoneDetector}.
 */
@Component
@RequiredArgsConstructor
public class FrameAnalyzer {

    private final OpenCvSupport cv;
    private final ProctoringProperties props;

    public record Finding(ViolationType type, String detail) {
    }

    /**
     * @param state per-session memory (previous frame, look-away streak); updated in place
     * @return the detected problems (empty = frame looks fine)
     */
    public List<Finding> analyze(byte[] imageBytes, SessionState state) {
        List<Finding> findings = new ArrayList<>();
        if (!cv.available()) {
            return findings;
        }
        Mat color = OpenCvSupport.decode(imageBytes);
        Mat gray = new Mat();
        Mat small = new Mat();
        MatOfRect faces = new MatOfRect();
        try {
            if (color.empty()) {
                findings.add(new Finding(ViolationType.CAMERA_BLOCKED, "Frame could not be read"));
                return findings;
            }
            Imgproc.cvtColor(color, gray, Imgproc.COLOR_BGR2GRAY);

            // ---- camera covered or very dark
            MatOfDouble mean = new MatOfDouble();
            MatOfDouble std = new MatOfDouble();
            Core.meanStdDev(gray, mean, std);
            double brightness = mean.toArray()[0];
            double spread = std.toArray()[0];
            mean.release();
            std.release();
            if (brightness < props.darkThreshold() || spread < 8) {
                findings.add(new Finding(ViolationType.CAMERA_BLOCKED,
                        String.format("Brightness %.0f, contrast %.0f", brightness, spread)));
                state.resetMotion();
                return findings;
            }

            Imgproc.equalizeHist(gray, gray);

            // ---- faces
            int minFace = Math.max(40, Math.min(gray.width(), gray.height()) / 8);
            cv.frontalFace().detectMultiScale(gray, faces, 1.1, 5, 0, new Size(minFace, minFace), new Size());
            Rect[] found = faces.toArray();
            if (found.length == 0) {
                if (profileVisible(gray, minFace)) {
                    lookingAway(findings, state, "Head turned to the side");
                } else {
                    findings.add(new Finding(ViolationType.NO_FACE, "No face in frame"));
                    state.lookAwayStreak = 0;
                }
            } else if (found.length > 1) {
                findings.add(new Finding(ViolationType.MULTIPLE_FACES, found.length + " faces in frame"));
                state.lookAwayStreak = 0;
            } else {
                Rect face = found[0];
                double centreX = face.x + face.width / 2.0;
                double offset = Math.abs(centreX - gray.width() / 2.0) / gray.width();
                if (offset > 0.32) {
                    lookingAway(findings, state, "Face near the edge of the frame");
                } else if (!eyesVisible(gray, face)) {
                    lookingAway(findings, state, "Eyes not visible (looking down or away)");
                } else {
                    state.lookAwayStreak = 0;
                }
                state.lastFace = face;
            }

            // ---- movement: compare a small blurred copy with the previous frame
            Imgproc.resize(gray, small, new Size(160, 120));
            Imgproc.GaussianBlur(small, small, new Size(5, 5), 0);
            if (state.previous != null) {
                Mat diff = new Mat();
                Core.absdiff(state.previous, small, diff);
                Imgproc.threshold(diff, diff, 25, 255, Imgproc.THRESH_BINARY);
                double changed = 100.0 * Core.countNonZero(diff) / diff.total();
                diff.release();
                if (changed > props.movementThresholdPercent()) {
                    findings.add(new Finding(ViolationType.EXCESSIVE_MOVEMENT, String.format("%.0f%% of the image changed", changed)));
                }
                state.previous.release();
            }
            state.previous = small.clone();
            return findings;
        } finally {
            color.release();
            gray.release();
            small.release();
            faces.release();
        }
    }

    private void lookingAway(List<Finding> findings, SessionState state, String detail) {
        state.lookAwayStreak++;
        if (state.lookAwayStreak >= 2) {
            findings.add(new Finding(ViolationType.LOOKING_AWAY, detail));
        }
    }

    private boolean profileVisible(Mat gray, int minFace) {
        MatOfRect profiles = new MatOfRect();
        Mat flipped = new Mat();
        try {
            cv.profileFace().detectMultiScale(gray, profiles, 1.1, 4, 0, new Size(minFace, minFace), new Size());
            if (profiles.toArray().length > 0) {
                return true;
            }
            // the cascade only knows one side, so also check the mirrored image
            Core.flip(gray, flipped, 1);
            cv.profileFace().detectMultiScale(flipped, profiles, 1.1, 4, 0, new Size(minFace, minFace), new Size());
            return profiles.toArray().length > 0;
        } finally {
            profiles.release();
            flipped.release();
        }
    }

    private boolean eyesVisible(Mat gray, Rect face) {
        // eyes are in the upper 60% of the face
        Rect upper = new Rect(face.x, face.y, face.width, (int) (face.height * 0.6));
        Mat roi = gray.submat(upper);
        MatOfRect eyes = new MatOfRect();
        try {
            int min = Math.max(10, face.width / 8);
            cv.eyes().detectMultiScale(roi, eyes, 1.1, 4, 0, new Size(min, min), new Size());
            return eyes.toArray().length >= 1;
        } finally {
            roi.release();
            eyes.release();
        }
    }
}

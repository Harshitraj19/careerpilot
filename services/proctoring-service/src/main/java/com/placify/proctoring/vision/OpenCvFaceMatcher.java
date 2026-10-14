package com.placify.proctoring.vision;

import lombok.RequiredArgsConstructor;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight identity check with plain OpenCV: the largest face of each image
 * is cropped, normalised to 100x100 and compared by histogram correlation and
 * normalised cross-correlation. It needs no extra model, but it is less
 * accurate than a deep face-embedding model (see {@link DjlFaceMatcher}), so a
 * mismatch is only a flag for human review, never an automatic rejection.
 */
@RequiredArgsConstructor
public class OpenCvFaceMatcher implements FaceMatcher {

    private final OpenCvSupport cv;

    @Override
    public String name() {
        return "OpenCV (histogram + NCC)";
    }

    @Override
    public double similarity(byte[] imageA, byte[] imageB) {
        if (!cv.available()) {
            return -1;
        }
        Mat a = normalizedFace(imageA);
        Mat b = normalizedFace(imageB);
        try {
            if (a == null || b == null) {
                return -1;
            }
            double hist = histogramCorrelation(a, b);
            Mat result = new Mat();
            Imgproc.matchTemplate(a, b, result, Imgproc.TM_CCOEFF_NORMED);
            double ncc = Core.minMaxLoc(result).maxVal;
            result.release();
            double score = 0.5 * Math.max(0, hist) + 0.5 * Math.max(0, ncc);
            return Math.min(1, score);
        } finally {
            if (a != null) {
                a.release();
            }
            if (b != null) {
                b.release();
            }
        }
    }

    private Mat normalizedFace(byte[] bytes) {
        Mat color = OpenCvSupport.decode(bytes);
        Mat gray = new Mat();
        MatOfRect faces = new MatOfRect();
        try {
            if (color.empty()) {
                return null;
            }
            Imgproc.cvtColor(color, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(gray, gray);
            cv.frontalFace().detectMultiScale(gray, faces, 1.1, 5, 0, new Size(40, 40), new Size());
            Rect[] found = faces.toArray();
            if (found.length == 0) {
                return null;
            }
            Rect largest = Arrays.stream(found).max(Comparator.comparingInt(r -> r.width * r.height)).orElseThrow();
            Mat crop = gray.submat(largest);
            Mat out = new Mat();
            Imgproc.resize(crop, out, new Size(100, 100));
            Imgproc.equalizeHist(out, out);
            crop.release();
            return out;
        } finally {
            color.release();
            gray.release();
            faces.release();
        }
    }

    private static double histogramCorrelation(Mat a, Mat b) {
        Mat ha = new Mat();
        Mat hb = new Mat();
        MatOfInt channels = new MatOfInt(0);
        MatOfInt size = new MatOfInt(64);
        MatOfFloat ranges = new MatOfFloat(0f, 256f);
        try {
            Imgproc.calcHist(List.of(a), channels, new Mat(), ha, size, ranges);
            Imgproc.calcHist(List.of(b), channels, new Mat(), hb, size, ranges);
            Core.normalize(ha, ha, 0, 1, Core.NORM_MINMAX);
            Core.normalize(hb, hb, 0, 1, Core.NORM_MINMAX);
            return Imgproc.compareHist(ha, hb, Imgproc.HISTCMP_CORREL);
        } finally {
            ha.release();
            hb.release();
            channels.release();
            size.release();
            ranges.release();
        }
    }
}

package com.placify.proctoring.vision;

/**
 * Compares the face in two images (identity check).
 */
public interface FaceMatcher {

    /**
     * @return similarity 0..1, or a negative value when no face could be found in one of the images
     */
    double similarity(byte[] imageA, byte[] imageB);

    String name();
}

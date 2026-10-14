package com.placify.proctoring.vision;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads the OpenCV native library (bundled by org.openpnp:opencv) and the Haar
 * cascade models. Cascade classifiers are not shared between threads, so every
 * worker thread gets its own instances.
 */
@Slf4j
@Component
public class OpenCvSupport {

    private volatile boolean available;
    private String frontalPath;
    private String profilePath;
    private String eyePath;

    private final ThreadLocal<CascadeClassifier> frontal = ThreadLocal.withInitial(() -> new CascadeClassifier(frontalPath));
    private final ThreadLocal<CascadeClassifier> profile = ThreadLocal.withInitial(() -> new CascadeClassifier(profilePath));
    private final ThreadLocal<CascadeClassifier> eyes = ThreadLocal.withInitial(() -> new CascadeClassifier(eyePath));

    @PostConstruct
    void init() {
        try {
            OpenCV.loadLocally();
            Path dir = Files.createTempDirectory("placify-cascades");
            frontalPath = copy("haarcascades/haarcascade_frontalface_default.xml", dir);
            profilePath = copy("haarcascades/haarcascade_profileface.xml", dir);
            eyePath = copy("haarcascades/haarcascade_eye.xml", dir);
            available = true;
            log.info("OpenCV loaded; camera proctoring is active");
        } catch (Throwable ex) {
            available = false;
            log.error("OpenCV could not be loaded - camera analysis is disabled: {}", ex.getMessage());
        }
    }

    private static String copy(String resource, Path dir) throws IOException {
        Path target = dir.resolve(Path.of(resource).getFileName().toString());
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toAbsolutePath().toString();
    }

    public boolean available() {
        return available;
    }

    public CascadeClassifier frontalFace() {
        return frontal.get();
    }

    public CascadeClassifier profileFace() {
        return profile.get();
    }

    public CascadeClassifier eyes() {
        return eyes.get();
    }

    /** Decodes JPEG/PNG bytes into a colour image (empty Mat if invalid). */
    public static Mat decode(byte[] bytes) {
        MatOfByte buffer = new MatOfByte(bytes);
        try {
            return Imgcodecs.imdecode(buffer, Imgcodecs.IMREAD_COLOR);
        } finally {
            buffer.release();
        }
    }
}

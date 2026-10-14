package com.placify.proctoring.vision;

import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Pipeline;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Identity check with a deep face-embedding model run in Java by DJL
 * (e.g. a FaceNet/ArcFace TorchScript model producing a feature vector).
 * OpenCV finds and crops the face, the model turns it into an embedding and the
 * cosine similarity of the two embeddings is the identity score.
 * Enabled by setting placify.proctoring.face-model-url.
 */
@Slf4j
public class DjlFaceMatcher implements FaceMatcher, AutoCloseable {

    private final OpenCvSupport cv;
    private final ZooModel<Image, float[]> model;

    public DjlFaceMatcher(OpenCvSupport cv, String modelUrl) throws Exception {
        this.cv = cv;
        Criteria<Image, float[]> criteria = Criteria.builder()
                .setTypes(Image.class, float[].class)
                .optModelUrls(modelUrl)
                .optTranslator(new FaceFeatureTranslator())
                .optEngine("PyTorch")
                .build();
        this.model = criteria.loadModel();
        log.info("Loaded DJL face model from {}", modelUrl);
    }

    @Override
    public String name() {
        return "DJL face embeddings";
    }

    @Override
    public double similarity(byte[] imageA, byte[] imageB) {
        try (Predictor<Image, float[]> predictor = model.newPredictor()) {
            Image a = cropFace(imageA);
            Image b = cropFace(imageB);
            if (a == null || b == null) {
                return -1;
            }
            float[] ea = predictor.predict(a);
            float[] eb = predictor.predict(b);
            return Math.max(0, cosine(ea, eb));
        } catch (Exception ex) {
            log.warn("Face embedding failed: {}", ex.getMessage());
            return -1;
        }
    }

    private Image cropFace(byte[] bytes) throws Exception {
        Mat color = OpenCvSupport.decode(bytes);
        Mat gray = new Mat();
        MatOfRect faces = new MatOfRect();
        MatOfByte encoded = new MatOfByte();
        try {
            if (color.empty()) {
                return null;
            }
            Imgproc.cvtColor(color, gray, Imgproc.COLOR_BGR2GRAY);
            cv.frontalFace().detectMultiScale(gray, faces, 1.1, 5, 0, new Size(40, 40), new Size());
            Rect[] found = faces.toArray();
            if (found.length == 0) {
                return null;
            }
            Rect largest = Arrays.stream(found).max(Comparator.comparingInt(r -> r.width * r.height)).orElseThrow();
            Mat crop = color.submat(largest);
            Imgcodecs.imencode(".jpg", crop, encoded);
            crop.release();
            return ImageFactory.getInstance().fromInputStream(new ByteArrayInputStream(encoded.toArray()));
        } finally {
            color.release();
            gray.release();
            faces.release();
            encoded.release();
        }
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    @Override
    public void close() {
        model.close();
    }

    /** 112x112 RGB, normalised to [-1, 1], output = embedding vector. */
    static final class FaceFeatureTranslator implements Translator<Image, float[]> {

        private final Pipeline pipeline = new Pipeline()
                .add(new Resize(112, 112))
                .add(new ToTensor())
                .add(new Normalize(new float[]{0.5f, 0.5f, 0.5f}, new float[]{0.5f, 0.5f, 0.5f}));

        @Override
        public NDList processInput(TranslatorContext ctx, Image input) {
            NDArray array = input.toNDArray(ctx.getNDManager(), Image.Flag.COLOR);
            return pipeline.transform(new NDList(array));
        }

        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            return list.singletonOrThrow().toFloatArray();
        }
    }
}

package com.placify.ai.embedding;

import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Semantic similarity with sentence embeddings (all-MiniLM-L6-v2) computed in
 * the JVM by DJL. Used by the ATS scorer and job matching. When the model is
 * not available a term-frequency cosine similarity is used instead.
 */
@Slf4j
@Service
public class EmbeddingService {

    @Value("${placify.ai.embedding-model-url}")
    private String modelUrl;

    @Value("${placify.ai.embeddings-enabled:true}")
    private boolean enabled;

    private volatile ZooModel<String, float[]> model;

    /** Small LRU cache: the same job description is compared with many students. */
    private final Map<String, float[]> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
            return size() > 500;
        }
    };

    public record Similarity(double similarity, String method) {
    }

    @PostConstruct
    void load() {
        if (!enabled) {
            log.info("Embeddings disabled; using term-frequency similarity");
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Criteria<String, float[]> criteria = Criteria.builder()
                        .setTypes(String.class, float[].class)
                        .optModelUrls(modelUrl)
                        .optEngine("PyTorch")
                        .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                        .build();
                model = criteria.loadModel();
                log.info("Sentence embedding model loaded: {}", modelUrl);
            } catch (Exception | Error ex) {
                log.warn("Embedding model unavailable ({}); using term-frequency similarity", ex.getMessage());
            }
        }, "embedding-model-loader");
        t.setDaemon(true);
        t.start();
    }

    public Similarity similarity(String a, String b) {
        ZooModel<String, float[]> m = model;
        if (m != null) {
            try {
                float[] ea = embed(m, a);
                float[] eb = embed(m, b);
                return new Similarity(cosine(ea, eb), "embedding");
            } catch (Exception ex) {
                log.debug("Embedding failed: {}", ex.getMessage());
            }
        }
        return new Similarity(termCosine(a, b), "term-frequency");
    }

    private float[] embed(ZooModel<String, float[]> m, String text) throws Exception {
        String key = Integer.toHexString(text.hashCode()) + ":" + text.length();
        synchronized (cache) {
            float[] cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        float[] vector;
        try (Predictor<String, float[]> predictor = m.newPredictor()) {
            vector = predictor.predict(text.length() > 4000 ? text.substring(0, 4000) : text);
        }
        synchronized (cache) {
            cache.put(key, vector);
        }
        return vector;
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
        return na == 0 || nb == 0 ? 0 : Math.max(0, dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }

    /** Fallback: cosine of word-frequency vectors. */
    static double termCosine(String a, String b) {
        Map<String, Integer> fa = freq(a);
        Map<String, Integer> fb = freq(b);
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (Map.Entry<String, Integer> e : fa.entrySet()) {
            na += e.getValue() * e.getValue();
            Integer o = fb.get(e.getKey());
            if (o != null) {
                dot += e.getValue() * o;
            }
        }
        for (int v : fb.values()) {
            nb += v * v;
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static Map<String, Integer> freq(String text) {
        Map<String, Integer> m = new HashMap<>();
        if (text == null) {
            return m;
        }
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#]+")) {
            if (w.length() > 2) {
                m.merge(w, 1, Integer::sum);
            }
        }
        return m;
    }

    @PreDestroy
    void close() {
        if (model != null) {
            model.close();
        }
    }
}

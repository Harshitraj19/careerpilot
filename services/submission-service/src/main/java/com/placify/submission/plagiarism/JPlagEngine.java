package com.placify.submission.plagiarism;

import de.jplag.JPlag;
import de.jplag.JPlagComparison;
import de.jplag.JPlagResult;
import de.jplag.Language;
import de.jplag.options.JPlagOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Adapter around the JPlag library (token-based plagiarism detection used by
 * universities). Kept in one class so a JPlag version change only touches this file.
 */
@Slf4j
@Component
public class JPlagEngine {

    public record Pair(String keyA, String keyB, double similarity) {
    }

    /** Languages with a JPlag front-end on the classpath (see build.gradle). */
    public Optional<Language> languageFor(com.placify.submission.domain.Language lang) {
        return switch (lang) {
            case JAVA -> Optional.of(new de.jplag.java.JavaLanguage());
            case C, CPP -> Optional.of(new de.jplag.cpp.CPPLanguage());
            case PYTHON -> Optional.of(new de.jplag.python3.PythonLanguage());
            default -> Optional.empty();
        };
    }

    /**
     * @param codeByKey submission key -> source code (all in the same language)
     * @return all compared pairs with similarity 0..1
     */
    public List<Pair> compare(Language language, String extension, Map<String, String> codeByKey) throws Exception {
        Path root = Files.createTempDirectory("placify-jplag-");
        try {
            for (Map.Entry<String, String> e : codeByKey.entrySet()) {
                Path dir = Files.createDirectories(root.resolve(e.getKey()));
                Files.writeString(dir.resolve("Main." + extension), e.getValue(), StandardCharsets.UTF_8);
            }
            JPlagOptions options = new JPlagOptions(language, Set.of(root.toFile()), Set.<File>of());
            JPlagResult result = JPlag.run(options);
            List<Pair> pairs = new ArrayList<>();
            for (JPlagComparison c : result.getAllComparisons()) {
                pairs.add(new Pair(c.firstSubmission().getName(), c.secondSubmission().getName(), c.similarity()));
            }
            return pairs;
        } finally {
            deleteQuietly(root);
        }
    }

    private static void deleteQuietly(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // temp files are cleaned by the OS eventually
                }
            });
        } catch (IOException ex) {
            log.debug("Could not clean {}", root);
        }
    }
}

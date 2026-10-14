package com.placify.submission.client;

import com.placify.submission.domain.Language;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles and runs code on this machine when no Judge0 server is configured, so code execution
 * works out of the box for local development and demos. Results are returned in Judge0's response
 * shape (status id/description, stdout, stderr, compile_output, time), so the grading pipeline is
 * the same for both runners.
 * <p>
 * The code runs as a normal process with a wall-clock time limit and output limit, but without
 * Judge0's sandbox. Use Judge0 when untrusted users can submit code.
 */
@Slf4j
@Component
public class LocalCodeRunner {

    private static final int MAX_OUTPUT = 256 * 1024;
    private static final Pattern PUBLIC_CLASS = Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z_]\\w*)");
    private static final Pattern MAIN_CLASS = Pattern.compile("class\\s+([A-Za-z_]\\w*)[^{]*\\{[^}]*?static\\s+void\\s+main", Pattern.DOTALL);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private final Path workRoot;
    private final double defaultTimeLimit;
    private final String pythonCommand;
    private final Semaphore slots;
    private final Map<String, Program> compiled = new ConcurrentHashMap<>();
    private final Map<Language, Boolean> available = new ConcurrentHashMap<>();

    /** A compiled (or interpreted) program ready to run, or the compile error. */
    private record Program(Path dir, List<String> command, String compileError, long createdAt) {
    }

    public LocalCodeRunner(@Value("${placify.judge0.local-work-dir:}") String workDir,
                           @Value("${placify.judge0.local-time-limit-seconds:5}") double defaultTimeLimit,
                           @Value("${placify.judge0.local-python:}") String python,
                           @Value("${placify.judge0.local-parallel-runs:2}") int parallel) throws IOException {
        this.workRoot = workDir == null || workDir.isBlank()
                ? Files.createTempDirectory("placify-judge")
                : Files.createDirectories(Path.of(workDir));
        this.defaultTimeLimit = defaultTimeLimit;
        this.pythonCommand = python == null || python.isBlank() ? (WINDOWS ? "python" : "python3") : python;
        this.slots = new Semaphore(Math.max(1, parallel));
    }

    /** Runs the source against one input. {@code expected} null means "just run" (no verdict). */
    public Map<String, Object> run(int languageId, String source, String stdin, String expected, Double timeLimitSeconds) {
        Language language = Language.byJudge0Id(languageId).orElse(null);
        if (language == null) {
            return result(13, "Internal Error", null, "Unsupported language", null, null);
        }
        Program program;
        try {
            program = compile(language, source == null ? "" : source);
        } catch (IOException ex) {
            log.warn("Local compile failed: {}", ex.getMessage());
            return result(13, "Internal Error", null, ex.getMessage(), null, null);
        }
        if (program.compileError() != null) {
            return result(6, "Compilation Error", null, null, program.compileError(), null);
        }
        double limit = timeLimitSeconds == null || timeLimitSeconds <= 0 ? defaultTimeLimit : timeLimitSeconds;
        try {
            slots.acquire();
            try {
                return execute(program, stdin == null ? "" : stdin, expected, limit);
            } finally {
                slots.release();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return result(13, "Internal Error", null, "Interrupted", null, null);
        }
    }

    /** Whether the toolchain for a language exists on this machine (checked once). */
    public boolean supports(Language language) {
        return available.computeIfAbsent(language, l -> {
            List<String> probe = switch (l) {
                case JAVA -> List.of(javac(), "-version");
                case PYTHON -> List.of(pythonCommand, "--version");
                case C -> List.of("gcc", "--version");
                case CPP -> List.of("g++", "--version");
                case JAVASCRIPT -> List.of("node", "--version");
                case GO -> List.of("go", "version");
                case RUST -> List.of("rustc", "--version");
            };
            try {
                Process p = new ProcessBuilder(probe).redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (IOException ex) {
                return false;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        });
    }

    // ---------------------------------------------------------------- compile

    private Program compile(Language language, String source) throws IOException {
        String key = language.name() + ":" + sha256(source);
        Program cached = compiled.get(key);
        if (cached != null) {
            return cached;
        }
        evictOld();
        Path dir = Files.createTempDirectory(workRoot, language.name().toLowerCase(Locale.ROOT) + "-");
        String exe = WINDOWS ? "prog.exe" : "prog";
        Program program = switch (language) {
            case JAVA -> {
                String cls = javaClassName(source);
                Files.writeString(dir.resolve(cls + ".java"), source, StandardCharsets.UTF_8);
                String err = build(dir, List.of(javac(), "-encoding", "UTF-8", "-nowarn", "-d", ".", cls + ".java"));
                yield new Program(dir, List.of(java(), "-Xss64m", "-Xmx256m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1",
                        "-Dfile.encoding=UTF-8", "-cp", ".", cls), err, System.currentTimeMillis());
            }
            case PYTHON -> {
                Files.writeString(dir.resolve("main.py"), source, StandardCharsets.UTF_8);
                yield new Program(dir, List.of(pythonCommand, "-I", "main.py"), null, System.currentTimeMillis());
            }
            case JAVASCRIPT -> {
                Files.writeString(dir.resolve("main.js"), source, StandardCharsets.UTF_8);
                yield new Program(dir, List.of("node", "main.js"), null, System.currentTimeMillis());
            }
            case C -> {
                Files.writeString(dir.resolve("main.c"), source, StandardCharsets.UTF_8);
                String err = build(dir, List.of("gcc", "-O2", "-std=c11", "-o", exe, "main.c", "-lm"));
                yield new Program(dir, List.of(dir.resolve(exe).toString()), err, System.currentTimeMillis());
            }
            case CPP -> {
                Files.writeString(dir.resolve("main.cpp"), source, StandardCharsets.UTF_8);
                String err = build(dir, List.of("g++", "-O2", "-std=c++14", "-o", exe, "main.cpp"));
                yield new Program(dir, List.of(dir.resolve(exe).toString()), err, System.currentTimeMillis());
            }
            case GO -> {
                Files.writeString(dir.resolve("main.go"), source, StandardCharsets.UTF_8);
                String err = build(dir, List.of("go", "build", "-o", exe, "main.go"));
                yield new Program(dir, List.of(dir.resolve(exe).toString()), err, System.currentTimeMillis());
            }
            case RUST -> {
                Files.writeString(dir.resolve("main.rs"), source, StandardCharsets.UTF_8);
                String err = build(dir, List.of("rustc", "-O", "-o", exe, "main.rs"));
                yield new Program(dir, List.of(dir.resolve(exe).toString()), err, System.currentTimeMillis());
            }
        };
        compiled.put(key, program);
        return program;
    }

    /** Runs a compiler; returns its error output, or null when it succeeded. */
    private String build(Path dir, List<String> command) {
        try {
            Process p = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
            p.getOutputStream().close();
            CompletableFuture<String> out = drain(p.getInputStream());
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "Compilation took too long";
            }
            String text = out.get(5, TimeUnit.SECONDS);
            return p.exitValue() == 0 ? null : (text.isBlank() ? "Compilation failed" : text);
        } catch (IOException ex) {
            return command.get(0) + " is not installed on the code-execution server";
        } catch (Exception ex) {
            return "Compilation failed: " + ex.getMessage();
        }
    }

    // ---------------------------------------------------------------- run

    private Map<String, Object> execute(Program program, String stdin, String expected, double limitSeconds) {
        long start = System.nanoTime();
        Process p;
        try {
            p = new ProcessBuilder(program.command()).directory(program.dir().toFile()).start();
        } catch (IOException ex) {
            return result(6, "Compilation Error", null, null, program.command().get(0)
                    + " is not installed on the code-execution server", null);
        }
        CompletableFuture<String> out = drain(p.getInputStream());
        CompletableFuture<String> err = drain(p.getErrorStream());
        CompletableFuture.runAsync(() -> {
            try (OutputStream in = p.getOutputStream()) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // the program may exit without reading its input
            }
        });
        boolean finished;
        try {
            finished = p.waitFor((long) (limitSeconds * 1000), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        String time = String.format(Locale.ROOT, "%.3f", (System.nanoTime() - start) / 1e9);
        if (!finished) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            return result(5, "Time Limit Exceeded", text(out), text(err), null, time);
        }
        String stdout = text(out);
        String stderr = text(err);
        if (p.exitValue() != 0) {
            return result(11, "Runtime Error (NZEC)", stdout, stderr.isBlank() ? "Exit code " + p.exitValue() : stderr, null, time);
        }
        if (expected == null || normalize(stdout).equals(normalize(expected))) {
            return result(3, "Accepted", stdout, stderr, null, time);
        }
        return result(4, "Wrong Answer", stdout, stderr, null, time);
    }

    /** Judge0 compares output ignoring trailing spaces and blank lines; so do we. */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String[] lines = s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.stripTrailing()).append('\n');
        }
        return sb.toString().strip();
    }

    private static CompletableFuture<String> drain(InputStream in) {
        return CompletableFuture.supplyAsync(() -> {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            try (in) {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    if (buf.size() < MAX_OUTPUT) {
                        buf.write(chunk, 0, Math.min(n, MAX_OUTPUT - buf.size()));
                    }
                }
            } catch (IOException ignored) {
                // process was killed
            }
            return buf.toString(StandardCharsets.UTF_8);
        });
    }

    private static String text(CompletableFuture<String> f) {
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (Exception ex) {
            return "";
        }
    }

    private static Map<String, Object> result(int id, String description, String stdout, String stderr,
                                              String compileOutput, String time) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("id", id);
        status.put("description", description);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", status);
        r.put("stdout", stdout);
        r.put("stderr", stderr == null || stderr.isBlank() ? null : stderr);
        r.put("compile_output", compileOutput);
        r.put("time", time);
        r.put("memory", null);
        return r;
    }

    // ---------------------------------------------------------------- helpers

    static String javaClassName(String source) {
        Matcher m = PUBLIC_CLASS.matcher(source);
        if (m.find()) {
            return m.group(1);
        }
        m = MAIN_CLASS.matcher(source);
        return m.find() ? m.group(1) : "Main";
    }

    private static String javac() {
        Path p = Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "javac.exe" : "javac");
        return Files.exists(p) ? p.toString() : "javac";
    }

    private static String java() {
        return Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString();
    }

    private void evictOld() {
        long cutoff = System.currentTimeMillis() - 15 * 60_000L;
        compiled.entrySet().removeIf(e -> {
            if (e.getValue().createdAt() < cutoff) {
                delete(e.getValue().dir());
                return true;
            }
            return false;
        });
    }

    private static void delete(Path dir) {
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @PreDestroy
    void cleanUp() {
        delete(workRoot);
    }

    /** Languages in display order; used by the language list. */
    public List<Language> supportedLanguages() {
        List<Language> list = new ArrayList<>();
        for (Language l : Language.values()) {
            if (supports(l)) {
                list.add(l);
            }
        }
        return list;
    }
}

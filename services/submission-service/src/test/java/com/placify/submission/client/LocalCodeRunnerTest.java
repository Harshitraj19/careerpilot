package com.placify.submission.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCodeRunnerTest {

    @TempDir
    Path work;

    @Test
    void outputComparisonIgnoresTrailingSpacesAndLineEndings() {
        assertThat(LocalCodeRunner.normalize("YES  \r\n\r\n")).isEqualTo(LocalCodeRunner.normalize("YES"));
        assertThat(LocalCodeRunner.normalize("1 2\n3")).isNotEqualTo(LocalCodeRunner.normalize("1 2 3"));
    }

    @Test
    void findsTheJavaClassToRun() {
        assertThat(LocalCodeRunner.javaClassName("import java.util.*;\npublic class Solution { }")).isEqualTo("Solution");
        assertThat(LocalCodeRunner.javaClassName("class A { public static void main(String[] a) {} }")).isEqualTo("A");
        assertThat(LocalCodeRunner.javaClassName("int x;")).isEqualTo("Main");
    }

    @Test
    @SuppressWarnings("unchecked")
    void compilesRunsAndJudgesJava() throws Exception {
        LocalCodeRunner runner = new LocalCodeRunner(work.toString(), 10, "", 1);
        String code = "import java.util.*; public class Main { public static void main(String[] a) {"
                + " Scanner s = new Scanner(System.in); System.out.println(s.nextInt() + s.nextInt()); } }";
        Map<String, Object> ok = runner.run(62, code, "2 3", "5", null);
        Map<String, Object> wrong = runner.run(62, code, "2 3", "6", null);
        Map<String, Object> broken = runner.run(62, "public class Main { void x( }", "", "", null);
        assertThat(((Map<String, Object>) ok.get("status")).get("id")).isEqualTo(3);
        assertThat(((Map<String, Object>) wrong.get("status")).get("id")).isEqualTo(4);
        assertThat(((Map<String, Object>) broken.get("status")).get("id")).isEqualTo(6);
    }
}

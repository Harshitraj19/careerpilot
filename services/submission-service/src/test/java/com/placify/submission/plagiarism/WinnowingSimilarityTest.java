package com.placify.submission.plagiarism;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WinnowingSimilarityTest {

    private static final String ORIGINAL = """
            import java.util.*;
            public class Main {
                public static void main(String[] args) {
                    Scanner sc = new Scanner(System.in);
                    int n = sc.nextInt();
                    long best = Long.MIN_VALUE, cur = 0;
                    for (int i = 0; i < n; i++) {
                        long x = sc.nextLong();
                        cur = Math.max(x, cur + x);
                        best = Math.max(best, cur);
                    }
                    System.out.println(best);
                }
            }
            """;

    @Test
    void renamedVariablesAndCommentsAreStillDetected() {
        String disguised = ORIGINAL.replace("best", "answer").replace("cur", "running")
                .replace("int n", "int count").replace("i < n", "i < count") + "\n// my own solution\n";
        assertThat(WinnowingSimilarity.similarity(ORIGINAL, disguised)).isGreaterThan(0.8);
    }

    @Test
    void differentProgramsAreNotSimilar() {
        String other = """
                n = int(input())
                primes = [True] * (n + 1)
                for i in range(2, int(n ** 0.5) + 1):
                    if primes[i]:
                        for j in range(i * i, n + 1, i):
                            primes[j] = False
                print(sum(primes[2:]))
                """;
        assertThat(WinnowingSimilarity.similarity(ORIGINAL, other)).isLessThan(0.3);
    }

    @Test
    void identicalCodeIsFullySimilar() {
        assertThat(WinnowingSimilarity.similarity(ORIGINAL, ORIGINAL)).isEqualTo(1.0);
    }
}

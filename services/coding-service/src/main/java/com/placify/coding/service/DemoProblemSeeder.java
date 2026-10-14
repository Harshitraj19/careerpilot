package com.placify.coding.service;

import com.placify.coding.domain.Question;
import com.placify.coding.domain.Testcase;
import com.placify.coding.repository.QuestionRepository;
import com.placify.coding.repository.TestcaseRepository;
import com.placify.common.demo.DemoData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Demo problems with public and hidden test cases (DEMO_DATA=true): practice problems for every
 * student, and test-only problems used in the drives' online tests (not visible in practice).
 * Problems are added by title, so new ones also reach existing databases.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = DemoData.PROPERTY, havingValue = "true")
public class DemoProblemSeeder implements ApplicationRunner {

    private final QuestionRepository questions;
    private final TestcaseRepository testcases;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        add("Sum of Two Numbers", Question.Difficulty.EASY, "math, implementation",
                "Read two integers a and b and print their sum.",
                "One line with two integers a and b.", "Print a + b.", "-10^9 <= a, b <= 10^9",
                new String[][] {{"2 3", "5"}, {"-4 10", "6"}, {"1000000000 1000000000", "2000000000"}, {"0 0", "0"}});
        add("Reverse a String", Question.Difficulty.EASY, "strings",
                "Given a string s, print it reversed.", "A single line containing s (no spaces).", "The reversed string.",
                "1 <= |s| <= 10^5",
                new String[][] {{"placify", "yfilcalp"}, {"a", "a"}, {"racecar", "racecar"}, {"OpenAI2026", "6202IAnepO"}});
        add("Balanced Brackets", Question.Difficulty.MEDIUM, "stack, strings",
                "Given a string of brackets ()[]{}, print YES if every bracket is closed in the right order, otherwise NO.",
                "A single line with the bracket string.", "YES or NO.", "1 <= length <= 10^5",
                new String[][] {{"{[()]}", "YES"}, {"([)]", "NO"}, {"((()", "NO"}, {"()[]{}", "YES"}, {"]", "NO"}});
        add("Maximum Subarray Sum", Question.Difficulty.MEDIUM, "arrays, dynamic programming, kadane",
                "Given n integers, find the largest sum of a contiguous non-empty subarray.",
                "First line n. Second line n integers.", "The maximum subarray sum.", "1 <= n <= 2*10^5, |a[i]| <= 10^9",
                new String[][] {{"5\n-2 1 -3 4 -1", "4"}, {"9\n-2 1 -3 4 -1 2 1 -5 4", "6"}, {"3\n-5 -2 -9", "-2"}, {"1\n7", "7"}});
        add("Count Primes", Question.Difficulty.MEDIUM, "math, sieve",
                "Print how many prime numbers are less than or equal to n.", "A single integer n.", "The number of primes <= n.",
                "1 <= n <= 10^7",
                new String[][] {{"10", "4"}, {"1", "0"}, {"2", "1"}, {"100", "25"}, {"1000000", "78498"}});
        add("Longest Increasing Subsequence", Question.Difficulty.HARD, "dynamic programming, binary search",
                "Given n integers, print the length of the longest strictly increasing subsequence.",
                "First line n. Second line n integers.", "Length of the LIS.", "1 <= n <= 2*10^5",
                new String[][] {{"8\n10 9 2 5 3 7 101 18", "4"}, {"6\n0 1 0 3 2 3", "4"}, {"5\n7 7 7 7 7", "1"}, {"1\n42", "1"}});
        add("Nth Fibonacci Number", Question.Difficulty.EASY, "math, dynamic programming",
                "F(0) = 0, F(1) = 1 and F(n) = F(n-1) + F(n-2). Print F(n).", "A single integer n.", "F(n).", "0 <= n <= 90",
                new String[][] {{"10", "55"}, {"0", "0"}, {"1", "1"}, {"50", "12586269025"}, {"90", "2880067194370816120"}});
        add("Valid Anagram", Question.Difficulty.EASY, "strings, hashing",
                "Two lowercase words are given. Print YES if one is an anagram of the other, otherwise NO.",
                "One line with two words separated by a space.", "YES or NO.", "1 <= length <= 10^5",
                new String[][] {{"listen silent", "YES"}, {"rat car", "NO"}, {"a a", "YES"}, {"aab abb", "NO"}});
        add("First Occurrence in a Sorted Array", Question.Difficulty.MEDIUM, "binary search, arrays",
                "Given a sorted array of n integers and a value x, print the 0-based index of the first occurrence of x, or -1.",
                "First line n and x. Second line n sorted integers.", "The index or -1.", "1 <= n <= 2*10^5",
                new String[][] {{"6 4\n1 2 4 4 4 9", "2"}, {"5 3\n1 2 4 5 6", "-1"}, {"1 7\n7", "0"}, {"7 1\n1 1 1 1 1 1 1", "0"}});
        add("Number of Islands", Question.Difficulty.HARD, "graphs, dfs, bfs, grid",
                "A grid of 0s (water) and 1s (land) is given. Count the islands: groups of land cells connected up, down, left or right.",
                "First line r and c. Then r lines of c characters (0 or 1).", "The number of islands.", "1 <= r, c <= 500",
                new String[][] {{"4 5\n11000\n11000\n00100\n00011", "3"}, {"1 1\n0", "0"}, {"3 3\n111\n010\n111", "1"}, {"3 3\n101\n010\n101", "5"}});

        // test-only problems for the online assessments of the demo drives
        addTest("Second Largest Distinct Number", Question.Difficulty.EASY, "arrays",
                "Given n integers, print the second largest distinct value, or -1 if it does not exist.",
                "First line n. Second line n integers.", "The second largest distinct value or -1.", "1 <= n <= 10^5",
                new String[][] {{"5\n3 1 4 4 2", "3"}, {"3\n7 7 7", "-1"}, {"4\n-1 -5 -3 -2", "-2"}, {"2\n10 20", "10"}, {"6\n5 9 1 9 8 3", "8"}});
        addTest("Palindrome Sentence", Question.Difficulty.EASY, "strings, two pointers",
                "Given a sentence, print YES if it reads the same forwards and backwards when only letters and digits are kept and case is ignored, otherwise NO.",
                "A single line of text.", "YES or NO.", "1 <= length <= 10^5",
                new String[][] {{"A man, a plan, a canal: Panama", "YES"}, {"race a car", "NO"}, {"No 'x' in Nixon", "YES"}, {"ab", "NO"}, {"Was it a car or a cat I saw", "YES"}});
        addTest("Count Pairs With Given Sum", Question.Difficulty.MEDIUM, "hashing, arrays",
                "Given n integers and a value k, count the pairs (i, j) with i < j and a[i] + a[j] = k.",
                "First line n and k. Second line n integers.", "The number of pairs.", "1 <= n <= 10^5",
                new String[][] {{"5 6\n1 5 7 -1 5", "3"}, {"4 2\n1 1 1 1", "6"}, {"3 10\n1 2 3", "0"}, {"6 0\n-1 1 -2 2 0 0", "3"}});
        addTest("Merge Overlapping Intervals", Question.Difficulty.MEDIUM, "sorting, intervals",
                "Given n intervals [l, r], merge all overlapping (or touching) intervals and print how many remain.",
                "First line n. Then n lines with l and r.", "The number of intervals after merging.", "1 <= n <= 10^5",
                new String[][] {{"4\n1 3\n2 6\n8 10\n15 18", "3"}, {"2\n1 4\n4 5", "1"}, {"3\n1 2\n3 4\n5 6", "3"}, {"3\n1 10\n2 3\n4 5", "1"}});
    }

    private void addTest(String title, Question.Difficulty d, String tags, String desc, String in, String out, String constraints,
                         String[][] cases) {
        add(title, d, tags, desc, in, out, constraints, cases, Question.Visibility.TEST_ONLY);
    }

    private void add(String title, Question.Difficulty d, String tags, String desc, String in, String out, String constraints,
                     String[][] cases) {
        add(title, d, tags, desc, in, out, constraints, cases, Question.Visibility.PRACTICE);
    }

    private void add(String title, Question.Difficulty d, String tags, String desc, String in, String out, String constraints,
                     String[][] cases, Question.Visibility visibility) {
        if (questions.existsByTitle(title)) {
            return;
        }
        Question q = questions.save(Question.builder()
                .title(title).description(desc).inputFormat(in).outputFormat(out).constraints(constraints)
                .sampleInput(cases[0][0]).sampleOutput(cases[0][1]).difficulty(d).tags(tags).rating(d.defaultRating())
                .visibility(visibility).timeLimit(new BigDecimal("2")).memoryLimit(new BigDecimal("256"))
                .createdBy(DemoData.TPO_ID).active(true).build());
        for (int i = 0; i < cases.length; i++) {
            testcases.save(Testcase.builder().questionId(q.getId()).input(cases[i][0]).expectedOutput(cases[i][1])
                    .hidden(i >= 2).build());
        }
    }
}

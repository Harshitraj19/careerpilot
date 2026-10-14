package com.placify.ai.offline;

import com.placify.ai.domain.MockInterview;
import com.placify.ai.domain.MockInterviewTurn;
import com.placify.ai.offline.InterviewBank.BankQuestion;
import com.placify.ai.web.AiDtos.AnswerEvaluation;
import com.placify.ai.web.AiDtos.InterviewQuestion;
import com.placify.ai.web.AiDtos.InterviewSummary;
import com.placify.common.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Built-in interviewer used when no language model is configured (or it is unavailable): picks
 * questions from the bank to match the role, skills, interview type and difficulty, adapts the
 * difficulty to the last score, never repeats a question, and evaluates and summarises answers.
 */
@Component
@RequiredArgsConstructor
public class OfflineInterviewer {

    /** Core computer-science topics every technical interview may draw from. */
    private static final List<String> CORE = List.of("oop", "dsa", "dbms", "sql", "os", "networks");

    private final InterviewBank bank;

    /** The next question for this interview, given the turns so far. */
    public String nextQuestion(MockInterview interview, List<MockInterviewTurn> previous) {
        Set<String> asked = new LinkedHashSet<>();
        previous.forEach(t -> asked.add(t.getQuestion()));
        int number = previous.size() + 1;
        String wantedType = switch (interview.getType()) {
            case HR -> number == 2 || number == 5 ? "PROJECT" : "HR";
            case TECHNICAL -> number == 3 ? "PROJECT" : "TECHNICAL";
            case MIXED -> number % 2 == 1 ? "TECHNICAL" : (number == 2 ? "PROJECT" : "HR");
        };
        int level = level(interview.getDifficulty());
        if (!previous.isEmpty() && previous.get(previous.size() - 1).getScore() != null) {
            int last = previous.get(previous.size() - 1).getScore();
            level += last >= 8 ? 1 : last <= 4 ? -1 : 0;
        }
        Set<String> topics = SkillCatalog.topicsFor(Texts.toList(interview.getSkills()), interview.getTargetRole());
        Random random = new Random(interview.getId() * 31L + number);
        BankQuestion q = pick(wantedType, topics, Math.max(1, Math.min(3, level)), asked, random);
        if (q == null) {
            q = pick("TECHNICAL", Set.of(), level, asked, random);
        }
        if (q == null) {
            q = pick("HR", Set.of(), level, asked, random);
        }
        return q == null ? "Walk me through a recent problem you solved and how you approached it." : q.question();
    }

    private BankQuestion pick(String type, Set<String> topics, int level, Set<String> asked, Random random) {
        List<BankQuestion> pool = bank.all().stream()
                .filter(b -> b.type().equals(type))
                .filter(b -> !asked.contains(b.question()))
                .toList();
        if (pool.isEmpty()) {
            return null;
        }
        // prefer the candidate's skill topics, then core CS; prefer the target difficulty
        List<BankQuestion> ranked = new ArrayList<>(pool);
        Collections.shuffle(ranked, random);
        ranked.sort(Comparator.comparingInt((BankQuestion b) -> topicRank(b, topics))
                .thenComparingInt(b -> Math.abs(b.level() - level)));
        return ranked.get(0);
    }

    private static int topicRank(BankQuestion b, Set<String> topics) {
        if (!"TECHNICAL".equals(b.type())) {
            return 0;
        }
        if (topics.contains(b.topic())) {
            return 0;
        }
        return CORE.contains(b.topic()) ? 1 : 2;
    }

    public AnswerEvaluation evaluate(String question, String answer) {
        AnswerEvaluator.Evaluation e = AnswerEvaluator.evaluate(bank.find(question).orElse(null), question, answer);
        return new AnswerEvaluation(e.score(), e.feedback(), e.idealPoints());
    }

    public InterviewSummary summarise(MockInterview interview, List<MockInterviewTurn> turns) {
        List<MockInterviewTurn> answered = turns.stream().filter(t -> t.getScore() != null).toList();
        double avg = answered.stream().mapToInt(MockInterviewTurn::getScore).average().orElse(0);
        int overall = (int) Math.round(avg * 10);
        List<String> strengths = new ArrayList<>();
        List<String> improvements = new ArrayList<>();
        Map<String, Integer> weakTopics = new LinkedHashMap<>();
        for (MockInterviewTurn t : answered) {
            BankQuestion b = bank.find(t.getQuestion()).orElse(null);
            String area = b == null ? "general" : label(b);
            if (t.getScore() >= 7 && strengths.size() < 4) {
                strengths.add("Clear, well-supported answer on " + area);
            } else if (t.getScore() <= 5) {
                weakTopics.merge(area, 1, Integer::sum);
            }
        }
        long shortAnswers = answered.stream().filter(t -> TextTools.words(t.getAnswer()) < 40).count();
        if (shortAnswers > answered.size() / 2) {
            improvements.add("Give fuller answers: aim for 45-90 seconds with an explanation and an example");
        }
        if (answered.stream().anyMatch(t -> t.getScore() <= 5)) {
            improvements.add("Cover all the key points listed under each question, especially on " + String.join(", ", weakTopics.keySet()));
        }
        if (interview.getType() != MockInterview.Type.TECHNICAL) {
            improvements.add("Structure behavioural answers with STAR: situation, task, action and a measurable result");
        }
        if (strengths.isEmpty()) {
            strengths.add(answered.isEmpty() ? "You started the interview" : "You attempted every question");
        }
        String verdict = overall >= 80 ? "Ready for most campus interviews - keep practising to stay sharp."
                : overall >= 60 ? "Almost there: polish the weaker areas below and you will be interview-ready."
                : overall >= 40 ? "A fair start: revise the fundamentals and practise answering out loud."
                : "Needs practice: start with the basics and use the key points as a checklist.";
        List<String> revise = new ArrayList<>(weakTopics.keySet());
        if (revise.isEmpty()) {
            revise.add("Mock a harder interview next time");
        }
        return new InterviewSummary(overall, strengths, improvements.stream().limit(4).toList(), verdict, revise);
    }

    /** Ready-made questions with hints for the interview-preparation tool. */
    public List<InterviewQuestion> questions(String role, List<String> skills, int count) {
        Set<String> topics = SkillCatalog.topicsFor(skills, role);
        List<BankQuestion> technical = new ArrayList<>(bank.all().stream().filter(b -> "TECHNICAL".equals(b.type())).toList());
        technical.sort(Comparator.comparingInt((BankQuestion b) -> topicRank(b, topics)).thenComparingInt(BankQuestion::level));
        List<BankQuestion> behavioural = bank.all().stream().filter(BankQuestion::isBehavioural).toList();
        List<InterviewQuestion> out = new ArrayList<>();
        int t = 0;
        int h = 0;
        while (out.size() < count && (t < technical.size() || h < behavioural.size())) {
            boolean takeBehavioural = out.size() % 3 == 2 && h < behavioural.size();
            BankQuestion b = takeBehavioural || t >= technical.size() ? behavioural.get(h++) : technical.get(t++);
            out.add(new InterviewQuestion(b.question(), b.type(), b.difficulty(), b.hint()));
        }
        return out;
    }

    private static String label(BankQuestion b) {
        return switch (b.topic()) {
            case "oop" -> "OOP";
            case "dsa" -> "data structures and algorithms";
            case "dbms" -> "DBMS";
            case "sql" -> "SQL";
            case "os" -> "operating systems";
            case "networks" -> "computer networks";
            case "java" -> "Java";
            case "spring" -> "Spring and API design";
            case "python" -> "Python";
            case "javascript" -> "JavaScript and React";
            case "ml" -> "machine learning";
            case "cloud" -> "cloud and DevOps";
            case "c" -> "C and systems programming";
            case "security" -> "security";
            case "analytics" -> "analytics";
            default -> "PROJECT".equals(b.type()) ? "your projects" : "behavioural questions";
        };
    }

    private static int level(String difficulty) {
        return switch (difficulty == null ? "MEDIUM" : difficulty) {
            case "EASY" -> 1;
            case "HARD" -> 3;
            default -> 2;
        };
    }
}

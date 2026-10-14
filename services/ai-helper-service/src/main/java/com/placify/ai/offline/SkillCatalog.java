package com.placify.ai.offline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Technical and professional skills the offline engine recognises in resumes and job descriptions,
 * with their aliases, and the interview topic each skill belongs to.
 */
final class SkillCatalog {

    /** Display name -> aliases (lower case). The display name itself is always an alias. */
    private static final Map<String, List<String>> SKILLS = new LinkedHashMap<>();
    /** Display name -> interview topic in the question bank. */
    private static final Map<String, String> TOPIC = new LinkedHashMap<>();
    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        add("java", "Java", "core java", "java 8", "java 17", "j2ee");
        add("spring", "Spring Boot", "springboot", "spring framework", "spring mvc", "spring security", "hibernate", "jpa");
        add("python", "Python", "python3");
        add("javascript", "JavaScript", "js", "es6", "ecmascript");
        add("javascript", "TypeScript");
        add("javascript", "React", "react.js", "reactjs");
        add("javascript", "Angular", "angularjs");
        add("javascript", "Vue.js", "vue", "vuejs");
        add("javascript", "Node.js", "nodejs", "express.js", "expressjs");
        add("javascript", "Next.js", "nextjs");
        add("javascript", "HTML", "html5");
        add("javascript", "CSS", "css3", "tailwind", "bootstrap", "sass");
        add("c", "C", "c programming", "embedded c");
        add("c", "C++", "cpp", "c plus plus", "stl");
        add("c", "C#", "csharp", ".net", "asp.net");
        add("c", "Embedded Systems", "embedded", "microcontroller", "arduino", "stm32", "esp32", "rtos", "firmware");
        add("c", "Go", "golang");
        add("c", "Rust");
        add("java", "Kotlin", "android");
        add("sql", "SQL", "mysql", "postgresql", "postgres", "oracle", "pl/sql", "sqlite", "sql server");
        add("dbms", "MongoDB", "mongo", "nosql");
        add("dbms", "Redis");
        add("dbms", "DBMS", "database", "databases", "normalization", "rdbms");
        add("dsa", "Data Structures", "data structure", "dsa", "algorithms", "algorithm", "competitive programming", "leetcode");
        add("oop", "OOP", "oops", "object oriented", "object-oriented", "design patterns");
        add("os", "Operating Systems", "operating system", "linux", "unix", "shell scripting", "bash");
        add("networks", "Computer Networks", "networking", "tcp/ip", "tcp", "dns");
        add("cloud", "AWS", "amazon web services", "ec2", "s3");
        add("cloud", "Azure", "microsoft azure");
        add("cloud", "GCP", "google cloud");
        add("cloud", "Docker", "containers", "containerization");
        add("cloud", "Kubernetes", "k8s");
        add("cloud", "CI/CD", "jenkins", "github actions", "gitlab ci", "devops");
        add("cloud", "Git", "github", "gitlab", "version control");
        add("spring", "REST APIs", "rest api", "rest apis", "restful", "api", "apis", "microservices", "microservice");
        add("spring", "Kafka", "rabbitmq", "message queue");
        add("ml", "Machine Learning", "ml", "scikit-learn", "sklearn");
        add("ml", "Deep Learning", "neural network", "neural networks", "cnn", "rnn", "lstm", "transformers");
        add("ml", "TensorFlow", "keras");
        add("ml", "PyTorch", "torch");
        add("ml", "NLP", "natural language processing", "llm", "bert");
        add("ml", "Computer Vision", "opencv", "image processing");
        add("analytics", "Data Analysis", "data analytics", "pandas", "numpy", "eda");
        add("analytics", "Statistics", "statistical", "probability", "hypothesis testing");
        add("analytics", "Power BI", "powerbi", "tableau");
        add("analytics", "Excel", "ms excel", "spreadsheets");
        add("analytics", "Spark", "pyspark", "hadoop", "big data", "etl", "airflow");
        add("security", "Cyber Security", "cybersecurity", "network security", "penetration testing", "owasp", "siem", "ctf");
        add("general", "Figma", "ui/ux", "ux", "ui design");
        add("general", "Communication", "communication skills", "presentation");
        add("general", "Teamwork", "team player", "collaboration");
        add("general", "Leadership", "team lead", "led a team");
        add("general", "Problem Solving", "problem-solving", "analytical skills");
        for (Map.Entry<String, List<String>> e : SKILLS.entrySet()) {
            String alternatives = String.join("|", e.getValue().stream().map(Pattern::quote).toList());
            PATTERNS.put(e.getKey(), Pattern.compile("(?<![a-z0-9+#.])(?:" + alternatives + ")(?![a-z0-9+#])"));
        }
    }

    private SkillCatalog() {
    }

    private static void add(String topic, String name, String... aliases) {
        List<String> all = new ArrayList<>();
        all.add(name.toLowerCase(Locale.ROOT));
        for (String a : aliases) {
            all.add(a.toLowerCase(Locale.ROOT));
        }
        SKILLS.put(name, all);
        TOPIC.put(name, topic);
    }

    /** Skills found in the text, in catalogue order, using display names. */
    static List<String> find(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Pattern> e : PATTERNS.entrySet()) {
            if (e.getValue().matcher(lower).find()) {
                out.add(e.getKey());
            }
        }
        // "C" alone is too ambiguous in prose: keep it only with other C-family or embedded evidence
        if (out.contains("C") && !(lower.contains("c programming") || lower.contains("embedded") || out.contains("C++"))) {
            out.remove("C");
        }
        if (out.contains("Go") && !lower.contains("golang") && !Pattern.compile("(?<![a-z])go(lang)?,").matcher(lower).find()) {
            out.remove("Go");
        }
        return out;
    }

    /** Interview topics that match a list of free-text skills (unknown skills are ignored). */
    static Set<String> topicsFor(List<String> skills, String role) {
        Set<String> topics = new LinkedHashSet<>();
        List<String> text = new ArrayList<>();
        if (skills != null) {
            text.addAll(skills);
        }
        if (role != null) {
            text.add(role);
        }
        for (String s : text) {
            for (String found : find(s)) {
                topics.add(TOPIC.get(found));
            }
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.contains("data scien") || lower.contains("ml engineer") || lower.contains("ai ")) {
                topics.add("ml");
            }
            if (lower.contains("analyst")) {
                topics.add("analytics");
            }
            if (lower.contains("backend") || lower.contains("back-end")) {
                topics.add("spring");
            }
            if (lower.contains("frontend") || lower.contains("front-end") || lower.contains("full stack") || lower.contains("web")) {
                topics.add("javascript");
            }
            if (lower.contains("cloud") || lower.contains("devops") || lower.contains("sre")) {
                topics.add("cloud");
            }
        }
        topics.remove("general");
        return topics;
    }
}

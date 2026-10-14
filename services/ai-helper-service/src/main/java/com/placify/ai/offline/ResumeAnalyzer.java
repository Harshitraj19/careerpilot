package com.placify.ai.offline;

import com.placify.ai.web.AiDtos.ParsedProject;
import com.placify.ai.web.AiDtos.ParsedResume;
import com.placify.ai.web.AiDtos.ResumeFeedback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based resume reader used when no language model is configured: splits the resume into
 * sections, extracts skills, projects, certifications, internships, achievements and profile
 * links, and gives job-fit feedback against a job description.
 */
@Component
public class ResumeAnalyzer {

    private static final Map<String, Pattern> SECTIONS = new LinkedHashMap<>();
    private static final Pattern GITHUB = Pattern.compile("(?:https?://)?(?:www\\.)?github\\.com/[A-Za-z0-9_.-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINKEDIN = Pattern.compile("(?:https?://)?(?:[a-z]{2,3}\\.)?linkedin\\.com/in/[A-Za-z0-9_-]+/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile("https?://\\S+|(?:github|gitlab)\\.com/\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern METRIC = Pattern.compile("\\d+\\s*(%|percent|\\+|x\\b|users|students|customers|downloads|ms\\b|seconds|hours|k\\b|lakh|crore|rank)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEGREE = Pattern.compile("\\b(b\\.?\\s?tech|b\\.?e\\.?|bachelor|m\\.?\\s?tech|mca|bca|b\\.?sc|m\\.?sc|diploma)\\b[^\\n]{0,80}",
            Pattern.CASE_INSENSITIVE);

    static {
        SECTIONS.put("summary", Pattern.compile("^(professional\\s+)?(summary|profile|objective|career objective|about me)\\b", Pattern.CASE_INSENSITIVE));
        SECTIONS.put("skills", Pattern.compile("^(technical\\s+|key\\s+|core\\s+)?(skills|skill set|technologies|tech stack|tools)\\b", Pattern.CASE_INSENSITIVE));
        SECTIONS.put("projects", Pattern.compile("^(academic\\s+|personal\\s+|key\\s+|major\\s+)?projects?\\b", Pattern.CASE_INSENSITIVE));
        SECTIONS.put("experience", Pattern.compile("^(work\\s+|professional\\s+)?(experience|internships?|employment)\\b", Pattern.CASE_INSENSITIVE));
        SECTIONS.put("certifications", Pattern.compile("^(certifications?|certificates|courses|licenses)\\b", Pattern.CASE_INSENSITIVE));
        SECTIONS.put("achievements", Pattern.compile("^(achievements|awards|honou?rs|accomplishments|extra[- ]?curricular|positions? of responsibility|activities)\\b",
                Pattern.CASE_INSENSITIVE));
        SECTIONS.put("education", Pattern.compile("^(education|academic details|academics|qualifications?)\\b", Pattern.CASE_INSENSITIVE));
    }

    // ---------------------------------------------------------------- parsing

    public ParsedResume parse(String text) {
        Map<String, List<String>> sections = sections(text);
        Set<String> skills = new LinkedHashSet<>(SkillCatalog.find(String.join("\n", sections.getOrDefault("skills", List.of()))));
        skills.addAll(SkillCatalog.find(text));
        skills.removeAll(List.of("Communication", "Teamwork", "Leadership", "Problem Solving"));

        List<String> certifications = clean(sections.getOrDefault("certifications", List.of()), 8);
        if (certifications.isEmpty()) {
            certifications = TextTools.lines(text).stream()
                    .filter(l -> l.toLowerCase(Locale.ROOT).contains("certif") && l.length() < 160).map(ResumeAnalyzer::stripBullet).limit(5).toList();
        }
        List<ParsedProject> projects = projects(sections.getOrDefault("projects", List.of()));
        String internships = String.join("\n", clean(sections.getOrDefault("experience", List.of()), 12));
        List<String> achievements = clean(sections.getOrDefault("achievements", List.of()), 8);
        String summary = String.join(" ", clean(sections.getOrDefault("summary", List.of()), 6));
        if (summary.isBlank()) {
            summary = composeSummary(text, new ArrayList<>(skills), projects);
        }
        return new ParsedResume(new ArrayList<>(skills), certifications, projects, internships.isBlank() ? null : internships,
                achievements, summary, link(GITHUB, text), link(LINKEDIN, text));
    }

    /** Lines grouped under the section heading they follow; lines before any heading go under "header". */
    static Map<String, List<String>> sections(String text) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        String current = "header";
        for (String line : TextTools.lines(text)) {
            String heading = heading(line);
            if (heading != null) {
                current = heading;
                String rest = line.replaceFirst("^[^:]{1,40}:\\s*", "");
                if (!rest.equals(line) && !rest.isBlank()) {
                    out.computeIfAbsent(current, k -> new ArrayList<>()).add(rest);
                }
                continue;
            }
            out.computeIfAbsent(current, k -> new ArrayList<>()).add(line);
        }
        return out;
    }

    private static String heading(String line) {
        String t = stripBullet(line).replaceAll("[:\\-–_|]+$", "").trim();
        if (t.length() > 42 || t.split("\\s+").length > 5) {
            String beforeColon = line.contains(":") ? line.substring(0, line.indexOf(':')).trim() : null;
            if (beforeColon == null || beforeColon.length() > 30) {
                return null;
            }
            t = beforeColon;
        }
        for (Map.Entry<String, Pattern> e : SECTIONS.entrySet()) {
            if (e.getValue().matcher(t).find()) {
                return e.getKey();
            }
        }
        return null;
    }

    private static List<ParsedProject> projects(List<String> lines) {
        List<ParsedProject> out = new ArrayList<>();
        String title = null;
        List<String> body = new ArrayList<>();
        for (String raw : lines) {
            String line = stripBullet(raw);
            boolean bullet = !line.equals(raw.strip());
            boolean looksTitle = !bullet && line.split("\\s+").length <= 12 && !line.endsWith(".")
                    && (title == null || !body.isEmpty() || line.contains("|") || line.contains("(") || line.contains(" - "));
            if (looksTitle) {
                if (title != null) {
                    out.add(project(title, body));
                }
                title = line;
                body = new ArrayList<>();
            } else if (title != null) {
                body.add(line);
            } else {
                title = line.length() > 80 ? line.substring(0, 80) : line;
            }
            if (out.size() >= 6) {
                break;
            }
        }
        if (title != null && out.size() < 6) {
            out.add(project(title, body));
        }
        return out;
    }

    private static ParsedProject project(String titleLine, List<String> body) {
        String block = titleLine + "\n" + String.join("\n", body);
        String title = titleLine.split("\\s[|–-]\\s|\\(", 2)[0].trim();
        Matcher stackMatcher = Pattern.compile("(?i)(tech(?:nolog(?:y|ies))?(?:\\s*stack)?|built with|tools)\\s*[:\\-]\\s*([^\\n]+)").matcher(block);
        String stack = stackMatcher.find() ? stackMatcher.group(2).trim() : String.join(", ", SkillCatalog.find(block).stream().limit(5).toList());
        Matcher url = URL.matcher(block);
        String description = String.join(" ", body.stream().filter(l -> !l.toLowerCase(Locale.ROOT).matches("^(tech|technolog|built with|tools).*")).toList());
        if (description.length() > 400) {
            description = description.substring(0, 400) + "...";
        }
        return new ParsedProject(title, description.isBlank() ? null : description, stack.isBlank() ? null : stack,
                url.find() ? url.group().replaceAll("[),.;]+$", "") : null);
    }

    private static String composeSummary(String text, List<String> skills, List<ParsedProject> projects) {
        Matcher degree = DEGREE.matcher(text);
        String who = degree.find() ? degree.group().replaceAll("\\s+", " ").trim() + " student" : "Student";
        if (who.length() > 90) {
            who = who.substring(0, 90);
        }
        StringBuilder sb = new StringBuilder(who);
        if (!skills.isEmpty()) {
            sb.append(" skilled in ").append(String.join(", ", skills.subList(0, Math.min(4, skills.size()))));
        }
        if (!projects.isEmpty()) {
            sb.append(", with hands-on project experience such as ").append(projects.get(0).title());
        }
        return sb.append('.').toString();
    }

    // ---------------------------------------------------------------- feedback

    public ResumeFeedback feedback(String resume, String jobDescription) {
        boolean hasJd = jobDescription != null && !jobDescription.isBlank();
        List<String> resumeSkills = SkillCatalog.find(resume);
        List<String> jdSkills = hasJd ? SkillCatalog.find(jobDescription) : List.of("Data Structures", "OOP", "SQL", "Git", "Problem Solving");
        List<String> matched = jdSkills.stream().filter(resumeSkills::contains).toList();
        List<String> missing = jdSkills.stream().filter(s -> !resumeSkills.contains(s)).toList();
        double coverage = jdSkills.isEmpty() ? 0.5 : (double) matched.size() / jdSkills.size();

        Map<String, List<String>> sections = sections(resume);
        boolean projects = sections.containsKey("projects");
        boolean experience = sections.containsKey("experience");
        boolean education = sections.containsKey("education") || DEGREE.matcher(resume).find();
        boolean achievements = sections.containsKey("achievements") || sections.containsKey("certifications");
        int metrics = 0;
        Matcher m = METRIC.matcher(resume);
        while (m.find()) {
            metrics++;
        }
        int words = TextTools.words(resume);
        boolean goodLength = words >= 250 && words <= 900;
        boolean links = link(GITHUB, resume) != null || link(LINKEDIN, resume) != null;

        double sectionScore = (projects ? 1 : 0) + (experience ? 1 : 0) + (education ? 1 : 0) + (achievements ? 1 : 0);
        int fit = (int) Math.round(coverage * 60 + sectionScore / 4 * 15 + Math.min(metrics, 3) / 3.0 * 15 + (goodLength ? 6 : 2) + (links ? 4 : 0));
        fit = Math.max(5, Math.min(100, fit));

        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        if (!matched.isEmpty()) {
            strengths.add("Shows " + matched.size() + " of the " + jdSkills.size() + " key skills the role asks for: " + String.join(", ", matched));
        }
        if (projects) {
            strengths.add("Has a projects section that demonstrates hands-on work");
        }
        if (experience) {
            strengths.add("Includes internship or work experience");
        }
        if (metrics >= 2) {
            strengths.add("Quantifies results with numbers (" + metrics + " measurable outcomes found)");
        }
        if (achievements) {
            strengths.add("Lists certifications or achievements that add credibility");
        }
        if (!missing.isEmpty()) {
            weaknesses.add("Does not show these required skills: " + String.join(", ", missing));
            suggestions.add("If you know " + String.join(", ", missing.subList(0, Math.min(3, missing.size())))
                    + ", add them to the skills section and show where you used them in a project");
        }
        if (metrics < 2) {
            weaknesses.add("Few measurable results; most points describe tasks rather than impact");
            suggestions.add("Rewrite project bullets with numbers, e.g. \"reduced page load time by 40%\" or \"used by 300+ students\"");
        }
        if (!experience) {
            weaknesses.add("No internship or work experience section");
            suggestions.add("Add internships, freelance work, open-source contributions or significant college roles as experience");
        }
        if (!projects) {
            weaknesses.add("No clear projects section");
            suggestions.add("Add 2-3 projects with the problem, your role, the tech stack and the result");
        }
        if (!goodLength) {
            weaknesses.add(words < 250 ? "The resume is quite short (" + words + " words)" : "The resume is long (" + words + " words)");
            suggestions.add(words < 250 ? "Expand project and experience bullets to about 400-700 words in total"
                    : "Trim to one page for a fresher role: keep only the strongest, most relevant points");
        }
        if (!links) {
            suggestions.add("Add your GitHub and LinkedIn profile links in the header");
        }
        suggestions.add("Mirror the job description's wording for skills you genuinely have, so ATS keyword filters find them");
        if (strengths.isEmpty()) {
            strengths.add("The resume is readable as text, so applicant tracking systems can parse it");
        }
        if (weaknesses.isEmpty()) {
            weaknesses.add("No major gaps found for this role");
        }
        return new ResumeFeedback(fit, strengths.stream().limit(4).toList(), weaknesses.stream().limit(4).toList(),
                suggestions.stream().limit(6).toList());
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> clean(List<String> lines, int max) {
        return lines.stream().map(ResumeAnalyzer::stripBullet).filter(l -> !l.isBlank()).limit(max).toList();
    }

    static String stripBullet(String line) {
        return line.replaceFirst("^[\\s•●▪◦\\-*–·]+(?=\\S)", "").strip();
    }

    private static String link(Pattern p, String text) {
        Matcher m = p.matcher(text == null ? "" : text);
        if (!m.find()) {
            return null;
        }
        String url = m.group().replaceAll("/$", "");
        return url.startsWith("http") ? url : "https://" + url;
    }
}

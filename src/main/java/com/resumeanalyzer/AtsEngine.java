package com.resumeanalyzer;

import java.util.*;
import java.util.regex.*;

/** Rule-based ATS analyzer. Pure Java so it can be unit-tested without Spring. */
public class AtsEngine {

    public record Report(int score, String parsingRisk, Map<String, Integer> breakdown,
                         List<String> formattingIssues, List<String> weakBullets,
                         List<String> missingElements, List<String> matchedKeywords,
                         List<String> missingKeywords, int keywordMatchPercent,
                         List<String> nextSteps) {}

    private static final Map<String, List<String>> SECTIONS = new LinkedHashMap<>();
    static {
        SECTIONS.put("Summary", List.of("summary", "objective", "profile"));
        SECTIONS.put("Skills", List.of("skills", "technical skills", "core competencies"));
        SECTIONS.put("Experience", List.of("experience", "work experience", "internship", "internships"));
        SECTIONS.put("Education", List.of("education", "academic"));
        SECTIONS.put("Projects", List.of("projects", "project"));
    }

    private static final Set<String> STRONG_VERBS = Set.of(
        "built", "developed", "designed", "implemented", "led", "created", "optimized", "reduced",
        "increased", "improved", "automated", "deployed", "engineered", "launched", "analyzed",
        "architected", "integrated", "migrated", "delivered", "trained", "achieved", "streamlined",
        "managed", "mentored", "published", "established", "refactored", "scaled");

    private static final List<String> WEAK_STARTS = List.of(
        "responsible for", "worked on", "worked with", "helped", "assisted", "involved in",
        "duties included", "tasked with", "participated in", "was part of");

    /** Default expectations when no JD is given (software / AI-ML fresher profile). */
    private static final List<String> DEFAULT_KEYWORDS = List.of(
        "java", "python", "sql", "git", "github", "rest api", "data structures", "algorithms",
        "machine learning", "docker", "linux", "spring boot", "testing", "agile");

    /** Known tech terms used to pull keywords out of a job description. */
    private static final List<String> TECH_DICTIONARY = List.of(
        "java", "python", "c++", "javascript", "typescript", "sql", "nosql", "mongodb", "postgresql",
        "mysql", "spring boot", "spring", "hibernate", "react", "node.js", "rest api", "microservices",
        "docker", "kubernetes", "aws", "azure", "gcp", "git", "github", "ci/cd", "jenkins", "linux",
        "machine learning", "deep learning", "nlp", "tensorflow", "pytorch", "scikit-learn", "pandas",
        "numpy", "data structures", "algorithms", "oop", "agile", "scrum", "junit", "testing",
        "kafka", "redis", "tableau", "power bi", "excel", "seo", "analytics", "communication",
        "leadership", "problem solving", "teamwork");

    public Report analyze(String resumeText, String jobDescription) {
        String text = resumeText == null ? "" : resumeText;
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> lines = Arrays.stream(text.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        Map<String, Integer> pts = new LinkedHashMap<>();
        List<String> formatting = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> next = new ArrayList<>();

        // 1. Sections (20)
        int secPts = 0;
        for (var e : SECTIONS.entrySet()) {
            boolean found = lines.stream().anyMatch(l -> isHeading(l, e.getValue()));
            if (found) secPts += 4; else missing.add("Missing standard section heading: " + e.getKey());
        }
        pts.put("Sections", secPts);

        // 2. Contact & links (10)
        int contact = 0;
        if (Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+").matcher(text).find()) contact += 3; else missing.add("Email address not detected");
        if (Pattern.compile("(\\+?\\d[\\d\\s-]{8,13}\\d)").matcher(text).find()) contact += 2; else missing.add("Phone number not detected");
        if (lower.contains("linkedin.com")) contact += 2; else missing.add("LinkedIn URL missing");
        if (lower.contains("github.com")) contact += 3; else missing.add("GitHub URL missing");
        pts.put("Contact & Links", contact);

        // 3. Bullets: verbs, weak phrases, metrics
        List<String> bullets = extractBullets(lines);
        int strong = 0, metrics = 0;
        List<String> weak = new ArrayList<>();
        for (String b : bullets) {
            String bl = b.toLowerCase(Locale.ROOT);
            String firstWord = bl.split("\\W+", 2)[0];
            if (STRONG_VERBS.contains(firstWord)) strong++;
            if (Pattern.compile("\\d+\\s*(%|x|\\+|k\\b|m\\b|users|ms|sec|hours|days|rows|records|models|students)?").matcher(b).find()
                    && Pattern.compile("\\d").matcher(b).find()) metrics++;
            for (String w : WEAK_STARTS) if (bl.startsWith(w) || bl.contains(" " + w + " ")) { weak.add(b); break; }
        }
        int n = Math.max(bullets.size(), 1);
        pts.put("Action Verbs", (int) Math.round(15.0 * strong / n));
        pts.put("Quantified Impact", (int) Math.round(20.0 * Math.min(1.0, (double) metrics / (0.5 * n))));
        pts.put("Weak Phrasing (fewer is better)", Math.max(0, 10 - 2 * weak.size()));
        if (bullets.isEmpty()) missing.add("No bullet points detected - use '-' or '•' bullets for achievements");
        if (metrics == 0) missing.add("No quantifiable metrics (%, scale, time saved, users) found");

        // 4. Formatting risk (5)
        int fmt = 5;
        long pipes = lines.stream().filter(l -> l.chars().filter(c -> c == '|').count() >= 3).count();
        long tabs = lines.stream().filter(l -> l.contains("\t") || l.contains("    ")).count();
        long icons = text.codePoints().filter(c -> c > 0x2000 && c != 0x2022 && c != 0x2013 && c != 0x2014 && c != 0x2019).count();
        if (pipes > 2) { fmt -= 2; formatting.add("Possible table/multi-column layout (" + pipes + " pipe-heavy lines) - parsers may scramble order"); }
        if (tabs > 5) { fmt -= 1; formatting.add("Heavy tab/space alignment detected - may indicate columns"); }
        if (icons > 3) { fmt -= 1; formatting.add("Icons/special symbols detected (" + icons + ") - replace with plain text labels"); }
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        if (words < 150) { fmt -= 1; formatting.add("Very short resume (" + words + " words) - likely missing content or text not extracted (scanned image?)"); }
        if (words > 900) { fmt -= 1; formatting.add("Long resume (" + words + " words) - aim for 1 page for freshers, 2 max"); }
        pts.put("Formatting", Math.max(0, fmt));

        // 5. Keywords (20)
        boolean hasJd = jobDescription != null && !jobDescription.isBlank();
        List<String> targets = hasJd ? keywordsFromJd(jobDescription) : DEFAULT_KEYWORDS;
        List<String> matched = new ArrayList<>(), missingKw = new ArrayList<>();
        for (String k : targets) (containsTerm(lower, k) ? matched : missingKw).add(k);
        int pct = targets.isEmpty() ? 0 : (int) Math.round(100.0 * matched.size() / targets.size());
        pts.put("Keyword Match" + (hasJd ? " (vs JD)" : " (industry default)"), (int) Math.round(20.0 * pct / 100));

        int score = Math.min(100, pts.values().stream().mapToInt(Integer::intValue).sum());
        String risk = (formatting.size() >= 2 || score < 50) ? "High" : (formatting.size() == 1 || score < 75) ? "Medium" : "Low";

        if (!weak.isEmpty()) next.add("Rewrite " + weak.size() + " weak bullet(s) using the XYZ formula: Accomplished [X], measured by [Y], by doing [Z].");
        if (metrics < Math.max(1, n / 2)) next.add("Add numbers to at least half your bullets (accuracy %, dataset size, users, latency, time saved).");
        if (!missingKw.isEmpty()) next.add("Work these into Skills/Projects where truthful: " + String.join(", ", missingKw.stream().limit(8).toList()) + ".");
        if (!missing.isEmpty()) next.add("Fix structural gaps: " + missing.get(0) + (missing.size() > 1 ? " (+" + (missing.size() - 1) + " more)." : "."));
        if (!hasJd) next.add("Paste the target Job Description to get a real keyword-match percentage.");

        return new Report(score, risk, pts, formatting, weak, missing, matched, missingKw, pct, next);
    }

    private static boolean isHeading(String line, List<String> names) {
        String l = line.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", "").trim();
        return l.length() <= 30 && names.contains(l);
    }

    private static List<String> extractBullets(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            if (l.matches("^[-•*–·▪●]\\s*.+")) out.add(l.replaceFirst("^[-•*–·▪●]\\s*", ""));
        }
        if (out.isEmpty()) for (String l : lines) if (l.split("\\s+").length >= 8) out.add(l);
        return out;
    }

    private static boolean containsTerm(String lowerText, String term) {
        return Pattern.compile("(?<![a-z0-9+#])" + Pattern.quote(term) + "(?![a-z0-9+#])").matcher(lowerText).find();
    }

    private static List<String> keywordsFromJd(String jd) {
        String lj = jd.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String t : TECH_DICTIONARY) if (containsTerm(lj, t)) found.add(t);
        return found.isEmpty() ? DEFAULT_KEYWORDS : found;
    }
}

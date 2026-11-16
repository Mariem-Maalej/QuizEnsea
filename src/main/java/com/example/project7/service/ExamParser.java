package com.example.project7.service;

import com.example.project7.service.PdfTextExtractor.PdfLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExamParser {

    // "Question 1 ...", "Question 2 ♣ ...", "Question 14♣ ..."
    private static final Pattern QUESTION_PATTERN =
            Pattern.compile("^Question\\s*(\\d+)\\s*(♣)?\\s*(.*)$");

    // "(1 point)", "(2 points).", "(2 point)", "(0.5 point)", "(1,5 points)"
    private static final Pattern POINTS_PATTERN =
            Pattern.compile("\\(\\s*(\\d+(?:[.,]\\d+)?)\\s*points?\\s*\\)\\.?",
                    Pattern.CASE_INSENSITIVE);

    // Grading box of an open question: "F G VG", "F P G PG VG" (2+ short uppercase tokens)
    private static final Pattern SCORE_BOX_PATTERN =
            Pattern.compile("^[A-Z]{1,3}(?:\\s+[A-Z]{1,3})+$");

    // Dotted answer lines of an open question
    private static final Pattern DOTTED_LINE_PATTERN =
            Pattern.compile("^(?:\\.\\s*){5,}$");

    // AMC page code such as "+1/2/59+"
    private static final Pattern PAGE_CODE_PATTERN =
            Pattern.compile("^\\+\\d+/\\d+/\\d+\\+$");

    /** A line is a section (exercise) title when its font is this much bigger than the body text. */
    private static final float TITLE_FONT_RATIO = 1.3f;

    /** Lines starting within this distance (points) of "Question N" belong to the statement. */
    private static final float STATEMENT_X_TOLERANCE = 3f;

    private float bodyFontSize;
    private float rightMargin;

    public List<ParsedQuestion> parse(List<PdfLine> lines) {
        computeLayout(lines);

        List<ParsedQuestion> questions = new ArrayList<>();
        String currentSection = "";
        QuestionBlock block = null;

        for (PdfLine line : lines) {
            String text = line.text().trim();
            if (text.isEmpty() || PAGE_CODE_PATTERN.matcher(text).matches()) {
                continue;
            }

            if (line.fontSize() >= bodyFontSize * TITLE_FONT_RATIO) {
                if (block != null) {
                    questions.add(block.build());
                    block = null;
                }
                currentSection = normalizeSpaces(text);
                continue;
            }

            Matcher m = QUESTION_PATTERN.matcher(text);
            if (m.matches()) {
                if (block != null) {
                    questions.add(block.build());
                }
                block = new QuestionBlock(
                        Integer.parseInt(m.group(1)),
                        m.group(2) != null,
                        m.group(3),
                        line,
                        currentSection);
            } else if (block != null) {
                block.lines.add(line);
            }
            // Lines before the first question (exam header, student number grid…) are ignored.
        }

        if (block != null) {
            questions.add(block.build());
        }
        return questions;
    }

    /**
     * Exam title: the lines before the first question written in a font bigger than the
     * body text but smaller than a section title (e.g. "Systèmes à microcontrôleur … Second examen initial.").
     * Returns "" when there is none.
     */
    public String extractTitle(List<PdfLine> lines) {
        computeLayout(lines);
        StringBuilder title = new StringBuilder();
        for (PdfLine line : header(lines)) {
            boolean biggerThanBody = line.fontSize() > bodyFontSize + 0.5f;
            boolean sectionTitle = line.fontSize() >= bodyFontSize * TITLE_FONT_RATIO;
            if (biggerThanBody && !sectionTitle) {
                title.append(' ').append(normalizeSpaces(line.text()));
            }
        }
        String result = title.toString().trim();
        return result.endsWith(".") ? result.substring(0, result.length() - 1) : result;
    }

    /**
     * Exam instructions: the body-text paragraph before the first question
     * (e.g. "Questionnaire à choix multiples. … Le barème est indicatif …").
     * The student-number grid and the name box on the right are skipped.
     */
    public String extractInstructions(List<PdfLine> lines) {
        computeLayout(lines);
        float pageMiddle = rightMargin / 2;
        String text = "";
        for (PdfLine line : header(lines)) {
            String t = line.text().trim();
            boolean bodyText = Math.abs(line.fontSize() - bodyFontSize) < 0.5f;
            boolean leftColumn = line.x() < pageMiddle;
            boolean digitGrid = t.matches("[\\d\\s]+");
            if (bodyText && leftColumn && !digitGrid
                    && !DOTTED_LINE_PATTERN.matcher(t).matches()
                    && !PAGE_CODE_PATTERN.matcher(t).matches()) {
                text = joinWrapped(text, normalizeSpaces(t));
            }
        }
        return text;
    }

    /** Lines before the first "Question N" or section title. */
    private List<PdfLine> header(List<PdfLine> lines) {
        List<PdfLine> header = new ArrayList<>();
        for (PdfLine line : lines) {
            String text = line.text().trim();
            if (QUESTION_PATTERN.matcher(text).matches()
                    || line.fontSize() >= bodyFontSize * TITLE_FONT_RATIO) {
                break;
            }
            header.add(line);
        }
        return header;
    }

    /** Finds the body font size (most frequent) and the right margin of the text. */
    private void computeLayout(List<PdfLine> lines) {
        Map<Float, Integer> sizeCounts = new HashMap<>();
        for (PdfLine line : lines) {
            float size = Math.round(line.fontSize() * 2) / 2f;
            sizeCounts.merge(size, 1, Integer::sum);
        }
        bodyFontSize = sizeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(10f);

        // Justified lines all end at the right margin, so it is the most frequent line end.
        // (The maximum would be wrong: long code lines can overflow the margin.)
        Map<Integer, Integer> endCounts = new HashMap<>();
        for (PdfLine line : lines) {
            String text = line.text().trim();
            boolean bodyText = Math.abs(line.fontSize() - bodyFontSize) < 0.5f;
            if (bodyText
                    && !PAGE_CODE_PATTERN.matcher(text).matches()
                    && !DOTTED_LINE_PATTERN.matcher(text).matches()) {
                endCounts.merge(Math.round(line.endX()), 1, Integer::sum);
            }
        }
        rightMargin = endCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Integer.MAX_VALUE);
    }

    /** The lines of one question, before they are turned into a ParsedQuestion. */
    private class QuestionBlock {
        final int number;
        final boolean multipleAnswers;
        final String firstLineText;
        final PdfLine firstLine;
        final String section;
        final List<PdfLine> lines = new ArrayList<>();

        QuestionBlock(int number, boolean multipleAnswers, String firstLineText,
                      PdfLine firstLine, String section) {
            this.number = number;
            this.multipleAnswers = multipleAnswers;
            this.firstLineText = firstLineText;
            this.firstLine = firstLine;
            this.section = section;
        }

        ParsedQuestion build() {
            boolean openQuestion = lines.stream()
                    .anyMatch(l -> DOTTED_LINE_PATTERN.matcher(l.text().trim()).matches());

            // 1. Statement: the first line plus the following lines aligned with "Question N".
            List<String> statementLines = new ArrayList<>();
            statementLines.add(firstLineText);
            int i = 0;
            while (i < lines.size()
                    && Math.abs(lines.get(i).x() - firstLine.x()) <= STATEMENT_X_TOLERANCE
                    && !DOTTED_LINE_PATTERN.matcher(lines.get(i).text().trim()).matches()) {
                statementLines.add(lines.get(i).text().trim());
                i++;
            }

            List<String> scoreLevels = new ArrayList<>();
            String statement = "";
            for (String part : statementLines) {
                // The grading box can sit at the end of a statement line, after a wide gap.
                if (openQuestion) {
                    int tab = part.lastIndexOf('\t');
                    if (tab >= 0) {
                        String tail = normalizeSpaces(part.substring(tab + 1));
                        if (SCORE_BOX_PATTERN.matcher(tail).matches()) {
                            scoreLevels.addAll(List.of(tail.split(" ")));
                            part = part.substring(0, tab);
                        }
                    }
                }
                statement = joinWrapped(statement, normalizeSpaces(part));
            }

            // 2. Points: "(2 points)" anywhere in the statement.
            Double points = null;
            Matcher pm = POINTS_PATTERN.matcher(statement);
            if (pm.find()) {
                points = Double.parseDouble(pm.group(1).replace(',', '.'));
                statement = statement.substring(0, pm.start()) + statement.substring(pm.end());
            }
            statement = cleanStatement(statement);

            // 3. Remaining lines: answers, or grading box + dotted lines for open questions.
            List<String> answers = new ArrayList<>();
            int answerLineCount = 0;
            PdfLine previous = null;
            StringBuilder raw = new StringBuilder();

            for (; i < lines.size(); i++) {
                PdfLine line = lines.get(i);
                String text = line.text().trim();
                raw.append(text.replace('\t', ' ')).append('\n');

                if (DOTTED_LINE_PATTERN.matcher(text).matches()) {
                    answerLineCount++;
                    previous = null;
                    continue;
                }
                String flat = normalizeSpaces(text);
                if (openQuestion && SCORE_BOX_PATTERN.matcher(flat).matches()) {
                    scoreLevels.addAll(List.of(flat.split(" ")));
                    previous = null;
                    continue;
                }

                if (text.indexOf('\t') >= 0) {
                    // Answers laid out side by side.
                    for (String cell : text.split("\t")) {
                        String answer = normalizeSpaces(cell);
                        if (!answer.isEmpty()) {
                            answers.add(answer);
                        }
                    }
                    previous = null;
                } else if (previous != null && continuesPreviousLine(previous)) {
                    int last = answers.size() - 1;
                    answers.set(last, joinWrapped(answers.get(last), flat));
                    previous = line;
                } else {
                    answers.add(flat);
                    previous = line;
                }
            }

            String type;
            if (multipleAnswers) {
                type = "QCM";
            } else if (openQuestion || !scoreLevels.isEmpty()) {
                type = "QuestionLibre";
            } else {
                type = "QCU";
            }

            ParsedQuestion q = new ParsedQuestion(number, statement);
            q.setType(type);
            q.setPoints(points);
            q.setAnswers(answers);
            q.setScoreLevels(scoreLevels);
            q.setAnswerLineCount(answerLineCount);
            q.setSection(section);
            q.setRawContent(raw.toString().trim());
            return q;
        }
    }

    /**
     * True when the previous answer line was cut by the right margin in the middle of a
     * sentence, so the next line is its continuation rather than a new answer.
     */
    private boolean continuesPreviousLine(PdfLine previous) {
        String prev = previous.text().trim();
        boolean reachesMargin = previous.endX() >= rightMargin - 2 * bodyFontSize;
        return reachesMargin && !endsSentence(prev);
    }

    private static boolean endsSentence(String s) {
        if (s.isEmpty()) {
            return true;
        }
        char c = s.charAt(s.length() - 1);
        return c == '.' || c == '?' || c == '!' || c == ':' || c == ';' || c == '…';
    }

    /** Joins a wrapped line to the text before it, undoing LaTeX hyphenation ("pro-" + "cesseur"). */
    private static String joinWrapped(String before, String after) {
        if (before.isEmpty()) {
            return after;
        }
        if (after.isEmpty()) {
            return before;
        }
        if (before.endsWith("-")) {
            char beforeHyphen = before.length() > 1 ? before.charAt(before.length() - 2) : ' ';
            if (Character.isLetter(beforeHyphen) && Character.isLowerCase(after.charAt(0))) {
                return before.substring(0, before.length() - 1) + after;   // word split by hyphenation
            }
            return before + after;                                          // e.g. "1-" + "value_chooser;"
        }
        return before + " " + after;
    }

    private static String normalizeSpaces(String s) {
        return s.replace('\t', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String cleanStatement(String s) {
        s = normalizeSpaces(s);
        // Drop a final period (often left over after removing "(2 points).")
        if (s.endsWith(".") && !s.endsWith("..")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    public static class ParsedQuestion {

        private final int number;
        private String type;
        private final String statement;
        private Double points;
        private List<String> answers = new ArrayList<>();
        private List<String> scoreLevels = new ArrayList<>();
        private int answerLineCount;
        private String section = "";
        private String rawContent = "";

        public ParsedQuestion(int number, String statement) {
            this.number = number;
            this.statement = statement;
        }

        public int getNumber() {
            return number;
        }

        /** "QCM" (several correct answers), "QCU" (one correct answer) or "QuestionLibre". */
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getStatement() {
            return statement;
        }

        /** Points written in the statement, e.g. 2.0 for "(2 points)"; null when not given. */
        public Double getPoints() {
            return points;
        }

        public void setPoints(Double points) {
            this.points = points;
        }

        /** Kept for compatibility: "2 points", "1 point" or "" when not given. */
        public String getGrading() {
            if (points == null) {
                return "";
            }
            String value = points % 1 == 0 ? String.valueOf(points.intValue()) : String.valueOf(points);
            return value + (points > 1 ? " points" : " point");
        }

        /** The answer choices of a QCM / QCU question, in order. */
        public List<String> getAnswers() {
            return answers;
        }

        public void setAnswers(List<String> answers) {
            this.answers = answers;
        }

        /** Grading levels of an open question, e.g. [F, P, G, PG, VG]. */
        public List<String> getScoreLevels() {
            return scoreLevels;
        }

        public void setScoreLevels(List<String> scoreLevels) {
            this.scoreLevels = scoreLevels;
        }

        /** Number of dotted lines left for the answer of an open question. */
        public int getAnswerLineCount() {
            return answerLineCount;
        }

        public void setAnswerLineCount(int answerLineCount) {
            this.answerLineCount = answerLineCount;
        }

        /** Title of the section (exercise) the question belongs to. */
        public String getSection() {
            return section;
        }

        public void setSection(String section) {
            this.section = section;
        }

        public String getRawContent() {
            return rawContent;
        }

        public void setRawContent(String rawContent) {
            this.rawContent = rawContent;
        }
    }
}
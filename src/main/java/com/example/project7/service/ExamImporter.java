package com.example.project7.service;

import com.example.project7.model.TypeDevoir;
import com.example.project7.model.TypeProjet;
import com.example.project7.service.ExamParser.ParsedQuestion;
import com.example.project7.service.PdfTextExtractor.PdfLine;
import sql_connection.SqlConnection;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports an exam PDF into the database as a new Projet + Controle, with one Exercice per
 * section title and one Section (QCM / QCU / QuestionLibre) per question.
 *
 * The PDF is not a corrected copy, so every answer is saved with isCorrect = false and
 * score = 0: the teacher marks the correct answers afterwards in the QCM / QCU editor.
 */
public class ExamImporter {

    // Same defaults as the QuestionLibre model, used when the PDF does not give a value.
    private static final float DEFAULT_SCORE_TOTAL = 5f;
    private static final int DEFAULT_NOMBRE_SCORE = 5;
    private static final int DEFAULT_NOMBRE_LIGNE = 10;
    private static final float DEFAULT_TAILLE_LIGNE = 0.3f;

    public record ImportResult(int projetId, int controleId, String nomProjet,
                               int exercices, int questions) {
    }

    /** What was read from the PDF, before anything is saved. */
    public record ParsedExam(String title, String instructions, List<ParsedQuestion> questions) {

        /** Questions grouped by section title, in order: one group = one Exercice. */
        public List<List<ParsedQuestion>> exercices() {
            List<List<ParsedQuestion>> groups = new ArrayList<>();
            String current = null;
            for (ParsedQuestion q : questions) {
                if (groups.isEmpty() || !q.getSection().equals(current)) {
                    groups.add(new ArrayList<>());
                    current = q.getSection();
                }
                groups.get(groups.size() - 1).add(q);
            }
            return groups;
        }
    }

    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final ExamParser parser = new ExamParser();

    /** Reads the PDF. Nothing is written to the database. */
    public ParsedExam parse(File pdf) {
        List<PdfLine> lines = extractor.extractLines(pdf);
        return new ParsedExam(
                parser.extractTitle(lines),
                parser.extractInstructions(lines),
                parser.parse(lines));
    }

    /**
     * Saves a parsed exam as a new project.
     *
     * @param nomProjet  project name; also used as a folder name inside saveFolder
     * @param saveFolder folder where the app will generate the project files
     *                   (stored as Projet.localisationProjet)
     */
    public ImportResult save(ParsedExam exam, String nomProjet, File saveFolder) throws SQLException {
        if (exam.questions().isEmpty()) {
            throw new IllegalArgumentException("No question found in the PDF");
        }
        if (!saveFolder.isDirectory() && !saveFolder.mkdirs()) {
            throw new IllegalArgumentException("Cannot create the folder " + saveFolder.getAbsolutePath());
        }
        String nomDevoir = exam.title().isEmpty() ? nomProjet : exam.title();

        try (Connection conn = SqlConnection.getConnection()) {
            conn.setAutoCommit(false);   // all or nothing
            try {
                int projetId = insertProjet(conn, nomProjet, saveFolder.getAbsolutePath());
                int controleId = insertControle(conn, nomDevoir, exam.instructions(), projetId);
                int exercices = insertExercicesAndQuestions(conn, controleId, exam.exercices());
                conn.commit();
                return new ImportResult(projetId, controleId, nomProjet, exercices, exam.questions().size());
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Parse + save in one call, with the project named after the PDF file. */
    public ImportResult importPdf(File pdf, File saveFolder) throws SQLException {
        return save(parse(pdf), projectNameFor(pdf), saveFolder);
    }

    /** True when a project created from this PDF already exists (same name). */
    public boolean isAlreadyImported(File pdf) throws SQLException {
        return projectExists(projectNameFor(pdf));
    }

    public boolean projectExists(String nomProjet) throws SQLException {
        String query = "SELECT COUNT(*) FROM Projet WHERE nomProjet = ?";
        try (Connection conn = SqlConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nomProjet);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Default project name for a PDF: its file name, made safe to use as a folder name. */
    public static String projectNameFor(File pdf) {
        String name = pdf.getName().replaceFirst("(?i)\\.pdf$", "");
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /** True when the name can be used as a folder name on Windows, macOS and Linux. */
    public static boolean isValidProjectName(String name) {
        return name != null && !name.isBlank() && !name.matches(".*[\\\\/:*?\"<>|].*");
    }

    // ==================== INSERTS ====================

    private int insertProjet(Connection conn, String nomProjet, String localisation) throws SQLException {
        String sql = "INSERT INTO Projet (nomProjet, localisationProjet, typeProjet) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, nomProjet);
            stmt.setString(2, localisation);
            stmt.setString(3, TypeProjet.BasicModel.getNomProjet());
            stmt.executeUpdate();
            return generatedId(stmt);
        }
    }

    private int insertControle(Connection conn, String nomDevoir, String examHeader, int projetId)
            throws SQLException {
        String sql = "INSERT INTO Controle (nomDevoir, typeDevoir, examHeader, reponseHeader, projetID, creationDate) "
                + "VALUES (?, ?, ?, ?, ?, CURRENT_DATE)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, nomDevoir);
            stmt.setString(2, TypeDevoir.Controle_Continue.getNomDevoir());
            stmt.setString(3, examHeader);
            stmt.setString(4, "");
            stmt.setInt(5, projetId);
            stmt.executeUpdate();
            return generatedId(stmt);
        }
    }

    /**
     * Consecutive questions with the same section title form one Exercice.
     * Section.ordreSection runs from 1 to N over the whole exam, because the editor
     * lists the questions of a Controle ordered by ordreSection.
     */
    private int insertExercicesAndQuestions(Connection conn, int controleId, List<List<ParsedQuestion>> groups)
            throws SQLException {
        int numeroExercice = 1;
        int ordreSection = 1;
        long importStamp = System.currentTimeMillis();

        for (List<ParsedQuestion> group : groups) {
            String section = group.get(0).getSection();
            String titre = section.isEmpty() ? "Exercice " + numeroExercice : section;
            int exerciceId = insertExercice(conn, numeroExercice, titre, controleId);

            for (ParsedQuestion q : group) {
                // Same ID format as ExtractViewController, unique per import.
                String sectionId = "s_" + controleId + "_" + exerciceId + "_" + ordreSection + "_" + importStamp;
                insertSection(conn, sectionId, ordreSection, controleId, exerciceId);

                if (q.getType().equals("QuestionLibre")) {
                    insertQuestionLibre(conn, q, sectionId);
                } else {
                    insertQcm(conn, q, sectionId);
                }
                ordreSection++;
            }
            numeroExercice++;
        }
        return groups.size();
    }

    private int insertExercice(Connection conn, int numero, String titre, int controleId) throws SQLException {
        String sql = "INSERT INTO Exercice (numero, titre, consigne, bareme, controleID) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, numero);
            stmt.setString(2, titre);
            stmt.setString(3, "");
            stmt.setDouble(4, 0.0);
            stmt.setInt(5, controleId);
            stmt.executeUpdate();
            return generatedId(stmt);
        }
    }

    private void insertSection(Connection conn, String sectionId, int ordre, int controleId, int exerciceId)
            throws SQLException {
        String sql = "INSERT INTO Section (idSection, ordreSection, controleID, exerciceID) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sectionId);
            stmt.setInt(2, ordre);
            stmt.setInt(3, controleId);
            stmt.setInt(4, exerciceId);
            stmt.executeUpdate();
        }
    }

    private void insertQcm(Connection conn, ParsedQuestion q, String sectionId) throws SQLException {
        String sql = "INSERT INTO QCM (question, isQCU, sectionID) VALUES (?, ?, ?)";
        int qcmId;
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, q.getStatement());
            stmt.setBoolean(2, q.getType().equals("QCU"));
            stmt.setString(3, sectionId);
            stmt.executeUpdate();
            qcmId = generatedId(stmt);
        }

        String sqlRep = "INSERT INTO QCM_Reponses (qcmID, reponse, score, isCorrect) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sqlRep)) {
            for (String answer : q.getAnswers()) {
                stmt.setInt(1, qcmId);
                stmt.setString(2, answer);
                stmt.setInt(3, 0);           // set by the teacher in the editor
                stmt.setBoolean(4, false);   // not corrected yet
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void insertQuestionLibre(Connection conn, ParsedQuestion q, String sectionId) throws SQLException {
        String sql = "INSERT INTO QuestionLibre (question, scoreTotal, nombreScore, nombreLigne, tailleLigne, rappel, sectionID) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, q.getStatement());
            stmt.setFloat(2, q.getPoints() != null ? q.getPoints().floatValue() : DEFAULT_SCORE_TOTAL);
            stmt.setInt(3, !q.getScoreLevels().isEmpty() ? q.getScoreLevels().size() : DEFAULT_NOMBRE_SCORE);
            stmt.setInt(4, q.getAnswerLineCount() > 0 ? q.getAnswerLineCount() : DEFAULT_NOMBRE_LIGNE);
            stmt.setFloat(5, DEFAULT_TAILLE_LIGNE);
            stmt.setString(6, "");
            stmt.setString(7, sectionId);
            stmt.executeUpdate();
        }
    }

    private static int generatedId(PreparedStatement stmt) throws SQLException {
        try (ResultSet keys = stmt.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("No generated id returned");
            }
            return keys.getInt(1);
        }
    }
}

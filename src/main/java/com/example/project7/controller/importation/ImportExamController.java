package com.example.project7.controller.importation;

import com.example.project7.FxmlLoader;
import com.example.project7.controller.edition.EditerProjet;
import com.example.project7.model.Projet;
import com.example.project7.service.ExamImporter;
import com.example.project7.service.ExamImporter.ImportResult;
import com.example.project7.service.ExamImporter.ParsedExam;
import com.example.project7.service.ExamParser.ParsedQuestion;
import com.jfoenix.controls.JFXButton;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import sql_connection.SqlConnection;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * "Import PDF" screen: choose an exam PDF and a save folder, preview what was read,
 * then create the project (Projet + Controle + Exercices + questions) in the database.
 */
public class ImportExamController implements Initializable {

    @FXML private TextField txtPdf;
    @FXML private TextField txtProjectName;
    @FXML private TextField txtFolder;
    @FXML private Label lblNameError;
    @FXML private Label lblExamTitle;
    @FXML private Label lblSummary;
    @FXML private Label lblStatus;
    @FXML private TreeView<String> treePreview;
    @FXML private ProgressIndicator progress;
    @FXML private JFXButton btnChoosePdf;
    @FXML private JFXButton btnChooseFolder;
    @FXML private JFXButton btnImport;

    private final ExamImporter importer = new ExamImporter();

    private AnchorPane parentPane;
    private File pdfFile;
    private File saveFolder;
    private ParsedExam parsedExam;
    private boolean busy;

    public void setParentPane(AnchorPane pane) {
        this.parentPane = pane;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        File home = new File(System.getProperty("user.home"));
        setSaveFolder(home);
        treePreview.setRoot(new TreeItem<>());
        updateImportButton();
    }

    // ==================== 1. PDF ====================

    @FXML
    private void handleChoosePdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the exam PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        if (pdfFile != null && pdfFile.getParentFile() != null) {
            chooser.setInitialDirectory(pdfFile.getParentFile());
        }

        File chosen = chooser.showOpenDialog(window());
        if (chosen != null) {
            loadPdf(chosen);
        }
    }

    /** Reads the PDF in the background and shows the preview. */
    void loadPdf(File file) {
        pdfFile = file;
        parsedExam = null;
        txtPdf.setText(file.getName());
        txtProjectName.setText(ExamImporter.projectNameFor(file));
        validateProjectName();
        lblExamTitle.setText("Reading " + file.getName() + "…");
        lblSummary.setText("");
        treePreview.setRoot(new TreeItem<>());

        Task<ParsedExam> task = new Task<>() {
            @Override
            protected ParsedExam call() {
                return importer.parse(file);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false, "");
            showPreview(task.getValue());
        });
        task.setOnFailed(e -> {
            setBusy(false, "");
            lblExamTitle.setText("This PDF could not be read.");
            lblSummary.setText(String.valueOf(task.getException().getMessage()));
            task.getException().printStackTrace();
        });
        setBusy(true, "Reading the PDF…");
        startInBackground(task);
    }

    private void showPreview(ParsedExam exam) {
        if (exam.questions().isEmpty()) {
            parsedExam = null;
            lblExamTitle.setText("No question found in this PDF.");
            lblSummary.setText("The importer looks for lines starting with \"Question 1\", \"Question 2\"… "
                    + "Check that this is an exam generated with the app's template.");
            updateImportButton();
            return;
        }
        parsedExam = exam;

        lblExamTitle.setText(exam.title().isEmpty() ? "(No title found — the project name will be used)" : exam.title());

        long qcu = exam.questions().stream().filter(q -> q.getType().equals("QCU")).count();
        long qcm = exam.questions().stream().filter(q -> q.getType().equals("QCM")).count();
        long open = exam.questions().stream().filter(q -> q.getType().equals("QuestionLibre")).count();
        List<List<ParsedQuestion>> exercices = exam.exercices();
        lblSummary.setText(String.format("%d exercise(s) · %d question(s): %d QCU, %d QCM, %d open",
                exercices.size(), exam.questions().size(), qcu, qcm, open));

        TreeItem<String> root = new TreeItem<>();
        int numero = 1;
        for (List<ParsedQuestion> group : exercices) {
            String section = group.get(0).getSection();
            String titre = section.isEmpty() ? "" : " — " + section;
            TreeItem<String> exItem = new TreeItem<>(
                    "Exercice " + numero + titre + "  (" + group.size() + " question" + (group.size() > 1 ? "s" : "") + ")");
            exItem.setExpanded(true);
            for (ParsedQuestion q : group) {
                exItem.getChildren().add(questionItem(q));
            }
            root.getChildren().add(exItem);
            numero++;
        }
        treePreview.setRoot(root);
        updateImportButton();
    }

    private TreeItem<String> questionItem(ParsedQuestion q) {
        String type = switch (q.getType()) {
            case "QCM" -> "QCM ♣";
            case "QuestionLibre" -> "Open";
            default -> "QCU";
        };
        String points = q.getPoints() != null ? " · " + q.getGrading() : "";
        TreeItem<String> item = new TreeItem<>("Q" + q.getNumber() + "  [" + type + points + "]  " + shorten(q.getStatement(), 110));

        if (q.getType().equals("QuestionLibre")) {
            String levels = q.getScoreLevels().isEmpty() ? "" : "grading " + String.join(" / ", q.getScoreLevels()) + " · ";
            item.getChildren().add(new TreeItem<>("✎ " + levels + q.getAnswerLineCount() + " answer line(s)"));
        } else {
            String bullet = q.getType().equals("QCM") ? "☐ " : "○ ";
            for (String answer : q.getAnswers()) {
                item.getChildren().add(new TreeItem<>(bullet + answer));
            }
        }
        return item;
    }

    // ==================== 2. PROJECT NAME ====================

    @FXML
    private void handleProjectNameChanged() {
        validateProjectName();
        updateImportButton();
    }

    private boolean validateProjectName() {
        String name = txtProjectName.getText();
        boolean valid = ExamImporter.isValidProjectName(name);
        if (name == null || name.isBlank()) {
            lblNameError.setText("The project name is required.");
        } else if (!valid) {
            lblNameError.setText("The name is also a folder name: it cannot contain \\ / : * ? \" < > |");
        } else {
            lblNameError.setText("");
        }
        txtProjectName.getStyleClass().remove("text-field-danger");
        if (!valid) {
            txtProjectName.getStyleClass().add("text-field-danger");
        }
        return valid;
    }

    // ==================== 3. FOLDER ====================

    @FXML
    private void handleChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose where to save the project");
        if (saveFolder != null && saveFolder.isDirectory()) {
            chooser.setInitialDirectory(saveFolder);
        }
        File chosen = chooser.showDialog(window());
        if (chosen != null) {
            setSaveFolder(chosen);
            updateImportButton();
        }
    }

    private void setSaveFolder(File folder) {
        saveFolder = folder;
        txtFolder.setText(folder.getAbsolutePath());
    }

    // ==================== IMPORT ====================

    @FXML
    private void handleImport() {
        if (parsedExam == null || saveFolder == null || !validateProjectName()) {
            return;
        }
        String nomProjet = txtProjectName.getText().trim();
        ParsedExam exam = parsedExam;
        File folder = saveFolder;

        Task<ImportResult> task = new Task<>() {
            @Override
            protected ImportResult call() throws Exception {
                if (importer.projectExists(nomProjet)) {
                    throw new IllegalStateException("A project named \"" + nomProjet + "\" already exists. "
                            + "Choose another project name.");
                }
                return importer.save(exam, nomProjet, folder);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false, "");
            onImported(task.getValue());
        });
        task.setOnFailed(e -> {
            setBusy(false, "");
            Throwable error = task.getException();
            if (error instanceof IllegalStateException) {
                lblNameError.setText(error.getMessage());
                txtProjectName.getStyleClass().add("text-field-danger");
                txtProjectName.requestFocus();
            } else {
                error.printStackTrace();
                showError("Import failed", "Nothing was saved.\n\n" + error.getMessage());
            }
        });
        setBusy(true, "Importing…");
        startInBackground(task);
    }

    private void onImported(ImportResult result) {
        lblStatus.setText("✅ \"" + result.nomProjet() + "\" imported.");

        ButtonType open = new ButtonType("Open in editor", ButtonBar.ButtonData.OK_DONE);
        ButtonType another = new ButtonType("Import another PDF", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", open, another);
        alert.setTitle("Import successful");
        alert.setHeaderText("✅ " + result.nomProjet() + " was created");
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);   // show the whole text
        alert.getDialogPane().setPrefWidth(460);
        alert.setContentText(String.format(
                "%d exercise(s) and %d question(s) were imported.%n%n"
                        + "Open the exam to mark the correct answers of each QCM / QCU.",
                result.exercices(), result.questions()));

        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isPresent() && choice.get() == open) {
            openInEditor(result.projetId());
        } else {
            resetForm();
        }
    }

    /** Same navigation as the Open / Extract screens. */
    private void openInEditor(int projetId) {
        try (Connection conn = SqlConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM Projet WHERE idProjet = ?")) {
            stmt.setInt(1, projetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    showError("Error", "The imported project could not be found.");
                    return;
                }
                Projet projet = new Projet(
                        projetId,
                        rs.getString("nomProjet"),
                        rs.getString("localisationProjet"),
                        rs.getString("typeProjet"),
                        rs.getDate("creationDate"));

                FxmlLoader loader = new FxmlLoader();
                Parent view = loader.getPane("editer_quiz/_2_EditerProjet");
                EditerProjet controller = (EditerProjet) loader.getController();
                if (controller != null) {
                    controller.setParentPane(parentPane);
                    controller.setProjet(projet);
                }
                parentPane.getChildren().setAll(view);
                AnchorPane.setTopAnchor(view, 0.0);
                AnchorPane.setBottomAnchor(view, 0.0);
                AnchorPane.setLeftAnchor(view, 0.0);
                AnchorPane.setRightAnchor(view, 0.0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error", "Could not open the project: " + e.getMessage());
        }
    }

    private void resetForm() {
        pdfFile = null;
        parsedExam = null;
        txtPdf.clear();
        txtProjectName.clear();
        lblNameError.setText("");
        txtProjectName.getStyleClass().remove("text-field-danger");
        lblExamTitle.setText("Choose a PDF to see what will be imported.");
        lblSummary.setText("");
        treePreview.setRoot(new TreeItem<>());
        updateImportButton();
    }

    // ==================== HELPERS ====================

    private void updateImportButton() {
        boolean ready = parsedExam != null
                && saveFolder != null
                && ExamImporter.isValidProjectName(txtProjectName.getText());
        btnImport.setDisable(busy || !ready);
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        progress.setVisible(busy);
        progress.setManaged(busy);
        lblStatus.setText(message);
        btnChoosePdf.setDisable(busy);
        btnChooseFolder.setDisable(busy);
        txtProjectName.setDisable(busy);
        updateImportButton();
    }

    private static void startInBackground(Task<?> task) {
        Thread thread = new Thread(task, "exam-import");
        thread.setDaemon(true);
        thread.start();
    }

    private Window window() {
        return btnImport.getScene() != null ? btnImport.getScene().getWindow() : null;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

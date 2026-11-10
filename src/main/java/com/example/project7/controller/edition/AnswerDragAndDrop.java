package com.example.project7.controller.edition;

import com.example.project7.model.Reponse;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.Node;

import java.util.function.IntSupplier;

/**
 * Drag and drop of answers in the QCM / QCU editors:
 * <ul>
 *   <li>QCM: drag an answer from one table to the other to make it correct or incorrect;</li>
 *   <li>QCU: drag an answer from the table onto the correct-answer field.</li>
 * </ul>
 */
public final class AnswerDragAndDrop {

    private static final String DROP_HIGHLIGHT =
            "-fx-border-color: #4CAF50; -fx-border-width: 2; -fx-border-style: dashed;";

    private static Reponse draggedAnswer;
    private static TableView<Reponse> sourceTable;

    private AnswerDragAndDrop() {
    }

    public static void makeRowsDraggable(TableView<Reponse> table) {
        table.setRowFactory(tv -> {
            TableRow<Reponse> row = new TableRow<>();
            row.setTooltip(new Tooltip("Drag this answer to move it"));
            row.addEventFilter(MouseEvent.DRAG_DETECTED, event -> {
                if (row.isEmpty() || row.getItem() == null) {
                    return;
                }
                if (table.getEditingCell() != null) {
                    table.edit(-1, null);
                }
                draggedAnswer = row.getItem();
                sourceTable = table;

                Dragboard dragboard = row.startDragAndDrop(TransferMode.MOVE);

                dragboard.setDragView(row.snapshot(null, null), -12, -12);
                ClipboardContent content = new ClipboardContent();
                content.putString(row.getItem().getResponse());
                dragboard.setContent(content);
                event.consume();
            });
            row.setOnDragDone(event -> {
                draggedAnswer = null;
                sourceTable = null;
            });
            return row;
        });
    }


    public static void acceptAnswersFromOtherTable(TableView<Reponse> table, IntSupplier scoreWhenDropped,
                                                   String placeholderText) {
        table.setPlaceholder(new Label(placeholderText));

        table.setOnDragOver(event -> {
            if (draggedAnswer != null && sourceTable != null && sourceTable != table) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        highlightWhileDragging(table, () -> sourceTable != null && sourceTable != table);

        table.setOnDragDropped(event -> {
            boolean done = false;
            if (draggedAnswer != null && sourceTable != null && sourceTable != table) {
                moveAnswer(draggedAnswer, sourceTable, table, scoreWhenDropped.getAsInt());
                done = true;
            }
            event.setDropCompleted(done);
            event.consume();
        });
    }


    public static void acceptAnswerAsCorrect(TextArea correctAnswerField, TextField correctScoreField,
                                             TableView<Reponse> incorrectTable, IntSupplier incorrectScore) {
        correctAnswerField.setPromptText("Type the correct answer, or drag one here from the table below");

        correctAnswerField.setOnDragOver(event -> {
            if (draggedAnswer != null && sourceTable == incorrectTable) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        highlightWhileDragging(correctAnswerField, () -> sourceTable == incorrectTable);

        correctAnswerField.setOnDragDropped(event -> {
            boolean done = false;
            if (draggedAnswer != null && sourceTable == incorrectTable) {
                setAsCorrectAnswer(draggedAnswer, correctAnswerField, correctScoreField,
                        incorrectTable, incorrectScore.getAsInt());
                done = true;
            }
            event.setDropCompleted(done);
            event.consume();
        });
    }


    static void moveAnswer(Reponse answer, TableView<Reponse> from, TableView<Reponse> to, int newScore) {
        int index = from.getItems().indexOf(answer);
        if (index < 0) {
            return;
        }
        from.getItems().remove(index);
        answer.setScore(newScore);
        to.getItems().add(answer);
    }

    static void setAsCorrectAnswer(Reponse answer, TextArea correctAnswerField, TextField correctScoreField,
                                   TableView<Reponse> incorrectTable, int incorrectScore) {
        int index = incorrectTable.getItems().indexOf(answer);
        if (index < 0) {
            return;
        }
        String previousCorrect = correctAnswerField.getText() == null ? "" : correctAnswerField.getText().trim();

        incorrectTable.getItems().remove(index);
        if (!previousCorrect.isEmpty()) {
            incorrectTable.getItems().add(index, new Reponse(previousCorrect, incorrectScore));
        }
        correctAnswerField.setText(answer.getResponse());
        correctAnswerField.getStyleClass().removeAll("text-field-danger");

        String score = correctScoreField.getText() == null ? "" : correctScoreField.getText().trim();
        if (score.isEmpty() || score.equals("0")) {
            correctScoreField.setText(answer.getScore() > 0 ? String.valueOf(answer.getScore()) : "1");
        }
    }

    private static void highlightWhileDragging(Node target, java.util.function.BooleanSupplier accepts) {
        final String[] originalStyle = new String[1];
        target.setOnDragEntered(event -> {
            if (draggedAnswer != null && accepts.getAsBoolean()) {
                originalStyle[0] = target.getStyle();
                target.setStyle(originalStyle[0] + DROP_HIGHLIGHT);
            }
            event.consume();
        });
        target.setOnDragExited(event -> {
            if (originalStyle[0] != null) {
                target.setStyle(originalStyle[0]);
                originalStyle[0] = null;
            }
            event.consume();
        });
    }

    static int parseScore(TextField field, int fallback) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
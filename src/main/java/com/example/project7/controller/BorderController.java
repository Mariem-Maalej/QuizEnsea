package com.example.project7.controller;

import com.example.project7.FxmlLoader;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;

import java.net.URL;
import java.util.ResourceBundle;


public class BorderController implements Initializable {
    private double xOffSet = 0;
    private double yOffSet = 0;


    Stage stage;
    @FXML
    private AnchorPane all;
    @FXML
    private AnchorPane  border;

    @FXML
    private AnchorPane mainscreen;

    @FXML
    private void handleMousePressed(MouseEvent event) {
        Stage stage = (Stage) border.getScene().getWindow();

        xOffSet = event.getSceneX();
        yOffSet = event.getSceneY();
    }

    @FXML
    private void handleMouseDragged(MouseEvent event) {
        Stage stage = (Stage) border.getScene().getWindow();

        if (!stage.isMaximized()) {
            stage.setX(event.getScreenX() - xOffSet);
            stage.setY(event.getScreenY() - yOffSet);
        }
    }

    @FXML
    void handleClicksClose(ActionEvent event) {
        stage = (Stage) all.getScene().getWindow();
        stage.close();
    }

    @FXML
    void handleClicksMinimize(ActionEvent event) {
        stage = (Stage) all.getScene().getWindow();
        stage.setIconified(true);
    }
    @FXML
    private void handleClicksMaximize(ActionEvent event) {
        Stage stage = (Stage) all.getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        FxmlLoader object = new FxmlLoader();
        AnchorPane view = object.getPane("Barre");
        if (view == null) {
            System.err.println("Impossible de charger Barre.fxml");
            return;
        }
        mainscreen.getChildren().setAll(view);
        AnchorPane.setTopAnchor(view, 0.0);
        AnchorPane.setBottomAnchor(view, 0.0);
        AnchorPane.setLeftAnchor(view, 0.0);
        AnchorPane.setRightAnchor(view, 0.0);
    }


}

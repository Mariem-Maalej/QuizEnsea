
package com.example.project7.controller;

import com.example.project7.FxmlLoader;
import com.example.project7.NavigationUtils;

import com.example.project7.controller.edition.ExtractViewController;
import com.example.project7.controller.edition.OpenProjet;
import com.example.project7.controller.edition.SelectionTypeProjet;
import com.example.project7.controller.edition.ThemeView;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

import java.net.URL;
import java.util.ResourceBundle;
import com.jfoenix.controls.JFXButton;
public class InterfaceController implements Initializable {

    @FXML
    private AnchorPane anchorpane3;

    @FXML
    public Label name;


    // ==========================================
    // HOME
    // ==========================================

    @FXML
    void handleClicksAccueil(ActionEvent event) {

        FxmlLoader loader = new FxmlLoader();
        Parent view = loader.getPane("Home");

        if (view != null) {
            NavigationUtils.afficherPage(anchorpane3, view);

            // Désélectionne les autres boutons et active Home
            setActiveButton(btnHome);
        }
    }



    // ==========================================
    // EXTRACT
    // ==========================================

    @FXML
    private void handleClicksExtract(ActionEvent event) {

        try {
            FxmlLoader loader = new FxmlLoader();

            Parent view = loader.getPane(
                    "editer_quiz/ExtractView"
            );

            if (view == null) {
                System.err.println(
                        "Impossible de charger ExtractView.fxml"
                );
                return;
            }

            ExtractViewController controller =
                    (ExtractViewController) loader.getController();

            if (controller != null) {
                controller.setParentPane(anchorpane3);
            }

            NavigationUtils.afficherPage(anchorpane3, view);

        } catch (Exception e) {
            System.err.println(
                    "Erreur lors du chargement de ExtractView"
            );
            e.printStackTrace();
        }
        setActiveButton(btnExtract);
    }


    // ==========================================
    // NEW PROJECT
    // ==========================================

    @FXML
    void handleClicksNew(ActionEvent event) {

        FxmlLoader loader = new FxmlLoader();

        Parent view = loader.getPane(
                "editer_quiz/_1_SelectionTypeDeProjet"
        );

        if (view == null) {
            System.err.println(
                    "Impossible de charger SelectionTypeDeProjet.fxml"
            );
            return;
        }

        SelectionTypeProjet controller =
                (SelectionTypeProjet) loader.getController();

        if (controller != null) {
            controller.setParentPane(anchorpane3);
        }

        NavigationUtils.afficherPage(anchorpane3, view);
        setActiveButton(btnNew);
    }


    // ==========================================
    // OPEN PROJECT
    // ==========================================

    @FXML
    void handleClicksOpen(ActionEvent event) {

        FxmlLoader loader = new FxmlLoader();

        Parent view = loader.getPane("openinterf");

        if (view == null) {
            System.err.println(
                    "Impossible de charger openinterf.fxml"
            );
            return;
        }

        OpenProjet controller =
                (OpenProjet) loader.getController();

        if (controller != null) {
            controller.setParentPane(anchorpane3);
        }

        NavigationUtils.afficherPage(anchorpane3, view);
        // Mettre en évidence Open
        setActiveButton(btnOpen);
    }


    // ==========================================
    // SETTINGS
    // ==========================================

    @FXML
    void handleClicksSetting(ActionEvent event) {

        FxmlLoader loader = new FxmlLoader();

        Parent view = loader.getPane("SettingInterf");

        NavigationUtils.afficherPage(anchorpane3, view);
        setActiveButton(btnSetting);
    }


    // ==========================================
    // HELP
    // ==========================================

    @FXML
    void handleClicksHelp(ActionEvent event) {

        FxmlLoader loader = new FxmlLoader();

        Parent view = loader.getPane("HelpInterf");

        NavigationUtils.afficherPage(anchorpane3, view);
        setActiveButton(btnHelp);
    }


    // ==========================================
    // THEMES
    // ==========================================

    @FXML
    void handleClicksThemes(ActionEvent event) {

        try {
            URL fxmlUrl = getClass().getResource(
                    "/com/example/project7/editer_quiz/ThemeView.fxml"
            );

            if (fxmlUrl == null) {
                System.err.println(
                        "Fichier ThemeView.fxml introuvable !"
                );
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);

            Parent view = loader.load();

            ThemeView controller = loader.getController();

            if (controller != null) {
                controller.setParentPane(anchorpane3);
            }

            NavigationUtils.afficherPage(anchorpane3, view);

            System.out.println(
                    "ThemeView chargé avec succès !"
            );

        } catch (Exception e) {
            System.err.println(
                    "Erreur lors du chargement de ThemeView :"
            );
            e.printStackTrace();
        }
        setActiveButton(btnGererThemes);
    }

    @FXML private JFXButton btnHome;
    @FXML private JFXButton btnNew;
    @FXML private JFXButton btnOpen;
    @FXML private JFXButton btnGererThemes;
    @FXML private JFXButton btnExtract;
    @FXML private JFXButton btnSetting;
    @FXML private JFXButton btnHelp;

    private void setActiveButton(JFXButton activeButton) {

        JFXButton[] buttons = {
                btnHome,
                btnNew,
                btnOpen,
                btnGererThemes,
                btnExtract,
                btnSetting,
                btnHelp
        };

        for (JFXButton button : buttons) {
            if (button != null) {
                button.getStyleClass().remove("active");
            }
        }

        if (activeButton != null) {
            activeButton.getStyleClass().add("active");
        }
    }

    // ==========================================
    // INITIALISATION
    // ==========================================

    @Override
    public void initialize(
            URL location,
            ResourceBundle resources
    ) {

        FxmlLoader loader = new FxmlLoader();

        Parent view = loader.getPane("Home");

        NavigationUtils.afficherPage(anchorpane3, view);

    setActiveButton(btnHome);
    }
}

package com.example.project7;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
public class NavigationUtils  {

    public static void afficherPage(AnchorPane parent, Node page) {

        if (parent == null || page == null) {
            return;
        }

        parent.getChildren().setAll(page);

        AnchorPane.setTopAnchor(page, 0.0);
        AnchorPane.setBottomAnchor(page, 0.0);
        AnchorPane.setLeftAnchor(page, 0.0);
        AnchorPane.setRightAnchor(page, 0.0);
    }
}
package at.tugraz.oo2.client.ui;

import at.tugraz.oo2.client.ui.controller.MainUI;
import at.tugraz.oo2.helpers.Log;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class GUIMain extends Application {

	public static void openGUI(String... args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) throws IOException {
		setUserAgentStylesheet(STYLESHEET_MODENA);
		stage.setTitle("OO2 Client");
		stage.setMaximized(true);
		stage.setMinWidth(800);
		stage.setMinHeight(500);

		final Parent root = new MainUI();
		root.prefHeight(stage.getHeight());
		root.prefWidth(stage.getWidth());

		final Scene scene = new Scene(root);
		stage.setScene(scene);
		stage.show();
		stage.setOnCloseRequest(windowEvent -> {
			((MainUI) root).cancelServices();
			Log.INFO("Close button pressed - now terminating the client.", false);
			System.exit(0);
		});
	}
}

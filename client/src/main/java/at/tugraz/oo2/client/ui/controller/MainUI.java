package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.client.ClientConnection;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class MainUI extends VBox {

	private final ClientConnection clientConnection;

	public MainUI() {
		clientConnection = new ClientConnection();
		FXMLLoader loader = new FXMLLoader();
		loader.setControllerFactory(c -> this);
		loader.setRoot(this);
		try {
			loader.load(getClass().getResource("/main.fxml").openStream());
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}

		AssignmentUI assignmentUI = new AssignmentUI(clientConnection);
		assignmentUI.prefHeightProperty().bind(this.heightProperty());
		assignmentUI.prefWidthProperty().bind(this.widthProperty());

		this.getChildren().add(new ConnectionUI(clientConnection));
		this.getChildren().add(assignmentUI);
	}

	public void cancelServices() {
		clientConnection.closeServerThread();
	}
}

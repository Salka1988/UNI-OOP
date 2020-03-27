package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.client.ClientConnection;
import at.tugraz.oo2.helpers.Log;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;
import java.io.IOException;

public class ConnectionUI extends GridPane {

	private final ClientConnection clientConnection;

	@FXML
	public Button connectButton;
	@FXML
	public Button disconnectButton;
	@FXML
	public TextField serverTextField;
	@FXML
	public TextField portTextField;
	@FXML
	public Label status;


	public ConnectionUI(ClientConnection clientConnection) {
		this.clientConnection = clientConnection;
		FXMLLoader loader = new FXMLLoader();
		loader.setControllerFactory(c -> this);
		loader.setRoot(this);
		try {
			loader.load(getClass().getResource("/connection.fxml").openStream());
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}

		clientConnection.addConnectionClosedListener(this::onConnectionClosed);
		clientConnection.addConnectionOpenedListener(this::onConnectionOpened);
		clientConnection.addConnectionBrokeListener(this::onConnectionBroken);
	}

	private void onConnectionOpened() {
		setConnected(true);
		Platform.runLater(() -> status.setText("Connected!"));
		// play audio sound on opened connection
		try {
			Clip clip = AudioSystem.getClip();
			clip.open(AudioSystem.getAudioInputStream(new File("jiraiyas_theme.wav")));
			clip.start();
		} catch (Exception ex) {
			Log.ERROR("Error playing audio file on connection opened", false);
		}
	}

	private void onConnectionClosed() {
		setConnected(false);
		Platform.runLater(() -> status.setText("Disconnected!"));
	}

	private void onConnectionBroken() {
		showAlertDialog("Connection error", "There was an error while trying to reach the server.");
		clientConnection.closeServerThread();
		onConnectionClosed();
	}

	private void setConnected(boolean connected) {
		connectButton.setVisible(!connected);
		disconnectButton.setVisible(connected);
		serverTextField.setDisable(connected);
		portTextField.setDisable(connected);
	}


	public void onConnectClick(ActionEvent actionEvent) {
		if (checkValidInput()) {
			try {
				clientConnection.connect(serverTextField.getText(), Integer.parseInt(portTextField.getText()));
			} catch (IOException e) {
				Log.ERROR("[GUI] Error connecting to the server.", false);
				onConnectionBroken();
			}
		}
	}

	public void onDisconnectClick(ActionEvent actionEvent) throws IOException {
		clientConnection.closeServerThread();
	}

	private boolean checkValidInput() {
		boolean success;
		try {
			success = !serverTextField.getText().isEmpty() && !portTextField.getText().isEmpty();
			Integer.parseInt(portTextField.getText());
		} catch (NumberFormatException | NullPointerException nfe) {
			success = false;
		}

		if (!success) showAlertDialog("Invalid server/port", "Please insert valid server/port values.");

		return success;
	}

	private void showAlertDialog(String header, String content) {
		Platform.runLater(() -> {
			Alert errorAlert = new Alert(Alert.AlertType.ERROR);
			errorAlert.setHeaderText(header);
			errorAlert.setContentText(content);
			errorAlert.showAndWait();
		});
	}
}

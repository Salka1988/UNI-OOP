package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.client.ClientConnection;
import at.tugraz.oo2.data.LiveData;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LiveUI extends AnchorPane {

	private final ClientConnection clientConnection;
	private ScheduledFuture<?> service;

	@FXML
	private TableView<at.tugraz.oo2.data.LiveData> tvData;

	@FXML
	private ProgressIndicator progressBar;

	@FXML
	private HBox progressHbox;

	public LiveUI(ClientConnection clientConnection) {
		this.clientConnection = clientConnection;

		FXMLLoader loader = new FXMLLoader(getClass().getResource("/live.fxml"));
		loader.setRoot(this);
		loader.setController(this);

		try {
			loader.load();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}

		progressHbox.setAlignment(Pos.CENTER);
		tvData.setPlaceholder(new Label(""));

		clientConnection.addConnectionClosedListener(this::onConnectionClosed);
		clientConnection.addConnectionOpenedListener(this::onConnectionOpened);
		clientConnection.addConnectionBrokeListener(this::onConnectionClosed);
	}

	private void onConnectionClosed() {
		service.cancel(true);
		Platform.runLater(() -> tvData.getItems().clear());
	}

	private void onConnectionOpened() {
		Platform.runLater(() -> {
			progressHbox.setVisible(true);
			progressBar.setVisible(true);
		});

		ObservableList<at.tugraz.oo2.data.LiveData> list = FXCollections.observableArrayList();

		list.addListener((ListChangeListener<LiveData>) change -> {
			Platform.runLater(() -> {
				progressHbox.setVisible(false);
				progressBar.setVisible(false);
			});
		});

		service = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
			Instant started = Instant.now();
			clientConnection.getLiveData().thenAccept(liveData -> {
				list.clear();
				list.addAll(liveData);
				printReceivedAt(started, Instant.now());
			});
		}, 0, 10, TimeUnit.SECONDS);

		tvData.setItems(list);
	}


	/*
	 * Method that calculates the time the live data needed to be fetched
	 * Logs the time in seconds
	 * */
	private void printReceivedAt(Instant started, Instant ended) {
		long time = ended.getEpochSecond() - started.getEpochSecond();
//		Log.INFO("LiveData took " + time + " seconds", false);
	}

	@FXML
	public void initialize() {
		TableColumn sensorColumn = new TableColumn<LiveData, Object>("Sensor");
		TableColumn<LiveData, String> locationColumn = new TableColumn<>("Location");
		TableColumn<LiveData, String> metricColumn = new TableColumn<>("Metric");
		sensorColumn.getColumns().addAll(locationColumn, metricColumn);
		TableColumn<LiveData, String> dataColumn = new TableColumn<>("Data");
		TableColumn<LiveData, String> timestampColumn = new TableColumn<>("Last update");

		locationColumn.setMinWidth(250);
		metricColumn.setMinWidth(250);
		dataColumn.setMinWidth(200);
		timestampColumn.setMinWidth(250);

		tvData.getColumns().addAll(sensorColumn, dataColumn, timestampColumn);
		locationColumn.setCellValueFactory(new PropertyValueFactory<LiveData, String>("location"));
		metricColumn.setCellValueFactory(new PropertyValueFactory<LiveData, String>("metric"));
		dataColumn.setCellValueFactory(new PropertyValueFactory<LiveData, String>("data"));
		timestampColumn.setCellValueFactory(new PropertyValueFactory<LiveData, String>("timestamp"));

	}
}

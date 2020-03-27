package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.Util;
import at.tugraz.oo2.client.ClientConnection;
import at.tugraz.oo2.client.ui.component.drawing.DrawingTable;
import at.tugraz.oo2.data.MatchedCurve;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SketchUI extends AnchorPane {
	private final ClientConnection clientConnection;
	private final AssignmentUI assignmentUI;
	private DrawingTable drawingTable;
	private ObservableList<SimInfo> simInfoObservableList = FXCollections.observableArrayList();
	private Map<SimInfo, Tab> tabMap = new HashMap<>();

	@FXML
	private GridPane drawingPane;
	@FXML
	private ListView<String> lvMetric;
	@FXML
	private DatePicker dpFrom;
	@FXML
	private DatePicker dpTo;
	@FXML
	private TextField tfMinSize;
	@FXML
	private TextField tfMaxSize;
	@FXML
	private TextField tfMaxResultCount;
	@FXML
	private Button btnStart;
	@FXML
	private TableView<SimInfo> tvSimResults;
	@FXML
	private VBox vbMiddle;
	@FXML
	private Label labelInfo;
	@FXML
	private ProgressIndicator progressIndicator;
	@FXML
	private GridPane leftGridPane;
	@FXML
	private Button btnClear;

	public SketchUI(ClientConnection clientConnection, final AssignmentUI assignmentUI) {
		this.clientConnection = clientConnection;
		this.assignmentUI = assignmentUI;

		FXMLLoader loader = new FXMLLoader(getClass().getResource("/sketch.fxml"));

		loader.setControllerFactory(c -> this);
		loader.setRoot(this);

		try {
			loader.load();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}

		drawingTable = new DrawingTable(drawingPane);

		clientConnection.addConnectionOpenedListener(this::onConnectionOpened);
		clientConnection.addConnectionClosedListener(this::onConnectionClosed);
	}

	private void onConnectionClosed() {
		Platform.runLater(() -> {
			simInfoObservableList.clear();
			tabMap.forEach((number, tab) -> assignmentUI.getTabs().remove(tab));
			tabMap.clear();
			labelInfo.setVisible(false);
			progressIndicator.setVisible(false);
		});
	}

	private void onConnectionOpened() {
		ObservableList<String> metrics = FXCollections.observableArrayList();
		clientConnection.querySensors().thenAcceptAsync(sensors -> {
			sensors.forEach(sensor -> {
				if (!metrics.contains(sensor.getMetric())) {
					metrics.add(sensor.getMetric());
				}
			});

			Platform.runLater(() -> lvMetric.setItems(metrics));
		});
	}

	private void onStartClicked() {
		String metric = lvMetric.getSelectionModel().getSelectedItem();
		if (metric == null) {
			showErrorDialog("Please choose a metric");
			return;
		}

		if (dpFrom.getValue() == null || dpTo.getValue() == null) {
			showErrorDialog("Please choose begin/end time!");
			return;
		}

		long from = Util.parseUserTime(dpFrom.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		long to = Util.parseUserTime(dpTo.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

		if (to < from) {
			showErrorDialog("End time should not be smaller than begin time!");
			return;
		}

		if (to == from) {
			showErrorDialog("End time and begin time should not be equal!");
			return;
		}

		if (tfMinSize.getText().isEmpty()) {
			showErrorDialog("Please provide min. window size!");
			return;
		}

		if (tfMaxSize.getText().isEmpty()) {
			showErrorDialog("Please provide max. window size!");
			return;
		}

		if (tfMaxResultCount.getText().isEmpty()) {
			showErrorDialog("Please provide max. result count!");
			return;
		}

		double[] drawnPoints = drawingTable.circlesValuesReturn();
		if (drawnPoints.length == 0) {
			showErrorDialog("Please draw a reference curve!");
			return;
		}

		simInfoObservableList.clear();
		Platform.runLater(() -> {
			tabMap.forEach((number, tab) -> assignmentUI.getTabs().remove(tab));
			tabMap.clear();
			labelInfo.setVisible(false);
			tvSimResults.setDisable(true);
			leftGridPane.setDisable(true);
			progressIndicator.setVisible(true);
		});

		clientConnection.getSimilarity(
				metric,
				from,
				to,
				Long.parseLong(tfMinSize.getText()) * Util.MIN_TO_MS,
				Long.parseLong(tfMaxSize.getText()) * Util.MIN_TO_MS,
				Integer.parseInt(tfMaxResultCount.getText()),
				drawnPoints
		).thenAcceptAsync(this::onSimResultReceived);
	}

	private void onSimResultReceived(List<MatchedCurve> matchedCurves) {
		List<SimInfo> simInfoList = new ArrayList<>();
		matchedCurves.forEach(matchedCurve -> {
			SimInfo simInfo = new SimInfo(matchedCurve.getSensor().getLocation(),
					matchedCurve.getSeries().getValueCount(),
					matchedCurve.getError());

			simInfo.setMatchedCurve(matchedCurve);
			simInfoList.add(simInfo);
		});
		Platform.runLater(() -> {
			progressIndicator.setVisible(false);
			tvSimResults.setDisable(false);
			labelInfo.setVisible(true);
			leftGridPane.setDisable(false);
			drawingPane.setDisable(false);
		});
		simInfoObservableList.addAll(simInfoList);
	}

	private void showErrorDialog(String message) {
		Platform.runLater(() -> {
			Alert errorAlert = new Alert(Alert.AlertType.ERROR);
			errorAlert.setHeaderText("Error!");
			errorAlert.setContentText(message);
			errorAlert.showAndWait();
		});
	}

	private void onTableRowDoubleClicked(TableRow<SimInfo> simInfoTableRow) {
		if (tabMap.containsKey(simInfoTableRow.getItem())) {
			Platform.runLater(() -> assignmentUI.getSelectionModel().select(tabMap.get(simInfoTableRow.getItem())));
		} else {
			final Tab tab = new Tab("Sim curve " + simInfoTableRow.getIndex());
			tab.setContent(new SimDetailUI(simInfoTableRow.getItem().matchedCurve));
			tab.setOnClosed(event -> tabMap.remove(simInfoTableRow.getItem()));
			tabMap.put(simInfoTableRow.getItem(), tab);
			Platform.runLater(() -> {
				assignmentUI.getTabs().add(tab);
				assignmentUI.getSelectionModel().select(tab);
			});
		}
	}

	private void onClearSketchPressed() {
		drawingTable.clearAll();
	}


	@FXML
	public void initialize() {
		lvMetric.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		Util.makeTextFieldNumberOnly(tfMaxResultCount);
		Util.makeTextFieldNumberOnly(tfMaxSize);
		Util.makeTextFieldNumberOnly(tfMinSize);
		tfMinSize.setText("30");
		tfMaxSize.setText("180");
		tfMaxResultCount.setText("25");
		btnStart.setOnMouseClicked(mouseEvent -> onStartClicked());
		tvSimResults.prefWidthProperty().bind(vbMiddle.widthProperty());
		tvSimResults.prefHeightProperty().bind(vbMiddle.heightProperty());
		btnClear.setOnMouseClicked(mouseEvent -> onClearSketchPressed());

		// sim info table view
		tvSimResults.setPlaceholder(new Label(""));
		TableColumn<SimInfo, String> locationColumn = new TableColumn<>("Location");
		TableColumn<SimInfo, Integer> memberColumn = new TableColumn<>("Data Points");
		TableColumn<SimInfo, Integer> errorColumn = new TableColumn<>("Error");
		locationColumn.setMinWidth(170);
		errorColumn.setMinWidth(115);
		memberColumn.setMinWidth(115);
		tvSimResults.getColumns().addAll(locationColumn, errorColumn, memberColumn);
		locationColumn.setCellValueFactory(new PropertyValueFactory<>("location"));
		errorColumn.setCellValueFactory(new PropertyValueFactory<>("error"));
		memberColumn.setCellValueFactory(new PropertyValueFactory<>("dataPoints"));
		tvSimResults.setItems(simInfoObservableList);

		// detect double click
		tvSimResults.setRowFactory( tv -> {
			TableRow<SimInfo> row = new TableRow<>();
			row.setOnMouseClicked(event -> {
				if (event.getClickCount() == 2 && (! row.isEmpty()) ) {
					onTableRowDoubleClicked(row);
				}
			});
			return row;
		});

		// default dates
		Calendar date = new GregorianCalendar();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		date.set(Calendar.MONTH, 10); // november actually
		date.set(Calendar.DAY_OF_MONTH, 1);
		date.set(Calendar.YEAR, 2019);
		LocalDate fromDate = LocalDate.of(date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
		date.set(Calendar.MONTH, 11); // december actually
		LocalDate toDate = LocalDate.of(date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
		dpFrom.setValue(fromDate);
		dpTo.setValue(toDate);
	}

	@Data
	public static class SimInfo {
		private final String location;
		private final int dataPoints;
		private final double error;
		private @Setter @Getter MatchedCurve matchedCurve;
	}
}

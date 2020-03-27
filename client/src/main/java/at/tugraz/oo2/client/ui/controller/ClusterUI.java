package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.Util;
import at.tugraz.oo2.client.ClientConnection;
import at.tugraz.oo2.data.ClusterDescriptor;
import at.tugraz.oo2.data.Sensor;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ClusterUI extends AnchorPane {

	private final ClientConnection clientConnection;
	private final AssignmentUI assignmentUI;
	private Map<String, List<String>> sensorMap = new HashMap<>();
	private ObservableList<String> metrics = FXCollections.observableArrayList();
	private ObservableList<ClusterInfo> clusterInfoObservableList = FXCollections.observableArrayList();
	private Map<Integer, Tab> tabMap = new HashMap<>();

	@FXML
	private ListView<String> lvSensors;
	@FXML
	private DatePicker dpFrom;
	@FXML
	private DatePicker dpTo;
	@FXML
	private TextField tfIntervalClusters;
	@FXML
	private TextField tfPointsCluster;
	@FXML
	private TextField tfClusters;
	@FXML
	private ComboBox<String> cbMetric;
	@FXML
	private Button btnStart;
	@FXML
	private TableView<ClusterInfo> tvClusters;
	@FXML
	private ScrollPane scrollPane;
	@FXML
	private ScrollPane spChart;
	@FXML
	private GridPane gpChart;
	@FXML
	private HBox mainHbox;
	@FXML
	private GridPane chartGrid;
	@FXML
	private Label clusterDetailsInfo;
	@FXML
	private ProgressIndicator progressIndicator;
	@FXML
	private GridPane leftGridPane;

	public ClusterUI(ClientConnection clientConnection, final AssignmentUI assignmentUI) {
		this.clientConnection = clientConnection;
		this.assignmentUI = assignmentUI;

		FXMLLoader loader = new FXMLLoader(getClass().getResource("/cluster.fxml"));
		loader.setRoot(this);
		loader.setController(this);

		try {
			loader.load();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}

		clientConnection.addConnectionOpenedListener(this::onConnectionOpened);
		clientConnection.addConnectionClosedListener(this::onConnectionClosed);
	}

	private void onConnectionOpened() {
		getSensors();

//		// give date pickers default values
		// input data from the task description
		// cluster HSi12 humidity 2019-09-01 2019-10-01 120 5 10 (choose HSi12 and humidity from GUI)
		Calendar date = new GregorianCalendar();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		date.set(Calendar.MONTH, 8); // september actually
		date.set(Calendar.DAY_OF_MONTH, 1);
		date.set(Calendar.YEAR, 2019);
		LocalDate fromDate = LocalDate.of(date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
		date.set(Calendar.MONTH, 9); // october actually
		LocalDate toDate = LocalDate.of(date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
		dpFrom.setValue(fromDate);
		dpTo.setValue(toDate);
		tfIntervalClusters.setText("120");
		tfPointsCluster.setText("5");
		tfClusters.setText("10");
		spChart.setVisible(false);
	}

	private void onConnectionClosed() {
		sensorMap.clear();
		Platform.runLater(() -> {
			clusterInfoObservableList.clear();
			tabMap.forEach((number, tab) -> assignmentUI.getTabs().remove(tab));
			tabMap.clear();
			lvSensors.getItems().clear();
			metrics.clear();
			tvClusters.getItems().clear();
			spChart.setVisible(false);
		});
	}

	private void getSensors() {
		ObservableList<String> sensorList = FXCollections.observableArrayList();
		clientConnection.querySensors().thenAcceptAsync(sensors -> {
			sensors.forEach(sensor -> {
				if (sensorMap.get(sensor.getLocation()) == null) {
					List<String> metrics = new ArrayList<>();
					metrics.add(sensor.getMetric());
					sensorMap.put(sensor.getLocation(), metrics);
					sensorList.add(sensor.getLocation());
				} else {
					sensorMap.get(sensor.getLocation()).add(sensor.getMetric());
				}
			});

			Platform.runLater(() -> lvSensors.setItems(sensorList));
		});
	}

	private void onLocationSelected() {
		metrics.clear();
		metrics.addAll(sensorMap.get(lvSensors.getSelectionModel().getSelectedItem()));
		cbMetric.getSelectionModel().selectFirst();
	}

	private void onStartClicked() {
		String location = lvSensors.getSelectionModel().getSelectedItem();
		if (location == null || cbMetric.getValue().isEmpty()) {
			showErrorDialog("Please choose a location.");
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

		if (tfIntervalClusters.getText().isEmpty()) {
			showErrorDialog("Please provide the number of interval clusters!");
			return;
		}

		if (Long.parseLong(tfIntervalClusters.getText()) < 5 || Long.parseLong(tfIntervalClusters.getText()) % 5 != 0) {
			showErrorDialog("The number of clusters should be at least 5 minutes and also divisible by 5!");
			return;
		}

		if (tfPointsCluster.getText().isEmpty()) {
			showErrorDialog("Please provide the number of cluster points!");
			return;
		}

		if (tfClusters.getText().isEmpty()) {
			showErrorDialog("Please provide the number of clusters!");
			return;
		}

		Platform.runLater(() -> {
			tabMap.forEach((number, tab) -> assignmentUI.getTabs().remove(tab));
			tabMap.clear();
			clusterDetailsInfo.setVisible(false);
			spChart.setVisible(false);
			scrollPane.setDisable(true);
			progressIndicator.setVisible(true);
			leftGridPane.setDisable(true);
			clusterInfoObservableList.clear();
		});
		Sensor sensor = new Sensor(location, cbMetric.getValue());
		long intervalClusters = Long.parseLong(tfIntervalClusters.getText()) * 60 * 1000;
		long intervalPoints = Long.parseLong(tfPointsCluster.getText()) * 60 * 1000;
		int numberOfClusters = Integer.parseInt(tfClusters.getText());
		clientConnection
				.getClustering(sensor, from, to, intervalClusters, intervalPoints, numberOfClusters)
				.thenAcceptAsync(this::onReceiveClusters);
	}

	private void showErrorDialog(String message) {
		Platform.runLater(() -> {
			Alert errorAlert = new Alert(Alert.AlertType.ERROR);
			errorAlert.setHeaderText("Error!");
			errorAlert.setContentText(message);
			errorAlert.showAndWait();
		});
	}

	private void onReceiveClusters(List<ClusterDescriptor> clusterDescriptorList) {

		if (clusterDescriptorList == null) {
			Platform.runLater(() -> {
				spChart.setContent(new Label("No data."));
				progressIndicator.setVisible(false);
				spChart.setVisible(true);
				leftGridPane.setDisable(false);
				scrollPane.setDisable(false);
			});
			return;
		}

		// sort clusters by error
		clusterDescriptorList.sort((o1, o2) -> {
			if (o1.getClusterError() == o2.getClusterError()) return 0;
			return o1.getClusterError() < o2.getClusterError() ? -1 : 0;
		});

		// fill table view with data
		clusterInfoObservableList.clear();
		for (int index = 0; index < clusterDescriptorList.size(); index++) {
			ClusterDescriptor current = clusterDescriptorList.get(index);
			ClusterInfo clusterInfo = new ClusterInfo(index, current.getMembers().size(), current.getClusterError());
			clusterInfo.setClusterDescriptor(current);
			clusterInfo.setMetric(cbMetric.getSelectionModel().getSelectedItem());
			clusterInfoObservableList.add(clusterInfo);
		}
		Platform.runLater(() -> clusterDetailsInfo.setVisible(true));

		// prepare charts for each cluster
		List<XYChart.Series> chartsToBeDrawn = new ArrayList<>();
		for (int index = 0; index < clusterDescriptorList.size(); index++) {
			ClusterDescriptor current = clusterDescriptorList.get(index);
			XYChart.Series series = new XYChart.Series();
			series.setName("Cluster " + index);
			int interval = 0;
			long chosenInterval = Long.parseLong(tfPointsCluster.getText());
			double[] average = current.getAverage();
			for (double v : average) {
				series.getData().add(new XYChart.Data(interval, v));
				interval += chosenInterval;
			}
			chartsToBeDrawn.add(series);
		}

		// draw charts
		drawCharts(chartsToBeDrawn);
	}

	private void drawCharts(List<XYChart.Series> charts) {
		Platform.runLater(() -> {
			GridPane subPane = new GridPane();
			subPane.setVgap(50);
			subPane.setHgap(80);
			subPane.alignmentProperty().setValue(Pos.CENTER);
			int row = 0, column = 0;
			for (XYChart.Series chart : charts) {
				LineChart<Number, Number> lineChart = new LineChart<>(new NumberAxis(), new NumberAxis());
				lineChart.getXAxis().setLabel("Minutes");
				lineChart.getYAxis().setLabel("Average value");
				lineChart.setTitle("");
				lineChart.setAnimated(false);
				lineChart.getData().add(chart);
				lineChart.setMinSize(0, 0);
				subPane.add(lineChart, column++, row);
				if (column == 2) row++;
				column %= 2;
			}
			spChart.setContent(subPane);
			progressIndicator.setVisible(false);
			spChart.setVisible(true);
			leftGridPane.setDisable(false);
			scrollPane.setDisable(false);
		});
	}

	private void onTableRowDoubleClicked(ClusterInfo clusterInfo) {
		if (tabMap.containsKey(clusterInfo.getClusterNo())) {
			assignmentUI.getSelectionModel().select(tabMap.get(clusterInfo.getClusterNo()));
		} else {
			Tab tab = new Tab("Cluster " + clusterInfo.clusterNo);
			tabMap.put(clusterInfo.clusterNo, tab);
			assignmentUI.getTabs().add(tab);
			tab.setContent(new ClusterDetailUI(clusterInfo));
			tab.setOnClosed(event -> tabMap.remove(clusterInfo.clusterNo));
			assignmentUI.getSelectionModel().select(tab);
		}
	}

	@FXML
	public void initialize() {
		Util.makeTextFieldNumberOnly(tfClusters);
		Util.makeTextFieldNumberOnly(tfIntervalClusters);
		Util.makeTextFieldNumberOnly(tfPointsCluster);
		lvSensors.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		lvSensors.setOnMouseClicked(mouseClickEvent -> onLocationSelected());
		cbMetric.setItems(metrics);
		btnStart.setOnMouseClicked(mouseEvent -> onStartClicked());

		// table view
		tvClusters.prefWidthProperty().bind(scrollPane.widthProperty());
		tvClusters.prefHeightProperty().bind(scrollPane.heightProperty());
		tvClusters.setPlaceholder(new Label(""));
		TableColumn<ClusterInfo, Integer> clusterNoColumn = new TableColumn<>("Cluster no.");
		TableColumn<ClusterInfo, Integer> memberColumn = new TableColumn<>("Members");
		TableColumn<ClusterInfo, Integer> errorColumn = new TableColumn<>("Error");
		clusterNoColumn.setMinWidth(100);
		errorColumn.setMinWidth(190);
		memberColumn.setMinWidth(100);
		tvClusters.getColumns().addAll(clusterNoColumn, errorColumn, memberColumn);
		clusterNoColumn.setCellValueFactory(new PropertyValueFactory<>("clusterNo"));
		errorColumn.setCellValueFactory(new PropertyValueFactory<>("error"));
		memberColumn.setCellValueFactory(new PropertyValueFactory<>("members"));
		tvClusters.setItems(clusterInfoObservableList);

		// detect double click
		tvClusters.setRowFactory( tv -> {
			TableRow<ClusterInfo> row = new TableRow<>();
			row.setOnMouseClicked(event -> {
				if (event.getClickCount() == 2 && (! row.isEmpty()) ) {
					onTableRowDoubleClicked(row.getItem());
				}
			});
			return row;
		});

		// grid chart view
		gpChart.setAlignment(Pos.CENTER);
		spChart.prefHeightProperty().bind(gpChart.heightProperty());
		spChart.prefWidthProperty().bind(gpChart.widthProperty());
		spChart.setFitToWidth(true);
	}

	@Data
	public static class ClusterInfo {
		private final int clusterNo;
		private final int members;
		private final double error;
		private @Getter @Setter ClusterDescriptor clusterDescriptor;
		private @Getter @Setter String metric;
	}
}

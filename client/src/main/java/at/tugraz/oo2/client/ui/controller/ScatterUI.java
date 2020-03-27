package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.Util;
import at.tugraz.oo2.client.ClientConnection;
import at.tugraz.oo2.client.ui.ScatterHistoryUI;
import at.tugraz.oo2.data.Sensor;
import at.tugraz.oo2.helpers.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ScatterUI extends HBox {

	private final ClientConnection clientConnection;
	private Map<String, List<String>> sensorMap = new HashMap<>();
	private Task<ScatterData> task;
	private ScatterChart<Number, Number> scatterChart;
	private boolean wasQueryingData = false;

	@FXML
	private ComboBox cbSensorX;
	@FXML
	private ComboBox cbSensorY;
	@FXML
    private ComboBox metricComboBox;
	@FXML
	private DatePicker dpFrom;
	@FXML
	private DatePicker dpTo;
	@FXML
	private TextField tfInterval;
	@FXML
	private GridPane rightPanelPane;
	@FXML
	private GridPane leftGridPane;
	@FXML
	private HBox rightGridHbox;
	@FXML
	private ProgressIndicator progressIndicator;

	public ScatterUI(ClientConnection clientConnection) {
		this.clientConnection = clientConnection;

		FXMLLoader loader = new FXMLLoader(getClass().getResource("/scatter.fxml"));
		loader.setRoot(this);
        loader.setControllerFactory(c -> this);

		try {
			loader.load();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}

		clientConnection.addConnectionOpenedListener(this::onConnectionOpened);
		clientConnection.addConnectionClosedListener(this::onConnectionClosed);
	}

	private void onConnectionClosed() {
		Platform.runLater(() -> {
			cbSensorX.getItems().clear();
			cbSensorY.getItems().clear();
			dpTo.setValue(null);
			dpFrom.setValue(null);
			tfInterval.setText("");
			if (scatterChart != null) {
				scatterChart.getData().clear();
				rightGridHbox.getChildren().remove(scatterChart);
			}
			if (wasQueryingData) {
				task.cancel();
				wasQueryingData = false;
				leftGridPane.setDisable(false);
				progressIndicator.setVisible(false);
			}
		});
		sensorMap.clear();
	}

	private void onConnectionOpened() {
		getSensors();

		// give date pickers default value from today's midnight
		Calendar date = new GregorianCalendar();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		LocalDate todayMidnight = LocalDate.of(date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
		dpFrom.setValue(todayMidnight);
		dpTo.setValue(todayMidnight);

		// give interval text field default value
		tfInterval.setText("60");
	}

	private void getSensors() {
		ObservableList<String> list = FXCollections.observableArrayList();
		clientConnection.querySensors().thenAcceptAsync(sensors -> {
			sensors.forEach(sensor -> {
				if (sensorMap.get(sensor.getLocation()) == null) {
					List<String> metrics = new ArrayList<>();
					metrics.add(sensor.getMetric());
					sensorMap.put(sensor.getLocation(), metrics);
					list.add(sensor.getLocation());
				} else {
					sensorMap.get(sensor.getLocation()).add(sensor.getMetric());
				}
			});

			Platform.runLater(() -> {
				cbSensorX.setItems(list);
				cbSensorY.setItems(list);

				cbSensorX.getSelectionModel().selectFirst();
				cbSensorY.getSelectionModel().selectFirst();

				fillCommonMetrics(cbSensorX.getSelectionModel().getSelectedItem().toString(),
						cbSensorY.getSelectionModel().getSelectedItem().toString());
			});
		});
	}

	private void onComboBoxItemsSelected() {
		if (!cbSensorX.getSelectionModel().isEmpty() && !cbSensorY.getSelectionModel().isEmpty() && !sensorMap.isEmpty()) {
			String locationX = cbSensorX.getSelectionModel().getSelectedItem().toString();
			String locationY = cbSensorY.getSelectionModel().getSelectedItem().toString();

			fillCommonMetrics(locationX, locationY);
		}
	}

	private void fillCommonMetrics(String locationX, String locationY) {
		List<String> metricsX = sensorMap.get(locationX);
		metricsX.retainAll(sensorMap.get(locationY));

		ObservableList<String> list = FXCollections.observableArrayList();
		list.addAll(metricsX);

		if (!list.isEmpty()) {
			metricComboBox.setItems(list);
			metricComboBox.getSelectionModel().selectFirst();
		}
	}

	public void onDrawClick(ActionEvent actionEvent) {
	    if (cbSensorY.getSelectionModel().getSelectedItem().toString()
				.equals(cbSensorX.getSelectionModel().getSelectedItem().toString())) {
            showErrorDialog("Please select distinct locations!");
            return;
        }

		if (dpFrom.getValue() == null) {
			showErrorDialog("Please select start date!");
			return;
		}

		if (dpTo.getValue() == null) {
			showErrorDialog("Please select end date!");
			return;
		}

		if (tfInterval.getText().isBlank()) {
			showErrorDialog("Please insert an interval!");
			return;
		}

		if (Integer.parseInt(tfInterval.getText()) % 5 != 0) {
			showErrorDialog("Interval must be divisible by 5!");
			return;
		}

		long from = Util.parseUserTime(dpFrom.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		long to = Util.parseUserTime(dpTo.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		long interval = Integer.parseUnsignedInt(String.valueOf(Integer.parseInt(tfInterval.getText()) * 60 * 1000));

		if (to - from < interval) {
			showErrorDialog("Interval is bigger than the whole data series! Make sure that \"from\", \"to\" and \"interval\" have correct values.");
			return;
		}

		wasQueryingData = true;

		Platform.runLater(() -> {
			if (scatterChart != null) rightGridHbox.getChildren().remove(scatterChart);
			progressIndicator.setVisible(true);
			leftGridPane.setDisable(true);
		});

		String locationX = cbSensorX.getSelectionModel().getSelectedItem().toString();
		String locationY = cbSensorY.getSelectionModel().getSelectedItem().toString();
		String metric = metricComboBox.getSelectionModel().getSelectedItem().toString();
		List<Sensor> sensorList = Arrays.asList(new Sensor(locationX, metric), new Sensor(locationY, metric));

		List<CompletableFuture<List<String>>> listOfValues = new ArrayList<>();
		sensorList.forEach(sensor -> listOfValues.add(CompletableFuture.supplyAsync(() -> {
			List<String> values = new ArrayList<>();
			try {
				clientConnection.queryData(sensor, from, to, interval).get().forEach(dataPoint -> values.add(String.valueOf(dataPoint.getValue())));
			} catch (InterruptedException | ExecutionException e) {
				Log.ERROR("Something went wrong when fetching data series for scatter!", false);
			}
			return values;
		})));

		task = new Task<>() {
			@Override
			protected ScatterData call() throws Exception {
				List<List<String>> twoListsOfDataPoints = new ArrayList<>();
				listOfValues.forEach(seriesCompletableFuture -> {
					try {
						twoListsOfDataPoints.add(seriesCompletableFuture.get());
					} catch (InterruptedException | ExecutionException e) {
						Log.ERROR("Something went wrong when fetching data series for line chart!", false);
					}
				});

				// we know that there will always be data from just two sensors
				List<String> dataX = twoListsOfDataPoints.get(0);
				List<String> dataY = twoListsOfDataPoints.get(1);

				int size = dataX.size();
				if (dataY.size() < size) size = dataY.size();

				XYChart.Series series = new XYChart.Series();
				double minimumX = Double.MAX_VALUE, minimumY = Double.MAX_VALUE;
				double maximumX = 0.0, maximumY = 0.0, sumOfAllX = 0.0, sumOfAllY = 0.0;

				for (int iterator = 0; iterator < size; iterator++) {
					double valueX = Double.parseDouble(dataX.get(iterator));
					double valueY = Double.parseDouble(dataY.get(iterator));
					if (minimumX > valueX) minimumX = valueX;
					if (minimumY > valueY) minimumY = valueY;
					if (maximumX < valueX) maximumX = valueX;
					if (maximumY < valueY) maximumY = valueY;
					sumOfAllX += valueX;
					sumOfAllY += valueY;
					series.getData().add(new XYChart.Data(valueX, valueY));
				}

				double miX = sumOfAllX / size;
				double miY = sumOfAllY / size;

				return new ScatterData(miX, miY, minimumX, maximumX, minimumY, maximumY, series, locationX, locationY, metric);
			}
		};

		task.stateProperty().addListener((observableValue, state, t1) -> {
			if (t1 == Worker.State.SUCCEEDED) {
				drawChart(task.getValue());
			}
		});

		new Thread(task).start();
	}

	private void drawChart(ScatterData scatterData) {
		wasQueryingData = false;
		Platform.runLater(() -> {
			if (scatterChart != null)
				rightGridHbox.getChildren().remove(scatterChart);

			scatterChart = new ScatterChart<>(scatterData.getAxisX(), scatterData.getAxisY());
			scatterChart.setTitle("Scatter chart for " + scatterData.metric);
			scatterChart.setAnimated(false);
			scatterChart.setLegendVisible(false);

			rightGridHbox.getChildren().add(scatterChart);
			scatterChart.prefWidthProperty().bind(rightGridHbox.widthProperty());
			scatterChart.prefHeightProperty().bind(rightGridHbox.heightProperty());
			scatterChart.getData().add(scatterData.series);

			progressIndicator.setVisible(false);
			leftGridPane.setDisable(false);
		});

		saveSearchHistory();
	}

	private void showErrorDialog(String content) {
		Platform.runLater(() -> {
			Alert errorAlert = new Alert(Alert.AlertType.ERROR);
			errorAlert.setHeaderText("Error!");
			errorAlert.setContentText(content);
			errorAlert.showAndWait();
		});
	}

	private void saveSearchHistory() {
		long beginDate = Util.parseUserTime(dpFrom.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		long endDate = Util.parseUserTime(dpTo.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		String metric = metricComboBox.getSelectionModel().getSelectedItem().toString();
		String interval = tfInterval.getText();
		String locationX = cbSensorX.getSelectionModel().getSelectedItem().toString();
		String locationY = cbSensorY.getSelectionModel().getSelectedItem().toString();

		JsonObject jsonObject;
		// read json from file
		try {
			FileReader fr = new FileReader("./scatterHistory.json");
			StringBuilder content = new StringBuilder();
			int i;
			while ((i=fr.read()) != -1)
				content.append((char) i);

			jsonObject = new Gson().fromJson(content.toString(), JsonObject.class).getAsJsonObject();
			Log.INFO("File scatterHistory.json found! Using existent values array.", false);
		} catch (IOException e) {
			Log.INFO("File scatterHistory.json not found! Creating new one.", false);
			jsonObject = new JsonObject();
			jsonObject.addProperty("Created on", Util.TIME_FORMAT.format(Date.from(Instant.now())));
			JsonArray jsonArray = new JsonArray();
			jsonObject.add("values", jsonArray);
		}

		// create json
		JsonArray values = jsonObject.getAsJsonArray("values");
		JsonObject newEntry = new JsonObject();
		newEntry.addProperty("time", Util.TIME_FORMAT.format(Date.from(Instant.now())));
		newEntry.addProperty("locationX", locationX);
		newEntry.addProperty("locationY", locationY);
		newEntry.addProperty("beginDate", beginDate);
		newEntry.addProperty("endDate", endDate);
		newEntry.addProperty("interval", interval);
		newEntry.addProperty("metric", metric);

		values.add(newEntry);
		jsonObject.add("values", values);

		// store to file
		try {
			FileWriter file = new FileWriter("./scatterHistory.json");
			file.write(jsonObject.toString());
			file.close();
			Log.INFO("Successfully Copied JSON Object to File...", false);
		} catch (IOException e) {
			Log.ERROR("Error copying JSON object to file!", false);
		}
	}

	public void onHistoryClicked() {
		ScatterHistoryUI.ScatterChartData result = ScatterHistoryUI.display();
		if (!result.getDate().equals(ScatterHistoryUI.getNegativeAnswerConstant().getDate())) {
			reuseData(result);
			ScatterHistoryUI.resetAnswer();
		}
	}

	private void reuseData(ScatterHistoryUI.ScatterChartData scatterChartData) {
		tfInterval.setText(scatterChartData.getInterval());
		dpFrom.setValue(ScatterHistoryUI.ScatterChartData.getDate(scatterChartData.getBeginDate()));
		dpTo.setValue(ScatterHistoryUI.ScatterChartData.getDate(scatterChartData.getEndDate()));
		cbSensorX.getItems().clear();
		cbSensorY.getItems().clear();

		ObservableList<String> locations = FXCollections.observableArrayList();
		locations.addAll(sensorMap.keySet());
		cbSensorX.setItems(locations);
		cbSensorX.getItems().forEach(locationX -> {
			if (locationX.toString().equals(scatterChartData.getLocationX())) {
				cbSensorX.getSelectionModel().select(locationX);
			}
		});
		cbSensorY.setItems(locations);
		cbSensorY.getItems().forEach(locationY -> {
			if (locationY.toString().equals(scatterChartData.getLocationY())) {
				cbSensorY.getSelectionModel().select(locationY);
			}
		});

		fillCommonMetrics(scatterChartData.getLocationX(), scatterChartData.getLocationY());
		metricComboBox.getItems().forEach(metric -> {
			if (metric.toString().equals(scatterChartData.getMetric())) {
				metricComboBox.getSelectionModel().select(metric);
			}
		});
	}

	@FXML
	public void initialize() {
		rightGridHbox.setAlignment(Pos.CENTER);
		rightGridHbox.prefHeightProperty().bind(rightPanelPane.heightProperty());
		rightGridHbox.prefWidthProperty().bind(rightPanelPane.widthProperty());

		tfInterval.setText("60");
		Util.makeTextFieldNumberOnly(tfInterval);

		cbSensorY.setOnAction(actionEvent -> onComboBoxItemsSelected());
		cbSensorX.setOnAction(actionEvent -> onComboBoxItemsSelected());
	}

	private static class ScatterData {
		double miX;
		double miY;
		double minimumX;
		double maximumX;
		double minimumY;
		double maximumY;
		XYChart.Series series;
		String locationX;
		String locationY;
		String metric;

		ScatterData(double miX, double miY, double minimumX, double maximumX, double minimumY, double maximumY,
					XYChart.Series series, String locationX, String locationY, String metric) {
			this.miX = miX;
			this.miY = miY;
			this.minimumX = minimumX;
			this.maximumX = maximumX;
			this.minimumY = minimumY;
			this.maximumY = maximumY;
			this.series = series;
			this.locationX = locationX;
			this.locationY = locationY;
			this.metric = metric;
		}

		NumberAxis getAxisX() {
			double paddedMaximumX = getPaddedMaxX();
			double paddedMinimumX = getPaddedMinX();
			double stepsX = getStepsX();
			NumberAxis xAxis = new NumberAxis(Math.floor(paddedMinimumX), Math.ceil(paddedMaximumX), stepsX);
			xAxis.setLabel(Double.isNaN(miX) ? locationX + " (no data)" : locationX);
			return xAxis;
		}

		NumberAxis getAxisY() {
			double paddedMaximumY = getPaddedMaxY();
			double paddedMinimumY = getPaddedMinY();
			double stepsY = getStepsY();
			NumberAxis yAxis = new NumberAxis(Math.floor(paddedMinimumY), Math.ceil(paddedMaximumY), stepsY);
			yAxis.setLabel(Double.isNaN(miY) ? locationY + " (no data)" : locationY);

			return yAxis;
		}

		private double getPaddedMinX() {
			double paddedMinimumX = minimumX - (maximumX - miX);
			if (paddedMinimumX < 0) paddedMinimumX = 0;
			return Math.floor(paddedMinimumX);
		}

		private double getPaddedMinY() {
			double paddedMinimumY = minimumY - (maximumY - miY);
			if (paddedMinimumY < 0) paddedMinimumY = 0;
			return Math.floor(paddedMinimumY);
		}

		private double getPaddedMaxX() {
			return Math.ceil(2 * maximumX - miX);
		}

		private double getPaddedMaxY() {
			return Math.ceil(2 * maximumY - miY);
		}

		private double getStepsX() {
			double stepsX = Math.ceil((getPaddedMaxX() - getPaddedMinX()) / series.getData().size());
			if (stepsX <= 1.0) stepsX /= 10; // when range from 1 to 2, show steps by 0.1
			return stepsX;
		}

		private double getStepsY() {
			double stepsY = Math.ceil((getPaddedMaxY() - getPaddedMinY()) / series.getData().size());
			if (stepsY <= 1.0) stepsY /= 10;
			return stepsY;
		}
	}

}

package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.Util;
import at.tugraz.oo2.client.ClientConnection;
import at.tugraz.oo2.client.ui.LineChartHistoryUI;
import at.tugraz.oo2.client.ui.component.DateTimePicker;
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
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class LineChartUI extends AnchorPane {
    private final ClientConnection clientConnection;
    private Map<String, List<String>> sensorMap = new HashMap<>();
    private boolean wasQueryingData = false;
    private Task< List<XYChart.Series>> task;
    private CategoryAxis xAxis = new CategoryAxis();
    private NumberAxis yAxis = new NumberAxis();
    private LineChart<String, Number> lineChart = new LineChart<String, Number>(xAxis,yAxis);
    private List<Sensor> selectedLocations = new ArrayList<>();
    private ObservableList<String> availableMetricsList = FXCollections.observableArrayList();


    @FXML
    private ListView<String> lvSensors;
    @FXML
    private DateTimePicker dpFrom;
    @FXML
    private DateTimePicker dpTo;
    @FXML
    private TextField tfInterval;
    @FXML
    public ComboBox metricComboBox;
    @FXML
    public Button draw;
    @FXML
    public Button someButton;
    @FXML
    private GridPane rightGridPane;
    @FXML
    private GridPane leftGridPane;
    @FXML
    private HBox rightGridHbox;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    public Button btnHistory;

    public LineChartUI(ClientConnection clientConnection) {
        this.clientConnection = clientConnection;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/linechart.fxml"));

        loader.setControllerFactory(c -> this);
        loader.setRoot(this);

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

        lineChart.getData().clear();
        if (!rightGridPane.getChildren().isEmpty()) {
            rightGridHbox.getChildren().remove(lineChart);
        }

        // give date pickers default value from today's midnight
        LocalTime midnight = LocalTime.MIDNIGHT;
        LocalDate today = LocalDate.now();
        LocalDateTime todayMidnight = LocalDateTime.of(today, midnight);
        dpFrom.setValue(todayMidnight);
        dpTo.setValue(todayMidnight);

        // give interval text field default value
        tfInterval.setText("1440");
    }

    private void onConnectionClosed() {
        Platform.runLater(() -> {
            lvSensors.getItems().clear();
            dpTo.setValue(null);
            dpFrom.setValue(null);
            tfInterval.setText("");
            lineChart.getData().clear();
            rightGridHbox.getChildren().remove(lineChart);
            if (wasQueryingData) {
                task.cancel();
                wasQueryingData = false;
                leftGridPane.setDisable(false);
                progressIndicator.setVisible(false);
            }
        });
        sensorMap.clear();
    }

    private void getSensors() {
        ObservableList<String> list = FXCollections.observableArrayList();
        try {
            clientConnection.querySensors().thenAcceptAsync(sensors -> sensors.forEach(sensor -> {
                if (sensorMap.get(sensor.getLocation()) == null) {
                    List<String> metrics = new ArrayList<>();
                    metrics.add(sensor.getMetric());
                    sensorMap.put(sensor.getLocation(), metrics);
                    list.add(sensor.getLocation());
                } else {
                    sensorMap.get(sensor.getLocation()).add(sensor.getMetric());
                }
            })).get();
        } catch (Exception e) {
            Log.ERROR("Something went wrong when fetching sensor list!", false);
        }
        lvSensors.setItems(list);
    }

    @FXML
    public void handleMouseClick(MouseEvent arg0) {
        metricComboBox.setItems(availableMetricsList);
        MultipleSelectionModel<String> selectionModel = lvSensors.getSelectionModel();
        lvSensors.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>();
            cell.textProperty().bind(cell.itemProperty());
            cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                lvSensors.requestFocus();
                if (!cell.isEmpty()) {
                    int index = cell.getIndex();
                    if (selectionModel.getSelectedIndices().contains(index)) {
                        selectionModel.clearSelection(index);
                    } else {
                        selectionModel.select(index);
                    }
                    event.consume();
                }
            });
            return cell;
        });

        if (!lvSensors.getSelectionModel().getSelectedItems().isEmpty()) {
            List<String> metrics = new ArrayList<>(sensorMap.get(lvSensors.getSelectionModel().getSelectedItems().get(0)));
            lvSensors.getSelectionModel().getSelectedItems().forEach((item) -> {
                metrics.retainAll(sensorMap.get(item));
            });
            availableMetricsList.clear();
            availableMetricsList.addAll(metrics);
        }

        metricComboBox.getSelectionModel().selectFirst();
    }


    public void onDrawClick(ActionEvent actionEvent) {

        if (lvSensors.getSelectionModel().getSelectedItems().isEmpty()) {
            showErrorDialog("Please select one or more locations!");
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

        long from = Util.parseUserTime(dpFrom.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss")));
        long to = Util.parseUserTime(dpTo.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss")));
        long interval = Integer.parseUnsignedInt(String.valueOf(Integer.parseInt(tfInterval.getText()) * 60 * 1000));

        if (to - from < interval) {
            showErrorDialog("Interval is bigger than the whole data series! Make sure that \"from\", \"to\" and \"interval\" have correct values.");
            return;
        }

        wasQueryingData = true;

        Platform.runLater(() -> {
            rightGridHbox.getChildren().remove(lineChart);
            progressIndicator.setVisible(true);
            leftGridPane.setDisable(true);
        });

        if (!lineChart.getData().isEmpty()) {
            lineChart.getData().clear();
        }

        selectedLocations.clear();
        lvSensors.getSelectionModel().getSelectedItems().forEach((item) -> {
            Sensor sensor = new Sensor(item, metricComboBox.getSelectionModel().getSelectedItem().toString());
            selectedLocations.add(sensor);
        });

        List<CompletableFuture<XYChart.Series>> listOfCharts = new ArrayList<>();

        selectedLocations.forEach((sensor) -> listOfCharts.add(CompletableFuture.supplyAsync(() -> {
            XYChart.Series values = new XYChart.Series();
            values.setName(sensor.getLocation());
            try {
                clientConnection.queryData(sensor, from, to, interval).get().forEach(dataPoint -> {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");
                    String date = dateFormat.format(new Date(dataPoint.getTime()));
                    values.getData().add(new XYChart.Data(date, dataPoint.getValue()));
                });
            } catch (InterruptedException | ExecutionException e) {
                Log.ERROR("Something went wrong when fetching data series for line chart!", false);
            }

            return values;
        })));

        task = new Task<>() {
            @Override
            protected List<XYChart.Series> call() throws Exception {
                List<XYChart.Series> chartsToBeDrawn = new ArrayList<>();
                listOfCharts.forEach(seriesCompletableFuture -> {
                    try {
                        XYChart.Series value = seriesCompletableFuture.get();
                        if (value.getData().isEmpty())
                            value.setName(value.getName() + " (NOT PRESENT)");
                        chartsToBeDrawn.add(value);
                    } catch (InterruptedException | ExecutionException e) {
                        Log.ERROR("Something went wrong when fetching data series for line chart!", false);
                    }
                });
                return chartsToBeDrawn;
            }
        };

        task.stateProperty().addListener((observableValue, state, t1) -> {
            if (t1 == Worker.State.SUCCEEDED) {
                init(task.getValue());
            }
        });

        new Thread(task).start();
    }

    public void someButtonAction() {
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(new File("bane1.wav")));
            clip.start();
        } catch (Exception ex) {
            Log.ERROR("Error playing audio file on connection opened", false);
        }
    }

    private void init(List<XYChart.Series> seriesList) {
        wasQueryingData = false;
        Platform.runLater(() -> {
            xAxis.setLabel("Time");
            yAxis.setLabel(metricComboBox.getSelectionModel().getSelectedItem().toString());
            lineChart.setTitle("");
            lineChart.setAnimated(false);
            rightGridHbox.getChildren().add(lineChart);
            lineChart.prefWidthProperty().bind(rightGridHbox.widthProperty());
            lineChart.prefHeightProperty().bind(rightGridHbox.heightProperty());
            seriesList.forEach(series -> lineChart.getData().add(series));
            progressIndicator.setVisible(false);
            leftGridPane.setDisable(false);
        });

        // we will only store successful searches
        saveSearchHistory();
    }

    private void saveSearchHistory() {
        if (dpFrom == null || dpTo == null || metricComboBox.getSelectionModel().getSelectedItem().toString().isEmpty() || tfInterval.getText().isEmpty())
            return;

        long beginDate = Util.parseUserTime(dpFrom.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss")));
        long endDate = Util.parseUserTime(dpTo.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss")));
        String metric = metricComboBox.getSelectionModel().getSelectedItem().toString();
        String interval = tfInterval.getText();

        JsonObject jsonObject;
        // read json from file
        try {
            FileReader fr = new FileReader("./lineChartHistory.json");
            StringBuilder content = new StringBuilder();
            int i;
            while ((i=fr.read()) != -1)
                content.append((char) i);

            jsonObject = new Gson().fromJson(content.toString(), JsonObject.class).getAsJsonObject();
            Log.INFO("File lineChartHistory.json found! Using existent values array.", false);
        } catch (IOException e) {
            Log.INFO("File lineChartHistory.json not found! Creating new one.", false);
            jsonObject = new JsonObject();
            jsonObject.addProperty("Created on", Util.TIME_FORMAT.format(Date.from(Instant.now())));
            JsonArray jsonArray = new JsonArray();
            jsonObject.add("values", jsonArray);
        }

        // create json
        JsonArray values = jsonObject.getAsJsonArray("values");
        JsonObject newEntry = new JsonObject();
        newEntry.addProperty("time", Util.TIME_FORMAT.format(Date.from(Instant.now())));
        newEntry.addProperty("beginDate", beginDate);
        newEntry.addProperty("endDate", endDate);
        newEntry.addProperty("interval", interval);
        newEntry.addProperty("metric", metric);

        JsonArray jsonLocations = new JsonArray();
        selectedLocations.forEach(location -> {
            jsonLocations.add(location.getLocation());
        });

        newEntry.add("selectedLocations", jsonLocations);
        values.add(newEntry);

        jsonObject.add("values", values);

        // store to file
        try {
            FileWriter file = new FileWriter("./lineChartHistory.json");
            file.write(jsonObject.toString());
            file.close();
            Log.INFO("Successfully Copied JSON Object to File...", false);
        } catch (IOException e) {
            Log.ERROR("Error copying JSON object to file!", false);
        }
    }

    public void onHistoryClicked(ActionEvent actionEvent) {
        LineChartHistoryUI.LineChartData result = LineChartHistoryUI.display();
        if (!result.getDate().equals(LineChartHistoryUI.getNegativeAnswerConstant().getDate())) {
            reuseData(result);
            LineChartHistoryUI.resetAnswer();
        }
    }

    private void reuseData(LineChartHistoryUI.LineChartData lineChartData) {
        tfInterval.setText(lineChartData.getInterval());
        dpFrom.setValue(LineChartHistoryUI.LineChartData.getDate(lineChartData.getBeginDate()));
        dpTo.setValue(LineChartHistoryUI.LineChartData.getDate(lineChartData.getEndDate()));

        lvSensors.getItems().clear();
        ObservableList<String> list = FXCollections.observableArrayList();
        sensorMap.keySet().forEach(location -> {
            list.add(location);
        });
        lvSensors.setItems(list);

        lineChartData.getLocations().forEach(location -> {
            lvSensors.getItems().forEach(item -> {
                if (item.equals(location)) {
                    lvSensors.getSelectionModel().select(item);
                }
            });
        });

        List<String> metrics = new ArrayList<>(sensorMap.get(lineChartData.getLocations().get(0)));
        lineChartData.getLocations().forEach(location -> metrics.retainAll(sensorMap.get(location)));
        availableMetricsList.clear();
        availableMetricsList.addAll(metrics);

        metricComboBox.setItems(availableMetricsList);
        metricComboBox.getItems().forEach(metric -> {
            if (metric.toString().equals(lineChartData.getMetric())) {
                metricComboBox.getSelectionModel().select(metric);
            }
        });
    }

    @FXML
    public void initialize() {
        lvSensors.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        rightGridHbox.setAlignment(Pos.CENTER);
        rightGridHbox.prefHeightProperty().bind(rightGridPane.heightProperty());
        rightGridHbox.prefWidthProperty().bind(rightGridPane.widthProperty());
        Util.makeTextFieldNumberOnly(tfInterval);
    }

    private void showErrorDialog(String content) {
        Platform.runLater(() -> {
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setHeaderText("Error!");
            errorAlert.setContentText(content);
            errorAlert.showAndWait();
        });
    }

}

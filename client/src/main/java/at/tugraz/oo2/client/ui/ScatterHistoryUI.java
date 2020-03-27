package at.tugraz.oo2.client.ui;

import at.tugraz.oo2.helpers.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Data;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;

public class ScatterHistoryUI {

    private static ScatterChartData answer = getNegativeAnswerConstant();

    public static ScatterChartData getNegativeAnswerConstant() {
        return new ScatterChartData("CLOSED", null, null, null, null, null, null);
    }

    public static void resetAnswer() { answer = getNegativeAnswerConstant(); }

    public static ScatterChartData display() {
        Stage window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        window.setTitle("Line chart search history");

        Button reuseButton = new Button("Reuse");
        Button closeButton = new Button("Close");
        Button deleteButton = new Button("Delete all");
        final TableView<ScatterChartData> tvData = setupTable();

        reuseButton.setOnAction(e -> {
            if (tvData.getSelectionModel().getSelectedItem() != null) {
                answer = tvData.getSelectionModel().getSelectedItem();
                window.close();
            } else {
                Platform.runLater(() -> {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setHeaderText("Error!");
                    errorAlert.setContentText("Please select an item to reuse.");
                    errorAlert.showAndWait();
                });
            }
        });

        closeButton.setOnAction(e -> {
            answer = getNegativeAnswerConstant();
            window.close();
        });

        deleteButton.setOnAction(e -> {
            handleDeleteAll();
            tvData.setItems(null);
        });

        VBox layout = new VBox();
        HBox buttons = new HBox();
        buttons.getChildren().addAll(reuseButton, closeButton, deleteButton);
        buttons.prefWidthProperty().bind(layout.prefWidthProperty());

        reuseButton.setPrefWidth(100);
        closeButton.setPrefWidth(100);
        deleteButton.setPrefWidth(100);
        buttons.setAlignment(Pos.BOTTOM_CENTER);
        buttons.setSpacing(30);

        ObservableList<ScatterChartData> list = getDataFromJsonFile();
        tvData.setItems(list);

        tvData.prefHeightProperty().bind(layout.prefHeightProperty());
        tvData.prefWidthProperty().bind(layout.prefWidthProperty());
        layout.getChildren().addAll(tvData, buttons);
        Scene scene = new Scene(layout, 800, 750);
        layout.prefWidthProperty().bind(scene.widthProperty());
        layout.prefHeightProperty().bind(scene.heightProperty());
        window.setScene(scene);
        window.showAndWait();
        return answer;
    }

    private static TableView<ScatterChartData> setupTable() {
        TableView<ScatterChartData> tvData = new TableView<>();

        TableColumn<ScatterChartData, String> dateColumn = new TableColumn<>("Date");
        TableColumn<ScatterChartData, String> metricColumn = new TableColumn<>("Metric");
        TableColumn<ScatterChartData, String> locationXColumn = new TableColumn<>("Location X");
        TableColumn<ScatterChartData, String> locationYColumn = new TableColumn<>("Location Y");
        TableColumn<ScatterChartData, Double> beginColumn = new TableColumn<>("Begin");
        TableColumn<ScatterChartData, Double> endColumn = new TableColumn<>("End");
        TableColumn<ScatterChartData, String> intervalColumn = new TableColumn<>("Interval");

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        metricColumn.setCellValueFactory(new PropertyValueFactory<>("metric"));
        locationXColumn.setCellValueFactory(new PropertyValueFactory<>("locationX"));
        locationYColumn.setCellValueFactory(new PropertyValueFactory<>("locationY"));
        beginColumn.setCellValueFactory(new PropertyValueFactory<>("beginDate"));
        endColumn.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        intervalColumn.setCellValueFactory(new PropertyValueFactory<>("interval"));
        tvData.getColumns().addAll(dateColumn, metricColumn, locationXColumn, locationYColumn, beginColumn, endColumn, intervalColumn);

        tvData.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        return tvData;
    }

    private static ObservableList<ScatterChartData> getDataFromJsonFile() {
        ObservableList<ScatterChartData> list = FXCollections.observableArrayList();

        JsonObject jsonObject;
        // read json from file
        try {
            FileReader fr = new FileReader("./scatterHistory.json");
            StringBuilder content = new StringBuilder();
            int iterator;
            while ((iterator = fr.read()) != -1)
                content.append((char) iterator);

            jsonObject = new Gson().fromJson(content.toString(), JsonObject.class).getAsJsonObject();
            Log.INFO("File scatterHistory.json found!", false);

            JsonArray values = jsonObject.getAsJsonArray("values");

            values.forEach(jsonElement -> {
                JsonObject obj = jsonElement.getAsJsonObject();
                ScatterChartData scatterChartData = new ScatterChartData(obj.get("time").getAsString(),
                        obj.get("metric").getAsString(),
                        obj.get("locationX").getAsString(),
                        obj.get("locationY").getAsString(),
                        obj.get("beginDate").getAsLong(),
                        obj.get("endDate").getAsLong(),
                        obj.get("interval").getAsString());

                list.add(scatterChartData);
            });

        } catch (IOException e) {
            Log.INFO("File scatterHistory.json not found!", false);
        }

        return list;
    }

    private static void handleDeleteAll() {
        File file = new File("./scatterHistory.json");
        if (file.delete()) {
            Log.INFO("File deleted successfully", false);
        } else {
            Log.INFO("Failed to delete the file", false);
        }
    }

    @Data
    public static class ScatterChartData {
        private final String date;
        private final String metric;
        private final String locationX;
        private final String locationY;
        private final Long beginDate;
        private final Long endDate;
        private final String interval;

        public static LocalDate getDate(Long timestamp) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(Date.from(Instant.ofEpochMilli(timestamp)));
            return LocalDate.of(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
        }
    }

}

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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class LineChartHistoryUI {

    private static LineChartData answer = getNegativeAnswerConstant();

    public static LineChartData getNegativeAnswerConstant() {
        return new LineChartData("CLOSED", null, null, null, null, null);
    }

    public static void resetAnswer() { answer = getNegativeAnswerConstant(); }

    public static LineChartData display() {
        Stage window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        window.setTitle("Line chart search history");

        Button reuseButton = new Button("Reuse");
        Button closeButton = new Button("Close");
        Button deleteButton = new Button("Delete all");
        final TableView<LineChartData> tvData = setupTable();

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

        ObservableList<LineChartData> list = getDataFromJsonFile();
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

    private static TableView<LineChartData> setupTable() {
        TableView<LineChartData> tvData = new TableView<>();

        TableColumn<LineChartData, String> dateColumn = new TableColumn<>("Date");
        TableColumn<LineChartData, String> metricColumn = new TableColumn<>("Metric");
        TableColumn<LineChartData, List<String>> locationColumn = new TableColumn<>("Locations");
        TableColumn<LineChartData, Double> beginColumn = new TableColumn<>("Begin");
        TableColumn<LineChartData, Double> endColumn = new TableColumn<>("End");
        TableColumn<LineChartData, String> intervalColumn = new TableColumn<>("Interval");

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        metricColumn.setCellValueFactory(new PropertyValueFactory<>("metric"));
        locationColumn.setCellValueFactory(new PropertyValueFactory<>("locations"));
        beginColumn.setCellValueFactory(new PropertyValueFactory<>("beginDate"));
        endColumn.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        intervalColumn.setCellValueFactory(new PropertyValueFactory<>("interval"));
        tvData.getColumns().addAll(dateColumn, metricColumn, locationColumn, beginColumn, endColumn, intervalColumn);

        tvData.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        return tvData;
    }

    private static ObservableList<LineChartData> getDataFromJsonFile() {
        ObservableList<LineChartData> list = FXCollections.observableArrayList();

        JsonObject jsonObject;
        // read json from file
        try {
            FileReader fr = new FileReader("./lineChartHistory.json");
            StringBuilder content = new StringBuilder();
            int iterator;
            while ((iterator = fr.read()) != -1)
                content.append((char) iterator);

            jsonObject = new Gson().fromJson(content.toString(), JsonObject.class).getAsJsonObject();
            Log.INFO("File lineChartHistory.json found!", false);

            JsonArray values = jsonObject.getAsJsonArray("values");

            values.forEach(jsonElement -> {
                JsonObject obj = jsonElement.getAsJsonObject();
                List<String> locations = new ArrayList<>();
                obj.getAsJsonArray("selectedLocations").forEach(location -> locations.add(location.getAsString()));
                LineChartData lineChartData = new LineChartData(obj.get("time").getAsString(),
                        obj.get("metric").getAsString(),
                        locations,
                        obj.get("beginDate").getAsLong(),
                        obj.get("endDate").getAsLong(),
                        obj.get("interval").getAsString());

                list.add(lineChartData);
            });

        } catch (IOException e) {
            Log.INFO("File lineChartHistory.json not found!", false);
        }

        return list;
    }

    private static void handleDeleteAll() {
        File file = new File("./lineChartHistory.json");
        if (file.delete()) {
            Log.INFO("File deleted successfully", false);
        } else {
            Log.INFO("Failed to delete the file", false);
        }
    }

    @Data
    public static class LineChartData {
        private final String date;
        private final String metric;
        private final List<String> locations;
        private final Long beginDate;
        private final Long endDate;
        private final String interval;

        public static LocalDateTime getDate(Long timestamp) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(Date.from(Instant.ofEpochMilli(timestamp)));
            LocalTime time = LocalTime.of(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND));
            LocalDate date = LocalDate.of(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
            return LocalDateTime.of(date, time);
        }
    }

}

package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.client.ClientConnection;
import at.tugraz.oo2.client.ui.component.LiveLineChart;
import at.tugraz.oo2.data.LiveData;
import at.tugraz.oo2.data.Sensor;
import at.tugraz.oo2.helpers.Log;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class LiveChartUI extends AnchorPane {

    private final ClientConnection clientConnection;
    private ScheduledFuture<?> service;


    @FXML
    private GridPane gridPane;
    @FXML
    private ProgressIndicator progressBar;
    @FXML
    private HBox progressHbox;

    private LiveLineChart liveLineChart;
    ObservableList<at.tugraz.oo2.data.LiveData> listLive = FXCollections.observableArrayList();

    public LiveChartUI(ClientConnection clientConnection) {
        this.clientConnection = clientConnection;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/livechart.fxml"));
        loader.setRoot(this);
        loader.setControllerFactory(c -> this);

        try {
            loader.load();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        progressHbox.setAlignment(Pos.CENTER);
        liveLineChart = new LiveLineChart();

        clientConnection.addConnectionClosedListener(this::onConnectionClosed);
        clientConnection.addConnectionOpenedListener(this::onConnectionOpened);
    }

    private void onConnectionClosed() {
        service.cancel(true);
        Platform.runLater(() -> {
            gridPane.setVisible(false);
            progressHbox.setVisible(true);
            progressBar.setVisible(false);
            liveLineChart.clearUp();
        });
    }

    private void onConnectionOpened() {
        Platform.runLater(() -> {
            progressHbox.setVisible(true);
            progressBar.setVisible(true);
        });

        ObservableList<at.tugraz.oo2.data.LiveData> list = FXCollections.observableArrayList();

        service = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            clientConnection.getLiveData().thenAccept(liveData -> {
                list.clear();
                listLive.clear();
                list.addAll(liveData);
                liveLineChart.addSeries(list);
            }).thenRunAsync(this::init);
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void init(){
        Platform.runLater(() -> {
            gridPane.getChildren().clear();
            GridPane test = liveLineChart.getGridPane();
            gridPane.getChildren().add(test);
            test.prefHeightProperty().bind(gridPane.heightProperty());
            test.prefWidthProperty().bind(gridPane.widthProperty());

            gridPane.setVisible(true);
            progressHbox.setVisible(false);
            progressBar.setVisible(false);
        });
    }

}

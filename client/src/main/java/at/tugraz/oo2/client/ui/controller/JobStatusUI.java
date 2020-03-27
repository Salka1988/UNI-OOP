package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.client.ClientConnection;
import at.tugraz.oo2.commands.DataObject;
import at.tugraz.oo2.commands.SimJob;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import lombok.Data;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;

public class JobStatusUI extends AnchorPane {
    private final ClientConnection clientConnection;
    private ObservableList<JobStatus> observableList = FXCollections.observableArrayList();

    @FXML
    private TableView<JobStatus> tableView;

    public JobStatusUI(ClientConnection clientConnection) {
        this.clientConnection = clientConnection;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/jobStatusUI.fxml"));

        loader.setControllerFactory(c -> this);
        loader.setRoot(this);

        try {
            loader.load();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        clientConnection.setSimJobAddedHandler(this::onSimJobReceived);
        clientConnection.addConnectionClosedListener(this::onClose);
        clientConnection.addConnectionBrokeListener(this::onClose);
    }

    private void onClose() {
        Platform.runLater(() -> observableList.clear());
    }

    private void onSimJobReceived(DataObject dataObject) {
        SimJob simJob = dataObject.job;

        DateTime dt = new DateTime(simJob.simData.from, DateTimeZone.getDefault());
        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy/MM/dd");
        String from = dtf.print(dt);

        dt = new DateTime(simJob.simData.to, DateTimeZone.getDefault());
        String to = dtf.print(dt);

        Platform.runLater(() -> observableList.add(new JobStatus(
                dataObject.sensor.getLocation(),
                dataObject.sensor.getMetric(),
                simJob.origin,
                from,
                to,
                simJob.simData.maxSize,
                simJob.simData.minSize,
                simJob.result.isEmpty() ? "No results found." : String.valueOf(simJob.result.get(0).getError())
        )));
    }

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            tableView.setPlaceholder(new Label(""));
            TableColumn<JobStatus, String> locationColumn = new TableColumn<>("Location");
            TableColumn<JobStatus, String> metricColumn = new TableColumn<>("Metric");
            TableColumn<JobStatus, Long> originColumn = new TableColumn<>("Origin");
            TableColumn<JobStatus, String> fromColumn = new TableColumn<>("From");
            TableColumn<JobStatus, String> toColumn = new TableColumn<>("To");
            TableColumn<JobStatus, Long> maxSizeColumn = new TableColumn<>("Max. Size");
            TableColumn<JobStatus, Long> minSizeColumn = new TableColumn<>("Min. Size");
            TableColumn<JobStatus, String> lowestErrorColumn = new TableColumn<>("Lowest error");
            tableView.getColumns().addAll(locationColumn, metricColumn, originColumn, fromColumn, toColumn, maxSizeColumn, minSizeColumn, lowestErrorColumn);

            locationColumn.setMinWidth(200);
            metricColumn.setMinWidth(150);
            originColumn.setMinWidth(150);
            fromColumn.setMinWidth(200);
            toColumn.setMinWidth(200);
            maxSizeColumn.setMinWidth(200);
            minSizeColumn.setMinWidth(200);
            lowestErrorColumn.setMinWidth(200);

            locationColumn.setCellValueFactory(new PropertyValueFactory<>("location"));
            metricColumn.setCellValueFactory(new PropertyValueFactory<>("metric"));
            originColumn.setCellValueFactory(new PropertyValueFactory<>("origin"));
            fromColumn.setCellValueFactory(new PropertyValueFactory<>("from"));
            toColumn.setCellValueFactory(new PropertyValueFactory<>("to"));
            maxSizeColumn.setCellValueFactory(new PropertyValueFactory<>("maxSize"));
            minSizeColumn.setCellValueFactory(new PropertyValueFactory<>("minSize"));
            lowestErrorColumn.setCellValueFactory(new PropertyValueFactory<>("lowestError"));
            tableView.setItems(observableList);
        });
    }

    @Data
    public static class JobStatus {
        private final String location;
        private final String metric;
        private final long origin;
        private final String from;
        private final String to;
        private final long maxSize;
        private final long minSize;
        private final String lowestError;
    }
}

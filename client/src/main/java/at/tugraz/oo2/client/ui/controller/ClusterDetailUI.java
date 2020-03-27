package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.data.ClusterDescriptor;
import at.tugraz.oo2.data.DataSeries;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ClusterDetailUI extends AnchorPane {
    private final ClusterDescriptor clusterDescriptor;
    private final ClusterUI.ClusterInfo clusterInfo;

    @FXML
    private ScrollPane scrollPane;
    @FXML
    private Label labelClusterError;
    @FXML
    private Label labelMembers;
    @FXML
    private ProgressIndicator progressIndicator;

    public ClusterDetailUI(ClusterUI.ClusterInfo clusterInfo) {
        this.clusterInfo = clusterInfo;
        this.clusterDescriptor = clusterInfo.getClusterDescriptor();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/clusterDetail.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        setupData();
    }

    private void setupData() {
        Platform.runLater(() -> {
            progressIndicator.setVisible(true);
            scrollPane.setVisible(false);
            GridPane subPane = new GridPane();
            subPane.setVgap(50);
            subPane.setHgap(80);
            subPane.alignmentProperty().setValue(Pos.CENTER);
            subPane.prefWidthProperty().bind(scrollPane.widthProperty());
            int row = 0, column = 0, memberIndex = 1;
            for (DataSeries member : clusterDescriptor.getNonNormalizedMembers()) {
                HBox hBox = new HBox();
                hBox.setPrefWidth(750);
                hBox.setPrefHeight(600);
                XYChart.Series series = new XYChart.Series();
                member.getDataPoints().forEach(dataPoint -> {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");
                    String date = dateFormat.format(new Date(dataPoint.getTime()));
                    series.getData().add(new XYChart.Data(date, dataPoint.getValue()));
                });
                LineChart<String, Number> lineChart = new LineChart<>(new CategoryAxis(), member.getScaledYAxis());
                lineChart.getXAxis().setLabel("Date");
                lineChart.getYAxis().setLabel(clusterInfo.getMetric());
                lineChart.setTitle("Member " + memberIndex++ + " error: " + clusterDescriptor.getErrorOf(member));
                lineChart.setAnimated(false);
                lineChart.getData().add(series);
                lineChart.setLegendVisible(false);
                lineChart.setMinSize(0, 0);
                lineChart.prefHeightProperty().bind(hBox.heightProperty());
                lineChart.prefWidthProperty().bind(hBox.widthProperty());
                hBox.getChildren().add(lineChart);
                subPane.add(hBox, column++, row);
                if (column == 2) row++;
                column %= 2;
            }
            scrollPane.setContent(subPane);
            progressIndicator.setVisible(false);
            scrollPane.setVisible(true);
        });
    }

    @FXML
    public void initialize() {
        labelClusterError.setText(String.valueOf(clusterDescriptor.getClusterError()));
        labelMembers.setText(String.valueOf(clusterDescriptor.getMembers().size()));
    }
}

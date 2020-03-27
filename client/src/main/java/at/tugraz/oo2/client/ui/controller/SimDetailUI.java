package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.data.MatchedCurve;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SimDetailUI extends AnchorPane {
    private final MatchedCurve matchedCurve;

    @FXML
    private HBox hBox;

    public SimDetailUI(MatchedCurve matchedCurve) {
        this.matchedCurve = matchedCurve;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/simdetail.fxml"));
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
        XYChart.Series series = new XYChart.Series();
        matchedCurve.getOriginalSeries().getDataPoints().forEach(dataPoint -> {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");
            String date = dateFormat.format(new Date(dataPoint.getTime()));
            series.getData().add(new XYChart.Data(date, dataPoint.getValue()));
        });
        LineChart<String, Number> lineChart = new LineChart<>(new CategoryAxis(), matchedCurve.getOriginalSeries().getScaledYAxis());
        lineChart.getXAxis().setLabel("Date");
        lineChart.getYAxis().setLabel(matchedCurve.getSensor().getMetric());
        lineChart.setTitle(matchedCurve.getSensor().getLocation() + " with error of " + matchedCurve.getError());
        lineChart.setAnimated(false);
        lineChart.getData().add(series);
        lineChart.setLegendVisible(false);
        lineChart.setMinSize(0, 0);
        lineChart.prefHeightProperty().bind(hBox.heightProperty());
        lineChart.prefWidthProperty().bind(hBox.widthProperty());
        hBox.getChildren().add(lineChart);
    }

    @FXML
    public void initialize() { }
}

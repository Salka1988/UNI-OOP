package at.tugraz.oo2.client.ui.component;

import at.tugraz.oo2.data.LiveData;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.RowConstraints;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class LiveLineChart {

    private boolean alreadyExecuted ;
    private  SimpleDateFormat simpleDateFormat;

    private  GridPane gridPane;

    private  ScrollPane scrollPane;

    private  HashMap<String,HashMap<String,XYChart.Series<String, Number>>> allSensors;

    private  HashMap<String, LineChart<String, Number>> allLineCharts;



    public LiveLineChart(){
        alreadyExecuted = false;
        simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
        gridPane = new GridPane();
        scrollPane = new ScrollPane();
        allSensors = new HashMap<>();
        allLineCharts = new HashMap<>();
    }


    public void addSeries(ObservableList<LiveData> list)
    {
        list.forEach((sensor)->
        {
                if (!allSensors.containsKey(sensor.getMetric())) {
                    Date now = new Date();
                    HashMap<String, XYChart.Series<String, Number>> addSensor = new HashMap<>();
                    XYChart.Series<String, Number> test = new XYChart.Series();
                    test.setName(sensor.getLocation());
                    test.getData().add(new XYChart.Data(simpleDateFormat.format(now), sensor.getData()));
                    addSensor.put(sensor.getLocation(), test);
                    allSensors.put(sensor.getMetric(), addSensor);
                } else {
                    if (!allSensors.get(sensor.getMetric()).containsKey(sensor.getLocation())) {
                        Date now = new Date();
                        XYChart.Series<String, Number> test = new XYChart.Series();
                        test.setName(sensor.getLocation());
                        test.getData().add(new XYChart.Data(simpleDateFormat.format(now), sensor.getData()));
                        allSensors.get(sensor.getMetric()).put(sensor.getLocation(), test);

                    } else {
                        Platform.runLater(() -> {
                            Date now = new Date();
                            allSensors.get(sensor.getMetric()).get(sensor.getLocation()).getData().add(new XYChart.Data(simpleDateFormat.format(now), sensor.getData()));
                            if (allSensors.get(sensor.getMetric()).get(sensor.getLocation()).getData().size() > 30) {
                                allSensors.get(sensor.getMetric()).get(sensor.getLocation()).getData().remove(0);
                            }
                        });
                    }
                }
        });

        if(!alreadyExecuted) {
            makeLineCharts();
            prepareGridPane();
            alreadyExecuted = true;
        }
    }

    public void makeLineCharts()
    {
        for (String metric: allSensors.keySet())
        {
            if(!allLineCharts.containsKey(metric))
            {
                CategoryAxis xAxis = new CategoryAxis();
                xAxis.setLabel("Time");
                NumberAxis yAxis = new NumberAxis();
                yAxis.setLabel(metric);
                LineChart<String, Number> lineChart = new LineChart<String, Number>(xAxis,yAxis);
                lineChart.setTitle(metric.toUpperCase());
                lineChart.setAnimated(false);
                for (String location: allSensors.get(metric).keySet())
                {
                    lineChart.getData().add(allSensors.get(metric).get(location));
                }
                allLineCharts.put(metric,lineChart);
            }
        }
    }


    public void prepareGridPane()
    {
        int row = 0;
            gridPane.getChildren().clear();
            gridPane.getChildren().add(scrollPane);
            scrollPane.prefHeightProperty().bind(gridPane.heightProperty());
            scrollPane.prefWidthProperty().bind(gridPane.widthProperty());
            scrollPane.setFitToWidth(true);

        GridPane subPane = new GridPane();
            for (String location: allLineCharts.keySet())
            {
                HBox hBox = new HBox();

                hBox.getChildren().add(allLineCharts.get(location));

                hBox.prefWidthProperty().bind(scrollPane.widthProperty());
                hBox.prefHeightProperty().bind(scrollPane.heightProperty());
                subPane.add(hBox,0, row);
                row++;

                allLineCharts.get(location).prefWidthProperty().bind(hBox.widthProperty());
                allLineCharts.get(location).prefHeightProperty().bind(hBox.heightProperty());
            }

        scrollPane.setContent(subPane);
    }


    public GridPane getGridPane()
    {
        return this.gridPane;
    }

    public void clearUp() {
        alreadyExecuted = false;
        simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
        gridPane = new GridPane();
        scrollPane = new ScrollPane();
        allSensors = new HashMap<>();
        allLineCharts = new HashMap<>();
    }
}

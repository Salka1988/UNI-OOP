package at.tugraz.oo2.client.ui.component.drawing;

import javafx.event.EventHandler;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

import java.util.ArrayList;
import java.util.Comparator;


public class DrawingTable {

    private CircleMouseHandler circleMouseHandler;
    private LineMouseHandler lineMouseHandler;

    public DrawingTable(final GridPane gridPane) {
        final NumberAxis yAxis = new NumberAxis(0, 30, 0.1);
        final NumberAxis xAxis = new NumberAxis();
        yAxis.setTickUnit(1);
        yAxis.setPrefWidth(35);
        yAxis.setMinorTickCount(10);

        final LineChart<Number, Number> lineChart = new LineChart<Number, Number>(xAxis, yAxis);

        lineChart.setCreateSymbols(false);
        lineChart.setAlternativeRowFillVisible(false);
        lineChart.setLegendVisible(false);

        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(lineChart);
        lineChart.prefWidthProperty().bind(borderPane.widthProperty());
        lineChart.prefHeightProperty().bind(borderPane.heightProperty());
        lineChart.setAnimated(false);

        MouseHandler mouseHandler = new MouseHandler(borderPane);
        borderPane.setOnMouseClicked(mouseHandler);
        borderPane.setOnMouseMoved(mouseHandler);

        gridPane.getChildren().add(borderPane);
        borderPane.prefWidthProperty().bind(gridPane.widthProperty());
        borderPane.prefHeightProperty().bind(gridPane.heightProperty());
        gridPane.setOnMouseDragged(mouseHandler);
        gridPane.setOnMousePressed(mouseHandler);
    }

    public void clearAll() {
        circleMouseHandler.clearAll();
    }


    class MouseHandler implements EventHandler< MouseEvent > {
        private Pane pane;
        Circle newCircle;
        public ArrayList<Circle> circles;
        public ArrayList<Line> lines;

        private double  x1;
        private double  y1;

        public MouseHandler( Pane pane ) {
            this.pane = pane;
            this.circles = new ArrayList<>();
            this.lines = new ArrayList<>();
            circleMouseHandler = new CircleMouseHandler(pane,circles, lines);
            lineMouseHandler = new LineMouseHandler(pane,lines);
        }


        @Override
        public void handle( MouseEvent event ) {
            if( event.getEventType() == MouseEvent.MOUSE_PRESSED && event.isPrimaryButtonDown()) {
                x1 = event.getX();
                y1 = event.getY();
                newCircle = new Circle(x1, y1, 5);
                newCircle.setFill(Color.RED);

                pane.getChildren().forEach(node -> { if(node instanceof Circle) ((Circle)node).setFill(Color.BLACK);});
                pane.getChildren().add(newCircle);

                this.circles.add(newCircle);

                newCircle.setOnMouseEntered(circleMouseHandler);
                newCircle.setOnMouseExited(circleMouseHandler);
                newCircle.setOnMouseDragged(circleMouseHandler);
                newCircle.setOnMousePressed(circleMouseHandler);
                newCircle.setOnMouseClicked(circleMouseHandler);
                newCircle.setOnMouseReleased(circleMouseHandler);

                if (this.circles.size() > 1) {
                    Circle previousCircle = circles.get(this.circles.size() - 2);
                    Line lineBetween = new Line();
                    lineBetween.setStrokeWidth(2);
                    lineBetween.setStroke(Color.WHITE);
                    lineBetween.startXProperty().bind(newCircle.centerXProperty().add(newCircle.translateXProperty()));
                    lineBetween.startYProperty().bind(newCircle.centerYProperty().add(newCircle.translateYProperty()));
                    lineBetween.endXProperty().bind(previousCircle.centerXProperty().add(previousCircle.translateXProperty()));
                    lineBetween.endYProperty().bind(previousCircle.centerYProperty().add(previousCircle.translateYProperty()));

                    pane.getChildren().add(lineBetween);
                    this.lines.add(lineBetween);

                    lineBetween.setOnMouseEntered(lineMouseHandler);
                    lineBetween.setOnMouseExited(lineMouseHandler);
                    lineBetween.setOnMousePressed(lineMouseHandler);

                }
            }
        }
    }

    public double[] circlesValuesReturn() {
        circleMouseHandler.circles.sort(Comparator.comparingDouble(Circle::getCenterX));
        double[] circleValues = new double[circleMouseHandler.circles.size()];
        for (int i = 0; i < this.circleMouseHandler.circles.size(); i++) {
            // -1 * (value - 1000) because the canvas has inverted Y axis
            circleValues[i] = (this.circleMouseHandler.circles.get(i).getCenterY() - 1000) * -1;
        }
        return circleValues;
    }


}
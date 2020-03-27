package at.tugraz.oo2.client.ui.component.drawing;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;

import java.util.ArrayList;


class CircleMouseHandler implements EventHandler< MouseEvent > {
    double x, y;
    Pane pane;
    ArrayList<Circle> circles;
    ArrayList<Line> lines;

    public CircleMouseHandler(Pane pane, ArrayList<Circle> circles, ArrayList<Line> lines) {
        this.pane = pane;
        this.circles = circles;
        this.lines = lines;
    }

    @Override
    public void handle(MouseEvent e) {
        Circle l = (Circle) e.getSource();
        if (e.getEventType() == MouseEvent.MOUSE_PRESSED
                && e.isSecondaryButtonDown()) {

            pane.getChildren().removeIf(node -> node instanceof Line &&
                    (((Line) node).getStartX() == l.getCenterX() || ((Line) node).getStartY() == l.getCenterY() ||
                            ((Line) node).getEndX() == l.getCenterX() || ((Line) node).getEndY() == l.getCenterY()));

            this.lines.clear();
            pane.getChildren().forEach(node -> { if (node instanceof Line) { lines.add((Line) node); }});
            pane.getChildren().remove(l);
            circles.remove(l);
            pane.getChildren().forEach(node -> { if(node instanceof Circle) ((Circle)node).setFill(Color.WHITE);});
            if (circles.size() >= 1) circles.get(circles.size() - 1).setFill(Color.RED);

        } else if (e.getEventType() == MouseEvent.MOUSE_DRAGGED
                && e.isPrimaryButtonDown()) {
            double tx = e.getX();
            double ty = e.getY();
            double deltaX = tx - x;
            double deltaY = ty - y;
            l.setCenterX(l.getCenterX() + deltaX);
            l.setCenterY(l.getCenterY() + deltaY);
            x = tx;
            y = ty;
            intersectionCheck(l);

        } else if (e.getEventType() == MouseEvent.MOUSE_ENTERED) {
            x = e.getX();
            y = e.getY();
            l.setStroke(Color.RED);
        } else if (e.getEventType() == MouseEvent.MOUSE_EXITED) {
            l.setStroke(Color.WHITE);
        }
        else if (e.getEventType() == MouseEvent.MOUSE_PRESSED)
        {
            pane.getChildren().forEach(node -> { if(node instanceof Circle) ((Circle)node).setFill(Color.BLACK);});
            l.setFill(Color.RED);
        }
        e.consume();
    }

    public void clearAll() {
        lines.forEach(line -> pane.getChildren().remove(line));
        lines.clear();
        circles.forEach(circle -> pane.getChildren().remove(circle));
        circles.clear();
    }

    private void intersectionCheck(Circle block) {
        boolean collisionDetected = false;
        for (Node node : pane.getChildren()) {
            if (node instanceof Circle) {
                if (node != block) {
                    Shape collision = Shape.intersect(block,(Circle)node);
                    if (collision.getBoundsInLocal().getWidth() != -1) collisionDetected = true;
                }
            }
            if (collisionDetected) block.setFill(Color.GREEN);
            else block.setFill(Color.RED);
        }

    }



}

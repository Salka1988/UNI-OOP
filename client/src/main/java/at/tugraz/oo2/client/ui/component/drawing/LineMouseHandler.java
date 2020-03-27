package at.tugraz.oo2.client.ui.component.drawing;

import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import java.util.ArrayList;

class LineMouseHandler implements EventHandler< MouseEvent > {
    double x, y;
    Pane pane;
    ArrayList<Line> lines;

    public LineMouseHandler(Pane pane, ArrayList<Line> lines) {
        this.pane = pane;
        this.lines = lines;
    }

    @Override
    public void handle(MouseEvent e) {
        Line line = (Line) e.getSource();
        if (e.getEventType() == MouseEvent.MOUSE_PRESSED
                && e.isSecondaryButtonDown()) {
            pane.getChildren().remove(line);
            this.lines.remove(line);

        } else if (e.getEventType() == MouseEvent.MOUSE_ENTERED) {
            x = e.getX();
            y = e.getY();
            line.setStroke(Color.RED);
        } else if (e.getEventType() == MouseEvent.MOUSE_EXITED) {
            line.setStroke(Color.WHITE);
        }
        e.consume();
    }
}

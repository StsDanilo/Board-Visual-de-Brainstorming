package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.transform.Rotate;

/**
 * Seta entre dois cards.
 *
 * Toda a geometria é derivada por binding das propriedades dos cards no
 * modelo: quando um card se move, a seta se recalcula sozinha. Nenhum código
 * de arrastar precisa saber que setas existem.
 */
public class ConnectionView extends Group {

    private static final double ARROW_LENGTH = 12;
    private static final double ARROW_HALF_WIDTH = 6;

    private final Connection connection;

    public ConnectionView(Connection connection) {
        this.connection = connection;
        getStyleClass().add("connection");
        setPickOnBounds(false);

        Card source = connection.getSource();
        Card target = connection.getTarget();
        Observable[] deps = CardGeometry.dependencies(source, target);

        // Pontos nas bordas dos cards, na reta que liga os dois centros.
        ObjectBinding<Point2D> start = Bindings.createObjectBinding(
                () -> CardGeometry.borderPoint(source, target.getCenterX(), target.getCenterY()), deps);
        ObjectBinding<Point2D> tip = Bindings.createObjectBinding(
                () -> CardGeometry.borderPoint(target, source.getCenterX(), source.getCenterY()), deps);
        DoubleBinding angle = Bindings.createDoubleBinding(() -> {
            Point2D a = start.get();
            Point2D b = tip.get();
            return Math.toDegrees(Math.atan2(b.getY() - a.getY(), b.getX() - a.getX()));
        }, start, tip);

        Line line = new Line();
        line.getStyleClass().add("connection-line");
        line.startXProperty().bind(Bindings.createDoubleBinding(() -> start.get().getX(), start));
        line.startYProperty().bind(Bindings.createDoubleBinding(() -> start.get().getY(), start));
        // A linha termina na base da ponta da seta, para não "vazar" na frente dela.
        line.endXProperty().bind(Bindings.createDoubleBinding(
                () -> tip.get().getX() - ARROW_LENGTH * Math.cos(Math.toRadians(angle.get())), tip, angle));
        line.endYProperty().bind(Bindings.createDoubleBinding(
                () -> tip.get().getY() - ARROW_LENGTH * Math.sin(Math.toRadians(angle.get())), tip, angle));

        // Triângulo desenhado apontando para +x com a ponta na origem;
        // é movido até a borda do destino e girado conforme o ângulo.
        Polygon arrowHead = new Polygon(0, 0, -ARROW_LENGTH, -ARROW_HALF_WIDTH, -ARROW_LENGTH, ARROW_HALF_WIDTH);
        arrowHead.getStyleClass().add("connection-arrow");
        arrowHead.translateXProperty().bind(Bindings.createDoubleBinding(() -> tip.get().getX(), tip));
        arrowHead.translateYProperty().bind(Bindings.createDoubleBinding(() -> tip.get().getY(), tip));
        Rotate rotate = new Rotate();
        rotate.angleProperty().bind(angle);
        arrowHead.getTransforms().add(rotate);

        getChildren().addAll(line, arrowHead);
    }

    public Connection getConnection() {
        return connection;
    }
}

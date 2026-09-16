package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import javafx.beans.Observable;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Cálculos geométricos sobre o contorno de um card, usados pelas setas. */
final class CardGeometry {

    private CardGeometry() {
    }

    /**
     * Ponto onde a reta que sai do centro do card em direção a
     * ({@code towardX}, {@code towardY}) cruza a borda do card.
     */
    static Point2D borderPoint(Card card, double towardX, double towardY) {
        double cx = card.getCenterX();
        double cy = card.getCenterY();
        double dx = towardX - cx;
        double dy = towardY - cy;
        if (dx == 0 && dy == 0) {
            return new Point2D(cx, cy);
        }
        // Novos formatos (elipse, losango) ganham seu próprio cálculo aqui.
        double t = switch (card.getShape()) {
            case RECTANGLE -> rectangleScale(card.getWidth() / 2, card.getHeight() / 2, dx, dy);
        };
        return new Point2D(cx + dx * t, cy + dy * t);
    }

    private static double rectangleScale(double halfWidth, double halfHeight, double dx, double dy) {
        double sx = dx == 0 ? Double.POSITIVE_INFINITY : halfWidth / Math.abs(dx);
        double sy = dy == 0 ? Double.POSITIVE_INFINITY : halfHeight / Math.abs(dy);
        return Math.min(sx, sy);
    }

    /** Propriedades que, ao mudar, alteram a geometria dos cards informados. */
    static Observable[] dependencies(Card... cards) {
        List<Observable> deps = new ArrayList<>();
        for (Card card : cards) {
            forEach(card, deps::add);
        }
        return deps.toArray(new Observable[0]);
    }

    /** Aplica a ação a cada propriedade que define a geometria do card. */
    static void forEach(Card card, Consumer<Observable> action) {
        action.accept(card.xProperty());
        action.accept(card.yProperty());
        action.accept(card.widthProperty());
        action.accept(card.heightProperty());
        action.accept(card.shapeProperty());
    }
}

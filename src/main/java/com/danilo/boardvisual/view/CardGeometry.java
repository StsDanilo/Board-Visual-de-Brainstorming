package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import javafx.beans.Observable;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Cálculos geométricos sobre o contorno de cada formato de card. */
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
        // t: quanto andar na direção (dx, dy) a partir do centro até tocar a borda.
        double a = card.getWidth() / 2;
        double b = card.getHeight() / 2;
        double t = switch (card.getShape()) {
            case RECTANGLE -> Math.min(
                    dx == 0 ? Double.POSITIVE_INFINITY : a / Math.abs(dx),
                    dy == 0 ? Double.POSITIVE_INFINITY : b / Math.abs(dy));
            // (x/a)² + (y/b)² = 1
            case ELLIPSE -> 1 / Math.sqrt((dx / a) * (dx / a) + (dy / b) * (dy / b));
            // |x|/a + |y|/b = 1
            case DIAMOND -> 1 / (Math.abs(dx) / a + Math.abs(dy) / b);
        };
        return new Point2D(cx + dx * t, cy + dy * t);
    }

    /** Tamanho (diâmetro) do botão que abre o painel flutuante. */
    static final double PANEL_BUTTON_SIZE = 24;

    /**
     * Espaçamento interno para o texto caber dentro do formato: no retângulo é
     * uma margem fixa; na elipse e no losango, o maior retângulo inscrito.
     * Em painéis flutuantes, a parte de baixo reserva espaço para o botão.
     */
    static Insets contentInsets(CardShape shape, double width, double height, boolean panel) {
        Insets base = switch (shape) {
            case RECTANGLE -> new Insets(12, 10, 10, 10);
            // Retângulo inscrito na elipse: lados a·√2 e b·√2.
            case ELLIPSE -> symmetric(width * (1 - Math.sqrt(0.5)) / 2 + 4, height * (1 - Math.sqrt(0.5)) / 2 + 2);
            // Retângulo inscrito no losango: metade da largura e da altura.
            case DIAMOND -> symmetric(width / 4 + 2, height / 4);
        };
        if (!panel) {
            return base;
        }
        double buttonTop = panelButtonCenter(shape, width, height).getY() - PANEL_BUTTON_SIZE / 2;
        double bottom = Math.max(base.getBottom(), height - buttonTop + 4);
        return new Insets(base.getTop(), base.getRight(), bottom, base.getLeft());
    }

    /**
     * Centro do botão do painel: embaixo, no meio, sempre dentro da silhueta
     * (no losango, a ponta de baixo é estreita, então ele sobe um pouco mais).
     */
    static Point2D panelButtonCenter(CardShape shape, double width, double height) {
        double fromBottom = switch (shape) {
            case RECTANGLE -> 18;
            case ELLIPSE -> 20;
            case DIAMOND -> 26;
        };
        return new Point2D(width / 2, height - fromBottom);
    }

    private static Insets symmetric(double horizontal, double vertical) {
        return new Insets(vertical, horizontal, vertical, horizontal);
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

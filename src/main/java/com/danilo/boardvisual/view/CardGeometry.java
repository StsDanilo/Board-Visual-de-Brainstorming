package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.ConnectionGeometry;
import com.danilo.boardvisual.model.Frames;
import javafx.beans.Observable;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cálculos geométricos sobre o contorno de cada formato de card.
 *
 * As contas são Java puro ({@link Padding}, {@link #panelButtonPoint}); os
 * métodos que devolvem {@code Insets}/{@code Point2D} só embrulham o resultado
 * para a tela. Quem desenha sem JavaFX (o SVG) usa as versões puras.
 */
public final class CardGeometry {

    /**
     * Silhueta da elipse: o mesmo {@code -fx-shape} de {@code .card.shape-ellipse}
     * no app.css, desenhado num quadrado 100×100 e escalado ao tamanho do card.
     * O {@code CssMirrorTest} confere que os dois continuam iguais.
     */
    public static final String ELLIPSE_PATH = "M 0 50 A 50 50 0 1 1 100 50 A 50 50 0 1 1 0 50 Z";
    /** Silhueta do losango ({@code .card.shape-diamond} no app.css), no quadrado 100×100. */
    public static final String DIAMOND_PATH = "M 50 0 L 100 50 L 50 100 L 0 50 Z";

    /** Recuo do texto em cada lado (Java puro; a tela usa o {@code Insets} equivalente). */
    public record Padding(double top, double right, double bottom, double left) {
        Insets toInsets() {
            return new Insets(top, right, bottom, left);
        }
    }

    private CardGeometry() {
    }

    /**
     * Ponto onde a reta que sai do centro do card em direção a
     * ({@code towardX}, {@code towardY}) cruza a borda do card.
     */
    static Point2D borderPoint(Card card, double towardX, double towardY) {
        // A conta fica no modelo (ConnectionGeometry): o PDF também precisa dela.
        ConnectionGeometry.Point p = ConnectionGeometry.borderPoint(card, towardX, towardY);
        return new Point2D(p.x(), p.y());
    }

    /** Tamanho (diâmetro) do botão que abre o painel flutuante. */
    static final double PANEL_BUTTON_SIZE = 24;

    /**
     * Espaçamento interno para o texto caber dentro do formato: no retângulo é
     * uma margem fixa; na elipse e no losango, o maior retângulo inscrito.
     * Em painéis flutuantes, a parte de baixo reserva espaço para o botão.
     */
    static Insets contentInsets(CardShape shape, double width, double height, boolean panel) {
        return contentInsets(shape, width, height, panel, 0);
    }

    /** Espaço entre a imagem e a legenda, num card de imagem. */
    static final double IMAGE_CAPTION_GAP = 6;
    /** A imagem de um card fica dentro da borda dele ({@code .card}: 1 px). */
    public static final double IMAGE_INSET = 1;
    /** Raio dos cantos da imagem: o do card (8) menos a borda. */
    public static final double IMAGE_CORNER = 7;

    /**
     * Como {@link #contentInsets(CardShape, double, double, boolean)}, num card
     * com imagem de altura {@code imageHeight} no topo: o texto (legenda) fica
     * abaixo dela.
     */
    static Insets contentInsets(CardShape shape, double width, double height, boolean panel, double imageHeight) {
        return contentInsets(shape, width, height, panel, imageHeight, 0);
    }

    /**
     * Como acima, num card com {@code markers} marcadores na borda de cima
     * ({@link CardMarkers}): o texto começa abaixo da fileira deles. Num card de
     * imagem os marcadores ficam sobre a imagem, então quase nunca mudam nada.
     */
    static Insets contentInsets(CardShape shape, double width, double height, boolean panel, double imageHeight,
                                int markers) {
        return contentPadding(shape, width, height, panel, imageHeight, markers).toInsets();
    }

    /** O mesmo que {@link #contentInsets(CardShape, double, double, boolean, double, int)}, em Java puro. */
    public static Padding contentPadding(CardShape shape, double width, double height, boolean panel,
                                         double imageHeight, int markers) {
        if (shape == CardShape.FRAME) {
            return frameTitleInsets(width, height, markers);
        }
        Padding insets = shapeInsets(shape, width, height, panel);
        double top = insets.top();
        if (imageHeight > 0) {
            top = Math.max(top, imageHeight + IMAGE_CAPTION_GAP);
        }
        top = Math.max(top, CardMarkers.reservedTop(shape, width, height, markers));
        if (top == insets.top()) {
            return insets;
        }
        return new Padding(top, insets.right(), insets.bottom(), insets.left());
    }

    /** Recuo do título da seção à esquerda: livra o cadeado do canto (card travado). */
    static final double FRAME_TITLE_LEFT = 16;
    /** Recuo do título da seção à direita, sem marcadores. */
    static final double FRAME_TITLE_RIGHT = 12;

    /**
     * Seção (frame): o texto é o título, numa faixa de altura fixa no topo
     * ({@link Frames#TITLE_HEIGHT}); o resto da seção fica livre para os cards.
     * Os marcadores ficam na mesma faixa, à direita, e o título termina antes deles.
     */
    private static Padding frameTitleInsets(double width, double height, int markers) {
        double right = FRAME_TITLE_RIGHT;
        if (markers > 0) {
            right = CardMarkers.RIGHT + markers * CardMarkers.SIZE + (markers - 1) * CardMarkers.SPACING + 6;
        }
        double bottom = Math.max(0, height - Frames.TITLE_HEIGHT);
        return new Padding(6, right, bottom, FRAME_TITLE_LEFT);
    }

    private static Padding shapeInsets(CardShape shape, double width, double height, boolean panel) {
        Padding base = switch (shape) {
            case RECTANGLE, FRAME -> new Padding(12, 10, 10, 10);
            // Retângulo inscrito na elipse: lados a·√2 e b·√2.
            case ELLIPSE -> symmetric(width * (1 - Math.sqrt(0.5)) / 2 + 4, height * (1 - Math.sqrt(0.5)) / 2 + 2);
            // Retângulo inscrito no losango: metade da largura e da altura.
            case DIAMOND -> symmetric(width / 4 + 2, height / 4);
        };
        if (!panel) {
            return base;
        }
        double buttonTop = panelButtonPoint(shape, width, height).y() - PANEL_BUTTON_SIZE / 2;
        double bottom = Math.max(base.bottom(), height - buttonTop + 4);
        return new Padding(base.top(), base.right(), bottom, base.left());
    }

    /**
     * Centro do botão do painel: embaixo, no meio, sempre dentro da silhueta
     * (no losango, a ponta de baixo é estreita, então ele sobe um pouco mais).
     */
    static Point2D panelButtonCenter(CardShape shape, double width, double height) {
        ConnectionGeometry.Point p = panelButtonPoint(shape, width, height);
        return new Point2D(p.x(), p.y());
    }

    /** O mesmo que {@link #panelButtonCenter}, em Java puro (coordenadas do card). */
    public static ConnectionGeometry.Point panelButtonPoint(CardShape shape, double width, double height) {
        double fromBottom = switch (shape) {
            case RECTANGLE, FRAME -> 18;
            case ELLIPSE -> 20;
            case DIAMOND -> 26;
        };
        return new ConnectionGeometry.Point(width / 2, height - fromBottom);
    }

    private static Padding symmetric(double horizontal, double vertical) {
        return new Padding(vertical, horizontal, vertical, horizontal);
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

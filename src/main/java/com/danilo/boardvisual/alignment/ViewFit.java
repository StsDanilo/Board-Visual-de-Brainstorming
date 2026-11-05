package com.danilo.boardvisual.alignment;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.ConnectionGeometry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Zoom para caber tudo" (Shift+1) e "zoom para caber a seleção" (Shift+2): a conta
 * do pan e do zoom que mostram um conjunto de caixas de uma vez, com margem. Sem
 * JavaFX e sem tela (testável): quem aplica o resultado é o {@code BoardView}.
 *
 * <b>A conta.</b> A área útil é a tela menos as margens ({@link Margins}). O zoom é o
 * maior que faz a união das caixas caber nela (nos dois eixos), limitado a
 * {@code [minZoom, maxZoom]}; o pan põe o centro da união no centro da área útil.
 * Por isso, um conteúdo minúsculo não passa de {@code maxZoom}, e um conteúdo tão
 * grande que nem em {@code minZoom} cabe fica <b>centralizado</b> (o recorte é igual dos
 * dois lados; {@link Limit#MIN_ZOOM} avisa quem chamou). Sem caixas (board vazio),
 * o resultado é o de "Redefinir visualização": 100% e pan zero.
 *
 * <b>O que conta.</b> A caixa de cada card (seções e imagens são cards) e, de cada seta
 * com texto, uma caixa estimada do texto ({@link #labelBox}): marcadores de link e o
 * cadeado não contam (cabem na folga da margem).
 */
public final class ViewFit {

    /** Largura máxima do texto da seta (a mesma do {@code ConnectionView}); passou disso, termina em "…". */
    static final double LABEL_MAX_WIDTH = 220;
    /** Estimativa do texto da seta (fonte de 12 px, recuo de 5 px dos lados): largura média por caractere. */
    static final double LABEL_CHAR_WIDTH = 6.5;
    static final double LABEL_PADDING_X = 10;
    static final double LABEL_HEIGHT = 20;

    private ViewFit() {
    }

    /** Folgas entre o conteúdo e as bordas da tela, em pixels de tela. */
    public record Margins(double top, double right, double bottom, double left) {
        public static final Margins NONE = new Margins(0, 0, 0, 0);

        public static Margins uniform(double margin) {
            return new Margins(margin, margin, margin, margin);
        }
    }

    /** O que limitou o zoom do resultado. */
    public enum Limit {
        /** O conteúdo coube com folga dentro dos limites de zoom. */
        NONE,
        /** Um conteúdo pequeno: o zoom parou no máximo (poderia ampliar mais). */
        MAX_ZOOM,
        /** Um conteúdo grande demais: nem no zoom mínimo ele cabe (fica centralizado, cortado dos lados). */
        MIN_ZOOM
    }

    /** Pan (em pixels de tela) e zoom que mostram o conteúdo. */
    public record Result(double panX, double panY, double zoom, Limit limit) {
    }

    /** 100%, sem deslocamento: o mesmo que "Redefinir visualização". */
    public static Result reset() {
        return new Result(0, 0, 1, Limit.NONE);
    }

    /**
     * O pan e o zoom que mostram todas as caixas (em coordenadas do board) numa tela
     * de {@code viewWidth} x {@code viewHeight} pixels.
     *
     * @param boxes   caixas a mostrar; vazia = {@link #reset()}
     * @param margins folgas da tela; se juntas passam de metade de um eixo, encolhem para caber
     */
    public static Result fit(List<Box> boxes, double viewWidth, double viewHeight, Margins margins,
                             double minZoom, double maxZoom) {
        if (boxes.isEmpty()) {
            return reset();
        }
        Box union = boxes.get(0);
        for (int i = 1; i < boxes.size(); i++) {
            union = union.union(boxes.get(i));
        }
        double left = margins.left();
        double right = margins.right();
        double top = margins.top();
        double bottom = margins.bottom();
        double horizontal = left + right;
        if (horizontal > viewWidth / 2) {
            double factor = horizontal == 0 ? 0 : viewWidth / 2 / horizontal;
            left *= factor;
            right *= factor;
        }
        double vertical = top + bottom;
        if (vertical > viewHeight / 2) {
            double factor = vertical == 0 ? 0 : viewHeight / 2 / vertical;
            top *= factor;
            bottom *= factor;
        }
        double availableWidth = Math.max(1, viewWidth - left - right);
        double availableHeight = Math.max(1, viewHeight - top - bottom);

        double wanted = Math.min(
                union.width() <= 0 ? Double.POSITIVE_INFINITY : availableWidth / union.width(),
                union.height() <= 0 ? Double.POSITIVE_INFINITY : availableHeight / union.height());
        double zoom = Math.max(minZoom, Math.min(maxZoom, wanted));
        Limit limit = wanted < minZoom ? Limit.MIN_ZOOM : wanted > maxZoom ? Limit.MAX_ZOOM : Limit.NONE;

        double centerX = left + availableWidth / 2;
        double centerY = top + availableHeight / 2;
        double panX = centerX - (union.x() + union.width() / 2) * zoom;
        double panY = centerY - (union.y() + union.height() / 2) * zoom;
        return new Result(panX, panY, zoom, limit);
    }

    // ------------------------------------------------------------------ caixas

    /** As caixas de todos os cards do board e dos textos das setas dele. */
    public static List<Box> boxesOf(Board board) {
        return boxesOf(board, board.getCards());
    }

    /**
     * As caixas dos cards informados (os que não estão no board são ignorados) e dos
     * textos das setas que ligam dois deles (as setas só existem entre cards do mesmo board).
     */
    public static List<Box> boxesOf(Board board, Collection<Card> cards) {
        Set<Card> wanted = new HashSet<>(cards);
        List<Box> boxes = new ArrayList<>();
        for (Card card : board.getCards()) {
            if (wanted.contains(card)) {
                boxes.add(new Box(card.getX(), card.getY(), card.getWidth(), card.getHeight()));
            }
        }
        for (Connection connection : board.getConnections()) {
            if (connection.hasLabel() && wanted.contains(connection.getSource())
                    && wanted.contains(connection.getTarget()) && board.getCards().contains(connection.getSource())
                    && board.getCards().contains(connection.getTarget())) {
                boxes.add(labelBox(connection, board.hasReverse(connection)));
            }
        }
        return boxes;
    }

    /**
     * Caixa estimada do texto da seta (o tamanho real depende da fonte, que o modelo
     * não conhece): uma linha, até {@link #LABEL_MAX_WIDTH}, centrada onde a tela a
     * desenha ({@link ConnectionGeometry#labelCenter}).
     */
    public static Box labelBox(Connection connection, boolean reverse) {
        double width = Math.min(LABEL_MAX_WIDTH, LABEL_PADDING_X + LABEL_CHAR_WIDTH * connection.getLabel().length());
        ConnectionGeometry.Point center = ConnectionGeometry.labelCenter(connection, width, LABEL_HEIGHT, reverse);
        return new Box(center.x() - width / 2, center.y() - LABEL_HEIGHT / 2, width, LABEL_HEIGHT);
    }
}

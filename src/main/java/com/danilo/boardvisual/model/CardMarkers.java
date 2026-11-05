package com.danilo.boardvisual.model;

import javafx.beans.Observable;

import java.util.ArrayList;
import java.util.List;

/**
 * Marcadores na borda de cima de um card: botõezinhos redondos que indicam
 * algo a mais no card e agem ao clicar (o link externo: clipe; o link para
 * outro board: seta curva).
 *
 * Fica no modelo, em Java puro, porque três lugares precisam da mesma conta:
 * a tela ({@code CardView}), o espaço do texto ({@code CardGeometry} e
 * {@code CardTextFit}) e o PDF (área clicável do marcador). Um card pode ter
 * vários marcadores, lado a lado, na ordem de {@link Kind}.
 *
 * Posição (coordenadas do card, origem no canto de cima à esquerda):
 * <ul>
 *   <li>retângulo (e card de imagem): canto de cima à direita, por dentro,
 *       afastado da alça de redimensionar do canto;</li>
 *   <li>elipse e losango: no meio, em cima, descendo o suficiente para ficar
 *       inteiro dentro da silhueta (espelho do botão do painel, que fica embaixo).</li>
 * </ul>
 * O texto do card começa abaixo da fileira ({@link #reservedTop}).
 */
public final class CardMarkers {

    /** Tipos de marcador, na ordem em que aparecem (da esquerda para a direita). */
    public enum Kind {
        /** Endereço web ({@link Card#getLink()}): clipe; clicar abre no navegador. */
        LINK,
        /**
         * Atalho para outro board da árvore ({@link Card#getBoardLink()}): seta
         * curva; clicar vai ao board de destino. Aparece mesmo com o destino
         * excluído (aí "quebrado", ver {@code CardView.setMarkerBroken}).
         */
        BOARD_LINK
    }

    /** Lado do marcador (é um círculo). */
    public static final double SIZE = 18;
    /** Espaço entre dois marcadores. */
    public static final double SPACING = 4;
    /** Distância mínima até a borda de cima. */
    static final double TOP = 6;
    /**
     * Distância até a borda direita no retângulo: a alça de redimensionar do
     * canto ocupa 8,5 px para dentro (17 px de área clicável centrada na quina).
     */
    static final double RIGHT = 9;
    /** Folga dentro da silhueta em elipse e losango. */
    private static final double SHAPE_MARGIN = 2;
    /** Espaço entre a fileira de marcadores e o texto. */
    static final double TEXT_GAP = 3;

    /** Um marcador posicionado (canto de cima à esquerda e lado), em coordenadas do card. */
    public record Spot(Kind kind, double x, double y, double size) {
        public double centerX() {
            return x + size / 2;
        }

        public double centerY() {
            return y + size / 2;
        }
    }

    private CardMarkers() {
    }

    /** Marcadores que o card mostra, na ordem. */
    public static List<Kind> of(Card card) {
        List<Kind> kinds = new ArrayList<>();
        if (card.hasLink()) {
            kinds.add(Kind.LINK);
        }
        if (card.hasBoardLink()) {
            kinds.add(Kind.BOARD_LINK);
        }
        return kinds;
    }

    /** Propriedades do card que decidem quais marcadores aparecem (para quem precisa reagir a elas). */
    public static Observable[] dependencies(Card card) {
        return new Observable[] {card.linkProperty(), card.boardLinkProperty()};
    }

    /** Onde fica cada marcador de {@code kinds} num card desse formato e tamanho. */
    public static List<Spot> layout(CardShape shape, double width, double height, List<Kind> kinds) {
        if (kinds.isEmpty()) {
            return List.of();
        }
        int count = kinds.size();
        double rowWidth = count * SIZE + (count - 1) * SPACING;
        double a = width / 2;
        double b = height / 2;
        double half = rowWidth / 2 + SHAPE_MARGIN;
        // Switch exaustivo: um formato novo (5.2 do DOCUMENTACAO.md) não compila sem decidir aqui.
        double y = switch (shape) {
            case RECTANGLE -> TOP;
            // (x/a)² + (y/b)² = 1: desce até a meia largura da elipse cobrir metade da fileira.
            case ELLIPSE -> {
                double ratio = a <= 0 ? 1 : Math.min(1, half / a);
                yield Math.max(TOP, b * (1 - Math.sqrt(1 - ratio * ratio)) + 1);
            }
            // |x|/a + |y|/b = 1: a meia largura cresce em linha reta a partir da ponta de cima.
            case DIAMOND -> Math.max(TOP, (a <= 0 ? b : b * half / a) + 1);
        };
        double x0 = switch (shape) {
            case RECTANGLE -> width - RIGHT - rowWidth;
            case ELLIPSE, DIAMOND -> (width - rowWidth) / 2;
        };
        // Card minúsculo: nunca passa do meio do card.
        y = Math.max(0, Math.min(y, height / 2 - SIZE / 2));
        x0 = Math.max(0, x0);
        List<Spot> spots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            spots.add(new Spot(kinds.get(i), x0 + i * (SIZE + SPACING), y, SIZE));
        }
        return spots;
    }

    /** Marcadores de um card, já posicionados. */
    public static List<Spot> layout(Card card) {
        return layout(card.getShape(), card.getWidth(), card.getHeight(), of(card));
    }

    /**
     * Altura no topo do card que o texto deve deixar livre por causa dos
     * marcadores (0 sem marcadores).
     */
    public static double reservedTop(CardShape shape, double width, double height, int count) {
        if (count <= 0) {
            return 0;
        }
        List<Kind> kinds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            kinds.add(Kind.LINK); // só a quantidade importa para a posição
        }
        Spot first = layout(shape, width, height, kinds).get(0);
        return first.y() + first.size() + TEXT_GAP;
    }
}

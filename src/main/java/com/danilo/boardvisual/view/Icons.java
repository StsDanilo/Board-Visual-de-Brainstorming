package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.ConnectionStyle;
import com.danilo.boardvisual.model.PanelMode;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

/**
 * Ícones de linha (paths SVG em área 24x24), estilizados pela classe {@code tool-icon}.
 *
 * Os paths que aparecem nos cards (marcadores e botão do card) são públicos:
 * a exportação SVG desenha os mesmos ícones.
 */
public final class Icons {

    static final String SELECT = "M6 3 L6 19 L10.5 15 L13.5 21.5 L16 20.3 L13 14 L19 14 Z";
    static final String CONNECTION = "M5 19 L18 6 M11 6 H18 V13";
    static final String DUPLICATE = "M9 9 H20 V20 H9 Z M5 15 V4 H15";
    static final String DELETE = "M4 7 H20 M9.5 7 V4.5 H14.5 V7 M6.5 7 L7.5 20 H16.5 L17.5 7 M10.5 11 V16 M13.5 11 V16";
    /** Card com uma seta saindo: "abre algo". */
    static final String PANEL = "M2.5 6.5 H14.5 V17.5 H2.5 Z M17 8.5 L20.5 12 L17 15.5";
    public static final String PANEL_TEXT = "M5 7 H19 M5 11 H19 M5 15 H19 M5 19 H13";
    public static final String PANEL_LIST = "M4.5 7 H5.5 M9 7 H19 M4.5 12 H5.5 M9 12 H19 M4.5 17 H5.5 M9 17 H19";
    static final String CLOSE = "M7 7 L17 17 M17 7 L7 17";
    static final String CHEVRON_UP = "M6.5 14.5 L12 9 L17.5 14.5";
    static final String CHEVRON_DOWN = "M6.5 9.5 L12 15 L17.5 9.5";
    static final String ADD = "M12 6 V18 M6 12 H18";
    /** Dois cards empilhados: "board dentro de board". */
    static final String NESTED_BOARD = "M7.5 4 H20 V15.5 M4 7.5 H16.5 V20 H4 Z";
    /** Seta para entrar (no botão do card). */
    public static final String ENTER = "M5 12 H18 M13 7 L18 12 L13 17";
    static final String BACK = "M19 12 H6 M11 7 L6 12 L11 17";
    /** Moldura com montanha e sol: "imagem". */
    static final String IMAGE = "M3.5 5.5 H20.5 V18.5 H3.5 Z M3.5 15.5 L8.5 10.5 L14 16 M12 14 L15 11 L20.5 16.5 "
            + "M14 8.5 A1.5 1.5 0 1 0 17 8.5 A1.5 1.5 0 1 0 14 8.5 Z";

    /**
     * Clipe de papel em pé (três voltas): o símbolo de "link". {@link #link()}
     * o inclina 45°, como costuma aparecer.
     */
    public static final String LINK = "M10 8 V15.5 A2 2 0 0 0 14 15.5 V6 A3 3 0 0 0 8 6 V17 A4 4 0 0 0 16 17 V9";

    /**
     * Seta curva (sobe e vira para a direita): "atalho para outro lugar". É o
     * ícone do link para outro board, diferente do clipe do link externo.
     */
    public static final String BOARD_LINK = "M5 20 V14.5 A6.5 6.5 0 0 1 11.5 8 H19.5 M15.5 4 L19.5 8 L15.5 12";
    /** Cadeado fechado: "travado". */
    static final String LOCK = "M8 11 V8 A4 4 0 0 1 16 8 V11 M5.5 11 H18.5 V20.5 H5.5 Z M12 14.5 V17";
    /** Cadeado aberto (a alça solta de um lado): "destravado". */
    static final String UNLOCK = "M8 11 V8 A4 4 0 0 1 15.9 7.2 M5.5 11 H18.5 V20.5 H5.5 Z M12 14.5 V17";

    /** Três camadas empilhadas: "ordem de empilhamento". */
    static final String LAYERS = "M12 4 L20 8.5 L12 13 L4 8.5 Z M4 12.5 L12 17 L20 12.5 M4 16.5 L12 21 L20 16.5";

    /** Traço em diagonal por cima de um ícone: "não existe mais". */
    public static final String STRIKE = " M3.5 3.5 L20.5 20.5";

    private Icons() {
    }

    /** Ícone de link (clipe inclinado), no tamanho dos ícones das barras. */
    static SVGPath link() {
        SVGPath icon = create(LINK);
        icon.setRotate(45);
        return icon;
    }

    /** Ícone do link para outro board (seta curva), no tamanho dos ícones das barras. */
    static SVGPath boardLink() {
        return create(BOARD_LINK);
    }

    /** Ícone de cada marcador de card (switch exaustivo: marcador novo precisa de ícone). */
    static SVGPath marker(CardMarkers.Kind kind) {
        return marker(kind, false);
    }

    /**
     * @param broken o destino do marcador não existe mais: o ícone vem riscado
     *               (só o link para outro board pode ficar assim)
     */
    static SVGPath marker(CardMarkers.Kind kind, boolean broken) {
        SVGPath icon = switch (kind) {
            case LINK -> link();
            case BOARD_LINK -> create(broken ? BOARD_LINK + STRIKE : BOARD_LINK);
        };
        // O marcador tem 18 px: o ícone de 24 px encolhe para caber.
        icon.setScaleX(0.62);
        icon.setScaleY(0.62);
        return icon;
    }

    static String shape(CardShape shape) {
        return switch (shape) {
            case RECTANGLE -> "M3.5 6.5 H20.5 V17.5 H3.5 Z";
            case ELLIPSE -> "M2.5 12 A9.5 6.5 0 1 0 21.5 12 A9.5 6.5 0 1 0 2.5 12 Z";
            case DIAMOND -> "M12 3 L21.5 12 L12 21 L2.5 12 Z";
            // Moldura com a faixa do título em cima: "seção".
            case FRAME -> "M3.5 4.5 H20.5 V19.5 H3.5 Z M3.5 9 H20.5 M6.5 6.75 H12";
        };
    }

    /** Ícone do modo do painel ({@code null} para card comum). */
    public static String panelMode(PanelMode mode) {
        return switch (mode) {
            case TEXT -> PANEL_TEXT;
            case LIST -> PANEL_LIST;
            case NONE -> null;
        };
    }

    /** Linha diagonal de baixo-esquerda a cima-direita: o corpo dos ícones de seta. */
    private static final String STYLE_LINE = "M5 19 L19 5";
    /** Ponta em cima à direita (a mesma do ícone {@link #CONNECTION}). */
    private static final String STYLE_END_TIP = "M11.5 5 H19 V12.5";
    /** Ponta embaixo à esquerda: a mesma, girada 180°. */
    private static final String STYLE_START_TIP = "M12.5 19 H5 V11.5";

    /**
     * Ícone de cada estilo de seta, desenhado com o próprio estilo (tracejada,
     * ponta dupla, sem ponta). Switch exaustivo: estilo novo precisa de ícone.
     * É um grupo: a linha tracejada leva o tracejado, as pontas ficam cheias.
     */
    static Group connectionStyle(ConnectionStyle style) {
        SVGPath line = create(STYLE_LINE);
        Group icon = new Group(line);
        switch (style) {
            case ARROW -> icon.getChildren().add(create(STYLE_END_TIP));
            case DASHED -> {
                line.getStyleClass().add("dashed");
                icon.getChildren().add(create(STYLE_END_TIP));
            }
            case DOUBLE -> icon.getChildren().addAll(create(STYLE_END_TIP), create(STYLE_START_TIP));
            case LINE -> {
                // só a linha
            }
        }
        // Mesma área de 24x24 em todos (sem isso, a linha sem ponta fica menor que as outras).
        Rectangle frame = new Rectangle(0, 0, 24, 24);
        frame.setFill(Color.TRANSPARENT);
        frame.setMouseTransparent(true);
        icon.getChildren().add(0, frame);
        icon.getStyleClass().add("connection-style-icon");
        return icon;
    }

    static SVGPath create(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("tool-icon");
        return icon;
    }
}

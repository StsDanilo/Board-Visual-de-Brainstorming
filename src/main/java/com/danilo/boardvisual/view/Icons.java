package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.PanelMode;
import javafx.scene.shape.SVGPath;

/** Ícones de linha (paths SVG em área 24x24), estilizados pela classe {@code tool-icon}. */
final class Icons {

    static final String SELECT = "M6 3 L6 19 L10.5 15 L13.5 21.5 L16 20.3 L13 14 L19 14 Z";
    static final String CONNECTION = "M5 19 L18 6 M11 6 H18 V13";
    static final String DUPLICATE = "M9 9 H20 V20 H9 Z M5 15 V4 H15";
    static final String DELETE = "M4 7 H20 M9.5 7 V4.5 H14.5 V7 M6.5 7 L7.5 20 H16.5 L17.5 7 M10.5 11 V16 M13.5 11 V16";
    /** Card com uma seta saindo: "abre algo". */
    static final String PANEL = "M2.5 6.5 H14.5 V17.5 H2.5 Z M17 8.5 L20.5 12 L17 15.5";
    static final String PANEL_TEXT = "M5 7 H19 M5 11 H19 M5 15 H19 M5 19 H13";
    static final String PANEL_LIST = "M4.5 7 H5.5 M9 7 H19 M4.5 12 H5.5 M9 12 H19 M4.5 17 H5.5 M9 17 H19";
    static final String CLOSE = "M7 7 L17 17 M17 7 L7 17";
    static final String ADD = "M12 6 V18 M6 12 H18";
    /** Dois cards empilhados: "board dentro de board". */
    static final String NESTED_BOARD = "M7.5 4 H20 V15.5 M4 7.5 H16.5 V20 H4 Z";
    /** Seta para entrar (no botão do card). */
    static final String ENTER = "M5 12 H18 M13 7 L18 12 L13 17";
    static final String BACK = "M19 12 H6 M11 7 L6 12 L11 17";

    private Icons() {
    }

    static String shape(CardShape shape) {
        return switch (shape) {
            case RECTANGLE -> "M3.5 6.5 H20.5 V17.5 H3.5 Z";
            case ELLIPSE -> "M2.5 12 A9.5 6.5 0 1 0 21.5 12 A9.5 6.5 0 1 0 2.5 12 Z";
            case DIAMOND -> "M12 3 L21.5 12 L12 21 L2.5 12 Z";
        };
    }

    /** Ícone do modo do painel ({@code null} para card comum). */
    static String panelMode(PanelMode mode) {
        return switch (mode) {
            case TEXT -> PANEL_TEXT;
            case LIST -> PANEL_LIST;
            case NONE -> null;
        };
    }

    static SVGPath create(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("tool-icon");
        return icon;
    }
}

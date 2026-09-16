package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.CardShape;
import javafx.scene.shape.SVGPath;

/** Ícones de linha (paths SVG em área 24x24), estilizados pela classe {@code tool-icon}. */
final class Icons {

    static final String SELECT = "M6 3 L6 19 L10.5 15 L13.5 21.5 L16 20.3 L13 14 L19 14 Z";
    static final String CONNECTION = "M5 19 L18 6 M11 6 H18 V13";
    static final String DUPLICATE = "M9 9 H20 V20 H9 Z M5 15 V4 H15";
    static final String DELETE = "M4 7 H20 M9.5 7 V4.5 H14.5 V7 M6.5 7 L7.5 20 H16.5 L17.5 7 M10.5 11 V16 M13.5 11 V16";

    private Icons() {
    }

    static String shape(CardShape shape) {
        return switch (shape) {
            case RECTANGLE -> "M3.5 6.5 H20.5 V17.5 H3.5 Z";
            case ELLIPSE -> "M2.5 12 A9.5 6.5 0 1 0 21.5 12 A9.5 6.5 0 1 0 2.5 12 Z";
            case DIAMOND -> "M12 3 L21.5 12 L12 21 L2.5 12 Z";
        };
    }

    static SVGPath create(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("tool-icon");
        return icon;
    }
}

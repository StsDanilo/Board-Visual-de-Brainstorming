package com.danilo.boardvisual.view;

import javafx.scene.shape.SVGPath;

/** Ícones de linha (paths SVG em área 24x24), estilizados pela classe {@code tool-icon}. */
final class Icons {

    static final String DUPLICATE = "M9 9 H20 V20 H9 Z M5 15 V4 H15";
    static final String DELETE = "M4 7 H20 M9.5 7 V4.5 H14.5 V7 M6.5 7 L7.5 20 H16.5 L17.5 7 M10.5 11 V16 M13.5 11 V16";

    private Icons() {
    }

    static SVGPath create(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("tool-icon");
        return icon;
    }
}

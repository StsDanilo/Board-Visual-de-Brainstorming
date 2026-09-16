package com.danilo.boardvisual.view;

import javafx.css.PseudoClass;

/**
 * Ferramentas da barra lateral. A ferramenta ativa define o que o mouse faz
 * no canvas; cada controller consulta a ferramenta antes de agir.
 *
 * Nova ferramenta = nova constante aqui + o comportamento no controller
 * correspondente. A barra e a dica da barra de status se ajustam sozinhas.
 */
public enum Tool {

    SELECT("Selecionar",
            "Clique para selecionar  ·  Arraste o card para mover  ·  Arraste o fundo para navegar  ·  Roda do mouse: zoom",
            "M6 3 L6 19 L10.5 15 L13.5 21.5 L16 20.3 L13 14 L19 14 Z"),
    CARD("Card",
            "Clique no canvas para criar um card",
            "M4 5 H20 V19 H4 Z M8 10 H16 M8 14 H13"),
    CONNECTION("Conexão",
            "Arraste de um card até outro para conectá-los",
            "M5 19 L18 6 M11 6 H18 V13");

    private final String label;
    private final String hint;
    private final String iconPath;
    private final PseudoClass pseudoClass;

    Tool(String label, String hint, String iconPath) {
        this.label = label;
        this.hint = hint;
        this.iconPath = iconPath;
        this.pseudoClass = PseudoClass.getPseudoClass("tool-" + name().toLowerCase());
    }

    public String getLabel() {
        return label;
    }

    /** Texto de ajuda mostrado na barra de status enquanto a ferramenta está ativa. */
    public String getHint() {
        return hint;
    }

    /** Ícone como path SVG em uma área de 24x24. */
    String getIconPath() {
        return iconPath;
    }

    /** Pseudo-classe aplicada ao canvas (ex.: {@code :tool-card}) para o CSS mudar cursores. */
    PseudoClass getPseudoClass() {
        return pseudoClass;
    }
}

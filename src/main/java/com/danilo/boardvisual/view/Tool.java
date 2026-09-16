package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.CardShape;
import javafx.css.PseudoClass;
import javafx.scene.input.KeyCode;

/**
 * Ferramentas da barra lateral. A ferramenta ativa define o que o mouse faz
 * no canvas; cada controller consulta a ferramenta antes de agir.
 *
 * Nova ferramenta = nova constante aqui + o comportamento no controller
 * correspondente. A barra, a dica da barra de status e o atalho de teclado
 * se ajustam sozinhos.
 */
public enum Tool {

    SELECT("Selecionar", KeyCode.V, null, Icons.SELECT,
            "Clique para selecionar  ·  Arraste o card para mover  ·  Arraste o fundo para navegar  ·  Roda do mouse: zoom"),
    RECTANGLE("Retângulo", KeyCode.R, CardShape.RECTANGLE, Icons.shape(CardShape.RECTANGLE),
            "Clique no canvas para criar um card retangular"),
    ELLIPSE("Elipse", KeyCode.E, CardShape.ELLIPSE, Icons.shape(CardShape.ELLIPSE),
            "Clique no canvas para criar um card em elipse"),
    DIAMOND("Losango", KeyCode.L, CardShape.DIAMOND, Icons.shape(CardShape.DIAMOND),
            "Clique no canvas para criar um card em losango"),
    CONNECTION("Conexão", KeyCode.C, null, Icons.CONNECTION,
            "Arraste de um card até outro para conectá-los");

    private final String label;
    private final KeyCode shortcut;
    private final CardShape shape;
    private final String iconPath;
    private final String hint;
    private final PseudoClass pseudoClass;

    Tool(String label, KeyCode shortcut, CardShape shape, String iconPath, String hint) {
        this.label = label;
        this.shortcut = shortcut;
        this.shape = shape;
        this.iconPath = iconPath;
        this.hint = hint;
        this.pseudoClass = PseudoClass.getPseudoClass("tool-" + name().toLowerCase());
    }

    public String getLabel() {
        return label;
    }

    /** Tecla que ativa a ferramenta (sem modificadores). */
    public KeyCode getShortcut() {
        return shortcut;
    }

    /** Formato do card criado por esta ferramenta, ou {@code null} se ela não cria cards. */
    public CardShape getShape() {
        return shape;
    }

    /** Texto de ajuda mostrado na barra de status enquanto a ferramenta está ativa. */
    public String getHint() {
        return hint;
    }

    /** Ícone como path SVG em uma área de 24x24. */
    String getIconPath() {
        return iconPath;
    }

    /** Pseudo-classe aplicada ao canvas (ex.: {@code :tool-connection}) para o CSS mudar cursores. */
    PseudoClass getPseudoClass() {
        return pseudoClass;
    }
}

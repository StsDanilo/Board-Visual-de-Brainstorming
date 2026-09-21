package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.PanelMode;
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

    SELECT("Selecionar", KeyCode.V, null, PanelMode.NONE, Icons.SELECT,
            "Clique para selecionar (Shift soma)  ·  Arraste o fundo para selecionar vários  ·  "
                    + "Espaço+arrastar ou botão direito: mover a visão  ·  Roda do mouse: zoom"),
    RECTANGLE("Retângulo", KeyCode.R, CardShape.RECTANGLE, PanelMode.NONE, Icons.shape(CardShape.RECTANGLE),
            "Clique no canvas para criar um card retangular"),
    ELLIPSE("Elipse", KeyCode.E, CardShape.ELLIPSE, PanelMode.NONE, Icons.shape(CardShape.ELLIPSE),
            "Clique no canvas para criar um card em elipse"),
    DIAMOND("Losango", KeyCode.L, CardShape.DIAMOND, PanelMode.NONE, Icons.shape(CardShape.DIAMOND),
            "Clique no canvas para criar um card em losango"),
    PANEL("Painel flutuante", KeyCode.P, CardShape.RECTANGLE, PanelMode.TEXT, Icons.PANEL,
            "Clique no canvas para criar um painel flutuante  ·  O botão no card abre o painel  ·  "
                    + "Texto ou lista: escolha na barra do card selecionado"),
    NESTED_BOARD("Board aninhado", KeyCode.B, CardShape.RECTANGLE, PanelMode.NONE, true, Icons.NESTED_BOARD,
            "Clique no canvas para criar um board aninhado  ·  O botão no card entra no board  ·  "
                    + "Alt+← ou o caminho no topo voltam"),
    CONNECTION("Conexão", KeyCode.C, null, PanelMode.NONE, Icons.CONNECTION,
            "Arraste de um card até outro para conectá-los");

    private final String label;
    private final KeyCode shortcut;
    private final CardShape shape;
    private final PanelMode panelMode;
    private final boolean createsChildBoard;
    private final String iconPath;
    private final String hint;
    private final PseudoClass pseudoClass;

    Tool(String label, KeyCode shortcut, CardShape shape, PanelMode panelMode, String iconPath, String hint) {
        this(label, shortcut, shape, panelMode, false, iconPath, hint);
    }

    Tool(String label, KeyCode shortcut, CardShape shape, PanelMode panelMode, boolean createsChildBoard,
         String iconPath, String hint) {
        this.createsChildBoard = createsChildBoard;
        this.label = label;
        this.shortcut = shortcut;
        this.shape = shape;
        this.panelMode = panelMode;
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

    /** Modo do painel do card criado ({@link PanelMode#NONE} para card comum). */
    public PanelMode getPanelMode() {
        return panelMode;
    }

    /** Se o card criado contém um board filho (board aninhado). */
    public boolean createsChildBoard() {
        return createsChildBoard;
    }

    /** Ferramentas que criam cards comuns de um formato (as opções do seletor de formato). */
    boolean isPlainShapeTool() {
        return shape != null && panelMode == PanelMode.NONE && !createsChildBoard;
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

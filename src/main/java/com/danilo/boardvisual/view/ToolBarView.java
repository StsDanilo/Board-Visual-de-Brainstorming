package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.ConnectionStyle;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Barra de ferramentas vertical, flutuando sobre o canvas.
 *
 * - Ferramentas: a fonte da verdade é {@link #activeToolProperty()}; clicar
 *   num botão muda a propriedade, e mudar a propriedade por código (ex.:
 *   voltar para "Selecionar" depois de criar um card) atualiza os botões.
 * - Estilo das setas: com o mouse sobre a ferramenta Conexão, uma mini barra
 *   flutuante à direita mostra os outros estilos ({@link #setUpConnectionStyles});
 *   o ícone da ferramenta mostra o estilo das setas novas ({@link #connectionStyleProperty()}).
 * - Cor: botão que abre a paleta. A cor escolhida vira a cor atual (usada
 *   nos próximos cards) e é repassada a {@link #setOnColorPicked}, para o
 *   controller aplicar também na seleção.
 */
public class ToolBarView extends VBox {

    private final ObjectProperty<Tool> activeTool = new SimpleObjectProperty<>(this, "activeTool", Tool.SELECT);
    private final ToggleGroup group = new ToggleGroup();
    private final Map<Tool, ToggleButton> buttons = new EnumMap<>(Tool.class);

    private final ReadOnlyStringWrapper currentColor = new ReadOnlyStringWrapper(this, "currentColor", Card.DEFAULT_COLOR);
    private final ColorPickerButton colorButton = new ColorPickerButton("Cor (novos cards e seleção)", Side.RIGHT);
    private Consumer<String> onColorPicked = color -> { };

    private final ConnectionStyleOrder styleOrder = new ConnectionStyleOrder();
    private final List<Button> styleButtons = new ArrayList<>();
    private final ReadOnlyObjectWrapper<ConnectionStyle> connectionStyle =
            new ReadOnlyObjectWrapper<>(this, "connectionStyle", ConnectionStyle.DEFAULT);
    private final BooleanProperty connectionSelected = new SimpleBooleanProperty(this, "connectionSelected");
    private Predicate<ConnectionStyle> onConnectionStylePicked = style -> false;
    /** Mini barra flutuante com os outros estilos de seta (ver {@link #setUpConnectionStyles}). */
    private final HBox styleBar = new HBox();
    private final PauseTransition styleBarCloser = new PauseTransition(STYLE_BAR_CLOSE_DELAY);

    public ToolBarView() {
        getStyleClass().add("tool-bar-view");
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        for (Tool tool : Tool.values()) {
            ToggleButton button = createToolButton(tool);
            buttons.put(tool, button);
            getChildren().add(button);
        }
        setUpConnectionStyles(buttons.get(Tool.CONNECTION));

        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                // Clicar na ferramenta já ativa não deve deixar a barra sem nenhuma.
                oldToggle.setSelected(true);
            } else {
                activeTool.set((Tool) newToggle.getUserData());
            }
        });
        activeTool.addListener((obs, old, tool) -> buttons.get(tool).setSelected(true));
        buttons.get(activeTool.get()).setSelected(true);

        getChildren().addAll(new Separator(), createColorButton());
    }

    private ToggleButton createToolButton(Tool tool) {
        ToggleButton button = new ToggleButton();
        button.getStyleClass().add("tool-button");
        button.setGraphic(Icons.create(tool.getIconPath()));
        button.setTooltip(new Tooltip(tool.getLabel() + "  (" + tool.getShortcut().getName() + ")"));
        button.setUserData(tool);
        button.setToggleGroup(group);
        // Não roubar o foco do texto de um card ao trocar de ferramenta.
        button.setFocusTraversable(false);
        return button;
    }

    // ------------------------------------------------------ estilos de seta

    /**
     * Os estilos de seta da ferramenta Conexão. O ícone da ferramenta mostra o
     * estilo principal (o das setas novas); os outros estilos ficam numa
     * <b>mini barra flutuante</b> à direita da barra de ferramentas
     * ({@link #getConnectionStyleBar()}), cada um desenhado no próprio estilo.
     * Ela aparece só enquanto o mouse está sobre o botão da Conexão, e continua
     * aberta enquanto o mouse está nela (para dar tempo de chegar até ela, só
     * fecha {@link #STYLE_BAR_CLOSE_DELAY} depois de o mouse sair dos dois).
     * Clicar num estilo da mini barra troca de lugar com o principal
     * ({@link ConnectionStyleOrder}) e:
     * <ul>
     * <li>sem seta selecionada, ativa a ferramenta Conexão;</li>
     * <li>com seta selecionada, aplica o estilo nela ({@link #setOnConnectionStylePicked})
     *     e a ferramenta não muda. Nesse caso o ícone principal também aplica o
     *     estilo dele na seta, em vez de ativar a ferramenta (a tecla C continua ativando).</li>
     * </ul>
     */
    private void setUpConnectionStyles(ToggleButton connectionButton) {
        HBox extras = styleBar;
        extras.getStyleClass().addAll("tool-bar-view", "connection-style-bar");
        extras.setManaged(false); // fica fora do layout de quem a contém: posição calculada (placeStyleBar)
        extras.setVisible(false);
        // Pega o mouse só no fundo desenhado: pelo retângulo, a sombra (-fx-effect) também contaria
        // e cobriria a borda da barra de ferramentas.
        extras.setPickOnBounds(false);
        for (int i = 0; i < styleOrder.extras().size(); i++) {
            Button button = new Button();
            button.getStyleClass().addAll("tool-button", "connection-style-button");
            button.setFocusTraversable(false);
            button.setOnAction(e -> {
                ConnectionStyle style = (ConnectionStyle) button.getUserData();
                chooseConnectionStyle(style);
                if (!(connectionSelected.get() && onConnectionStylePicked.test(style))) {
                    activeTool.set(Tool.CONNECTION);
                }
            });
            styleButtons.add(button);
            extras.getChildren().add(button);
        }
        // Com seta selecionada, o ícone principal aplica o estilo dele na seta
        // (o filtro consome o clique antes de o botão "armar" e ativar a ferramenta).
        connectionButton.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && connectionSelected.get()
                    && onConnectionStylePicked.test(styleOrder.primary())) {
                e.consume();
            }
        });

        // Abre com o mouse sobre o botão da Conexão; fecha um pouco depois de o
        // mouse sair do botão e da mini barra (o caminho entre os dois passa pela
        // borda da barra e por um vão, onde nenhum dos dois está com o mouse).
        styleBarCloser.setOnFinished(e -> {
            if (!connectionButton.isHover() && !extras.isHover()) {
                extras.setVisible(false);
            }
        });
        InvalidationListener hoverWatcher = obs -> {
            if (connectionButton.isHover()) {
                styleBarCloser.stop();
                showStyleBar();
            } else if (extras.isHover()) {
                styleBarCloser.stop();
            } else if (extras.isVisible()) {
                styleBarCloser.playFromStart();
            }
        };
        connectionButton.hoverProperty().addListener(hoverWatcher);
        extras.hoverProperty().addListener(hoverWatcher);
        // A barra muda de tamanho/lugar (janela redimensionada): a mini barra acompanha.
        boundsInParentProperty().addListener(obs -> placeStyleBar());
        refreshConnectionStyleButtons();
    }

    /** Tempo que a mini barra de estilos espera, depois de o mouse sair, para fechar. */
    static final Duration STYLE_BAR_CLOSE_DELAY = Duration.millis(350);
    /** Espaço entre a barra de ferramentas e a mini barra de estilos. */
    private static final double STYLE_BAR_GAP = 6;

    private void showStyleBar() {
        if (!styleBar.isVisible()) {
            styleBar.setVisible(true);
            placeStyleBar();
        }
    }

    /**
     * Põe a mini barra logo à direita da barra de ferramentas, centrada na
     * altura do botão da Conexão. As duas precisam estar no mesmo pai (a área
     * do canvas, montada pela {@code MainWindow}).
     */
    private void placeStyleBar() {
        if (!styleBar.isVisible() || styleBar.getParent() == null || getParent() != styleBar.getParent()) {
            return;
        }
        ToggleButton button = buttons.get(Tool.CONNECTION);
        styleBar.applyCss();
        styleBar.autosize();
        Bounds buttonBounds = styleBar.getParent().sceneToLocal(
                button.localToScene(button.getBoundsInLocal()));
        // Pela caixa de layout, não pelo boundsInParent: este inclui a sombra da barra (-fx-effect).
        double x = localToParent(getLayoutBounds()).getMaxX() + STYLE_BAR_GAP;
        double y = buttonBounds.getCenterY() - styleBar.getHeight() / 2;
        styleBar.relocate(Math.round(x), Math.round(y));
    }

    /**
     * A mini barra flutuante com os outros estilos de seta. Quem monta a janela
     * a põe no mesmo pai da barra de ferramentas, por cima do canvas; ela se
     * posiciona sozinha.
     */
    public HBox getConnectionStyleBar() {
        return styleBar;
    }

    /** Ícones e dicas conforme a ordem atual dos estilos. */
    private void refreshConnectionStyleButtons() {
        ToggleButton main = buttons.get(Tool.CONNECTION);
        ConnectionStyle primary = styleOrder.primary();
        main.setGraphic(Icons.connectionStyle(primary));
        main.getTooltip().setText(Tool.CONNECTION.getLabel() + ": " + primary.getLabel().toLowerCase()
                + "  (" + Tool.CONNECTION.getShortcut().getName() + ")");
        List<ConnectionStyle> extras = styleOrder.extras();
        for (int i = 0; i < styleButtons.size(); i++) {
            Button button = styleButtons.get(i);
            ConnectionStyle style = extras.get(i);
            button.setUserData(style);
            button.setGraphic(Icons.connectionStyle(style));
            button.setTooltip(new Tooltip(style.getLabel() + " (passa a ser o padrão das setas novas)"));
        }
        connectionStyle.set(primary);
    }

    /**
     * Põe o estilo no lugar principal (troca de lugar com o atual): as setas
     * novas, pela ferramenta Conexão ou pela alça do card, nascem nele.
     */
    public void chooseConnectionStyle(ConnectionStyle style) {
        if (styleOrder.choose(style)) {
            refreshConnectionStyleButtons();
        }
    }

    /**
     * Se há uma seta selecionada no canvas (quem liga a barra ao canvas
     * mantém isto em dia). Com seta selecionada, os botões de estilo mudam a
     * seta em vez de ativar a ferramenta.
     */
    public BooleanProperty connectionSelectedProperty() {
        return connectionSelected;
    }

    /**
     * Chamado quando o usuário escolhe um estilo com uma seta selecionada;
     * devolve se aplicou o estilo na seta (aí a ferramenta não muda).
     */
    public void setOnConnectionStylePicked(Predicate<ConnectionStyle> handler) {
        onConnectionStylePicked = handler == null ? style -> false : handler;
    }

    /** Estilo das setas novas (o do ícone da ferramenta Conexão). */
    public ReadOnlyObjectProperty<ConnectionStyle> connectionStyleProperty() {
        return connectionStyle.getReadOnlyProperty();
    }

    /** Ordem atual dos estilos: a posição 0 é a do ícone principal. */
    public List<ConnectionStyle> getConnectionStyleOrder() {
        return styleOrder.order();
    }

    /** Os botões extras, na ordem em que aparecem (cada um guarda o estilo em {@code getUserData()}). */
    public List<Button> getConnectionStyleButtons() {
        return List.copyOf(styleButtons);
    }

    /** O botão da ferramenta (usado pelos roteiros de teste de interface). */
    public ToggleButton getToolButton(Tool tool) {
        return buttons.get(tool);
    }

    private ColorPickerButton createColorButton() {
        colorButton.setColor(currentColor.get());
        currentColor.addListener((obs, old, color) -> colorButton.setColor(color));
        colorButton.setOnColorPicked(color -> {
            currentColor.set(color);
            onColorPicked.accept(color);
        });
        return colorButton;
    }

    public ObjectProperty<Tool> activeToolProperty() {
        return activeTool;
    }

    public Tool getActiveTool() {
        return activeTool.get();
    }

    public void setActiveTool(Tool tool) {
        activeTool.set(tool);
    }

    /** Cor usada nos próximos cards criados. */
    public ReadOnlyStringProperty currentColorProperty() {
        return currentColor.getReadOnlyProperty();
    }

    /** Chamado quando o usuário escolhe uma cor na paleta da barra. */
    public void setOnColorPicked(Consumer<String> handler) {
        onColorPicked = handler == null ? color -> { } : handler;
    }
}

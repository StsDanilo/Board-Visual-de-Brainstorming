package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardSlides;
import com.danilo.boardvisual.view.BoardView;
import com.danilo.boardvisual.view.BoardView.ViewState;
import com.danilo.boardvisual.view.BreadcrumbView;
import com.danilo.boardvisual.view.CardView;
import com.danilo.boardvisual.view.FloatingPanelView;
import com.danilo.boardvisual.view.MainWindow;
import com.danilo.boardvisual.view.Tool;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.event.EventTarget;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.util.Map;
import java.util.Optional;

/**
 * Modo apresentação (F5 ou Exibir › Apresentar): o board em tela cheia, sem barras nem menu,
 * navegando entre os boards como slides. Só ver, navegar e clicar; esc sai.
 *
 * <h3>O que a apresentação liga</h3>
 * {@link MainWindow#presentingProperty()} (uma propriedade só): ligada por aqui, ela esconde o menu,
 * a barra de ferramentas, a barra de status e a mini barra de estilos de seta, deixa o caminho do
 * board discreto (CSS {@code :presenting}) e põe o {@link BoardView} em modo leitura
 * ({@link BoardView#setPresenting}: nada se seleciona, sem barra da seleção nem alças, textos só
 * de leitura). Ao entrar, a janela vai à tela cheia (com a dica e a combinação de saída padrão do
 * JavaFX desligadas: o Esc é daqui), o board da tela é enquadrado com o zoom para caber tudo
 * ({@link BoardView#fitAllForPresentation}) e o histórico é congelado ({@link EditHistory#setFrozen}).
 *
 * <h3>Como a edição fica bloqueada (várias barreiras)</h3>
 * <ol>
 *   <li><b>Filtro de eventos na janela</b> (o principal): enquanto a apresentação está ligada, só
 *       passam os eventos de navegação (a lista está em {@link #onKey}, {@link #onMouse}); o resto é
 *       consumido antes de chegar a qualquer controller: teclas (inclusive os atalhos do menu, que
 *       só disparam se o evento chega até a cena), cliques e arrastos em cards, setas e painéis,
 *       menu de clique direito, soltar arquivos (imagens), método de entrada (IME).</li>
 *   <li><b>Histórico congelado</b>: {@code perform} não executa a alteração; desfazer, refazer e
 *       registrar não fazem nada. Nada do que se faz aqui entra no desfazer.</li>
 *   <li><b>Canvas em leitura</b> ({@code BoardView.setPresenting}): sem seleção (então nada para
 *       excluir, duplicar, travar, reordenar...), textos dos cards e dos painéis sem edição.</li>
 *   <li><b>Menu escondido e desabilitado</b> (feito pelo {@code MainController}).</li>
 * </ol>
 *
 * <h3>O que continua funcionando</h3>
 * Roda do mouse (zoom em torno do cursor), arrastar para mover a visão (qualquer botão do mouse; o
 * botão primário também, já que não há seleção a desenhar), {@code Shift+1} (enquadra de novo),
 * {@code Ctrl+0} (100%), clicar no botão de entrar num board aninhado, no botão que abre o painel,
 * nos marcadores de link (externo e para outro board), no ✕ do painel, e no caminho do topo e no
 * botão voltar ({@code Alt+←} também); {@code → / PageDown} e {@code ← / PageUp} vão ao próximo e
 * ao anterior board na ordem do PDF ({@link BoardSlides}); {@code Esc} sai.
 *
 * <h3>Decisões</h3>
 * <ul>
 *   <li><b>Esc sai direto</b>, mesmo com um painel aberto (o painel fecha junto): um só Esc sempre
 *       tira da tela cheia. Para fechar só o painel: ✕, o botão do card ou um clique fora dele.</li>
 *   <li><b>Todo board que aparece é enquadrado</b> (entrar, voltar, link, → e ←): a cada troca de
 *       board a visão vai para "caber tudo". Se a tela muda de tamanho (a tela cheia chega depois
 *       da entrada), o enquadramento é refeito enquanto você não mexeu na visão.</li>
 *   <li><b>Sair fica no board em que a apresentação terminou</b> (como nos programas de slides);
 *       a visão que cada board da pilha tinha antes é devolvida (se terminou no mesmo board de
 *       onde entrou, é exatamente a visão de antes); um board que só foi visitado na apresentação
 *       é enquadrado de novo, com as folgas normais. A ferramenta ativa e a janela (fora da tela
 *       cheia) também voltam. O {@code Alt+←}/botão voltar leva de volta à origem de um pulo, como
 *       nos links.</li>
 *   <li>→ e ← usam o {@code jumpTo} (como o link para outro board): o voltar volta ao slide de
 *       onde se veio. No primeiro e no último board nada acontece e aparece um aviso discreto.</li>
 * </ul>
 */
public class PresentationController {

    private static final String PANEL_CLOSE_STYLE = "card-panel-close";
    static final String START_NOTICE = "Esc sai  ·  ← → mudam de board  ·  Shift+1 enquadra";
    static final String FIRST_NOTICE = "Primeiro board";
    static final String LAST_NOTICE = "Último board";

    private final Stage stage;
    private final MainWindow window;
    private final BoardView view;
    private final NavigationController navigation;
    private final PanelController panels;
    private final EditHistory history;
    private final ObjectProperty<Tool> activeTool;
    private final SearchController search;
    private final CardContextMenuController contextMenu;
    private final CanvasController canvas;

    /** O que a entrada guarda para a saída devolver. */
    private record Saved(Map<String, ViewState> views, Tool tool, boolean fullScreen,
                         KeyCombination exitKey, String exitHint) {
    }

    private Saved saved;
    /** A visão logo depois do último enquadramento; se ela ainda é a atual quando a tela muda de tamanho, enquadra de novo. */
    private ViewState lastFit;
    private boolean panning;
    private double lastSceneX;
    private double lastSceneY;

    public PresentationController(Stage stage, MainWindow window, NavigationController navigation,
                                  PanelController panels, EditHistory history, ObjectProperty<Tool> activeTool,
                                  SearchController search, CardContextMenuController contextMenu,
                                  CanvasController canvas) {
        this.stage = stage;
        this.window = window;
        this.view = window.getBoardView();
        this.navigation = navigation;
        this.panels = panels;
        this.history = history;
        this.activeTool = activeTool;
        this.search = search;
        this.contextMenu = contextMenu;
        this.canvas = canvas;

        stage.addEventFilter(KeyEvent.ANY, this::onKey);
        stage.addEventFilter(MouseEvent.ANY, this::onMouse);
        stage.addEventFilter(ContextMenuEvent.ANY, this::blockWhilePresenting);
        stage.addEventFilter(DragEvent.ANY, this::blockWhilePresenting);
        stage.addEventFilter(InputMethodEvent.ANY, this::blockWhilePresenting);
        navigation.addOnBoardShown(this::boardShown);
        view.widthProperty().addListener(obs -> viewResized());
        view.heightProperty().addListener(obs -> viewResized());
        // Saiu da tela cheia por fora (o sistema, outro atalho): a apresentação acaba junto.
        stage.fullScreenProperty().addListener((obs, was, now) -> {
            if (!now && isPresenting()) {
                stop();
            }
        });
    }

    public boolean isPresenting() {
        return window.presentingProperty().get();
    }

    public ReadOnlyBooleanProperty presentingProperty() {
        return window.presentingProperty();
    }

    // ------------------------------------------------------------- entrar / sair

    /** Entra na apresentação, no board que está na tela. Não faz nada se já está apresentando. */
    public void start() {
        if (isPresenting()) {
            return;
        }
        // Termina o que está em andamento enquanto o histórico ainda grava: a edição de texto (de card,
        // de painel ou do texto de uma seta), o painel e a busca abertos, o menu de clique direito.
        view.requestFocus();
        search.closeIfOpen();
        panels.close();
        contextMenu.getMenu().hide();
        canvas.setSpaceDown(false);

        saved = new Saved(navigation.viewStates(), activeTool.get(), stage.isFullScreen(),
                stage.getFullScreenExitKeyCombination(), stage.getFullScreenExitHint());
        activeTool.set(Tool.SELECT);
        history.setFrozen(true);
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        stage.setFullScreenExitHint("");
        window.presentingProperty().set(true);
        stage.setFullScreen(true);
        fit(); // com o tamanho de agora; quando a tela cheia chegar, viewResized() enquadra de novo
        window.showPresentationNotice(START_NOTICE);
        view.requestFocus();
    }

    /** Sai da apresentação e devolve o estado de antes (ver a descrição da classe). Não faz nada se não está apresentando. */
    public void stop() {
        if (!isPresenting()) {
            return;
        }
        Saved before = saved;
        saved = null;
        endPan();
        panels.close();
        window.hidePresentationNotice();

        window.presentingProperty().set(false); // as barras e o menu voltam; o canvas volta a editar
        history.setFrozen(false);
        stage.setFullScreen(before.fullScreen());
        stage.setFullScreenExitKeyCombination(before.exitKey());
        stage.setFullScreenExitHint(before.exitHint());
        activeTool.set(before.tool());

        Board current = navigation.getCurrent();
        boolean known = current != null && before.views().containsKey(current.getId());
        navigation.restoreViewStates(before.views());
        if (known) {
            lastFit = null;
        } else {
            fit(); // board só visto na apresentação: enquadra com as folgas normais (e de novo quando a janela voltar)
        }
        view.requestFocus();
    }

    // ------------------------------------------------------------------- slides

    /** Vai ao próximo board da ordem do PDF. Retorna false (e avisa) se já é o último. */
    public boolean nextSlide() {
        return step(true);
    }

    /** Vai ao board anterior da ordem do PDF. Retorna false (e avisa) se já é o primeiro. */
    public boolean previousSlide() {
        return step(false);
    }

    private boolean step(boolean forward) {
        if (!isPresenting()) {
            return false;
        }
        Board root = navigation.getRoot();
        Board current = navigation.getCurrent();
        Optional<Board> target = forward ? BoardSlides.next(root, current) : BoardSlides.previous(root, current);
        if (target.isEmpty()) {
            window.showPresentationNotice(forward ? LAST_NOTICE : FIRST_NOTICE);
            return false;
        }
        return navigation.jumpTo(target.get().getId()) == NavigationPath.JumpResult.DONE;
    }

    // ----------------------------------------------------------- enquadramento

    /** Enquadra o board da tela: com as folgas da apresentação, ou as normais depois de sair dela. */
    private void fit() {
        if (isPresenting()) {
            view.fitAllForPresentation();
        } else {
            view.fitAll();
        }
        lastFit = view.getViewState();
    }

    /** A cada troca de board da apresentação (entrar, voltar, link, → e ←) o novo board aparece enquadrado. */
    private void boardShown() {
        if (isPresenting()) {
            panning = false;
            fit();
        }
    }

    private void viewResized() {
        if (lastFit == null || !view.getViewState().equals(lastFit)) {
            return; // a visão foi mexida (pan, zoom) depois do enquadramento: fica como está
        }
        fit();
        if (!isPresenting()) {
            lastFit = null; // o ajuste de depois de sair vale uma vez só
        }
    }

    // ------------------------------------------------------------------- teclas

    /**
     * Teclado. Na apresentação tudo é consumido (inclusive os atalhos do menu), menos as teclas
     * abaixo, que agem aqui mesmo:
     * <ul>
     *   <li>{@code Esc}: sai;</li>
     *   <li>{@code →} / {@code PageDown}: próximo board; {@code ←} / {@code PageUp}: anterior
     *       (sem Ctrl, Shift, Alt nem Meta);</li>
     *   <li>{@code Alt+←}: voltar (o botão ← do caminho);</li>
     *   <li>{@code Shift+1}: enquadra o board de novo; {@code Ctrl+0}: zoom de 100%.</li>
     * </ul>
     * O código da tecla não depende do layout: no teclado ABNT2 o {@code Shift+1} ("!") é
     * {@code DIGIT1}; as setas, PageUp/PageDown, Esc e F5 chegam com os mesmos códigos de qualquer teclado.
     */
    private void onKey(KeyEvent e) {
        if (!isPresenting()) {
            return;
        }
        e.consume();
        if (e.getEventType() != KeyEvent.KEY_PRESSED) {
            return; // soltar e digitar: nada a fazer (e nada pode chegar a um campo de texto)
        }
        boolean shift = e.isShiftDown();
        boolean ctrl = e.isControlDown();
        boolean alt = e.isAltDown();
        boolean meta = e.isMetaDown();
        boolean plain = !shift && !ctrl && !alt && !meta;
        KeyCode code = e.getCode();
        if (code == KeyCode.ESCAPE) {
            stop();
        } else if (plain && (code == KeyCode.RIGHT || code == KeyCode.PAGE_DOWN)) {
            nextSlide();
        } else if (plain && (code == KeyCode.LEFT || code == KeyCode.PAGE_UP)) {
            previousSlide();
        } else if (code == KeyCode.LEFT && alt && !shift && !ctrl && !meta) {
            navigation.back();
        } else if (code == KeyCode.DIGIT1 && shift && !ctrl && !alt && !meta) {
            fit();
        } else if (code == KeyCode.DIGIT0 && (ctrl || meta) && !shift && !alt) {
            view.resetView();
            lastFit = null;
        }
    }

    // -------------------------------------------------------------------- mouse

    /**
     * Mouse. Na apresentação passa (sem mexer): o mouse só passando por cima (dicas, cursor) e o
     * botão primário em quem navega ({@link #isNavigationTarget}: botão de entrar no board, botão do
     * painel, marcadores de link, ✕ do painel, caminho do topo, barras de rolagem). O resto é
     * consumido; apertar e arrastar (qualquer botão) move a visão, e o clique fora do painel aberto
     * fecha o painel. A roda do mouse (zoom) não é evento de mouse e segue para o canvas.
     */
    private void onMouse(MouseEvent e) {
        if (!isPresenting()) {
            return;
        }
        EventType<? extends MouseEvent> type = e.getEventType();
        if (type == MouseEvent.MOUSE_MOVED || type == MouseEvent.MOUSE_ENTERED || type == MouseEvent.MOUSE_EXITED
                || type == MouseEvent.MOUSE_ENTERED_TARGET || type == MouseEvent.MOUSE_EXITED_TARGET) {
            return;
        }
        if (e.getButton() == MouseButton.PRIMARY && !panning && isNavigationTarget(e.getTarget())) {
            return;
        }
        if (type == MouseEvent.MOUSE_PRESSED) {
            panels.closeIfPressedOutside(e);
            if (!isInsidePanel(e.getTarget())) {
                panning = true;
                lastSceneX = e.getSceneX();
                lastSceneY = e.getSceneY();
            }
        } else if (type == MouseEvent.MOUSE_DRAGGED) {
            if (panning) {
                view.setPanning(true);
                view.panBy(e.getSceneX() - lastSceneX, e.getSceneY() - lastSceneY);
                lastSceneX = e.getSceneX();
                lastSceneY = e.getSceneY();
            }
        } else if (type == MouseEvent.MOUSE_RELEASED) {
            endPan();
        }
        e.consume();
    }

    private void endPan() {
        if (panning) {
            panning = false;
            view.setPanning(false);
        }
    }

    /** Menu de clique direito, soltar arquivos e método de entrada: nada disso existe na apresentação. */
    private void blockWhilePresenting(javafx.event.Event e) {
        if (isPresenting()) {
            e.consume();
        }
    }

    /**
     * Quem recebe o clique do botão primário na apresentação: um botão de um card (entrar no board,
     * abrir o painel, os marcadores de link), o ✕ do painel, o caminho do topo (o botão voltar e os
     * níveis) e as barras de rolagem (do painel). Qualquer outra coisa (texto, campos e botões de edição
     * do painel, cards, setas, o fundo) não age: o clique só move a visão.
     */
    static boolean isNavigationTarget(EventTarget target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        for (Node n = node; n != null; n = n.getParent()) {
            if (n instanceof ScrollBar || n instanceof BreadcrumbView) {
                return true;
            }
            if (n instanceof Button button
                    && (button.getStyleClass().contains(PANEL_CLOSE_STYLE) || isInsideCardView(button))) {
                return true;
            }
            if (n instanceof FloatingPanelView) {
                return false; // dentro do painel, só o ✕ e as barras de rolagem (acima) agem
            }
        }
        return false;
    }

    private static boolean isInsideCardView(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n instanceof CardView) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInsidePanel(EventTarget target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        for (Node n = node; n != null; n = n.getParent()) {
            if (n instanceof FloatingPanelView) {
                return true;
            }
        }
        return false;
    }
}

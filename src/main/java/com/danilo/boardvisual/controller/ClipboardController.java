package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.CardClip;
import com.danilo.boardvisual.model.PasteStaircase;
import com.danilo.boardvisual.persistence.ClipboardImages;
import com.danilo.boardvisual.view.BoardView;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Copiar, recortar e colar cards (Ctrl+C, Ctrl+X, Ctrl+V), no mesmo board ou em
 * outro. Regras completas na seção 3.25 do DOCUMENTACAO.md.
 *
 * <b>A área de transferência do app é da sessão</b> (não vale entre programas): o
 * que foi copiado fica aqui como um {@link CardClip} (foto imutável dos cards
 * e das setas entre eles), independente do board de origem. Navegar para outro
 * board da árvore, ou abrir outro save, não a esvazia; colar aplica a cópia no
 * board que está na tela, com ids novos, num único passo do desfazer, e deixa os
 * cards colados selecionados. A montagem em si (ids, links, posição) é do
 * {@link CardClip}, sem tela; as imagens, do {@link ClipboardImages}.
 *
 * <b>Teclas.</b> Ctrl+C/X/V são aceleradores do menu Editar (como Ctrl+Z e
 * Ctrl+A): com um texto sendo editado o próprio texto consome a tecla e nada
 * chega aqui. Os itens do menu, usados com o mouse, agem no texto se ele ainda
 * tem o foco.
 *
 * <b>Imagem do sistema × cards copiados.</b> O Ctrl+V só tratava imagem da área
 * de transferência do <i>sistema</i> (3.17). Para decidir qual dos dois colar sem
 * ter como saber a hora de cada um: copiar cards aqui <b>substitui</b> o que
 * estava na área de transferência do sistema por texto (os textos dos cards,
 * que também dá para colar em outro programa). Então, se na hora do Ctrl+V o
 * sistema tem uma imagem (ou arquivos de imagem), ela entrou <b>depois</b> da cópia
 * e é ela que vale; senão, valem os cards copiados. Se o sistema recusar a
 * escrita, a imagem que estiver lá vence (o caminho conservador).
 */
public class ClipboardController {

    /**
     * O que o controller precisa da área de transferência do sistema. Nos testes
     * entra uma versão falsa, para não mexer na do usuário.
     */
    public interface SystemSide {
        /** Se há imagem (ou arquivos de imagem) para colar. */
        boolean hasImage();

        /** Troca o conteúdo da área de transferência do sistema por este texto; false se não deu. */
        boolean putText(String text);

        /** Cola a imagem do sistema no board, centralizada em {@code center} (null = centro da área visível). */
        boolean pasteImage(Point2D center);
    }

    /** Onde ficam as imagens do board atual (quem sabe é o {@link ImageController}). */
    public interface ImageSpace {
        /** Pastas onde as imagens do board atual são procuradas. */
        List<Path> searchDirs();

        /** Pasta (criada se preciso) onde entram as imagens que chegam sem estar no save atual. */
        Path newImagesDir() throws IOException;
    }

    private final BoardView view;
    private final EditHistory history;
    private final SelectionActionsController selectionActions;
    private final ImageSpace imageSpace;
    private final ClipboardImages clipImages = new ClipboardImages();
    private final PasteStaircase staircase = new PasteStaircase();
    private final ContextMenu backgroundMenu = new ContextMenu();

    private SystemSide system;
    private CardClip clip;
    private Consumer<String> notifier = text -> { };

    public ClipboardController(BoardView view, EditHistory history, SelectionActionsController selectionActions,
                               ImageSpace imageSpace, SystemSide system, CardContextMenuController contextMenu) {
        this.view = view;
        this.history = history;
        this.selectionActions = selectionActions;
        this.imageSpace = imageSpace;
        this.system = system;

        contextMenu.addContributor(this::menuItems);
        backgroundMenu.getStyleClass().add("card-context-menu");
        view.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            // Clique direito parado no fundo (arrastar é pan): oferece Colar onde se clicou.
            if (e.getButton() == MouseButton.SECONDARY && e.isStillSincePress()
                    && e.getPickResult().getIntersectedNode() == view) {
                showBackgroundMenu(e.getSceneX(), e.getSceneY(), e.getScreenX(), e.getScreenY());
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(clipImages::discard, "limpa-imagens-copiadas"));
    }

    /** A área de transferência do sistema que vale (os roteiros de interface trocam por uma falsa). */
    public void setSystemSide(SystemSide system) {
        this.system = system;
    }

    /** Onde aparecem os avisos curtos (na aplicação, a barra de status). */
    public void setNotifier(Consumer<String> notifier) {
        this.notifier = notifier == null ? text -> { } : notifier;
    }

    /** O que foi copiado, ou vazio se nada foi. */
    public Optional<CardClip> getClip() {
        return Optional.ofNullable(clip);
    }

    /** Se um Ctrl+V agora faria algo: há cards copiados ou imagem no sistema. */
    public boolean canPaste() {
        return clip != null || system.hasImage();
    }

    // ------------------------------------------------------------ copiar e recortar

    /**
     * Copia os cards selecionados (e as setas entre eles). Retorna false, sem
     * mudar o que já estava copiado, se não há card selecionado.
     */
    public boolean copy() {
        return copy("copiado", "copiados");
    }

    /**
     * Recorta: copia e exclui os selecionados que não estão travados (os
     * travados ficam, com o aviso de sempre). A exclusão é um passo do desfazer.
     */
    public boolean cut() {
        if (!copy("recortado", "recortados")) {
            return false;
        }
        selectionActions.deleteSelection();
        return true;
    }

    private boolean copy(String one, String many) {
        Board board = view.getBoard();
        if (board == null) {
            return false;
        }
        Optional<CardClip> made = CardClip.of(board, view.getSelection());
        if (made.isEmpty()) {
            return false;
        }
        CardClip next = made.get();
        try {
            // Antes de a origem poder mudar: os arquivos de imagem ficam guardados com a cópia.
            clipImages.keep(next.imageFiles(), imageSpace.searchDirs());
        } catch (IOException e) {
            notifier.accept("Não foi possível guardar as imagens copiadas: " + e.getMessage());
        }
        clip = next;
        staircase.reset();
        // Substitui o que o sistema tinha (ver a regra de prioridade na descrição da classe).
        String text = next.plainText();
        system.putText(text.isBlank() ? "(cards do BoardVisual)" : text);
        notifier.accept(next.cardCount() == 1 ? "1 card " + one + "." : next.cardCount() + " cards " + many + ".");
        return true;
    }

    // ------------------------------------------------------------ colar

    /** Ctrl+V: cola no centro da área visível. Retorna false se não havia o que colar. */
    public boolean paste() {
        return paste(null);
    }

    /**
     * Cola o que vale agora (ver a prioridade na descrição da classe): a imagem
     * do sistema, se há; senão os cards copiados, centralizados em {@code center}
     * (ponto do board; null = centro da área visível), já selecionados, num passo
     * do desfazer. Colar de novo no mesmo lugar desce um degrau ({@link PasteStaircase}).
     */
    public boolean paste(Point2D center) {
        Board board = view.getBoard();
        if (board == null) {
            return false;
        }
        if (system.hasImage() && system.pasteImage(center)) {
            return true;
        }
        if (clip == null) {
            return false;
        }
        Point2D base = center != null ? center : view.visibleCenter();
        PasteStaircase.Spot spot = staircase.next(board.getId(), base.getX(), base.getY());

        bringImages();
        CardClip.Pasted pasted = clip.pasteCenteredAt(spot.x(), spot.y());
        history.perform(() -> {
            pasted.cards().forEach(board::addCard);
            pasted.connections().forEach(board::addConnection);
        });
        view.setSelection(pasted.cards());
        view.requestFocus();
        return true;
    }

    /** Põe onde o board atual procura imagens as dos cards copiados que ainda não estão lá. */
    private void bringImages() {
        try {
            List<String> missing = clipImages.deliver(clip.imageFiles(), imageSpace.searchDirs(), imageSpace::newImagesDir);
            if (!missing.isEmpty()) {
                notifier.accept(missing.size() == 1
                        ? "Uma imagem dos cards copiados já não existia: o card colado mostra \"Imagem não encontrada\"."
                        : missing.size() + " imagens dos cards copiados já não existiam: os cards colados mostram \"Imagem não encontrada\".");
            }
        } catch (IOException e) {
            notifier.accept("Não foi possível copiar as imagens: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ menu Editar

    /** Item Copiar do menu: com texto em edição que ainda tem o foco, copia o texto. */
    public void menuCopy() {
        if (!delegateToText(TextInputControl::copy)) {
            copy();
        }
    }

    public void menuCut() {
        if (!delegateToText(TextInputControl::cut)) {
            cut();
        }
    }

    public void menuPaste() {
        if (!delegateToText(TextInputControl::paste)) {
            paste();
        }
    }

    private boolean delegateToText(Consumer<TextInputControl> action) {
        Scene scene = view.getScene();
        if (scene != null && scene.getFocusOwner() instanceof TextInputControl text) {
            action.accept(text);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ menus de clique direito

    private List<MenuItem> menuItems(CardContextMenuController.MenuContext context) {
        MenuItem copyItem = new MenuItem("Copiar");
        copyItem.setOnAction(e -> copy());
        MenuItem cutItem = new MenuItem("Recortar");
        cutItem.setOnAction(e -> cut());
        MenuItem pasteItem = new MenuItem("Colar");
        pasteItem.setDisable(!canPaste());
        pasteItem.setOnAction(e -> paste());
        return List.of(copyItem, cutItem, pasteItem);
    }

    /** O menu do clique direito no fundo: só "Colar", onde se clicou. */
    private void showBackgroundMenu(double sceneX, double sceneY, double screenX, double screenY) {
        Point2D where = view.sceneToWorld(sceneX, sceneY);
        MenuItem pasteItem = new MenuItem("Colar");
        pasteItem.setDisable(!canPaste());
        pasteItem.setOnAction(e -> paste(where));
        backgroundMenu.hide();
        backgroundMenu.getItems().setAll(pasteItem);
        backgroundMenu.show(view, screenX, screenY);
    }

    /** O menu do fundo (para os roteiros de teste de interface). */
    public ContextMenu getBackgroundMenu() {
        return backgroundMenu;
    }

    // ------------------------------------------------------------ o sistema de verdade

    /**
     * A área de transferência do sistema de verdade (JavaFX): a imagem é colada
     * pelo {@link ImageController}.
     */
    public static SystemSide systemOf(ImageController images) {
        return new SystemSide() {
            @Override
            public boolean hasImage() {
                return images.canPasteSystemImage();
            }

            @Override
            public boolean putText(String text) {
                try {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    return Clipboard.getSystemClipboard().setContent(content);
                } catch (RuntimeException e) {
                    return false; // a área de transferência pode estar em uso por outro programa
                }
            }

            @Override
            public boolean pasteImage(Point2D center) {
                return images.paste(center);
            }
        };
    }
}

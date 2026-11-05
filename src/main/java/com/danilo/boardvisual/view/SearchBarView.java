package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.BoardSearch;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Caixa de busca (Ctrl+F), no canto superior direito do canvas: uma camada fora do
 * {@code world} do {@link BoardView}, então não sofre pan nem zoom (como a barra
 * flutuante e a caixa de seleção). Só desenha e repassa os eventos; quem busca,
 * destaca e navega é o {@code SearchController}.
 *
 * <pre>
 * [ campo de texto ........ ]  3 de 12  [↑] [↓] [×]
 * [ ] Em todos os boards
 * lista de resultados (só com "Em todos os boards"): trecho + caminho; clicar leva até lá
 * </pre>
 *
 * Nenhum botão aceita foco: clicar neles não tira o cursor do campo de texto (o Enter
 * continua indo ao próximo resultado). Fechada, fica invisível e fora do layout.
 */
public class SearchBarView extends VBox {

    private static final PseudoClass NO_MATCH = PseudoClass.getPseudoClass("no-match");
    /** Quantos resultados cabem na lista antes de ela rolar, e a altura de cada linha. */
    private static final int MAX_VISIBLE_RESULTS = 6;
    private static final double RESULT_CELL_HEIGHT = 46;

    private final TextField field = new TextField();
    private final Label counter = new Label();
    private final Button previousButton = new Button();
    private final Button nextButton = new Button();
    private final Button closeButton = new Button();
    private final CheckBox allBoards = new CheckBox("Em todos os boards");
    private final ListView<BoardSearch.Hit> resultList = new ListView<>();

    private IntConsumer onPick = index -> { };

    public SearchBarView() {
        getStyleClass().addAll("floating-panel", "search-bar");
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setPrefWidth(380);
        setVisible(false);
        managedProperty().bind(visibleProperty());

        field.getStyleClass().add("search-field");
        field.setPromptText("Buscar nos cards...");
        HBox.setHgrow(field, Priority.ALWAYS);

        counter.getStyleClass().add("search-counter");
        counter.setMinWidth(Region.USE_PREF_SIZE);

        setUpButton(previousButton, Icons.CHEVRON_UP, "Resultado anterior  (Shift+Enter)");
        setUpButton(nextButton, Icons.CHEVRON_DOWN, "Próximo resultado  (Enter)");
        setUpButton(closeButton, Icons.CLOSE, "Fechar a busca  (Esc)");

        HBox row = new HBox(6, field, counter, previousButton, nextButton, closeButton);
        row.setAlignment(Pos.CENTER_LEFT);

        // Clicar na caixa não tira o foco do campo de texto.
        allBoards.setFocusTraversable(false);
        allBoards.getStyleClass().add("search-scope");
        allBoards.setTooltip(new Tooltip("Procurar também nos boards dentro dos cards"));

        setUpResultList();

        getChildren().addAll(row, allBoards, resultList);
    }

    private static void setUpButton(Button button, String icon, String tooltip) {
        button.getStyleClass().addAll("tool-button", "search-button");
        button.setGraphic(Icons.create(icon));
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
    }

    private void setUpResultList() {
        resultList.getStyleClass().add("search-results");
        resultList.setFocusTraversable(false);
        resultList.setFixedCellSize(RESULT_CELL_HEIGHT);
        resultList.setCellFactory(list -> new ResultCell());
        resultList.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> allBoards.isSelected() && !resultList.getItems().isEmpty(),
                allBoards.selectedProperty(), resultList.itemsProperty(), resultList.getItems()));
        resultList.managedProperty().bind(resultList.visibleProperty());
        resultList.setPlaceholder(new Label());
    }

    /** Uma linha da lista: o trecho achado e, embaixo, o caminho do board; clicar leva ao resultado. */
    private final class ResultCell extends ListCell<BoardSearch.Hit> {
        private final Label snippet = new Label();
        private final Label path = new Label();
        private final VBox box = new VBox(snippet, path);

        ResultCell() {
            getStyleClass().add("search-result");
            snippet.getStyleClass().add("search-result-snippet");
            path.getStyleClass().add("search-result-path");
            box.setAlignment(Pos.CENTER_LEFT);
            setOnMouseClicked(e -> {
                if (!isEmpty() && getIndex() >= 0) {
                    pickResult(getIndex());
                }
            });
        }

        @Override
        protected void updateItem(BoardSearch.Hit hit, boolean empty) {
            super.updateItem(hit, empty);
            if (empty || hit == null) {
                setGraphic(null);
                return;
            }
            snippet.setText(prefixOf(hit) + hit.snippet());
            path.setText(hit.pathLabel());
            setGraphic(box);
        }
    }

    /** Dica de onde o trecho veio, quando não é o texto do próprio card. */
    static String prefixOf(BoardSearch.Hit hit) {
        return switch (hit.field()) {
            case TEXT -> "";
            case DETAIL, LIST_ITEM -> "Painel: ";
            case ARROW -> "Seta: ";
        };
    }

    // ------------------------------------------------------------- estado

    public boolean isOpen() {
        return isVisible();
    }

    public void open() {
        setVisible(true);
    }

    public void close() {
        setVisible(false);
    }

    /** Se o foco do teclado está em algum lugar da caixa de busca. */
    public boolean hasFocusInside() {
        if (getScene() == null) {
            return false;
        }
        for (Node n = getScene().getFocusOwner(); n != null; n = n.getParent()) {
            if (n == this) {
                return true;
            }
        }
        return false;
    }

    /**
     * O resumo ao lado do campo ("3 de 12", "Nenhum resultado" ou vazio). Sem resultado,
     * o campo e o resumo ganham {@code :no-match} (o CSS os avisa de forma discreta).
     */
    public void setSummary(String text, boolean noMatch) {
        counter.setText(text);
        counter.pseudoClassStateChanged(NO_MATCH, noMatch);
        field.pseudoClassStateChanged(NO_MATCH, noMatch);
    }

    public String getSummary() {
        return counter.getText();
    }

    /** Se o campo de texto está avisando que nada foi achado. */
    public boolean isNoMatch() {
        return field.getPseudoClassStates().contains(NO_MATCH);
    }

    /** Mostra os resultados na lista (só aparece com "Em todos os boards") e marca o atual. */
    public void showResults(List<BoardSearch.Hit> hits, int currentIndex) {
        resultList.getItems().setAll(hits);
        resultList.setPrefHeight(Math.min(hits.size(), MAX_VISIBLE_RESULTS) * RESULT_CELL_HEIGHT + 2);
        if (currentIndex >= 0 && currentIndex < hits.size()) {
            resultList.getSelectionModel().select(currentIndex);
            resultList.scrollTo(Math.max(0, currentIndex - 1));
        } else {
            resultList.getSelectionModel().clearSelection();
        }
    }

    /** O que o clique numa linha da lista faz: avisa quem escolheu (a posição do resultado). */
    public void pickResult(int index) {
        onPick.accept(index);
    }

    /** Quem é avisado ao clicar num resultado da lista (a posição dele). */
    public void setOnPick(IntConsumer onPick) {
        this.onPick = onPick == null ? index -> { } : onPick;
    }

    public TextField getField() { return field; }
    public CheckBox getAllBoardsBox() { return allBoards; }
    public Button getPreviousButton() { return previousButton; }
    public Button getNextButton() { return nextButton; }
    public Button getCloseButton() { return closeButton; }
    public ListView<BoardSearch.Hit> getResultList() { return resultList; }
}

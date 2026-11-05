package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.StackMove;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Botão "Ordem" da barra flutuante (ícone de camadas): abre as quatro ações
 * da ordem de empilhamento, cada uma com o atalho ao lado. Um botão só, em vez
 * de quatro, para a barra não crescer demais; as opções que não mudariam nada
 * (ex.: "Trazer para frente" com o card já no topo) aparecem desabilitadas.
 *
 * Mesma separação dos outros seletores: quem decide o que fazer é o controller
 * ({@link #setOnMove}, {@link #setAvailability}).
 */
public class StackOrderButton extends PopupButton {

    private final Map<StackMove, Button> options = new EnumMap<>(StackMove.class);
    private Consumer<StackMove> onMove = move -> { };
    private Predicate<StackMove> availability = move -> true;

    public StackOrderButton(String tooltip, Side popupSide) {
        super(tooltip, popupSide, "Ordem de empilhamento");
        getStyleClass().add("stack-order-button");
        setGraphic(Icons.create(Icons.LAYERS));

        VBox body = new VBox();
        body.getStyleClass().add("stack-order-options");
        for (StackMove move : new StackMove[] {StackMove.FORWARD, StackMove.TO_FRONT,
                StackMove.BACKWARD, StackMove.TO_BACK}) {
            body.getChildren().add(option(move));
        }
        setPopupBody(body);
    }

    /** Nome da ação (o mesmo no menu de clique direito). */
    public static String label(StackMove move) {
        return switch (move) {
            case FORWARD -> "Trazer para frente";
            case BACKWARD -> "Enviar para trás";
            case TO_FRONT -> "Trazer para o topo";
            case TO_BACK -> "Enviar para o fundo";
        };
    }

    /**
     * Atalhos da ação, como aparecem para o usuário. Os colchetes valem pelo
     * caractere da tecla (funcionam no ABNT2 e no americano); PgUp/PgDn são a
     * alternativa que não depende do layout (3.16 do DOCUMENTACAO.md).
     */
    public static String shortcut(StackMove move) {
        return switch (move) {
            case FORWARD -> "Ctrl+]  ou  Ctrl+PgUp";
            case BACKWARD -> "Ctrl+[  ou  Ctrl+PgDn";
            case TO_FRONT -> "Ctrl+Shift+]  ou  Ctrl+Shift+PgUp";
            case TO_BACK -> "Ctrl+Shift+[  ou  Ctrl+Shift+PgDn";
        };
    }

    private Button option(StackMove move) {
        Label name = new Label(label(move));
        name.getStyleClass().add("stack-order-name");
        Label keys = new Label(shortcut(move));
        keys.getStyleClass().add("stack-order-shortcut");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(name, spacer, keys);
        row.getStyleClass().add("stack-order-row");

        Button option = new Button();
        option.setGraphic(row);
        option.getStyleClass().addAll("tool-button", "stack-order-option");
        option.setMaxWidth(Double.MAX_VALUE);
        option.setFocusTraversable(false);
        option.setTooltip(new Tooltip(label(move)));
        option.setOnAction(e -> {
            hidePopup();
            onMove.accept(move);
        });
        options.put(move, option);
        return option;
    }

    @Override
    void beforeShowPopup() {
        options.forEach((move, option) -> option.setDisable(!availability.test(move)));
    }

    /** A opção de cada ação (para os roteiros de teste de interface). */
    public Button getOption(StackMove move) {
        return options.get(move);
    }

    /** Chamado quando o usuário escolhe uma ação. */
    public void setOnMove(Consumer<StackMove> handler) {
        onMove = handler == null ? move -> { } : handler;
    }

    /** Diz se a ação mudaria algo agora (as que não mudam ficam desabilitadas ao abrir). */
    public void setAvailability(Predicate<StackMove> available) {
        availability = available == null ? move -> true : available;
    }
}

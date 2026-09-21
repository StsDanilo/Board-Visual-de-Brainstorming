package com.danilo.boardvisual.view;

import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Caminho do board atual, no canto superior esquerdo, como num explorador de
 * arquivos: {@code Board principal › board 2 › board 3}.
 *
 * Cada nível anterior é um link que leva direto até ele; o último (onde você
 * está) aparece em destaque, sem link. A partir do primeiro board filho
 * aparece também o botão de voltar.
 */
public class BreadcrumbView extends HBox {

    private final Button backButton = new Button();
    private final HBox segments = new HBox();

    private IntConsumer onNavigate = index -> { };

    public BreadcrumbView() {
        getStyleClass().addAll("floating-panel", "breadcrumb");
        setAlignment(Pos.CENTER_LEFT);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        backButton.getStyleClass().addAll("tool-button", "breadcrumb-back");
        backButton.setGraphic(Icons.create(Icons.BACK));
        backButton.setTooltip(new Tooltip("Voltar ao board anterior  (Alt+←)"));
        backButton.setFocusTraversable(false);
        backButton.managedProperty().bind(backButton.visibleProperty());
        backButton.setVisible(false);

        segments.getStyleClass().add("breadcrumb-segments");
        segments.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(backButton, segments);
    }

    /**
     * Define o caminho, do board principal até o atual. Os nomes são
     * observáveis: renomear um card atualiza o caminho na hora.
     */
    public void setPath(List<? extends ObservableStringValue> names) {
        segments.getChildren().clear();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                Label separator = new Label("›");
                separator.getStyleClass().add("breadcrumb-separator");
                segments.getChildren().add(separator);
            }
            boolean current = i == names.size() - 1;
            if (current) {
                Label label = new Label();
                label.getStyleClass().add("breadcrumb-current");
                label.textProperty().bind(names.get(i));
                segments.getChildren().add(label);
            } else {
                int index = i;
                Hyperlink link = new Hyperlink();
                link.getStyleClass().add("breadcrumb-link");
                link.textProperty().bind(names.get(i));
                link.setFocusTraversable(false);
                link.setOnAction(e -> {
                    link.setVisited(false);
                    onNavigate.accept(index);
                });
                segments.getChildren().add(link);
            }
        }
        backButton.setVisible(names.size() > 1);
    }

    /** Chamado com o índice do nível clicado (0 = board principal). */
    public void setOnNavigate(IntConsumer handler) {
        onNavigate = handler == null ? index -> { } : handler;
    }

    public Button getBackButton() {
        return backButton;
    }
}

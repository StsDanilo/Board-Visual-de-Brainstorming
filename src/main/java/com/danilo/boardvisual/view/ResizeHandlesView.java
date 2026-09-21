package com.danilo.boardvisual.view;

import com.danilo.boardvisual.alignment.Edge;
import com.danilo.boardvisual.alignment.ResizeHandle;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.layout.Region;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * As 8 alças de redimensionamento em volta do card selecionado.
 *
 * Ficam em pixels de tela (fora do zoom), posicionadas pelo {@link BoardView}.
 * Cada alça tem a classe CSS {@code resize-<nome>} para o cursor certo
 * (ex.: {@code resize-south-east} → cursor diagonal).
 *
 * A área clicável ({@link #SIZE}) é maior que o quadradinho desenhado: o CSS
 * desenha só o centro, e o resto é margem transparente que também aceita clique.
 */
public class ResizeHandlesView extends Group {

    /** Área clicável de cada alça; o quadrado visível (9 px) é desenhado no centro pelo CSS. */
    static final double SIZE = 17;

    private final Map<ResizeHandle, Region> handles = new EnumMap<>(ResizeHandle.class);

    public ResizeHandlesView() {
        getStyleClass().add("resize-handles");
        for (ResizeHandle handle : ResizeHandle.values()) {
            Region region = new Region();
            region.getStyleClass().addAll("resize-handle",
                    "resize-" + handle.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            region.setMinSize(SIZE, SIZE);
            region.setPrefSize(SIZE, SIZE);
            region.setMaxSize(SIZE, SIZE);
            handles.put(handle, region);
            getChildren().add(region);
        }
    }

    public Region getHandle(ResizeHandle handle) {
        return handles.get(handle);
    }

    /**
     * Posiciona as alças em volta do retângulo (coordenadas do {@link BoardView}).
     * Com {@code cornersOnly}, só as dos cantos aparecem (formatos que mantêm a proporção).
     */
    void layoutAround(Bounds bounds, boolean cornersOnly) {
        // Card pequeno na tela: as alças do meio das bordas encostariam nas dos cantos.
        boolean roomForHorizontalMiddle = bounds.getWidth() >= 3 * SIZE;
        boolean roomForVerticalMiddle = bounds.getHeight() >= 3 * SIZE;
        handles.forEach((handle, region) -> {
            double x = position(handle.xEdge(), bounds.getMinX(), bounds.getMaxX());
            double y = position(handle.yEdge(), bounds.getMinY(), bounds.getMaxY());
            region.resize(SIZE, SIZE);
            region.relocate(Math.round(x - SIZE / 2), Math.round(y - SIZE / 2));
            boolean visible;
            if (handle.isCorner()) {
                visible = true;
            } else if (cornersOnly) {
                visible = false;
            } else if (handle.xEdge() == null) {
                visible = roomForHorizontalMiddle; // norte / sul
            } else {
                visible = roomForVerticalMiddle;   // leste / oeste
            }
            region.setVisible(visible);
        });
    }

    private static double position(Edge edge, double min, double max) {
        if (edge == Edge.START) {
            return min;
        }
        if (edge == Edge.END) {
            return max;
        }
        return (min + max) / 2;
    }
}

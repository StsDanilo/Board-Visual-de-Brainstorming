package com.danilo.boardvisual.view;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.model.Connection;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Predicate;

/**
 * Desenha um board inteiro numa imagem, fora da tela (para o PDF e o PNG).
 *
 * Monta os mesmos {@link CardView} e {@link ConnectionView} do canvas numa cena
 * própria, com o mesmo CSS, e tira um snapshot na resolução pedida. Não usa o
 * {@link BoardView}: não há seleção, pan, zoom nem barras. A classe
 * {@code board-export} no CSS esconde o que só faz sentido na tela (texto de
 * exemplo dos cards vazios, alça de conexão).
 *
 * Precisa rodar na thread do JavaFX. Não altera o modelo: a fonte de cada card
 * é calculada e aplicada só na cópia visual.
 */
public final class BoardRenderer {

    private final List<String> stylesheets;
    private final CardImageLoader imageLoader;
    private final Predicate<String> boardExists;

    public BoardRenderer(List<String> stylesheets) {
        this(stylesheets, CardImageLoader.NONE);
    }

    /**
     * @param imageLoader de onde vêm as imagens dos cards (a pasta do save); precisa
     *                    devolver imagens já carregadas, porque o snapshot é imediato
     */
    public BoardRenderer(List<String> stylesheets, CardImageLoader imageLoader) {
        this(stylesheets, imageLoader, boardId -> true);
    }

    /**
     * @param boardExists diz se um board (pelo id) ainda está na árvore: os links
     *                    para boards que não existem mais saem com o marcador
     *                    "quebrado", como na tela
     */
    public BoardRenderer(List<String> stylesheets, CardImageLoader imageLoader, Predicate<String> boardExists) {
        this.stylesheets = List.copyOf(stylesheets);
        this.imageLoader = imageLoader == null ? CardImageLoader.NONE : imageLoader;
        this.boardExists = boardExists == null ? boardId -> true : boardExists;
    }

    /** Com fundo branco (o PDF e o PNG padrão). */
    public BufferedImage render(Board board, Box bounds, double pixelsPerUnit) {
        return render(board, bounds, pixelsPerUnit, false);
    }

    /**
     * @param transparent fundo transparente (imagem com canal alfa) em vez de branco
     */
    public BufferedImage render(Board board, Box bounds, double pixelsPerUnit, boolean transparent) {
        Group connections = new Group();
        for (Connection connection : board.getConnections()) {
            connections.getChildren().add(new ConnectionView(connection));
        }
        Group cards = new Group();
        for (Card card : board.getCards()) {
            CardView view = new CardView(card, imageLoader);
            view.setTextFontSize(CardTextFit.forScreen().fit(card).fontSize());
            if (card.hasBoardLink()) {
                view.setMarkerBroken(CardMarkers.Kind.BOARD_LINK, !boardExists.test(card.getBoardLink()));
            }
            cards.getChildren().add(view);
        }
        Group content = new Group(connections, cards);
        Group root = new Group(content);
        root.getStyleClass().add("board-export");
        Scene scene = new Scene(root);
        scene.getStylesheets().setAll(stylesheets);
        root.applyCss();
        root.layout();

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(transparent ? Color.TRANSPARENT : Color.WHITE);
        parameters.setTransform(new Scale(pixelsPerUnit, pixelsPerUnit));
        // Origem num pixel inteiro: com origem fracionária o snapshot arredonda as duas
        // pontas e sai 1 px maior que o pedido (a margem em volta absorve o deslocamento).
        parameters.setViewport(new Rectangle2D(
                Math.floor(bounds.x() * pixelsPerUnit), Math.floor(bounds.y() * pixelsPerUnit),
                pixels(bounds.width(), pixelsPerUnit), pixels(bounds.height(), pixelsPerUnit)));
        WritableImage snapshot = content.snapshot(parameters, null);
        return toBufferedImage(snapshot, transparent);
    }

    /** Pixels de um lado: para cima, descontando o erro de ponto flutuante (mesma conta do PngExporter). */
    private static int pixels(double units, double pixelsPerUnit) {
        return Math.max(1, (int) Math.ceil(units * pixelsPerUnit - 1e-6));
    }

    /**
     * Converte sem depender do módulo javafx.swing: copia os pixels pelo
     * PixelReader. Com fundo branco a imagem não precisa de canal alfa.
     */
    private static BufferedImage toBufferedImage(WritableImage image, boolean alpha) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage result = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }
}

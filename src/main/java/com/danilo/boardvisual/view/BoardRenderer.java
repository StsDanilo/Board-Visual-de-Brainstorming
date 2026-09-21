package com.danilo.boardvisual.view;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
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

/**
 * Desenha um board inteiro numa imagem, fora da tela (para o PDF).
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

    public BoardRenderer(List<String> stylesheets) {
        this.stylesheets = List.copyOf(stylesheets);
    }

    public BufferedImage render(Board board, Box bounds, double pixelsPerUnit) {
        Group connections = new Group();
        for (Connection connection : board.getConnections()) {
            connections.getChildren().add(new ConnectionView(connection));
        }
        Group cards = new Group();
        for (Card card : board.getCards()) {
            CardView view = new CardView(card);
            view.setTextFontSize(CardTextFit.forScreen().fit(card).fontSize());
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
        parameters.setFill(Color.WHITE);
        parameters.setTransform(new Scale(pixelsPerUnit, pixelsPerUnit));
        parameters.setViewport(new Rectangle2D(bounds.x() * pixelsPerUnit, bounds.y() * pixelsPerUnit,
                Math.ceil(bounds.width() * pixelsPerUnit), Math.ceil(bounds.height() * pixelsPerUnit)));
        WritableImage snapshot = content.snapshot(parameters, null);
        return toBufferedImage(snapshot);
    }

    /** Converte sem depender do módulo javafx.swing (fundo branco: sem transparência). */
    private static BufferedImage toBufferedImage(WritableImage image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }
}

package com.danilo.boardvisual.controller;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.alignment.BoxResizer;
import com.danilo.boardvisual.alignment.ResizeHandle;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.persistence.BoardImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Partes do ImageController e do CardResizeController que não precisam de tela. */
class ImageCardControllerTest {

    private final Board root = new Board("raiz");
    private final EditHistory history = new EditHistory(() -> root);

    // ------------------------------------------------------------ limpeza ao salvar

    @Test
    void cleanupKeepsImagesOfNestedBoardsAndOfTheUndoHistory(@TempDir Path folder) throws IOException {
        root.addCard(Card.createImage("raiz.png", 0, 0, 10, 10));
        Card holder = new Card(0, 0);
        Board child = new Board("filho");
        child.addCard(Card.createImage("filho.png", 0, 0, 10, 10));
        holder.setChildBoard(child);
        root.addCard(holder);
        // Inserida e depois excluída: só o desfazer ainda a cita.
        Card deleted = Card.createImage("excluida.png", 0, 0, 10, 10);
        history.perform(() -> root.addCard(deleted));
        history.perform(() -> root.removeCard(deleted));

        Path images = Files.createDirectories(BoardImages.dirOf(folder));
        for (String name : List.of("raiz.png", "filho.png", "excluida.png", "orfa.png")) {
            Files.write(images.resolve(name), new byte[]{1});
        }

        Set<String> inUse = ImageController.imagesInUse(root, history);
        List<String> removed = BoardImages.removeUnused(folder, inUse);

        assertEquals(Set.of("raiz.png", "filho.png", "excluida.png"), inUse);
        assertEquals(List.of("orfa.png"), removed);
        try (Stream<Path> left = Files.list(images)) {
            assertEquals(3, left.count());
        }
    }

    @Test
    void afterTheHistoryIsClearedTheDeletedImageIsNoLongerInUse() {
        Card deleted = Card.createImage("excluida.png", 0, 0, 10, 10);
        history.perform(() -> root.addCard(deleted));
        history.perform(() -> root.removeCard(deleted));

        history.clear(); // ex.: abrir outro board

        assertEquals(Set.of(), ImageController.imagesInUse(root, history));
    }

    // ------------------------------------------------------------ tamanho inicial

    @Test
    void initialSizeKeepsTheProportionWithinTheLimits() {
        assertArrayEquals(new double[]{320, 180}, ImageController.initialSize(1920, 1080));
        assertArrayEquals(new double[]{160, 320}, ImageController.initialSize(1000, 2000));
        assertArrayEquals(new double[]{200, 150}, ImageController.initialSize(200, 150), "já cabe: tamanho natural");
        assertArrayEquals(new double[]{60, 30}, ImageController.initialSize(16, 8), "minúscula: ampliada");
    }

    // ------------------------------------------------------------ redimensionar

    @Test
    void resizingAnImageAlwaysKeepsItsProportionFromAnyCorner() {
        double ratio = 16.0 / 9;
        // Card na tela com legenda: 320 × 230 (imagem 320 × 180 + 50 de legenda).
        Box shown = new Box(100, 100, 320, 230);
        for (ResizeHandle handle : List.of(ResizeHandle.NORTH_WEST, ResizeHandle.NORTH_EAST, ResizeHandle.SOUTH_EAST, ResizeHandle.SOUTH_WEST)) {
            BoxResizer.Result result = CardResizeController.resizeImage(shown, ratio, handle, 77, -31, List.of(), 0);
            Box image = result.box();
            assertEquals(ratio, image.width() / image.height(), 1e-9, handle.name());
        }
    }

    @Test
    void resizingTheBottomRightCornerKeepsTheTopLeftOfTheImage() {
        Box shown = new Box(100, 100, 320, 230);

        Box image = CardResizeController.resizeImage(shown, 16.0 / 9, ResizeHandle.SOUTH_EAST, 160, 0, List.of(), 0).box();

        assertEquals(100, image.x(), 1e-9);
        assertEquals(100, image.y(), 1e-9);
        assertEquals(480, image.width(), 1e-9);
        assertEquals(270, image.height(), 1e-9);
    }

    @Test
    void resizingTheTopLeftCornerKeepsTheImageBottomRight() {
        Box shown = new Box(100, 100, 320, 230);

        Box image = CardResizeController.resizeImage(shown, 16.0 / 9, ResizeHandle.NORTH_WEST, -160, 0, List.of(), 0).box();

        assertEquals(480, image.width(), 1e-9);
        assertEquals(420, image.x() + image.width(), 1e-9);
        assertEquals(280, image.y() + image.height(), 1e-9, "base da imagem (100 + 180) parada");
    }

    @Test
    void veryWideImageCanShrinkUntilItsLongestSideHitsTheMinimum() {
        double ratio = 20; // panorama 20:1
        Box shown = new Box(0, 0, 400, 20);

        Box image = CardResizeController.resizeImage(shown, ratio, ResizeHandle.SOUTH_EAST, -1000, 0, List.of(), 0).box();

        assertEquals(CardResizeController.MIN_IMAGE_SIDE, image.width(), 1e-9);
        assertEquals(CardResizeController.MIN_IMAGE_SIDE / ratio, image.height(), 1e-9);
    }
}

package com.danilo.boardvisual.persistence;

import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As imagens dos cards copiados (3.25 do DOCUMENTACAO.md): guardadas ao copiar,
 * entregues ao colar só se o board de destino ainda não as tem.
 */
class ClipboardImagesTest {

    private final ClipboardImages images = new ClipboardImages();

    @AfterEach
    void cleanUp() {
        images.discard();
    }

    private static Path write(Path dir, String name, int... bytes) throws IOException {
        Files.createDirectories(dir);
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) bytes[i];
        }
        Path file = dir.resolve(name);
        Files.write(file, data);
        return file;
    }

    private static List<String> names(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void pastingInTheSameSaveReusesTheFileWithoutCopying(@TempDir Path root) throws IOException {
        Path saveImages = BoardImages.dirOf(root.resolve("save"));
        write(saveImages, "a.png", 1, 2, 3);
        images.keep(Set.of("a.png"), List.of(saveImages));
        Path work = root.resolve("trabalho");

        List<String> missing = images.deliver(Set.of("a.png"), List.of(saveImages), () -> work);

        assertEquals(List.of(), missing);
        assertFalse(Files.exists(work), "o arquivo já está no save: nada é copiado");
        assertEquals(List.of("a.png"), names(saveImages));
    }

    @Test
    void pastingInAnotherSaveCopiesTheFileIntoTheWorkFolder(@TempDir Path root) throws IOException {
        Path sourceImages = BoardImages.dirOf(root.resolve("origem"));
        write(sourceImages, "a.png", 1, 2, 3);
        write(sourceImages, "b.jpg", 9);
        images.keep(Set.of("a.png", "b.jpg"), List.of(sourceImages));
        Path destinationImages = BoardImages.dirOf(root.resolve("destino"));
        Path work = root.resolve("trabalho");

        List<String> missing = images.deliver(Set.of("a.png", "b.jpg"), List.of(destinationImages), () -> work);

        assertEquals(List.of(), missing);
        assertEquals(List.of("a.png", "b.jpg"), names(work), "o mesmo nome: os cards colados apontam para ele");
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(work.resolve("a.png")));
    }

    @Test
    void onlyTheMissingFilesAreCopiedWhenSomeAlreadyExist(@TempDir Path root) throws IOException {
        Path sourceImages = BoardImages.dirOf(root.resolve("origem"));
        write(sourceImages, "a.png", 1);
        write(sourceImages, "b.png", 2);
        images.keep(Set.of("a.png", "b.png"), List.of(sourceImages));
        Path destinationImages = BoardImages.dirOf(root.resolve("destino"));
        write(destinationImages, "a.png", 1); // este já está no destino
        Path work = root.resolve("trabalho");

        images.deliver(Set.of("a.png", "b.png"), List.of(destinationImages), () -> work);

        assertEquals(List.of("b.png"), names(work));
    }

    @Test
    void theKeptFilesSurviveTheSourceBeingChangedOrDeleted(@TempDir Path root) throws IOException {
        Path sourceImages = BoardImages.dirOf(root.resolve("origem"));
        Path original = write(sourceImages, "a.png", 7, 7);
        images.keep(Set.of("a.png"), List.of(sourceImages));

        Files.delete(original); // ex.: a limpeza ao salvar, ou outro save aberto no lugar
        Path work = root.resolve("trabalho");
        List<String> missing = images.deliver(Set.of("a.png"), List.of(BoardImages.dirOf(root.resolve("outro"))), () -> work);

        assertEquals(List.of(), missing);
        assertArrayEquals(new byte[] {7, 7}, Files.readAllBytes(work.resolve("a.png")));
    }

    @Test
    void aFileThatWasAlreadyMissingWhenCopyingStaysMissing(@TempDir Path root) throws IOException {
        Path sourceImages = BoardImages.dirOf(root.resolve("origem"));
        write(sourceImages, "a.png", 1);

        List<String> notKept = images.keep(Set.of("a.png", "sumiu.png"), List.of(sourceImages));
        List<String> missing = images.deliver(Set.of("a.png", "sumiu.png"),
                List.of(BoardImages.dirOf(root.resolve("destino"))), () -> root.resolve("trabalho"));

        assertEquals(List.of("sumiu.png"), notKept);
        assertEquals(List.of("sumiu.png"), missing, "o card colado mostra o aviso de imagem não encontrada");
        assertEquals(List.of("a.png"), names(root.resolve("trabalho")));
    }

    @Test
    void searchesTheSourceFoldersInOrderIncludingTheWorkFolder(@TempDir Path root) throws IOException {
        Path work = root.resolve("trabalho"); // board ainda não salvo: a imagem só está na pasta de trabalho
        write(work, "nova.png", 5);
        Path saveImages = BoardImages.dirOf(root.resolve("save"));
        write(saveImages, "salva.png", 6);

        List<String> notKept = images.keep(Set.of("nova.png", "salva.png"), List.of(work, saveImages));

        assertEquals(List.of(), notKept);
        assertEquals(List.of("nova.png", "salva.png"), names(images.keptDir()));
    }

    @Test
    void aNewCopyReplacesWhatTheLastOneKept(@TempDir Path root) throws IOException {
        Path sourceImages = BoardImages.dirOf(root.resolve("origem"));
        write(sourceImages, "a.png", 1);
        write(sourceImages, "b.png", 2);

        images.keep(Set.of("a.png"), List.of(sourceImages));
        images.keep(Set.of("b.png"), List.of(sourceImages));

        assertEquals(List.of("b.png"), names(images.keptDir()));
    }

    @Test
    void copyingWithoutImagesKeepsNothingAndDiscardRemovesTheFolder(@TempDir Path root) throws IOException {
        Path sourceImages = BoardImages.dirOf(root.resolve("origem"));
        write(sourceImages, "a.png", 1);
        images.keep(Set.of("a.png"), List.of(sourceImages));
        Path kept = images.keptDir();
        assertNotNull(kept);
        assertTrue(Files.isDirectory(kept));

        images.keep(Set.of(), List.of(sourceImages));
        assertNull(images.keptDir(), "cópia sem imagens: a pasta guardada some");
        assertFalse(Files.exists(kept));

        images.keep(Set.of("a.png"), List.of(sourceImages));
        Path again = images.keptDir();
        images.discard();
        assertFalse(Files.exists(again));
        assertNull(images.keptDir());
    }

    @Test
    void namesWithFoldersAreNeverReadOrCopied(@TempDir Path root) throws IOException {
        Path sourceImages = BoardImages.dirOf(root.resolve("origem"));
        write(sourceImages, "a.png", 1);
        write(root, "segredo.png", 1);

        List<String> notKept = images.keep(Set.of("../../segredo.png"), List.of(sourceImages));

        assertEquals(List.of("../../segredo.png"), notKept);
        assertNull(images.keptDir());
    }
}

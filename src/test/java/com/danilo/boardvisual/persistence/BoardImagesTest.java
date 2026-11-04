package com.danilo.boardvisual.persistence;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Usa arquivos falsos (alguns bytes): a pasta imagens/ não se importa com o conteúdo. */
class BoardImagesTest {

    private static Path fakeImage(Path imagesDir, String name, int... bytes) throws IOException {
        Files.createDirectories(imagesDir);
        byte[] content = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            content[i] = (byte) bytes[i];
        }
        return Files.write(imagesDir.resolve(name), content);
    }

    private static List<String> namesIn(Path dir) throws IOException {
        try (Stream<Path> contents = Files.list(dir)) {
            return contents.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    // ------------------------------------------------------------ achar

    @Test
    void findsTheFileInTheFirstFolderThatHasIt(@TempDir Path dir) throws IOException {
        Path work = dir.resolve("trabalho");
        Path saved = dir.resolve("save").resolve("imagens");
        fakeImage(work, "nova.png", 1);
        fakeImage(saved, "antiga.png", 2);
        fakeImage(saved, "nova.png", 3);

        assertEquals(work.resolve("nova.png"), BoardImages.find("nova.png", List.of(work, saved)).orElseThrow());
        assertEquals(saved.resolve("antiga.png"), BoardImages.find("antiga.png", List.of(work, saved)).orElseThrow());
        assertTrue(BoardImages.find("nenhuma.png", List.of(work, saved)).isEmpty());
    }

    @Test
    void namesWithFoldersAreNeverLookedUp(@TempDir Path dir) throws IOException {
        Path images = dir.resolve("save").resolve("imagens");
        fakeImage(dir.resolve("save"), "segredo.png", 1);
        Files.createDirectories(images);

        assertTrue(BoardImages.find("../segredo.png", List.of(images)).isEmpty());
        assertTrue(BoardImages.find("..\\segredo.png", List.of(images)).isEmpty());
        assertFalse(BoardImages.isPlainFileName(".."));
        assertFalse(BoardImages.isPlainFileName("C:segredo.png"));
        assertFalse(BoardImages.isPlainFileName(null));
        assertTrue(BoardImages.isPlainFileName("3f2a-1b.png"));
    }

    // ------------------------------------------------------------ limpeza

    @Test
    void removesOnlyFilesNoCardReferences(@TempDir Path folder) throws IOException {
        Path images = BoardImages.dirOf(folder);
        fakeImage(images, "usada.png", 1);
        fakeImage(images, "solta.png", 2);
        fakeImage(images, "outra-solta.png", 3);

        List<String> removed = BoardImages.removeUnused(folder, Set.of("usada.png"));

        assertEquals(List.of("outra-solta.png", "solta.png"), removed);
        assertEquals(List.of("usada.png"), namesIn(images));
    }

    @Test
    void keepsEverythingWhenAllAreReferenced(@TempDir Path folder) throws IOException {
        Path images = BoardImages.dirOf(folder);
        fakeImage(images, "a.png", 1);
        fakeImage(images, "b.png", 2);

        assertEquals(List.of(), BoardImages.removeUnused(folder, Set.of("a.png", "b.png")));
        assertEquals(List.of("a.png", "b.png"), namesIn(images));
    }

    @Test
    void emptyReferencedSetRemovesEveryFile(@TempDir Path folder) throws IOException {
        Path images = BoardImages.dirOf(folder);
        fakeImage(images, "a.png", 1);

        assertEquals(List.of("a.png"), BoardImages.removeUnused(folder, Set.of()));
        assertEquals(List.of(), namesIn(images));
    }

    @Test
    void referencedNameThatIsNotOnDiskIsIgnored(@TempDir Path folder) throws IOException {
        Path images = BoardImages.dirOf(folder);
        fakeImage(images, "a.png", 1);

        assertEquals(List.of(), BoardImages.removeUnused(folder, Set.of("a.png", "sumiu.png")));
        assertEquals(List.of("a.png"), namesIn(images));
    }

    @Test
    void doesNothingWithoutAnImagesFolder(@TempDir Path folder) throws IOException {
        assertEquals(List.of(), BoardImages.removeUnused(folder, Set.of("a.png")));
        assertFalse(Files.exists(BoardImages.dirOf(folder)), "a limpeza não cria a pasta");
    }

    @Test
    void leavesSubfoldersAndTheRestOfTheSaveAlone(@TempDir Path folder) throws IOException {
        Path images = BoardImages.dirOf(folder);
        fakeImage(images, "solta.png", 1);
        fakeImage(images.resolve("subpasta"), "dentro.png", 2);
        Files.writeString(folder.resolve("board.json"), "{}");
        Files.writeString(folder.resolve("anotacao.txt"), "minha");

        BoardImages.removeUnused(folder, Set.of());

        assertTrue(Files.isRegularFile(images.resolve("subpasta").resolve("dentro.png")));
        assertTrue(Files.isRegularFile(folder.resolve("board.json")));
        assertTrue(Files.isRegularFile(folder.resolve("anotacao.txt")));
    }

    // ------------------------------------------------------------ cópia

    @Test
    void copiesFilesIntoTheSaveWithoutLeavingTemporaries(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("origem");
        fakeImage(source, "a.png", 1, 2, 3);
        fakeImage(source, "b.png", 4);
        Path folder = dir.resolve("Destino");

        BoardImages.copyInto(source, folder);

        Path images = BoardImages.dirOf(folder);
        assertEquals(List.of("a.png", "b.png"), namesIn(images));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(images.resolve("a.png")));
    }

    @Test
    void replacesAStaleCopyWithDifferentSize(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("origem");
        fakeImage(source, "a.png", 1, 2, 3);
        Path folder = dir.resolve("Destino");
        fakeImage(BoardImages.dirOf(folder), "a.png", 9);

        BoardImages.copyInto(source, folder);

        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(BoardImages.dirOf(folder).resolve("a.png")));
    }

    @Test
    void keepsAnIdenticalCopyAlreadyThere(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("origem");
        fakeImage(source, "a.png", 1, 2, 3);
        Path folder = dir.resolve("Destino");
        Path existing = fakeImage(BoardImages.dirOf(folder), "a.png", 7, 8, 9);
        long modifiedBefore = Files.getLastModifiedTime(existing).toMillis();

        BoardImages.copyInto(source, folder);

        // Mesmo nome e tamanho: considerada a mesma imagem (o nome é único), não é recopiada.
        assertArrayEquals(new byte[]{7, 8, 9}, Files.readAllBytes(existing));
        assertEquals(modifiedBefore, Files.getLastModifiedTime(existing).toMillis());
    }

    @Test
    void doesNotCreateTheImagesFolderWithoutImages(@TempDir Path dir) throws IOException {
        Path folder = dir.resolve("Destino");

        BoardImages.copyInto(null, folder);
        BoardImages.copyInto(dir.resolve("nao-existe"), folder);
        BoardImages.copyInto(Files.createDirectory(dir.resolve("vazia")), folder);

        assertFalse(Files.exists(BoardImages.dirOf(folder)));
    }

    @Test
    void copyingFromItsOwnFolderChangesNothing(@TempDir Path folder) throws IOException {
        Path images = BoardImages.dirOf(folder);
        fakeImage(images, "a.png", 1, 2, 3);

        BoardImages.copyInto(images, folder);

        assertEquals(List.of("a.png"), namesIn(images));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(images.resolve("a.png")));
    }
}

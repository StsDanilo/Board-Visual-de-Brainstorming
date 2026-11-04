package com.danilo.boardvisual.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Imagens reais, pequenas, geradas no próprio teste com o ImageIO. */
class ImageImporterTest {

    private static BufferedImage image(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width / 2, height / 2);
        g.dispose();
        return image;
    }

    private static Path write(Path dir, String name, String format, BufferedImage image) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        assertTrue(ImageIO.write(image, format, file.toFile()), "o JDK deveria gravar " + format);
        return file;
    }

    private static List<String> namesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static BufferedImage read(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        assertTrue(image != null, "não abriu " + file);
        return image;
    }

    @Test
    void smallPngIsCopiedByteForByteWithANewName(@TempDir Path dir) throws IOException {
        Path source = write(dir.resolve("origem"), "minha foto.png", "png", image(300, 200, BufferedImage.TYPE_INT_ARGB));
        Path target = dir.resolve("imagens");

        ImageImporter.Imported imported = ImageImporter.importFile(source, target);

        assertTrue(imported.fileName().endsWith(".png"));
        assertNotEquals("minha foto.png", imported.fileName());
        assertEquals(300, imported.width());
        assertEquals(200, imported.height());
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(target.resolve(imported.fileName())));
        assertEquals(List.of(imported.fileName()), namesIn(target), "sem temporários sobrando");
    }

    @Test
    void smallJpegKeepsItsBytesAndBecomesJpg(@TempDir Path dir) throws IOException {
        Path source = write(dir, "foto.jpeg", "jpeg", image(640, 480, BufferedImage.TYPE_INT_RGB));

        ImageImporter.Imported imported = ImageImporter.importFile(source, dir.resolve("imagens"));

        assertTrue(imported.fileName().endsWith(".jpg"));
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(dir.resolve("imagens").resolve(imported.fileName())));
    }

    @Test
    void largeImageIsReducedToTheLimitKeepingTheProportion(@TempDir Path dir) throws IOException {
        Path source = write(dir, "grande.jpg", "jpeg", image(4500, 1500, BufferedImage.TYPE_INT_RGB));

        ImageImporter.Imported imported = ImageImporter.importFile(source, dir.resolve("imagens"));

        assertEquals(ImageImporter.MAX_SIDE, imported.width());
        assertEquals(667, imported.height());
        assertTrue(imported.fileName().endsWith(".jpg"), "JPG continua JPG");
        BufferedImage stored = read(dir.resolve("imagens").resolve(imported.fileName()));
        assertEquals(ImageImporter.MAX_SIDE, stored.getWidth());
        assertEquals(667, stored.getHeight());
    }

    @Test
    void largePngStaysPngAndKeepsTransparency(@TempDir Path dir) throws IOException {
        BufferedImage transparent = new BufferedImage(1000, 3000, BufferedImage.TYPE_INT_ARGB); // tudo transparente
        Path source = write(dir, "alta.png", "png", transparent);

        ImageImporter.Imported imported = ImageImporter.importFile(source, dir.resolve("imagens"));

        assertEquals(667, imported.width());
        assertEquals(ImageImporter.MAX_SIDE, imported.height());
        BufferedImage stored = read(dir.resolve("imagens").resolve(imported.fileName()));
        assertTrue(stored.getColorModel().hasAlpha());
        assertEquals(0, stored.getRGB(10, 10) >>> 24, "continua transparente");
    }

    @Test
    void gifAndBmpBecomePng(@TempDir Path dir) throws IOException {
        Path gif = write(dir, "anim.gif", "gif", image(120, 80, BufferedImage.TYPE_INT_RGB));
        Path bmp = write(dir, "velha.bmp", "bmp", image(90, 60, BufferedImage.TYPE_INT_RGB));
        Path target = dir.resolve("imagens");

        ImageImporter.Imported fromGif = ImageImporter.importFile(gif, target);
        ImageImporter.Imported fromBmp = ImageImporter.importFile(bmp, target);

        assertTrue(fromGif.fileName().endsWith(".png"));
        assertTrue(fromBmp.fileName().endsWith(".png"));
        assertEquals(120, read(target.resolve(fromGif.fileName())).getWidth());
        assertEquals(60, read(target.resolve(fromBmp.fileName())).getHeight());
    }

    @Test
    void formatComesFromTheContentNotFromTheExtension(@TempDir Path dir) throws IOException {
        // Um JPG renomeado para .png continua sendo guardado como .jpg.
        Path source = write(dir, "engano.png", "jpeg", image(50, 40, BufferedImage.TYPE_INT_RGB));

        ImageImporter.Imported imported = ImageImporter.importFile(source, dir.resolve("imagens"));

        assertTrue(imported.fileName().endsWith(".jpg"));
    }

    @Test
    void corruptOrNonImageFileIsRefusedWithoutLeftovers(@TempDir Path dir) throws IOException {
        Path text = dir.resolve("nota.png");
        Files.writeString(text, "isto não é uma imagem");
        Path truncated = write(dir, "cortada.png", "png", image(200, 200, BufferedImage.TYPE_INT_RGB));
        byte[] bytes = Files.readAllBytes(truncated);
        Files.write(truncated, java.util.Arrays.copyOf(bytes, bytes.length / 3));
        Path target = dir.resolve("imagens");

        IOException notImage = assertThrows(IOException.class, () -> ImageImporter.importFile(text, target));
        assertTrue(notImage.getMessage().contains("nota.png"));
        assertThrows(IOException.class, () -> ImageImporter.importFile(truncated, target));
        assertThrows(IOException.class, () -> ImageImporter.importFile(dir.resolve("nao-existe.png"), target));

        assertFalse(Files.exists(target) && !namesIn(target).isEmpty(), "nada gravado em imagens/");
    }

    @Test
    void imageFromMemoryIsSavedAsPng(@TempDir Path dir) throws IOException {
        ImageImporter.Imported imported = ImageImporter.importImage(
                image(2400, 1200, BufferedImage.TYPE_INT_ARGB), dir.resolve("imagens"));

        assertTrue(imported.fileName().endsWith(".png"));
        assertEquals(ImageImporter.MAX_SIDE, imported.width());
        assertEquals(1000, imported.height());
        assertEquals(1000, read(dir.resolve("imagens").resolve(imported.fileName())).getHeight());
    }

    @Test
    void recognizesSupportedExtensions() {
        assertTrue(ImageImporter.isSupportedFile(Path.of("a.PNG")));
        assertTrue(ImageImporter.isSupportedFile(Path.of("pasta", "b.jpeg")));
        assertTrue(ImageImporter.isSupportedFile(Path.of("c.jpg")));
        assertTrue(ImageImporter.isSupportedFile(Path.of("d.gif")));
        assertTrue(ImageImporter.isSupportedFile(Path.of("e.bmp")));
        assertFalse(ImageImporter.isSupportedFile(Path.of("f.webp")));
        assertFalse(ImageImporter.isSupportedFile(Path.of("png")));
        assertFalse(ImageImporter.isSupportedFile(Path.of("notas.txt")));
    }
}

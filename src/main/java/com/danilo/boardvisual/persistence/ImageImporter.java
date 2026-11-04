package com.danilo.boardvisual.persistence;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Traz uma imagem para dentro de uma pasta de imagens (a {@code imagens/} do
 * save ou a pasta de trabalho de um board ainda não salvo), com nome novo e
 * único ({@code <uuid>.png} ou {@code .jpg}).
 *
 * Regras (DOCUMENTACAO.md, 3.17):
 * - Formatos aceitos: PNG, JPG, GIF e BMP, reconhecidos pelo conteúdo do
 *   arquivo (não pela extensão).
 * - PNG e JPG dentro do limite de {@link #MAX_SIDE} px: os bytes originais são
 *   copiados, sem recompressão.
 * - Maior que o limite: reduzido para {@link #MAX_SIDE} px no maior lado; JPG
 *   continua JPG (qualidade 0,9), o resto vira PNG.
 * - GIF vira PNG do primeiro quadro (a tela e o PDF mostram uma imagem parada);
 *   BMP vira PNG (o BMP não tem compressão e deixaria o save pesado).
 *
 * Usa o ImageIO, que já vem no módulo {@code java.desktop} (o mesmo do
 * {@link BufferedImage} entregue ao PDFBox): nenhum módulo novo.
 */
public final class ImageImporter {

    /** Maior lado, em pixels, de uma imagem guardada no save. */
    public static final int MAX_SIDE = 2000;
    /** Extensões oferecidas no seletor e aceitas ao arrastar arquivos. */
    public static final List<String> EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp");

    private static final float JPEG_QUALITY = 0.9f;

    /** Imagem guardada: nome do arquivo na pasta e tamanho em pixels. */
    public record Imported(String fileName, int width, int height) {
    }

    private ImageImporter() {
    }

    /** Se o arquivo tem uma das extensões aceitas (o conteúdo é conferido ao importar). */
    public static boolean isSupportedFile(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Importa um arquivo de imagem para {@code targetDir} (criada se preciso). */
    public static Imported importFile(Path source, Path targetDir) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("O arquivo \"" + source.getFileName() + "\" não foi encontrado.");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(source.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw unsupported(source);
            }
            ImageReader reader = readers.next();
            try {
                String format = formatOf(reader.getFormatName());
                if (format == null) {
                    throw unsupported(source);
                }
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // Imagens enormes: lê pulando pixels até ~2x o limite (economiza memória);
                // a redução final, suave, vem depois.
                int subsampling = Math.max(1, Math.max(width, height) / (2 * MAX_SIDE));
                ImageReadParam param = reader.getDefaultReadParam();
                if (subsampling > 1) {
                    param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }
                BufferedImage image = reader.read(0, param); // decodifica tudo: confere que não está corrompida

                boolean fits = width <= MAX_SIDE && height <= MAX_SIDE;
                if (fits && (format.equals("png") || format.equals("jpg"))) {
                    String name = newName(format);
                    copyAtomically(source, targetDir, name);
                    return new Imported(name, width, height);
                }
                return write(image, format.equals("jpg") ? "jpg" : "png", targetDir);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            // Leitores do ImageIO às vezes lançam exceções não verificadas com arquivos quebrados.
            throw new IOException("Não foi possível ler a imagem \"" + source.getFileName() + "\".", e);
        }
    }

    /** Importa uma imagem já na memória (ex.: colada da área de transferência) como PNG. */
    public static Imported importImage(BufferedImage image, Path targetDir) throws IOException {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("A imagem está vazia.");
        }
        return write(image, "png", targetDir);
    }

    // ------------------------------------------------------------ gravação

    private static Imported write(BufferedImage source, String format, Path targetDir) throws IOException {
        BufferedImage image = scaleDown(source, MAX_SIDE, format.equals("jpg"));
        String name = newName(format);
        Files.createDirectories(targetDir);
        Path temp = targetDir.resolve(name + ".tmp");
        try {
            if (format.equals("jpg")) {
                writeJpeg(image, temp);
            } else if (!ImageIO.write(image, "png", temp.toFile())) {
                throw new IOException("Não foi possível gravar a imagem.");
            }
            move(temp, targetDir.resolve(name));
        } finally {
            Files.deleteIfExists(temp);
        }
        return new Imported(name, image.getWidth(), image.getHeight());
    }

    private static void writeJpeg(BufferedImage image, Path file) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /**
     * Reduz até o maior lado caber em {@code maxSide} (nunca aumenta). Reduções
     * grandes vão em etapas de metade, para não serrilhar. JPG não tem
     * transparência: nesse caso a imagem sai sem canal alfa (fundo branco).
     */
    static BufferedImage scaleDown(BufferedImage source, int maxSide, boolean opaque) {
        int type = opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) maxSide / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage current = source;
        while (current.getWidth() > targetWidth
                && current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2, type);
        }
        // Sempre redesenha ao menos uma vez: também converte o tipo (ex.: GIF com paleta, JPG sem alfa).
        if (current == source || current.getWidth() != targetWidth || current.getHeight() != targetHeight) {
            current = draw(current, targetWidth, targetHeight, type);
        }
        return current;
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, int type) {
        BufferedImage result = new BufferedImage(width, height, type);
        Graphics2D g = result.createGraphics();
        try {
            if (type == BufferedImage.TYPE_INT_RGB) {
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, width, height);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return result;
    }

    // ------------------------------------------------------------ auxiliares

    /** Formato aceito ({@code png}, {@code jpg}, {@code gif}, {@code bmp}) ou {@code null}. */
    private static String formatOf(String readerFormat) {
        String format = readerFormat.toLowerCase(Locale.ROOT);
        return switch (format) {
            case "png", "gif", "bmp" -> format;
            case "jpeg", "jpg" -> "jpg";
            default -> null;
        };
    }

    private static IOException unsupported(Path source) {
        return new IOException("\"" + source.getFileName()
                + "\" não é uma imagem que o BoardVisual consiga abrir. Use PNG, JPG, GIF ou BMP.");
    }

    private static String newName(String extension) {
        return UUID.randomUUID() + "." + extension;
    }

    private static void copyAtomically(Path source, Path targetDir, String name) throws IOException {
        Files.createDirectories(targetDir);
        Path temp = targetDir.resolve(name + ".tmp");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            move(temp, targetDir.resolve(name));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

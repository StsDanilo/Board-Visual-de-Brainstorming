package com.danilo.boardvisual.export;

import com.danilo.boardvisual.alignment.Box;
import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.BoardNames;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Exporta um único board (o que está na tela) para uma imagem PNG.
 *
 * Diferente do PDF, não percorre os boards aninhados: é a "foto" de um board.
 * A área é a mesma do PDF ({@link PdfExporter#contentBounds}: os cards mais uma
 * margem), a 2 pixels por unidade do board, reduzida se passar dos limites
 * de tamanho. Fundo branco ou transparente ({@link Background}).
 *
 * Como no PDF, quem desenha é a tela (JavaFX), que chega aqui como um
 * {@link Renderer}: esta classe não depende do JavaFX e é testável sem janela.
 */
public final class PngExporter {

    /** Fundo da imagem. Branco é o padrão; transparente serve para colar sobre outra coisa. */
    public enum Background {
        WHITE, TRANSPARENT
    }

    /** Desenha a região {@code bounds} de um board (coordenadas do board). */
    @FunctionalInterface
    public interface Renderer {
        BufferedImage render(Board board, Box bounds, double pixelsPerUnit, Background background);
    }

    /** Tamanho da imagem e a escala usada para chegar a ele. */
    public record Plan(Box bounds, double pixelsPerUnit, int width, int height) {
    }

    /** Pixels por unidade do board (2 = nítido, como a imagem do PDF). */
    static final double RESOLUTION = 2;
    /**
     * Limites da imagem (os mesmos da imagem de cada board no PDF). Um board
     * maior é reduzido em vez de falhar: até 8.000 px no lado maior e 40 milhões
     * de pixels no total (~160 MB por cópia da imagem em memória; o snapshot,
     * a cópia em int[] e a BufferedImage existem ao mesmo tempo).
     */
    static final int MAX_SIDE = 8_000;
    static final long MAX_PIXELS = 40_000_000L;

    /** Nome curto o bastante para caber com folga no caminho de qualquer pasta do Windows. */
    static final int MAX_NAME_LENGTH = 120;
    private static final Set<String> RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private final Renderer renderer;

    public PngExporter(Renderer renderer) {
        this.renderer = renderer;
    }

    /**
     * Grava o PNG do board (num arquivo temporário e depois no lugar, como o
     * salvamento e o PDF: nunca fica um arquivo pela metade).
     *
     * @throws IllegalArgumentException se o board não tem cards (não há o que mostrar)
     */
    public void export(Board board, Path file, Background background) throws IOException {
        BufferedImage image = render(board, background);
        Path absolute = file.toAbsolutePath();
        Path temp = Files.createTempFile(absolute.getParent(), ".boardvisual-", ".png.tmp");
        try {
            if (!ImageIO.write(image, "png", temp.toFile())) {
                throw new IOException("Não há gravador de PNG disponível.");
            }
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Monta a imagem em memória (separado de {@link #export} para os testes). */
    BufferedImage render(Board board, Background background) {
        if (isEmpty(board)) {
            throw new IllegalArgumentException("O board está vazio: não há nada para exportar.");
        }
        Plan plan = plan(board);
        return renderer.render(board, plan.bounds(), plan.pixelsPerUnit(), background);
    }

    public static boolean isEmpty(Board board) {
        return board.getCards().isEmpty();
    }

    /** Área, escala e tamanho em pixels da imagem do board. */
    public static Plan plan(Board board) {
        Box bounds = PdfExporter.contentBounds(board);
        double scale = pixelsPerUnit(bounds);
        return new Plan(bounds, scale, pixels(bounds.width(), scale), pixels(bounds.height(), scale));
    }

    /** {@link #RESOLUTION}, ou menos se a imagem passaria de {@link #MAX_SIDE} ou {@link #MAX_PIXELS}. */
    static double pixelsPerUnit(Box bounds) {
        double bySide = MAX_SIDE / Math.max(bounds.width(), bounds.height());
        double byArea = Math.sqrt(MAX_PIXELS / (bounds.width() * bounds.height()));
        return Math.min(RESOLUTION, Math.min(bySide, byArea));
    }

    /**
     * Pixels de um lado: arredonda para cima (não cortar a borda), descontando
     * o erro de ponto flutuante (8000,0000001 é 8000). Mesma conta do
     * {@code BoardRenderer}, para o tamanho previsto ser o tamanho gerado.
     */
    public static int pixels(double units, double pixelsPerUnit) {
        return Math.max(1, (int) Math.ceil(units * pixelsPerUnit - 1e-6));
    }

    /**
     * Nome sugerido do arquivo: o caminho até o board na tela, com " - " entre os
     * níveis ("Projeto - Etapa 1.png"); no board principal, só o nome dele, como
     * no PDF. Caracteres que o Windows não aceita viram "_".
     *
     * @param path nomes do board principal até o board na tela
     */
    public static String suggestedFileName(List<String> path) {
        return baseFileName(path) + ".png";
    }

    /** O nome sugerido sem a extensão (o SVG usa o mesmo, com ".svg"). */
    static String baseFileName(List<String> path) {
        StringBuilder name = new StringBuilder();
        for (String level : path) {
            String clean = cleanName(level);
            if (clean.isEmpty()) {
                clean = BoardNames.UNTITLED;
            }
            if (!name.isEmpty()) {
                name.append(" - ");
            }
            name.append(clean);
        }
        String base = name.toString();
        if (base.length() > MAX_NAME_LENGTH) {
            base = base.substring(0, MAX_NAME_LENGTH);
        }
        base = stripTrailingDotsAndSpaces(base);
        if (base.isEmpty()) {
            base = BoardNames.UNTITLED;
        }
        if (RESERVED_NAMES.contains(base.toUpperCase(Locale.ROOT))) {
            base = base + "_";
        }
        return base;
    }

    /** O arquivo escolhido, com ".png" no fim se o usuário não digitou. */
    public static Path withPngExtension(Path chosen) {
        return withExtension(chosen, ".png");
    }

    /** O arquivo escolhido, com {@code extension} (".png", ".svg") no fim se o usuário não digitou. */
    static Path withExtension(Path chosen, String extension) {
        String name = chosen.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(extension) ? chosen : chosen.resolveSibling(name + extension);
    }

    private static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || "\\/:*?\"<>|".indexOf(c) >= 0) {
                clean.append('_');
            } else {
                clean.append(c);
            }
        }
        return clean.toString().strip();
    }

    private static String stripTrailingDotsAndSpaces(String name) {
        int end = name.length();
        while (end > 0 && (name.charAt(end - 1) == '.' || name.charAt(end - 1) == ' ')) {
            end--;
        }
        return name.substring(0, end);
    }
}

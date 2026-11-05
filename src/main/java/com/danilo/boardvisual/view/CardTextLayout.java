package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.ConnectionGeometry;
import com.danilo.boardvisual.model.Frames;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Onde fica cada linha de texto de um card (e do texto das setas), como a tela
 * desenha, para quem desenha o board sem JavaFX (a exportação SVG).
 *
 * Java puro: não usa a cena nem o toolkit do JavaFX. A medição do texto fica
 * atrás de {@link Metrics} (na aplicação, {@link ScreenTextMetrics}, que mede
 * com a fonte da tela; nos testes, uma medida fixa), como o {@link CardTextFit}.
 *
 * <b>Como a tela quebra as linhas</b> (e esta classe repete):
 * <ul>
 *   <li>o texto do card é um {@code TextArea} com quebra automática; o JavaFX
 *       quebra cada parágrafo (separado por Enter) no começo da palavra que
 *       passou da largura: só em espaços, não depois de hífen (os detalhes,
 *       inclusive os casos estranhos, estão em {@link #wrapParagraph});</li>
 *   <li>os espaços no fim da linha "penduram" (não empurram a quebra) e não
 *       aparecem, mas contam na hora de centralizar a linha;</li>
 *   <li>uma palavra maior que a largura inteira é cortada no meio, no último
 *       caractere que cabe (pelo menos um por linha);</li>
 *   <li>a largura da quebra é a da área do texto: o card menos a borda (1 px,
 *       só no retângulo: num formato com {@code -fx-shape} a borda não entra) e
 *       o recuo do formato ({@link CardGeometry#contentPadding}), com as posições
 *       arredondadas para pixel inteiro como no layout do JavaFX; cada linha é
 *       centralizada nela ({@code -fx-text-alignment: center} no app.css);</li>
 *   <li>a altura da linha e a linha de base são as dos limites
 *       {@code LOGICAL_VERTICAL_CENTER} (os do {@code TextArea} e do {@code Label});</li>
 *   <li>a fonte é a do {@link CardTextFit} (automática de 9 a 24 px, ou a
 *       manual; legenda de imagem 14 px; título de seção 15 px).</li>
 * </ul>
 * O título da seção e o texto da seta são rótulos de uma linha: o que não
 * cabe termina em {@link #ELLIPSIS}, como o {@code Label} do JavaFX. Cada regra
 * foi medida no app e é conferida contra o JavaFX de verdade
 * ({@code ScreenTextMetricsTest} e o roteiro de interface; DOCUMENTACAO.md, 4.6).
 */
public final class CardTextLayout {

    /** Mede texto na fonte dos cards. Estende o medidor do {@link CardTextFit}: os dois usam a mesma fonte. */
    public interface Metrics extends CardTextFit.TextMeasurer {

        /** Largura de um texto numa linha só (sem quebra), normal ou em negrito. */
        double width(String text, double fontSize, boolean bold);

        /**
         * Altura de uma linha (a distância entre as linhas de um texto quebrado),
         * como o JavaFX mede o texto do {@code TextArea} e dos {@code Label}: limites
         * {@code LOGICAL_VERTICAL_CENTER}, os mesmos do {@link CardTextFit}.
         */
        double lineHeight(double fontSize);

        /** Distância do topo da linha até a linha de base (onde as letras "assentam"). */
        double ascent(double fontSize);

        @Override
        default double wordWidth(String word, double fontSize) {
            return width(word, fontSize, false);
        }

        /** Altura do texto quebrado por esta classe (a medição de verdade da tela pode sobrescrever). */
        @Override
        default double textHeight(String text, double fontSize, double wrapWidth) {
            return wrap(this, text, fontSize, false, wrapWidth).size() * lineHeight(fontSize);
        }
    }

    /** Como a linha se alinha ao seu {@code x}: começa nele ou fica centralizada nele. */
    public enum Anchor {
        START, MIDDLE
    }

    /** Uma linha de texto: {@code x} conforme o {@link Anchor}, {@code baseline} = linha de base. */
    public record Line(String text, double x, double baseline) {
    }

    /** Um bloco de texto já posicionado (coordenadas do card, ou do board para o texto da seta). */
    public record Block(List<Line> lines, double fontSize, boolean bold, Anchor anchor) {

        static final Block EMPTY = new Block(List.of(), 0, false, Anchor.START);

        public Block {
            lines = List.copyOf(lines);
        }

        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }

    /** O texto de uma seta: a caixa com fundo (coordenadas do board) e o texto dentro dela. */
    public record LabelBox(double x, double y, double width, double height, Block text) {
    }

    /** Texto do aviso no lugar de uma imagem que não abre (o mesmo da tela). */
    public static final String MISSING_IMAGE_TEXT = "Imagem não encontrada";
    /** Reticências do {@code Label} do JavaFX ({@code ellipsisString} padrão). */
    public static final String ELLIPSIS = "...";

    // Medidas do app.css que decidem onde o texto fica.
    /** Borda do card ({@code .card}): o texto fica por dentro dela. */
    static final double CARD_BORDER = 1;
    /** Borda da seção ({@code .card.shape-frame}). */
    static final double FRAME_BORDER = 1.5;
    /** Fonte do texto das setas ({@code .connection-label}). */
    public static final double LABEL_FONT_SIZE = 12;
    /** Largura máxima do texto da seta (a {@code ConnectionView} usa a mesma); passou disso, termina em reticências. */
    public static final double LABEL_MAX_WIDTH = 220;
    /** Espaço em volta do texto da seta ({@code .connection-label}: 1 em cima e embaixo, 5 dos lados). */
    static final double LABEL_PADDING_X = 5;
    static final double LABEL_PADDING_Y = 1;
    /** Fonte e espaço do aviso de imagem não encontrada ({@code .card-image-missing}). */
    public static final double MISSING_IMAGE_FONT_SIZE = 12;
    static final double MISSING_IMAGE_PADDING = 6;
    private static final double IMAGE_INSET = CardGeometry.IMAGE_INSET;

    private final Metrics metrics;
    private final CardTextFit fit;

    public CardTextLayout(Metrics metrics) {
        this.metrics = metrics;
        this.fit = new CardTextFit(metrics);
    }

    public Metrics metrics() {
        return metrics;
    }

    // ------------------------------------------------------------ texto do card

    /**
     * O texto do card como aparece na exportação: o texto (ou a legenda, num
     * card de imagem) quebrado em linhas, ou o título de uma seção. Vazio se o
     * card não tem texto (o texto de exemplo não sai na exportação).
     */
    public Block cardText(Card card) {
        if (card.isFrame()) {
            return frameTitle(card);
        }
        String text = card.getText();
        if (text == null || text.isEmpty()) {
            return Block.EMPTY;
        }
        double size = fit.fit(card).fontSize();
        double width = card.getWidth();
        CardGeometry.Padding padding = CardGeometry.contentPadding(card.getShape(), width, card.getHeight(),
                card.isPanel() || card.hasChildBoard(), card.imageHeight(width), CardMarkers.of(card).size());
        // Como o layout do JavaFX (conferido no app): num formato com -fx-shape (elipse,
        // losango) a borda não entra no recuo; e o recuo é arredondado para pixel inteiro.
        double border = card.getShape() == CardShape.RECTANGLE ? CARD_BORDER : 0;
        double left = Math.round(border + padding.left());
        double right = Math.round(border + padding.right());
        double top = Math.round(border + padding.top());
        double wrapWidth = width - left - right;
        double centerX = left + Math.max(0, wrapWidth) / 2;
        return centered(wrapRaw(metrics, text, size, false, wrapWidth), size, centerX, top);
    }

    /** Fonte do texto do card (a mesma conta da tela, {@link CardTextFit}). */
    public double fontSize(Card card) {
        return fit.fit(card).fontSize();
    }

    /**
     * Título da seção: uma linha (as quebras viram espaço), em negrito, na faixa
     * do topo ({@link Frames#TITLE_HEIGHT}), centralizado na altura da faixa e
     * cortado com reticências antes dos marcadores. Seção sem título: nada
     * (o "Seção" esmaecido é só da tela).
     */
    Block frameTitle(Card card) {
        String title = card.getText().replaceAll("\\s*\\R\\s*", " ").strip();
        if (title.isEmpty()) {
            return Block.EMPTY;
        }
        double size = CardTextFit.FRAME_TITLE_FONT_SIZE;
        CardGeometry.Padding padding = CardGeometry.contentPadding(CardShape.FRAME, card.getWidth(), card.getHeight(),
                false, 0, CardMarkers.of(card).size());
        // O rótulo fica dentro da borda (1,5 px, arredondada para 2 pelo layout do JavaFX),
        // com o recuo do texto dos lados e o texto centralizado na altura da faixa.
        double inset = Math.round(FRAME_BORDER);
        double available = card.getWidth() - 2 * inset - padding.left() - padding.right();
        String shown = ellipsize(metrics, title, size, true, available);
        if (shown.isEmpty()) {
            return Block.EMPTY;
        }
        double band = Math.min(Frames.TITLE_HEIGHT, Math.max(0, card.getHeight() - 2 * inset));
        double baseline = inset + Math.round((band - metrics.lineHeight(size)) / 2) + metrics.ascent(size);
        return new Block(List.of(new Line(shown, inset + padding.left(), baseline)), size, true, Anchor.START);
    }

    /**
     * O aviso "Imagem não encontrada" no lugar da imagem: quebrado na largura
     * da área da imagem (menos o espaço em volta) e centralizado nela.
     */
    public Block missingImageText(Card card) {
        return imageNotice(card, MISSING_IMAGE_TEXT);
    }

    /** Um aviso no lugar da imagem, com o visual do "Imagem não encontrada" (ver {@link #missingImageText}). */
    public Block imageNotice(Card card, String message) {
        double boxWidth = Math.max(0, card.getWidth() - 2 * IMAGE_INSET);
        double boxHeight = Math.max(0, card.imageHeight(card.getWidth()) - 2 * IMAGE_INSET);
        double size = MISSING_IMAGE_FONT_SIZE;
        List<String> lines = wrapRaw(metrics, message, size, false, boxWidth - 2 * MISSING_IMAGE_PADDING);
        double height = lines.size() * metrics.lineHeight(size);
        // O rótulo ocupa a área da imagem e centraliza o texto (posição arredondada, como no JavaFX).
        double top = IMAGE_INSET
                + Math.round(MISSING_IMAGE_PADDING + (boxHeight - 2 * MISSING_IMAGE_PADDING - height) / 2);
        return centered(lines, size, IMAGE_INSET + boxWidth / 2, top);
    }

    // ------------------------------------------------------------ texto da seta

    /**
     * Caixa do texto da seta, em coordenadas do board, ou {@code null} se a seta
     * não tem texto. Como a tela: uma linha, até {@link #LABEL_MAX_WIDTH}
     * de largura (o resto vira reticências), no meio da seta (ou afastada da seta
     * contrária, {@link ConnectionGeometry#labelCenter}).
     */
    public LabelBox connectionLabel(Connection connection, boolean reverse) {
        if (!connection.hasLabel()) {
            return null;
        }
        String label = connection.getLabel();
        double size = LABEL_FONT_SIZE;
        // O texto da seta anda por "translate" (não pelo layout): nada é arredondado aqui.
        double textWidth = metrics.width(label, size, false);
        double width = Math.min(LABEL_MAX_WIDTH, textWidth + 2 * LABEL_PADDING_X);
        String shown = textWidth + 2 * LABEL_PADDING_X > LABEL_MAX_WIDTH
                ? ellipsize(metrics, label, size, false, width - 2 * LABEL_PADDING_X)
                : label;
        double height = metrics.lineHeight(size) + 2 * LABEL_PADDING_Y;
        ConnectionGeometry.Point center = ConnectionGeometry.labelCenter(connection, width, height, reverse);
        double x = center.x() - width / 2;
        double y = center.y() - height / 2;
        Block text = new Block(List.of(new Line(shown, center.x(), y + LABEL_PADDING_Y + metrics.ascent(size))),
                size, false, Anchor.MIDDLE);
        return new LabelBox(x, y, width, height, text);
    }

    // ------------------------------------------------------------ utilitários

    /**
     * Linhas centralizadas em {@code centerX}, uma embaixo da outra a partir de
     * {@code top}. O JavaFX centraliza a linha <b>com</b> os espaços pendurados
     * no fim dela (conferido no app); a linha guardada vem sem eles, então o
     * meio dela anda para a esquerda metade da largura desses espaços.
     */
    private Block centered(List<String> rawLines, double size, double centerX, double top) {
        double lineHeight = metrics.lineHeight(size);
        double ascent = metrics.ascent(size);
        List<Line> placed = new ArrayList<>(rawLines.size());
        for (int i = 0; i < rawLines.size(); i++) {
            String raw = rawLines.get(i);
            String visible = raw.stripTrailing();
            double hanging = visible.length() == raw.length() ? 0
                    : metrics.width(raw, size, false) - metrics.width(visible, size, false);
            placed.add(new Line(visible, centerX - hanging / 2, top + i * lineHeight + ascent));
        }
        return new Block(placed, size, false, Anchor.MIDDLE);
    }

    /**
     * Quebra o texto em linhas de no máximo {@code wrapWidth}, como o JavaFX
     * quebra o texto de um {@code TextArea} (ver a descrição da classe). Cada
     * parágrafo (separado por quebra de linha) começa numa linha nova; um
     * parágrafo vazio é uma linha vazia. Largura 0 ou menos: sem quebra
     * automática (o JavaFX também não quebra).
     */
    public static List<String> wrap(Metrics metrics, String text, double fontSize, boolean bold, double wrapWidth) {
        List<String> lines = wrapRaw(metrics, text, fontSize, bold, wrapWidth);
        lines.replaceAll(String::stripTrailing);
        return lines;
    }

    /** Como {@link #wrap}, mas cada linha com os espaços pendurados no fim (sem a quebra de linha). */
    static List<String> wrapRaw(Metrics metrics, String text, double fontSize, boolean bold, double wrapWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        // Como o JavaFX: a regra de quebra vale para o texto inteiro (não por parágrafo).
        boolean complex = needsLineBreaker(text);
        for (String paragraph : text.split("\\R", -1)) {
            if (wrapWidth <= 0 || paragraph.isEmpty()) {
                lines.add(paragraph);
                continue;
            }
            wrapParagraph(metrics, paragraph, fontSize, bold, wrapWidth, complex, lines);
        }
        return lines;
    }

    /**
     * A regra do JavaFX 21 ({@code PrismTextLayout}), conferida contra o JavaFX
     * de verdade no {@code ScreenTextMetricsTest}:
     * <ol>
     *   <li>acha o primeiro caractere que passa da largura;</li>
     *   <li>se ele é um espaço (só o espaço comum), anda um caractere;</li>
     *   <li>texto comum: volta até o começo da palavra (um não-espaço logo depois
     *       de um espaço). Atenção: parado num espaço, volta até o começo da
     *       palavra anterior. Texto com escrita complexa, emoji colorido ou
     *       ideogramas: usa a última oportunidade de quebra do {@link BreakIterator};</li>
     *   <li>sem oportunidade de quebra na linha: corta onde passou da largura
     *       (pelo menos um caractere). Por isso um espaço a mais pode começar a linha seguinte.</li>
     * </ol>
     * Os espaços do fim de cada linha "penduram" e não aparecem.
     */
    private static void wrapParagraph(Metrics metrics, String paragraph, double fontSize, boolean bold,
                                      double wrapWidth, boolean complex, List<String> lines) {
        BreakIterator breaks = null;
        if (complex) {
            breaks = BreakIterator.getLineInstance();
            breaks.setText(paragraph);
        }
        int length = paragraph.length();
        int start = 0;
        while (start < length) {
            int hit = longestFit(metrics, paragraph, start, fontSize, bold, wrapWidth);
            if (hit >= length) {
                lines.add(paragraph.substring(start));
                return;
            }
            if (hit > start && Character.isLowSurrogate(paragraph.charAt(hit))
                    && Character.isHighSurrogate(paragraph.charAt(hit - 1))) {
                hit--; // o par de surrogates é um caractere só: vai inteiro para a linha seguinte
            }
            int offset = hit;
            if (offset + 1 < length && paragraph.charAt(offset) == ' ') {
                offset++;
            }
            int end;
            if (breaks != null) {
                end = breaks.isBoundary(offset) || paragraph.charAt(offset) == '\t'
                        ? offset : breaks.preceding(offset);
            } else {
                end = offset;
                boolean current = Character.isWhitespace(paragraph.charAt(end));
                while (end > start) {
                    boolean previous = Character.isWhitespace(paragraph.charAt(end - 1));
                    if (!current && previous) {
                        break;
                    }
                    current = previous;
                    end--;
                }
            }
            if (end <= start) {
                // Sem onde quebrar: corta a palavra onde ela passou da largura.
                end = hit > start ? hit : start + Character.charCount(paragraph.codePointAt(start));
            }
            lines.add(paragraph.substring(start, end));
            start = end;
        }
    }

    /**
     * Se o JavaFX quebraria este texto pelo {@link BreakIterator} (e não pela
     * regra dos espaços): quando há escrita complexa (acentos combinados,
     * hebraico, árabe, escritas indianas, tailandês...), caracteres fora do
     * plano básico (emoji) ou ideogramas (chinês, japonês). Aproxima o que o
     * {@code GlyphLayout} do JavaFX marca no texto (ele ainda olha a fonte para
     * emoji colorido do plano básico, o que esta classe não sabe).
     */
    static boolean needsLineBreaker(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp > 0xFFFF || Character.isIdeographic(cp) || isComplex(cp)) {
                return true;
            }
        }
        return false;
    }

    /** Faixas de escrita complexa do JavaFX/JDK ({@code ScriptMapper.isComplexCharCode}). */
    private static boolean isComplex(int cp) {
        return (cp >= 0x0300 && cp <= 0x036F)      // acentos combinados
                || (cp >= 0x0590 && cp <= 0x06FF)  // hebraico, árabe
                || (cp >= 0x0900 && cp <= 0x0E7F)  // escritas indianas, tailandês, laosiano
                || (cp >= 0x0F00 && cp <= 0x109F)  // tibetano, birmanês
                || (cp >= 0x1100 && cp <= 0x11FF)  // jamo do hangul
                || (cp >= 0x1780 && cp <= 0x17FF)  // khmer
                || cp == 0x200C || cp == 0x200D    // ZWNJ, ZWJ
                || (cp >= 0x202A && cp <= 0x202E)  // controles de direção
                || (cp >= 0x206A && cp <= 0x206F);
    }

    /**
     * Maior {@code end} tal que {@code paragraph[start, end)} cabe em {@code wrapWidth}
     * ({@code start} se nem o primeiro caractere cabe). Busca exponencial e depois
     * binária: o número de medições não depende do tamanho do texto inteiro.
     */
    private static int longestFit(Metrics metrics, String paragraph, int start, double fontSize, boolean bold,
                                  double wrapWidth) {
        int length = paragraph.length();
        int fits = start;
        int step = 16;
        int tooLong = -1;
        while (tooLong < 0) {
            int probe = Math.min(length, start + step);
            if (metrics.width(paragraph.substring(start, probe), fontSize, bold) <= wrapWidth) {
                fits = probe;
                if (probe == length) {
                    return length;
                }
                step *= 2;
            } else {
                tooLong = probe;
            }
        }
        while (tooLong - fits > 1) {
            int middle = (fits + tooLong) >>> 1;
            if (metrics.width(paragraph.substring(start, middle), fontSize, bold) <= wrapWidth) {
                fits = middle;
            } else {
                tooLong = middle;
            }
        }
        return fits;
    }

    /**
     * O texto inteiro se cabe em {@code available}; senão, o maior começo dele
     * seguido de {@link #ELLIPSIS} que cabe; vazio se nem as reticências cabem.
     */
    public static String ellipsize(Metrics metrics, String text, double fontSize, boolean bold, double available) {
        if (metrics.width(text, fontSize, bold) <= available) {
            return text;
        }
        if (metrics.width(ELLIPSIS, fontSize, bold) > available) {
            return "";
        }
        int fits = 0;
        int tooLong = text.length();
        while (tooLong - fits > 1) {
            int middle = (fits + tooLong) >>> 1;
            if (metrics.width(text.substring(0, middle) + ELLIPSIS, fontSize, bold) <= available) {
                fits = middle;
            } else {
                tooLong = middle;
            }
        }
        if (fits > 0 && Character.isHighSurrogate(text.charAt(fits - 1))) {
            fits--; // não corta um par de surrogates ao meio
        }
        return text.substring(0, fits) + ELLIPSIS;
    }
}

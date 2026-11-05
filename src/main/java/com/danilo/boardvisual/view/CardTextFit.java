package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardMarkers;
import com.danilo.boardvisual.model.CardShape;
import javafx.geometry.Insets;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

import java.util.List;

/**
 * Calcula a fonte e o tamanho de um card para o texto caber inteiro, sem
 * rolagem (o que aparece na tela é o que vai para o PDF).
 *
 * - Fonte automática: a maior entre {@link #MIN_FONT_SIZE} e
 *   {@link #MAX_FONT_SIZE} em que o texto cabe no tamanho definido à mão.
 * - Se nem a fonte mínima (ou a fonte manual) cabe, o card cresce: o retângulo
 *   só na altura; elipse e losango mantendo a proporção.
 * - Marcadores na borda de cima (link): o texto começa abaixo deles.
 * - Card de imagem: o texto é a legenda, abaixo da imagem. Sem legenda nada
 *   muda; com legenda (fonte {@link #CAPTION_FONT_SIZE} ou a manual), o card
 *   cresce embaixo da imagem, que mantém a proporção.
 *
 * O cálculo parte sempre do tamanho definido à mão, então apagar texto faz o
 * card voltar a ele. A medição fica atrás de {@link TextMeasurer}: na tela usa
 * as métricas de fonte do JavaFX; nos testes, uma medida fixa e previsível.
 */
public final class CardTextFit {

    public static final double MIN_FONT_SIZE = 9;
    public static final double MAX_FONT_SIZE = 24;
    /**
     * Fonte "automática" da legenda de um card de imagem. Lá o tamanho definido
     * à mão é o da imagem e não sobra espaço para o texto (o card sempre cresce
     * para a legenda), então "a maior fonte que cabe" não faz sentido: usa-se um
     * tamanho fixo de legenda.
     */
    public static final double CAPTION_FONT_SIZE = 14;
    /**
     * Fonte do título de uma seção (frame): fixa. O título nunca faz a seção
     * crescer nem muda de fonte; título longo é cortado com reticências na tela.
     */
    public static final double FRAME_TITLE_FONT_SIZE = 15;
    /** Tamanhos oferecidos na escolha manual. */
    public static final List<Integer> MANUAL_FONT_SIZES = List.of(10, 12, 14, 16, 18, 20, 24, 28, 32, 40, 48);

    /** Borda do card ({@code .card} no app.css): fica entre o card e a área do texto. */
    private static final double CARD_BORDER = 1;
    /** Folga para arredondamentos do TextArea (evita a barra de rolagem aparecer por 1px). */
    private static final double SLACK = 3;
    /**
     * Largura da barra de rolagem do texto (app.css). A quebra de linha é medida
     * como se ela estivesse lá: se ela aparecer por um instante (ex.: no primeiro
     * layout), estreita o texto; medindo sem ela, o texto passaria a precisar
     * dela e ela nunca mais sumiria.
     */
    private static final double SCROLLBAR_WIDTH = 6;
    /** Precisão da busca da fonte automática, em px. */
    private static final double FONT_STEP = 0.5;
    /** Limite de crescimento de elipse e losango (evita laço infinito com texto absurdo). */
    private static final double MAX_SCALE = 64;

    /** Mede texto numa fonte. */
    public interface TextMeasurer {
        /** Altura do texto quebrado em linhas de largura {@code wrapWidth}. */
        double textHeight(String text, double fontSize, double wrapWidth);

        /** Largura de uma palavra, sem quebra. */
        double wordWidth(String word, double fontSize);
    }

    /** Fonte a usar e tamanho exibido do card. */
    public record Fit(double fontSize, double width, double height) {
    }

    private final TextMeasurer measurer;

    public CardTextFit(TextMeasurer measurer) {
        this.measurer = measurer;
    }

    /** Instância que mede com a fonte usada pelos cards na tela. */
    public static CardTextFit forScreen() {
        return ScreenHolder.INSTANCE;
    }

    /** Fonte e tamanho do card, partindo do tamanho definido à mão. */
    public Fit fit(Card card) {
        return fit(card, card.getBaseWidth(), card.getBaseHeight());
    }

    /** Como {@link #fit(Card)}, mas partindo de outro tamanho (ex.: durante o redimensionamento). */
    public Fit fit(Card card, double width, double height) {
        return fit(card, width, height, card.getText());
    }

    /**
     * Como {@link #fit(Card, double, double)}, medindo outro texto (ex.: o texto
     * de exemplo da legenda enquanto ela é editada vazia).
     */
    public Fit fit(Card card, double width, double height, String text) {
        double imageRatio = card.hasImage() && card.getBaseWidth() > 0 ? card.getBaseHeight() / card.getBaseWidth() : 0;
        return fit(card.getShape(), width, height, card.isPanel() || card.hasChildBoard(),
                imageRatio, CardMarkers.of(card).size(), text, card.getFontSize());
    }

    /** Card sem imagem e sem marcadores (ver o método completo abaixo). */
    public Fit fit(CardShape shape, double width, double height, boolean hasButton, String text, double fontSize) {
        return fit(shape, width, height, hasButton, 0, 0, text, fontSize);
    }

    /** Card sem marcadores (ver o método completo abaixo). */
    public Fit fit(CardShape shape, double width, double height, boolean hasButton, double imageRatio,
                   String text, double fontSize) {
        return fit(shape, width, height, hasButton, imageRatio, 0, text, fontSize);
    }

    /**
     * @param hasButton  se o card tem botão embaixo (painel ou board aninhado), que ocupa espaço
     * @param imageRatio altura ÷ largura da imagem no topo do card; 0 = card sem imagem
     * @param markers    quantos marcadores o card tem em cima ({@link CardMarkers}): o texto fica abaixo deles
     * @param fontSize   tamanho manual, ou {@link Card#AUTO_FONT_SIZE}
     */
    public Fit fit(CardShape shape, double width, double height, boolean hasButton, double imageRatio,
                   int markers, String text, double fontSize) {
        if (shape == CardShape.FRAME) {
            // Seção: o tamanho é sempre o definido à mão; o título não a faz crescer.
            return new Fit(FRAME_TITLE_FONT_SIZE, width, height);
        }
        Frame frame = new Frame(shape, hasButton, imageRatio, markers);
        boolean empty = text == null || text.isEmpty();
        if (imageRatio > 0) {
            // Card de imagem: o tamanho definido à mão é o da imagem, sem espaço para texto.
            // Sem legenda não há o que encaixar; com legenda, o card cresce embaixo da imagem.
            double font = fontSize > 0 ? fontSize : CAPTION_FONT_SIZE;
            if (empty || fits(frame, width, height, text, font)) {
                return new Fit(font, width, height);
            }
            return grow(frame, width, height, text, font);
        }
        if (fontSize <= 0) {
            // Vazio: a fonte se ajusta ao texto de exemplo, mas ele nunca faz o card crescer.
            String measured = empty ? CardView.PROMPT_TEXT : text;
            double auto = largestAutoFont(frame, width, height, measured);
            if (!Double.isNaN(auto)) {
                return new Fit(auto, width, height);
            }
            if (empty) {
                return new Fit(MIN_FONT_SIZE, width, height);
            }
            return grow(frame, width, height, text, MIN_FONT_SIZE);
        }
        if (empty || fits(frame, width, height, text, fontSize)) {
            return new Fit(fontSize, width, height);
        }
        return grow(frame, width, height, text, fontSize);
    }

    /**
     * O que, além do tamanho, decide o espaço do texto: formato, botão embaixo,
     * imagem em cima e marcadores na borda de cima.
     */
    private record Frame(CardShape shape, boolean button, double imageRatio, int markers) {
        Insets insets(double w, double h) {
            return CardGeometry.contentInsets(shape, w, h, button, w * imageRatio, markers);
        }
    }

    /** Maior fonte automática em que o texto cabe, ou NaN se nem a mínima cabe. */
    private double largestAutoFont(Frame frame, double w, double h, String text) {
        if (fitsAuto(frame, w, h, text, MAX_FONT_SIZE)) {
            return MAX_FONT_SIZE;
        }
        if (!fitsAuto(frame, w, h, text, MIN_FONT_SIZE)) {
            return Double.NaN;
        }
        double lo = MIN_FONT_SIZE; // cabe
        double hi = MAX_FONT_SIZE; // não cabe
        while (hi - lo > FONT_STEP) {
            double mid = Math.floor((lo + hi) / 2 / FONT_STEP) * FONT_STEP;
            if (mid <= lo) {
                break;
            }
            if (fitsAuto(frame, w, h, text, mid)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * No automático, além da altura, cada palavra tem que caber inteira na
     * largura (senão a fonte grande quebraria palavras no meio).
     */
    private boolean fitsAuto(Frame frame, double w, double h, String text, double font) {
        if (!fits(frame, w, h, text, font)) {
            return false;
        }
        double available = textArea(frame, w, h)[0];
        for (String word : text.split("\\s+")) {
            if (!word.isEmpty() && measurer.wordWidth(word, font) > available) {
                return false;
            }
        }
        return true;
    }

    private boolean fits(Frame frame, double w, double h, String text, double font) {
        double[] area = textArea(frame, w, h);
        if (area[0] <= 1 || area[1] <= 0) {
            return false;
        }
        return measurer.textHeight(text, font, area[0]) <= area[1];
    }

    /**
     * Cresce o card até o texto caber na fonte informada. Num card de imagem a
     * imagem acompanha a largura (a altura dela é largura × proporção), então
     * crescer só na altura dá espaço à legenda sem distorcer a imagem.
     */
    private Fit grow(Frame frame, double w, double h, String text, double font) {
        if (frame.shape() == CardShape.RECTANGLE) {
            // No retângulo o recuo não depende da altura: dá para calcular direto.
            Insets insets = frame.insets(w, h);
            double wrapWidth = Math.max(1, w - insets.getLeft() - insets.getRight() - 2 * CARD_BORDER
                    - SCROLLBAR_WIDTH - SLACK);
            double needed = measurer.textHeight(text, font, wrapWidth) + SLACK
                    + insets.getTop() + insets.getBottom() + 2 * CARD_BORDER;
            return new Fit(font, w, Math.max(h, Math.ceil(needed)));
        }
        double lo = 1;       // não cabe
        double hi = 2;
        while (!fits(frame, w * hi, h * hi, text, font) && hi < MAX_SCALE) {
            lo = hi;
            hi *= 2;
        }
        while ((hi - lo) * w > 0.5) {
            double mid = (lo + hi) / 2;
            if (fits(frame, w * mid, h * mid, text, font)) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return new Fit(font, w * hi, h * hi);
    }

    /** Largura de quebra e altura disponíveis para o texto, já com a folga. */
    private static double[] textArea(Frame frame, double w, double h) {
        Insets insets = frame.insets(w, h);
        return new double[] {
                w - insets.getLeft() - insets.getRight() - 2 * CARD_BORDER - SCROLLBAR_WIDTH - SLACK,
                h - insets.getTop() - insets.getBottom() - 2 * CARD_BORDER - SLACK
        };
    }

    // ------------------------------------------------------------ medição real

    /**
     * Família da fonte dos cards: a primeira instalada da lista do app.css.
     * Resolvida só no primeiro uso (os testes não iniciam o JavaFX).
     */
    public static String fontFamily() {
        return FamilyHolder.FAMILY;
    }

    private static final class FamilyHolder {
        static final String FAMILY = resolveFamily("Segoe UI", "Helvetica Neue");
    }

    private static String resolveFamily(String... candidates) {
        List<String> installed = Font.getFamilies();
        for (String candidate : candidates) {
            if (installed.contains(candidate)) {
                return candidate;
            }
        }
        return Font.getDefault().getFamily();
    }

    /**
     * Mede com um {@link Text} fora da tela, configurado como o que o TextArea
     * desenha: um único Text com o texto inteiro, limites LOGICAL_VERTICAL_CENTER.
     */
    static final class JavaFxMeasurer implements TextMeasurer {

        private final Text text = new Text();

        JavaFxMeasurer() {
            text.setBoundsType(TextBoundsType.LOGICAL_VERTICAL_CENTER);
        }

        @Override
        public double textHeight(String value, double fontSize, double wrapWidth) {
            text.setFont(Font.font(fontFamily(), fontSize));
            text.setWrappingWidth(wrapWidth);
            text.setText(value);
            return text.getLayoutBounds().getHeight();
        }

        @Override
        public double wordWidth(String word, double fontSize) {
            text.setFont(Font.font(fontFamily(), fontSize));
            text.setWrappingWidth(0);
            text.setText(word);
            return text.getLayoutBounds().getWidth();
        }
    }

    private static final class ScreenHolder {
        static final CardTextFit INSTANCE = new CardTextFit(new JavaFxMeasurer());
    }
}

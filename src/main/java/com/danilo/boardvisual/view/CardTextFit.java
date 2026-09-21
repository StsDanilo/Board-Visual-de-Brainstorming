package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
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
 *
 * O cálculo parte sempre do tamanho definido à mão, então apagar texto faz o
 * card voltar a ele. A medição fica atrás de {@link TextMeasurer}: na tela usa
 * as métricas de fonte do JavaFX; nos testes, uma medida fixa e previsível.
 */
public final class CardTextFit {

    public static final double MIN_FONT_SIZE = 9;
    public static final double MAX_FONT_SIZE = 24;
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
        return fit(card.getShape(), width, height, card.isPanel() || card.hasChildBoard(),
                card.getText(), card.getFontSize());
    }

    /**
     * @param hasButton se o card tem botão embaixo (painel ou board aninhado), que ocupa espaço
     * @param fontSize  tamanho manual, ou {@link Card#AUTO_FONT_SIZE}
     */
    public Fit fit(CardShape shape, double width, double height, boolean hasButton, String text, double fontSize) {
        boolean empty = text == null || text.isEmpty();
        if (fontSize <= 0) {
            // Vazio: a fonte se ajusta ao texto de exemplo, mas ele nunca faz o card crescer.
            String measured = empty ? CardView.PROMPT_TEXT : text;
            double auto = largestAutoFont(shape, width, height, hasButton, measured);
            if (!Double.isNaN(auto)) {
                return new Fit(auto, width, height);
            }
            if (empty) {
                return new Fit(MIN_FONT_SIZE, width, height);
            }
            return grow(shape, width, height, hasButton, text, MIN_FONT_SIZE);
        }
        if (empty || fits(shape, width, height, hasButton, text, fontSize)) {
            return new Fit(fontSize, width, height);
        }
        return grow(shape, width, height, hasButton, text, fontSize);
    }

    /** Maior fonte automática em que o texto cabe, ou NaN se nem a mínima cabe. */
    private double largestAutoFont(CardShape shape, double w, double h, boolean button, String text) {
        if (fitsAuto(shape, w, h, button, text, MAX_FONT_SIZE)) {
            return MAX_FONT_SIZE;
        }
        if (!fitsAuto(shape, w, h, button, text, MIN_FONT_SIZE)) {
            return Double.NaN;
        }
        double lo = MIN_FONT_SIZE; // cabe
        double hi = MAX_FONT_SIZE; // não cabe
        while (hi - lo > FONT_STEP) {
            double mid = Math.floor((lo + hi) / 2 / FONT_STEP) * FONT_STEP;
            if (mid <= lo) {
                break;
            }
            if (fitsAuto(shape, w, h, button, text, mid)) {
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
    private boolean fitsAuto(CardShape shape, double w, double h, boolean button, String text, double font) {
        if (!fits(shape, w, h, button, text, font)) {
            return false;
        }
        double available = textArea(shape, w, h, button)[0];
        for (String word : text.split("\\s+")) {
            if (!word.isEmpty() && measurer.wordWidth(word, font) > available) {
                return false;
            }
        }
        return true;
    }

    private boolean fits(CardShape shape, double w, double h, boolean button, String text, double font) {
        double[] area = textArea(shape, w, h, button);
        if (area[0] <= 1 || area[1] <= 0) {
            return false;
        }
        return measurer.textHeight(text, font, area[0]) <= area[1];
    }

    /** Cresce o card até o texto caber na fonte informada. */
    private Fit grow(CardShape shape, double w, double h, boolean button, String text, double font) {
        if (shape == CardShape.RECTANGLE) {
            // No retângulo o recuo não depende da altura: dá para calcular direto.
            Insets insets = CardGeometry.contentInsets(shape, w, h, button);
            double wrapWidth = Math.max(1, w - insets.getLeft() - insets.getRight() - 2 * CARD_BORDER
                    - SCROLLBAR_WIDTH - SLACK);
            double needed = measurer.textHeight(text, font, wrapWidth) + SLACK
                    + insets.getTop() + insets.getBottom() + 2 * CARD_BORDER;
            return new Fit(font, w, Math.max(h, Math.ceil(needed)));
        }
        double lo = 1;       // não cabe
        double hi = 2;
        while (!fits(shape, w * hi, h * hi, button, text, font) && hi < MAX_SCALE) {
            lo = hi;
            hi *= 2;
        }
        while ((hi - lo) * w > 0.5) {
            double mid = (lo + hi) / 2;
            if (fits(shape, w * mid, h * mid, button, text, font)) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return new Fit(font, w * hi, h * hi);
    }

    /** Largura de quebra e altura disponíveis para o texto, já com a folga. */
    private static double[] textArea(CardShape shape, double w, double h, boolean button) {
        Insets insets = CardGeometry.contentInsets(shape, w, h, button);
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

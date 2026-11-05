package com.danilo.boardvisual.export;

import com.danilo.boardvisual.view.CardGeometry;
import com.danilo.boardvisual.view.CardTextLayout;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O SVG não lê o app.css (é escrito à mão): as cores, medidas e silhuetas que
 * ele usa são cópias. Este teste quebra se alguém mudar o app.css sem mudar a
 * cópia (ou o contrário), para a exportação continuar parecida com a tela.
 */
class SvgCssMirrorTest {

    private static String css;

    @BeforeAll
    static void readCss() throws IOException {
        try (InputStream in = SvgExporter.class.getResourceAsStream("/com/danilo/boardvisual/styles/app.css")) {
            assertNotNull(in, "app.css nos recursos");
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** O corpo da primeira regra cujo seletor é exatamente {@code selector}. */
    private static String rule(String selector) {
        Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)}").matcher(css);
        assertTrue(m.find(), "regra " + selector + " no app.css");
        return m.group(1);
    }

    private static void assertDeclares(String body, String declaration) {
        assertTrue(body.replaceAll("\\s+", " ").contains(declaration), "esperado \"" + declaration + "\" em: " + body);
    }

    @Test
    void shapePathsAreTheCssShapes() {
        assertDeclares(rule(".card.shape-ellipse"), "-fx-shape: \"" + CardGeometry.ELLIPSE_PATH + "\";");
        assertDeclares(rule(".card.shape-diamond"), "-fx-shape: \"" + CardGeometry.DIAMOND_PATH + "\";");
    }

    @Test
    void paletteColorsAreTheCssColors() {
        String root = rule(".root");
        assertDeclares(root, "-bv-text: " + SvgExporter.TEXT_COLOR + ";");
        assertDeclares(root, "-bv-text-muted: " + SvgExporter.MUTED_TEXT_COLOR + ";");
        assertDeclares(root, "-bv-border: " + SvgExporter.BORDER_COLOR + ";");
        assertDeclares(root, "-bv-border-strong: " + SvgExporter.BORDER_STRONG_COLOR + ";");
        assertDeclares(root, "-bv-surface: " + SvgExporter.SURFACE_COLOR + ";");
        assertDeclares(root, "-bv-accent: " + SvgExporter.ACCENT_COLOR + ";");
        assertDeclares(root, "-bv-danger: " + SvgExporter.DANGER_COLOR + ";");
        assertDeclares(root, "-bv-connection: " + SvgExporter.CONNECTION_COLOR + ";");
        assertEquals("'Segoe UI', 'Helvetica Neue', sans-serif", SvgExporter.FONT_FAMILY);
        assertDeclares(root, "-fx-font-family: \"Segoe UI\", \"Helvetica Neue\", sans-serif;");
    }

    @Test
    void cardMeasuresAreTheCssMeasures() {
        String card = rule(".card");
        assertDeclares(card, "-fx-background-radius: " + SvgExporter.num(SvgExporter.CARD_RADIUS) + ";");
        assertDeclares(card, "-fx-border-width: " + SvgExporter.num(SvgExporter.CARD_BORDER_WIDTH) + ";");
        assertDeclares(card, "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.08), 10, 0, 0, 2);");
        String frame = rule(".card.shape-frame");
        assertDeclares(frame, "-fx-border-width: " + SvgExporter.num(SvgExporter.FRAME_BORDER_WIDTH) + ";");
        assertDeclares(frame, "-fx-border-color: derive(-card-color, -30%);");
        String frameFill = rule(".frame-fill");
        assertDeclares(frameFill, "-fx-background-radius: " + SvgExporter.num(SvgExporter.FRAME_FILL_RADIUS) + ";");
        assertDeclares(frameFill, "-fx-opacity: " + SvgExporter.num(SvgExporter.FRAME_FILL_OPACITY) + ";");
        assertDeclares(rule(".card-view:has-board .card"), "-fx-background-insets: 6 -6 -6 6, 7 -5 -5 7, 0;");
        assertDeclares(rule(".card-image-missing"), "-fx-background-color: " + SvgExporter.MISSING_IMAGE_COLOR + ";");
        assertDeclares(rule(".card-image-missing"),
                "-fx-font-size: " + SvgExporter.num(CardTextLayout.MISSING_IMAGE_FONT_SIZE) + "px;");
    }

    @Test
    void arrowAndButtonMeasuresAreTheCssMeasures() {
        assertDeclares(rule(".connection-line"), "-fx-stroke-width: " + SvgExporter.num(SvgExporter.CONNECTION_WIDTH) + ";");
        assertDeclares(rule(".connection:dashed .connection-line"),
                "-fx-stroke-dash-array: " + SvgExporter.CONNECTION_DASHES + ";");
        String label = rule(".connection-label");
        assertDeclares(label, "-fx-background-radius: " + SvgExporter.num(SvgExporter.LABEL_RADIUS) + ";");
        assertDeclares(label, "-fx-padding: 1 5 1 5;");
        assertDeclares(label, "-fx-font-size: " + SvgExporter.num(CardTextLayout.LABEL_FONT_SIZE) + "px;");
        assertDeclares(rule(".board-export .connection-label"), "-fx-background-color: white;");
        assertDeclares(rule(".panel-button"), "-fx-pref-width: " + SvgExporter.num(SvgExporter.BUTTON_SIZE) + ";");
        assertDeclares(rule(".tool-icon"), "-fx-stroke-width: " + SvgExporter.num(SvgExporter.ICON_STROKE_WIDTH) + ";");
        assertDeclares(rule(".card-marker .tool-icon"),
                "-fx-stroke-width: " + SvgExporter.num(SvgExporter.MARKER_ICON_STROKE_WIDTH) + ";");
        assertDeclares(rule(".card-marker:broken"), "-fx-opacity: " + SvgExporter.num(SvgExporter.BROKEN_MARKER_OPACITY) + ";");
    }
}

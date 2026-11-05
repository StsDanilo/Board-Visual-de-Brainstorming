package com.danilo.boardvisual.view;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A quebra de linhas do {@link CardTextLayout} medindo com a fonte da tela
 * ({@link ScreenTextMetrics}) é a mesma que o JavaFX faz: as linhas de um
 * {@link Text} com quebra automática (o que o TextArea do card desenha) são
 * lidas pelo próprio JavaFX ({@code hitTest} no começo de cada linha) e
 * comparadas. Precisa do JavaFX (sem tela, o teste é pulado).
 */
class ScreenTextMetricsTest {

    @BeforeAll
    static void startJavaFx() {
        try {
            CompletableFuture<Void> started = new CompletableFuture<>();
            Platform.startup(() -> started.complete(null));
            started.get(10, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyStarted) {
            // outro teste já iniciou o JavaFX nesta JVM
        } catch (Exception | Error e) {
            Assumptions.abort("JavaFX indisponível neste ambiente: " + e);
        }
        Platform.setImplicitExit(false);
    }

    private static final List<String> TEXTS = List.of(
            "Uma ideia",
            "Uma ideia comprida que precisa de várias linhas para caber no card, com acentuação e pontuação.",
            "Palavra-composta bem-vinda, guarda-chuva e pré-requisito: hífens no meio das linhas.",
            "Supercalifragilisticexpialidocious_e_uma_palavra_enorme_sem_espacos",
            "Primeiro parágrafo\n\nTerceiro, depois de uma linha vazia\nQuarto",
            "Espaços    no    meio     e no fim       ",
            "Números 1.234,56 e (parênteses) [colchetes] / barras / 50% — travessão… reticências",
            "Linha com emoji 😀 e outros símbolos ★ ✓ no meio do texto que quebra");

    @Test
    void wrapMatchesTheLinesJavaFxDraws() throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                ScreenTextMetrics metrics = new ScreenTextMetrics();
                StringBuilder report = new StringBuilder();
                int compared = 0;
                for (double size : new double[] {9, 13, 14, 16.5, 24}) {
                    for (double width : new double[] {40, 90, 157, 178, 260}) {
                        for (String text : TEXTS) {
                            List<String> ours = CardTextLayout.wrap(metrics, text, size, false, width);
                            List<String> javaFx = javaFxLines(text, size, width);
                            compared++;
                            if (!ours.equals(javaFx)) {
                                report.append("\nfonte ").append(size).append(", largura ").append(width)
                                        .append(":\n  nossa:  ").append(ours).append("\n  JavaFX: ").append(javaFx);
                            }
                        }
                    }
                }
                result.complete(compared + " comparações" + report);
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        String report = result.get(60, TimeUnit.SECONDS);
        // O relatório também vai para um arquivo em UTF-8 (o console do Windows estraga os acentos).
        java.nio.file.Files.writeString(java.nio.file.Path.of("target", "quebra-de-linhas-javafx.txt"), report);
        assertTrue(report.endsWith("comparações"), "quebras diferentes do JavaFX: " + report);
    }

    /**
     * A altura da linha e a linha de base do texto do card são as do {@code Text}
     * que um {@code TextArea} de verdade (com o CSS dos cards) desenha; as dos
     * rótulos, as de um {@code Label} de verdade.
     */
    @Test
    void lineMetricsAreThoseOfTheRealTextAreaAndLabel() throws Exception {
        CompletableFuture<double[]> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                ScreenTextMetrics metrics = new ScreenTextMetrics();
                double size = 16;
                TextArea area = new TextArea("um\ndois\ntrês");
                area.setWrapText(true);
                area.setStyle(String.format(java.util.Locale.ROOT, "-fx-font-family: \"%s\"; -fx-font-size: %.1fpx;",
                        CardTextFit.fontFamily(), size));
                Label label = new Label("Rótulo");
                label.setStyle(area.getStyle());
                Scene scene = new Scene(new VBox(area, label), 300, 300);
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                Text inner = (Text) area.lookup(".text");
                double line1 = ((MoveTo) inner.caretShape(0, true)[0]).getY();
                double line2 = ((MoveTo) inner.caretShape(3, true)[0]).getY();
                Text labelText = (Text) label.lookup(".text");
                Bounds labelBounds = labelText.getLayoutBounds();
                result.complete(new double[] {
                        metrics.lineHeight(size), line2 - line1,
                        metrics.ascent(size), inner.getLayoutBounds().getMinY() + inner.getBaselineOffset() - line1,
                        labelBounds.getHeight(),
                        labelText.getBaselineOffset()}); // distância do topo da linha até a linha de base
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        double[] v = result.get(10, TimeUnit.SECONDS);
        assertEquals(v[1], v[0], 0.01, "distância entre as linhas do TextArea");
        assertEquals(v[3], v[2], 0.01, "linha de base da primeira linha do TextArea");
        assertEquals(v[4], v[0], 0.01, "o Label mede a linha igual ao TextArea");
        assertEquals(v[5], v[2], 0.01, "e põe a linha de base no mesmo lugar da linha");
    }

    /**
     * As linhas como o JavaFX quebrou: a altura do cursor ({@code caretShape})
     * antes de cada caractere diz em que linha ele está; uma linha nova começa
     * onde a altura muda. Cada linha vai até o começo da próxima, sem os espaços
     * (e a quebra) do fim.
     */
    private static List<String> javaFxLines(String value, double size, double width) {
        Text text = new Text(value);
        text.setFont(Font.font(CardTextFit.fontFamily(), size));
        text.setWrappingWidth(width);
        text.setBoundsType(TextBoundsType.LOGICAL_VERTICAL_CENTER); // como o TextArea (CardTextFit)
        text.setTextOrigin(VPos.TOP);
        List<Integer> starts = new ArrayList<>();
        double previousY = Double.NaN;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLowSurrogate(value.charAt(i))) {
                continue;
            }
            PathElement[] caret = text.caretShape(i, true);
            double y = ((MoveTo) caret[0]).getY();
            if (starts.isEmpty() || Math.abs(y - previousY) > 0.5) {
                starts.add(i);
                previousY = y;
            }
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : value.length();
            result.add(value.substring(starts.get(i), end).stripTrailing());
        }
        return result;
    }
}

package com.danilo.boardvisual.export;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Texto no PDF: fontes, medida e quebra de linha.
 *
 * Usa as fontes padrão do PDF (Helvetica), que todo leitor tem e não precisam
 * ser embutidas. Elas cobrem o português (acentos, ç) mas não tudo: caracteres
 * fora da tabela (ex.: emoji) viram "?".
 */
final class PdfText {

    static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final Map<Integer, Boolean> ENCODABLE = new HashMap<>();

    private PdfText() {
    }

    /** Troca o que a fonte não consegue escrever. */
    static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (cp == '\t') {
                out.append("    ");
            } else if (cp == '\r' || cp == '\n') {
                out.append(' ');
            } else if (canEncode(cp)) {
                out.appendCodePoint(cp);
            } else {
                out.append('?');
            }
        });
        return out.toString();
    }

    private static boolean canEncode(int codePoint) {
        return ENCODABLE.computeIfAbsent(codePoint, cp -> {
            try {
                // Regular e negrito usam a mesma tabela de caracteres.
                REGULAR.encode(new String(Character.toChars(cp)));
                return true;
            } catch (IllegalArgumentException | IOException e) {
                return false;
            }
        });
    }

    static float width(PDFont font, float size, String text) {
        try {
            return font.getStringWidth(sanitize(text)) / 1000 * size;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Corta com "…" para caber na largura. */
    static String ellipsize(PDFont font, float size, String text, float maxWidth) {
        String clean = sanitize(text);
        if (width(font, size, clean) <= maxWidth) {
            return clean;
        }
        int end = clean.length();
        while (end > 0 && width(font, size, clean.substring(0, end) + "…") > maxWidth) {
            end--;
        }
        return clean.substring(0, end).strip() + "…";
    }

    /**
     * Quebra um parágrafo em linhas de no máximo {@code maxWidth}. Palavras
     * maiores que a linha são cortadas. Parágrafo vazio vira uma linha vazia.
     */
    static List<String> wrap(PDFont font, float size, String paragraph, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : sanitize(paragraph).split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(font, size, candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            String rest = word;
            while (width(font, size, rest) > maxWidth && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && width(font, size, rest.substring(0, cut)) > maxWidth) {
                    cut--;
                }
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        lines.add(line.toString());
        return lines;
    }
}

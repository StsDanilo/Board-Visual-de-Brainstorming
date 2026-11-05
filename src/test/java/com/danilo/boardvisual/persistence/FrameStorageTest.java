package com.danilo.boardvisual.persistence;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seções (frames) no arquivo: é um formato de card ("shape": "FRAME"), sem
 * campo novo e sem mudar a versão; o conteúdo não é gravado (é geometria).
 */
class FrameStorageTest {

    private final BoardStorage storage = new BoardStorage();

    @Test
    void framesAreSavedAndOpenedBelowTheCards(@TempDir Path dir) throws IOException {
        Board board = new Board("Seções");
        Card frame = new Card("f", 0, 0);
        frame.setShape(CardShape.FRAME);
        frame.resize(500, 400);
        frame.setText("Pesquisa");
        frame.setColor("#D3F9D8");
        frame.setLocked(true);
        frame.setLink("exemplo.com");
        Card inside = new Card("a", 100, 100);
        inside.setText("dentro");
        board.addCard(inside);
        board.addCard(frame);
        Path folder = dir.resolve("secoes");

        storage.save(board, folder);
        String json = Files.readString(folder.resolve("board.json"), StandardCharsets.UTF_8);
        Board loaded = storage.load(folder).board();

        assertTrue(json.contains("\"FRAME\""), json);
        assertTrue(json.contains("\"version\": 2"), "sem mudar a versão: " + json);
        assertFalse(json.contains("frame\":"), "nenhum campo novo de vínculo com os cards");
        assertEquals(List.of("f", "a"), loaded.getCards().stream().map(Card::getId).toList());
        Card f = loaded.getCards().get(0);
        assertTrue(f.isFrame());
        assertEquals("Pesquisa", f.getText());
        assertEquals("#D3F9D8", f.getColor());
        assertEquals(500, f.getWidth());
        assertEquals(400, f.getHeight());
        assertTrue(f.isLocked());
        assertEquals("https://exemplo.com", f.getLink());
    }

    @Test
    void frameAboveACardInTheFileIsPutBelowItOnOpen(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("mao.json");
        Files.writeString(file, """
                {"version": 1, "name": "Editado à mão", "cards": [
                  {"id": "a", "x": 10, "y": 10, "width": 200, "height": 120, "text": "a", "color": "#FFFFFF", "shape": "RECTANGLE"},
                  {"id": "f", "x": 0, "y": 0, "width": 400, "height": 300, "text": "F", "color": "#FFFFFF", "shape": "FRAME"},
                  {"id": "b", "x": 300, "y": 10, "width": 200, "height": 120, "text": "b", "color": "#FFFFFF", "shape": "RECTANGLE"}
                ], "connections": [
                  {"id": "s1", "sourceId": "a", "targetId": "f"},
                  {"id": "s2", "sourceId": "a", "targetId": "b"}
                ]}
                """, StandardCharsets.UTF_8);

        Board loaded = storage.load(file).board();

        assertEquals(List.of("f", "a", "b"), loaded.getCards().stream().map(Card::getId).toList());
        assertEquals(1, loaded.getConnections().size(), "seta para a seção é ignorada");
        assertEquals("s2", loaded.getConnections().get(0).getId());
    }

    @Test
    void saveWithoutFramesKeepsItsOrderAndShapes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("antigo.json");
        Files.writeString(file, """
                {"version": 1, "name": "Antigo", "cards": [
                  {"id": "b", "x": 0, "y": 0, "width": 200, "height": 120, "text": "b", "color": "#FFFFFF", "shape": "DIAMOND"},
                  {"id": "a", "x": 50, "y": 50, "width": 200, "height": 120, "text": "a", "color": "#FFFFFF", "shape": "RECTANGLE"}
                ], "connections": []}
                """, StandardCharsets.UTF_8);

        Board loaded = storage.load(file).board();

        assertEquals(List.of("b", "a"), loaded.getCards().stream().map(Card::getId).toList());
        assertTrue(loaded.getCards().stream().noneMatch(Card::isFrame));
        assertEquals(CardShape.DIAMOND, loaded.getCards().get(0).getShape());
    }
}

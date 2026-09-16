package com.danilo.boardvisual.persistence;

import com.danilo.boardvisual.model.Board;
import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.CardShape;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.persistence.BoardFileFormat.BoardData;
import com.danilo.boardvisual.persistence.BoardFileFormat.CardData;
import com.danilo.boardvisual.persistence.BoardFileFormat.ConnectionData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Salva e carrega boards em arquivos JSON locais. */
public class BoardStorage {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void save(Board board, Path file) throws IOException {
        BoardData data = toData(board);
        Path absolute = file.toAbsolutePath();
        // Escreve num arquivo temporário e troca no fim: se algo falhar no
        // meio, o arquivo anterior continua intacto.
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        }
        try {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Board load(Path file) throws IOException {
        BoardData data;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            data = gson.fromJson(reader, BoardData.class);
        } catch (JsonParseException e) {
            throw new IOException("O arquivo não é um board válido.", e);
        }
        if (data == null) {
            throw new IOException("O arquivo está vazio.");
        }
        if (data.version() > BoardFileFormat.CURRENT_VERSION) {
            throw new IOException("O arquivo foi salvo por uma versão mais nova do BoardVisual.");
        }
        return fromData(data);
    }

    // ------------------------------------------------------------ mapeamento

    private static BoardData toData(Board board) {
        List<CardData> cards = board.getCards().stream()
                .map(c -> new CardData(c.getId(), c.getX(), c.getY(), c.getWidth(), c.getHeight(),
                        c.getText(), c.getColor(), c.getShape().name()))
                .toList();
        List<ConnectionData> connections = board.getConnections().stream()
                .map(c -> new ConnectionData(c.getId(), c.getSource().getId(), c.getTarget().getId()))
                .toList();
        return new BoardData(BoardFileFormat.CURRENT_VERSION, board.getId(), board.getName(), cards, connections);
    }

    private static Board fromData(BoardData data) {
        Board board = data.id() == null ? new Board(data.name()) : new Board(data.id(), data.name());

        if (data.cards() != null) {
            for (CardData cd : data.cards()) {
                if (cd == null || cd.id() == null) {
                    continue;
                }
                Card card = new Card(cd.id(), cd.x(), cd.y());
                card.setWidth(cd.width() > 0 ? cd.width() : Card.DEFAULT_WIDTH);
                card.setHeight(cd.height() > 0 ? cd.height() : Card.DEFAULT_HEIGHT);
                card.setText(cd.text());
                card.setColor(cd.color());
                card.setShape(CardShape.fromName(cd.shape()));
                board.addCard(card);
            }
        }

        if (data.connections() != null) {
            for (ConnectionData cd : data.connections()) {
                if (cd == null) {
                    continue;
                }
                var source = board.findCard(cd.sourceId());
                var target = board.findCard(cd.targetId());
                // Conexões apontando para cards inexistentes são ignoradas.
                if (source.isPresent() && target.isPresent()) {
                    Connection connection = cd.id() == null
                            ? new Connection(source.get(), target.get())
                            : new Connection(cd.id(), source.get(), target.get());
                    board.addConnection(connection);
                }
            }
        }
        return board;
    }
}

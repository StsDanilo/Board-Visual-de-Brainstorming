package com.danilo.boardvisual.model;

import java.util.List;
import java.util.Set;

/**
 * Foto imutável do conteúdo de um board (cards e conexões), usada pelo
 * desfazer/refazer. Records comparam por valor, então dá para saber se uma
 * ação mudou algo comparando a foto de antes com a de depois.
 *
 * Inclui os boards filhos (recursivamente), para que excluir e desfazer um
 * card que contém um board traga o conteúdo inteiro de volta.
 */
public record BoardSnapshot(List<CardState> cards, List<ConnectionState> connections) {

    public BoardSnapshot {
        cards = List.copyOf(cards);
        connections = List.copyOf(connections);
    }

    public record CardState(String id, double x, double y, double width, double height,
                            double baseWidth, double baseHeight, double fontSize,
                            String text, String color, CardShape shape,
                            PanelMode panelMode, String detailText, List<String> listItems,
                            String imageFile, String link, String boardLink, boolean locked,
                            String childBoardId, BoardSnapshot childBoard) {

        public CardState {
            listItems = List.copyOf(listItems);
        }

        static CardState of(Card card) {
            Board child = card.getChildBoard();
            return new CardState(card.getId(), card.getX(), card.getY(), card.getWidth(), card.getHeight(),
                    card.getBaseWidth(), card.getBaseHeight(), card.getFontSize(),
                    card.getText(), card.getColor(), card.getShape(),
                    card.getPanelMode(), card.getDetailText(), card.getListItemTexts(), card.getImageFile(),
                    card.getLink(), card.getBoardLink(), card.isLocked(),
                    child == null ? null : child.getId(), child == null ? null : child.snapshot());
        }

        void applyTo(Card card) {
            card.setX(x);
            card.setY(y);
            card.setWidth(width);
            card.setHeight(height);
            card.setBaseWidth(baseWidth);
            card.setBaseHeight(baseHeight);
            card.setFontSize(fontSize);
            card.setText(text);
            card.setColor(color);
            card.setShape(shape);
            card.setPanelMode(panelMode);
            card.setDetailText(detailText);
            card.setListItemTexts(listItems);
            card.setImageFile(imageFile);
            card.setLink(link);
            card.setBoardLink(boardLink);
            card.setLocked(locked);
            applyChildBoardTo(card);
        }

        /** Ver {@link Board#restore}: board filho existente (mesmo id) não é alterado. */
        private void applyChildBoardTo(Card card) {
            Board current = card.getChildBoard();
            if (childBoard == null) {
                card.setChildBoard(null);
            } else if (current == null || !current.getId().equals(childBoardId)) {
                Board restored = new Board(childBoardId, "");
                restored.restore(childBoard);
                card.setChildBoard(restored);
            }
        }
    }

    /**
     * Acrescenta a {@code names} as imagens citadas nesta foto, inclusive nos
     * boards filhos (usado para não apagar, ao salvar, imagens que o desfazer
     * ainda pode trazer de volta).
     */
    public void collectImageFiles(Set<String> names) {
        for (CardState card : cards) {
            if (card.imageFile() != null) {
                names.add(card.imageFile());
            }
            if (card.childBoard() != null) {
                card.childBoard().collectImageFiles(names);
            }
        }
    }

    /**
     * Uma seta: as pontas (fixas) e o que muda nela (texto e estilo). Texto e
     * estilo entram na comparação, então editar o texto ou trocar o estilo é
     * um passo do desfazer.
     */
    public record ConnectionState(String id, String sourceId, String targetId, String label, ConnectionStyle style) {

        static ConnectionState of(Connection connection) {
            return new ConnectionState(connection.getId(),
                    connection.getSource().getId(), connection.getTarget().getId(),
                    connection.getLabel(), connection.getStyle());
        }

        void applyTo(Connection connection) {
            connection.setLabel(label);
            connection.setStyle(style);
        }
    }
}

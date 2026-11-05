package com.danilo.boardvisual.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Um card do board: posição, tamanho, conteúdo e aparência.
 *
 * Não sabe nada sobre como é desenhado. Os campos são JavaFX properties para
 * que a view faça binding direto neles: mover um card é só alterar x/y aqui,
 * e tudo que depende disso (o próprio card na tela, as setas) se atualiza.
 *
 * Um card pode ser um "painel flutuante" ({@link #getPanelMode()} diferente de
 * NONE): além do texto que aparece no board, guarda um conteúdo maior (texto
 * detalhado ou lista) exibido num painel ao clicar no botão do card.
 *
 * Ou pode conter um board filho ({@link #getChildBoard()}): o botão do card
 * entra nesse board, que pode ter outros cards com boards filhos, sem limite.
 * O nome desse board é o texto do card.
 *
 * Ou pode mostrar uma imagem ({@link #getImageFile()}): o card guarda só o
 * nome do arquivo na pasta imagens/ do save, e o texto vira a legenda.
 *
 * Qualquer card pode ter ainda um endereço web ({@link #getLink()}), aberto
 * no navegador pelo marcador de clipe, e um atalho para outro board da árvore
 * ({@link #getBoardLink()}), aberto pelo marcador de seta curva.
 *
 * E pode estar travado ({@link #isLocked()}): não se move nem se exclui por
 * engano (as regras ficam nos controllers).
 *
 * Tamanho: {@link #getBaseWidth()}/{@link #getBaseHeight()} guardam o tamanho
 * definido à mão (ao criar ou redimensionar); {@link #getWidth()}/{@link #getHeight()}
 * são o tamanho exibido, que pode ser maior quando o texto não cabe (o card
 * cresce sozinho e volta ao tamanho definido à mão quando o texto diminui).
 * Esse ajuste é feito fora do modelo, porque depende de medir o texto na tela.
 */
public class Card {

    /** Tamanho padrão de um card sem formato definido (o do retângulo). */
    public static final double DEFAULT_WIDTH = 200;
    public static final double DEFAULT_HEIGHT = 120;
    /** Cor de fundo padrão, em hex CSS. */
    public static final String DEFAULT_COLOR = "#FFFFFF";
    /** Valor de {@link #getFontSize()} que indica fonte automática (ajustada ao card). */
    public static final double AUTO_FONT_SIZE = 0;

    private final String id;
    private final DoubleProperty x = new SimpleDoubleProperty(this, "x");
    private final DoubleProperty y = new SimpleDoubleProperty(this, "y");
    private final DoubleProperty width = new SimpleDoubleProperty(this, "width", DEFAULT_WIDTH);
    private final DoubleProperty height = new SimpleDoubleProperty(this, "height", DEFAULT_HEIGHT);
    private final DoubleProperty baseWidth = new SimpleDoubleProperty(this, "baseWidth", DEFAULT_WIDTH);
    private final DoubleProperty baseHeight = new SimpleDoubleProperty(this, "baseHeight", DEFAULT_HEIGHT);
    private final DoubleProperty fontSize = new SimpleDoubleProperty(this, "fontSize", AUTO_FONT_SIZE);
    private final StringProperty text = new SimpleStringProperty(this, "text", "");
    private final StringProperty color = new SimpleStringProperty(this, "color", DEFAULT_COLOR);
    private final ObjectProperty<CardShape> shape = new SimpleObjectProperty<>(this, "shape", CardShape.RECTANGLE);

    private final ObjectProperty<PanelMode> panelMode = new SimpleObjectProperty<>(this, "panelMode", PanelMode.NONE);
    private final StringProperty detailText = new SimpleStringProperty(this, "detailText", "");
    private final ObservableList<ListItem> listItems = FXCollections.observableArrayList();
    private final ObservableList<ListItem> readOnlyListItems = FXCollections.unmodifiableObservableList(listItems);

    private final ObjectProperty<Board> childBoard = new SimpleObjectProperty<>(this, "childBoard");

    /** Nome do arquivo da imagem na pasta {@code imagens/} do save, ou {@code null}. */
    private final StringProperty imageFile = new SimpleStringProperty(this, "imageFile");

    /**
     * Endereço web do card ("" = sem link). Só leitura por fora: tudo passa por
     * {@link #setLink}, que garante que nunca há aqui algo além de http(s) válido.
     */
    private final ReadOnlyStringWrapper link = new ReadOnlyStringWrapper(this, "link", "");

    /** Id do board de destino do atalho para outro board, ou {@code null}. */
    private final StringProperty boardLink = new SimpleStringProperty(this, "boardLink");

    /** Travado: protegido contra mover, redimensionar, trocar o formato e excluir sem querer. */
    private final BooleanProperty locked = new SimpleBooleanProperty(this, "locked", false);

    public Card(double x, double y) {
        this(UUID.randomUUID().toString(), x, y);
    }

    public Card(String id, double x, double y) {
        this.id = Objects.requireNonNull(id, "id");
        setX(x);
        setY(y);
    }

    /** Cria um card do formato informado, no tamanho padrão dele, centralizado no ponto. */
    public static Card create(CardShape shape, double centerX, double centerY) {
        Card card = new Card(centerX - shape.getDefaultWidth() / 2, centerY - shape.getDefaultHeight() / 2);
        card.setShape(shape);
        card.resize(shape.getDefaultWidth(), shape.getDefaultHeight());
        return card;
    }

    public String getId() {
        return id;
    }

    public DoubleProperty xProperty() { return x; }
    public double getX() { return x.get(); }
    public void setX(double value) { x.set(value); }

    public DoubleProperty yProperty() { return y; }
    public double getY() { return y.get(); }
    public void setY(double value) { y.set(value); }

    public DoubleProperty widthProperty() { return width; }
    public double getWidth() { return width.get(); }
    public void setWidth(double value) { width.set(value); }

    public DoubleProperty heightProperty() { return height; }
    public double getHeight() { return height.get(); }
    public void setHeight(double value) { height.set(value); }

    /** Tamanho definido à mão (ver a descrição da classe). */
    public DoubleProperty baseWidthProperty() { return baseWidth; }
    public double getBaseWidth() { return baseWidth.get(); }
    public void setBaseWidth(double value) { baseWidth.set(value); }

    public DoubleProperty baseHeightProperty() { return baseHeight; }
    public double getBaseHeight() { return baseHeight.get(); }
    public void setBaseHeight(double value) { baseHeight.set(value); }

    /** Define o tamanho à mão: muda o tamanho definido à mão e o exibido. */
    public void resize(double newWidth, double newHeight) {
        setWidth(newWidth);
        setHeight(newHeight);
        setBaseWidth(newWidth);
        setBaseHeight(newHeight);
    }

    /** Tamanho da fonte do texto, em px; {@link #AUTO_FONT_SIZE} = automático. */
    public DoubleProperty fontSizeProperty() { return fontSize; }
    public double getFontSize() { return fontSize.get(); }
    public void setFontSize(double value) { fontSize.set(value > 0 ? value : AUTO_FONT_SIZE); }

    public boolean isAutoFontSize() {
        return getFontSize() == AUTO_FONT_SIZE;
    }

    public StringProperty textProperty() { return text; }
    public String getText() { return text.get(); }
    public void setText(String value) { text.set(value == null ? "" : value); }

    public StringProperty colorProperty() { return color; }
    public String getColor() { return color.get(); }
    public void setColor(String value) { color.set(value == null ? DEFAULT_COLOR : value); }

    public ObjectProperty<CardShape> shapeProperty() { return shape; }
    public CardShape getShape() { return shape.get(); }
    public void setShape(CardShape value) { shape.set(value == null ? CardShape.RECTANGLE : value); }

    /**
     * Se o card é uma seção (frame, {@link CardShape#FRAME}): área com título que
     * fica atrás dos cards comuns e leva junto, ao ser arrastada, os cards que
     * estão dentro dela ({@link Frames}). O texto do card é o título.
     */
    public boolean isFrame() {
        return getShape() == CardShape.FRAME;
    }

    // ------------------------------------------------------ painel flutuante

    public ObjectProperty<PanelMode> panelModeProperty() { return panelMode; }
    public PanelMode getPanelMode() { return panelMode.get(); }
    public void setPanelMode(PanelMode value) { panelMode.set(value == null ? PanelMode.NONE : value); }

    public boolean isPanel() {
        return getPanelMode() != PanelMode.NONE;
    }

    /** Texto maior do modo {@link PanelMode#TEXT}. */
    public StringProperty detailTextProperty() { return detailText; }
    public String getDetailText() { return detailText.get(); }
    public void setDetailText(String value) { detailText.set(value == null ? "" : value); }

    /** Itens do modo {@link PanelMode#LIST} (somente leitura; altere pelos métodos abaixo). */
    public ObservableList<ListItem> getListItems() {
        return readOnlyListItems;
    }

    public ListItem addListItem(String text) {
        ListItem item = new ListItem(text);
        listItems.add(item);
        return item;
    }

    public void removeListItem(ListItem item) {
        listItems.remove(item);
    }

    /** Textos dos itens, na ordem. */
    public List<String> getListItemTexts() {
        return listItems.stream().map(ListItem::getText).toList();
    }

    /**
     * Define os itens pelos textos. Se a quantidade não muda, atualiza os
     * itens existentes no lugar (a tela não precisa recriar as linhas).
     */
    public void setListItemTexts(List<String> texts) {
        if (texts.size() == listItems.size()) {
            for (int i = 0; i < texts.size(); i++) {
                listItems.get(i).setText(texts.get(i));
            }
        } else {
            listItems.setAll(texts.stream().map(ListItem::new).toList());
        }
    }

    /** Se o painel tem algo escrito (texto detalhado ou algum item não vazio). */
    public boolean hasPanelContent() {
        return !getDetailText().isBlank()
                || listItems.stream().anyMatch(item -> !item.getText().isBlank());
    }

    /**
     * Troca o modo do painel. O conteúdo do modo anterior é apagado: texto
     * detalhado e lista não se convertem um no outro.
     */
    public void changePanelMode(PanelMode newMode) {
        if (newMode == null || newMode == getPanelMode()) {
            return;
        }
        setPanelMode(newMode);
        if (newMode != PanelMode.TEXT) {
            setDetailText("");
        }
        if (newMode != PanelMode.LIST) {
            listItems.clear();
        }
    }

    // ------------------------------------------------------ board aninhado

    /** Board filho aberto pelo botão do card, ou {@code null} se o card não contém um board. */
    public ObjectProperty<Board> childBoardProperty() { return childBoard; }
    public Board getChildBoard() { return childBoard.get(); }
    public void setChildBoard(Board value) { childBoard.set(value); }

    public boolean hasChildBoard() {
        return getChildBoard() != null;
    }

    // ---------------------------------------------------------------- imagem

    /**
     * Cria um card de imagem: retângulo do tamanho informado (já na proporção
     * da imagem), centralizado no ponto, fundo branco e sem legenda.
     */
    public static Card createImage(String imageFile, double centerX, double centerY, double width, double height) {
        Card card = new Card(centerX - width / 2, centerY - height / 2);
        card.resize(width, height);
        card.setImageFile(imageFile);
        return card;
    }

    /**
     * Imagem do card: só o nome do arquivo dentro da pasta {@code imagens/} do
     * save (nunca os bytes). {@code null} = card sem imagem.
     *
     * Num card de imagem, o tamanho definido à mão tem sempre a proporção da
     * imagem: a imagem ocupa o topo do card, na largura toda, e o texto (a
     * legenda) fica no espaço que o card ganha embaixo dela ao crescer.
     */
    public StringProperty imageFileProperty() { return imageFile; }
    public String getImageFile() { return imageFile.get(); }
    public void setImageFile(String value) { imageFile.set(value == null || value.isBlank() ? null : value); }

    public boolean hasImage() {
        return getImageFile() != null;
    }

    /**
     * Altura da imagem num card de largura {@code cardWidth}: a proporção vem do
     * tamanho definido à mão. 0 se o card não tem imagem.
     */
    public double imageHeight(double cardWidth) {
        if (!hasImage() || getBaseWidth() <= 0) {
            return 0;
        }
        return cardWidth * getBaseHeight() / getBaseWidth();
    }

    // ---------------------------------------------------------------- link

    /**
     * Endereço web (link externo) do card, já normalizado ({@link WebLink});
     * vazio = sem link. Qualquer card pode ter um: comum, painel, board
     * aninhado ou imagem. A tela mostra um marcador de clipe ({@link CardMarkers}).
     */
    public ReadOnlyStringProperty linkProperty() { return link.getReadOnlyProperty(); }
    public String getLink() { return link.get(); }

    /**
     * Define o link. O valor é normalizado ({@code exemplo.com} vira
     * {@code https://exemplo.com}); o que não é http(s) válido vira "sem link"
     * (quem quer avisar o usuário confere antes com {@link WebLink#check}).
     */
    public void setLink(String value) { link.set(WebLink.normalizeOrEmpty(value)); }

    public boolean hasLink() {
        return !getLink().isEmpty();
    }

    // ------------------------------------------------------ link para board

    /**
     * Id do board de destino do atalho "link para outro board" (qualquer board
     * da árvore, ver {@link BoardTree}), ou {@code null} = sem atalho. Guarda o
     * id, não o objeto: renomear o card do destino não quebra o link, e um
     * destino excluído deixa o id aqui (o marcador aparece "quebrado"; desfazer
     * a exclusão traz o destino de volta com o mesmo id).
     */
    public StringProperty boardLinkProperty() { return boardLink; }
    public String getBoardLink() { return boardLink.get(); }
    public void setBoardLink(String boardId) {
        boardLink.set(boardId == null || boardId.isBlank() ? null : boardId);
    }

    public boolean hasBoardLink() {
        return getBoardLink() != null;
    }

    // ---------------------------------------------------------------- travado

    /**
     * Card travado (cadeado): proteção contra clique errado. O que isso impede
     * (mover, redimensionar, trocar o formato, excluir) é decidido pelos
     * controllers; o modelo só guarda o estado. Texto, cor, setas, links e
     * painéis continuam livres.
     */
    public BooleanProperty lockedProperty() { return locked; }
    public boolean isLocked() { return locked.get(); }
    public void setLocked(boolean value) { locked.set(value); }

    // ---------------------------------------------------------------- formato

    /**
     * Troca o formato mantendo o card no mesmo centro. Se o tamanho definido à
     * mão ainda é o padrão do formato antigo, passa ao padrão do novo (a área
     * de texto de uma elipse ou losango é menor que a de um retângulo).
     */
    public void changeShape(CardShape newShape) {
        CardShape oldShape = getShape();
        if (newShape == null || newShape == oldShape) {
            return;
        }
        boolean hasDefaultSize = getBaseWidth() == oldShape.getDefaultWidth()
                && getBaseHeight() == oldShape.getDefaultHeight();
        double centerX = getCenterX();
        double centerY = getCenterY();
        setShape(newShape);
        if (hasDefaultSize) {
            resize(newShape.getDefaultWidth(), newShape.getDefaultHeight());
            setX(centerX - getWidth() / 2);
            setY(centerY - getHeight() / 2);
        }
    }

    /** Cópia com id novo e mesmo tamanho, texto e aparência, na mesma posição. */
    public Card copy() {
        Card copy = new Card(getX(), getY());
        copy.setWidth(getWidth());
        copy.setHeight(getHeight());
        copy.setBaseWidth(getBaseWidth());
        copy.setBaseHeight(getBaseHeight());
        copy.setFontSize(getFontSize());
        copy.setText(getText());
        copy.setColor(getColor());
        copy.setShape(getShape());
        copy.setPanelMode(getPanelMode());
        copy.setDetailText(getDetailText());
        copy.setListItemTexts(getListItemTexts());
        // Mesmo arquivo: duplicar não copia a imagem de novo.
        copy.setImageFile(getImageFile());
        copy.setLink(getLink());
        // Mesmo destino: a cópia leva ao mesmo board que o original.
        copy.setBoardLink(getBoardLink());
        // Cópia fiel (inclusive travado); o "Duplicar" da tela destrava a cópia para ela poder ser posicionada.
        copy.setLocked(isLocked());
        if (hasChildBoard()) {
            copy.setChildBoard(getChildBoard().deepCopy());
        }
        return copy;
    }

    public double getCenterX() {
        return getX() + getWidth() / 2;
    }

    public double getCenterY() {
        return getY() + getHeight() / 2;
    }

    @Override
    public String toString() {
        return "Card[" + id + "]";
    }
}

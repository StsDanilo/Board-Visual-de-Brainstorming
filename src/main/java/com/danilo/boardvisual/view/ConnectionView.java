package com.danilo.boardvisual.view;

import com.danilo.boardvisual.model.Card;
import com.danilo.boardvisual.model.Connection;
import com.danilo.boardvisual.model.ConnectionGeometry;
import com.danilo.boardvisual.model.ConnectionStyle;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.transform.Rotate;

import java.util.ArrayList;
import java.util.List;

/**
 * Seta entre dois cards, com o texto opcional no meio.
 *
 * Toda a geometria é derivada por binding das propriedades dos cards e da
 * seta no modelo: quando um card se move ou a seta muda de estilo, ela se
 * recalcula sozinha. Nenhum código de arrastar precisa saber que setas existem.
 *
 * Partes:
 * <pre>
 * hitLine     linha larga e transparente: só para o clique (selecionar, duplo clique edita o texto);
 *             com a seta contrária (B→A), fica só do seu lado da linha
 * line        a linha visível (cheia ou tracejada); termina na base das pontas
 * endArrow    ponta no destino (todos os estilos menos a linha sem ponta)
 * startArrow  ponta na origem (só a ponta dupla): a mesma ponta, girada 180°
 * label       o texto, horizontal, com fundo da cor do canvas; uma linha, com "…" se não couber
 * editor      campo de texto que aparece no lugar do texto durante a edição
 * </pre>
 * O texto e o campo ficam em {@link #getLabelLayer()}, que quem mostra a seta
 * põe numa camada acima de todas as linhas (senão a linha de outra seta
 * cruzaria o texto).
 * Só a hitLine, o texto e o campo recebem cliques: a linha e as pontas são
 * transparentes ao mouse, para não roubarem cliques dos cards nem do fundo.
 */
public class ConnectionView extends Group {

    private static final double ARROW_LENGTH = 12;
    private static final double ARROW_HALF_WIDTH = 6;
    /** Largura máxima do texto da seta (em unidades do board); passou disso, termina em "…". */
    static final double LABEL_MAX_WIDTH = 220;
    /**
     * Quanto a área de clique sai para o lado quando existe a seta contrária:
     * metade da largura dela (14 no CSS), para a borda encostar na linha.
     */
    static final double HIT_SIDE_OFFSET = 7;

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass DASHED = PseudoClass.getPseudoClass("dashed");
    private static final PseudoClass SEARCH_MATCH = PseudoClass.getPseudoClass("search-match");
    private static final PseudoClass SEARCH_CURRENT = PseudoClass.getPseudoClass("search-current");

    private final Connection connection;
    private final Line hitLine = new Line();
    private final Label label = new Label();
    private final TextField editor = new TextField();
    private final Group labelLayer = new Group();
    private final ReadOnlyBooleanWrapper editing = new ReadOnlyBooleanWrapper(this, "editing");
    private final BooleanProperty reverse = new SimpleBooleanProperty(this, "reverse");

    public ConnectionView(Connection connection) {
        this.connection = connection;
        getStyleClass().add("connection");
        setPickOnBounds(false);

        Card source = connection.getSource();
        Card target = connection.getTarget();
        List<Observable> geometry = new ArrayList<>(List.of(CardGeometry.dependencies(source, target)));
        Observable[] deps = geometry.toArray(new Observable[0]);
        geometry.add(connection.styleProperty());
        Observable[] styledDeps = geometry.toArray(new Observable[0]);

        // Pontos nas bordas dos cards, na reta que liga os dois centros.
        ObjectBinding<Point2D> start = Bindings.createObjectBinding(() -> point(ConnectionGeometry.start(connection)), deps);
        ObjectBinding<Point2D> tip = Bindings.createObjectBinding(() -> point(ConnectionGeometry.tip(connection)), deps);
        DoubleBinding angle = Bindings.createDoubleBinding(() -> {
            Point2D a = start.get();
            Point2D b = tip.get();
            return Math.toDegrees(Math.atan2(b.getY() - a.getY(), b.getX() - a.getX()));
        }, start, tip);

        hitLine.getStyleClass().add("connection-hit");
        bindEnds(hitLine, start, tip);
        // Com a seta contrária sobre a mesma reta, as duas áreas de clique se
        // cobririam (só a de cima seria clicável). Cada uma vai para o seu lado
        // da linha: para a esquerda de quem anda no sentido da seta, então A→B e
        // B→A ficam em lados opostos e se encontram na linha.
        hitLine.translateXProperty().bind(Bindings.createDoubleBinding(
                () -> reverse.get() ? Math.sin(Math.toRadians(angle.get())) * HIT_SIDE_OFFSET : 0, angle, reverse));
        hitLine.translateYProperty().bind(Bindings.createDoubleBinding(
                () -> reverse.get() ? -Math.cos(Math.toRadians(angle.get())) * HIT_SIDE_OFFSET : 0, angle, reverse));

        // A linha termina na base de cada ponta, para não "vazar" na frente dela.
        Line line = new Line();
        line.getStyleClass().add("connection-line");
        line.setMouseTransparent(true);
        line.startXProperty().bind(Bindings.createDoubleBinding(
                () -> start.get().getX() + startInset() * Math.cos(Math.toRadians(angle.get())), start, angle,
                connection.styleProperty()));
        line.startYProperty().bind(Bindings.createDoubleBinding(
                () -> start.get().getY() + startInset() * Math.sin(Math.toRadians(angle.get())), start, angle,
                connection.styleProperty()));
        line.endXProperty().bind(Bindings.createDoubleBinding(
                () -> tip.get().getX() - endInset() * Math.cos(Math.toRadians(angle.get())), tip, angle,
                connection.styleProperty()));
        line.endYProperty().bind(Bindings.createDoubleBinding(
                () -> tip.get().getY() - endInset() * Math.sin(Math.toRadians(angle.get())), tip, angle,
                connection.styleProperty()));

        Polygon endArrow = arrowHead(tip, angle, 0);
        endArrow.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> connection.getStyle().hasEndArrow(), styledDeps));
        Polygon startArrow = arrowHead(start, angle, 180);
        startArrow.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> connection.getStyle().hasStartArrow(), styledDeps));

        connection.styleProperty().addListener((obs, old, style) -> updateStyle(style));
        updateStyle(connection.getStyle());

        // Texto: no meio da seta (afastado do texto da seta contrária, se houver).
        label.getStyleClass().add("connection-label");
        label.textProperty().bind(connection.labelProperty());
        label.setMaxWidth(LABEL_MAX_WIDTH);
        label.setWrapText(false);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> connection.hasLabel() && !editing.get(), connection.labelProperty(), editing));
        ObjectBinding<Point2D> labelCenter = Bindings.createObjectBinding(
                () -> point(ConnectionGeometry.labelCenter(connection, label.getWidth(), label.getHeight(),
                        reverse.get())),
                withExtra(deps, label.widthProperty(), label.heightProperty(), reverse));
        label.translateXProperty().bind(Bindings.createDoubleBinding(
                () -> labelCenter.get().getX() - label.getWidth() / 2, labelCenter, label.widthProperty()));
        label.translateYProperty().bind(Bindings.createDoubleBinding(
                () -> labelCenter.get().getY() - label.getHeight() / 2, labelCenter, label.heightProperty()));

        // Campo de edição: centrado no meio da seta, escondido fora da edição.
        editor.getStyleClass().add("connection-label-editor");
        editor.setPrefColumnCount(14);
        editor.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= Connection.MAX_LABEL_LENGTH ? change : null));
        editor.setVisible(false);
        ObjectBinding<Point2D> middle = Bindings.createObjectBinding(() -> {
            Point2D a = start.get();
            Point2D b = tip.get();
            return a.midpoint(b);
        }, start, tip);
        editor.translateXProperty().bind(Bindings.createDoubleBinding(
                () -> middle.get().getX() - editor.getWidth() / 2, middle, editor.widthProperty()));
        editor.translateYProperty().bind(Bindings.createDoubleBinding(
                () -> middle.get().getY() - editor.getHeight() / 2, middle, editor.heightProperty()));

        getChildren().addAll(hitLine, line, endArrow, startArrow);
        labelLayer.getStyleClass().add("connection-label-group");
        labelLayer.setPickOnBounds(false);
        labelLayer.getChildren().addAll(label, editor);
    }

    /**
     * O texto e o campo de edição, num grupo à parte: quem mostra a seta põe
     * este grupo numa camada <b>acima de todas as linhas</b> (BoardView,
     * BoardRenderer). Dentro da própria seta, a linha de outra seta (ex.: a
     * contrária, B→A) desenhada depois passaria por cima do texto. As
     * coordenadas são as mesmas da seta (a camada fica no mesmo "mundo").
     */
    public Group getLabelLayer() {
        return labelLayer;
    }

    private double startInset() {
        return connection.getStyle().hasStartArrow() ? ARROW_LENGTH : 0;
    }

    private double endInset() {
        return connection.getStyle().hasEndArrow() ? ARROW_LENGTH : 0;
    }

    private void updateStyle(ConnectionStyle style) {
        pseudoClassStateChanged(DASHED, style.isDashed());
    }

    /**
     * Triângulo desenhado apontando para +x com a ponta na origem; é movido até
     * {@code point} e girado conforme o ângulo da seta (+180° para a ponta da origem).
     */
    private static Polygon arrowHead(ObjectBinding<Point2D> point, DoubleBinding angle, double extraRotation) {
        Polygon head = new Polygon(0, 0, -ARROW_LENGTH, -ARROW_HALF_WIDTH, -ARROW_LENGTH, ARROW_HALF_WIDTH);
        head.getStyleClass().add("connection-arrow");
        head.setMouseTransparent(true);
        head.translateXProperty().bind(Bindings.createDoubleBinding(() -> point.get().getX(), point));
        head.translateYProperty().bind(Bindings.createDoubleBinding(() -> point.get().getY(), point));
        Rotate rotate = new Rotate();
        rotate.angleProperty().bind(angle.add(extraRotation));
        head.getTransforms().add(rotate);
        return head;
    }

    private static void bindEnds(Line line, ObjectBinding<Point2D> start, ObjectBinding<Point2D> end) {
        line.startXProperty().bind(Bindings.createDoubleBinding(() -> start.get().getX(), start));
        line.startYProperty().bind(Bindings.createDoubleBinding(() -> start.get().getY(), start));
        line.endXProperty().bind(Bindings.createDoubleBinding(() -> end.get().getX(), end));
        line.endYProperty().bind(Bindings.createDoubleBinding(() -> end.get().getY(), end));
    }

    private static Point2D point(ConnectionGeometry.Point p) {
        return new Point2D(p.x(), p.y());
    }

    private static Observable[] withExtra(Observable[] deps, Observable... extra) {
        Observable[] all = new Observable[deps.length + extra.length];
        System.arraycopy(deps, 0, all, 0, deps.length);
        System.arraycopy(extra, 0, all, deps.length, extra.length);
        return all;
    }

    public Connection getConnection() {
        return connection;
    }

    // ------------------------------------------------------------ interação

    /** Linha larga e transparente, só para clique. */
    public Line getHitLine() {
        return hitLine;
    }

    /** O texto da seta (também recebe clique: selecionar, duplo clique edita). */
    public Label getLabel() {
        return label;
    }

    public TextField getLabelEditor() {
        return editor;
    }

    /** Destaque de seta selecionada (estado só da tela). */
    public void setSelected(boolean selected) {
        pseudoClassStateChanged(SELECTED, selected);
        labelLayer.pseudoClassStateChanged(SELECTED, selected); // o texto fica em outra camada
    }

    /**
     * Destaque da busca de texto no texto da seta ({@code :search-match} e, no resultado
     * atual, {@code :search-current}); o texto fica em outra camada, por isso as duas recebem.
     */
    public void setSearchState(boolean match, boolean current) {
        pseudoClassStateChanged(SEARCH_MATCH, match);
        pseudoClassStateChanged(SEARCH_CURRENT, match && current);
        labelLayer.pseudoClassStateChanged(SEARCH_MATCH, match);
        labelLayer.pseudoClassStateChanged(SEARCH_CURRENT, match && current);
    }

    /**
     * Existe a seta no sentido contrário (B→A desta A→B): as duas ficam sobre a
     * mesma reta, então o texto desta anda um pouco para o lado do destino
     * ({@link ConnectionGeometry#labelCenter}).
     */
    public void setReverse(boolean value) {
        reverse.set(value);
    }

    public boolean isReverse() {
        return reverse.get();
    }

    /** Mostra o campo de edição com o texto atual (o controller dá o foco). */
    public void showLabelEditor() {
        editor.setText(connection.getLabel().length() > Connection.MAX_LABEL_LENGTH
                ? connection.getLabel().substring(0, Connection.MAX_LABEL_LENGTH) : connection.getLabel());
        editing.set(true);
        editor.setVisible(true);
        labelLayer.toFront(); // por cima dos textos das outras setas
    }

    /** Esconde o campo de edição (o texto mostrado volta a ser o do modelo). */
    public void hideLabelEditor() {
        editor.setVisible(false);
        editing.set(false);
    }

    public ReadOnlyBooleanProperty editingProperty() {
        return editing.getReadOnlyProperty();
    }

    public boolean isEditingLabel() {
        return editing.get();
    }
}

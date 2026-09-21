module com.danilo.boardvisual {
    requires javafx.controls;
    requires com.google.gson;
    // Exportar para PDF; java.desktop traz o BufferedImage usado para passar a imagem ao PDFBox.
    requires org.apache.pdfbox;
    requires java.desktop;

    // O JavaFX precisa instanciar a Application via reflexão.
    exports com.danilo.boardvisual;
    // O Gson lê/escreve os records do formato de arquivo via reflexão.
    opens com.danilo.boardvisual.persistence to com.google.gson;
}

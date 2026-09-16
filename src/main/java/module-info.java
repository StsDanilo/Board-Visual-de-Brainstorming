module com.danilo.boardvisual {
    requires javafx.controls;
    requires com.google.gson;

    // O JavaFX precisa instanciar a Application via reflexão.
    exports com.danilo.boardvisual;
    // O Gson lê/escreve os records do formato de arquivo via reflexão.
    opens com.danilo.boardvisual.persistence to com.google.gson;
}

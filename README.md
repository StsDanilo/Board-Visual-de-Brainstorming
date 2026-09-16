# Board Visual de Brainstorming

Aplicativo desktop para organizar ideias espacialmente, no estilo Miro/Milanote:
um canvas infinito onde você cria cards, posiciona livremente e conecta com setas.

## Requisitos

- JDK 17 ou superior
- Maven 3.9+

## Como rodar

```bash
mvn javafx:run
```

Testes:

```bash
mvn test
```

## Funcionalidades

- Cards com texto editável direto no card
- Arrastar cards livremente; setas acompanham automaticamente
- Conexões entre cards (ferramenta Conexão ou alça na borda do card)
- Canvas com pan e zoom
- Barra de ferramentas: Selecionar, Card, Conexão e cor dos novos cards
- Barra flutuante do card selecionado: cor, duplicar e excluir
- Salvar e abrir boards em arquivos JSON locais

## Estrutura

```
src/main/java/com/danilo/boardvisual
├─ model/        Card, Connection, Board — dados puros, sem nada visual
├─ view/         Nós JavaFX: canvas, cards, setas, barras
├─ controller/   Interações de mouse e ações sobre o board
└─ persistence/  Leitura e escrita do JSON
src/main/resources/.../styles/app.css   Estilo visual (paleta e espaçamentos)
```

O modelo usa propriedades observáveis do JavaFX: a view faz binding nelas,
então mover um card no modelo atualiza o card e as setas na tela sem código
de redesenho.

## Stack

Java 17 · JavaFX 21 · Gson · JUnit 5

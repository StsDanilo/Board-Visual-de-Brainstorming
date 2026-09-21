# BoardVisual — Documentação do Projeto

> Documento de estudo e referência do projeto, mantido junto com o código: toda mudança relevante
> na arquitetura ou nas funcionalidades deve vir acompanhada da atualização deste arquivo.
> Estado descrito: até o painel flutuante (texto e lista).

Este documento está organizado em **níveis de profundidade**. Cada nível pressupõe os anteriores,
mas você pode parar em qualquer um:

| Nível | Para quê | Tempo de leitura |
|---|---|---|
| [1. Visão geral](#nível-1--visão-geral) | Explicar o projeto para alguém em um minuto | 2 min |
| [2. Arquitetura](#nível-2--arquitetura) | Saber onde cada coisa mora e por quê | 10 min |
| [3. Como cada funcionalidade funciona](#nível-3--como-cada-funcionalidade-funciona) | Seguir o caminho de uma ação do clique até a tela | 25 min |
| [4. Aprofundamento técnico](#nível-4--aprofundamento-técnico) | Entender os mecanismos do JavaFX e as decisões não óbvias | 40 min |
| [5. Guia de extensão](#nível-5--guia-de-extensão) | Adicionar funcionalidades sem quebrar nada | consulta |
| [Apêndices](#apêndices) | Decisões, armadilhas, testes, glossário | consulta |

---

# Nível 1 — Visão geral

## O que é

Um aplicativo desktop para organizar ideias no espaço, no estilo Miro/Milanote. Existe um canvas
infinito; nele você cria **cards** (retângulo, elipse ou losango) com texto, arrasta livremente,
liga os cards com **setas**, pinta com cores, seleciona vários de uma vez, desfaz erros e salva
tudo num arquivo **JSON** no computador. Não há servidor nem banco de dados.

## Tecnologias

- **Java 17** (compila tanto no JDK 17 quanto no 25)
- **JavaFX 21** para a interface
- **Gson** para ler e escrever JSON
- **JUnit 5** para testes
- **Maven** para build (`mvn javafx:run` roda, `mvn test` testa)

## A ideia central em uma frase

> **Os dados ficam num lugar, o desenho em outro, e o desenho se atualiza sozinho quando os dados mudam.**

Quando você arrasta um card, o código **não redesenha nada**: ele só muda o número `x` do card.
A tela e as setas "escutam" esse número e se reposicionam sozinhas. Esse é o mecanismo de
**binding** do JavaFX, e o projeto inteiro é construído em cima dele.

---

# Nível 2 — Arquitetura

## As quatro camadas

```
                    ┌───────────────────────────────┐
   usuário  ──────► │  controller/                  │   "o que fazer quando o usuário age"
  (mouse,           │  CanvasController, ...        │
   teclado)         └──────────────┬────────────────┘
                                   │ altera
                                   ▼
                    ┌───────────────────────────────┐
                    │  model/                       │   "os dados"
                    │  Board, Card, Connection      │   (sem nada visual)
                    └───────┬───────────────┬───────┘
              observa (binding)             │ lê/escreve
                            ▼               ▼
        ┌───────────────────────────┐   ┌─────────────────────────┐
        │  view/                    │   │  persistence/           │
        │  BoardView, CardView, ... │   │  BoardStorage (JSON)    │
        └───────────────────────────┘   └─────────────────────────┘
          "como desenhar"                 "como salvar em disco"
```

**Regras de dependência** (quem pode conhecer quem):

| Camada | Pode usar | Não pode usar |
|---|---|---|
| `model` | nada do projeto (só `javafx.beans`/`javafx.collections`) | view, controller, persistence |
| `persistence` | model | view, controller |
| `view` | model | controller, persistence |
| `controller` | model, view, persistence | — |

Consequência prática: o **modelo é testável sem abrir janela**, e dá para trocar a aparência inteira
sem tocar no modelo.

> **Por que o modelo usa classes do JavaFX?** `javafx.beans` e `javafx.collections` são a parte
> "não visual" do JavaFX (propriedades observáveis e listas observáveis). Usá-las no modelo é o que
> permite o binding. Foi uma troca consciente: um pouco de acoplamento com o JavaFX em troca de
> não escrever nenhum código de sincronização manual entre dados e tela.

## Mapa de arquivos

### `model/` — os dados

| Arquivo | Responsabilidade |
|---|---|
| `Card` | Um card: id, x, y, largura, altura, texto, cor (hex), formato. Tudo como propriedade observável. |
| `CardShape` | Enum dos formatos (`RECTANGLE`, `ELLIPSE`, `DIAMOND`) com o tamanho padrão de cada um. |
| `Connection` | Uma seta: id, card de origem, card de destino. Imutável. |
| `Board` | Conjunto de cards e conexões. Garante consistência (sem seta para card inexistente, sem seta duplicada). |
| `BoardSnapshot` | "Foto" imutável do board, usada pelo desfazer/refazer. |
| `PanelMode` | Enum do painel flutuante: `NONE` (card comum), `TEXT`, `LIST`. |
| `ListItem` | Um item da lista de um painel (objeto com `textProperty`, para binding por linha). |

### `view/` — a aparência

| Arquivo | Responsabilidade |
|---|---|
| `MainWindow` | Layout da janela: menu, área do canvas (com a barra de ferramentas por cima), barra de status. |
| `BoardView` | O canvas: pan/zoom, camadas, cria/remove `CardView`/`ConnectionView` quando o board muda, seleção, caixa de seleção, posição da barra flutuante. |
| `CardView` | Desenho de um card: fundo com a silhueta do formato, campo de texto, alça de conexão. |
| `ConnectionView` | Desenho de uma seta, calculado por binding a partir dos dois cards. |
| `CardGeometry` | Matemática: onde a seta toca o contorno de cada formato; recuo do texto dentro do formato. |
| `Tool` | Enum das ferramentas: nome, tecla de atalho, formato que cria, ícone, dica. |
| `ToolBarView` | Barra lateral de ferramentas + bolinha de cor dos novos cards. |
| `SelectionToolbarView` | Barra flutuante acima da seleção: cor, formato, modo do painel (Texto/Lista), duplicar, excluir. |
| `FloatingPanelView` | O painel aberto ao lado de um card: cabeçalho + corpo de texto ou lista. |
| `PopupButton` | Base de "botão que abre um painelzinho". |
| `ColorPickerButton` / `ShapePickerButton` | Os dois botões com popup (cor e formato). |
| `ColorSwatchGrid` | Grade de bolinhas de cor. |
| `CardPalette` | As 8 cores oferecidas. |
| `Icons` | Ícones desenhados com path SVG. |

### `controller/` — o comportamento

| Arquivo | Responsabilidade |
|---|---|
| `MainController` | Monta e conecta tudo; ações de arquivo (novo, abrir, salvar); desfazer/refazer do menu. |
| `CanvasController` | Mouse no fundo: pan, zoom, caixa de seleção, criar card. |
| `CardDragController` | Mouse no card: selecionar, Shift+clique, arrastar (inclusive em grupo). |
| `ConnectionController` | Criar setas arrastando de um card a outro. |
| `SelectionActionsController` | Cor, formato, duplicar e excluir aplicados à seleção. |
| `TextEditController` | Registra a edição de texto no histórico (sessões de edição). |
| `PanelController` | Abrir/fechar painéis, itens da lista, troca de modo com confirmação. |
| `KeyboardController` | Atalhos de teclado. |
| `EditHistory` | Pilha de desfazer/refazer. |

### `persistence/` — o arquivo

| Arquivo | Responsabilidade |
|---|---|
| `BoardFileFormat` | Records que espelham exatamente o JSON salvo. |
| `BoardStorage` | Converte `Board` ⇄ JSON e grava/lê o arquivo com segurança. |

### Outros

| Arquivo | Responsabilidade |
|---|---|
| `Main` | `main()` — só chama `Application.launch`. |
| `BoardVisualApp` | Cria janela, controller, cena, aplica o CSS. |
| `module-info.java` | Declara o módulo Java (dependências e permissões de reflexão). |
| `resources/.../app.css` | **Toda** a aparência: paleta, bordas, sombras, cursores, formatos. |

## Como a aplicação é montada na inicialização

```
Main.main()
 └─ Application.launch(BoardVisualApp)
     └─ BoardVisualApp.start(stage)
         ├─ new MainWindow()                 ← cria BoardView, ToolBarView, SelectionToolbarView, menus
         ├─ new MainController(stage, window)
         │   ├─ new EditHistory(...)
         │   ├─ new CanvasController(...)    ← instala handlers no BoardView
         │   ├─ new CardDragController(...)  ┐
         │   ├─ new ConnectionController(...)├─ ainda sem cards: registram-se no "inicializador"
         │   ├─ new TextEditController(...)  ┘  que o BoardView chama para cada CardView criado
         │   ├─ new SelectionActionsController(...)
         │   ├─ new KeyboardController(...)  ← instala handlers de tecla na janela
         │   ├─ wireMenu()
         │   └─ newBoard()                   ← BoardView.setBoard(new Board("Sem título"))
         ├─ new Scene(window) + app.css
         ├─ stage.show()
         └─ boardView.requestFocus()         ← atalhos funcionam sem clicar antes
```

Repare que **quem conhece todo mundo é só o `MainController`**. Cada controller recebe apenas o que
precisa pelo construtor (injeção de dependência manual, sem framework).

---

# Nível 3 — Como cada funcionalidade funciona

Cada seção segue o caminho **evento → controller → modelo → tela**.

## 3.1 Criar um card

1. Usuário aperta `E` (ou clica no botão Elipse).
   `KeyboardController` percorre `Tool.values()`, acha `Tool.ELLIPSE` pela tecla e faz
   `activeTool.set(ELLIPSE)`.
2. A propriedade `activeTool` pertence ao `ToolBarView`. Mudar a propriedade:
   - marca o botão Elipse na barra (listener no `ToolBarView`);
   - troca a dica da barra de status (binding no `MainWindow`);
   - aplica a pseudo-classe `:tool-ellipse` e `:tool-create` no `BoardView` (listener no `MainController`) → o CSS muda o cursor para mira.
3. Usuário clica no fundo. `CanvasController` recebe `MOUSE_CLICKED`, confirma que foi no fundo
   (`isBackground`) e sem arrastar (`isStillSincePress`).
4. Converte a posição do clique para **coordenadas de mundo** (`view.sceneToWorld`) e cria
   `Card.create(ELLIPSE, x, y)` com a cor atual da barra.
5. `history.perform(() -> board.addCard(card))` — adiciona registrando no desfazer.
6. `board.getCards()` é uma lista observável; o `BoardView` está escutando e cria um `CardView`
   automaticamente, chamando o **inicializador** (que pendura os handlers dos controllers no card novo).
7. O controller seleciona o card, coloca o cursor no texto e volta a ferramenta para Selecionar.

## 3.2 Arrastar um card (ou um grupo)

```
MOUSE_PRESSED no card
  ├─ filtro no CardView: traz para frente; ajusta a seleção (clique / Shift+clique)
  └─ handler no body (se não foi no texto):
       guarda ponto inicial (mundo), posições iniciais de TODOS os selecionados
       e uma foto do board (history.capture())
MOUSE_DRAGGED
  └─ para cada selecionado: x = xInicial + deslocamento; y = yInicial + deslocamento
MOUSE_RELEASED
  └─ history.record(fotoInicial)  → um único passo de desfazer, se algo mudou
```

O controller **só muda `x` e `y` no modelo**. O que acontece na tela:

- `CardView.layoutX` está ligado (`bind`) a `card.x` → o card se move.
- A seta tem os pontos calculados por binding que depende de `x, y, largura, altura, formato`
  dos dois cards → a seta se recalcula.
- A barra flutuante escuta a geometria dos cards selecionados → reposiciona.

Nenhuma dessas três coisas é chamada pelo código de arrastar.

**Detalhe de UX:** clicar sem arrastar num card que faz parte de um grupo seleciona só ele — mas
apenas **ao soltar** o botão, e só se não houve movimento. Se a seleção mudasse já no clique,
seria impossível arrastar o grupo.

## 3.3 Criar uma conexão

Duas formas de começar, ambas no `ConnectionController`:

- Ferramenta **Conexão**: pressionar em qualquer ponto do card.
- Ferramenta **Selecionar**: pressionar na **alça** (bolinha na borda direita).

```
PRESSED  → marca o card de origem; mostra uma linha tracejada de prévia
DRAGGED  → atualiza a ponta da prévia; destaca o card sob o mouse (pseudo-classe :connection-target)
RELEASED → se soltou sobre outro card: history.perform(() -> board.connect(origem, destino))
           esconde a prévia
```

`Board.connect` recusa: ligar um card a ele mesmo, cards fora do board e seta repetida
(A→B duas vezes). B→A é permitido — é outra seta.

Os eventos são tratados com **filtros** no `CardView` para serem capturados **antes** do campo de
texto (senão, pressionar no texto posicionaria o cursor em vez de começar a seta). Ver 4.3.

## 3.4 Seleção

A seleção mora no `BoardView` como um `ObservableSet<Card>`. Formas de alterá-la:

| Gesto | Onde é tratado |
|---|---|
| Clique no card | `CardDragController` (filtro de press) |
| Shift+clique | `CardDragController` — inclui ou retira |
| Clique no fundo | `CanvasController` — limpa |
| Arrastar o fundo | `CanvasController` — caixa de seleção |
| Ctrl+A | menu Editar → `boardView.selectAll()` |
| Esc (sem edição, sem painel aberto, na ferramenta Selecionar) | `KeyboardController` — limpa |

**Caixa de seleção:** no press, guarda o ponto inicial e (se Shift) a seleção atual. A cada
arrasto: desenha o retângulo e define `seleção = seleçãoInicial ∪ cardsQueTocamORetângulo`.
Recalcular do zero a cada movimento (em vez de ir somando) faz a seleção **encolher** quando você
volta com o mouse — igual aos editores gráficos.

Quando a seleção muda, um listener no `BoardView`:
1. liga/desliga a pseudo-classe `:selected` nos `CardView` (o CSS desenha o destaque);
2. reposiciona a barra flutuante.

E o `SelectionActionsController` também escuta, para mostrar na barra a cor e o formato da seleção
(ou o estado "misto" se forem diferentes).

## 3.5 Cor e formato

Dois caminhos para escolher, mesmo efeito:

- **Barra de ferramentas** (bolinha): define a cor dos **próximos** cards e aplica na seleção.
- **Barra flutuante**: aplica na seleção.

```
ColorPickerButton (clique numa amostra)
  → onColorPicked(hex)
  → SelectionActionsController.applyColor(hex)
  → history.perform(() -> para cada selecionado: card.setColor(hex))
  → CardView escuta card.color → body.setStyle("-card-color: #hex")
  → CSS: .card { -fx-background-color: -card-color; }
```

O formato segue o mesmo caminho, mas chama `card.changeShape(formato)`, que também ajusta o
tamanho mantendo o centro (a área útil para texto de uma elipse/losango é menor).

## 3.6 Duplicar e excluir

- **Duplicar:** `card.copy()` (id novo, mesmo tamanho/texto/cor/formato), deslocado 24px, e a
  seleção passa para as cópias — duplicar de novo cria uma "escada". Setas não são copiadas.
- **Excluir:** `board.removeCard(card)`, que antes remove todas as setas ligadas ao card.
  A seleção se ajusta sozinha (o `BoardView` retira da seleção cards que saem do board).

Ambos passam por `history.perform`.

## 3.7 Desfazer / refazer

Estratégia: **pilha de estados** (fotos), não pilha de comandos.

```
history.perform(ação):
    antes = board.snapshot()
    ação.run()
    se board.snapshot() != antes:   ← records comparam por valor
        pilhaDesfazer.push(antes)
        pilhaRefazer.clear()

undo():
    pilhaRefazer.push(board.snapshot())
    board.restore(pilhaDesfazer.pop())

redo():  (espelhado)
```

Para gestos longos (arrastar) usa-se `capture()` no início e `record(antes)` no fim, para que o
gesto inteiro vire **um** passo.

Para **digitação** existem sessões: `beginEdit()` quando um campo ganha foco e `endEdit()` quando
perde. Se uma ação pontual acontece no meio da sessão (ex.: digitando no painel, clicar para remover
um item), `perform` encerra a sessão (a digitação até ali vira um passo), executa a ação (outro
passo) e reabre a sessão. Assim os passos ficam na ordem certa, sem duplicação.

**Texto:** enquanto o campo tem foco, `Ctrl+Z` vai para o `TextArea` (desfazer nativo, letra a letra).
O `TextEditController` abre a sessão quando o texto **ganha** foco e fecha quando **perde** — toda a
digitação vira um passo do board. No painel flutuante, a sessão acompanha o `focusWithin` do painel
inteiro (foco em qualquer campo dele).

**Restauração no lugar** — ver 4.7. Resumo: `restore` não recria o board; atualiza os cards
existentes e só adiciona/remove o que mudou. A tela não pisca e os `CardView` continuam os mesmos.

## 3.8 Pan e zoom

- **Pan:** botão do meio ou direito (qualquer lugar), Espaço+arrastar (qualquer lugar), ou arrastar
  o fundo nas ferramentas que não são Selecionar.
- **Zoom:** roda do mouse ou pinça no touchpad, sempre em torno do cursor, entre 20% e 300%.

Nenhum dos dois altera o modelo: só mudam duas transformações (`Translate` e `Scale`) aplicadas ao
grupo que contém tudo. Ver 4.2.

## 3.9 Salvar e abrir

```
Salvar:  Board → BoardData (records) → Gson → arquivo.tmp → move para arquivo.json
Abrir:   arquivo.json → Gson → BoardData → valida/completa → Board novo → BoardView.setBoard
```

Exemplo real de arquivo:

```json
{
  "version": 1,
  "id": "7f3c…",
  "name": "Ideias",
  "cards": [
    { "id": "a1…", "x": 150.0, "y": 190.0, "width": 200.0, "height": 120.0,
      "text": "Ideia central", "color": "#FFF1A8", "shape": "RECTANGLE" }
  ],
  "connections": [
    { "id": "c9…", "sourceId": "a1…", "targetId": "b2…" }
  ]
}
```

Abrir um board limpa o histórico de desfazer e volta a visão para 100%.

## 3.10 Painel flutuante

Um painel flutuante é um **card comum com dados a mais** (`Card.panelMode`, `detailText`,
`listItems`). Não existe uma classe separada: tudo que vale para cards (formato, cor, seta,
seleção, duplicar, desfazer, salvar) vale para painéis automaticamente.

```
Ferramenta Painel (P) + clique  → Card.create(...) com panelMode = TEXT
Botão no card (CardView.panelButton)
  → PanelController.toggle(card)
  → new FloatingPanelView(card)          (binding direto com detailText / listItems)
  → BoardView.showPanel(painel, card)    (posiciona ao lado do card, acompanha pan/zoom/arrasto)
```

- **Fechar:** ✕, botão do card de novo, `Esc` (depois de sair da edição), ou clique fora do painel
  e fora do card dele. O `BoardView` também fecha sozinho se o card sai do board (excluir, desfazer
  a criação, trocar de board) e avisa pelo `panelAnchorProperty`.
- **Um por vez:** abrir outro painel fecha o anterior.
- **Lista:** cada linha faz `bindBidirectional` com o `textProperty` do seu `ListItem`, então editar
  um item não recria a lista. Adicionar/remover passam por `history.perform`; as linhas são
  reconstruídas pelo listener da lista.
- **Troca de modo:** botões Texto | Lista na barra flutuante, visíveis só se **todos** os selecionados
  forem painéis. `Card.changePanelMode` apaga o conteúdo do modo anterior; se havia conteúdo, o
  `PanelController` pede confirmação num `Alert` antes. Tudo é desfazível.
- **Tamanho do painel:** o painel e a barra flutuante ficam como filhos *managed* do `BoardView`
  (ver 4.2): o `Pane` reajusta o tamanho quando o conteúdo cresce, e os listeners de largura/altura
  reposicionam. Com `setManaged(false)` eles não cresciam — foi um bug encontrado nos testes.

## 3.11 Atalhos de teclado

O `KeyboardController` registra um **handler na janela** (`Stage`). Como handlers rodam na fase de
"borbulhamento" (ver 4.3), ele só recebe teclas que **ninguém consumiu antes** — em especial, o
campo de texto consome as letras enquanto você digita.

Mesmo assim ele verifica explicitamente `isEditingText()` (o dono do foco é um `TextInputControl`?).
Uma letra gera dois eventos (`KEY_PRESSED` e `KEY_TYPED`), e o texto é inserido a partir do
`KEY_TYPED`; não depender de o `KEY_PRESSED` ter sido consumido garante que digitar "v" no card
nunca troque a ferramenta. (Esse cenário é coberto pelo teste de interface "atalho ignorado durante edição".)

Divisão de responsabilidades:

| Atalho | Tratado por | Por quê |
|---|---|---|
| Ctrl+N/O/S/Shift+S, Ctrl+0 | aceleradores de menu | aparecem no menu |
| Ctrl+Z, Ctrl+Y, Ctrl+A | aceleradores de menu | idem; e só disparam se o texto não usar a tecla |
| V R E L C, Delete, Ctrl+D, Ctrl+Shift+Z, Espaço, Esc | `KeyboardController` | teclas sem item de menu ou com lógica condicional |

---

# Nível 4 — Aprofundamento técnico

## 4.1 Propriedades e binding do JavaFX

Uma `DoubleProperty` é um "número observável":

```java
DoubleProperty x = new SimpleDoubleProperty(0);
x.addListener((obs, antigo, novo) -> System.out.println(novo)); // reage a mudanças
node.layoutXProperty().bind(x);   // node.layoutX passa a SEMPRE valer x
x.set(100);                        // imprime 100 e move o node
```

Três formas usadas no projeto:

| Forma | Exemplo | Uso |
|---|---|---|
| `bind` (unidirecional) | `layoutXProperty().bind(card.xProperty())` | A view segue o modelo |
| `bindBidirectional` | `textArea.textProperty().bindBidirectional(card.textProperty())` | Digitar muda o modelo, e desfazer muda o texto |
| Binding calculado | `Bindings.createDoubleBinding(() -> cálculo, dependências...)` | Setas, recuo do texto |

**Binding calculado** é o que faz as setas funcionarem (`ConnectionView`):

```java
ObjectBinding<Point2D> start = Bindings.createObjectBinding(
        () -> CardGeometry.borderPoint(source, target.getCenterX(), target.getCenterY()),
        deps);   // deps = x, y, width, height, shape dos DOIS cards
line.startXProperty().bind(Bindings.createDoubleBinding(() -> start.get().getX(), start));
```

O JavaFX só recalcula quando alguma dependência muda, e só quando o valor é lido de novo
(avaliação **preguiçosa**). Arrastar um card gera dezenas de eventos por segundo, mas cada seta
recalcula no máximo uma vez por quadro desenhado.

> **Cuidado:** se você esquecer uma dependência na lista, o binding não atualiza quando ela muda.
> Por isso existe `CardGeometry.forEach(card, ...)`, uma lista única das propriedades geométricas
> usada tanto pelas setas quanto pela barra flutuante.

## 4.2 Scene graph, camadas e sistemas de coordenadas

### Estrutura dos nós

```
Scene
 └─ MainWindow (BorderPane)
     ├─ top:    MenuBar
     ├─ center: StackPane
     │   ├─ BoardView (Pane) ─────────────────────── recortado (clip) ao tamanho visível
     │   │   ├─ world (Group) ── transforms: [Translate pan, Scale zoom]
     │   │   │   ├─ connectionLayer (Group)  → ConnectionView...
     │   │   │   ├─ cardLayer (Group)        → CardView...
     │   │   │   └─ overlayLayer (Group)     → linha de prévia da conexão
     │   │   ├─ marquee (Rectangle)          ← fora do world: não sofre zoom
     │   │   └─ SelectionToolbarView         ← fora do world: não sofre zoom
     │   └─ ToolBarView                      ← por cima do canvas, à esquerda
     └─ bottom: barra de status
```

As setas ficam numa camada **abaixo** dos cards, por isso passam "por trás" deles.

### Quatro sistemas de coordenadas

| Sistema | Origem | Quem usa |
|---|---|---|
| **Tela** | canto do monitor | `Robot`, posição de popups |
| **Cena** | canto da área da janela | eventos de mouse (`getSceneX`) |
| **View** | canto do `BoardView` | marquee, barra flutuante, zoom em torno do cursor |
| **Mundo** | origem do board, antes de pan/zoom | **modelo** (`card.x`, `card.y`) |

Conversões usadas:

```java
Point2D mundo = world.sceneToLocal(sceneX, sceneY);     // BoardView.sceneToWorld
Point2D naView = world.localToParent(mundoX, mundoY);   // posição da barra flutuante
```

**Regra de ouro:** o modelo guarda **mundo**. Por isso o arrasto funciona igual em qualquer zoom:
o controller converte o mouse para mundo antes de calcular o deslocamento.

### Zoom em torno do cursor

Com `tela = pan + zoom · mundo`, queremos que o ponto do mundo sob o cursor continue sob o cursor
depois do zoom:

```
mundo  = (cursor − panAntigo) / zoomAntigo
panNovo = cursor − mundo · zoomNovo
```

É exatamente o que `BoardView.zoomAt` faz.

### Por que a barra flutuante e a caixa de seleção ficam fora do `world`

Se ficassem dentro, cresceriam e encolheriam com o zoom. Fora, elas ficam em pixels de tela e o
`BoardView` calcula a posição convertendo os cards de mundo para view a cada mudança de
geometria, pan, zoom ou tamanho da janela.

## 4.3 Eventos: filtros, handlers e consumo

Um evento de mouse percorre a árvore em **duas fases**:

```
          CAPTURA (filtros) ↓                     ↑ BORBULHAMENTO (handlers)
Window → Scene → ... → BoardView → CardView → body → TextArea (alvo)
```

- `addEventFilter` → roda **na descida**, antes dos filhos.
- `addEventHandler` / `setOnX` → roda **na subida**, depois dos filhos.
- `event.consume()` → interrompe o caminho.

Onde o projeto usa cada um e por quê:

| Local | Tipo | Motivo |
|---|---|---|
| `BoardView`, press do botão do meio/direito/Espaço | filtro | O pan deve funcionar **mesmo sobre um card**; precisa agir antes do card. |
| `BoardView`, press esquerdo no fundo | handler | Só interessa se nenhum card tratou o clique. |
| `BoardView`, drag/release do pan e da caixa | filtro | O drag é entregue ao nó onde o press começou; o filtro no pai pega de qualquer forma. |
| `CardView`, press (seleção, trazer para frente) | filtro | Deve acontecer até se o clique foi no texto. |
| `CardView`, press da conexão | filtro + consume | Deve **impedir** o `TextArea` de receber o clique. |
| `body`, press/drag do arrasto | handler | Só se o clique **não** foi no texto (checado com `isTextNode`). |
| `Stage`, teclas | handler | Só teclas não consumidas pelo campo de texto. |

**Como saber se o clique foi "no fundo":** `event.getPickResult().getIntersectedNode() == view`.
O nó intersectado é o mais específico sob o mouse; se for o próprio `BoardView`, não havia card ali.

**Pan que só começa se mover:** no press apenas se marca `panPressed`; a pseudo-classe `:panning`
(cursor de mão fechada) só liga no primeiro drag. Sem isso, um simples clique piscaria o cursor.

## 4.4 Picking e silhuetas

"Picking" é o JavaFX decidir qual nó está sob o mouse. Por padrão um `Region` responde pelo
**retângulo** que o envolve (`pickOnBounds = true`). Para elipse e losango isso é errado: clicar no
canto vazio selecionaria o card.

Solução em `CardView`:

```java
body.setPickOnBounds(false);     // body usa a silhueta (-fx-shape) para decidir
content.setPickOnBounds(false);  // a área de texto ocupa o card todo; sem isso, ela
                                 // "pegaria" os cantos pelo retângulo dela
```

O segundo foi um bug real encontrado nos testes: o `body` já respeitava a silhueta, mas o `content`
(que o `StackPane` estica até o tamanho do card) aceitava clique no retângulo inteiro.

`BoardView.cardViewAt` (usado para saber sobre qual card uma seta foi solta) usa
`body.contains(ponto)`, que também respeita a silhueta.

## 4.5 CSS: como a aparência fica fora do Java

Princípio: **Java decide o estado, CSS decide a aparência.**

### Variáveis de paleta

```css
.root { -bv-accent: #4f7cff; -bv-border: #d8d8d2; ... }
.card { -fx-border-color: -bv-border; }
```

Mudar a identidade visual = mudar valores em `.root`.

### Dados do modelo viram variáveis CSS

A cor do card vem do modelo, mas o Java **não pinta o fundo**; ele só define uma variável:

```java
body.setStyle("-card-color: #FFF1A8;");        // CardView.applyColor
```
```css
.card { -card-color: #ffffff; -fx-background-color: -card-color; }
```

Assim sombras, bordas e estados de seleção continuam 100% no CSS. O mesmo truque é usado nas
amostras de cor (`-swatch-color`).

### Estado vira pseudo-classe

```java
pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), true);
```
```css
.card-view:selected .card { -fx-border-color: -bv-accent, rgba(79,124,255,.22); ... }
```

Pseudo-classes usadas: `:selected`, `:connection-target`, `:tool-select`, `:tool-rectangle`,
`:tool-ellipse`, `:tool-diamond`, `:tool-connection`, `:tool-create`, `:panning`, `:space-pan`,
`:mixed`, e as nativas `:hover`, `:pressed`, `:focus-within`.

### Formatos com `-fx-shape`

```css
.card.shape-ellipse { -fx-shape: "M 0 50 A 50 50 0 1 1 100 50 A 50 50 0 1 1 0 50 Z"; }
.card.shape-diamond { -fx-shape: "M 50 0 L 100 50 L 50 100 L 0 50 Z"; }
```

O path é desenhado num quadrado 100×100 e o JavaFX **escala** para o tamanho do `Region`. Fundo,
borda e sombra seguem a silhueta automaticamente.

### Precedência: código × CSS (armadilha importante)

No JavaFX a ordem de prioridade é:

```
CSS do próprio JavaFX (modena)  <  valor definido por código  <  seu CSS (app.css)  <  setStyle inline
```

Ou seja, **`app.css` vence um `setPadding(...)` feito no Java**. Por isso:

- O recuo do texto (calculado no Java por formato) é aplicado num nó (`.card-content`) que
  **não tem `-fx-padding` no CSS** — há um comentário avisando.
- Cursores de pan são definidos por pseudo-classe no CSS, e não com `setCursor` no Java.

### Popups são outra janela

`PopupButton` abre um `javafx.stage.Popup`, que tem **cena própria**: o `app.css` da janela
principal não se aplica automaticamente. Por isso, ao abrir, o código copia as folhas de estilo e
adiciona a classe `root` ao conteúdo (para as variáveis `-bv-*` existirem ali).

### Texto centralizado no `TextArea`

O `TextArea` não tem propriedade de alinhamento, mas desenha o conteúdo com um nó `Text` interno
(classe `.text`), que aceita `-fx-text-alignment`. Foi testado: cursor, clique e seleção acompanham.
É dependência de estrutura interna — se o JavaFX mudar, o texto só volta a ficar à esquerda.

## 4.6 Geometria

### Onde a seta toca o card (`CardGeometry.borderPoint`)

A partir do centro `(cx, cy)`, na direção `(dx, dy)` do outro card, procuramos `t` tal que
`(cx + t·dx, cy + t·dy)` esteja **no contorno**. Com `a = largura/2` e `b = altura/2`:

| Formato | Equação do contorno | `t` |
|---|---|---|
| Retângulo | `|x| = a` ou `|y| = b` | `min(a/|dx|, b/|dy|)` |
| Elipse | `(x/a)² + (y/b)² = 1` | `1 / √((dx/a)² + (dy/b)²)` |
| Losango | `|x|/a + |y|/b = 1` | `1 / (|dx|/a + |dy|/b)` |

A ponta da seta é um triângulo desenhado apontando para +x com a ponta na origem; ele é
**transladado** até o ponto de contato e **girado** pelo ângulo `atan2(dy, dx)`. A linha termina na
base do triângulo (não na ponta) para não "vazar" à frente dele.

### Área do texto dentro do formato (`CardGeometry.contentInsets`)

| Formato | Maior retângulo inscrito |
|---|---|
| Retângulo | o próprio card, com margem fixa |
| Elipse | lados `a·√2` × `b·√2` → recuo ≈ 14,6% de cada lado |
| Losango | metade da largura × metade da altura → recuo de 25% |

Por isso `CardShape` dá tamanhos padrão maiores para elipse (230×150) e losango (260×170).

## 4.7 Desfazer: por que fotos, e como `restore` funciona

### Fotos × comandos

| | Pilha de comandos | **Pilha de fotos (escolhida)** |
|---|---|---|
| Cada ação precisa | uma classe com `do()`/`undo()` | nada — só chamar `perform` |
| Nova propriedade no card | revisar todos os comandos | adicionar ao `CardState` |
| Risco de bug | `undo` incompleto e estado corrompido | baixo: restaura tudo |
| Memória | pequena | proporcional ao board × 200 passos |

Para boards de brainstorming (dezenas ou centenas de cards de poucos bytes), a memória é
irrelevante e a robustez compensa.

### `Board.restore(foto)` — atualizar no lugar

```
1. Remover conexões cujo id não está na foto
2. Remover cards cujo id não está na foto        (removeCard também tira setas)
3. Para cada card da foto:
      existe? → aplicar os valores no MESMO objeto Card
      não?    → criar Card com o id da foto e adicionar
4. Para cada conexão da foto que não existe: recriar ligando aos cards pelos ids
```

Por que no lugar, e não `boardView.setBoard(boardNovo)`?

- Os `CardView` existentes continuam (sem recriar nós, sem piscar).
- A seleção de cards que continuam existindo é preservada.
- As listas observáveis só notificam o que de fato entrou ou saiu.

### Detecção de "nada mudou"

`BoardSnapshot`, `CardState` e `ConnectionState` são **records**, que implementam `equals` por
valor. `antes.equals(depois)` diz se a ação mudou algo. Clicar num card sem arrastar, por exemplo,
não gera passo.

### Interação com a edição de texto

- Foto tirada quando o texto ganha foco; registro quando perde.
- `MainController.undo()` chama `boardView.requestFocus()` **antes** de desfazer: se havia uma
  edição em andamento (desfazer pelo menu com o mouse), ela é encerrada e registrada primeiro, e
  só então o desfazer acontece. Sem isso, o registro da edição chegaria depois e bagunçaria a pilha.

## 4.8 Persistência

### Por que um formato separado do modelo

O modelo tem propriedades JavaFX e referências entre objetos (`Connection` aponta para `Card`).
Serializar isso diretamente com Gson seria frágil. `BoardFileFormat` são records simples, com ids no
lugar de referências, que espelham exatamente o JSON.

### Robustez ao abrir

| Situação | Tratamento |
|---|---|
| Campo ausente (arquivo antigo) | formato → `RECTANGLE`; tamanho → padrão do formato; cor → padrão |
| Formato desconhecido | `CardShape.fromName` → `RECTANGLE` |
| Cor inválida | `CardView` usa a cor padrão na tela |
| Seta para card inexistente | ignorada |
| JSON inválido | `IOException` com mensagem amigável |
| `version` maior que a suportada | recusa abrir (arquivo de versão futura) |

### Gravação segura

Grava em `arquivo.json.tmp` e depois **move** sobre o original (atômico quando o sistema permite).
Se o programa cair no meio da gravação, o arquivo anterior continua intacto.

## 4.9 Módulos Java (`module-info.java`)

```java
module com.danilo.boardvisual {
    requires javafx.controls;
    requires com.google.gson;
    exports com.danilo.boardvisual;                               // JavaFX instancia BoardVisualApp
    opens com.danilo.boardvisual.persistence to com.google.gson;  // Gson lê os records por reflexão
}
```

- Sem `exports`, o JavaFX não consegue criar a `Application`.
- Sem `opens`, o Gson falha em tempo de execução ao ler/escrever os records.
- Se um dia outro pacote for serializado por reflexão, ele precisa de `opens` também.

## 4.10 Foco do teclado

"Foco" é qual nó recebe as teclas. Pontos em que o projeto move o foco de propósito:

| Momento | Para onde | Por quê |
|---|---|---|
| App abre | `BoardView` | atalhos funcionam sem clicar |
| Clique no fundo ou no corpo de um card | `BoardView` | sair da edição de texto |
| Card criado | `TextArea` do card | já digitar |
| Esc durante edição | `BoardView` | sair da edição |
| Desfazer/refazer | `BoardView` | encerrar edição antes (ver 4.7) |

Botões das barras têm `setFocusTraversable(false)`: clicar neles **não** tira o foco do texto que
você estava editando.

---

# Nível 5 — Guia de extensão

## 5.1 Adicionar uma propriedade ao card (ex.: `locked`)

É o caso mais sujeito a esquecimentos. **Checklist:**

- [ ] `Card`: campo como propriedade + getter/setter/`xxxProperty()`
- [ ] `Card.copy()`: copiar o valor (se fizer sentido na duplicação)
- [ ] `BoardSnapshot.CardState`: novo componente + `of()` + `applyTo()` → **senão desfazer ignora a propriedade**
  (exemplo completo: veja como `panelMode`, `detailText` e `listItems` foram adicionados)
- [ ] `BoardFileFormat.CardData`: novo componente → **senão não salva**
- [ ] `BoardStorage.toData` / `fromData`: mapear, com valor padrão para arquivos antigos
- [ ] `CardView`: refletir visualmente (pseudo-classe ou variável CSS), se houver efeito visual
- [ ] `app.css`: estilo
- [ ] Controller: quem altera deve usar `history.perform`
- [ ] Testes: `BoardStorageTest` (salvar/abrir) e `BoardSnapshotTest` (desfazer)

> **Sugestão:** um teste que cria um card com **todas** as propriedades diferentes do padrão e
> verifica que `copy`, snapshot/restore e salvar/abrir preservam tudo pegaria esquecimentos
> automaticamente.

## 5.2 Adicionar um formato (ex.: hexágono)

1. `CardShape`: `HEXAGON(largura, altura)`.
2. `CardGeometry.borderPoint`: `case HEXAGON -> ...` (o `switch` é exaustivo: **o compilador acusa se esquecer**).
3. `CardGeometry.contentInsets`: `case HEXAGON -> ...` (idem).
4. `Icons.shape`: `case HEXAGON -> "path..."` (idem).
5. `Tool`: `HEXAGON("Hexágono", KeyCode.H, CardShape.HEXAGON, Icons.shape(CardShape.HEXAGON), "dica")`.
6. `app.css`: `.card.shape-hexagon { -fx-shape: "..."; }` e incluir na regra de seleção de formatos não retangulares.
7. Teste em `CardGeometryTest`.

A barra de ferramentas, o seletor de formato da barra flutuante, o atalho e a persistência passam
a funcionar sem mais nada.

## 5.3 Adicionar uma ferramenta

1. Constante em `Tool` (nome, tecla, formato ou `null`, ícone, dica).
2. Comportamento no controller adequado, checando `activeTool.get() == Tool.NOVA`.
3. Cursor/estilo em `app.css` usando `:tool-nova`, se preciso.

## 5.4 Adicionar uma ação na barra flutuante

1. Botão em `SelectionToolbarView` (use `actionButton(tooltip, iconPath)` e um ícone em `Icons`).
2. Ação em `SelectionActionsController`, operando sobre `selectedCards()` dentro de `history.perform`.
3. Atalho em `KeyboardController`, se houver.

## 5.5 Mapa das próximas funcionalidades (Trello)

| Funcionalidade | Onde encaixa | Observações |
|---|---|---|
| **Múltiplos boards** | `MainController` + `BoardView.setBoard` já troca o board inteiro; `EditHistory.clear()` já existe | Decidir: abas, lista lateral ou só "abrir recente" |
| **Smart guides** | `CardDragController` no drag: comparar bordas/centros com outros cards, ajustar `dx/dy` (snap) e desenhar linhas no `overlayLayer` | Guias devem ficar em coordenadas de mundo |
| **Boards aninhados** | Card com `childBoard`; pilha de navegação no `MainController`; `setBoard` ao entrar/sair | Persistência precisa aninhar ou referenciar arquivos; histórico por board |
| **Lock de item** | Propriedade `locked` (checklist 5.1); `CardDragController` ignora cards travados; pseudo-classe `:locked` | Excluir/duplicar travados? decidir |
| **Setas com texto** | `Connection.label` (propriedade); `ConnectionView` com `Label` posicionado no meio por binding; `BoardSnapshot.ConnectionState` e `ConnectionData` | Hoje `Connection` é imutável — label será a primeira parte mutável |
| **Agrupar** | `groupId` no card, ou um modelo `Group`; seleção de um membro seleciona o grupo todo | Seleção múltipla e mover em grupo já existem |
| **Aviso de alterações não salvas** | Comparar `board.snapshot()` com a foto do último salvamento | O snapshot já resolve a detecção |

---

# Apêndices

## A. Decisões de projeto e trade-offs

| Decisão | Alternativa descartada | Motivo |
|---|---|---|
| Cards como nós reais do JavaFX | Desenhar tudo num `Canvas` | Texto editável, foco, seleção de texto e CSS "de graça" |
| Modelo com propriedades JavaFX | POJOs + sincronização manual | Binding elimina código de redesenho |
| Formato de arquivo separado do modelo | Serializar o modelo direto | Modelo evolui sem quebrar arquivos |
| Desfazer por fotos | Padrão Command | Menos código, menos bugs, suficiente para o tamanho dos boards |
| Seleção no `BoardView` | Seleção no modelo | Seleção é estado de interface, não é salva |
| Barra flutuante fora do `world` | Dentro do `world` | Não sofrer zoom |
| Caixa de seleção pega o que **toca** | Só o que está **dentro** | Mais fácil de usar; é uma linha para trocar |
| Arrastar fundo = caixa; pan com Espaço/meio/direito | Arrastar fundo = pan | Padrão Figma/Excalidraw (decidido com você) |
| Sem framework de injeção de dependência | Spring/Guice | Escopo pequeno; `MainController` monta tudo à mão |
| Sem abstração de persistência | Interface para "futuro banco" | Só existe JSON local; evitar over-engineering |
| Paleta fixa de cores | Seletor livre | Consistência de categorias |
| `TextArea` com alinhamento via CSS interno | `TextField` / `Label`+edição | `TextField` é uma linha só; ver 4.5 |
| Compilar com `release 17` | Java 25 | Maven do terminal usa JDK 17; o IntelliJ usa 25 |

## B. Armadilhas conhecidas

1. **CSS vence código.** Um valor definido no `app.css` sobrescreve `setPadding`, `setCursor` etc.
   feitos no Java (4.5).
2. **Popups não herdam o CSS** da janela automaticamente (4.5).
3. **`pickOnBounds`** padrão dos `Region` é o retângulo; formatos precisam de `false` (4.4).
4. **Nova propriedade no card** exige mexer em 5 lugares (5.1).
5. **Dependências de binding**: esquecer uma = tela desatualizada (4.1).
6. **Atalhos de uma letra** precisam checar `isEditingText()` em vez de confiar que o campo de texto
   consumiu a tecla (3.10).
7. **Desfazer nativo do `TextArea`** guarda a sessão inteira do campo: editar, sair, voltar e usar
   `Ctrl+Z` dentro do texto pode desfazer a digitação anterior também. O desfazer do board segue
   correto.
8. **`module-info`**: pacote lido por reflexão precisa de `opens` (4.9).
9. **Ordem de registro de filtros** no mesmo nó importa: o `CardDragController` registra antes do
   `ConnectionController` (ordem do inicializador no `MainController`), então a seleção é ajustada
   antes de uma conexão consumir o evento.
10. **Z-order não é salvo**: trazer para frente só vale na tela; ao reabrir, vale a ordem da lista.
11. **Setas A→B e B→A** ficam sobrepostas (parecem uma seta de duas pontas).

## C. Testes

### Automatizados (`mvn test`) — 34 testes, sem abrir janela

| Classe | Cobre |
|---|---|
| `BoardTest` | regras de conexão, remoção em cascata, cópia de card |
| `CardShapeTest` | criação por formato, troca de formato mantendo o centro/tamanho |
| `CardGeometryTest` | ponto de contato da seta em cada formato |
| `BoardSnapshotTest` | foto/restauração: mudança de propriedades, card excluído com setas, card novo |
| `PanelCardTest` | modos do painel, conteúdo apagado na troca, cópia independente, foto/restauração do painel |
| `EditHistoryTest` | desfazer/refazer, ação sem mudança, gesto longo, sessões de edição atravessadas por ações |
| `BoardStorageTest` | salvar/abrir (inclusive painéis), arquivo antigo com campos faltando, JSON inválido |

Tudo que é **lógica** (modelo, geometria, histórico, persistência) é testável sem interface — é o
benefício direto da separação em camadas.

### Testes de interface (não versionados)

Durante o desenvolvimento, a interface foi validada com roteiros usando `javafx.scene.robot.Robot`,
que move o mouse e aperta teclas de verdade na janela, seguidos de snapshots da cena. Eles não estão
no repositório (dependem de tela, foco do sistema operacional e tempo). Lições aprendidas:

- O Windows descarta a primeira entrada enquanto ativa a janela de teste; é preciso "aquecer".
- Coordenadas fora da área visível da tela não geram evento algum.
- Clicar num popup logo após abri-lo falha: esperar a janela do popup existir.

Se quiser trazê-los para o projeto, o caminho padrão é **TestFX** (biblioteca de testes de interface
para JavaFX), em um perfil Maven separado para não rodar no `mvn test` comum.

## D. Glossário

| Termo | Significado |
|---|---|
| **Binding** | Ligação automática: uma propriedade passa a seguir outra (ou um cálculo). |
| **Propriedade observável** | Valor que avisa quando muda (`DoubleProperty`, `StringProperty`...). |
| **Scene graph** | Árvore de nós visuais do JavaFX. |
| **Nó (`Node`)** | Qualquer elemento da árvore: botão, forma, painel. |
| **`Region` / `Pane`** | Nós com tamanho, fundo, borda e CSS; `Pane` posiciona filhos livremente. |
| **`Group`** | Nó que só agrupa; aceita transformações (usado no `world`). |
| **Pan** | Mover a visão do canvas. |
| **Coordenadas de mundo** | Posição no board, independente de pan/zoom. |
| **Filtro de evento** | Handler que roda na descida, antes dos filhos. |
| **Consumir evento** | Parar a propagação do evento. |
| **Picking** | Decidir qual nó está sob o mouse. |
| **Pseudo-classe** | Estado aplicável no CSS (`:hover`, `:selected`...). |
| **Looked-up color** | Variável de cor do CSS do JavaFX (`-bv-accent`). |
| **Snapshot / foto** | Cópia imutável do estado do board para desfazer. |
| **Record** | Classe Java imutável com `equals` por valor. |
| **Marquee** | Caixa de seleção desenhada arrastando. |
| **Popup** | Janela pequena sem moldura (paleta de cores, seletor de formato). |

## E. Comandos úteis

```bash
mvn javafx:run          # rodar o app
mvn test                # rodar os testes
mvn clean test          # recompilar tudo do zero e testar
git log --oneline       # histórico de commits
```

# BoardVisual — Documentação do Projeto

> Documento de estudo e referência do projeto, mantido junto com o código: toda mudança relevante
> na arquitetura ou nas funcionalidades deve vir acompanhada da atualização deste arquivo.
> Estado descrito: até a exportação para PDF (card [3]), o salvamento em pasta (`board.json` + `imagens/`)
> as imagens nos cards (colar, arrastar, ferramenta Imagem), a exportação do board atual para PNG
> o link externo nos cards (marcador de clipe, menu de clique direito) e o link para outro board
> (atalho para qualquer board da árvore, com volta ao board de origem).

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
tudo numa **pasta** no computador (um `board.json` mais uma pasta `imagens/`). **Imagens** entram no board
como cards (colar, arrastar o arquivo ou escolher). Qualquer card pode ter um **link** para um site (um
clipe no card abre o endereço no navegador). Cards podem abrir um **painel flutuante** (texto ou lista)
ou conter um **board inteiro dentro deles** (boards aninhados, em qualquer profundidade), e qualquer card
pode ser um **atalho para outro board** da árvore, mesmo em outro ramo (seta curva no card).
Tudo pode ser exportado para um **PDF navegável**, com links entre os boards, e o board da tela
pode virar uma **imagem PNG** (fundo branco ou transparente).
Não há servidor nem banco de dados.

## Tecnologias

- **Java 17** (compila tanto no JDK 17 quanto no 25)
- **JavaFX 21** para a interface
- **Gson** para ler e escrever JSON
- **Apache PDFBox 3** para gerar o PDF
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
| `alignment` | nada (Java puro, sem JavaFX) | todo o resto |
| `persistence` | model | view, controller |
| `export` | model, alignment | view, controller (a imagem do board chega por uma interface) |
| `view` | model, alignment | controller, persistence |
| `controller` | model, view, persistence, export, alignment | — |

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
| `Card` | Um card: id, x, y, largura, altura, tamanho definido à mão, texto, tamanho da fonte, cor (hex), formato, imagem (nome do arquivo), link (endereço web), link para outro board (id do board de destino). Tudo como propriedade observável. |
| `BoardTree` | Consultas na árvore de boards (Java puro): caminho do principal até um board pelo id, lista de todos os boards com o caminho de cada um (indentada), ids da árvore. Usada pela navegação, pela lista de destinos, pelo marcador quebrado e pelo PDF. |
| `WebLink` | Regras do endereço web do card (Java puro): aceita só `http`/`https`, normaliza (`exemplo.com` → `https://exemplo.com`), recusa o resto com mensagem; forma ASCII entregue ao navegador e ao PDF. |
| `CardMarkers` | Marcadores na borda de cima do card (clipe do link externo, seta curva do link para outro board): quais o card tem e onde ficam em cada formato. A mesma conta serve à tela, ao espaço do texto e ao PDF. |
| `CardShape` | Enum dos formatos (`RECTANGLE`, `ELLIPSE`, `DIAMOND`) com o tamanho padrão de cada um. |
| `Connection` | Uma seta: id, card de origem, card de destino. Imutável. |
| `Board` | Conjunto de cards e conexões. Garante consistência (sem seta para card inexistente, sem seta duplicada). |
| `BoardSnapshot` | "Foto" imutável do board, usada pelo desfazer/refazer. |
| `PanelMode` | Enum do painel flutuante: `NONE` (card comum), `TEXT`, `LIST`. |
| `ListItem` | Um item da lista de um painel (objeto com `textProperty`, para binding por linha). |
| `BoardNames` | Nome exibido de cada board (pasta do save no principal, 1ª linha do card nos filhos), usado no caminho da tela e no PDF. |

Um `Card` pode ter um `childBoard` (outro `Board`), o que forma uma **árvore**: o board principal
contém cards, que contêm boards, que contêm cards... O `board.json` salva essa árvore inteira. Cada
board tem um **id único na árvore** (`Board.getId()`), que é como o link para outro board o encontra.

### `view/` — a aparência

| Arquivo | Responsabilidade |
|---|---|
| `MainWindow` | Layout da janela: menu, área do canvas (com a barra de ferramentas por cima), barra de status. |
| `BoardView` | O canvas: pan/zoom, camadas, cria/remove `CardView`/`ConnectionView` quando o board muda, seleção, caixa de seleção, posição da barra flutuante. |
| `CardView` | Desenho de um card: fundo com a silhueta do formato, imagem no topo (cards de imagem, ou o aviso "Imagem não encontrada"), campo de texto, marcadores (clipe do link, seta do link para outro board, com o estado "quebrado"), alça de conexão. |
| `CardImageLoader` | Interface "nome do arquivo → imagem carregada": a view não sabe onde as imagens moram; o controller sabe. |
| `ConnectionView` | Desenho de uma seta, calculado por binding a partir dos dois cards. |
| `CardGeometry` | Matemática: onde a seta toca o contorno de cada formato; recuo do texto dentro do formato. |
| `CardTextFit` | Mede o texto e decide a fonte e o tamanho do card para o texto caber inteiro (3.14). |
| `Tool` | Enum das ferramentas: nome, tecla de atalho, formato que cria, ícone, dica. |
| `ToolBarView` | Barra lateral de ferramentas + bolinha de cor dos novos cards. |
| `SelectionToolbarView` | Barra flutuante acima da seleção: cor, formato (some se só há imagens), tamanho do texto, modo do painel (Texto/Lista), link e link para outro board (só com um card), duplicar, excluir. |
| `LinkEditorPopup` | Popup pequeno com o campo do endereço do link (Salvar, Cancelar, Remover), com a mensagem de erro da validação. |
| `BoardLinkPickerPopup` | Popup com a lista da árvore de boards (indentada, com o caminho de cada um) para escolher o destino do link para outro board; o board do card aparece desabilitado e o destino atual, marcado. |
| `FloatingPanelView` | O painel aberto ao lado de um card: cabeçalho + corpo de texto ou lista. |
| `BreadcrumbView` | Caminho no canto superior esquerdo (`Principal › board 2 › board 3`), com links e botão voltar (a dica diz para onde ele leva). |
| `ResizeHandlesView` | As 8 alças de redimensionamento em volta do card selecionado (em pixels de tela). |
| `BoardRenderer` | Desenha um board inteiro numa imagem, fora da tela, para o PDF (3.15) e o PNG (3.18); fundo branco ou transparente; links para boards que não existem mais saem com o marcador quebrado. |
| `PopupButton` | Base de "botão que abre um painelzinho". |
| `ColorPickerButton` / `ShapePickerButton` / `FontSizePickerButton` | Os botões com popup (cor, formato e tamanho do texto). |
| `ColorSwatchGrid` | Grade de bolinhas de cor. |
| `CardPalette` | As 8 cores oferecidas. |
| `Icons` | Ícones desenhados com path SVG (inclusive o clipe do link, a seta curva do link para outro board e o ícone de cada marcador, riscado quando quebrado). |

### `controller/` — o comportamento

| Arquivo | Responsabilidade |
|---|---|
| `MainController` | Monta e conecta tudo (recebe de fora quem abre links no navegador); ações de arquivo (novo, abrir, salvar, salvar como em pasta, limpeza de `imagens/`, exportar PDF, exportar PNG do board atual); desfazer/refazer do menu. |
| `ImageController` | Imagens: inserir (ferramenta Imagem, `Ctrl+V`, arrastar arquivo), pasta de trabalho das imagens ainda não salvas, carregar a imagem de cada card (com cache). |
| `CanvasController` | Mouse no fundo: pan, zoom, caixa de seleção, criar card. |
| `CardDragController` | Mouse no card: selecionar, Shift+clique, arrastar (inclusive em grupo), com alinhamento. |
| `CardResizeController` | Redimensionar o card selecionado pelas alças, com alinhamento das bordas puxadas (card de imagem: proporção da imagem). |
| `CardTextFitController` | Reaplica a fonte e o crescimento de cada card quando o texto, a fonte ou o tamanho mudam. |
| `ConnectionController` | Criar setas arrastando de um card a outro. |
| `SelectionActionsController` | Cor, formato, tamanho do texto, duplicar e excluir aplicados à seleção. |
| `TextEditController` | Registra a edição de texto no histórico (sessões de edição); duplo clique num card de imagem abre a legenda. |
| `PanelController` | Abrir/fechar painéis, itens da lista, troca de modo com confirmação. |
| `NavigationController` | Boards aninhados: entrar, voltar, ir direto a um nível, pular para qualquer board da árvore pelo id (`jumpTo`); guarda o board principal e sabe qual board está na tela e o caminho de nomes até ele. Mostra o resultado; a pilha fica no `NavigationPath`. |
| `NavigationPath` | A pilha de níveis da navegação, sem tela (testável): entrar, voltar, ir a um nível, pular (`jumpTo` reconstrói o caminho real do destino e guarda a pilha de origem para o voltar). |
| `KeyboardController` | Atalhos de teclado. |
| `CardContextMenuController` | Menu de clique direito nos cards (clique sem arrastar). Montado na hora a partir de quem se registra (`addContributor`): é onde as próximas funcionalidades plugam itens. |
| `LinkController` | Link externo: botão Link da barra flutuante, itens do menu de clique direito, popup de edição (com desfazer) e abrir o endereço pelo clipe do card (`Opener`, na aplicação o `HostServices`). |
| `BoardLinkController` | Link para outro board: botão de seta curva da barra flutuante, itens do menu de clique direito, lista de destinos (com desfazer), ir ao destino pelo marcador e marcador quebrado quando o destino foi excluído. |
| `EditHistory` | Pilha de desfazer/refazer. |

### `alignment/` — geometria de caixas (alinhamento e redimensionamento)

Pacote de **Java puro** (nem JavaFX, nem modelo): só retângulos e números. Por isso é fácil de testar
e de reaproveitar — o mesmo alinhamento serve ao arrastar e ao redimensionar (ver 5.5).

| Arquivo | Responsabilidade |
|---|---|
| `Box` | Retângulo (x, y, largura, altura); dá o valor de cada linha e faz `translate`/`union`. |
| `Axis` | `X` (linhas verticais: esquerda/centro/direita) ou `Y` (horizontais: topo/meio/base). |
| `Edge` | `START`, `CENTER`, `END` — uma das três linhas de uma caixa num eixo. |
| `Guide` | Linha guia a desenhar (eixo, posição, início e fim). |
| `SnapResult` | Ajuste em X e Y para grudar + lista de guias. |
| `AlignmentGuides` | O algoritmo: `snap(caixa, linhasX, linhasY, outras, limite)` e o atalho `snapMove`. |
| `ResizeHandle` | As 8 alças (N, NE, E, SE, S, SW, W, NW), cada uma dizendo qual linha de cada eixo ela puxa. |
| `BoxResizer` | Nova caixa ao arrastar uma alça: aplica alinhamento nas linhas puxadas, tamanho mínimo e proporção. |

### `persistence/` — o save em disco

| Arquivo | Responsabilidade |
|---|---|
| `BoardFileFormat` | Records que espelham exatamente o `board.json`; as constantes de versão (2 = save em pasta). |
| `BoardStorage` | Converte `Board` ⇄ JSON; grava o save em pasta com segurança (imagens antes do JSON); abre pasta, `board.json` ou `.json` antigo; diz se um destino de "salvar como" está livre. |
| `BoardImages` | A pasta `imagens/` do save: copiar arquivos para ela, achar um arquivo pelo nome (recusando nomes com pasta) e remover os que nenhum card usa. |
| `ImageImporter` | Traz uma imagem (arquivo ou imagem em memória) para uma pasta de imagens: confere o formato, reduz se passar de 2000 px, nome novo `<uuid>.png/.jpg`. |

### `export/` — o PDF e o PNG

| Arquivo | Responsabilidade |
|---|---|
| `PdfExporter` | Monta o PDF: páginas na ordem da árvore, caminho no topo, links (entre páginas e para sites), páginas de detalhes, marcadores. |
| `PdfText` | Fonte padrão do PDF (Helvetica), medida e quebra de linha, troca de caracteres sem suporte. |
| `PngExporter` | PNG de um board: área e escala (com os limites de tamanho), nome sugerido do arquivo, gravação segura. Sem JavaFX (3.18). |

### Outros

| Arquivo | Responsabilidade |
|---|---|
| `Main` | `main()` — só chama `Application.launch`. |
| `BoardVisualApp` | Cria janela, controller (passando `getHostServices()::showDocument` para abrir links), cena, aplica o CSS. |
| `module-info.java` | Declara o módulo Java (dependências e permissões de reflexão). |
| `resources/.../app.css` | **Toda** a aparência: paleta, bordas, sombras, cursores, formatos. |

## Como a aplicação é montada na inicialização

```
Main.main()
 └─ Application.launch(BoardVisualApp)
     └─ BoardVisualApp.start(stage)
         ├─ new MainWindow()                 ← cria BoardView, ToolBarView, SelectionToolbarView, menus
         ├─ new MainController(stage, window, getHostServices()::showDocument)
         │   ├─ new EditHistory(...)
         │   ├─ new CanvasController(...)    ← instala handlers no BoardView
         │   ├─ new CardDragController(...)  ┐
         │   ├─ new ConnectionController(...)├─ ainda sem cards: registram-se no "inicializador"
         │   ├─ new TextEditController(...)  ┘  que o BoardView chama para cada CardView criado
         │   ├─ new SelectionActionsController(...)
         │   ├─ new PanelController(...)
         │   ├─ new NavigationController(...)
         │   ├─ new ImageController(...)     ← dá ao BoardView o carregador de imagens; arrastar arquivo
         │   ├─ new CardContextMenuController(...) ← menu de clique direito (vazio; os outros põem itens)
         │   ├─ new LinkController(...)      ← botão Link, itens do menu, clipe do card abre o link
         │   ├─ new BoardLinkController(...) ← botão de seta curva, itens do menu, seta do card vai ao board
         │   ├─ new KeyboardController(...)  ← instala handlers de tecla na janela
         │   ├─ wireMenu()
         │   └─ newBoard()                   ← navigation.openRoot(new Board("Sem título"))
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
  seleção passa para as cópias — duplicar de novo cria uma "escada". Setas não são copiadas. Os links
  (para site e para outro board) vão junto: a cópia leva ao mesmo destino.
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
  o fundo nas ferramentas que não são Selecionar. O direito só vira pan **ao mover**: um clique direito
  parado sobre um card abre o menu do card (3.19).
- **Zoom:** roda do mouse ou pinça no touchpad, sempre em torno do cursor, entre 20% e 300%.

Nenhum dos dois altera o modelo: só mudam duas transformações (`Translate` e `Scale`) aplicadas ao
grupo que contém tudo. Ver 4.2.

## 3.9 Salvar e abrir

Um save é uma **pasta**, não mais um arquivo solto:

```
MeuBoard/
  board.json        o board principal, com a árvore inteira de boards aninhados
  imagens/          um arquivo por imagem (<uuid>.png ou .jpg); a pasta só nasce com a primeira imagem
```

```
Salvar:  Board → BoardData (records) → Gson → board.json.tmp → move para board.json
         (antes, se houver imagens a trazer, elas são copiadas para imagens/;
          depois, as que nenhum card usa são apagadas)
Abrir:   pasta, board.json ou .json antigo → Gson → BoardData → valida/completa → Board novo → BoardView.setBoard
```

Exemplo real do `board.json`:

```json
{
  "version": 2,
  "id": "7f3c…",
  "name": "Ideias",
  "cards": [
    { "id": "a1…", "x": 150.0, "y": 190.0, "width": 200.0, "height": 120.0,
      "text": "Ideia central", "color": "#FFF1A8", "shape": "RECTANGLE" },
    { "id": "f7…", "x": 420.0, "y": 160.0, "width": 320.0, "height": 219.0,
      "baseWidth": 320.0, "baseHeight": 180.0, "text": "praia ao sol",
      "color": "#FFFFFF", "shape": "RECTANGLE", "imageFile": "4c4e9a3e-….png" }
  ],
  "connections": [
    { "id": "c9…", "sourceId": "a1…", "targetId": "b2…" }
  ]
}
```

Abrir um board limpa o histórico de desfazer e volta a visão para 100%.

Com boards aninhados, o card leva um campo `childBoard` com um board completo dentro (mesma
estrutura, recursiva). **Salvar sempre grava o board principal**, mesmo que você esteja dentro de um
filho — o `MainController` pede o principal ao `NavigationController`, nunca o board da tela.
Os boards aninhados **compartilham a mesma pasta `imagens/`**: um save = uma pasta = uma árvore.

### Versão do arquivo

`version` sobe para **2** (save em pasta). A leitura aceita 1 e 2; o conteúdo do JSON é o mesmo, muda
só o contêiner. Campos novos entram com valor padrão para arquivos que não os têm, **sem novo bump**
(a versão só sobe se o significado de um campo existente mudar).

### O que o "Abrir" escolhe (decisão)

O seletor de **Abrir** mostra arquivos: você escolhe o **`board.json` dentro da pasta do board** — ou,
para um save antigo, o `.json` solto. Um único "Abrir..." cobre os dois formatos.

| O que você escolhe | O que acontece |
|---|---|
| `board.json` de uma pasta (`version` 2) | abre o save em pasta; "Salvar" grava na mesma pasta |
| `.json` solto (`version` 1) | abre normalmente; o primeiro "Salvar" vira "Salvar como" (pede a pasta nova) |
| outra coisa | mensagem de erro; o board que estava na tela continua |

Quem diferencia os dois é a **`version` gravada dentro do JSON**, não o nome do arquivo: um `.json`
versão 1 que por acaso se chama `board.json` continua sendo um save antigo. Já um arquivo versão 2
**precisa se chamar `board.json`** (senão recusa): é o nome fixo que garante que a pasta ao lado é a
pasta de um save, e não uma pasta qualquer cuja `imagens/` poderia ser limpa por engano.

Por que assim: é o que menos surpreende — o "Abrir..." continua igual para quem já tem saves antigos
(que abrem do jeito de sempre) e não precisa de um segundo item de menu. A alternativa, abrir
escolhendo a **pasta** (`DirectoryChooser`), deixaria os `.json` antigos fora do seletor. Se um dia
preferir, `BoardStorage.load` já aceita uma pasta direto: basta um item de menu "Abrir pasta...".

### Salvar e Salvar como

- **Salvar** (`Ctrl+S`) grava na pasta do save atual. Board novo, ou aberto de um `.json` antigo, ainda
  não tem pasta: cai no "Salvar como". O `.json` antigo **nunca é sobrescrito**.
- **Salvar como** (`Ctrl+Shift+S`) usa o seletor de arquivo só para escolher onde e com que nome: o nome
  digitado vira o nome da **pasta** (um `.json` digitado no fim é descartado). A sugestão é o nome do
  board; num save já em pasta, `<nome> - cópia` (com o nome igual o Windows "entraria" na pasta em vez de
  escolher um nome novo). Ele grava o board atual e **copia a `imagens/` do save de origem**.
- O nome do board principal é o nome da pasta: muda no "Salvar como" e, ao abrir, vem da pasta
  (renomear a pasta no Explorer vale na próxima abertura).
- Se o destino já existe (`BoardStorage.inspectTarget`): vazio ou inexistente → grava; já é um save →
  pede confirmação para substituir; tem outros arquivos → recusa. No Windows o seletor nunca devolve uma
  pasta existente (digitar o nome dela só "entra" na pasta), então essas duas guardas valem para outros
  sistemas.
- **Imagens a trazer** (`BoardStorage.save(board, pasta, origens)`): a pasta de trabalho com as imagens
  inseridas desde o último salvamento (3.17) e, no "Salvar como", também a `imagens/` do save atual.
  Board novo e board vindo de `.json` antigo não têm `imagens/` de origem: só a pasta de trabalho.

### Ordem de gravação (por que é segura)

1. cria a pasta (e as de cima) se não existir;
2. copia as imagens a trazer para `imagens/` (cada uma via arquivo temporário + move);
3. grava `board.json.tmp` e **move** sobre `board.json`;
4. só então, remove as imagens que ninguém usa (ver abaixo).

Imagens antes do JSON: se algo falhar no meio, nunca existe um `board.json` apontando para uma imagem
que não chegou; e o `board.json` anterior só é trocado no passo 3, de uma vez. A limpeza vem por
último pelo mesmo motivo: se falhar antes, o JSON antigo ainda aponta para arquivos que existem.

### Limpeza de imagens não usadas

`BoardImages.removeUnused(pastaDoSave, nomesReferenciados)` apaga de `imagens/` os arquivos que não
estão no conjunto e devolve os nomes apagados. Só mexe em arquivos da própria `imagens/` (subpastas e o
resto do save ficam). O `MainController.saveTo` chama logo depois de gravar o JSON, no save de destino,
com o conjunto de `ImageController.imagesInUse`:

- as imagens dos cards de **todos os boards da árvore** (os aninhados compartilham a mesma `imagens/`), e
- as citadas em **qualquer foto do histórico** (desfazer e refazer, de todos os boards, e a foto da edição
  de texto em andamento).

> **Por que o histórico entra (decisão).** Um card excluído e restaurado com `Ctrl+Z` *depois* de salvar
> voltaria sem a imagem se ela tivesse sido apagada. Com o histórico no conjunto, o arquivo só some no
> primeiro salvamento depois que ninguém mais pode trazê-lo de volta (ex.: depois de reabrir o board, que
> zera o histórico). O custo é guardar por um tempo arquivos de cards já excluídos.

Uma falha na limpeza não desfaz o salvamento (o JSON já está gravado): no pior caso sobra um arquivo,
limpo no próximo "Salvar". O "Salvar como" limpa só o destino; o save de origem não é tocado.

Como as imagens chegam à pasta e onde ficam antes do primeiro salvamento: 3.17.

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

## 3.11 Boards aninhados

Um card com `childBoard` diferente de `null` **contém um board**. O botão do card (seta →) entra
nele. O nome do board filho **é o texto do card** — não há um nome separado para manter em sincronia.

```
Ferramenta Board aninhado (B) + clique  → Card.create(...) + card.setChildBoard(new Board(""))
Botão do card (CardView.boardButton)
  → NavigationController.enter(card)
      guarda pan/zoom do nível atual
      empilha o nível (board filho + card que o contém)
      BoardView.setBoard(filho)   ← a mesma view passa a mostrar outro board
      EditHistory.boardChanged()  ← desfazer passa a usar as pilhas do filho
      BreadcrumbView.setPath(...) ← caminho no topo
```

**Caminho (breadcrumb):** cada nível anterior é um `Hyperlink` que leva direto até ele
(`navigateTo(índice)` desempilha até lá); o nível atual aparece em negrito. Os nomes são bindings:
renomear o card atualiza o caminho na hora. A partir do primeiro filho aparece o botão ← (e o
atalho `Alt+←`).

**Cada nível lembra a própria visão** (`BoardView.ViewState`: pan e zoom). Entrar num filho começa
em 100%; voltar restaura onde você estava.

**A pilha mora no `NavigationPath`** (classe sem tela, testada no `NavigationPathTest`); o
`NavigationController` guarda a visão do nível atual, pede a mudança à pilha e, se algo mudou, mostra o
novo nível (`BoardView.setBoard`, `history.boardChanged`, caminho no topo). Depois de cada troca ele avisa
quem se registrou em `setOnBoardShown` (o `BoardLinkController` confere os marcadores).

**Pular para qualquer board (link para outro board, 3.20).** `NavigationController.jumpTo(id)`:

```
BoardTree.pathTo(raiz, id)             ← busca em profundidade a partir do principal
  vazio?           → NOT_FOUND (destino excluído: nada muda)
  é o board atual? → SAME_BOARD (nada muda)
pilha de antes → guardada no nível de chegada (returnPath)
pilha nova     = caminho real do destino (Principal › Projeto B › Custos);
                 níveis que já estavam na pilha (ex.: o principal) mantêm a visão guardada
```

**Voltar (botão ← e `Alt+←`) depois de um pulo (decisão):**

| Onde você está | O voltar leva a |
|---|---|
| No board de chegada do pulo | **o board de onde saiu**, com a pilha inteira de antes (cada nível com a sua visão) |
| Num filho que você abriu a partir do destino | o pai, como sempre; voltando até o destino, o próximo voltar leva à origem |
| Num nível acima do destino que você clicou no caminho do topo | o pai, como sempre: o nível de chegada saiu da pilha, e a volta à origem foi junto |
| No principal, chegando por um pulo | a origem (o botão ← aparece mesmo no principal) |

Pulos encadeados voltam um a um. A dica do botão ← diz para onde ele leva ("Voltar para "X", de onde você
veio"). Se a origem deixou de existir enquanto você estava fora (o card que a continha foi excluído), o
voltar sobe ao pai; se ela foi excluída e o desfazer a trouxe de volta (objetos novos, mesmos ids), a pilha
é refeita pelo id. Navegar não entra no desfazer.

**Desfazer por board:** o `EditHistory` guarda pilhas separadas por board (`WeakHashMap<Board, …>`).
`Ctrl+Z` age só no board da tela. Para isso funcionar, `Board.restore` **não mexe no conteúdo de
boards filhos de cards que continuam existindo** — senão desfazer uma cor no pai desfaria também o
que você fez dentro do filho depois. Já um card **recriado** (desfazer uma exclusão) volta com a
árvore inteira, porque a foto (`BoardSnapshot`) inclui os filhos recursivamente.

**Duplicar** um card-board copia a árvore inteira (`Board.deepCopy`), independente da original. O board
copiado e **todos os boards dentro dele** ganham ids novos (o id de board é único na árvore; antes, os
filhos da cópia repetiam os ids, e o `BoardStorage` corrige isso ao abrir saves antigos). Por isso um link
para outro board, mesmo dentro da cópia, continua apontando para o **original** (3.20).

**Visual:** o card-board tem uma "folha" deslocada atrás dele (CSS com camadas de fundo e insets
negativos, via pseudo-classe `:has-board`), para parecer uma pilha.

## 3.12 Alinhamento simplificado (linhas guia)

Ao arrastar, as 6 linhas da caixa que está se movendo (esquerda, centro, direita, topo, meio, base)
são comparadas com as 6 linhas de cada outro card. Se alguma ficar a menos de **6 pixels de tela**, o
movimento é ajustado para encostar exatamente, e aparece uma linha guia tracejada.

```
MOUSE_PRESSED (CardDragController)
  caixa inicial = união dos cards selecionados   (vários cards alinham como um bloco)
  referências   = caixas dos outros cards        (não mudam durante o arrasto: calcula uma vez)
MOUSE_DRAGGED
  deslocamento livre (dx, dy) do mouse
  r = AlignmentGuides.snapMove(caixaInicial + (dx, dy), referências, 6 / zoom)
  aplica (dx + r.dx, dy + r.dy) em todos os selecionados
  BoardView.showGuides(r.guides())
MOUSE_RELEASED
  BoardView.hideGuides(); history.record(...)
```

- **Cada eixo é independente:** pode grudar só na horizontal, só na vertical ou nas duas.
- **Escolha:** em cada eixo vence o candidato mais próximo. Depois do ajuste, as guias são geradas
  para **todas** as linhas que coincidem, e cada guia vai de uma ponta à outra das caixas envolvidas
  (se três cards estão alinhados, a guia atravessa os três).
- **Limite em pixels de tela:** `6 / zoom` em unidades do board. O "ímã" tem a mesma força com
  qualquer zoom (a 200%, 6 px de tela = 3 unidades do board).
- **Formatos:** elipse e losango alinham pela caixa que os envolve, como nos editores gráficos.
- **Alt:** segurado durante o arrasto, desliga o alinhamento.
- **Desenho:** as guias ficam numa camada do `BoardView` fora do `world` (em pixels de tela), então a
  linha tem sempre 1px, e é posicionada em meio pixel para ficar nítida.

## 3.13 Redimensionar cards

Com **exatamente um** card selecionado e a ferramenta Selecionar, aparecem alças em volta dele
(`ResizeHandlesView`, desenhada pelo `BoardView` em pixels de tela, como as guias).

| Formato | Alças | Comportamento |
|---|---|---|
| Retângulo | 8 (cantos e bordas) | Largura e altura livres; `Shift` num canto mantém a proporção |
| Elipse, losango | 4 (cantos) | Sempre mantêm a proporção (a área útil para texto depende das duas dimensões) |
| Card de imagem | 4 (cantos) | Sempre a proporção **da imagem**, mesmo com legenda embaixo (3.17) |

```
MOUSE_PRESSED na alça (CardResizeController)
  caixa inicial + referências (outros cards) + foto para o desfazer
MOUSE_DRAGGED
  BoxResizer.resize(caixaInicial, alça, dx, dy, mínimo, manterProporção, referências, 6 / zoom)
    1. puxa só as linhas da alça (borda direita → largura; borda esquerda → x e largura)
    2. alinhamento só nessas linhas (AlignmentGuides.snap com os Edges da alça)
    3. aplica o piso de tamanho (60 × 40) com o lado oposto parado
  fitText: aumenta a caixa até o texto caber (CardTextFit, 3.14), lado oposto parado
  card.resize(...) → vira o "tamanho definido à mão"; mostra as guias
MOUSE_RELEASED
  esconde as guias; history.record(foto)  → um passo no desfazer
```

- **O lado oposto à alça fica parado** (puxar a borda esquerda não mexe na direita).
- **Tamanho mínimo = o que o texto precisa** (na fonte mínima, se a fonte é automática), com um
  piso de 60 × 40 para cards quase vazios. Passando disso, a alça "trava": o card não encolhe mais e
  o lado oposto continua parado (puxar a borda de cima para baixo não empurra a de baixo). Enquanto
  trava, as guias somem (a caixa não é mais a do alinhamento).
- **O tamanho final vira o "tamanho definido à mão"** (`Card.resize`), a base do crescimento
  automático (3.14).
- **Alt** desliga o alinhamento, como ao mover.
- Setas, painel flutuante e barra flutuante acompanham sozinhos: todos dependem de largura e altura
  do modelo por binding ou listener.
- **Alça de conexão** (bolinha azul) foi afastada 14 px para fora da borda direita, para não disputar
  lugar com a alça de redimensionar dessa borda.
- **Alças × barra flutuante:** com alças visíveis, a barra fica 24 px acima do card (em vez de 10).
  Antes, num card pequeno, a barra ficava a 6 px das alças de cima e "comia" o clique (bug encontrado
  no uso). Além disso, a área clicável de cada alça é de 17 px — o CSS desenha só o quadrado de 9 px no
  centro (`-fx-background-insets`), o resto é margem transparente que também aceita clique.
- **Card pequeno na tela** (ou zoom baixo): as alças do meio das bordas somem quando não há espaço
  para elas entre os cantos (menos de 3 alças de largura/altura); os cantos sempre ficam.
- **Decisão:** o card [1] previa a propriedade de "tamanho definido à mão"; ela entrou junto com o
  crescimento automático (3.14), quando passou a ter uso.

## 3.14 Fonte automática e crescimento do card

Objetivo: **nenhum card fica com rolagem**; o que aparece na tela é o que vai para o PDF (card [3]).

Dois tamanhos no `Card`:

| Propriedade | O que é | Quem muda |
|---|---|---|
| `baseWidth` / `baseHeight` | Tamanho **definido à mão** | Criar card, trocar formato (se ainda no padrão), redimensionar — sempre por `Card.resize(w, h)` |
| `width` / `height` | Tamanho **exibido** (≥ o definido à mão) | `CardTextFitController`, quando o texto não cabe |

E a fonte: `fontSize` = `Card.AUTO_FONT_SIZE` (0, automático) ou um valor manual em px.

```
texto / fonte / tamanho à mão / formato / botão do card mudou   (CardTextFitController)
  CardTextFit.fit(card)  — sempre a partir do tamanho definido à mão
    automático: maior fonte entre 9 e 24 px em que o texto cabe
                (altura das linhas + cada palavra inteira na largura; busca binária de 0,5 em 0,5)
      nem 9 px cabe → fonte 9 e o card cresce
    manual: a fonte escolhida; se não cabe, o card cresce
    crescer: retângulo → só a altura (conta direta); elipse/losango → escala os dois lados
             (busca binária), mantendo a proporção
  CardView.setTextFontSize(fonte)       (estilo inline no TextArea)
  card.width/height = resultado; x ajustado para o centro da largura não mudar
```

- **Cresce para baixo:** o topo fica parado. Elipse e losango crescem para os dois lados, com o centro
  horizontal parado.
- **Encolhe sozinho:** como o cálculo parte sempre do tamanho definido à mão, apagar texto volta a
  ele — nunca menor.
- **Aumentar o card à mão** muda o tamanho definido à mão, então a fonte automática cresce de novo
  (até 24 px).
- **Card vazio:** a fonte se ajusta ao texto de exemplo ("Escreva uma ideia..."), mas o exemplo nunca
  faz o card crescer.
- **Fonte manual:** botão na barra flutuante (mostra "Auto" ou o número; "–" com seleção mista),
  com "Automático" e 10–48 px. É um passo no desfazer.
- **Desfazer:** o crescimento durante a digitação acontece dentro da sessão de edição (4.7), então
  sai junto com o texto num único `Ctrl+Z`. As fotos guardam os dois tamanhos e a fonte; ao
  restaurar, o recálculo chega ao mesmo resultado.
- **Por que no controller e não no modelo:** medir texto depende das métricas de fonte do JavaFX
  (camada de tela). O modelo só guarda o resultado; os testes do cálculo usam um medidor falso
  (`CardTextFit.TextMeasurer`).
- **Não evita sobreposição:** um card que cresce pode cobrir o de baixo; o usuário reorganiza.

Detalhes da medição (por que o texto cabe de verdade) em 4.6.

## 3.15 Exportar para PDF

`Arquivo › Exportar PDF...` (`Ctrl+E`) exporta o **board principal e todos os aninhados**, qualquer
que seja o board aberto na tela.

```
MainController.exportPdf()
  escolhe o arquivo (nome sugerido: o da pasta do save ou o do board)
  new PdfExporter(new BoardRenderer(css da janela)::render).export(raiz, arquivo)
    árvore: BoardNode por board (nome por BoardNames), PanelDetails por painel com conteúdo
    1ª passada, na ordem das páginas (board → detalhes dele → filhos, recursivo):
      tamanho da página; pede a imagem ao BoardRenderer; cria as páginas (vazias)
    2ª passada: desenha cada página e cria os links (os destinos já existem)
    marcadores com a mesma árvore; grava em .tmp e move (como o JSON)
```

**Página de um board**

| Parte | Como |
|---|---|
| Tamanho | O do conteúdo (cards + margem de 32), 1 unidade do board = 1 pt; boards maiores que 14.000 pt são reduzidos (limite do formato PDF) |
| Caminho no topo | `Principal › board 2 › board 3` em texto de verdade; cada nível anterior é link para a página dele |
| Board | **Imagem** feita pelo `BoardRenderer`, com 2 pixels por ponto (nítida no zoom e na impressão) |
| Cards clicáveis | Card de board aninhado → página do filho; painel com conteúdo → primeira página de detalhes; card com link para outro board → página do destino (3.20); card com link externo → o site (3.19). O card todo leva ao primeiro destino da ordem página própria › outro board › site; os demais ficam só na área do marcador, por cima |
| Texto invisível | O texto de cada card, invisível, sobre o card: buscar (`Ctrl+F`) e copiar funcionam. Num card de imagem, só sobre a faixa da legenda (`PdfExporter.textRect`) |
| Imagens dos cards | Vêm na própria imagem do board: o `BoardRenderer` recebe o mesmo carregador de imagens da tela (`ImageController.loader()`), então inclui as ainda não salvas; imagem que falta sai com o aviso |

**Página de detalhes** (painel flutuante): A4, título (texto do card), link `‹ Voltar ao board`, e o
conteúdo em texto de verdade — parágrafos no modo texto, marcadores `•` no modo lista (itens vazios
somem). Conteúdo longo continua nas páginas seguintes ("(continuação)"). Painel sem conteúdo não gera
página nem link.

**`BoardRenderer`** monta `CardView`s e `ConnectionView`s numa `Scene` própria, fora da janela, com o
mesmo CSS, e tira um snapshot. Não usa o `BoardView` (sem seleção, barras, pan/zoom) e **não altera o
modelo**: a fonte de cada card é calculada com o `CardTextFit` e aplicada só na cópia visual. A classe
`board-export` no CSS esconde o texto de exemplo de cards vazios e a alça de conexão.

**Por que a imagem vem por uma interface** (`PdfExporter.BoardImageRenderer`): o `export` não
conhece JavaFX, então os testes geram PDFs reais com uma imagem em branco e conferem a estrutura
(páginas, links, marcadores, texto) lendo o arquivo de volta com o próprio PDFBox.

Decisões tomadas na implementação (o card deixou "a decidir"):
- **Tamanho da página = tamanho do conteúdo**, não A4: o board não fica cortado nem minúsculo; o
  leitor de PDF ajusta à tela. As páginas de detalhes, que são texto corrido, usam A4.
- **Board grande = uma página só**, reduzida se passar de 14.000 pt. Dividir em várias páginas
  quebraria os cards e os links.

## 3.16 Atalhos de teclado

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
| Ctrl+N/O/S/Shift+S, Ctrl+E, Ctrl+Shift+E, Ctrl+0 | aceleradores de menu | aparecem no menu |
| Ctrl+Z, Ctrl+Y, Ctrl+A | aceleradores de menu | idem; e só disparam se o texto não usar a tecla |
| V R E L P B I C, Delete, Ctrl+D, Ctrl+V, Ctrl+Shift+Z, Alt+←, Espaço, Esc | `KeyboardController` | teclas sem item de menu ou com lógica condicional |

**Popup do link (3.19):** o que se digita no campo do endereço nunca chega a estes atalhos — o popup
consome todas as teclas no fim do caminho (sem isso, "r" trocaria a ferramenta e `Delete` excluiria o
card selecionado enquanto você digita o endereço). Enter salva, Esc cancela. A **lista de boards** do
link para outro board (3.20) segue a mesma regra: setas mudam a linha marcada, Enter confirma, Esc
cancela, e letras ou `Delete` não chegam ao board.

`Alt+←` e o botão ← voltam ao board anterior; depois de um link para outro board, ao board de onde você
saiu (3.11).

`Ctrl+V` (colar imagem) segue a mesma regra das letras: com texto em edição ele nem chega ao
`KeyboardController` como atalho (`isEditingText()`), e o texto cola o que estiver na área de
transferência. Fora da edição, se não há imagem para colar, a tecla não é consumida.

## 3.17 Imagens nos cards

Uma imagem entra no board como um **card comum com um dado a mais**: `Card.imageFile`, o **nome do
arquivo** dentro de `imagens/` (nunca os bytes, nunca base64 no JSON — decisão do Danilo). Por ser um
card, mover, conectar, selecionar em grupo, duplicar, desfazer e salvar funcionam sem código novo.

### Três formas de inserir (`ImageController`)

| Como | Onde o card nasce | Detalhes |
|---|---|---|
| Ferramenta **Imagem** (`I` ou botão) | centro da área visível | Abre o seletor de arquivo (vários de uma vez) e volta para Selecionar: a ferramenta nunca fica ativa |
| **Colar** (`Ctrl+V`, fora da edição de texto) | centro da área visível | Imagem copiada (print, navegador, editor) ou arquivos de imagem copiados no Explorer |
| **Arrastar** arquivo para o canvas | onde foi solto | Filtros de `DRAG_OVER`/`DRAG_DROPPED` no `BoardView` (funcionam também soltando em cima de um card); a leitura acontece depois do evento, para liberar o Explorer |

Várias imagens de uma vez entram em cascata (24 px) e são **um único passo** no desfazer. O card nasce
com a proporção da imagem, no máximo 320 px no maior lado (imagens minúsculas sobem para 60 px), fundo
branco, sem legenda, e fica selecionado.

```
arquivo / imagem colada
  → ImageImporter.importFile / importImage(…, pastaDeTrabalho)   confere, reduz, grava <uuid>.png|.jpg
  → Card.createImage(nome, centro, largura, altura)
  → history.perform(() -> board.addCard(card))
  → BoardView cria o CardView → CardImageLoader acha o arquivo (pasta de trabalho, depois imagens/ do save)
```

### Formatos e tamanho (`ImageImporter`)

| Entrada | O que vai para a pasta |
|---|---|
| PNG ou JPG até 2000 px no maior lado | **os bytes originais**, com nome novo (`.png`/`.jpg`) |
| PNG ou JPG maior | reduzido para 2000 px no maior lado: JPG continua JPG (qualidade 0,9), PNG continua PNG (com transparência) |
| GIF | PNG do **primeiro quadro** (a tela e o PDF mostram imagem parada) |
| BMP | PNG (o BMP não tem compressão e deixaria o save pesado) |
| Imagem colada (sem arquivo) | PNG |
| Outro formato, arquivo quebrado | recusado com mensagem; nada é gravado |

O formato é decidido pelo **conteúdo** do arquivo (o leitor do ImageIO), não pela extensão: um JPG
renomeado para `.png` vira `.jpg`. Imagens enormes são lidas pulando pixels até ~2× o limite (economiza
memória) e depois reduzidas em etapas de metade (sem serrilhado). Tudo com o **ImageIO do módulo
`java.desktop`**, que o projeto já usava (4.9): nenhum módulo novo.

### Onde a imagem mora antes de salvar (pasta de trabalho)

Inserir uma imagem **não mexe na pasta do save**: o arquivo vai para uma **pasta de trabalho temporária**
(`%TEMP%\boardvisual-imagens-…`, criada na primeira imagem). O `CardImageLoader` procura primeiro nela e
depois na `imagens/` do save atual.

| Momento | O que acontece |
|---|---|
| Salvar / Salvar como | a pasta de trabalho é uma das origens copiadas para `imagens/` (antes do JSON, 3.9); depois do sucesso ela é esvaziada |
| Novo / Abrir outro board | a pasta de trabalho é esvaziada (imagens de um board não salvo são descartadas, como o texto) e o cache de imagens é limpo |
| Fechar o programa | a pasta de trabalho é apagada (gancho de encerramento da JVM) |

Assim um board novo (sem pasta), um board vindo de `.json` antigo e um save em pasta funcionam igual.

### Aparência e legenda (`CardView`, `CardTextFit`)

```
┌─────────────────────┐  ← imagem: largura do card, altura = largura × (altura÷largura à mão)
│      (imagem)       │     recortada com os cantos do card
│                     │
├─────────────────────┤
│   legenda (texto)   │  ← só existe se o card cresceu para ela
└─────────────────────┘
```

- **Invariante:** num card de imagem, o **tamanho definido à mão é o tamanho da imagem** (tem a proporção
  dela). A imagem é desenhada com `Card.imageHeight(largura)`; o texto do card é a **legenda**, recuada
  para baixo da imagem (`CardGeometry.contentInsets(…, alturaDaImagem)`).
- **Sem legenda não há o que encaixar:** o `CardTextFit` devolve o próprio tamanho (texto vazio nunca faz
  o card crescer). **Com legenda** vale o crescimento de sempre (retângulo cresce só na altura), então a
  imagem não distorce. A fonte "automática" da legenda é fixa em **14 px** (`CAPTION_FONT_SIZE`): o
  espaço do texto começa em zero, e "a maior fonte que cabe" sairia sempre a mínima. Fonte manual vale.
- **Escrever a legenda:** **duplo clique** na imagem (não há área de texto para clicar). Enquanto a
  legenda vazia está em edição, o card abre uma linha com "Escreva uma legenda...". Sair sem digitar
  devolve o tamanho e **não cria passo** no desfazer: o crescimento ao ganhar o foco espera o
  `TextEditController` tirar a foto do início da edição (`Platform.runLater`); ao perder o foco, encolhe
  antes de ele registrar.
- **Sempre retângulo:** o botão de formato some quando só há imagens selecionadas; numa seleção mista, o
  formato vale só para os outros cards.
- **Redimensionar:** só os 4 cantos, sempre na proporção da imagem (`CardResizeController.resizeImage`,
  com o `BoxResizer` no modo de proporção). Quem muda é a imagem; a legenda acompanha embaixo. Numa alça
  de cima, a base do card (fim da legenda) fica parada. O mínimo vale para o **maior lado** (40 px), para
  um panorama 20:1 não "pular" de tamanho.
- **Duplicar** reaproveita o mesmo arquivo (`Card.copy` copia o nome).
- **Excluir** não apaga o arquivo: a limpeza só acontece ao salvar (3.9).

### Imagem faltando ou corrompida

Ao abrir, o modelo nem olha os arquivos: o card vem com o nome que estiver no JSON. Se o `CardImageLoader`
não acha o arquivo (ou o JavaFX não consegue abri-lo), o card mostra um **aviso cinza "Imagem não
encontrada"** do mesmo tamanho (com o nome do arquivo na dica) e **mantém a referência**: salvar de novo
não perde o dado, e devolver o arquivo à `imagens/` faz a imagem voltar ao reabrir. Nomes com pasta
(`../x.png`, editados à mão) nunca são procurados fora da pasta (`BoardImages.isPlainFileName`).

### Fora do escopo (ficou o caminho aberto)

- **Exportar PNG:** feito em 3.18, com o mesmo `CardImageLoader`. **SVG** continua fora (tarefa 8).
- Copiar/colar **cards** entre boards (tarefa 6): o `Ctrl+V` de hoje só trata imagens; quando houver
  cards na área de transferência interna, decidir a prioridade no `KeyboardController`.

## 3.18 Exportar PNG do board atual

`Arquivo › Exportar PNG...` (`Ctrl+Shift+E`) grava uma imagem **só do board que está na tela** — o
principal ou o aninhado que estiver aberto. Para a árvore inteira existe o PDF (3.15).
`Arquivo › Exportar PNG com fundo transparente...` faz o mesmo sem o fundo branco.

```
MainController.exportPng(fundo)
  boardView.requestFocus()            conclui uma edição de texto em andamento (como no PDF)
  board = navigation.getCurrent()     o board da tela
  board vazio? → aviso "Este board está vazio." e para (nem abre o seletor)
  seletor de arquivo; nome sugerido = PngExporter.suggestedFileName(caminho de nomes)
  new PngExporter((b, área, escala, fundo) -> BoardRenderer.render(..., transparente?))
      .export(board, arquivo + ".png" se faltar, fundo)
    PngExporter.plan(board): área = PdfExporter.contentBounds (cards + 32), escala = 2 px/unidade ou menos
    BoardRenderer.render → BufferedImage (RGB com fundo branco, ARGB com fundo transparente)
    ImageIO.write em .png.tmp na mesma pasta → move para o nome final (nunca fica arquivo pela metade)
```

| Parte | Como |
|---|---|
| O que entra | Exatamente o que o PDF desenha: cards (formatos, cores, fonte), setas, painéis flutuantes **fechados**, pilha do board aninhado, imagens com legenda. Nada de interface: seleção, alças, barra flutuante, caixa de seleção, alça de conexão e cursor de texto ficam de fora, porque o `BoardRenderer` não usa o `BoardView` |
| Imagens dos cards | O mesmo `ImageController.loader()` da tela: vêm da pasta do save ou, em board ainda não salvo, da pasta de trabalho temporária |
| Tamanho | O do conteúdo (cards + margem de 32, como o PDF), a **2 pixels por unidade** do board |
| Limite | No máximo **8.000 px no lado maior** e **40 milhões de pixels** no total; acima disso a escala é reduzida (a imagem fica menos nítida, mas sai inteira). São os mesmos limites da imagem de cada board no PDF |
| Fundo | Branco (padrão) ou transparente: sombras e bordas suavizadas ficam semitransparentes, então a imagem pode ser colada sobre qualquer cor |
| Nome sugerido | O do board, como no PDF (nome da pasta do save, do `.json` antigo ou "Sem título"); num aninhado, o caminho com ` - ` entre os níveis: `Meu board - Projeto - Etapa 1.png`. Caracteres que o Windows não aceita (`\ / : * ? " < >` e a barra vertical) viram `_`; nome limitado a 120 caracteres |
| Estado | Exportar não mexe em seleção, pan/zoom, desfazer nem título; o board não é alterado (a cópia visual é montada à parte) |

**Por que o limite é esse.** Cada cópia da imagem ocupa 4 bytes por pixel; durante a exportação
existem três ao mesmo tempo (snapshot do JavaFX, `int[]` copiado pelo `PixelReader` e `BufferedImage`),
então 40 milhões de pixels ≈ 480 MB no pico — o máximo razoável para a memória padrão da JVM. O lado de
8.000 px foi testado de verdade (o snapshot do JavaFX 21 divide em pedaços imagens maiores que a textura
da placa de vídeo): um board de 30.000 × 184 unidades sai com 8.000 px de largura e os dois extremos
aparecem (`BoardRendererPngTest`).

**Conversão sem `javafx.swing`.** O `WritableImage` do JavaFX não grava arquivo; o `BoardRenderer`
copia os pixels com o `PixelReader` (`getPixels` em ARGB) para uma `BufferedImage`, e o `ImageIO`
(módulo `java.desktop`, que já estava no projeto) grava o PNG. Nenhum módulo novo no `module-info`.

**Separação para teste** (como no PDF): `PngExporter` não conhece JavaFX — recebe um `Renderer`
(board, área, escala, fundo → `BufferedImage`). A conta de tamanho, o nome sugerido e a gravação
são testados sem tela; um teste de ponta a ponta liga o JavaFX e gera PNGs de verdade.

Decisões tomadas (o card deixou "a decidir"):
- **Fundo transparente = segundo item de menu**, não uma pergunta a cada exportação: o caso comum
  (branco) continua com um clique/atalho e nenhuma caixa extra; quem quer transparência acha a opção
  escrita no menu. O `PngExporter.Background` fica pronto para o SVG (tarefa 8) reaproveitar.
- **Board vazio = aviso**, sem gerar arquivo: uma imagem em branco não serve para nada e esconderia o
  engano (por exemplo, exportar estando dentro de um aninhado vazio).
- **"Só a seleção" ficou para depois:** com cards selecionados, exportar só eles mudaria o resultado
  conforme algo que não está no arquivo (a seleção), e decidir as setas entre selecionados e não
  selecionados pede um board temporário. Caminho: um terceiro item de menu que passa ao `BoardRenderer`
  só os cards selecionados e as setas entre eles, com a área calculada sobre eles.
- **"Todos os boards"** (uma pasta com um PNG por board) está fora desta tarefa; o PDF já cobre a árvore.

## 3.19 Link externo nos cards (e o menu de clique direito)

Qualquer card — comum, painel, board aninhado ou imagem — pode ter um **endereço web**: `Card.link`
(texto; vazio = sem link). Não existe um "card de link": é um atributo, então mover, duplicar, desfazer,
salvar e exportar funcionam como para o resto do card.

### Como adicionar, editar e remover

| Onde | Como |
|---|---|
| **Clique direito** no card (sem arrastar) | Menu: "Adicionar link..." (sem link) ou "Abrir link", "Editar link...", "Remover link" |
| **Botão Link** da barra flutuante (clipe) | Aparece só com **um** card selecionado; fica marcado se o card já tem link. Abre o mesmo popup |

O popup (`LinkEditorPopup`) tem o campo do endereço, "Salvar" (ou Enter), "Cancelar" (ou Esc, ou clicar
fora) e "Remover" (só se já há link). Salvar com o campo vazio também tira o link. Cada mudança é **um
passo** no desfazer (`history.perform`); salvar o mesmo link de novo não cria passo.

```
Salvar no popup
  → WebLink.check(texto)          inválido: mensagem no popup, nada muda (e o aviso passa a seguir a digitação)
  → LinkController.setLink        history.perform(() -> card.setLink(normalizado))
  → CardView escuta card.link     mostra o clipe; o texto do card desce para baixo dele (CardTextFit refaz a conta)
```

### Validação (`WebLink`, Java puro, testável)

O app entrega o endereço ao sistema para abrir, então só passa o que é claramente uma página web:

| Entrada | Resultado |
|---|---|
| `https://…`, `http://…` | aceito como está (o esquema vira minúsculo) |
| `exemplo.com`, `www.unit.br/x`, `localhost:3000` | ganha `https://` na frente (`site:porta` não é confundido com esquema) |
| `file:`, `javascript:`, `mailto:`, `ftp:`, `data:`… | recusado: "Só endereços da web são aceitos (http:// ou https://)" |
| `C:\…`, `\\servidor\…`, `/pasta`, qualquer `\` | recusado: caminhos do computador não são aceitos |
| espaço, quebra de linha, caractere de controle | recusado |
| `https://banco.com@golpe.com` (usuário antes do site) | recusado (truque comum para disfarçar o site real) |
| site sem domínio (`ola`), porta inválida, `%zz`, `<`… | recusado: endereço incompleto ou inválido |
| mais de 2.000 caracteres | recusado |

A regra vale em **três momentos**: ao digitar (mensagem para o usuário); no próprio modelo
(`Card.setLink` normaliza e transforma o que não passa em "sem link", e a propriedade é só leitura por
fora — o `Card` nunca guarda um link perigoso); e logo antes de abrir (`WebLink.toBrowserUri`). Um
`board.json` adulterado com `"link": "file:///C:/…"` abre com o card **sem link** (aviso no console).
Site com acento vai ao navegador e ao PDF na forma ASCII (`açaí.com.br` → `xn--aa-4iaz.com.br`).

### O marcador (`CardMarkers`, `CardView`)

Um botãozinho redondo de **18 px** com o clipe, na borda de cima do card:

| Formato | Onde | Por quê |
|---|---|---|
| Retângulo (e card de imagem) | canto de cima à direita, por dentro (6 px do topo, 9 px da direita) | longe da alça de redimensionar do canto (8,5 px para dentro) e da do meio da borda |
| Elipse, losango | no meio, em cima, descendo até caber inteiro na silhueta | espelho do botão do painel/board, que fica embaixo no meio; esses formatos só têm alças nos cantos |

- **Texto:** o texto começa abaixo da fileira de marcadores (`CardGeometry.contentInsets` com a quantidade
  de marcadores; `CardTextFit` usa a mesma conta, então a fonte automática diminui ou o card cresce um
  pouco no retângulo). No losango o recuo de cima já é maior que a fileira: nada muda. No card de imagem o
  clipe fica sobre a imagem.
- **Clique:** abre o link. Não seleciona, não traz para frente, não arrasta e não entra no texto
  (`CardDragController` e `ConnectionController` ignoram o clique quando `CardView.isMarkerNode`); funciona
  em qualquer ferramenta (com Conexão, não começa uma seta). A dica (tooltip) mostra o endereço.
- **Não atrapalha:** alça de conexão (fora do card, à direita), botão do painel/board (embaixo), alças
  de redimensionar e barra flutuante (acima do card) ficam livres — conferido no teste de interface.
- **Reaproveitável (a tarefa 3b usou assim, 3.20):** `CardMarkers.Kind` lista os tipos na ordem; um card pode ter vários, lado
  a lado. Para um marcador novo: constante em `Kind`, condição em `CardMarkers.of`, propriedade em
  `CardMarkers.dependencies`, ícone em `Icons.marker` e dica em `CardView.setUpMarkers` (os dois `switch`
  são exaustivos: o compilador aponta o que falta), e a ação no controller (`cardView.getMarker(kind)`).

### Abrir o endereço

`LinkController.open(card)` confere de novo (`WebLink.toBrowserUri`) e chama o `Opener` recebido pelo
construtor. Na aplicação ele é `getHostServices()::showDocument`, passado pelo `BoardVisualApp` ao
`MainController` (sem nada estático); nos roteiros de teste, um interceptador que só anota o endereço.
Se o `Opener` lança erro, aparece um aviso. **Limite:** no Windows o `HostServices` entrega o endereço ao
sistema e engole as falhas (só imprime no console), então "navegador não abriu" nem sempre vira aviso.

### O menu de clique direito (`CardContextMenuController`)

```
MOUSE_PRESSED direito   → CanvasController: começo de um possível pan (filtro, consome)
MOUSE_RELEASED direito  → se o mouse NÃO se mexeu (isStillSincePress) e há card sob ele:
                            seleciona o card (se ainda não estava na seleção)
                            pede itens a cada Contributor registrado e abre o ContextMenu
                          se mexeu: era pan, nada de menu
CONTEXT_MENU_REQUESTED  → consumido sobre cards (senão o TextArea abriria o menu Copiar/Colar por cima)
```

O menu é **montado na hora**: cada controller registra um `Contributor` (`itemsFor(contexto)` → itens), e
os grupos de contribuidores são separados por uma linha. Hoje contribuem o `LinkController` e o
`BoardLinkController` (3.20), nessa ordem; a ordem de empilhamento entrará como mais um contribuidor, sem
mexer no menu.
O contexto traz o card, o `CardView` e a posição de tela do clique (onde abrir um popup). Clique direito
no fundo não abre menu.

### PDF e PNG

- **PNG:** o clipe aparece desenhado (o `BoardRenderer` monta os mesmos `CardView`s) — é o aviso visual de
  que aquele card tem link.
- **PDF:** o clipe aparece na imagem do board e o card vira um `PDAnnotationLink` com ação **URI** (forma
  ASCII). Card sem outro destino: o card todo abre o site. Card que já leva a uma página do PDF (board
  aninhado, painel com detalhes): o card continua levando à página e só a **área do clipe** abre o site
  (anotação acrescentada depois, por cima; `PdfExporter.markerRect` usa a mesma conta da tela).

### Fora do escopo

Ordem de empilhamento, frames. (O link para outro board, que reaproveita o marcador e o menu, é a 3.20.)

## 3.20 Link para outro board (atalho entre caminhos)

Qualquer card pode ser um **atalho para qualquer board da árvore**, não só o pai ou o filho: no card
"Orçamento" do board A, um atalho leva direto a `Principal › Projeto B › Custos`. É um atributo do card,
`Card.boardLink` (o **id** do board de destino; `null` = sem atalho), então mover, duplicar, desfazer,
salvar e exportar funcionam como para o resto do card. É a continuação do link externo (3.19): mesmo
marcador, mesmo menu, mesma barra.

### Como adicionar, trocar e remover

| Onde | Como |
|---|---|
| **Clique direito** no card | Menu, num grupo próprio abaixo do link externo: "Link para outro board..." (sem atalho) ou "Ir para o board de destino", "Trocar board de destino...", "Remover link para board" |
| **Botão de seta curva** na barra flutuante | Ao lado do clipe do link externo; aparece só com **um** card selecionado e fica marcado se o card já tem destino. Abre a mesma lista |

**Botão (decisão):** um **segundo botão**, não um botão Link com duas opções. São duas ações diferentes
(site × board), cada uma a um clique, com o mesmo ícone do marcador que ela cria; um popup intermediário
"site ou board?" seria um clique a mais sempre.

A **lista** (`BoardLinkPickerPopup`) mostra a árvore inteira (`BoardTree.entries`), na ordem do PDF,
**indentada** pela profundidade; cada linha tem o nome do board e, embaixo, o **caminho completo** em cinza
(`Principal › Projeto B › Custos`). O board onde o card está aparece **desabilitado** ("(este board)"); o
destino atual vem marcado ("(destino atual)", em negrito). Clicar numa linha confirma na hora; com o
teclado, setas e Enter; Esc ou clicar fora cancela; "Remover link" aparece quando já há destino. Cada
mudança é **um passo** no desfazer (`history.perform`); escolher o mesmo destino de novo não cria passo.

```
clique na linha (ou Enter)
  → BoardLinkController.setBoardLink   history.perform(() -> card.setBoardLink(board.getId()))
  → CardView escuta card.boardLink     mostra a seta curva ao lado do clipe; o texto desce para baixo da fileira
```

### O marcador

O mesmo botãozinho de 18 px do clipe (`CardMarkers`, 3.19), com **ícone de seta curva** (sobe e vira para
a direita, `Icons.BOARD_LINK`). Com os dois links, ficam **lado a lado**: clipe à esquerda, seta à direita
(ordem de `CardMarkers.Kind`). Texto, alças, PDF e PNG já seguiam a conta do `CardMarkers`.

- **Clique:** vai ao board de destino (`NavigationController.jumpTo`, 3.11), com o **caminho completo no
  topo**; o voltar leva de volta ao board de onde você saiu. Não seleciona, não arrasta, não entra no texto.
- **Dica:** o caminho do destino, montado na hora em que a dica aparece (renomear o card do destino muda o
  caminho na próxima vez).

### Destino guardado pelo id

O destino é o `id` do board (já gravado no JSON desde sempre), não o card nem o nome: **renomear** o card
do destino não quebra nada (o caminho e a dica mostram o nome novo). O id do board é **único na árvore**:
`Board.deepCopy` dá ids novos a todos os boards copiados, e o `BoardStorage` dá id novo a um board com id
repetido ao abrir (saves feitos antes desta versão, em que duplicar um card-board com filhos repetia os
ids dos filhos; o primeiro na ordem do arquivo mantém o id).

### Destino excluído (marcador quebrado)

Excluir o card que contém o board de destino (ou um card acima dele) **não apaga o link**: o desfazer da
exclusão traz o board de volta com o **mesmo id**, e o link volta a funcionar sozinho.

| Enquanto o destino não existe | Como |
|---|---|
| Marcador | **quebrado**: ícone riscado, borda e ícone vermelhos, esmaecido (`CardView.setMarkerBroken`, pseudo-classe `:broken` no `app.css`) |
| Dica | "Board de destino não existe mais" |
| Clique | aviso "Board de destino não existe mais.", com a explicação (desfazer a exclusão traz de volta; clique direito para escolher outro); não navega |
| Lista de destinos | aviso em vermelho no alto; "Remover link" disponível |
| PDF | o marcador sai riscado e o card **não** ganha área clicável para o destino |
| PNG | o marcador sai riscado |
| Salvar/abrir | o id é mantido como está |

A conferência é feita pelo `BoardLinkController` (`BoardTree.ids` da raiz) quando o board da tela muda,
quando cards entram ou saem dele (excluir, desfazer, duplicar) e quando o link ou o board filho de um card
muda. Como só se edita o board da tela, isso cobre todos os casos.

### Duplicar

- **Card com link para outro board:** a cópia leva ao mesmo destino (`Card.copy` copia o id).
- **Card-board que é destino de algum link:** a cópia ganha boards com ids novos, então os links
  continuam levando ao **original**. Isso vale também para links **dentro** da cópia que apontavam para
  boards dentro do original (eles não são "redirecionados" para a cópia). É o comportamento esperado:
  "duplicar" faz uma variação nova, e quem tinha um atalho para o original continua com ele.

### PDF e PNG

- **PDF:** o marcador vira link para a **página do board de destino** (a 2ª passada do `PdfExporter`
  já tem todas as páginas; o mapa id → página é montado antes dela). Regra da área clicável:
  o card todo leva ao **primeiro** destino de: página própria (board aninhado, detalhes) › outro board ›
  site; os outros ficam só na **área do marcador**, por cima (anotação acrescentada depois). Assim, um
  card comum com atalho e link externo: o card todo vai ao board, o clipe abre o site.
- **PNG:** a seta aparece desenhada (o `BoardRenderer` monta os mesmos `CardView`s e recebe do
  `MainController` quais boards existem, para desenhar o quebrado).

### Limites (primeira versão)

- Só destinos **dentro da mesma árvore** (o mesmo save). Link para outro arquivo/pasta fica de fora.
- Sem busca na lista (uma tarefa futura de busca global vai reaproveitar `BoardTree.pathTo`).
- Fora do escopo: ordem de empilhamento, frames, busca global.

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
| `CardView`, press (seleção e conexão) | filtro | **Ignora** o clique num marcador (`isMarkerNode`): o clipe só abre o link. |
| `BoardView`, release do botão direito | filtro | Abre o menu do card se não houve movimento (3.19). |
| `BoardView`, `CONTEXT_MENU_REQUESTED` sobre card | filtro + consume | Impede o menu nativo do `TextArea` por cima do nosso. |
| `Stage`, teclas | handler | Só teclas não consumidas pelo campo de texto. |
| Popup do link, teclas | handler na raiz + consume | Nenhuma tecla digitada no endereço chega aos atalhos da janela. |

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
`:mixed`, `:has-board`, `:has-image`, e as nativas `:hover`, `:pressed`, `:focus-within`.

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
adiciona a classe `root` ao conteúdo (para as variáveis `-bv-*` existirem ali). O `LinkEditorPopup`
faz o mesmo. (O `ContextMenu` do clique direito não precisa: ele herda o CSS do nó dono.)

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

### Medir o texto como o `TextArea` desenha (`CardTextFit`)

A medição usa um `Text` fora da tela com a mesma fonte, e só funciona se imitar o `TextArea` em
todos os detalhes. Cada um destes foi encontrado nos testes de interface, com o texto "cabendo" na
conta e a barra de rolagem aparecendo mesmo assim:

| Detalhe | Consequência se ignorar |
|---|---|
| O `TextArea` desenha **um único `Text`** com o texto inteiro (limites `LOGICAL_VERTICAL_CENTER`) | Somar parágrafo a parágrafo dá uma altura menor que a real |
| O `.card` tem **1 px de borda** entre o card e o texto | Faltam 2 px em cada eixo |
| A **barra de rolagem** (6 px) estreita a linha quando aparece | Se ela aparece por um instante (ex.: no primeiro layout), o texto passa a precisar dela e ela não some mais. Por isso a quebra é medida já **sem** esses 6 px: o texto cabe com ou sem ela |
| Família da fonte | Medir e desenhar com a mesma: `CardTextFit.fontFamily()` resolve a primeira instalada da lista do `app.css`, e o `CardView` aplica essa família junto com o tamanho |

Mais uma folga de 3 px para arredondamentos. Os testes de interface conferem que nenhum de 70 textos
de tamanhos crescentes (em cada formato e no painel) mostra rolagem.

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

> Com boards aninhados há uma regra extra: o board filho de um card que **continua existindo** não é
> restaurado (mesmo id = deixa como está). Só cards **recriados** recebem o board filho da foto.
> Motivo: cada board tem o próprio histórico (3.11).

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
| `version` maior que a suportada (> 2) | recusa abrir (arquivo de versão futura) |
| `version` 1 (ou ausente) | save antigo: abre; o primeiro "Salvar" pede uma pasta nova (3.9) |
| `version` 2 em arquivo que não se chama `board.json` | recusa, pedindo o `board.json` da pasta |
| Pasta escolhida sem `board.json` ("pasta incompleta") | `IOException` com mensagem amigável |
| `board.json` vazio | `IOException` ("O arquivo está vazio.") |
| Pasta `imagens/` ausente | normal: ela só nasce com a primeira imagem |
| Card sem `imageFile` (saves anteriores) | card sem imagem, sem novo bump de `version` |
| `imageFile` aponta para arquivo que sumiu ou não abre | abre normalmente; o card mostra "Imagem não encontrada" e guarda o nome (3.17) |
| `imageFile` com pasta no nome (`../x.png`) | mantido no card, mas nunca procurado fora de `imagens/` (aviso de imagem não encontrada) |
| Card sem `link` (saves anteriores) | card sem link, sem novo bump de `version` |
| `link` que não é `http(s)` válido (`file:`, `javascript:`, caminho, `usuário@site`…) | **descartado** ao abrir: o card fica sem link (aviso no console) — 3.19 |

### Gravação segura

Grava em `board.json.tmp` (dentro da pasta do save) e depois **move** sobre o `board.json` (atômico
quando o sistema permite). Se o programa cair no meio da gravação, o `board.json` anterior continua
intacto. Quando há imagens, elas são copiadas **antes** do JSON e a limpeza das não usadas vem
**depois** dele (ordem completa em 3.9). Uma pasta nova que ficou sem `board.json` por uma falha no
meio do primeiro salvamento não perde nada de ninguém; abri-la dá "pasta incompleta" e o "Salvar como"
pode reaproveitar o nome.

## 4.9 Módulos Java (`module-info.java`)

```java
module com.danilo.boardvisual {
    requires javafx.controls;
    requires com.google.gson;
    requires org.apache.pdfbox;                                   // exportar PDF
    requires java.desktop;                                        // BufferedImage (PDFBox) e ImageIO (imagens)
    exports com.danilo.boardvisual;                               // JavaFX instancia BoardVisualApp
    opens com.danilo.boardvisual.persistence to com.google.gson;  // Gson lê os records por reflexão
}
```

O PDFBox não tem `module-info`; é um **módulo automático** (o nome `org.apache.pdfbox` vem do
manifesto do jar). A imagem do board passa do JavaFX para o PDFBox como `BufferedImage`, convertida
pixel a pixel no `BoardRenderer` para não precisar do módulo `javafx.swing`.

As imagens dos cards usam o **ImageIO** (`javax.imageio`), que também está no `java.desktop`: ler e
conferir PNG/JPG/GIF/BMP, reduzir e gravar PNG/JPG (`ImageImporter`). O JavaFX sozinho lê imagens, mas
não grava; o caminho "oficial" seria `SwingFXUtils` do módulo `javafx.swing`, evitado aqui do mesmo jeito
que no PDF: a imagem colada (JavaFX) vira `BufferedImage` pixel a pixel no `ImageController`.
O **Exportar PNG** usa o mesmo par: `BoardRenderer` converte pixel a pixel e o `ImageIO` grava (3.18).

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
| Imagem inserida | `BoardView` | imagem nasce sem legenda; atalhos (`Delete`, `Ctrl+D`...) já valem para ela |
| Duplo clique num card de imagem | `TextArea` (legenda) | não há área de texto visível para clicar |
| Esc durante edição | `BoardView` | sair da edição |
| Desfazer/refazer | `BoardView` | encerrar edição antes (ver 4.7) |
| Clique direito (pan ou menu) | `BoardView` | o pan começa no press; por isso o menu do card nunca disputa com a edição de texto |
| Popup do link aberto | campo do endereço (no popup) | digitar já; o foco da janela principal não muda |

Botões das barras têm `setFocusTraversable(false)`: clicar neles **não** tira o foco do texto que
você estava editando.

---

# Nível 5 — Guia de extensão

## 5.1 Adicionar uma propriedade ao card (ex.: `locked`)

É o caso mais sujeito a esquecimentos. **Checklist:**

- [ ] `Card`: campo como propriedade + getter/setter/`xxxProperty()`
- [ ] `Card.copy()`: copiar o valor (se fizer sentido na duplicação)
- [ ] `BoardSnapshot.CardState`: novo componente + `of()` + `applyTo()` → **senão desfazer ignora a propriedade**
  (exemplo completo: veja como `panelMode`, `detailText` e `listItems` foram adicionados, ou
  `baseWidth`/`baseHeight`/`fontSize`, com valor padrão para arquivos antigos; os mais recentes são
  `imageFile`, `link` e `boardLink`, que percorreram o checklist inteiro)
- [ ] `BoardFileFormat.CardData`: novo componente → **senão não salva**
- [ ] `BoardStorage.toData` / `fromData`: mapear, com valor padrão para arquivos antigos
- [ ] `CardView`: refletir visualmente (pseudo-classe ou variável CSS), se houver efeito visual; se for um
  **marcador** na borda do card (como o clipe do `link`), use `CardMarkers` (3.19) em vez de posicionar à mão
- [ ] Valor que vem de fora e vai para o sistema (como o `link`): validar no **setter** do modelo (o `link`
  é só leitura por fora) e de novo antes de usar; nunca confiar no que veio do arquivo
- [ ] Referência a outra parte da árvore (como o `boardLink`): guarde o **id** (não o objeto nem o nome),
  mantenha o id ao abrir mesmo que o alvo não exista mais, e mostre o estado "quebrado" na tela (3.20)
- [ ] `app.css`: estilo
- [ ] Controller: quem altera deve usar `history.perform`
- [ ] **`CardAllPropertiesTest.fullCard()`**: preencher a propriedade nova com um valor diferente do padrão
- [ ] Testes: `BoardStorageTest` (salvar/abrir) e `BoardSnapshotTest` (desfazer)

> **Rede de segurança (`CardAllPropertiesTest`):** um card com **todas** as propriedades diferentes do
> padrão passa por `copy`, foto/restauração e salvar/abrir, e o teste compara tudo. Os valores são lidos
> por reflexão dos getters públicos do `Card` (`get*`, `is*`, `has*`), então uma propriedade nova entra
> na comparação sozinha. O primeiro teste falha enquanto você não a preencher em `fullCard()`; os
> demais falham apontando o lugar esquecido (`copy`, `CardState` ou `toData`/`fromData`).
> Propriedades que o `Card` expõe só por outro caminho (sem getter com esses prefixos) não são vistas.

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

1. Constante em `Tool` (nome, tecla, formato ou `null`, ícone em `Icons`, dica).
2. Comportamento no controller adequado, checando `activeTool.get() == Tool.NOVA`.
3. Cursor/estilo em `app.css` usando `:tool-nova`, se preciso.

**Nem toda funcionalidade é uma ferramenta.** Se ela age sobre um card que já existe (ex.: o link
externo e o link para outro board), o lugar é a barra flutuante (5.4) e/ou o menu de clique direito
(abaixo), não a barra lateral.

**Ferramenta que é uma ação** (ex.: `Tool.IMAGE`): em vez de esperar um clique no canvas, o controller
escuta `activeTool` e, quando ela vira a ferramenta, faz a ação e volta para Selecionar — dentro de um
`Platform.runLater`, para não mudar a propriedade no meio da notificação da própria mudança. Botão, tecla
e dica funcionam sem mais nada.

## 5.4 Adicionar uma ação na barra flutuante

1. Botão em `SelectionToolbarView` (use `actionButton(tooltip, iconPath)` e um ícone em `Icons`).
2. Ação em `SelectionActionsController`, operando sobre `selectedCards()` dentro de `history.perform`.
3. Atalho em `KeyboardController`, se houver.

Botão que só faz sentido com **um** card (como o Link e o Link para outro board): esconda com `managed`
ligado ao `visible` e controle a visibilidade escutando a seleção no controller (ver
`SelectionToolbarView.showLink`/`showBoardLink` e `LinkController.refreshToolbar`/
`BoardLinkController.refreshToolbar`).

Popup aberto a partir de um botão ou item de menu (como o `LinkEditorPopup` e o `BoardLinkPickerPopup`):
classes `root floating-panel popup-panel` no conteúdo, CSS copiado da cena dona (4.5), e consumir todas
as teclas na raiz (armadilha 29).

### Item no menu de clique direito

1. No controller, registre um contribuidor: `contextMenu.addContributor(ctx -> List.of(...MenuItems...))`.
   `ctx.card()` é o card clicado (já selecionado); `ctx.screenX()/screenY()` dizem onde abrir um popup.
2. A ação passa por `history.perform`. Devolva lista vazia quando não houver o que oferecer para aquele card.
3. A ordem do menu é a ordem de registro no `MainController`; cada contribuidor vira um grupo separado por linha.

### Marcador novo no card

Ver o fim de "O marcador" em 3.19 (`CardMarkers.Kind`, `Icons.marker`, `CardView.setUpMarkers`). Texto,
PDF e alças passam a respeitar o marcador novo sem mais nada. Exemplo completo: o `BOARD_LINK` (3.20),
que também mostra como ter um estado "quebrado" (`CardView.setMarkerBroken`, `Icons.marker(kind, broken)`,
`:broken` no CSS) e uma dica montada pelo controller (`setBoardLinkDescription`).

### Navegar para um board qualquer

Para levar o usuário a um board pelo id (busca, atalhos...), use `NavigationController.jumpTo(id)`: ele
monta o caminho real (`BoardTree.pathTo`) e cuida do voltar (3.11). Para listar boards (com caminho e
profundidade), `BoardTree.entries(raiz)`.

## 5.5 Alinhamento: mover × redimensionar

O mesmo `AlignmentGuides` atende os dois gestos (o redimensionamento está em `BoxResizer`, 3.13).
A diferença é **quais linhas participam** e **onde o ajuste é aplicado** — útil se um dia outro
gesto precisar de alinhamento (ex.: criar card arrastando, mover setas):

| Gesto | Linhas que participam | Onde aplicar `dx`/`dy` |
|---|---|---|
| Mover | `Edge.ALL` nos dois eixos | posição (`x += dx`, `y += dy`) |
| Arrastar borda direita | X: `{END}`; Y: nenhuma | largura (`width += dx`) |
| Arrastar canto inferior direito | X: `{END}`; Y: `{END}` | largura e altura |
| Arrastar borda esquerda | X: `{START}` | posição **e** largura (`x += dx`, `width -= dx`) |

Com proporção fixa (elipse, losango, ou `Shift`), só o eixo X participa do alinhamento; a altura é
derivada da largura, e o canto oposto à alça fica parado.

## 5.6 Mapa das próximas funcionalidades (Trello)

Sequência planejada (no Trello, numerada `[1]`→`[4]`, cada card com "Pré-requisitos / Libera").
**[1], [2] e [3] estão feitos** (3.13, 3.14 e 3.15), assim como o salvamento em pasta (3.9), as
imagens nos cards (3.17), o PNG (3.18), o link externo (3.19) e o link para outro board (3.20):

```
[1] Redimensionar cards ──► [2] Fonte automática/manual + crescimento do card ──► [3] Exportar PDF ──► [4] Notas "post-it" no PDF
```

| Funcionalidade | Onde encaixa | Observações |
|---|---|---|
| **Busca global** | `BoardTree.entries` já lista todos os boards com o caminho; buscar também no texto dos cards e ir ao resultado com `NavigationController.jumpTo` (3.20) | Decidir: só nomes de board ou texto dos cards; destacar o card encontrado |
| **Link para outro arquivo** | Extensão do link para outro board (3.20): guardar a pasta do save além do id | Primeira versão ficou só na mesma árvore |
| **Ordem de empilhamento** | Itens "Trazer para frente/Enviar para trás" como contribuidor do menu de clique direito; a ordem é a da lista `board.getCards()` (salva no arquivo) | Hoje o "trazer para frente" do clique não é salvo (armadilha 10) |
| **[4] Notas "post-it" no PDF** | `PDAnnotationText` sobre o card do painel, na 2ª passada do `PdfExporter.drawBoardPage` (o `rectOf(card)` já dá a posição) | Expansão opcional: só texto simples, comportamento varia por leitor, não imprime. Pode virar uma opção ao exportar |
| **Múltiplos boards** | `NavigationController.openRoot` já troca o board principal; `EditHistory.clear()` já existe | Decidir: abas, lista lateral ou só "abrir recente" |
| **Lock de item** | Propriedade `locked` (checklist 5.1); `CardDragController` ignora cards travados; pseudo-classe `:locked` | Excluir/duplicar travados? decidir |
| **Setas com texto** | `Connection.label` (propriedade); `ConnectionView` com `Label` posicionado no meio por binding; `BoardSnapshot.ConnectionState` e `ConnectionData` | Hoje `Connection` é imutável — label será a primeira parte mutável |
| **Agrupar** | `groupId` no card, ou um modelo `Group`; seleção de um membro seleciona o grupo todo | Seleção múltipla e mover em grupo já existem |
| **Aviso de alterações não salvas** | Comparar `board.snapshot()` com a foto do último salvamento | O snapshot já resolve a detecção |
| **Exportar SVG** (tarefa 8) | Item ao lado dos de PNG no menu `Arquivo`; pode reaproveitar `PngExporter.Background` e `suggestedFileName` | Vetorial: não dá para usar o snapshot; desenhar formatos e setas à mão |
| **PNG só da seleção / de todos os boards** | Seleção: `BoardRenderer` com só os cards selecionados e as setas entre eles. Todos: percorrer a árvore como o `PdfExporter.BoardNode`, um arquivo por board nomeado pelo caminho | Decisões em 3.18 |

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
| Board filho dentro do card, no mesmo arquivo | Um arquivo por board | Um `board.json` só é mais simples de salvar e abrir; todos os boards compartilham a mesma `imagens/` |
| Save = pasta (`board.json` + `imagens/`) | Um `.json` único; imagem em base64 dentro do JSON | Guarda a imagem de verdade (arquivo normal, sem inchar nem reescrever o JSON a cada salvamento); decidido com o Danilo |
| "Abrir" escolhe o `board.json` (seletor de arquivo) | Escolher a pasta (`DirectoryChooser`) | Um só "Abrir..." para saves novos e antigos; os `.json` antigos continuam abrindo como sempre (3.9) |
| `version` dentro do JSON decide pasta × `.json` antigo | Decidir pelo nome ou extensão do arquivo | O nome pode ser qualquer um num save antigo; `board.json` é só o nome fixo exigido dos saves novos |
| Salvar um board vindo de `.json` antigo = "Salvar como" | Sobrescrever o `.json` ou converter em lugar | Nunca se perde o arquivo antigo; sem conversor separado, o primeiro salvamento converte |
| Limpar `imagens/` só ao salvar, com o conjunto de nomes dado por quem chama | Apagar a imagem ao excluir o card | O desfazer pode trazer o card de volta; ver a atenção em 3.9 |
| Conjunto de imagens em uso = árvore inteira + fotos do histórico | Só o board atual | Ctrl+Z depois de salvar traz o card com a imagem; arquivos de cards excluídos ficam até o histórico ser zerado (3.9) |
| Imagem inserida vai para uma pasta de trabalho temporária, mesmo com o save já em pasta | Gravar direto em `imagens/` do save | O save só muda quando o usuário salva; board novo, `.json` antigo e save em pasta seguem o mesmo caminho (3.17) |
| PNG/JPG dentro do limite: bytes originais; acima de 2000 px: reduzido (JPG→JPG, resto→PNG); GIF e BMP → PNG | Recomprimir tudo; guardar sempre o original | Sem perda quando não precisa; save leve; GIF parado igual na tela e no PDF |
| ImageIO (`java.desktop`, já exigido) | Módulo `javafx.swing` (`SwingFXUtils`) | Nenhum módulo novo; a conversão JavaFX → `BufferedImage` é um laço de pixels (4.9) |
| Card de imagem: tamanho à mão = imagem; legenda faz o card crescer embaixo | Guardar a proporção da imagem num campo à parte | Nenhuma propriedade extra; a proporção nunca se perde, nem com a imagem faltando (o aviso ocupa o mesmo espaço) |
| Fonte automática da legenda = 14 px fixos | "Maior fonte que cabe", como nos outros cards | O espaço da legenda começa em zero: o automático daria sempre a fonte mínima |
| Card de imagem é sempre retângulo | Recortar a imagem em elipse/losango | Imagem recortada perde conteúdo; o botão de formato some para imagens |
| Imagem faltando: aviso no lugar e referência mantida | Remover o card ou a referência ao abrir | Não perde dado: devolver o arquivo à pasta faz a imagem voltar |
| Nome do board filho = texto do card | Nome próprio no `Board` | Nada para manter em sincronia; renomear o card renomeia o caminho |
| Desfazer por board | Um histórico global | `Ctrl+Z` desfaz o que está na tela, não algo de outro nível |
| Barra flutuante fora do `world` | Dentro do `world` | Não sofrer zoom |
| Caixa de seleção pega o que **toca** | Só o que está **dentro** | Mais fácil de usar; é uma linha para trocar |
| Arrastar fundo = caixa; pan com Espaço/meio/direito | Arrastar fundo = pan | Padrão Figma/Excalidraw (decidido com você) |
| Sem framework de injeção de dependência | Spring/Guice | Escopo pequeno; `MainController` monta tudo à mão |
| Sem abstração de persistência | Interface para "futuro banco" | Só existe JSON local; evitar over-engineering |
| Paleta fixa de cores | Seletor livre | Consistência de categorias |
| `TextArea` com alinhamento via CSS interno | `TextField` / `Label`+edição | `TextField` é uma linha só; ver 4.5 |
| Compilar com `release 17` | Java 25 | Maven do terminal usa JDK 17; o IntelliJ usa 25 |
| PDF: board como imagem + texto invisível | Desenhar cards e setas em vetorial no PDF | Reaproveita o desenho exato da tela (formatos, sombras, fontes) com pouco código; vetorial fica como evolução |
| PDF: fonte padrão Helvetica | Embutir a fonte da tela | Nada a embutir nem a distribuir; cobre o português. Emoji e símbolos raros viram "?" |
| PNG: só o board da tela | Exportar a árvore inteira em vários PNGs | Uma imagem é "foto" do que se vê; a árvore já tem o PDF (3.18) |
| PNG: fundo transparente num segundo item de menu | Perguntar a cada exportação; preferência guardada | Nenhuma caixa a mais no caso comum, e a opção fica visível (3.18) |
| PNG: reduzir a escala acima de 8.000 px / 40 Mpx | Falhar com erro; dividir em várias imagens | Sempre sai uma imagem inteira, sem estourar a memória (3.18) |
| PNG: board vazio avisa e não grava | Gravar uma imagem pequena em branco | Imagem vazia não tem uso e esconderia o engano (3.18) |
| Link externo = atributo de qualquer card | Um tipo de "card de link" | Card de imagem, painel e board também podem ter link; nada a duplicar (3.19) |
| Link aceita só `http`/`https`, validado ao digitar, no modelo e antes de abrir | Aceitar qualquer endereço; validar só na interface | O app entrega o endereço ao sistema: `file:`/caminhos poderiam abrir programas; um arquivo adulterado não passa pelo popup (3.19) |
| Sem esquema → `https://` | Recusar ou usar `http://` | É o que o usuário quer quase sempre; sites sem HTTPS são raros |
| Botão Link na barra flutuante + menu de clique direito | Botão na barra lateral de ferramentas | A barra flutuante age sobre o card selecionado; a lateral é de ferramentas de criação (pergunta pendente ao Danilo) |
| Menu de clique direito montado por contribuidores | Menu fixo com todos os itens no `BoardView` | As próximas funcionalidades plugam itens sem mexer no menu nem no `BoardView` |
| Clique direito parado = menu; arrastado = pan | Só pan, ou menu no press | Mantém o pan com o botão direito; só o soltar sem movimento decide o menu |
| Marcador em cima: canto direito no retângulo, meio na elipse/losango | Sempre no canto; fora do card | Fica dentro da silhueta em todos os formatos e longe das alças, do botão do painel e da alça de conexão |
| Texto desce para baixo dos marcadores | Marcador por cima do texto | "Não atrapalhar o texto"; o custo é um pouco de altura no retângulo |
| Clipe aparece no PDF e no PNG | Esconder na exportação | É o aviso visual de que o card tem link; no PDF a área é clicável |
| PDF: card todo abre o site; se já leva a uma página, só o clipe | Sempre só o clipe | Área grande é mais fácil de clicar no leitor de PDF, sem perder o link interno |
| Popup consome todas as teclas | Checar "popup aberto" no `KeyboardController` | Uma regra local, sem o atalho precisar conhecer o popup |
| Abrir pelo `HostServices` passado no construtor | `Desktop.browse` (AWT) ou acesso estático | Sem estático e testável (o roteiro intercepta); o `HostServices` é a via do JavaFX |
| Link para outro board guarda o **id** do board | Guardar o card ou o nome do destino | Renomear não quebra; o id já estava no JSON; excluir e desfazer volta com o mesmo id (3.20) |
| Id de board único na árvore (`deepCopy` renova todos; abrir corrige repetidos) | Id único só entre irmãos | A busca por id precisa de um resultado só; a cópia de um card-board é outra coisa, e os links continuam no original (3.20) |
| Destino excluído: marcador quebrado, link mantido | Apagar o link junto com o destino | O desfazer da exclusão traz o destino de volta, e o link volta a funcionar sem o usuário refazer nada |
| Segundo botão (seta curva) ao lado do Link | Um botão Link com popup "site ou board?" | Um clique a menos; cada botão tem o ícone do marcador que cria (3.20) |
| Voltar depois de um pulo = board de onde saiu; caminho no topo = caminho real do destino | Empilhar o destino em cima da origem (caminho "falso") | O topo sempre mostra onde o board está de verdade, e o voltar ainda leva de volta; clicar no caminho acima do destino esquece a origem (3.11) |
| Lista de destinos com a árvore inteira, indentada, caminho embaixo | Árvore expansível (`TreeView`) | Boards de um save cabem numa lista curta; o caminho em cada linha tira a ambiguidade de nomes iguais em ramos diferentes |
| Pilha de navegação numa classe sem tela (`NavigationPath`) | Lógica dentro do `NavigationController` | Testável sem janela (busca, reconstrução da pilha, voltar à origem) |

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
12. **Largura/altura × tamanho definido à mão:** para mudar o tamanho "de verdade", use
    `Card.resize`; `setWidth`/`setHeight` sozinhos são desfeitos pelo recálculo do texto (3.14).
13. **Mudar o visual do texto** (borda do `.card`, barra de rolagem, fonte, espaçamento de linha no
    `app.css`) exige ajustar as constantes do `CardTextFit` (4.6), senão volta a aparecer rolagem.
14. **PDF, primeira exportação:** o PDFBox monta um cache das fontes do sistema na primeira vez
    (alguns segundos, uma vez só por computador).
15. **PDF com caracteres fora do português** (emoji, alfabetos não latinos) mostra "?" no texto
    de verdade (caminho, detalhes, busca); a imagem do board mostra certo.
16. **Abrir escolhe o `board.json`, não a pasta.** Quem espera selecionar a pasta no seletor só consegue
    entrar nela; o título do diálogo avisa. Um `board.json` copiado para fora da pasta do save e
    renomeado deixa de abrir (versão 2 exige o nome `board.json`) (3.9).
17. **Limpeza de imagens × desfazer:** o conjunto de nomes passado a `BoardImages.removeUnused` precisa
    incluir as imagens das fotos do histórico; só o board atual faria o `Ctrl+Z` trazer um card sem a
    imagem. Conjunto vazio apaga **tudo** de `imagens/`. Use sempre `ImageController.imagesInUse` (3.9).
18. **Tudo que está solto na pasta do save é do usuário.** O programa só mexe em `board.json`,
    `board.json.tmp` e nos arquivos diretamente dentro de `imagens/`; o "Salvar como" não copia outros
    arquivos da pasta de origem.
19. **`mvn test` com JDK 21 e caminho com acento** (ex.: `...\Projeto de programação\...`) falha com
    `ClassNotFoundException` ao criar a classe de teste (o argfile do surefire sai com a codificação
    errada). Use o JDK 17 no `JAVA_HOME`, como o resto da documentação já pressupõe (A).
20. **Card de imagem: tamanho à mão = imagem.** Quem mexer no tamanho de um card de imagem precisa manter
    `baseWidth`/`baseHeight` na proporção da imagem (como faz `CardResizeController.resizeImage`);
    `Card.resize` com outra proporção deixaria a imagem com faixas vazias (ela é desenhada com
    `preserveRatio`). `Card.changeShape` não é chamado para imagens (3.17).
21. **Foto de celular deitada:** nem o ImageIO nem o JavaFX leem a orientação EXIF do JPG. Uma foto que
    o celular gravou "deitada" com a marca de girar aparece deitada no board (e no PDF).
22. **JPG em CMYK** (alguns arquivos de gráfica/Photoshop) não abre no ImageIO: a inserção é recusada com
    mensagem. Converter para RGB antes.
23. **Imagem colada invisível:** alguns programas no Windows copiam a imagem com o canal alfa todo zerado.
    O `ImageController.toBufferedImage` trata "tudo transparente" como opaco; se um dia aparecer uma
    imagem colada "vazia", é por aí.
24. **Pasta de trabalho no `%TEMP%`:** se o programa for fechado à força (sem passar pelo encerramento da
    JVM), a pasta `boardvisual-imagens-…` fica para trás no temporário do sistema. Não afeta nenhum save.
25. **Snapshot com origem fracionária sai 1 px maior.** Com escala reduzida (ex.: 0,27 px por unidade), a
    origem do recorte cai entre pixels e o JavaFX arredonda as duas pontas. O `BoardRenderer` alinha a
    origem a um pixel inteiro (a margem de 32 absorve o deslocamento) e conta os pixels como o
    `PngExporter.pixels`; quem mexer numa conta precisa mexer na outra.
26. **Cópias de exportação ficam presas aos cards.** O `CardView` escuta propriedades do card com listeners
    comuns (não fracos); as cópias montadas pelo `BoardRenderer` (PDF e PNG) continuam referenciadas pelos
    cards até eles sumirem. Em uso normal é pouca memória; exportar muitas vezes um board grande acumula.
27. **Tamanho do marcador só no código.** O `CardView` fixa 18 px (`CardMarkers.SIZE`); um `-fx-pref-width`
    ou `-fx-min-width` em `.card-marker` no `app.css` venceria o código (armadilha 1) e desalinharia o clipe
    da conta do texto e do PDF. Pela mesma razão o marcador não usa a classe `tool-button` (que tem mínimo de 38).
28. **Clique direito e menus nativos.** O Windows manda `CONTEXT_MENU_REQUESTED` depois de soltar o botão
    direito, mesmo depois de um pan; sem o filtro do `CardContextMenuController`, o `TextArea` abriria o menu
    Copiar/Colar sobre o card. Quem mexer no pan ou no menu deve manter o "só abre se `isStillSincePress`".
29. **Teclas num popup e os atalhos da janela de baixo.** O popup não é uma janela com foco próprio: o
    JavaFX entrega a ele as teclas que chegam à janela dona, e uma tecla que ele não consome pode seguir até
    os atalhos (ex.: "r" trocaria a ferramenta). Por garantia, o `LinkEditorPopup` consome tudo na raiz (o
    roteiro de interface confere que "r", "v", Backspace e Delete no popup não agem no board); um popup novo
    com campo de texto deve fazer o mesmo.
30. **`HostServices` não avisa falhas.** No Windows ele entrega o endereço ao sistema e só imprime o erro no
    console; o aviso do `LinkController` cobre exceções, mas não "o navegador não abriu".
31. **Ids de board repetidos.** O link para outro board acha o destino pelo id, que precisa ser único na
    árvore. Quem criar boards copiando outros deve usar `Board.deepCopy` (que renova todos os ids) e nunca
    reaproveitar uma `BoardSnapshot` de um board que continua na árvore para criar um segundo board. O
    `BoardStorage` corrige repetições ao abrir, mas só aí.
32. **Ouvintes nos cards a cada visita.** O `BoardLinkController.attach` roda para cada `CardView` criado, e um
    card ganha um `CardView` novo a cada vez que o board aparece. O ouvinte é sempre o mesmo objeto, removido
    antes de ser adicionado; um ouvinte novo a cada `attach` se acumularia no card.
33. **Roteiro de interface e a janela da frente.** Um roteiro com `java.awt.Robot` só vê coordenadas: se
    outra janela estiver na frente do app (o Windows nem sempre traz a janela de teste para a frente,
    principalmente com o usuário usando o computador), o clique cai nela. Conferir a janela da frente
    **antes de cada clique**, não só antes de digitar; ou usar o roteiro "dentro da JVM" (C), que não usa o
    mouse e o teclado do sistema.

## C. Testes

### Automatizados (`mvn test`) — 235 testes, sem abrir janela

(O `BoardRendererPngTest` liga o JavaFX, sem janela, para desenhar de verdade; num ambiente sem tela
ele é pulado em vez de falhar.)

| Classe | Cobre |
|---|---|
| `BoardTest` | regras de conexão, remoção em cascata, cópia de card |
| `CardShapeTest` | criação por formato, troca de formato mantendo o centro/tamanho |
| `CardGeometryTest` | ponto de contato da seta em cada formato |
| `AlignmentGuidesTest` | grudar borda/centro, candidato mais próximo, limite, guias atravessando várias caixas, redimensionar (só bordas puxadas) |
| `BoxResizerTest` | cada tipo de alça, lado oposto parado, tamanho mínimo, proporção, grude só nas bordas puxadas, Alt (limite 0) |
| `BoardSnapshotTest` | foto/restauração: mudança de propriedades, fonte e tamanho definido à mão, card excluído com setas, card novo |
| `CardTextFitTest` | espaço do clipe reservado em cima (fonte menor ou card maior), losango sem mudança; fonte máxima/menor/mínima, palavra nunca quebrada no meio, retângulo cresce só na altura, elipse mantém proporção, volta ao tamanho à mão, fonte manual, card vazio, espaço do botão; card de imagem: sem legenda não cresce, legenda cresce embaixo sem distorcer a imagem, fonte da legenda, imagem maior (com medidor falso, sem JavaFX) |
| `PanelCardTest` | modos do painel, conteúdo apagado na troca, cópia independente, foto/restauração do painel |
| `NestedBoardTest` | restauração não mexe em filhos existentes, exclusão+desfazer traz a árvore, cópia profunda |
| `EditHistoryTest` | desfazer/refazer, ação sem mudança, gesto longo, sessões de edição, histórico separado por board |
| `PdfExporterTest` | link para outro board: o card todo leva à página do destino em outro ramo; card que já leva a uma página vai ao destino só pelo marcador (por cima); card com os dois links e sem página própria (card todo → board, clipe → site); destino excluído sem área clicável; marcadores lado a lado; link externo: card todo com ação URI (endereço ASCII), card de board aninhado mantém o link da página e ganha o do site só no clipe (por cima), área do clipe acompanha a escala, cards sem link sem URI; ordem das páginas, links dos cards e do caminho, "voltar ao board", marcadores, texto buscável, continuação de detalhes longos, caracteres sem suporte, arquivo gravado sem sobra de temporário, texto invisível de card de imagem só na faixa da legenda |
| `BoardStorageTest` | link salvo e aberto (card comum, de imagem e de board), card sem link não grava o campo, save anterior sem o campo abre sem link, links maliciosos no arquivo (`javascript:`, `file:`, caminho, rede, `usuário@site`) descartados; salvar/abrir em pasta (painéis, boards aninhados em 3 níveis, fonte e tamanho definido à mão), abrir pela pasta ou pelo `board.json`, `.json` antigo (abre, salva no formato novo sem alterar o antigo, versão decide e não o nome), JSON inválido, pasta incompleta, `board.json` vazio, versão futura, `version` 2 com outro nome, "salvar como" copiando `imagens/`, imagens copiadas antes do JSON (falha na gravação não deixa JSON solto), falha mantém o `board.json` anterior, destino livre × save existente × ocupado; card de imagem salvo e aberto com o arquivo, card sem imagem não grava o campo, save anterior sem o campo abre, imagem faltando (e nome com `../`) não quebra e mantém a referência, imagens de várias origens |
| `BoardImagesTest` | limpeza só dos arquivos não referenciados (com arquivos falsos), conjunto vazio, nome citado que não existe, sem pasta `imagens/`, subpastas e resto do save intactos; cópia sem sobras temporárias, cópia desatualizada substituída, idêntica mantida, nada criado sem imagens, origem = destino não muda nada; achar o arquivo na ordem das pastas, nomes com pasta recusados |
| `ImageImporterTest` | (imagens reais geradas no teste) PNG e JPG pequenos copiados byte a byte com nome novo, grande reduzido a 2000 px mantendo a proporção (JPG continua JPG, PNG mantém transparência), GIF e BMP viram PNG, formato pelo conteúdo e não pela extensão, arquivo quebrado/não imagem/inexistente recusado sem sobras, imagem em memória vira PNG, extensões aceitas |
| `ImageCardTest` | card de imagem nasce retângulo do tamanho da imagem, altura da imagem pela proporção, nome em branco = sem imagem, duplicar reutiliza o arquivo, desfazer/refazer a inserção, imagens da árvore inteira e das fotos |
| `ImageCardControllerTest` | limpeza ao salvar mantém imagens de boards aninhados e do histórico e remove só a órfã; histórico zerado libera a imagem; tamanho inicial (limite, natural, ampliada); redimensionar mantém a proporção em todos os cantos, canto oposto parado, mínimo pelo maior lado |
| `PngExporterTest` | área com margem e 2 px por unidade, redução pelo lado maior (8.000 px exatos, sem 1 px a mais) e pelo total de pixels, arredondamento, nome sugerido (principal, caminho do aninhado, caracteres proibidos, ponto/espaço no fim, nome reservado, vazio, comprimento), `.png` acrescentado só se faltar, PNG gravado no tamanho previsto e sem `.tmp`, substitui arquivo existente, board vazio recusado sem gravar |
| `BoardRendererPngTest` | (JavaFX de verdade, CSS do app) clipe desenhado no card com link e ausente no sem link; PNG de board com formatos, cor, seta e card de imagem: tamanho previsto, fundo branco, cor do card, seta entre os cards, imagem do card no lugar certo; a mesma exportação com fundo transparente (canto transparente, card opaco); board de 30.000 unidades reduzido a 8.000 px com os dois extremos desenhados |
| `WebLinkTest` | aceita `http`/`https`, acrescenta `https://` (inclusive `site:porta` e `//site`), apara e põe o esquema em minúsculas, vazio = sem link; recusa outros esquemas, caminhos e executáveis, espaços e caracteres de controle, usuário antes do site, site sem domínio, porta e `%` inválidos, endereço longo; forma ASCII (site com acento); `Card.setLink` nunca guarda link perigoso |
| `BoardTreeTest` | caminho de um board por id em vários níveis e em ramos diferentes, caminho da raiz, id inexistente (e raiz/id nulos), excluir um card tira a subárvore da busca, renomear mantém o id e muda o caminho, lista da árvore na ordem do PDF com profundidade e caminho, ids da árvore; `deepCopy` dá ids novos a todos os boards dentro; duplicar um card-board mantém ids únicos e o link copiado aponta para o original; duplicar copia o link; texto em branco = sem link |
| `NavigationPathTest` | entrar/voltar como antes; pular monta o caminho real do destino; voltar do destino volta à origem (com a visão dela) e depois ao pai; pulo para o principal ainda tem voltar; filho aberto a partir do destino volta ao destino e depois à origem; clicar num nível acima do destino esquece a origem; pulos encadeados; visão mantida nos níveis em comum; pulo para o próprio board e para board excluído não mudam nada; origem excluída → voltar ao pai; origem excluída e trazida pelo desfazer → pilha refeita pelo id; abrir outro board zera tudo |
| `BoardLinkControllerTest` | adicionar, trocar e remover são passos de desfazer/refazer; o mesmo destino de novo não é passo; excluir o destino mantém o link (quebrado) e o desfazer o traz de volta; excluir o card com o link e desfazer; duplicar copia o link; duplicar o destino mantém o link no original |
| `BoardLinkStorageTest` | link para board de outro ramo salvo e aberto (caminho conferido), card sem link não grava o campo, save anterior sem o campo abre sem link, link para board inexistente mantido, ids de board repetidos (saves antigos) renovados ao abrir |
| `CardMarkersTest` | seta do link para outro board sozinha e depois do clipe (mesma fileira); sem link, sem marcador; retângulo: canto de cima à direita, fora das alças; elipse e losango: no meio e dentro da silhueta; losango estreito desce o marcador; vários em fila sem sobrepor; texto abaixo; card minúsculo |
| `LinkControllerTest` | adicionar, editar e remover são passos de desfazer/refazer; vazio remove; endereço inválido devolve mensagem e não muda nada; o mesmo link de novo não é passo; duplicar copia o link (painel, imagem); excluir e desfazer traz o link |
| `CardAllPropertiesTest` | card com todas as propriedades diferentes do padrão (inclusive `imageFile`, `link` e `boardLink`): preenchimento do `fullCard()` (falha se esquecer uma propriedade nova), `copy`, foto/restauração (card novo e existente) e salvar/abrir preservam tudo (5.1) |

Tudo que é **lógica** (modelo, geometria, histórico, persistência) é testável sem interface — é o
benefício direto da separação em camadas.

### Testes de interface (não versionados)

Durante o desenvolvimento, a interface foi validada com roteiros usando `javafx.scene.robot.Robot`,
que move o mouse e aperta teclas de verdade na janela, seguidos de snapshots da cena. Eles não estão
no repositório (dependem de tela, foco do sistema operacional e tempo). Lições aprendidas:

- O Windows descarta a primeira entrada enquanto ativa a janela de teste; é preciso "aquecer".
- Coordenadas fora da área visível da tela não geram evento algum.
- Clicar num popup logo após abri-lo falha: esperar a janela do popup existir.
- Depois de mudar o modelo, forçar `applyCss()` + `layout()` na cena antes de inspecionar tamanhos
  (senão a checagem vê o layout anterior).
- Os seletores de arquivo do Windows são **nativos**: dá para dirigi-los de fora da cena (Robot, ou
  `SendKeys` num script) digitando o caminho completo no campo "Nome" e `Enter`. Foi assim que o
  salvamento em pasta foi validado: novo board → `Ctrl+S` → reabrir → "salvar como" com imagem falsa em
  `imagens/` → abrir um `.json` antigo → salvar no formato novo (conferindo que o `.json` antigo ficou
  byte a byte igual) → abrir um `board.json` quebrado (mensagem de erro, board da tela preservado).
- **Imagens nos cards** foram validadas por um roteiro que roda o app na mesma JVM e dirige mouse e
  teclado com o `java.awt.Robot` (funciona mesmo com um seletor nativo aberto, ao contrário do `Robot` do
  JavaFX, que exige a thread do JavaFX). A área de transferência do sistema é preenchida pelo AWT (texto,
  imagem ou lista de arquivos, como um programa de fora), e o **arrastar** vem de uma janela Swing com um
  rótulo arrastável — um arraste de verdade entre programas, como o do Explorer. Os caminhos nos seletores
  nativos entram por `Ctrl+V` (digitar letra a letra depende do layout do teclado). Roteiro: ferramenta `I`
  → colar imagem → colar arquivos GIF/BMP copiados (um passo; desfazer/refazer) → arrastar um JPG de
  3000 px → redimensionar pelo canto (proporção) → duplo clique e legenda (com `i` e `Ctrl+V` indo para o
  texto) → duplicar → excluir → `Ctrl+S` numa pasta com acento → `Ctrl+Z` traz o card excluído com a
  imagem → exportar PDF (renderizado de volta para conferir a imagem; legenda buscável) → reabrir →
  "salvar como" (só as imagens usadas) → apagar um arquivo e reabrir (aviso no card, referência mantida).
  Rodou com o app no classpath e no module path (como o `mvn javafx:run`).
- **Exportar PNG** foi validado do mesmo jeito (32 verificações): board com retângulo, elipse e losango
  coloridos, duas setas, painel fechado, card de board aninhado e imagem colada com legenda (board ainda
  não salvo, imagem na pasta temporária) → `Ctrl+Shift+E` (nome sugerido lido do campo do seletor com
  `Ctrl+C`; tamanho, fundo e a cor de cada card/imagem conferidos no arquivo; seleção, pan/zoom, desfazer e
  título iguais antes e depois) → menu "com fundo transparente" pelo teclado, digitando o nome sem `.png`
  → entrar no aninhado e exportar (só ele; nome com o caminho) → salvar em pasta e exportar de novo
  (imagens vindas da pasta do save) → cancelar o seletor (nada gravado) → board novo vazio (aviso, nada
  gravado). Os PNGs foram abertos e comparados com a tela.
- **Link externo** foi validado por um roteiro do mesmo tipo (85 verificações), com o "abrir no navegador"
  interceptado (o `MainController` recebe um `Opener` que só anota o endereço): clique direito parado abre o
  menu e seleciona o card, sem o menu do `TextArea` por cima; clique direito arrastando faz pan e não abre
  menu; clique direito no fundo não abre nada; "Adicionar link..." → digitar `rrv`, Backspace e Delete no
  popup (nem trocam a ferramenta nem excluem o card) → `exemplo.com` + Enter (vira `https://exemplo.com`,
  clipe aparece, texto desce); clicar no clipe abre o link sem mudar seleção, posição nem foco; arrastar a
  partir do clipe não move o card; dica com o endereço; botão Link da barra (recusa `file:` e `C:\…` com
  mensagem, aviso some quando fica válido; Esc cancela); editar/remover/abrir pelo menu; Ctrl+Z/Ctrl+Y;
  "Remover" do popup; links em losango, painel, board aninhado e imagem (clipe dentro da silhueta, fora dos
  botões, do texto e das alças); clipe do card de board abre o link sem entrar; ferramenta Conexão não cria
  seta ao clicar no clipe; duplicar copia; salvar (link no `board.json`), PDF (anotações URI), PNG (clipe
  desenhado), reabrir (links e clipe funcionando). Rodou no classpath e no module path; o `mvn javafx:run`
  abriu o app normalmente. **Não validado:** o `HostServices` abrindo o navegador de verdade (abrir uma
  janela do navegador no meio do roteiro tiraria o foco do app, o que a regra de segurança proíbe).
- **Cuidado com o foco:** um clique fora da janela do app (coordenada fora da área visível do board)
  ativa outra janela, e as teclas seguintes vão para ela. O roteiro do PNG confere que todo clique cai
  dentro da janela e, antes de digitar num seletor, lê o título da janela da frente (`GetForegroundWindow`
  por PowerShell) e aborta se não for o seletor do app. **Isso não basta (armadilha 33):** no link para
  outro board, o app abriu atrás de outra janela e um clique direito do `java.awt.Robot`, dentro do
  retângulo do app, caiu na janela da frente (nenhuma tecla vazou: a checagem antes de digitar barrou).
- **Link para outro board** foi validado por um roteiro **dentro da JVM, sem mouse e teclado do sistema**
  (107 verificações): a janela real do app é aberta (e mandada para trás das outras), o foco da janela é
  simulado, e o roteiro dispara `MouseEvent`/`KeyEvent` do JavaFX nos nós, que passam pelos mesmos filtros
  e handlers do uso real (clique direito parado → menu; clique no marcador; teclas na lista e no board;
  `Ctrl+Z`/`Ctrl+Y`/`Ctrl+D` pelos aceleradores; `Alt+←`). Nada pode cair em outra janela do computador.
  Roteiro: árvore com dois ramos → clique direito → "Link para outro board..." (lista indentada com o
  caminho, board atual desabilitado; `r`, `v`, `b` e `Delete` na lista não agem no board) → escolher
  `Custos` (outro ramo) → seta aparece, dica com o caminho → clicar na seta: caminho completo no topo,
  voltar (botão e `Alt+←`) volta à origem, navegar não cria passo no desfazer → botão de seta curva da
  barra com setas + Enter → link externo no mesmo card (os dois marcadores lado a lado, sem cobrir o texto;
  o clipe continua abrindo o site) → trocar/remover pelo menu e pelo popup, desfazer/refazer → de dentro de
  um board aninhado, atalho para o principal, entrar num filho a partir do destino e voltar duas vezes;
  clicar num nível do caminho acima do destino esquece a origem → excluir o card do destino (marcador
  quebrado, dica e aviso ao clicar, sem navegar) → `Ctrl+Z` (volta a funcionar) → renomear o destino
  digitando no card (dica e caminho com o nome novo) → duplicar o card com link e o card-board de destino
  (ids únicos; o link segue no original) → salvar (`MainController.saveTo`), PDF e PNG com o renderer do
  `MainController` (links das páginas, marcador azul e quebrado vermelho) → reabrir (`showBoard`): links
  válidos e quebrados como estavam. **Não validado por esse roteiro:** os seletores nativos de arquivo
  (Salvar/Abrir/Exportar, código não alterado nesta tarefa), o clique físico do mouse (o caminho do evento
  dentro do JavaFX é o mesmo) e o `mvn javafx:run` no module path.

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
mvn clean test          # recompilar tudo do zero e testar (com JDK 17: ver armadilha 19)
git log --oneline       # histórico de commits
```

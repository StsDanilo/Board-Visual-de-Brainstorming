# BoardVisual — Documentação do Projeto

> Documento de estudo e referência do projeto, mantido junto com o código: toda mudança relevante
> na arquitetura ou nas funcionalidades deve vir acompanhada da atualização deste arquivo.
> Estado descrito: até a exportação para PDF (card [3]), o salvamento em pasta (`board.json` + `imagens/`)
> as imagens nos cards (colar, arrastar, ferramenta Imagem), a exportação do board atual para PNG
> o link externo nos cards (marcador de clipe, menu de clique direito), o link para outro board
> (atalho para qualquer board da árvore, com volta ao board de origem), a ordem de empilhamento salva
> (trazer para frente / enviar para trás), o lock de item (card travado, com cadeado), as seções
> (frames: área colorida com título, atrás dos cards, que leva junto o que está dentro dela), as setas
> com texto (duplo clique na seta) e os estilos de seta (simples, tracejada, ponta dupla, sem ponta,
> escolhidos na barra lateral).

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
Você escolhe quais cards ficam **por cima** de quais (a ordem é salva) e pode **travar** um card
(cadeado) para não movê-lo nem excluí-lo sem querer. **Seções** (frames) são áreas coloridas com título,
sempre atrás dos cards, que organizam o board por tema: arrastar a seção leva junto o que está dentro dela.
As setas podem ter um **texto** no meio ("leva a", "depende de") e um **estilo** (simples, tracejada,
ponta dupla ou linha sem ponta), escolhido ao lado da ferramenta Conexão.
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
| `Card` | Um card: id, x, y, largura, altura, tamanho definido à mão, texto, tamanho da fonte, cor (hex), formato, imagem (nome do arquivo), link (endereço web), link para outro board (id do board de destino), travado (`locked`). Tudo como propriedade observável. `isFrame()`: o card é uma seção (formato `FRAME`). |
| `StackMove` | Enum dos movimentos na ordem de empilhamento: `FORWARD`, `BACKWARD` (um nível), `TO_FRONT`, `TO_BACK` (3.21). |
| `BoardTree` | Consultas na árvore de boards (Java puro): caminho do principal até um board pelo id, lista de todos os boards com o caminho de cada um (indentada), ids da árvore. Usada pela navegação, pela lista de destinos, pelo marcador quebrado e pelo PDF. |
| `WebLink` | Regras do endereço web do card (Java puro): aceita só `http`/`https`, normaliza (`exemplo.com` → `https://exemplo.com`), recusa o resto com mensagem; forma ASCII entregue ao navegador e ao PDF. |
| `CardMarkers` | Marcadores na borda de cima do card (clipe do link externo, seta curva do link para outro board): quais o card tem e onde ficam em cada formato. A mesma conta serve à tela, ao espaço do texto e ao PDF. |
| `CardShape` | Enum dos formatos (`RECTANGLE`, `ELLIPSE`, `DIAMOND`) com o tamanho padrão de cada um, e `FRAME`, a **seção** (3.23): não é um formato do seletor, só a ferramenta Seção cria. |
| `Frames` | Regras das seções (Java puro): conteúdo por geometria (centro dentro da área), o que um arrasto de seções leva junto (`carriedBy`, sem repetir card, seção dentro de seção), caixa de seleção (seção só inteira) e as camadas (`layered`: seções antes dos cards). Altura da faixa do título. |
| `Connection` | Uma seta: id, card de origem, card de destino (fixos) e, mutáveis e observáveis, o **texto** (`label`, uma linha, `""` = sem texto) e o **estilo** (`style`) (3.24). |
| `ConnectionStyle` | Enum dos estilos de seta: `ARROW` (simples, o padrão), `DASHED` (tracejada), `DOUBLE` (ponta dupla), `LINE` (sem ponta); cada um diz se é tracejado e onde tem ponta. |
| `ConnectionGeometry` | Geometria da seta (Java puro, sem JavaFX): onde ela toca a borda de cada formato (`borderPoint`), início, ponta e o centro do texto (`labelCenter`, que afasta os textos de A→B e B→A). Usada pela tela e pelo PDF. |
| `Board` | Conjunto de cards e conexões. Garante consistência (sem seta para card inexistente, sem seta duplicada, sem seta de/para seção). `connect(a, b, estilo)`, `removeConnection`, `findConnection`, `hasReverse` (existe B→A). A ordem da lista de cards é a **ordem de empilhamento** (o último fica por cima); `reorder` a muda por permutação (3.21). As seções ficam sempre no começo da lista (atrás dos cards): `addCard`, `reorder` e `restore` mantêm isso (3.23). |
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
| `MainWindow` | Layout da janela: menu, área do canvas (com a barra de ferramentas por cima), barra de status (com avisos curtos e passageiros, `showStatusMessage`). |
| `BoardView` | O canvas: pan/zoom, camadas (seções numa camada atrás das setas), cria/remove `CardView`/`ConnectionView` quando o board muda, mantém os `CardView`s na ordem de empilhamento (sem recriá-los), seleção, caixa de seleção (seção só inteira), posição da barra flutuante. |
| `CardView` | Desenho de um card: fundo com a silhueta do formato, imagem no topo (cards de imagem, ou o aviso "Imagem não encontrada"), campo de texto, marcadores (clipe do link, seta do link para outro board, com o estado "quebrado"), alça de conexão, cadeado de card travado (`:locked`, só na tela). Seção: fundo translúcido sem clique, anel da borda e faixa do título que pegam o clique, título de uma linha com reticências (`:frame`, 3.23); `hitsScenePoint` diz se um ponto pega o card. |
| `CardImageLoader` | Interface "nome do arquivo → imagem carregada": a view não sabe onde as imagens moram; o controller sabe. |
| `ConnectionView` | Desenho de uma seta, calculado por binding a partir dos dois cards e da própria seta: linha (cheia ou tracejada, `:dashed`), pontas conforme o estilo, área de clique larga e invisível (`connection-hit`), destaque de seta selecionada (`:selected`), texto no meio e o campo de edição dele (os dois em `getLabelLayer()`, que vai para uma camada acima de todas as linhas). |
| `ConnectionStyleOrder` | Ordem dos estilos de seta na barra (Java puro): a posição 0 é o ícone principal; escolher um extra troca de lugar com ele. |
| `CardGeometry` | Matemática: onde a seta toca o contorno de cada formato (delega a `ConnectionGeometry`); recuo do texto dentro do formato. |
| `CardTextFit` | Mede o texto e decide a fonte e o tamanho do card para o texto caber inteiro (3.14). Seção: fonte fixa do título, nunca cresce. |
| `Tool` | Enum das ferramentas: nome, tecla de atalho, formato que cria, ícone, dica (inclusive `FRAME`, a Seção, tecla `S`). |
| `ToolBarView` | Barra lateral de ferramentas + bolinha de cor dos novos cards. Ao lado da ferramenta Conexão, os botões dos outros estilos de seta (aparecem com a ferramenta ativa, com o mouse em cima ou com uma seta selecionada); o ícone da Conexão desenha o estilo das setas novas (`connectionStyleProperty`). |
| `SelectionToolbarView` | Barra flutuante acima da seleção: cor, formato (some se só há imagens; desabilitado se todos estão travados), tamanho do texto, modo do painel (Texto/Lista), link e link para outro board (só com um card), ordem (camadas), cadeado (travar/destravar, com estado misto), duplicar, excluir. |
| `StackOrderButton` | Botão "Ordem" (camadas) da barra flutuante: popup com as quatro ações da ordem de empilhamento e os atalhos; as que não mudariam nada aparecem desabilitadas. |
| `LinkEditorPopup` | Popup pequeno com o campo do endereço do link (Salvar, Cancelar, Remover), com a mensagem de erro da validação. |
| `BoardLinkPickerPopup` | Popup com a lista da árvore de boards (indentada, com o caminho de cada um) para escolher o destino do link para outro board; o board do card aparece desabilitado e o destino atual, marcado. |
| `FloatingPanelView` | O painel aberto ao lado de um card: cabeçalho + corpo de texto ou lista. |
| `BreadcrumbView` | Caminho no canto superior esquerdo (`Principal › board 2 › board 3`), com links e botão voltar (a dica diz para onde ele leva). |
| `ResizeHandlesView` | As 8 alças de redimensionamento em volta do card selecionado (em pixels de tela). |
| `BoardRenderer` | Desenha um board inteiro numa imagem, fora da tela, para o PDF (3.15) e o PNG (3.18); fundo branco ou transparente; links para boards que não existem mais saem com o marcador quebrado; seções atrás das setas e textos das setas acima das linhas, como na tela. |
| `PopupButton` | Base de "botão que abre um painelzinho" (`beforeShowPopup`: atualizar as opções logo antes de abrir). |
| `ColorPickerButton` / `ShapePickerButton` / `FontSizePickerButton` | Os botões com popup (cor, formato e tamanho do texto). |
| `ColorSwatchGrid` | Grade de bolinhas de cor. |
| `CardPalette` | As 8 cores oferecidas. |
| `Icons` | Ícones desenhados com path SVG (inclusive o clipe do link, a seta curva do link para outro board e o ícone de cada marcador, riscado quando quebrado; camadas da ordem; cadeado aberto e fechado; cada estilo de seta desenhado no próprio estilo, `connectionStyle`). |

### `controller/` — o comportamento

| Arquivo | Responsabilidade |
|---|---|
| `MainController` | Monta e conecta tudo (recebe de fora quem abre links no navegador); ações de arquivo (novo, abrir, salvar, salvar como em pasta, limpeza de `imagens/`, exportar PDF, exportar PNG do board atual); desfazer/refazer do menu. |
| `ImageController` | Imagens: inserir (ferramenta Imagem, `Ctrl+V`, arrastar arquivo), pasta de trabalho das imagens ainda não salvas, carregar a imagem de cada card (com cache). |
| `CanvasController` | Mouse no fundo (inclusive o meio vazio de uma seção): pan, zoom, caixa de seleção, criar card (e seção). |
| `CardDragController` | Mouse no card: selecionar, Shift+clique, arrastar (inclusive em grupo; travados ficam parados), com alinhamento. Clicar não muda a ordem de empilhamento. Arrastar uma seção leva os cards de dentro (`draggedCards`); as seções só são referência de alinhamento quando o gesto envolve uma seção (`alignmentReferences`). |
| `CardResizeController` | Redimensionar o card selecionado pelas alças, com alinhamento das bordas puxadas (card de imagem: proporção da imagem; seção: 8 alças, mínimo fixo, sem mexer no conteúdo). Card travado não ganha alças. |
| `CardTextFitController` | Reaplica a fonte e o crescimento de cada card quando o texto, a fonte ou o tamanho mudam. |
| `ConnectionController` | Criar setas arrastando de um card a outro (seções ficam de fora: não começam nem recebem setas), no estilo do ícone principal da ferramenta Conexão. |
| `ConnectionEditController` | Setas prontas: clique seleciona, `Delete` exclui, duplo clique abre o campo do texto (`Enter`/clicar fora confirmam, `Esc` cancela), estilo da seta selecionada pela barra lateral (`applyStyleToSelected`); cada mudança é um passo no desfazer (3.24). |
| `SelectionActionsController` | Cor, formato, tamanho do texto, duplicar e excluir aplicados à seleção (travados: sem formato, sem excluir, com aviso na barra de status; cópia de travado nasce destravada; cópias entram no topo da pilha; seção: sem formato nem tamanho do texto, duplicar/excluir só a seção). |
| `StackOrderController` | Ordem de empilhamento: botão Ordem, quatro itens no menu de clique direito, atalhos (`moveFor`); cada mudança é um passo no desfazer (3.21). |
| `LockController` | Travar/destravar a seleção: botão de cadeado (com estado misto), item "Travar"/"Destravar" no menu, `Ctrl+L`; um passo no desfazer (3.22). |
| `TextEditController` | Registra a edição de texto no histórico (sessões de edição); duplo clique num card de imagem abre a legenda, e no título de uma seção abre o título (`Enter` termina). |
| `PanelController` | Abrir/fechar painéis, itens da lista, troca de modo com confirmação. |
| `NavigationController` | Boards aninhados: entrar, voltar, ir direto a um nível, pular para qualquer board da árvore pelo id (`jumpTo`); guarda o board principal e sabe qual board está na tela e o caminho de nomes até ele. Mostra o resultado; a pilha fica no `NavigationPath`. |
| `NavigationPath` | A pilha de níveis da navegação, sem tela (testável): entrar, voltar, ir a um nível, pular (`jumpTo` reconstrói o caminho real do destino e guarda a pilha de origem para o voltar). |
| `KeyboardController` | Atalhos de teclado (`Delete` exclui a seta selecionada, se houver; senão, os cards). |
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
| `PdfExporter` | Monta o PDF: páginas na ordem da árvore, caminho no topo, links (entre páginas e para sites), páginas de detalhes, marcadores. Seção: texto invisível só na faixa do título, links só nos marcadores (não cobrem os cards de dentro). Texto das setas também invisível no meio da seta (buscável). |
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
         │   ├─ new StackOrderController(...)← botão Ordem, itens do menu (trazer para frente...)
         │   ├─ new LockController(...)      ← botão de cadeado, item Travar/Destravar do menu
         │   ├─ new ConnectionEditController(...) ← selecionar/excluir setas, texto e estilo da seta pronta
         │   │                                  (registrado no "inicializador" de ConnectionView do BoardView)
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
  ├─ filtro no CardView: ajusta a seleção (clique / Shift+clique); NÃO muda a ordem de empilhamento
  └─ handler no body (se não foi no texto):
       guarda ponto inicial (mundo), posições iniciais dos selecionados NÃO travados
       (só travados: nada a mover, termina aqui) e uma foto do board (history.capture())
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

**Card travado (3.22):** fica parado. Num grupo com travados, os outros se movem normalmente (pegando
o grupo por qualquer card, até pelo travado), e o travado serve de referência para o alinhamento.

**Clicar não traz para frente.** Até a ordem de empilhamento (3.21), o filtro do clique chamava
`cardView.toFront()`, que mudava só a tela (ao reabrir, valia a ordem da lista). Isso foi retirado: a
ordem agora é um dado salvo e só muda pelas ações explícitas, e uma seção (3.23) precisa continuar
atrás dos cards ao ser clicada.

**Seção (3.23):** no press, além dos selecionados não travados, entra no arrasto o que está dentro de
cada seção movida (`CardDragController.draggedCards` → `Frames.carriedBy`), calculado **uma vez**, no
início. O conteúdo não entra na seleção visível; o bloco que alinha é só o que foi pego.

## 3.3 Criar uma conexão

Duas formas de começar, ambas no `ConnectionController`:

- Ferramenta **Conexão**: pressionar em qualquer ponto do card.
- Ferramenta **Selecionar**: pressionar na **alça** (bolinha na borda direita).

```
PRESSED  → marca o card de origem; mostra uma linha tracejada de prévia
DRAGGED  → atualiza a ponta da prévia; destaca o card sob o mouse (pseudo-classe :connection-target)
RELEASED → se soltou sobre outro card: history.perform(() -> board.connect(origem, destino, estilo))
           esconde a prévia
```

O `estilo` é o do ícone principal da ferramenta Conexão (`ToolBarView.connectionStyleProperty`), pelos
dois caminhos (ferramenta e alça). Texto, estilo, seleção e exclusão de setas prontas: 3.24.

`Board.connect` recusa: ligar um card a ele mesmo, cards fora do board e seta repetida
(A→B duas vezes, em qualquer estilo). B→A é permitido — é outra seta.

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
| Esc (sem edição, sem painel aberto, na ferramenta Selecionar) | `KeyboardController` — limpa (cards e seta) |
| Clique numa seta | `ConnectionEditController` — seleciona a seta e limpa os cards (3.24) |

**Caixa de seleção:** no press, guarda o ponto inicial e (se Shift) a seleção atual. A cada
arrasto: desenha o retângulo e define `seleção = seleçãoInicial ∪ cardsQueTocamORetângulo`. Uma
**seção** só entra se estiver **inteira** dentro do retângulo (`Frames.marqueeSelects`): uma caixa
começada no meio vazio de uma seção pega os cards dela, não a seção.
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
  (para site e para outro board) vão junto: a cópia leva ao mesmo destino. As cópias entram no **topo** da
  ordem de empilhamento, entre si na ordem dos originais (3.21); a cópia de um card travado nasce
  **destravada** (3.22).
- **Excluir:** `board.removeCard(card)`, que antes remove todas as setas ligadas ao card.
  A seleção se ajusta sozinha (o `BoardView` retira da seleção cards que saem do board). Cards
  **travados** não são excluídos: ficam (selecionados) e aparece um aviso na barra de status (3.22).

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

Com **exatamente um** card selecionado, **não travado** (3.22), e a ferramenta Selecionar, aparecem alças
em volta dele (`ResizeHandlesView`, desenhada pelo `BoardView` em pixels de tela, como as guias). O
`CardResizeController` observa o formato e o travado do card selecionado: destravar (ou desfazer o
travar) faz as alças aparecerem na hora.

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
| V R E L P B S I C, Delete, Ctrl+D, Ctrl+V, Ctrl+Shift+Z, Alt+←, Espaço, Esc | `KeyboardController` | teclas sem item de menu ou com lógica condicional (`S` = Seção, 3.23) |
| Enter no título de uma seção | `TextEditController` | termina a edição do título (que é de uma linha) |
| Ctrl+] / Ctrl+[ e Ctrl+PgUp / Ctrl+PgDn (com Shift: topo / fundo) | `KeyboardController` → `StackOrderController.moveFor` | ordem de empilhamento (3.21) |
| Ctrl+L | `KeyboardController` → `LockController` | travar / destravar (3.22); a letra L sozinha continua sendo o Losango |
| Delete / Backspace com uma seta selecionada | `KeyboardController` → `ConnectionEditController.deleteSelected` | exclui a seta (senão, os cards selecionados) (3.24) |
| Enter / Esc no campo do texto da seta | `ConnectionEditController` (filtro no campo) | confirma / cancela; não chegam aos atalhos da janela (3.24) |

**Colchetes no teclado ABNT2 (verificado):** o JavaFX 21 no Windows dá a `KeyCode` pela **posição** da tecla
no teclado americano (tabela de teclas virtuais do glass). No ABNT2, a tecla `[` (ao lado do `´`) é a
`VK_OEM_6` e chega como `KeyCode.CLOSE_BRACKET`; a tecla `]` (ao lado do `~`) é a `VK_OEM_5` e chega como
`KeyCode.BACK_SLASH`; e `KeyCode.OPEN_BRACKET` é a tecla do acento agudo. Conferido perguntando ao próprio
JavaFX, nesta máquina (layout 0416), qual `KeyCode` ele dá a cada caractere (`getKeyCodeForChar`, sem gerar
tecla nenhuma). Comparar a `KeyCode` (`KeyCodeCombination(CLOSE_BRACKET)`) **trocaria** as ações no ABNT2.
Por isso os colchetes são `KeyCharacterCombination("]")`/`("[")`: o JavaFX acha a tecla pelo **caractere**
no layout atual, e o atalho funciona no ABNT2 e no americano. `Ctrl+PgUp`/`Ctrl+PgDn` ficam como
alternativa que não depende do layout. Não validado com a tecla física: o roteiro de interface dispara
os `KeyEvent` com as `KeyCode`s que o glass produz para essas teclas (sem entrada do sistema).

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
os grupos de contribuidores são separados por uma linha. Hoje contribuem o `LinkController`, o
`BoardLinkController` (3.20), o `StackOrderController` (3.21) e o `LockController` (3.22), nessa ordem; os
dois últimos entraram sem mexer no menu.
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

Frames. (O link para outro board, que reaproveita o marcador e o menu, é a 3.20; a ordem de empilhamento,
que pluga itens no mesmo menu, é a 3.21.)

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
- Fora do escopo: frames, busca global.

## 3.21 Ordem de empilhamento (trazer para frente / enviar para trás)

Quando cards se sobrepõem, a **ordem de empilhamento** diz quem fica por cima. Ela é a **posição do card em
`board.getCards()`**: o primeiro fica no fundo, o último por cima. Essa lista já era a ordem de desenho, do
arquivo (o array `cards` do JSON) e do PDF/PNG; o que muda é que agora há controle explícito sobre ela, e
ela é **salva**.

> **Mudança de comportamento:** clicar num card **não o traz mais para frente**. Antes o clique fazia
> `toFront()` só na tela (ao reabrir, voltava a ordem da lista: era a armadilha 10). Agora a ordem muda
> só pelas ações abaixo. Uma seção (3.23) precisa continuar atrás dos cards ao ser clicada.
>
> **Seções (3.23):** formam uma camada própria, sempre abaixo dos cards comuns; as ações abaixo movem
> cada seção só entre as seções (e cada card só entre os cards).

| Ação | Menu de clique direito | Barra flutuante | Atalho |
|---|---|---|---|
| Um nível para cima | "Trazer para frente" | botão **Ordem** (camadas) → lista | `Ctrl+]` ou `Ctrl+PgUp` |
| Para o topo | "Trazer para o topo" | idem | `Ctrl+Shift+]` ou `Ctrl+Shift+PgUp` |
| Um nível para baixo | "Enviar para trás" | idem | `Ctrl+[` ou `Ctrl+PgDn` |
| Para o fundo | "Enviar para o fundo" | idem | `Ctrl+Shift+[` ou `Ctrl+Shift+PgDn` |

- **Menu:** um grupo próprio, depois dos links (contribuidor registrado depois do `BoardLinkController`). As
  ações que não mudariam nada (ex.: "Trazer para frente" com o card já no topo) aparecem **desabilitadas**.
- **Barra (decisão):** **um botão com popup** (`StackOrderButton`), não quatro botões: a barra já tem dez
  botões, e as quatro ações são raras. O popup lista as quatro com o atalho ao lado; as que não mudariam
  nada ficam desabilitadas (`PopupButton.beforeShowPopup`).
- **Atalhos:** fora da edição de texto (3.16). Colchetes pelo caractere, por causa do ABNT2 (3.16).

### Regras (`Board.reorder`, Java puro, testável)

```
reorder(cards, movimento)
  TO_FRONT: os outros na ordem de antes, depois os escolhidos na ordem de antes
  TO_BACK:  os escolhidos, depois os outros
  FORWARD:  do topo para baixo, cada escolhido troca de lugar com o vizinho de cima se ele não é escolhido
  BACKWARD: espelhado (de baixo para cima, com o vizinho de baixo)
  aplica com cards.sort(posição na nova ordem)  → aviso de PERMUTAÇÃO, não de "saiu e entrou"
```

- **Vários selecionados mantêm a ordem relativa** entre eles (não importa a ordem em que foram
  selecionados). Um bloco de selecionados juntos sobe/desce junto, um nível.
- **Um nível = uma posição na lista** (decisão): troca com o vizinho, mesmo que ele esteja longe na tela
  e nada mude visualmente. É o comportamento do PowerPoint/Google Slides, previsível e testável; para
  passar de vários de uma vez, "para o topo"/"para o fundo". (Pular até o próximo card **sobreposto** seria
  uma evolução possível.)
- **Limites:** no topo, "para frente" não faz nada; no fundo, "para trás" também. Sem mudança, **nenhum passo**
  no desfazer (`StackOrderController.reorder` confere `canReorder` antes).
- Cada ação é **um passo** no desfazer. Card **travado** (3.22) muda de ordem normalmente (a trava protege
  posição e tamanho, não a pilha).

### O que mais respeita a ordem

| Onde | Como |
|---|---|
| Tela | O `BoardView` escuta a lista: numa permutação, chama `toFront()` em cada `CardView` na ordem nova. O JavaFX trata `toFront` como troca de lugar entre irmãos: **o nó não sai da cena**, então foco, seleção, painel aberto e handlers continuam (conferido no roteiro de interface: nenhum `CardView` é recriado) |
| Desfazer | A foto (`BoardSnapshot`) guarda os cards na ordem; `restore` põe os cards na ordem da foto (permutação, mesmos objetos). Desfazer uma exclusão devolve o card ao **seu lugar** na pilha (antes ia para o topo) |
| Salvar/abrir | O array `cards` do JSON sempre esteve na ordem da lista; nada mudou no formato |
| PDF e PNG | O `BoardRenderer` monta os `CardView`s na ordem de `board.getCards()` |
| Novo card, colar imagem | Entram no **topo** (como antes) |
| Duplicar (decisão) | As cópias entram no **topo**, entre si na ordem dos originais. Alternativa descartada: "logo acima do original" (mais regras, e a cópia sai deslocada por cima do original de qualquer jeito) |
| Mover, redimensionar, mudar cor/texto | Não mexem na ordem |
| Boards aninhados | Cada board tem a sua ordem; `deepCopy` (duplicar card-board) mantém a ordem dos filhos |

## 3.22 Lock de item (card travado)

Um card **travado** fica protegido contra mudanças sem querer (clique errado, arrasto em grupo), no estilo
do cadeado do Canva. É um booleano no card, `Card.locked`, que percorreu o checklist 5.1 inteiro (cópia,
foto, arquivo).

| Onde | Como |
|---|---|
| **Clique direito** no card | "Travar" (ou "Destravar", se toda a seleção está travada), num grupo próprio no fim do menu |
| **Botão de cadeado** na barra flutuante | Ao lado do botão Ordem. Aberto = nada travado; fechado e marcado = tudo travado; fechado e **tracejado** = seleção mista (como a cor "mista") |
| **`Ctrl+L`** | Fora da edição de texto. Não conflita: a letra `L` sozinha é o Losango, e o `Ctrl+L` não era usado |

Com seleção múltipla vale para todos: se **todos** estão travados, destrava; senão (nenhum ou alguns), trava
todos. Cada travar/destravar é **um passo** no desfazer (o mesmo estado de novo não é passo).

### O que o travado bloqueia e o que permite (decisões)

| Bloqueia | Onde |
|---|---|
| Mover (arrastar) | `CardDragController.movableCards`: os travados ficam fora do arrasto. No **arrasto em grupo** os outros se movem e o travado fica; pegar o grupo pelo travado também move os outros. Só travados: o arrasto não move nada e não cria passo |
| Redimensionar | `CardResizeController`: as alças **não aparecem** |
| Trocar o formato | `SelectionActionsController.applyShape` pula os travados (o formato muda o tamanho); com só travados, o botão de formato fica desabilitado |
| Excluir | `SelectionActionsController.delete` exclui só os destravados; os travados **ficam selecionados** e aparece um aviso na barra de status ("Card travado não é excluído: destrave antes...", some em 5 s). Escolhido em vez de excluir mesmo assim (não protegeria) ou de um diálogo de confirmação (interrompe a cada Delete) |

| Permite (continua livre) | Por quê |
|---|---|
| Selecionar, Shift+clique, caixa de seleção | Para conseguir destravar e para agir em grupo |
| Editar o texto, cor, tamanho do texto | Conteúdo e aparência, não posição. (O texto pode fazer o card crescer: o crescimento automático continua) |
| Setas (criar a partir dele ou até ele), links e seus marcadores, painel, entrar no board | Nada disso move o card |
| Ordem de empilhamento | A pilha não é posição nem tamanho |
| **Duplicar** | A **cópia nasce destravada** (para poder ser posicionada); o original continua travado. `Card.copy()` copia tudo, inclusive o `locked`; quem destrava a cópia é o `SelectionActionsController.duplicate` |

Não existe outro gesto que mova cards (não há "mover com as setas do teclado"); o crescimento do card pelo
texto (3.14) não é um gesto do usuário e continua valendo.

### A aparência

- Pseudo-classe **`:locked`** no `CardView`: o cursor sobre o card deixa de ser o de mover (`app.css`).
- Um **cadeado pequeno** (16 px, `.card-lock-badge`) no **canto de cima à esquerda**, metade para fora:
  longe dos marcadores (em cima, à direita no retângulo e no meio da elipse/losango), do botão do painel
  (embaixo), da alça de conexão (à direita) e da barra flutuante (que fica 10 px acima do card e, sem alças,
  não desce até ele). Não recebe cliques. Na elipse e no losango ele fica no canto da caixa, fora da
  silhueta. O acabamento estético fica para depois.
- **PDF e PNG:** o cadeado **não sai** (`.board-export .card-lock-badge { visibility: hidden; }`): é um estado
  de edição, não conteúdo.

### Salvar e abrir

`"locked": true` no card do `board.json`, só nos travados (destravado não grava o campo). Saves anteriores
não têm o campo: abrem destravados. **Sem** novo bump de `version`.

## 3.23 Seções (frames coloridos)

Uma **seção** é uma área colorida, com título no topo, que fica **atrás dos cards** e os organiza por tema
(como os frames do Miro e as colunas do Milanote). Na interface ela se chama **Seção**; no código, frame.

**Decisão de modelo (a mais simples):** a seção é um **card** com o formato `CardShape.FRAME`, e não uma
propriedade nova. Assim ela ganha de graça posição, tamanho, texto (o título), cor, link, link para outro
board, travado, desfazer, salvar/abrir (o `"shape": "FRAME"` já cabe no campo `shape` do JSON: **nenhum campo
novo e nenhuma mudança de `version`**) e cópia. O `switch` exaustivo do `CardShape` apontou cada lugar que
precisava decidir algo (5.2). `Card.isFrame()` é o atalho para `getShape() == FRAME`.

### Criar

Ferramenta **Seção** na barra lateral (ícone de moldura com a faixa do título), tecla **`S`** (estava livre;
`Ctrl+S` continua sendo Salvar). Clicar no canvas cria uma seção de 480 × 320 centrada no clique, com a cor
atual da barra, já editando o título (como um card novo). A seção nova entra no **topo das seções**, logo
abaixo de todos os cards comuns (ver "Camadas").

### O título

- É o **texto do card**. Fora da edição aparece num rótulo (`frameTitle`) de **uma linha**, em fonte fixa
  (15 px, negrito; `CardTextFit.FRAME_TITLE_FONT_SIZE`), cortado com reticências se for longo; quebras de
  linha viram espaço. Seção sem título mostra "Seção" esmaecido (só na tela; no PDF/PNG fica em branco).
- **Duplo clique** no título (ou na borda) abre a edição: aparece o campo de texto, na mesma faixa;
  **`Enter` termina** (o título é de uma linha), como o `Esc`. Toda a edição é um passo no desfazer (4.7).
- **Nunca faz a seção crescer:** o `CardTextFit` devolve sempre o tamanho definido à mão para `FRAME`, e o
  botão de tamanho do texto some da barra quando só há seções selecionadas.
- A faixa do título tem `Frames.TITLE_HEIGHT` (34) de altura. Os marcadores (clipe, seta curva) ficam nela,
  à direita, e o título termina antes deles (`CardGeometry.frameTitleInsets`); o cadeado fica no canto de
  cima à esquerda, e o título começa 16 px para dentro, sem encostar nele.

### Onde a seção pega o clique (picking, 4.4)

| Onde | O que acontece |
|---|---|
| **Borda** (anel de 10 px, metade para fora) ou **faixa do título** | É a seção: selecionar, Shift+clique, arrastar, clique direito (menu), duplo clique (título) |
| **Meio vazio** | É o **fundo do canvas**: caixa de seleção, clicar com uma ferramenta de criação cria o card ali (dentro da seção), arrastar com outra ferramenta faz pan, clique limpa a seleção |
| Cards dentro dela | Clicáveis normalmente (estão por cima) |

O `CanvasController` não sabe de seções: o clique no meio vazio já chega a ele como clique no fundo
(`isBackground`), porque o JavaFX não acha nenhum nó ali (detalhes em 4.4).

### Mover leva o que está dentro (só geometria)

- Um card está **dentro** de uma seção quando o **centro** dele cai na área dela, bordas incluídas
  (`Frames.contains`). **Não há vínculo guardado:** soltar um card por cima de uma seção já o coloca nela, e
  tirá-lo de cima já o tira.
- Arrastar uma seção (pela borda ou pelo título) move junto o que está dentro dela **no início do arrasto**
  (`Frames.carriedBy`, chamado uma vez no press). O conteúdo **não** entra na seleção visível.
- **Sem mover em dobro:** um card dentro de duas seções arrastadas juntas, ou selecionado junto com a seção
  dele, anda uma vez só (o conjunto é um `Set`).
- **Seção dentro de seção** (na geometria): a de fora leva a de dentro e, por consequência, o que está
  dentro da de dentro (mesmo que esse card esteja fora da de fora). A de dentro sozinha só leva o dela.
- **Redimensionar não mexe no conteúdo:** as alças mudam só a seção (8 alças, como o retângulo; mínimo de
  120 × 80, sem depender do título). Um card que fica fora da área nova simplesmente deixa de pertencer.
- Cada gesto é **um passo** no desfazer (o arrasto inteiro, com o conteúdo).

### Travado (3.22) — decisões

- **Seção travada** não se move, então **não leva nada** (os cards de dentro continuam livres para serem
  arrastados um a um). Também não mostra alças e não é excluída.
- **Card travado dentro de uma seção que se move: vai junto.** A trava protege contra arrastar *o card*
  (clique errado, arrasto em grupo pegando-o sem querer). Mover a seção é organizar o tema inteiro: se o
  travado ficasse para trás, a seção perderia a organização e o card ficaria "solto" fora dela. Sozinho ele
  continua parado. (Mesma regra para uma seção travada dentro de outra seção que se move.)

### Camadas e ordem de empilhamento (3.21) — invariante

**As seções ficam sempre abaixo de todos os cards comuns.** Na lista `board.getCards()` elas vêm primeiro
(`Frames.layered`); o `Board` mantém isso:

| Onde | Como |
|---|---|
| Criar (`addCard`) | Seção entra no **topo das seções** (logo abaixo do primeiro card comum); card comum, no topo da pilha |
| Ordem (`reorder`/`canReorder`) | Seções e cards são **duas camadas**: cada grupo se reordena só dentro da sua. "Trazer para o topo" numa seção a leva ao topo das seções, nunca por cima de um card; "Enviar para o fundo" num card o leva ao fundo dos cards, logo acima das seções. As ações que não mudariam nada aparecem desabilitadas, como antes |
| Desfazer (`restore`) | A ordem da foto, com as seções na frente (uma foto do próprio board já vem assim) |
| Abrir | Cada card entra pelo `addCard`: um arquivo editado à mão com uma seção depois de um card abre com a seção atrás. Saves sem seções abrem exatamente como antes |
| Duplicar | A cópia da seção vai para o topo das seções |

**Decisão:** seção nova no **topo das seções**, não no fundo de tudo. As duas respeitam o invariante, mas
com o topo uma seção pequena criada dentro de uma maior fica por cima dela (dá para ver e pegar a borda da
pequena). Para mandá-la ao fundo, "Enviar para o fundo".

Na **tela** as seções ficam numa **camada própria** do `BoardView` (`frameLayer`), **atrás das setas**: uma
seta entre dois cards de dentro passa por cima do fundo da seção (antes da camada própria ela ficava por
baixo do fundo translúcido). O `BoardRenderer` (PDF e PNG) faz o mesmo.

### Setas, alinhamento, duplicar, excluir, formato

- **Setas:** seções não participam. A ferramenta Conexão não começa seta numa seção, soltar a seta numa
  seção não liga, a alça de conexão não aparece nela, e o `Board` recusa uma seta de ou para uma seção
  (inclusive vinda de um arquivo, que é ignorada ao abrir).
- **Alinhamento (linhas guia, 3.12):** ao arrastar ou redimensionar **cards comuns**, as seções **não**
  servem de referência (não poluem com guias da borda de uma seção enorme). Ao arrastar ou redimensionar
  **uma seção**, ela alinha com todos os outros (seções e cards) normalmente
  (`CardDragController.alignmentReferences`).
- **Duplicar** uma seção duplica **só a seção** (o conteúdo não é dela, é geometria). A cópia, deslocada
  24 px, já "contém" por geometria o que estiver sob ela.
- **Excluir** uma seção exclui **só a seção**; os cards de dentro ficam onde estão. O desfazer a devolve ao
  seu lugar na pilha.
- **Formato:** a seção não tem seletor de formato (o botão some quando só há seções; o `applyShape` pula
  seções) e nunca vira outro formato; nenhum formato vira seção. Também não tem painel flutuante nem board
  aninhado (a ferramenta Seção não cria). **Cor**, **link**, **link para outro board** e **cadeado** funcionam
  como em qualquer card.

### Aparência

- `.card.shape-frame` no `app.css`: o `body` **não tem fundo** (`-fx-background-color: null`) nem sombra,
  só a borda (cor da seção escurecida, `derive`). O fundo é o `.frame-fill`: a cor do card com
  `-fx-opacity: 0.45` (tom claro e translúcido, deixa ver os cards e o fundo do canvas). Selecionada, a borda
  ganha o destaque azul como os cards.
- Acabamento estético (tamanho do título, cores mais suaves, cantos) fica para a lista de melhorias.

### PDF e PNG

- A seção sai **atrás**, como no board (mesmo `CardView`, camada de seções atrás das setas).
- **Texto invisível** (busca/cópia) só sobre a **faixa do título** (`PdfExporter.textRect`).
- **Links:** a seção nunca ganha uma área clicável do tamanho dela. Se ela tem link (site ou outro board),
  ele fica **só no marcador**; os cards de dentro mantêm os links deles inteiros (a anotação da seção vem
  antes, e não cobre os cards).

### Fora do escopo

"Agrupar elementos" (3.21/5.6), copiar/colar entre boards. (Setas com texto e estilos de seta vieram
depois: 3.24.)

## 3.24 Setas com texto e estilos de seta

Uma seta pode ter um **texto** no meio ("leva a", "depende de") e um **estilo**: seta simples (o padrão),
tracejada, ponta dupla ou linha sem ponta. A `Connection` deixou de ser imutável: origem, destino e id
continuam fixos; `label` e `style` são propriedades observáveis, e a `ConnectionView` acompanha por binding.

### Selecionar uma seta (o mínimo para editar)

Antes, setas não eram clicáveis. Agora (`ConnectionEditController`):

- **Clique** na seta seleciona a seta (linha e pontas azuis, contorno azul no texto) e tira a seleção dos
  cards; selecionar um card tira a seleção da seta. Nunca há seta e cards selecionados juntos, e no
  máximo uma seta. Com a seta selecionada a barra flutuante não aparece (ela é dos cards).
- **`Delete`/`Backspace`** exclui a seta selecionada (os cards ficam), num passo do desfazer.
- **`Esc`**, clicar no fundo ou num card limpa a seleção da seta.
- A seleção de seta mora no `BoardView` (`selectConnection`, `selectedConnectionProperty`): é estado só da
  tela, não é salva e não entra no desfazer. Uma seta que sai do board (excluída, desfeita, troca de board)
  sai da seleção.
- **Área de clique:** a linha visível tem 2 px; por cima dela há uma linha **transparente de 14 px**
  (`.connection-hit`) só para o clique. A linha e as pontas são `mouseTransparent`. A área de clique só
  existe na ferramenta **Selecionar** (CSS: `visibility: hidden` em `:tool-create` e `:tool-connection`);
  nas outras, clicar sobre a seta age no fundo, como antes (ex.: criar um card ali). As setas ficam atrás
  dos cards, então perto das pontas, dentro do card, o clique é do card. A caixa de seleção começada no
  fundo atravessa setas normalmente; começar o arrasto exatamente sobre uma seta seleciona a seta.

### Escrever o texto

- **Duplo clique** na seta (ou no próprio texto) abre um campo no meio dela, com o texto atual selecionado.
- **`Enter`** confirma; **clicar fora** (perder o foco) também confirma, como o texto dos cards; **`Esc`**
  cancela (o campo consome `Enter` e `Esc` num filtro: não chegam aos atalhos da janela).
- Cada confirmação que muda o texto é **um passo** no desfazer (`history.perform` ao confirmar; cancelar ou
  confirmar sem mudança não cria passo). Espaços nas pontas são tirados; texto vazio = seta sem texto.
- Enquanto o campo tem o foco, as letras são do texto (`isEditingText()`, 3.16): "r" não troca de
  ferramenta e `Delete` apaga letra, não a seta.
- **Limites:** o campo aceita até **60 caracteres** (`Connection.MAX_LABEL_LENGTH`); o texto mostrado tem
  no máximo **220 px** e termina em "…" se não couber (uma linha só; quebras de linha vindas de um arquivo
  editado à mão viram espaço). O texto **nunca** muda o tamanho dos cards.

### Aparência do texto

- Horizontal, centrado no meio da seta (`ConnectionGeometry.labelCenter`), fonte de 12 px, com fundo da cor
  do canvas (`.connection-label`) para não se misturar com a linha; no PDF/PNG o fundo é branco.
- O texto e o campo ficam num grupo à parte (`ConnectionView.getLabelLayer()`), que o `BoardView` (e o
  `BoardRenderer`) põem numa **camada acima de todas as linhas** e abaixo dos cards. Dentro da própria
  `ConnectionView`, a linha de uma seta desenhada depois passaria por cima do texto (aconteceu com A→B e B→A).

### A→B e B→A (armadilha 11)

As duas setas ficam sobre a mesma reta. O `BoardView` marca cada uma com `setReverse` (`Board.hasReverse`):

- **Textos:** cada um anda, ao longo da reta, para o lado do próprio destino, o suficiente para ficarem
  lado a lado (6 px de folga) em vez de um por cima do outro. Se a contrária é excluída, o texto volta ao meio.
- **Área de clique:** cada uma vai para o **seu lado** da linha (7 px, metade da largura), à esquerda de quem
  anda no sentido da seta: acima da linha pega uma, abaixo pega a outra. Sem isso, só a de cima seria clicável.

### Estilos (barra de ferramentas lateral)

- O ícone da ferramenta **Conexão** desenha o **estilo principal**: o das setas novas, criadas pela
  ferramenta **ou pela alça** do card. Ao lado dele ficam **botões extras** com os outros três estilos,
  cada um desenhado no próprio estilo (`Icons.connectionStyle`).
- **Quando os extras aparecem:** com a ferramenta Conexão ativa, com o mouse sobre ela (ou sobre eles), ou
  com uma seta selecionada. A barra fica mais larga enquanto eles aparecem (acabamento visual fica para depois).
- **Clicar num extra troca de lugar** com o principal (`ConnectionStyleOrder.choose`): o escolhido vira o
  principal e o antigo principal vai para a posição do escolhido. São sempre os mesmos quatro estilos.
- **Sem seta selecionada**, clicar num extra também ativa a ferramenta Conexão. **Com uma seta selecionada**,
  clicar num extra troca de lugar **e aplica o estilo na seta** (um passo no desfazer), sem trocar de
  ferramenta; e clicar no **ícone principal** aplica o estilo principal na seta em vez de ativar a ferramenta
  (a tecla `C` continua ativando). Ligação: `ToolBarView.connectionSelectedProperty` (ligada à seleção do
  `BoardView`) e `setOnConnectionStylePicked` → `ConnectionEditController.applyStyleToSelected`.
- O estilo principal **não** é lembrado entre sessões do app (decisão do Danilo): ao abrir, é a seta simples.
- **Desenho** (`ConnectionView`): tracejada = `:dashed` → `-fx-stroke-dash-array: 8 6`; as pontas são o
  mesmo triângulo, a da origem girada 180°; a linha termina na **base** de cada ponta que existe (na ponta
  dupla, nas duas; na linha sem ponta, chega às bordas).
- As regras do `Board.connect` não mudam com o estilo: uma linha sem ponta de A para B também impede outra A→B.

### Salvar, desfazer, PDF e PNG

- Arquivo: `ConnectionData` ganhou `label` e `style`, gravados só fora do padrão (sem texto / seta simples =
  campo ausente). Saves anteriores (e o `.json` antigo) abrem com seta simples sem texto; um estilo
  desconhecido vira seta simples. Sem bump de versão.
- Desfazer: `BoardSnapshot.ConnectionState` guarda texto e estilo; `Board.restore` atualiza no lugar as setas
  que continuam existindo (como os cards) e recria as que faltam já com texto e estilo.
- Duplicar um card continua **não** copiando setas.
- PNG e PDF usam as mesmas `ConnectionView`s (estilo e texto aparecem). No PDF o texto da seta também vai como
  **texto invisível** no meio da seta (`PdfExporter.labelRect`, mesma conta da tela, largura medida na
  Helvetica): dá para buscar e copiar.

### Fora do escopo

Curvas, espessura, cor da seta, mudar a ponta (origem/destino) de uma seta pronta, texto em mais de uma linha.

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
        () -> point(ConnectionGeometry.start(connection)),
        deps);   // deps = x, y, width, height, shape dos DOIS cards (+ o estilo, nas partes que dependem dele)
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
     │   │   │   ├─ frameLayer (Group)       → CardView das seções (3.23)
     │   │   │   ├─ connectionLayer (Group)  → ConnectionView... (linha, pontas, área de clique)
     │   │   │   ├─ connectionLabelLayer     → texto de cada seta + campo de edição (3.24)
     │   │   │   ├─ cardLayer (Group)        → CardView...
     │   │   │   └─ overlayLayer (Group)     → linha de prévia da conexão
     │   │   ├─ marquee (Rectangle)          ← fora do world: não sofre zoom
     │   │   └─ SelectionToolbarView         ← fora do world: não sofre zoom
     │   └─ ToolBarView                      ← por cima do canvas, à esquerda
     └─ bottom: barra de status
```

As setas ficam numa camada **abaixo** dos cards, por isso passam "por trás" deles, e **acima** das seções,
por isso passam por cima do fundo delas (3.23). Os textos das setas ficam numa camada própria entre as
linhas e os cards: nenhuma linha passa por cima de um texto (3.24).

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
| `CardView`, press (seleção) | filtro | Deve acontecer até se o clique foi no texto. (Não traz mais para frente: 3.21.) |
| `CardView`, press da conexão | filtro + consume | Deve **impedir** o `TextArea` de receber o clique. |
| `body`, press/drag do arrasto | handler | Só se o clique **não** foi no texto (checado com `isTextNode`). |
| `CardView`, press (seleção e conexão) | filtro | **Ignora** o clique num marcador (`isMarkerNode`): o clipe só abre o link. |
| `BoardView`, release do botão direito | filtro | Abre o menu do card se não houve movimento (3.19). |
| `BoardView`, `CONTEXT_MENU_REQUESTED` sobre card | filtro + consume | Impede o menu nativo do `TextArea` por cima do nosso. |
| `Stage`, teclas | handler | Só teclas não consumidas pelo campo de texto. Os atalhos da ordem comparam por caractere (`KeyCharacterCombination`), não pela `KeyCode` (3.16). |
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

`BoardView.cardViewAt` (usado para saber sobre qual card uma seta foi solta e qual card recebe o menu de
clique direito) usa `CardView.hitsScenePoint`, que também respeita a silhueta.

### Seção: o meio vazio não é dela

Uma seção (3.23) precisa pegar o clique só na **borda** e no **título**; no meio vazio o clique tem que
cair no fundo do canvas. Como um `Region` com `pickOnBounds = false` decide o clique (`Region.contains`):

1. se tem **fundo** (`Background` com algum preenchimento, **mesmo transparente**), o retângulo do fundo conta;
2. senão, só o **anel das bordas** (`BorderStroke`: entre o lado de fora e a largura da borda) conta.

A cor não importa: um fundo `transparent` ainda pega o clique. Por isso, na seção:

```
body (.card.shape-frame)  -fx-background-color: null  → sem fundo: só a borda de 1,5 px conta
 ├─ frameFill   fundo translúcido, mouseTransparent    → desenha sem pegar clique
 ├─ frameGrip   sem fundo, borda TRANSPARENTE de 10 px com insets -5 → o anel em volta pega o clique
 ├─ frameTitle  Label com pickOnBounds = true           → a faixa inteira do título pega o clique
 └─ content/textArea  mouseTransparent fora da edição   → não rouba o clique da faixa do título
```

O `frameGrip` e o `frameTitle` são filhos do `body`, então o evento sobe até ele e os handlers de sempre
(selecionar, arrastar, menu) funcionam sem saber de seções. No meio vazio o JavaFX não acha nó nenhum da
seção, então o alvo é o `BoardView` (`isBackground` verdadeiro). Conferido com o picking **real** da cena no
roteiro de interface (C) e no `FrameViewTest`.

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
`:mixed` (cor e cadeado da seleção mista), `:has-board`, `:has-image`, `:broken`, `:locked` (card
travado), e as nativas `:hover`, `:pressed`, `:focus-within`, `:disabled`.

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

### Onde a seta toca o card (`ConnectionGeometry.borderPoint`)

A conta mora no modelo (`ConnectionGeometry`, Java puro) porque o PDF também precisa dela (texto
invisível das setas, 3.24); `CardGeometry.borderPoint` só a repassa como `Point2D` para a tela.

A partir do centro `(cx, cy)`, na direção `(dx, dy)` do outro card, procuramos `t` tal que
`(cx + t·dx, cy + t·dy)` esteja **no contorno**. Com `a = largura/2` e `b = altura/2`:

| Formato | Equação do contorno | `t` |
|---|---|---|
| Retângulo | `|x| = a` ou `|y| = b` | `min(a/|dx|, b/|dy|)` |
| Elipse | `(x/a)² + (y/b)² = 1` | `1 / √((dx/a)² + (dy/b)²)` |
| Losango | `|x|/a + |y|/b = 1` | `1 / (|dx|/a + |dy|/b)` |

A ponta da seta é um triângulo desenhado apontando para +x com a ponta na origem; ele é
**transladado** até o ponto de contato e **girado** pelo ângulo `atan2(dy, dx)`. A linha termina na
base do triângulo (não na ponta) para não "vazar" à frente dele. Na ponta dupla, a ponta da origem é o
mesmo triângulo girado `ângulo + 180°`, e a linha também começa na base dela; na linha sem ponta, a linha
vai de borda a borda (3.24).

**Centro do texto da seta (`ConnectionGeometry.labelCenter`):** o meio entre início e ponta. Com a seta
contrária (B→A), o texto anda ao longo da reta para o lado do próprio destino: `largura/2 / |cos|` se a reta
é mais deitada, `altura/2 / |sin|` se é mais em pé, mais 6 px. Assim as duas caixas horizontais ficam lado a
lado, encostadas, em qualquer inclinação.

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
4. Pôr os cards na ordem da foto (ordem de empilhamento, 3.21), por permutação: mesmos objetos
5. Para cada conexão da foto: existe? → aplicar texto e estilo no MESMO objeto Connection
                              não?    → recriar ligando aos cards pelos ids, já com texto e estilo
```

A ordem dos cards faz parte da foto (é uma lista): mudar só a ordem já é uma "mudança" para o
`perform`, e um card excluído volta ao seu lugar na pilha, não ao topo.

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
| Card sem `locked` (saves anteriores) | card destravado, sem novo bump de `version` — 3.22 |

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
- [ ] `Card.copy()`: copiar o valor. O `copy` é uma cópia **fiel** (o `CardAllPropertiesTest` confere); se a
  **ação** de duplicar deve mudar algo na cópia (como o `locked`, que nasce `false`), faça isso na ação
  (`SelectionActionsController.duplicate`), não no `copy`
- [ ] `BoardSnapshot.CardState`: novo componente + `of()` + `applyTo()` → **senão desfazer ignora a propriedade**
  (exemplo completo: veja como `panelMode`, `detailText` e `listItems` foram adicionados, ou
  `baseWidth`/`baseHeight`/`fontSize`, com valor padrão para arquivos antigos; os mais recentes são
  `imageFile`, `link`, `boardLink` e `locked`, que percorreram o checklist inteiro)
- [ ] `BoardFileFormat.CardData`: novo componente → **senão não salva**
- [ ] `BoardStorage.toData` / `fromData`: mapear, com valor padrão para arquivos antigos (booleano: use
  `Boolean` no `CardData`, grave `null` quando for o padrão, e leia com `Boolean.TRUE.equals(...)`, como o `locked`)
- [ ] `CardView`: refletir visualmente (pseudo-classe ou variável CSS), se houver efeito visual; se for um
  **marcador** na borda do card (como o clipe do `link`), use `CardMarkers` (3.19) em vez de posicionar à mão
- [ ] Valor que vem de fora e vai para o sistema (como o `link`): validar no **setter** do modelo (o `link`
  é só leitura por fora) e de novo antes de usar; nunca confiar no que veio do arquivo
- [ ] Referência a outra parte da árvore (como o `boardLink`): guarde o **id** (não o objeto nem o nome),
  mantenha o id ao abrir mesmo que o alvo não exista mais, e mostre o estado "quebrado" na tela (3.20)
- [ ] `app.css`: estilo (se algo for só de edição, como o cadeado do `locked`, esconda na exportação com
  `.board-export ... { visibility: hidden; }`)
- [ ] Controller: quem altera deve usar `history.perform`
- [ ] Propriedade que **restringe** ações (como o `locked`): a regra fica em cada controller que faz a ação
  (arrastar, redimensionar, formato, excluir), não no modelo; deixe um método estático testável
  (`CardDragController.movableCards`, `SelectionActionsController.delete`)
- [ ] Propriedade **derivada** com getter público (`is*`/`has*`, como o `isFrame()` da seção): a rede de segurança
  também a vê; o `fullCard()` precisa deixá-la diferente do padrão (por isso ele usa o formato `FRAME`)
- [ ] **`CardAllPropertiesTest.fullCard()`**: preencher a propriedade nova com um valor diferente do padrão
- [ ] Testes: `BoardStorageTest` (salvar/abrir) e `BoardSnapshotTest` (desfazer)

> **Rede de segurança (`CardAllPropertiesTest`):** um card com **todas** as propriedades diferentes do
> padrão passa por `copy`, foto/restauração e salvar/abrir, e o teste compara tudo. Os valores são lidos
> por reflexão dos getters públicos do `Card` (`get*`, `is*`, `has*`), então uma propriedade nova entra
> na comparação sozinha. O primeiro teste falha enquanto você não a preencher em `fullCard()`; os
> demais falham apontando o lugar esquecido (`copy`, `CardState` ou `toData`/`fromData`).
> Propriedades que o `Card` expõe só por outro caminho (sem getter com esses prefixos) não são vistas.
>
> **Propriedade de seta (`Connection`)**, como o `label` e o `style` (3.24): o mesmo checklist, trocando
> `CardState` por `BoardSnapshot.ConnectionState` (+ `applyTo`), `CardData` por `ConnectionData` e
> `CardView` por `ConnectionView`. Não há `copy` de seta (duplicar card não copia setas). A rede de
> segurança é o `ConnectionAllPropertiesTest`: preencha a propriedade em `fullConnection()`.
>
> **Antes de criar uma propriedade, veja se não é um formato.** A seção (3.23) foi feita como o formato
> `CardShape.FRAME`, sem propriedade nova: o checklist inteiro (cópia, foto, arquivo) já estava coberto pelo
> `shape`. Uma propriedade só compensa quando o valor é independente do resto (como o `locked`).

## 5.2 Adicionar um formato (ex.: hexágono)

1. `CardShape`: `HEXAGON(largura, altura)`.
2. `CardGeometry.borderPoint`, `shapeInsets` e `panelButtonCenter`: `case HEXAGON -> ...` (os `switch` são
   exaustivos: **o compilador acusa se esquecer**).
3. `CardMarkers.layout`: onde ficam os marcadores no formato (idem).
4. `Icons.shape`: `case HEXAGON -> "path..."` (idem).
5. `Tool`: `HEXAGON("Hexágono", KeyCode.H, CardShape.HEXAGON, Icons.shape(CardShape.HEXAGON), "dica")`.
6. `app.css`: `.card.shape-hexagon { -fx-shape: "..."; }` e incluir na regra de seleção de formatos não retangulares.
7. `CardResizeController.freeAspect`: se o formato muda largura e altura livremente (senão mantém a proporção).
8. Teste em `CardGeometryTest`; o `switch` do `CardMarkersTest.inside` também precisa do caso novo.

A barra de ferramentas, o seletor de formato da barra flutuante, o atalho e a persistência passam
a funcionar sem mais nada.

**Formato especial (como a seção, `FRAME`, 3.23):** algo que é "um card com outro comportamento" pode ser um
formato. Além dos passos acima, foi preciso: tirá-lo do seletor (`Tool.isPlainShapeTool`), o `CardTextFit`
(fonte fixa, sem crescer), o `CardGeometry.contentInsets` (texto só na faixa do título), as regras no `Board`
(camada, setas) e em cada controller que age sobre cards (arrastar, redimensionar, setas, formato, tamanho
do texto), o `PdfExporter` (texto e links) e o picking no `CardView`. Procure por `isFrame()` para ver todos.

## 5.3 Adicionar uma ferramenta

1. Constante em `Tool` (nome, tecla, formato ou `null`, ícone em `Icons`, dica). Confira a tecla na tabela de
   atalhos (3.16) e no README: letras já usadas são V R E L P B S I C.
2. Comportamento no controller adequado, checando `activeTool.get() == Tool.NOVA`.
3. Cursor/estilo em `app.css` usando `:tool-nova`, se preciso.

**Ferramenta que cria um card de formato próprio** (ex.: `Tool.FRAME`, a Seção): basta dar o formato à
constante; o `CanvasController.createCardAt` cria o card no clique, seleciona e abre o texto, e o
`Board.addCard` o põe na camada certa. O que a ferramenta tem de especial fica nas regras do formato (5.2).

**Variações de uma ferramenta** (como os estilos de seta da Conexão, 3.24): em vez de uma ferramenta por
variação, uma fileira (`HBox`) com o botão da ferramenta e botões extras ao lado (`ToolBarView.createConnectionRow`);
a ordem fica numa classe de lógica pura testável (`ConnectionStyleOrder`) e o valor escolhido sai por uma
propriedade (`connectionStyleProperty`) que o controller lê. Estilo novo: constante em `ConnectionStyle`,
caso em `Icons.connectionStyle` (switch exaustivo) e, se o desenho mudar, na `ConnectionView`.

**Nem toda funcionalidade é uma ferramenta.** Se ela age sobre um card que já existe (ex.: o link
externo, o link para outro board, a ordem de empilhamento e o travar), o lugar é a barra flutuante (5.4)
e/ou o menu de clique direito (abaixo), não a barra lateral.

**Ferramenta que é uma ação** (ex.: `Tool.IMAGE`): em vez de esperar um clique no canvas, o controller
escuta `activeTool` e, quando ela vira a ferramenta, faz a ação e volta para Selecionar — dentro de um
`Platform.runLater`, para não mudar a propriedade no meio da notificação da própria mudança. Botão, tecla
e dica funcionam sem mais nada.

## 5.4 Adicionar uma ação na barra flutuante

1. Botão em `SelectionToolbarView` (use `actionButton(tooltip, iconPath)` e um ícone em `Icons`).
2. Ação em `SelectionActionsController` (ou num controller próprio, como `StackOrderController` e
   `LockController`), operando sobre a seleção dentro de `history.perform`.
3. Atalho em `KeyboardController`, se houver. Atalho com tecla de pontuação (`[`, `]`, `;`...): use
   `KeyCharacterCombination`, não a `KeyCode` (no ABNT2 a `KeyCode` é a da posição no teclado americano, 3.16).

**Várias ações num botão só** (como o botão Ordem): estenda `PopupButton` e sobrescreva `beforeShowPopup` para
habilitar só as opções que mudariam algo agora (`StackOrderButton.setAvailability`).

**Botão de estado com seleção mista** (como o cadeado): pseudo-classes `:selected` (todos) e `:mixed`
(alguns), atualizadas por um controller que observa a propriedade de cada card selecionado (senão o
desfazer não atualiza o botão) — ver `LockController.observeSelection` e `SelectionToolbarView.showLock`.

**Aviso curto sem diálogo** (como "card travado não é excluído"): `MainWindow.showStatusMessage`, que o
`MainController` passa aos controllers como `Consumer<String>` (ver `SelectionActionsController.setNotifier`).

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
imagens nos cards (3.17), o PNG (3.18), o link externo (3.19), o link para outro board (3.20), a ordem de
empilhamento (3.21), o lock de item (3.22), as seções (3.23) e as setas com texto e estilos (3.24):

```
[1] Redimensionar cards ──► [2] Fonte automática/manual + crescimento do card ──► [3] Exportar PDF ──► [4] Notas "post-it" no PDF
```

| Funcionalidade | Onde encaixa | Observações |
|---|---|---|
| **Busca global** | `BoardTree.entries` já lista todos os boards com o caminho; buscar também no texto dos cards e ir ao resultado com `NavigationController.jumpTo` (3.20) | Decidir: só nomes de board ou texto dos cards; destacar o card encontrado |
| **Link para outro arquivo** | Extensão do link para outro board (3.20): guardar a pasta do save além do id | Primeira versão ficou só na mesma árvore |
| ~~Frames / seções coloridas~~ (tarefa 4b) | **Feito** (3.23): formato `FRAME`, camada própria atrás dos cards e das setas, conteúdo por geometria | — |
| **[4] Notas "post-it" no PDF** | `PDAnnotationText` sobre o card do painel, na 2ª passada do `PdfExporter.drawBoardPage` (o `rectOf(card)` já dá a posição) | Expansão opcional: só texto simples, comportamento varia por leitor, não imprime. Pode virar uma opção ao exportar |
| **Múltiplos boards** | `NavigationController.openRoot` já troca o board principal; `EditHistory.clear()` já existe | Decidir: abas, lista lateral ou só "abrir recente" |
| ~~Setas com texto + estilos de seta~~ (tarefa 5) | **Feito** (3.24): `Connection.label`/`style`, seleção de seta, estilos na barra lateral (também na seta selecionada) | Curvas e espessura ficaram para depois: seriam mais um componente na `ConnectionView` e um campo em `ConnectionData` (checklist em 5.1) |
| **Agrupar** | `groupId` no card, ou um modelo `Group`; seleção de um membro seleciona o grupo todo | Seleção múltipla e mover em grupo já existem. **Recomendação (depois da 4b):** manter separado das seções e fazer pequeno. A seção já resolve "juntar por tema e mover junto" pelo espaço; o que ela não resolve é juntar cards **espalhados** ou **sem área visível** (ex.: dois cards em cantos opostos que devem andar juntos). Se esse caso não aparecer no uso, descartar o card. Se aparecer, `groupId` no card (5.1) + `CardDragController.draggedCards` incluindo os do mesmo grupo (o mesmo ponto em que a seção soma o conteúdo). Não reaproveitar a seção para isso: ela é geometria, o grupo seria vínculo salvo |
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
| Ordem de empilhamento = posição em `board.getCards()` | Um número `zIndex` em cada card | A lista já era a ordem de desenho, do arquivo e do PDF; nenhum campo novo no JSON nem no `CardState` (3.21) |
| Clicar não traz para frente | Manter o `toFront` do clique (só na tela) | A ordem agora é dado salvo; o clique mudaria a tela sem mudar o arquivo (armadilha 10 antiga) e jogaria um frame por cima dos cards |
| Reordenar por permutação (`cards.sort`) | `setAll` com a lista nova | O `setAll` avisa "removidos e adicionados": a tela recriaria os `CardView`s (perderia foco, painel aberto, seleção visual) |
| Um nível = uma posição na lista | Pular até o próximo card sobreposto | Previsível e testável (como PowerPoint/Slides); "topo"/"fundo" resolvem o resto (3.21) |
| Botão Ordem com popup de quatro ações | Quatro botões na barra | A barra já é longa e as ações são raras; o popup mostra os atalhos (3.21) |
| Colchetes por caractere (`KeyCharacterCombination`) + `Ctrl+PgUp/PgDn` | Só `KeyCode` (`Ctrl+]`) ou trocar por outro conjunto | No ABNT2 a `KeyCode` dos colchetes é a da posição americana (trocaria as ações); pelo caractere funciona nos dois layouts, e PgUp/PgDn é o plano B (3.16) |
| Duplicar põe as cópias no topo | Logo acima do original | Igual a um card novo; menos regras (3.21) |
| Travado não é excluído; aviso na barra de status | Excluir mesmo assim; diálogo de confirmação | Excluir não protegeria; um diálogo a cada Delete interrompe. O aviso diz como destravar (3.22) |
| Travado: texto, cor, setas, links, ordem livres; formato bloqueado | Bloquear tudo | A trava protege posição e tamanho (o que um clique errado estraga); o formato muda o tamanho (3.22) |
| Cópia de travado nasce destravada; `Card.copy` continua fiel | Cópia travada | A cópia precisa ser posicionada; a regra fica na ação de duplicar, e o `copy` segue a rede de segurança do `CardAllPropertiesTest` |
| Cadeado no canto, só na tela | Marcador do `CardMarkers` (no PDF também) | É estado de edição, não conteúdo; não disputa espaço com o texto nem com os marcadores (3.22) |
| Seção = card com formato `FRAME` | Propriedade `frame` no card; tipo novo de nó | Posição, tamanho, título, cor, link, cadeado, desfazer e arquivo vêm prontos; nenhum campo novo no JSON nem mudança de versão; o `switch` exaustivo aponta o que decidir (3.23) |
| Conteúdo da seção por geometria (centro dentro), calculado no início do arrasto | Vínculo salvo (lista de cards da seção) | Soltar por cima já coloca; nada a manter em sincronia nem a corrigir ao excluir; o centro decide sem ambiguidade quando o card cruza a borda |
| Seções sempre abaixo dos cards, reordenando só entre si | Ordem livre (seção pode cobrir card) | Uma seção por cima esconderia os cards por engano; com duas camadas as quatro ações continuam valendo (3.23) |
| Seção nova no topo das seções | No fundo de tudo | Uma seção pequena criada dentro de uma grande fica visível e pegável; as duas respeitam o invariante |
| Seções numa camada atrás das setas | Na mesma camada dos cards | Seta entre cards de dentro passa por cima do fundo translúcido, não por baixo |
| Meio vazio da seção = fundo do canvas | A seção inteira pega o clique | Caixa de seleção, criar card e clicar entre os cards de dentro continuam naturais; a seção é pega pela borda e pelo título |
| Card travado dentro de uma seção movida vai junto | Fica para trás | A trava protege contra arrastar o card sozinho; a seção é a organização do tema (3.23) |
| Seções não são referência de alinhamento ao arrastar cards | Sempre referência | As bordas de uma seção grande puxariam cards a toda hora e poluiriam as guias; ao mover a seção ela alinha com tudo |
| Duplicar/excluir a seção age só nela | Levar o conteúdo junto | O conteúdo não é dela (geometria); excluir sem querer não apaga os cards |
| Link da seção no PDF só no marcador | Área inteira clicável | A área cobriria os cards de dentro e competiria com os links deles |
| Título da seção: rótulo de uma linha com reticências; campo só na edição | `TextArea` sempre visível | Título longo fica legível sem rolagem nem crescer; a faixa do título pega o clique para arrastar |
| Seleção de seta mínima (uma seta; exclusiva com a dos cards) | Seta na mesma seleção dos cards; várias setas | É o menor conjunto que torna a seta editável (duplo clique, Delete, estilo); a barra flutuante e as ações dos cards não precisam saber de setas (3.24) |
| Área de clique larga e transparente só na ferramenta Selecionar | Clique na linha de 2 px; área sempre ativa | Fácil de acertar sem roubar os cliques de criar card ou de mover a visão nas outras ferramentas |
| Texto da seta: uma linha, 60 caracteres, até 220 px com "…" | Quebrar em duas linhas; crescer à vontade | Mais simples; nunca empurra cards nem cobre a seta inteira (3.24) |
| Confirmar o texto da seta com `history.perform` | Sessão de edição (`beginEdit`/`endEdit`) | O campo abre e fecha sob controle do controller; `Esc` cancela sem passo, e cada confirmação é um passo |
| Textos das setas numa camada própria acima das linhas | Texto dentro da `ConnectionView` | A linha de outra seta (ex.: a contrária) desenhada depois cortava o texto (3.24) |
| A→B e B→A: textos lado a lado e área de clique de cada lado da linha | Curvar as duas setas | Sem mudar o desenho das setas (curva ficou fora do escopo), as duas continuam legíveis e clicáveis |
| Estilos ao lado do ícone Conexão, trocando de lugar com o principal | Seletor na barra flutuante; ferramenta por estilo | Decisão do Danilo: na barra lateral, com o ícone mostrando o estilo das setas novas (3.24) |
| Com seta selecionada, os botões de estilo mudam a seta | Estilo só para setas novas | Aprovado pelo Danilo; usa a mesma barra, sem botão novo |
| Estilo principal não é lembrado entre sessões | Preferência do usuário salva | Decisão do Danilo |
| Geometria da seta em `model/ConnectionGeometry` (Java puro) | Conta só na `view` | O PDF (texto invisível) usa a mesma conta sem depender da tela |

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
10. **A ordem de empilhamento é a ordem da lista `board.getCards()`** (3.21), e a tela precisa acompanhá-la.
    Nunca mude a ordem só na tela (`cardView.toFront()` direto, como o clique fazia antes da 3.21): o
    arquivo, o desfazer, o PDF e o PNG não veriam. Mude a lista (`Board.reorder`) e o `BoardView` reposiciona
    os nós. E mude a lista por **permutação** (`cards.sort`), não com `setAll`/remover+adicionar: senão a tela
    recria os `CardView`s (perde foco, painel aberto e seleção visual), e quem escuta a lista acha que cards
    saíram e entraram.
11. **Setas A→B e B→A** ficam sobrepostas (parecem uma seta de duas pontas, e com a ponta dupla da 3.24
    nem dá para diferenciar). Para continuarem usáveis (3.24): os textos ficam lado a lado (cada um anda para
    o lado do próprio destino) e a área de clique de cada uma fica do seu lado da linha (acima pega uma,
    abaixo a outra). Um texto muito longo numa seta curta pode passar por cima de um card.
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
34. **`KeyCode` de pontuação depende da posição, não do caractere.** No ABNT2 a tecla `[` chega como
    `KeyCode.CLOSE_BRACKET` e a `]` como `BACK_SLASH` (3.16). Atalho com pontuação deve usar
    `KeyCharacterCombination`. Letras e PgUp/PgDn não têm esse problema.
35. **Card travado continua mudando pelo desfazer e pelo texto.** A trava é regra de interface (arrastar,
    alças, formato, excluir), não do modelo: `Ctrl+Z` pode mover um card travado de volta (é desfazer), e o
    texto pode fazê-lo crescer (3.14). Um gesto novo que mova ou exclua cards precisa conferir `isLocked()`
    no seu controller (não há proteção central).
36. **O cadeado fica dentro do `CardView`.** Um card por cima, na ordem de empilhamento, cobre o cadeado do
    card de baixo, como cobre o resto dele. E o cadeado sai 6 px para fora da caixa do card: os
    `boundsInParent` do `CardView` travado são um pouco maiores que o card (a conta de posição de tudo usa o
    modelo, não esses limites).
37. **`ObservableList` só-leitura não compara com `List.of`.** `assertEquals(List.of(a, b), board.getCards())`
    falha mesmo com os mesmos cards (o `equals` da lista não modificável do JavaFX não é o de `List`); compare
    com `List.copyOf(board.getCards())`. E o desfazer de uma exclusão recria o card (mesmo id, outro objeto):
    compare pelos ids.
38. **Fundo transparente ainda pega clique.** Um `Region` com `pickOnBounds = false` aceita clique em qualquer
    `BackgroundFill`, mesmo `transparent` (a cor não importa). Para um nó que deve pegar clique só na borda
    (a seção), o fundo tem que ser `null` e o desenho do fundo vai para outro nó `mouseTransparent` (4.4).
39. **Estilos de estado sobrescrevem a borda da seção.** Regras como `.card-view:selected .card` têm mais
    especificidade que `.card.shape-frame`. Uma regra nova de borda/fundo para cards precisa considerar a
    seção (por isso o anel que pega o clique fica no `frameGrip`, não na borda do `body`).
40. **Card que vira seção (ou deixa de ser) não muda de camada.** O `BoardView` escolhe a camada do
    `CardView` ao criá-lo. Nenhum gesto troca o formato de/para `FRAME`; se um dia trocar, recrie o `CardView`.
41. **A barra flutuante cobre o título de uma seção logo acima do card selecionado.** É o comportamento
    normal da barra (fica acima da seleção); clicar fora ou no outro lado do título resolve. Um roteiro de
    interface com picking real precisa escolher o ponto (o do 4b clica no título à direita).
42. **O texto da seta não é filho da `ConnectionView`.** Ele fica em `getLabelLayer()`, que o `BoardView` e o
    `BoardRenderer` põem na camada dos textos. Quem mostrar uma `ConnectionView` em outro lugar (teste, outra
    exportação) precisa pôr também esse grupo na cena; senão o texto não aparece (nem é medido). O destaque
    de seleção do texto usa `.connection-label-group:selected`, não `.connection:selected`.
43. **Hover não sai em evento sintético.** Os botões de estilo aparecem com o mouse sobre a Conexão
    (`hoverProperty`), que o JavaFX só atualiza com o mouse de verdade; disparar `MOUSE_ENTERED` num nó não
    muda o hover. O roteiro de interface (C) não consegue conferir esse caminho.
44. **Pick da seta no meio de outra.** O texto de uma seta fica por cima da área de clique das outras (camada
    acima): um duplo clique sobre o texto de A→B, mesmo que caia sobre a linha de B→A, edita A→B.

## C. Testes

### Automatizados (`mvn test`) — 362 testes, sem abrir janela

(O `BoardRendererPngTest`, o `FrameViewTest`, o `ConnectionViewTest` e o `ConnectionEditControllerTest`
ligam o JavaFX, sem janela, para desenhar de verdade; num ambiente sem tela eles são pulados em vez de falhar.)

| Classe | Cobre |
|---|---|
| `BoardTest` | regras de conexão, remoção em cascata, cópia de card |
| `ConnectionTest` | seta nova sem texto e simples; texto de uma linha, nunca nulo; pontas e tracejado de cada estilo; estilo pelo nome (desconhecido = simples); `connect` com estilo mantém as regras (repetida em outro estilo, B→A, para si, seção, fora do board); excluir seta mantém os cards; contrária nos dois sentidos; duplicar card não copia setas; desfazer exclusão de card traz a seta com texto e estilo |
| `ConnectionAllPropertiesTest` | seta com todas as propriedades fora do padrão (`fullConnection()`, falha se esquecer uma nova): foto/restauração em board novo e na seta existente (atualizada no lugar), texto e estilo mudam a foto, `deepCopy`, salvar/abrir |
| `ConnectionGeometryTest` | seta de borda a borda; texto no meio; textos de A→B e B→A lado a lado (horizontal e vertical) |
| `ConnectionStorageTest` | os quatro estilos e textos salvos e abertos (também em board aninhado); padrão não grava `label`/`style` e mantém a versão 2; fora do padrão grava; save anterior sem os campos e `.json` antigo abrem com seta simples sem texto; quebra de linha vira espaço, estilo desconhecido vira simples |
| `ConnectionStyleOrderTest` | começa com a seta simples no principal; escolher um extra troca de lugar; escolher o principal não muda nada; sempre os mesmos quatro estilos |
| `ConnectionViewTest` | (JavaFX, CSS do app) pontas e início/fim da linha de cada estilo, tracejado no CSS, ponta da origem girada; texto no meio acompanhando mover/redimensionar; texto longo limitado sem mudar os cards; A→B e B→A com textos lado a lado e áreas de clique em lados opostos; seleção de seta exclusiva com a de cards, limpa ao excluir; PNG com o texto e o tracejado |
| `ConnectionEditControllerTest` | (JavaFX) Enter confirma num passo do desfazer (sem espaços nas pontas), Esc cancela sem passo, vazio tira o texto, limite de 60 no campo, Delete exclui a seta selecionada e o desfazer a traz com texto e estilo, estilo da seta selecionada pela barra num passo (o mesmo estilo não é passo; sem seta, não age) |
| `CardShapeTest` | criação por formato, troca de formato mantendo o centro/tamanho |
| `CardGeometryTest` | ponto de contato da seta em cada formato |
| `AlignmentGuidesTest` | grudar borda/centro, candidato mais próximo, limite, guias atravessando várias caixas, redimensionar (só bordas puxadas) |
| `BoxResizerTest` | cada tipo de alça, lado oposto parado, tamanho mínimo, proporção, grude só nas bordas puxadas, Alt (limite 0) |
| `BoardSnapshotTest` | foto/restauração: mudança de propriedades, fonte e tamanho definido à mão, card excluído com setas, card novo |
| `CardTextFitTest` | seção: título com fonte fixa nunca faz crescer (nem com fonte manual ou link), texto só na faixa do título e antes dos marcadores; espaço do clipe reservado em cima (fonte menor ou card maior), losango sem mudança; fonte máxima/menor/mínima, palavra nunca quebrada no meio, retângulo cresce só na altura, elipse mantém proporção, volta ao tamanho à mão, fonte manual, card vazio, espaço do botão; card de imagem: sem legenda não cresce, legenda cresce embaixo sem distorcer a imagem, fonte da legenda, imagem maior (com medidor falso, sem JavaFX) |
| `PanelCardTest` | modos do painel, conteúdo apagado na troca, cópia independente, foto/restauração do painel |
| `NestedBoardTest` | restauração não mexe em filhos existentes, exclusão+desfazer traz a árvore, cópia profunda |
| `EditHistoryTest` | desfazer/refazer, ação sem mudança, gesto longo, sessões de edição, histórico separado por board |
| `PdfExporterTest` | texto da seta buscável, no meio da seta; seção: texto invisível só na faixa do título; título buscável, link da seção só no marcador, cards de dentro com os links inteiros (card com site e card-board), nenhuma área clicável do tamanho da seção; link para outro board: o card todo leva à página do destino em outro ramo; card que já leva a uma página vai ao destino só pelo marcador (por cima); card com os dois links e sem página própria (card todo → board, clipe → site); destino excluído sem área clicável; marcadores lado a lado; link externo: card todo com ação URI (endereço ASCII), card de board aninhado mantém o link da página e ganha o do site só no clipe (por cima), área do clipe acompanha a escala, cards sem link sem URI; ordem das páginas, links dos cards e do caminho, "voltar ao board", marcadores, texto buscável, continuação de detalhes longos, caracteres sem suporte, arquivo gravado sem sobra de temporário, texto invisível de card de imagem só na faixa da legenda |
| `BoardStorageTest` | link salvo e aberto (card comum, de imagem e de board), card sem link não grava o campo, save anterior sem o campo abre sem link, links maliciosos no arquivo (`javascript:`, `file:`, caminho, rede, `usuário@site`) descartados; salvar/abrir em pasta (painéis, boards aninhados em 3 níveis, fonte e tamanho definido à mão), abrir pela pasta ou pelo `board.json`, `.json` antigo (abre, salva no formato novo sem alterar o antigo, versão decide e não o nome), JSON inválido, pasta incompleta, `board.json` vazio, versão futura, `version` 2 com outro nome, "salvar como" copiando `imagens/`, imagens copiadas antes do JSON (falha na gravação não deixa JSON solto), falha mantém o `board.json` anterior, destino livre × save existente × ocupado; card de imagem salvo e aberto com o arquivo, card sem imagem não grava o campo, save anterior sem o campo abre, imagem faltando (e nome com `../`) não quebra e mantém a referência, imagens de várias origens |
| `BoardImagesTest` | limpeza só dos arquivos não referenciados (com arquivos falsos), conjunto vazio, nome citado que não existe, sem pasta `imagens/`, subpastas e resto do save intactos; cópia sem sobras temporárias, cópia desatualizada substituída, idêntica mantida, nada criado sem imagens, origem = destino não muda nada; achar o arquivo na ordem das pastas, nomes com pasta recusados |
| `ImageImporterTest` | (imagens reais geradas no teste) PNG e JPG pequenos copiados byte a byte com nome novo, grande reduzido a 2000 px mantendo a proporção (JPG continua JPG, PNG mantém transparência), GIF e BMP viram PNG, formato pelo conteúdo e não pela extensão, arquivo quebrado/não imagem/inexistente recusado sem sobras, imagem em memória vira PNG, extensões aceitas |
| `ImageCardTest` | card de imagem nasce retângulo do tamanho da imagem, altura da imagem pela proporção, nome em branco = sem imagem, duplicar reutiliza o arquivo, desfazer/refazer a inserção, imagens da árvore inteira e das fotos |
| `ImageCardControllerTest` | limpeza ao salvar mantém imagens de boards aninhados e do histórico e remove só a órfã; histórico zerado libera a imagem; tamanho inicial (limite, natural, ampliada); redimensionar mantém a proporção em todos os cantos, canto oposto parado, mínimo pelo maior lado |
| `PngExporterTest` | área com margem e 2 px por unidade, redução pelo lado maior (8.000 px exatos, sem 1 px a mais) e pelo total de pixels, arredondamento, nome sugerido (principal, caminho do aninhado, caracteres proibidos, ponto/espaço no fim, nome reservado, vazio, comprimento), `.png` acrescentado só se faltar, PNG gravado no tamanho previsto e sem `.tmp`, substitui arquivo existente, board vazio recusado sem gravar |
| `BoardRendererPngTest` | (JavaFX de verdade, CSS do app) a exportação segue a ordem de empilhamento (na sobreposição aparece o card de cima, antes e depois de "trazer para o topo"); o cadeado do card travado não sai na imagem (PNG idêntico ao do destravado); clipe desenhado no card com link e ausente no sem link; PNG de board com formatos, cor, seta e card de imagem: tamanho previsto, fundo branco, cor do card, seta entre os cards, imagem do card no lugar certo; a mesma exportação com fundo transparente (canto transparente, card opaco); board de 30.000 unidades reduzido a 8.000 px com os dois extremos desenhados |
| `WebLinkTest` | aceita `http`/`https`, acrescenta `https://` (inclusive `site:porta` e `//site`), apara e põe o esquema em minúsculas, vazio = sem link; recusa outros esquemas, caminhos e executáveis, espaços e caracteres de controle, usuário antes do site, site sem domínio, porta e `%` inválidos, endereço longo; forma ASCII (site com acento); `Card.setLink` nunca guarda link perigoso |
| `BoardTreeTest` | caminho de um board por id em vários níveis e em ramos diferentes, caminho da raiz, id inexistente (e raiz/id nulos), excluir um card tira a subárvore da busca, renomear mantém o id e muda o caminho, lista da árvore na ordem do PDF com profundidade e caminho, ids da árvore; `deepCopy` dá ids novos a todos os boards dentro; duplicar um card-board mantém ids únicos e o link copiado aponta para o original; duplicar copia o link; texto em branco = sem link |
| `NavigationPathTest` | entrar/voltar como antes; pular monta o caminho real do destino; voltar do destino volta à origem (com a visão dela) e depois ao pai; pulo para o principal ainda tem voltar; filho aberto a partir do destino volta ao destino e depois à origem; clicar num nível acima do destino esquece a origem; pulos encadeados; visão mantida nos níveis em comum; pulo para o próprio board e para board excluído não mudam nada; origem excluída → voltar ao pai; origem excluída e trazida pelo desfazer → pilha refeita pelo id; abrir outro board zera tudo |
| `BoardLinkControllerTest` | adicionar, trocar e remover são passos de desfazer/refazer; o mesmo destino de novo não é passo; excluir o destino mantém o link (quebrado) e o desfazer o traz de volta; excluir o card com o link e desfazer; duplicar copia o link; duplicar o destino mantém o link no original |
| `BoardLinkStorageTest` | link para board de outro ramo salvo e aberto (caminho conferido), card sem link não grava o campo, save anterior sem o campo abre sem link, link para board inexistente mantido, ids de board repetidos (saves antigos) renovados ao abrir |
| `CardMarkersTest` | seta do link para outro board sozinha e depois do clipe (mesma fileira); sem link, sem marcador; retângulo: canto de cima à direita, fora das alças; elipse e losango: no meio e dentro da silhueta; losango estreito desce o marcador; seção: na faixa do título, à direita, sem empurrar o título; vários em fila sem sobrepor; texto abaixo; card minúsculo |
| `LinkControllerTest` | adicionar, editar e remover são passos de desfazer/refazer; vazio remove; endereço inválido devolve mensagem e não muda nada; o mesmo link de novo não é passo; duplicar copia o link (painel, imagem); excluir e desfazer traz o link |
| `CardAllPropertiesTest` | card com todas as propriedades diferentes do padrão (inclusive `imageFile`, `link`, `boardLink`, `locked` e o formato `FRAME`, que deixa `isFrame()` fora do padrão): preenchimento do `fullCard()` (falha se esquecer uma propriedade nova), `copy`, foto/restauração (card novo e existente) e salvar/abrir preservam tudo (5.1) |
| `StackOrderTest` | `Board.reorder`: um nível para cima e para baixo, topo, fundo, limites (topo/fundo/vazio/card de outro board não mudam nada), vários selecionados mantêm a ordem relativa, bloco junto sobe junto, card no topo fica e o outro selecionado sobe, aviso de permutação (não remoção), foto vê a ordem, `restore` devolve a ordem com os mesmos objetos, desfazer exclusão volta ao lugar na pilha, restaurar num board vazio, `deepCopy` mantém a ordem dos filhos |
| `StackOrderControllerTest` | cada movimento é um passo de desfazer/refazer; sem mudança, sem passo; duplicar põe as cópias no topo na ordem dos originais; excluir e desfazer mantém a ordem; mover não mexe na ordem; salvar/abrir mantém a ordem (inclusive num board aninhado) |
| `LockControllerTest` | travar/destravar são passos de desfazer; o mesmo estado não é passo; botão/Ctrl+L trava todos (nenhum ou alguns travados) ou destrava (todos); foto vê o travado; arrasto em grupo move só os destravados; excluir mantém os travados (e o desfazer devolve os outros); só travados: nada excluído, sem passo; formato não muda no travado e a cor muda; cópia de travado nasce destravada; travado muda de ordem |
| `LockStorageTest` | travado salvo e aberto (também em board aninhado), destravado não grava o campo, travado grava `"locked": true` sem mudar a versão, save anterior sem o campo abre destravado (e na ordem do arquivo) |
| `FramesTest` | seção: card dentro pelo centro (metade para fora conta, maior parte em cima não), bordas incluídas, card maior que a seção, seção não está dentro dela mesma, conteúdo na ordem da lista; o que um arrasto leva: card em duas seções uma vez só, seções movidas não entram, seção dentro de seção leva o conteúdo dela, cards comuns não levam nada, travado dentro vai; caixa de seleção (card por toque, seção só inteira); camadas |
| `FrameLayersTest` | seção nova no topo das seções e abaixo dos cards; tamanho padrão; "trazer para o topo" para abaixo dos cards; card "para o fundo" para acima das seções; um nível entre seções; seleção mista (cada grupo na sua camada); desfazer devolve camadas e ordem; desfazer exclusão de seção; foto com seção por cima de card é corrigida; seção sem setas; excluir seção mantém os cards |
| `FrameControllerTest` | arrastar a seção leva só o que está dentro; seção + card de dentro selecionados: anda uma vez; card em duas seções arrastadas: uma vez; seção travada não move nem leva; travado dentro acompanha (sozinho não); card solto em cima passa a pertencer; conteúdo pego no início; o arrasto é um passo (desfazer/refazer); referências de alinhamento (seções só quando é seção); redimensionar não mexe no conteúdo; duplicar copia só a seção (destravada, no topo das seções); excluir só a seção (desfazer volta atrás dos cards); seção travada não é excluída; formato nunca muda a seção; ordem com o invariante e desfazer |
| `FrameStorageTest` | seção salva e aberta (`"FRAME"`, título, cor, tamanho, travado, link), sem campo novo e com a versão 2; arquivo com seção depois de um card abre com ela atrás, seta para seção ignorada; save sem seções abre igual |
| `FrameViewTest` | (JavaFX de verdade, CSS do app) só a borda e o título pegam o clique, o meio vazio não (corpo sem fundo); card comum continua pegando no meio; seção sem título mostra "Seção", título longo numa linha sem crescer; PNG: seção atrás do card e translúcida; seta desenhada por cima da seção |

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
- **Ordem de empilhamento e lock de item** foram validados por um roteiro do mesmo tipo, **dentro da JVM, sem
  mouse e teclado do sistema** (115 verificações), rodado com o app no classpath e no **module path** (como o
  `mvn javafx:run` monta). Ordem: clicar num card não muda a ordem (nem a salva, nem a da tela, nem cria
  passo) → menu de clique direito (as quatro ações depois dos links; "para trás"/"fundo" desabilitados no
  fundo) → "Trazer para frente" (tela acompanha, **nenhum `CardView` recriado**, seleção mantida) → botão
  Ordem da barra (popup, opções desabilitadas no topo, topo e fundo) → `Ctrl+PgUp/PgDn` com e sem Shift
  (sem mudança, sem passo) → colchetes com as `KeyCode`s que o glass dá às teclas do ABNT2 (`BACK_SLASH` =
  tecla `]`, `CLOSE_BRACKET` = tecla `[`; `OPEN_BRACKET` = tecla `´` não faz nada) → vários selecionados
  (ordem relativa) → `Ctrl+Z`/`Ctrl+Y` (ordem e nós preservados) → atalhos ignorados durante a edição de
  texto → painel aberto e seleção preservados ao mudar a ordem → `Ctrl+D` (cópia no topo) → salvar
  (`board.json` na ordem), PNG e PDF (na sobreposição aparece o card de cima; no PDF, conferido na imagem da
  página) → reabrir (mesma ordem na tela). Lock: botão de cadeado (cadeado no card, `:locked`, cursor
  normal, sem alças, botão marcado, formato desabilitado, cadeado fora da barra flutuante) → arrastar o
  travado (não move, sem passo; `Ctrl+Z` desfaz o travar e as alças voltam) → arrasto em grupo pelo travado
  e pelo outro (só o destravado se move; botão "misto") → `Delete` e botão Excluir (o travado fica, com
  aviso na barra de status; sem passo quando só há travados) → cor, texto (clicando no texto), seta pela
  ferramenta Conexão e ordem no travado → clipe do link no travado (abre o link; cadeado e clipe não se
  sobrepõem) → elipse com board aninhado travada (entra no board) → menu Travar/Destravar e `Ctrl+L` (inclusive
  ignorado durante a edição) → `Ctrl+D` no travado (cópia destravada, que arrasta) → salvar (`locked` só nos
  travados), PNG sem o cadeado, reabrir (continua travado e sem mover). **Não validado:** a tecla física dos
  colchetes num teclado ABNT2 de verdade (o roteiro usa as `KeyCode`s que o JavaFX informa para essas teclas
  neste layout; ver 3.16), o clique físico do mouse, os seletores nativos de arquivo (código não alterado) e
  o cursor do mouse visto na tela (conferido pela propriedade `cursor` do nó).

- **Seções (frames)** foram validadas por um roteiro do mesmo tipo, **dentro da JVM, sem mouse e teclado do
  sistema** (100 verificações, no classpath e no **module path**). Diferença em relação aos anteriores: o
  alvo de cada evento de mouse vem do **picking real** da cena (`Scene.pick`, por reflexão), não de um nó
  escolhido pelo roteiro; assim o clique no meio vazio da seção chega mesmo ao fundo do canvas, como chegaria
  com o mouse. Roteiro: tecla `S` (botão Seção marcado) → clique no fundo cria a seção, já editando o título →
  digitar e `Enter` → ferramenta Retângulo e clique no **meio** da seção (o picking dá o fundo) cria o card
  dentro → card de fora arrastado e solto por cima da seção → clique no título e na borda selecionam (sem
  formato nem tamanho do texto, com cor, 8 alças, sem alça de conexão); clique no meio vazio limpa a seleção;
  caixa de seleção começando no meio pega o card e não a seção; card de dentro clicável e arrastável sozinho →
  cor Azul pela barra (fundo translúcido conferido no pixel da tela) → alça da direita (largura 600, cards
  parados; desfazer) e de baixo (mínimo 80) → arrastar pelo título leva A e C, deixa B, conteúdo fora da
  seleção, um passo; desfazer → seção + card de dentro selecionados (anda uma vez) → seção gruda no topo de B
  (guia) → card travado dentro acompanha a seção; sozinho não se move → seção travada (cadeado, sem alças) não
  se move nem leva → duplo clique no título, texto longo, `Enter` (uma linha, sem crescer) → ferramenta
  Conexão: a seção não começa seta, soltar no título não liga, A→B liga → segunda seção (`S`): topo das
  seções; clique direito no título (menu com a ordem, "trazer" desabilitado), "Enviar para o fundo",
  `Ctrl+Shift+PgUp` (topo das seções, ainda atrás dos cards), card "para o fundo" fica acima das seções;
  camadas da tela iguais ao modelo (seções atrás das setas) → `Ctrl+D` (só a seção), `Delete` (só a seção,
  cards ficam), `Ctrl+Z` (volta ao lugar) → salvar (`FRAME`, versão 2), PNG (fundo da seção azul claro, card de
  dentro por cima), PDF (títulos buscáveis) → reabrir (seções no fundo, título na tela, arrastar ainda leva o
  card de dentro). **Não validado:** o clique e as teclas físicas (o caminho do evento dentro do JavaFX é o
  mesmo, e o alvo agora vem do picking real), os seletores nativos de arquivo (código não alterado) e o
  `mvn javafx:run` aberto à mão (o roteiro rodou no module path equivalente).
- **Setas com texto e estilos de seta** (3.24) foram validadas pelo mesmo tipo de roteiro (dentro da JVM,
  picking real, **141 verificações**, no classpath; janela de 1400×860). Roteiro: seis cards pela tecla `R` →
  barra: ícone da Conexão desenha a seta simples, extras escondidos em Selecionar, tecla `C` mostra os extras
  (ordem e ícone de cada um conferidos) → setas pela **ferramenta** nos quatro estilos, escolhendo cada estilo
  pelo botão (troca de lugar, ícone principal redesenhado, ferramenta continua Conexão) → setas pela **alça**
  nos quatro estilos (`Esc`, escolher, voltar); B→A contrária; B→A repetida recusada → clique na seta seleciona
  (cards fora da seleção, sem barra flutuante, destaque); clicar num card, `Esc` ou no fundo limpa; A→B e B→A:
  acima da linha pega uma, abaixo a outra; dentro do card, sobre o fim da seta, o clique é do card; na
  ferramenta Retângulo, sobre a seta é fundo; caixa de seleção cruzando setas → `Delete` exclui a seta (um
  passo), `Ctrl+Z`/`Ctrl+Y` → seta selecionada + botões de estilo (aparecem em Selecionar): extra muda a seta
  (e vira o principal, ferramenta continua Selecionar, um passo), ícone principal aplica o estilo dele sem
  ativar a ferramenta, `Ctrl+Z`; sem seta, o ícone ativa Conexão → duplo clique na linha abre o campo com
  foco; letras e `R`/`C`/`V` não trocam de ferramenta; Backspace apaga letra, não a seta; `Enter` confirma
  (um passo); texto no meio, afastado do da contrária; `Esc` cancela (sem passo); clicar fora confirma; duplo
  clique no texto abre com o texto atual; `Ctrl+Z`/`Ctrl+Y` do texto; texto longo limitado (60 caracteres,
  220 px), cards do mesmo tamanho → mover e redimensionar cards: textos acompanham, seta de borda a borda →
  `Ctrl+D` não copia setas → salvar (`style`, `label`, versão 2), PNG (texto aparece), PDF (textos buscáveis)
  → reabrir (estilos, textos e contrária na tela). **Não validado:** os botões de estilo aparecendo **com o
  mouse em cima** da Conexão (o hover não sai de evento sintético, armadilha 43), clique e teclas físicas, e o
  `mvn javafx:run` aberto à mão.

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
| **Ordem de empilhamento (z-order)** | Quem fica por cima de quem quando cards se sobrepõem; aqui, a posição na lista de cards (3.21). |
| **Permutação** | Mudança de uma lista observável em que só a ordem muda (nada entra nem sai). |
| **Card travado (lock)** | Card protegido contra mover, redimensionar, trocar o formato e excluir (3.22). |
| **Seção (frame)** | Área colorida com título, sempre atrás dos cards; arrastá-la leva o que tem o centro dentro dela (3.23). |

## E. Comandos úteis

```bash
mvn javafx:run          # rodar o app
mvn test                # rodar os testes
mvn clean test          # recompilar tudo do zero e testar (com JDK 17: ver armadilha 19)
git log --oneline       # histórico de commits
```

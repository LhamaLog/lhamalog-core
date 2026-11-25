# Feature Specification: Consultar ocorrências (lista, filtros e detalhe)

**Feature Branch**: `LLT-135-lista-e-detalhe`

**Created**: 19/11/2026

**Status**: Draft

**Input**: User description: "Feature LLT-135 (Lista e detalhe, épico LLT-104 Ocorrências): LLT-203 listar ocorrências com paginação, ordenada pela última ocorrência, com tipo, título, produto, contagem, status e última vez, estado vazio e erro tratados; LLT-204 filtrar por data, tipo, status, produto e texto, com filtros combinados mantidos na paginação, visão multiproduto respeitando os produtos permitidos e limpar filtros voltando à lista padrão; LLT-205 ver o detalhe com exemplos de log mascarados, atributos OTel, contagem, primeira e última vez e histórico de status, com 404 para ocorrência de outro tenant ou produto sem acesso. Decisões do Felipe: o Core só lê a coleção de ocorrências do MongoDB compartilhado, que é gravada pelo Módulo Ocorrência (ADR_062), e esta spec publica o formato esperado como contrato para o Módulo; no perfil local há uma carga de exemplo para a tela funcionar já; status da ADR_082 (ABERTA, EM_ANALISE, FECHADA), com recorrente como atributo."

## Clarifications

### Session 2026-11-19

- Q: De onde o Core obtém as ocorrências, se o Módulo Ocorrência ainda não existe? → A: O Core só lê a coleção de ocorrências do banco compartilhado (DN-009-1 A do finder). Esta spec descreve o formato que o Core espera, e esse formato vira contrato para o Módulo. No perfil local, uma carga de exemplo preenche a coleção.
- Q: Quais status valem na lista e no filtro? → A: Os da ADR_082: ABERTA, EM_ANALISE e FECHADA. "Recorrente" é atributo, não status (ADR_063). Rótulos como "Resolvida" ou "Não resolvida" ficam para a LLT-206.
- Q: Quais repositórios e qual chave do Jira? → A: Core e web, uma spec em cada um, na branch `LLT-135-lista-e-detalhe`. A spec do web é a `008-lista-detalhe-ocorrencias`.
- Q: O filtro de período compara qual data? → A: Entram as ocorrências que aconteceram no período: primeira vez até o fim e última vez a partir do início.
- Q: Ocorrências de produto arquivado aparecem na lista padrão? → A: Sim. O produto arquivado continua consultável (002), e as ocorrências dele seguem visíveis para quem tem acesso.
- Q: Quais tamanhos de página? → A: 10, 20 e 50, com padrão 20.
- Q: O período aceita datas livres? → A: A API aceita início e fim livres; a tela usa só períodos prontos nesta entrega (spec 008 do web).

### Session 2026-11-22 (contrato do Finder aprovado)

O João respondeu às 19 perguntas e o contrato DN-009-2 / N3 A foi aprovado em 20/11 (`lhamalog-finder/specs/009-publicar-evento-ocorrencia/contracts/proposta-dn-009-2.openapi.yaml`). O Felipe cuida do Módulo Ocorrência. Ajustes nesta spec:

- Q: De onde vem o título? → A: O achado traz `titulo` e `resumo`, que podem vir `null`; não existe campo de mensagem normalizada. O título mostrado é `titulo`; sem ele, a mensagem normalizada tirada da assinatura (segunda parte de `nível|mensagem|serviço|host`). A mensagem do detalhe é o `resumo`, que pode faltar.
- Q: Os exemplos de log têm data? → A: Não. `linhas` é uma lista de trechos mascarados, sem data e sem garantia de tamanho. O Módulo guarda os 20 mais recentes; o detalhe mostra os 5 mais recentes, cada um cortado em 512 caracteres pelo Core.
- Q: Quais atributos aparecem? → A: Os que o contrato traz: `hostName` (sempre), `traceId`, `correlationId`, `moduloId`, `numeroLinha` e os pares de `camposExtraido`. O nome do serviço vem da assinatura (terceira parte).
- Q: A contagem e as datas são confiáveis? → A: Sim. O Finder manda os totais já somados e as datas em UTC; o Módulo grava sem somar de novo.
- Q: INFO vira ocorrência? → A: Sim (T7 A). Mudar só a `versaoRegras` mantém a mesma ocorrência. Reabertura FECHADA → ABERTA é do Módulo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Listar ocorrências com paginação (Priority: P1)

Um membro da empresa ativa consulta as ocorrências dos produtos a que tem acesso. A lista vem paginada, da ocorrência que aconteceu por último para a mais antiga. Cada item traz tipo, título, produto, contagem, status, se é recorrente, e primeira e última vez. A resposta informa o total de itens para a barra de paginação.

**Why this priority**: é a base da tela de Ocorrências (LLT-203). Filtro e detalhe partem dela.

**Independent Test**: com a carga de exemplo no perfil local, consultar a primeira página, conferir a ordem pela última vez e o total, e pedir a página seguinte.

**Acceptance Scenarios**:

1. **Given** um dono ou administrador e ocorrências em vários produtos da empresa, **When** ele consulta a lista sem filtros, **Then** recebe as ocorrências de todos os produtos da empresa, ordenadas da última vez mais recente para a mais antiga, com o total.
2. **Given** um membro com acesso a só um produto, **When** ele consulta a lista, **Then** recebe só as ocorrências desse produto, e o total conta só elas.
3. **Given** mais ocorrências do que cabem numa página, **When** a página 2 é pedida com o mesmo tamanho, **Then** ela traz os itens seguintes, sem repetir nem pular itens.
4. **Given** um tamanho de página entre os permitidos (10, 20 ou 50), **When** ele é pedido, **Then** a página tem até esse número de itens; sem tamanho, vale 20.
5. **Given** uma página além da última, **When** ela é pedida, **Then** a resposta traz a lista vazia e o total real.
6. **Given** uma empresa sem ocorrências, ou um membro sem acesso a nenhum produto, **When** a lista é consultada, **Then** a resposta é a lista vazia com total zero, não um erro.
7. **Given** um tamanho de página fora dos permitidos ou uma página menor que 1, **When** a lista é pedida, **Then** a resposta é 400, indicando o parâmetro inválido.

---

### User Story 2 - Filtrar e buscar ocorrências, inclusive entre produtos (Priority: P1)

O usuário restringe a lista por período, tipo, status, produto e texto. Os filtros se combinam e continuam valendo em todas as páginas. Sem nenhum produto escolhido, a lista cobre todos os produtos que ele pode ver (visão multiproduto). Sem filtros, a lista volta ao padrão da história 1.

**Why this priority**: critério de aceite da LLT-204 e dos casos USID_002, USID_005 e USID_037.

**Independent Test**: combinar tipo ERROR, status ABERTA e um produto, conferir que todos os itens e o total respeitam os três filtros, e repetir na página 2.

**Acceptance Scenarios**:

1. **Given** ocorrências de vários tipos e status, **When** o usuário filtra por um ou mais tipos e um ou mais status, **Then** só vêm ocorrências com um dos tipos e um dos status escolhidos.
2. **Given** um período com início e fim, **When** ele é aplicado, **Then** só vêm ocorrências que aconteceram dentro dele (primeira vez até o fim e última vez a partir do início).
3. **Given** um ou mais produtos que o usuário pode ver, **When** ele filtra por eles, **Then** só vêm ocorrências desses produtos.
4. **Given** um produto de outra empresa ou sem acesso no filtro, **When** a lista é pedida, **Then** a resposta é 404, como na consulta de produto, sem revelar se o produto existe.
5. **Given** um texto de busca, **When** ele é aplicado, **Then** vêm as ocorrências cujo título ou mensagem contém o texto, sem diferenciar maiúsculas de minúsculas, ou cujo identificador é igual ao texto.
6. **Given** filtros combinados, **When** o usuário pede outra página, **Then** os itens e o total continuam respeitando todos os filtros.
7. **Given** filtros que nenhuma ocorrência satisfaz, **When** a lista é pedida, **Then** a resposta é a lista vazia com total zero.
8. **Given** um período com início depois do fim, um tipo ou status desconhecido, ou um texto acima de 200 caracteres, **When** a lista é pedida, **Then** a resposta é 400 com o campo inválido.
9. **Given** uma consulta sem nenhum filtro, **When** a lista é pedida, **Then** o resultado é o mesmo da lista padrão da história 1.

---

### User Story 3 - Ver o detalhe da ocorrência (Priority: P1)

O usuário abre uma ocorrência e vê os dados técnicos: título, resumo, tipo, status, recorrente, produto, contagem, primeira e última vez, exemplos de log já mascarados, atributos técnicos (host, serviço, trace e campos extraídos) e o histórico de status.

**Why this priority**: critério de aceite da LLT-205 e do USID_002. Fecha o fluxo lista → detalhe.

**Independent Test**: abrir uma ocorrência da carga de exemplo e conferir os campos; abrir com outra empresa e receber 404.

**Acceptance Scenarios**:

1. **Given** uma ocorrência de um produto que o usuário pode ver, **When** ele consulta o detalhe, **Then** recebe todos os campos da ocorrência, com os exemplos de log e o histórico de status.
2. **Given** uma ocorrência com mais de 5 exemplos de log gravados, **When** o detalhe é consultado, **Then** vêm os 5 exemplos mais recentes (os últimos gravados), do mais novo para o mais antigo.
3. **Given** uma ocorrência sem exemplos, sem resumo, só com o host nos atributos ou sem histórico, **When** o detalhe é consultado, **Then** essas partes vêm vazias ou nulas, sem erro.
4. **Given** uma ocorrência de outra empresa, de um produto sem acesso ou um identificador inexistente, **When** o detalhe é consultado, **Then** a resposta é 404, a mesma nos três casos.
5. **Given** o histórico de status, **When** ele é devolvido, **Then** cada mudança traz o status anterior, o novo, a data e quem mudou (pessoa ou sistema), da mais antiga para a mais recente.
6. **Given** uma ocorrência gravada sem título, **When** a lista ou o detalhe é consultado, **Then** o título é a mensagem normalizada tirada da assinatura.

---

### User Story 4 - Carga de exemplo no ambiente local (Priority: P2)

No perfil local, a pessoa desenvolvedora carrega ocorrências de exemplo nos produtos da empresa de desenvolvimento, para usar a tela e rodar o roteiro sem o Módulo Ocorrência.

**Why this priority**: o Módulo ainda não grava ocorrências; sem a carga, a tela fica sempre vazia.

**Independent Test**: subir o Core no perfil local com a carga ligada, consultar a lista e encontrar os exemplos; subir de novo e conferir que eles não se duplicaram.

**Acceptance Scenarios**:

1. **Given** o perfil local com a carga ligada e uma empresa com produtos, **When** o Core sobe, **Then** a coleção ganha as ocorrências de exemplo, com os três tipos, os três status, recorrentes e não recorrentes, exemplos de log mascarados e histórico.
2. **Given** a carga já feita, **When** o Core sobe de novo, **Then** nenhum exemplo é duplicado.
3. **Given** o perfil de produção, **When** o Core sobe, **Then** a carga não existe e nada é gravado na coleção.

---

### Edge Cases

- Membro sem acesso a nenhum produto: lista vazia com total zero; qualquer detalhe responde 404.
- Produto arquivado: as ocorrências dele aparecem na lista e no detalhe para quem tem acesso ao produto.
- Documento da coleção com campo obrigatório ausente, tipo ou status desconhecido: fica fora da lista e do total e responde 404 no detalhe; no detalhe sai um WARN `event=occurrence.read.invalid` com o identificador, sem o conteúdo.
- Duas ocorrências com a mesma última vez: a ordem desempata pelo identificador, para a paginação não repetir nem pular itens.
- Texto de busca com caracteres especiais (`.`, `*`, `(`, `[`): tratados como texto literal, não como padrão.
- Texto de busca vazio ou só com espaços: ignorado, como se não houvesse busca.
- Só início ou só fim do período: vale o limite informado; o outro lado fica aberto.
- Ocorrência gravada enquanto o usuário pagina: pode deslocar itens entre páginas; a atualização em tempo real fica para a LLT-208.
- Linha de exemplo acima de 512 caracteres: devolvida cortada em 512 caracteres.
- Assinatura fora do formato `nível|mensagem|serviço|host` numa ocorrência sem título: o título vira a assinatura inteira, e o serviço fica fora dos atributos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O Core MUST oferecer a consulta paginada das ocorrências da empresa ativa, ordenada pela última vez (mais recente primeiro), com desempate estável pelo identificador.
- **FR-002**: Cada item da lista MUST trazer identificador, tipo, título, produto (identificador e nome), contagem, status, recorrente, primeira vez e última vez.
- **FR-003**: A página MUST trazer os itens, o número da página, o tamanho e o total de itens que satisfazem a consulta.
- **FR-004**: O tamanho da página MUST ser 10, 20 ou 50, com 20 como padrão; a página MUST começar em 1. Valores fora disso MUST responder 400 com o parâmetro inválido.
- **FR-005**: Dono e administrador MUST ver as ocorrências de todos os produtos da empresa, inclusive arquivados; os demais membros MUST ver só as dos produtos a que têm acesso (matriz de permissões da LLT-169, `PRODUCTS_VIEW_ALL` da feature 011/LLT-120, e regra de acesso da 001).
- **FR-006**: A lista MUST aceitar, combinados, os filtros: período (início e fim, cada um opcional), tipos (um ou mais), status (um ou mais), produtos (um ou mais) e texto. Sem filtro, o resultado MUST ser o da lista padrão.
- **FR-007**: O período MUST selecionar as ocorrências com primeira vez até o fim e última vez a partir do início; início depois do fim MUST responder 400.
- **FR-008**: Um produto no filtro que não existe, é de outra empresa ou não é acessível ao usuário MUST fazer a consulta responder 404, com a mesma resposta nos três casos.
- **FR-009**: O texto MUST buscar, sem diferenciar maiúsculas de minúsculas e como texto literal, no título, no resumo e na assinatura, e MUST também encontrar a ocorrência cujo identificador é igual ao texto. Texto acima de 200 caracteres MUST responder 400; texto vazio MUST ser ignorado.
- **FR-010**: Tipos válidos MUST ser ERROR, WARNING e INFO; status válidos MUST ser ABERTA, EM_ANALISE e FECHADA (ADR_082). Valor desconhecido no filtro MUST responder 400.
- **FR-011**: O Core MUST oferecer o detalhe de uma ocorrência com: identificador, tipo, título, resumo (opcional), status, recorrente, produto, contagem, primeira e última vez, até 5 exemplos de log mais recentes, atributos técnicos (nome e valor: host, serviço, trace, correlation id, módulo, número da linha e campos extraídos), a indicação de dados sensíveis quando conhecida e histórico de status.
- **FR-011a**: Ocorrência sem título gravado MUST ter como título a mensagem normalizada tirada da assinatura, na lista e no detalhe.
- **FR-012**: Cada entrada do histórico de status MUST trazer status anterior, status novo, data e autor (pessoa ou sistema), em ordem cronológica.
- **FR-013**: Ocorrência de outra empresa, de produto sem acesso ou inexistente MUST responder 404 no detalhe, com a mesma resposta nos três casos.
- **FR-014**: O Core MUST devolver os exemplos de log como estão gravados, já mascarados pelo pipeline (spec 010 do finder, ADR_040); MUST NOT montar ou devolver conteúdo de log bruto, e MUST cortar cada linha em 512 caracteres.
- **FR-015**: Documento com campo obrigatório ausente ou valor de tipo ou status desconhecido MUST ficar fora da lista e do total e MUST responder 404 no detalhe; quando o detalhe encontra um documento assim, MUST gerar um WARN `event=occurrence.read.invalid` com tenant e identificador, sem conteúdo de log.
- **FR-016**: O formato esperado da ocorrência gravada MUST ficar documentado nesta feature como contrato para o Módulo Ocorrência.
- **FR-017**: No perfil local MUST existir uma carga de exemplo, ligada por configuração, que grava ocorrências nos produtos existentes sem duplicar a cada subida; no perfil de produção ela MUST NOT existir.
- **FR-018**: Esta feature MUST NOT criar, alterar, arquivar nem apagar ocorrências fora da carga local; MUST NOT mudar o status (LLT-206), arquivar (LLT-207), avisar em tempo real (LLT-208), calcular gravidade ou métricas de painel.
- **FR-019**: As consultas MUST exigir a sessão da empresa ativa com Tenant, como as demais rotas `/api/**` (spec 008 e spec da LLT-289).

### Key Entities

- **Ocorrência**: agrupamento de logs com a mesma chave de unicidade (assinatura, tipo, produto e tenant), gravado pelo Módulo Ocorrência. Tem tipo, título e resumo (opcionais), status, recorrente, contagem, primeira e última vez, exemplos de log mascarados, campos técnicos (host, trace, correlation id, módulo, linha, campos extraídos), indicação de dados sensíveis e histórico de status. Pertence a um produto de um tenant.
- **Exemplo de log**: um trecho de log já mascarado, sem data (contrato do Finder).
- **Mudança de status**: status anterior, status novo, data e autor (pessoa ou sistema).
- **Página de ocorrências**: itens, página, tamanho e total.
- **Filtro de ocorrências**: período, tipos, status, produtos e texto (classe `FiltroOcorrenciaEmpresarial` do modelo de classes).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos testes, a lista vem ordenada pela última vez, e percorrer todas as páginas devolve cada ocorrência visível exatamente uma vez.
- **SC-002**: Em 100% dos testes de filtro combinado, todos os itens e o total respeitam todos os filtros, em qualquer página.
- **SC-003**: 100% das consultas de lista e de detalhe por quem não pode ver a ocorrência ou o produto respondem lista vazia ou 404, sem dados de outra empresa ou produto.
- **SC-004**: Com 10.000 ocorrências na empresa, a primeira página, com ou sem filtros, chega em menos de 1 segundo no ambiente local.
- **SC-005**: 100% dos detalhes devolvem os exemplos como gravados (mascarados) e com no máximo 512 caracteres por linha.
- **SC-006**: Na carga de exemplo, subir o Core duas vezes deixa o mesmo número de ocorrências.

## Assumptions

- O Módulo Ocorrência (ADR_062), a cargo do Felipe, grava as ocorrências a partir do `ResumoAnalise` do contrato DN-009-2 aprovado. O Core só lê nesta feature; até o Módulo existir, a coleção é preenchida pela carga local. A gravação (rotas `/internal/v1/...`, JWT interno, recibo e revisão) é outra feature.
- A chave de unicidade é (tenant, produto, assinatura, tipo), como no contrato do Finder; mudar a `versaoRegras` não cria ocorrência nova. A assinatura não aparece como campo para o usuário.
- O mascaramento acontece no Finder (spec 010). O Core não mascara de novo. O Finder não garante o tamanho dos trechos; o Core corta em 512 caracteres.
- Gravidade (calculada no Módulo, ADR_062) não entra nesta entrega; a tela usa o tipo para a cor.
- Arquivamento de ocorrência é da LLT-207; até lá, nenhuma ocorrência está arquivada.
- Cadastro, papéis, acesso a produto e isolamento por empresa das features 001, 002 e 008 continuam valendo.
- Fora de escopo: gravação pelo Módulo, mudança de status, arquivamento, tempo real, sugestão, gravidade, exportação, painéis de métricas e o tamanho de página livre.

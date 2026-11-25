# Feature Specification: Gravar ocorrências a partir do resumo de análise do Finder (Módulo Ocorrência)

**Feature Branch**: `LLT-293-gravar-ocorrencias-a-partir-do-resumo-de-analise-do-finder-modulo-ocorrencia`

**Created**: 25/11/2026

**Status**: Draft

**Input**: User description: "LLT-293 (épico LLT-104 Ocorrências): lado servidor do contrato Finder → Módulo Ocorrência aprovado em 20/11 (DN-009-2 e N3 A, spec 009 do lhamalog-finder). O Módulo Ocorrência é um pacote dentro do Core, que já lê a coleção de ocorrências para a tela (LLT-135). O Finder consulta o estado de cada achado, calcula os totais e entrega um resumo por lote; o Módulo grava as ocorrências e devolve um recibo. Critérios: consulta do estado por (tenant, produto, assinatura, tipo) com `estado: null` quando não existe; entrega com `201` e recibo na primeira vez e `200` com o mesmo recibo no reenvio da mesma chave, sem duplicar; revisão desatualizada recusa a entrega inteira com `409 REVISAO_DESATUALIZADA`, sem gravação parcial; entrega concorrente da mesma chave recebe `409 ENTREGA_EM_ANDAMENTO`; achado de ocorrência FECHADA reabre como ABERTA, com histórico; rotas com JWT interno de serviço (ADR_028), emissor, audiência e chaves da configuração, conferindo que o produto pertence ao tenant; homologação com o cliente do Finder (PR #13 do lhamalog-finder) nos cinco cenários do contrato. Fora do escopo: gravidade/criticidade, aviso ao Core para notificação (LLT-216), aviso de falha do OpenObserve (ADR_051), agendamento de reenvio no Finder e a tela de ocorrências (LLT-135)."

## Contexto

- Contrato de origem: `lhamalog-finder/specs/009-publicar-evento-ocorrencia/contracts/proposta-dn-009-2.md` e `proposta-dn-009-2.openapi.yaml` (aprovado em 20/11/2026). Os nomes de rota, campos JSON, códigos de erro e status HTTP são os desse contrato e não mudam nesta feature.
- Formato gravado: `specs/012-consultar-ocorrencias/contracts/ocorrencia-documento.md` e `data-model.md` (coleção `Ocorrencia`), que a tela da LLT-135 já lê. Esta feature é quem cumpre aquele contrato.
- Esta branch sai da `LLT-135-lista-e-detalhe` (PR #19 do core ainda aberto): o pacote `occurrence`, a coleção e os índices vêm de lá. O PR desta feature é empilhado sobre o da LLT-135.

## Clarifications

### Session 2026-11-25

- Q: O `count` do achado já vem somado? → A: Sim. O contrato diz que `contagem` "já inclui a contagem acumulada consultada mais o incremento do lote". O Módulo grava o valor como veio.
- Q: Os nomes em `camposExtraido` podem ter ponto (`http.route`)? → A: O contrato aceita qualquer nome (`additionalProperties: string`). O Módulo grava os nomes sem trocar o ponto, que é como a tela da LLT-135 lê. Pergunta de confirmação enviada ao João em 25/11; se a resposta mudar algo, esta linha é revista.
- Q: As `linhas` de um achado vêm da mais antiga para a mais nova? → A: Suposição até a resposta do João (25/11): sim. O Módulo acrescenta as linhas no fim da lista e guarda as 20 últimas; a tela mostra as 5 últimas como as mais recentes.
- Q: Como o JWT interno do Finder é emitido e validado? → A: HS256 com segredo compartilhado na configuração do Core (até dois segredos ativos, para rotação), emissor, audiência e lista de serviços (`sub`) configurados. Um comando do repositório gera o token que vai em `FINDER_MODULO_OCORRENCIA_JWT`. Sem servidor de chaves nesta entrega (Felipe, 25/11).
- Q: Como garantir o tudo ou nada sem transação? → A: Sem transação do Mongo, como nas specs 003, 005, 009 e 010: recibo em processamento com prazo, diário dos achados e escrita condicional pela revisão; queda ou corrida é concluída ou desfeita na próxima tentativa da chave. O compose local continua sem replica set (Felipe, 25/11).
- Q: Por quanto tempo o recibo fica guardado? → A: 30 dias, configurável, até a equipe que opera o Redis definir a janela real de reenvio (Felipe, 25/11).

## User Scenarios & Testing *(mandatory)*

Os "usuários" desta feature são serviços: o Finder (cliente) chama o Módulo Ocorrência (servidor, dentro do Core). O valor para as pessoas aparece na tela da LLT-135, que passa a mostrar ocorrências reais.

### User Story 1 - Consultar o estado de um achado (Priority: P1)

Antes de montar o resumo de um lote, o Finder pergunta ao Módulo o estado atual da ocorrência de cada achado, identificada por tenant, produto, assinatura e tipo. Com a resposta, o Finder soma a contagem do lote à acumulada e guarda a revisão que viu.

**Why this priority**: sem a consulta o Finder não calcula os totais nem a revisão; a entrega (US2) depende dela.

**Independent Test**: com ocorrências gravadas na coleção, chamar a consulta para uma chave existente e para uma inexistente, e conferir as duas respostas.

**Acceptance Scenarios**:

1. **Given** uma ocorrência gravada para (tenant, produto, assinatura, tipo), **When** o Finder consulta essa chave, **Then** a resposta é 200 com `estado` contendo a contagem acumulada, a primeira e a última ocorrência em UTC, o status e a revisão atual.
2. **Given** nenhuma ocorrência para a chave, **When** o Finder consulta, **Then** a resposta é 200 com `estado: null`.
3. **Given** um produto que não pertence ao tenant informado, **When** o Finder consulta, **Then** a resposta recusa o acesso sem revelar se o produto existe em outro tenant.
4. **Given** um corpo com campo a mais, tipo desconhecido ou campo obrigatório vazio, **When** o Finder consulta, **Then** a resposta é de dados inválidos, sem consultar nada.

---

### User Story 2 - Entregar o resumo de um lote e receber o recibo (Priority: P1)

O Finder entrega o resumo de análise de um lote. O Módulo grava cada achado na ocorrência da sua chave, criando ou atualizando, e devolve um recibo. Se a resposta se perder e o Finder reenviar a mesma chave de entrega, recebe o mesmo recibo e nada é gravado de novo.

**Why this priority**: é o que faz as ocorrências reais existirem; é o critério central da LLT-293.

**Independent Test**: entregar um resumo com achados novos e existentes, conferir a coleção e o recibo; reenviar o mesmo corpo e conferir o mesmo recibo, sem mudança na coleção.

**Acceptance Scenarios**:

1. **Given** um resumo cuja chave de entrega nunca foi aceita, com revisões consultadas atuais, **When** o Finder entrega, **Then** a resposta é 201 com o recibo (`aceito: true`, `reciboId`, `tenantId`, `productId`, `batchId`, `execId` quando veio, `repeticao: false`) e cada achado está gravado na sua ocorrência.
2. **Given** um achado sem ocorrência (revisão consultada `null`), **When** a entrega é aceita, **Then** nasce uma ocorrência com status ABERTA, os valores do achado, uma revisão nova e o histórico com a criação feita pelo sistema.
3. **Given** um achado de ocorrência existente, **When** a entrega é aceita, **Then** contagem, primeira e última ocorrência, recorrente e versão das regras recebem os valores do achado; título, resumo e os campos técnicos só mudam quando o achado os traz preenchidos; as linhas novas entram no fim dos exemplos, que guardam as 20 últimas; a revisão muda.
4. **Given** um achado de ocorrência FECHADA, **When** a entrega é aceita, **Then** a ocorrência volta a ABERTA e o histórico ganha a mudança FECHADA → ABERTA feita pelo sistema. Ocorrências ABERTA ou EM_ANALISE mantêm o status.
5. **Given** uma chave de entrega já aceita, **When** o Finder entrega de novo (mesmo corpo, corpo recalculado com outros números ou revisões já superadas), **Then** a resposta é 200 com o recibo original e `repeticao: true`, sem conferir revisões e sem gravar nada.
6. **Given** a mesma `batchId` com outro `execId`, **When** o Finder entrega, **Then** é uma chave nova, tratada como primeira entrega.
7. **Given** um resumo sem achados, **When** o Finder entrega, **Then** a entrega é aceita com recibo, sem mudar ocorrências.

---

### User Story 3 - Recusar entregas desatualizadas ou concorrentes sem efeito parcial (Priority: P1)

Dois lotes podem consultar a mesma ocorrência e entregar quase juntos. O Módulo só aceita a entrega se cada revisão consultada ainda for a atual; se uma só estiver desatualizada, recusa tudo, e o Finder consulta de novo e recalcula. Duas entregas da mesma chave ao mesmo tempo não podem gravar duas vezes.

**Why this priority**: sem isso, incrementos de um lote somem ou são contados duas vezes, e a contagem da tela deixa de ser confiável.

**Independent Test**: consultar, mudar a ocorrência por outra entrega e entregar com a revisão antiga; disparar duas entregas iguais em paralelo.

**Acceptance Scenarios**:

1. **Given** um resumo em que ao menos um achado traz revisão consultada diferente da atual (ou `null` para uma ocorrência que já existe, ou uma revisão para uma que não existe), **When** o Finder entrega, **Then** a resposta é `409 REVISAO_DESATUALIZADA` e nenhuma ocorrência do resumo muda, nem as dos outros achados.
2. **Given** a entrega recusada por revisão, **When** o Finder consulta de novo e entrega com os valores recalculados sob a mesma chave, **Then** a entrega é aceita com 201, porque ainda não havia recibo.
3. **Given** uma entrega da chave em processamento, **When** chega outra entrega da mesma chave, **Then** a segunda recebe `409 ENTREGA_EM_ANDAMENTO` e a primeira segue normalmente.
4. **Given** dois lotes diferentes que consultaram a mesma revisão de uma ocorrência, **When** os dois entregam, **Then** um é aceito e o outro recebe `409 REVISAO_DESATUALIZADA`; depois de consultar de novo e reenviar, a contagem final preserva os incrementos dos dois.
5. **Given** uma entrega interrompida no meio (queda do Core), **When** o Finder reenvia a mesma chave, **Then** o resultado final é o de uma única aplicação completa do resumo, ou de nenhuma: nunca parte dos achados.

---

### User Story 4 - Aceitar só o Finder autenticado e no tenant certo (Priority: P1)

As rotas do Módulo são internas. Só um serviço com o JWT interno válido para o Módulo pode chamá-las, e o produto informado precisa pertencer ao tenant informado.

**Why this priority**: as rotas escrevem dados de todos os tenants; sem autenticação e sem a conferência de tenant, qualquer chamada na rede interna corromperia ocorrências.

**Independent Test**: chamar as duas rotas sem token, com token expirado, com emissor ou audiência errados, com serviço não autorizado e com produto de outro tenant.

**Acceptance Scenarios**:

1. **Given** uma chamada sem `Authorization: Bearer`, com token malformado, assinatura inválida, expirado, ainda não válido, ou com emissor ou audiência diferentes dos configurados, **When** ela chega a qualquer rota do Módulo, **Then** a resposta é 401, sem processar o corpo.
2. **Given** um token válido de um serviço que não está autorizado para a operação, **When** ele chama, **Then** a resposta é 403.
3. **Given** um token válido do Finder e um `productId` que não é do `tenantId` (ou tenant inexistente), **When** ele consulta ou entrega, **Then** a resposta é 403, sem efeito, e o log não registra a assinatura nem trechos de log.
4. **Given** as rotas do Módulo, **When** um usuário chama com o token de sessão da Clerk, **Then** a resposta é 401: o token de usuário não serve para as rotas internas, e o JWT interno não serve para as rotas de usuário.

---

### User Story 5 - Homologar com o cliente do Finder (Priority: P2)

Com o Core local e o cliente do Finder da spec 009, os cinco cenários de homologação do contrato passam ponta a ponta, e a tela da LLT-135 mostra as ocorrências gravadas.

**Why this priority**: o contrato só fecha quando os dois lados conversam; depende das US1 a US4 e do ambiente do Finder.

**Independent Test**: roteiro do quickstart com o Finder apontado para o Core local.

**Acceptance Scenarios**:

1. **Given** o Core local com o Módulo configurado e o Finder com URL e JWT do Módulo, **When** o Finder processa lotes de teste, **Then** os cinco cenários de homologação do contrato DN-009-2 passam e as ocorrências aparecem na tela `/ocorrencias`.

---

### Edge Cases

- Resumo com dois achados da mesma chave (assinatura e tipo iguais): dados inválidos, sem gravar nada.
- Achado com `primeiraOcorrencia` depois de `ultimaOcorrencia`, contagem menor que 1, horário sem `Z`, tipo fora de ERROR/WARNING/INFO, campo desconhecido ou campo obrigatório ausente: dados inválidos (422), sem gravar nada.
- `linhasProcessadas` diferente de `erros + warnings + informacao`, ou `identificacaoRegras.versaoUnica` diferente de `versaoRegras`: dados inválidos.
- JSON ilegível ou corpo acima do limite: requisição inválida (400).
- Contagem do achado menor que a acumulada atual com a revisão certa: o Módulo grava como veio (o Finder é a fonte da soma) e registra um WARN, sem recusar.
- `linhas: []`: os exemplos anteriores ficam; `titulo`/`resumo`/campos técnicos `null`: o valor anterior fica.
- Mais de uma ocorrência gravada para a mesma chave (dado corrompido): a consulta e a entrega respondem erro interno e registram ERROR; o Finder mantém o lote pendente.
- Ocorrência gravada fora do Módulo sem contagem, datas ou status válidos na chave do achado: a consulta não consegue devolvê-la no formato do contrato e responde erro interno com ERROR no log, sem o conteúdo; o lote fica pendente no Finder até o dado ser corrigido. Só falta de revisão é tolerada (vale como `sem-revisao`).
- Produto arquivado: aceita normalmente (as ocorrências de produto arquivado seguem visíveis na LLT-135).
- Recibo de uma chave mais antigo que o prazo de retenção: depois de removido, um reenvio dessa chave é tratado como entrega nova; o prazo precisa cobrir a janela de reenvio do Finder.
- Mudança de status pelo usuário (LLT-206, futura) entre a consulta e a entrega: muda a revisão, e a entrega recebe `REVISAO_DESATUALIZADA`.

## Requirements *(mandatory)*

### Functional Requirements

**Consulta (US1)**

- **FR-001**: O Módulo MUST oferecer `POST /internal/v1/ocorrencias/consultas` que recebe `{tenantId, productId, assinatura, tipo}` e responde 200 com `estado: null` quando não existe ocorrência para a chave, ou `estado` com `contagemAcumulada`, `primeiraOcorrencia`, `ultimaOcorrencia` (UTC, sufixo `Z`), `status` e `revisao`.
- **FR-002**: A consulta MUST ser somente leitura e MUST responder erro interno quando houver mais de uma ocorrência para a chave.

**Entrega (US2)**

- **FR-003**: O Módulo MUST oferecer `POST /internal/v1/resumos-analise` que recebe o `ResumoAnalise` do contrato e responde 201 com o recibo na primeira entrega aceita da chave e 200 com o mesmo recibo (`repeticao: true`) nas seguintes.
- **FR-004**: A chave de entrega MUST ser (`tenantId`, `productId`, `batchId`) quando não há `execId`, e (`tenantId`, `productId`, `batchId`, `execId`) quando há. O `reciboId` MUST ser opaco e estável para a chave.
- **FR-005**: Para uma chave já aceita, o Módulo MUST devolver o recibo original antes de qualquer validação de revisões e MUST NOT gravar nada.
- **FR-006**: Cada achado aceito MUST ser gravado na ocorrência da chave (`tenantId`, `productId`, `assinatura`, `tipo`) conforme as regras 1 a 9 de `specs/012-consultar-ocorrencias/contracts/ocorrencia-documento.md`: criação com ABERTA; contagem e datas como vieram; `titulo`, `resumo`, `hostName`, `traceId`, `correlationId`, `moduloId`, `numeroLinha`, `camposExtraido` e `dadosSensiveis` só quando preenchidos; `linhas` no fim dos exemplos, mantendo as 20 últimas; `recorrente` e `versaoRegras` do resumo; revisão nova a cada gravação.
- **FR-007**: Achado de ocorrência FECHADA MUST reabrir a ocorrência como ABERTA. A criação e a reabertura MUST entrar no histórico de status com autor do tipo sistema e id `modulo-ocorrencia`.

**Concorrência e atomicidade (US3)**

- **FR-008**: Antes de gravar, o Módulo MUST conferir a revisão consultada de todos os achados: `null` exige que a ocorrência não exista; um valor exige que seja a revisão atual. Uma divergência MUST recusar a entrega inteira com `409 REVISAO_DESATUALIZADA`, sem recibo.
- **FR-009**: A entrega MUST ser tudo ou nada: nenhum estado intermediário (parte dos achados gravados e outra não) pode ficar como resultado final de uma entrega recusada, interrompida ou concorrente. Uma entrega interrompida MUST ser concluída ou desfeita na próxima tentativa da mesma chave, antes de qualquer outro efeito.
- **FR-010**: Enquanto uma entrega da chave estiver em processamento, outra entrega da mesma chave MUST receber `409 ENTREGA_EM_ANDAMENTO`. Um processamento abandonado (queda) MUST deixar de bloquear a chave depois de um prazo curto.
- **FR-011**: O recibo MUST ser gravado de forma durável junto com a aplicação dos achados: o Módulo só responde 201 depois que os achados e o recibo estão gravados.
- **FR-012**: O recibo MUST ficar disponível por um prazo de retenção configurável, com padrão que cubra a janela de reenvio do Finder.

**Validação**

- **FR-013**: As rotas MUST rejeitar campos desconhecidos e valores fora do contrato (tipo, classe, horários sem `Z`, contagem < 1, `numeroLinha` < 1, `execId` fora do formato `V`/`M` + UUID, chaves repetidas no mesmo resumo, `primeiraOcorrencia` > `ultimaOcorrencia`, contadores incoerentes) com 422 e JSON ilegível com 400, sempre no formato de erro do contrato (`{"codigo": "..."}`), sem gravar nada.

**Segurança (US4)**

- **FR-014**: As rotas `/internal/v1/**` MUST exigir `Authorization: Bearer <JWT interno>`, validando assinatura, `iss`, `aud`, `sub`, `exp` e `nbf` quando presente, com emissor, audiência, chaves e algoritmo permitidos vindos da configuração. O algoritmo é HS256 com segredo compartilhado (até dois segredos ativos para rotação); o token do Finder é gerado por um comando do repositório.
- **FR-015**: Só os serviços autorizados (`sub` na lista configurada, com o Finder) MUST poder chamar cada operação; outro serviço recebe 403.
- **FR-016**: O Módulo MUST conferir que o tenant existe e que o produto pertence ao tenant; caso contrário, 403 sem efeito.
- **FR-017**: O token de sessão de usuário (Clerk) MUST NOT valer nas rotas internas, e o JWT interno MUST NOT valer nas rotas de usuário.

**Observabilidade e dados**

- **FR-018**: Os logs MUST seguir o padrão do Core (constituição VI), com eventos `occurrence.lookup.*`, `occurrence.delivery.*` e `occurrence.auth.*`, e MUST NOT conter assinatura, trechos de log, títulos, resumos, campos extraídos ou o token; podem conter `tenantId`, `productId`, `batchId`, `execId`, quantidade de achados e o código do resultado.
- **FR-019**: Os erros MUST usar os códigos estáveis do contrato (`REVISAO_DESATUALIZADA`, `ENTREGA_EM_ANDAMENTO` e códigos de validação/autenticação em maiúsculas) e MUST NOT incluir credenciais, assinatura ou conteúdo de logs.
- **FR-020**: O formato gravado MUST continuar legível pela consulta da LLT-135 sem mudança nela (as ocorrências gravadas aparecem na lista e no detalhe).

### Key Entities

- **Ocorrência**: o documento da coleção `Ocorrencia` definido na spec 012 (chave tenant, produto, assinatura e tipo; contagem, datas, status, recorrente, exemplos, atributos técnicos, revisão e histórico).
- **Recibo de entrega**: o registro de que uma chave de entrega (tenant, produto, lote e execução) foi aceita, com o `reciboId`, a data de aceite e o necessário para responder reenvios; guardado pelo prazo de retenção.
- **Entrega em processamento**: a marca temporária de que uma chave está sendo aplicada, com prazo de expiração, usada para recusar entregas concorrentes e para concluir ou desfazer uma entrega interrompida.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos testes de reenvio (mesma chave, inclusive com corpo recalculado), a coleção termina igual à da primeira entrega e o `reciboId` é o mesmo.
- **SC-002**: Em 100% dos testes de revisão desatualizada, nenhuma ocorrência do resumo muda.
- **SC-003**: Em 100% dos testes de entregas concorrentes de lotes diferentes que consultaram a mesma revisão, a contagem final, depois dos reenvios, é a soma de todos os incrementos.
- **SC-004**: Com o Core local, uma consulta responde em até 200 ms e a entrega de um resumo com 100 achados em até 2 s (abaixo dos tempos de espera de 2 s e 5 s do cliente do Finder).
- **SC-005**: Em 100% das chamadas recusadas por autenticação, autorização ou tenant, nada é gravado e o log não contém token, assinatura ou trechos de log.
- **SC-006**: Os cinco cenários de homologação do contrato DN-009-2 passam com o cliente do Finder.

## Assumptions

- O Finder é o único cliente nesta entrega; a Varredura e outros serviços não chamam o Módulo.
- O Finder chama o Módulo por HTTPS na rede interna; no ambiente local, a homologação pode precisar de um proxy TLS na frente do Core, porque o cliente do Finder só monta URLs `https://`.
- Retenção dos recibos: 30 dias, configurável (clarify de 25/11).
- Tenant existente basta para aceitar a entrega; o status da assinatura do tenant não bloqueia a gravação (a retenção de dados segue as regras de plano já existentes).
- O status de uma ocorrência só muda nesta feature por criação e reabertura; mudança manual é a LLT-206.
- Gravidade/criticidade, aviso ao Core para notificação (LLT-216) e aviso de falha do OpenObserve (ADR_051) ficam fora.
- O Core continua com o MongoDB local sem replica set, como nas features anteriores (clarify de 25/11); o desenho do tudo ou nada fica no plano.

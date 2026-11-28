# Tasks: Alerta do Finder e aviso de ocorrência recorrente

**Input**: Design documents from `/specs/016-alerta-finder-evento-ocorrencia/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Incluídos. A constituição, princípio III, exige teste do comportamento novo antes do merge (`./mvnw verify`, Testcontainers com tag fixa). A cobertura está em research.md, R14, e nos SC-001 a SC-008. Esta lista não escreve esses testes.

**Organização**: Fases por história. A implementação assume #19, #20, #21 e #24 já mergeados e não recria o que essa pilha faz. (fonte: decisoes-joao-llt201.md, base da branch; plan.md, Summary; spec.md, Fora de escopo)

**Regra desta lista (rodada 3)**: as pendências 1 a 16 e a 20 estão fechadas. A coleção é `finder_alerts` e o literal de `Change` para "virou recorrente" é `RECURRENT` (fonte: decisoes-joao-llt201.md, Rodada 3). O aviso de tela do alerta, na primeira gravação (201), sai no canal `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`; a repetição (200) não avisa de novo. Nenhuma tarefa desta lista está marcada como feita. Os eventos de log da rota de alerta são `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected` (fonte: decisoes-joao-llt201.md, Rodada 4).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode andar em paralelo (arquivos diferentes, sem depender de tarefa ainda aberta)
- **[Story]**: história da spec (US1, US2, US3, US4). Setup, Foundational e Polish não levam história

## Path Conventions

- Serviço único: `src/main/java/br/com/lhamalog/core/`, testes em `src/test/java/br/com/lhamalog/core/`
- Árvore que esta feature altera: a de plan.md, Project Structure. O Módulo continua em `occurrence`. O alerta entra no pacote novo `finderalert` (exemplo `br.com.lhamalog.core.finderalert`). A cadeia segue em `config/InternalServiceSecurityConfiguration.java`. O filtro segue em `logging/CorrelationIdFilter.java`
- O pacote `finderalert` é o nome que o João deu (pendência 15). Não inventar outro pacote. Nenhuma classe além desse pacote está nomeada no plano (research.md, R13)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar a base já existente e o que esta feature não recria. Não há projeto novo nem dependência nova.

- [x] T001 Confirmar, sem criar código, que a base já contém `src/main/java/br/com/lhamalog/core/occurrence/` (pilha #19 ← #20 ← #21 ← #24) e que esta feature não recria lista/detalhe, `POST /internal/v1/ocorrencias/consultas`, `POST /internal/v1/resumos-analise`, recibo, `409`, ciclo de vida, `CREATED`, `REOPENED`, o canal `lhamalog:occurrence-events` nem o WebSocket `/ws/occurrences`. O acréscimo do alerta é o pacote `src/main/java/br/com/lhamalog/core/finderalert/` (plan.md, Summary e Project Structure; spec.md, Fora de escopo e Assumptions; fonte: decisoes-joao-llt201.md, base da branch, pendência 15, formulário 1; origem: contexto-llt201.md, §1 e §7 Q1 opção C)
- [x] T002 [P] Confirmar que `pom.xml` não recebe dependência nem versão nova: Java 25, Spring Boot 4.1.1 e os starters já declarados permanecem (plan.md, Technical Context e Constitution Check, Stack; research.md, R2; constituição, Stack tecnológico)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Preservar a cadeia única, o filtro de log e o aviso do #24 antes de qualquer história. A audiência por rota e o token único entram na história 1 (T019), nesta mesma cadeia. Nenhuma história começa antes desta fase.

**⚠️ CRÍTICO**: Nenhuma história de usuário começa antes que esta fase esteja completa

- [x] T003 Preservar o filtro já registrado em `/*` em `src/main/java/br/com/lhamalog/core/logging/CorrelationIdFilter.java` e `src/main/java/br/com/lhamalog/core/logging/CorrelationIdFilterConfiguration.java`. Não criar outro filtro de correlation id (plan.md, Constitution Check, princípio VI; research.md, R12; spec.md, FR-012)
- [x] T004 [P] Manter uma única cadeia em `src/main/java/br/com/lhamalog/core/config/InternalServiceSecurityConfiguration.java` para `/internal/v1/**`. Não criar segunda `SecurityFilterChain` para o alerta. O ajuste de audiência por rota e o token com as duas audiências são T019, nesta cadeia. Fato de código: no #20 a cadeia cobre todo `/internal/v1/**` e exige que o `aud` contenha `lhamalog.occurrence.internal-auth.audience` (padrão `lhamalog-modulo-ocorrencia`); a conferência é por "contém" (`values.contains(auth.audience())`). Sem o ajuste por rota, um token só com `lhamalog-core` recebe 401. `scripts/InternalJwt.java` gera um token com uma única audiência (`--aud`). (spec.md, FR-005; research.md, R4; plan.md, Project Structure; fonte: decisoes-joao-llt201.md, pendências 6, 15 e 20, formulário 1; respostas-pendentes-llt201.md, resumo, fato de código; origem: contexto-llt201.md, §7 Q6 opção A)
- [x] T005 [P] Manter canal, campos já existentes e encaminhamento em `src/main/java/br/com/lhamalog/core/occurrence/stream/RedisOccurrenceEventPublisher.java`, `src/main/java/br/com/lhamalog/core/occurrence/stream/RedisOccurrenceEventListener.java` e `src/main/java/br/com/lhamalog/core/occurrence/stream/OccurrenceConnections.java`. Não criar canal, envelope ADR_146 nem uso de `lhamalog:notifications:events` como envelope desta feature. O alerta do Finder não entra nesse canal Redis. O aviso de tela dele, só na primeira gravação (201), é o de T011: canal `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}` (fonte: decisoes-joao-llt201.md, Rodada 3). (spec.md, FR-006; contracts/aviso-ocorrencia.md; fonte: decisoes-joao-llt201.md, evento para a Notificação e pendência 1, formulário 1)
- [x] T006 [P] Preservar a escrita já feita pelo #20 em `src/main/java/br/com/lhamalog/core/occurrence/application/OccurrenceMerge.java`: `recurring` continua cópia de `finding.recurring()`, separado de `status`. Esta feature não muda essa regra. Ausente ou `null` conta como `false` na leitura do detector (T024), não nesta escrita (spec.md, FR-008; data-model.md, Ocorrência; research.md, R8; fonte: respostas-pendentes-llt201.md, pendência 11; origem: data-model da spec 012)
- [x] T007 [P] Preservar em `src/main/java/br/com/lhamalog/core/occurrence/application/AnalysisSummaryDeliveryService.java` a regra de publicar só em `notifyScreens`, depois do recibo confirmado (201). Entrega repetida ou recusada continua sem esse método. Não duplicar a entrega do #20 (spec.md, FR-007; contracts/aviso-ocorrencia.md; spec 015, FR-005 e FR-008, citadas no plan.md, Constraints)

**Checkpoint**: A base do #24 e a cadeia única do #20 seguem intactas. As histórias podem começar.

---

## Phase 3: User Story 1 - O Core aceita o alerta do Finder (Priority: P1) 🎯 MVP

**Goal**: Atender `POST /internal/v1/alertas/finder` no pacote `finderalert`, na cadeia `/internal/v1/**` do #20, com um token cujo `aud` contém a audiência do Módulo e `lhamalog-core`. Gravar um único alerta por `(tenantId, productId, batchId, motivo)` na coleção `finder_alerts`, responder 201 na primeira e 200 na repetição, e, só na primeira gravação, avisar a tela no canal `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`, sem evento novo para a Notificação. (spec.md, US1, FR-001 a FR-006)

**Independent Test**: Chamar a rota com o corpo do alerta, repetir a mesma identidade e conferir 201 depois 200, um único alerta em `finder_alerts`, o corpo sem campo extra, e o aviso `FINDER_ALERT` só na primeira gravação, sem evento novo para a Notificação. (spec.md, US1, Independent Test)

### Decisões aplicadas na história 1

- [x] T008 [US1] Coleção Mongo nova, com índice único em `(tenantId, productId, batchId, motivo)`, distinta da coleção do recibo `ReciboResumoAnalise` e da coleção `Ocorrencia`, sem transação multi-documento, no estilo de `occurrence_unique_key` em `src/main/java/br/com/lhamalog/core/occurrence/infrastructure/OccurrenceDocument.java`. O tipo Java fica em inglês no pacote `src/main/java/br/com/lhamalog/core/finderalert/`. Campos persistidos: `tenantId`, `productId`, `batchId`, `motivo`. `aceito` não é coluna obrigatória. Decisão aplicada: opção A da pendência 7. (fonte: respostas-pendentes-llt201.md, pendência 7; origem: padrão do #20 + DF-FINDER §3; spec.md, FR-003; data-model.md, Alerta do Finder; research.md, R5; origem do estilo de índice: contexto-llt201.md, §2 e R9). O nome da coleção é `finder_alerts`. (fonte: decisoes-joao-llt201.md, Rodada 3). A assertiva de teste que contar documentos usa esse nome, em `src/test/java/br/com/lhamalog/core/finderalert/`.
- [x] T009 [US1] Na resposta de `POST /internal/v1/alertas/finder`, no pacote `src/main/java/br/com/lhamalog/core/finderalert/`, a primeira gravação da chave responde 201 e a repetição responde 200. O corpo de sucesso é exatamente `{aceito: true, tenantId, productId, batchId, motivo}` iguais ao pedido, sem campo `repeticao` e sem outro campo. Decisão aplicada: opção A da pendência 8. (fonte: respostas-pendentes-llt201.md, pendência 8; origem: DF-FINDER §3 + `InternalOccurrenceController.deliver` do #20 + cliente estrito do Finder; spec.md, FR-002; contracts/alerta-finder.md)
- [x] T010 [US1] Quando duas primeiras gravações da mesma chave disputam o índice único, fica um documento. Quem grava responde 201. Quem perde a corrida relê o registro e responde 200 com a mesma identidade, sem segundo documento e sem 409. Decisão aplicada: opção A da pendência 9, com o 200 da repetição da pendência 8. Implementar no pacote `src/main/java/br/com/lhamalog/core/finderalert/`. (fonte: decisoes-joao-llt201.md, pendência 9, formulário 1; elaboração: respostas-pendentes-llt201.md, pendências 8 e 9; spec.md, FR-003; research.md, R6)
- [x] T011 [US1] Depois da primeira gravação durável (201), avisar a tela no canal `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`, sem evento novo e sem envelope para a Notificação. Não publicar este alerta em `lhamalog:occurrence-events` nem em `lhamalog:notifications:events`. A repetição (200) não grava outro alerta e não avisa de novo. O protocolo da ADR_051 continua documento (T041) e pode registrar este contrato já decidido. Decisão aplicada: opção A da pendência 1, com o contrato da Rodada 3. A recomendação D do Especialista não vale. Conflito ADR_165 × "só o aviso do #24": vale o João. Código no pacote `src/main/java/br/com/lhamalog/core/finderalert/`, sem canal Redis novo em `src/main/java/br/com/lhamalog/core/occurrence/stream/`. (fonte: decisoes-joao-llt201.md, pendência 1, formulário 1, e Rodada 3; respostas-pendentes-llt201.md, conflito 1; spec.md, FR-006; research.md, R7; contracts/alerta-finder.md; analyze-016.md, LOW A1)
- [x] T012 [US1] Aceitar só o motivo `consulta_openobserve_indisponivel`. Corpo incompleto, campo a mais ou motivo fora da lista: 422 `{"codigo":"DADOS_INVALIDOS"}`, sem gravação. JSON ilegível: 400 `{"codigo":"REQUISICAO_INVALIDA"}`, sem gravação. Não criar outro código. Reusar os códigos já existentes em `src/main/java/br/com/lhamalog/core/occurrence/application/InternalApiException.java` (`INVALID_DATA`, `INVALID_REQUEST`). A rota continua no pacote `src/main/java/br/com/lhamalog/core/finderalert/`. Decisão aplicada: opção B da pendência 4, com o complemento do padrão do #20. (fonte: respostas-pendentes-llt201.md, pendência 4; origem: DF-FINDER §3 + padrão do #20, spec 013; spec.md, FR-004; data-model.md, validação do pedido; contracts/alerta-finder.md)
- [x] T013 [US1] Conferir se `productId` pertence ao `tenantId` com a mesma checagem das rotas do Módulo: `ProductRepository.findByIdAndTenantId`, como `requireProductOfTenant` em `src/main/java/br/com/lhamalog/core/occurrence/api/InternalOccurrenceController.java`. Se não pertence, responder 403 `{"codigo":"ACESSO_NEGADO"}` (`InternalApiException.ACCESS_DENIED`) e não gravar. Não mover `POST /internal/v1/alertas/finder` para esse controller. A chamada fica no pacote `src/main/java/br/com/lhamalog/core/finderalert/`. Decisão aplicada: opção A da pendência 12. (fonte: decisoes-joao-llt201.md, pendência 12, formulário 1; elaboração: respostas-pendentes-llt201.md, pendência 12; origem do 403: spec 013, FR-016; spec.md, FR-014)
- [x] T014 [P] [US1] Colocar `POST /internal/v1/alertas/finder` no pacote novo `src/main/java/br/com/lhamalog/core/finderalert/` (exemplo `br.com.lhamalog.core.finderalert`). Tipos Java em inglês. Não acrescentar o método em `src/main/java/br/com/lhamalog/core/occurrence/api/InternalOccurrenceController.java`. A segurança não mora nesse pacote: continua na cadeia de T004 e T019. Decisão aplicada: opção C da pendência 15. O João nomeou o pacote `finderalert`. (fonte: decisoes-joao-llt201.md, pendência 15, formulário 1; spec.md, FR-001; research.md, R13; plan.md, Structure Decision)
- [x] T015 [P] [US1] Guardar a audiência `lhamalog-core` na propriedade cujo nome decidido é `core-audience`, usada só na rota de alerta. Ilustração de caminho, não um segundo nome: `lhamalog.occurrence.internal-auth.core-audience`, ao lado da propriedade já existente `lhamalog.occurrence.internal-auth.audience` (padrão `lhamalog-modulo-ocorrencia`) em `src/main/resources/application.properties` e em `src/main/java/br/com/lhamalog/core/occurrence/application/OccurrenceModuleProperties.java`. A propriedade do Módulo continua um valor só. Não transformar essa propriedade em lista. Decisão aplicada: opção B da pendência 6. (fonte: decisoes-joao-llt201.md, pendência 6, formulário 1; spec.md, FR-005; research.md, R4; data-model.md, Credencial interna do alerta)
- [x] T016 [US1] Log de negócio da rota de alerta no formato do princípio VI (`event=<dominio.acao.resultado>`), mesmo com o pacote `finderalert`. Sem segredo, token, corpo do alerta nem trecho de log. Os eventos são exatamente `occurrence.alert.received` (pedido recebido), `occurrence.alert.accepted` (aceite 201), `occurrence.alert.duplicate` (repetição 200) e `occurrence.alert.rejected` (recusa 4xx). Não usar outro literal nem `finder.alert.*`. Decisão aplicada: opção A da pendência 14, com os quatro eventos da Rodada 4. A recomendação B do Especialista não vale. O `cid` vem do filtro de T003. Código no pacote `src/main/java/br/com/lhamalog/core/finderalert/`. (fonte: decisoes-joao-llt201.md, pendência 14, formulário 2, e Rodada 4; spec.md, FR-012; constituição, princípios V e VI; research.md, R12)

### Tests for User Story 1

> Cobertura pedida pela constituição III e por research.md, R14. A contagem de documentos usa a coleção `finder_alerts` (T008).

- [x] T017 [P] [US1] Acrescentar em `src/test/java/br/com/lhamalog/core/occurrence/InternalAuthIntegrationTests.java`, com apoio de `src/test/java/br/com/lhamalog/core/occurrence/InternalTokens.java`, a rejeição e o aceite da rota de alerta (SC-007, FR-005). Sem JWT, JWT inválido ou `aud` sem a audiência exigida na rota responde 401 `{"codigo":"NAO_AUTENTICADO"}` com `WWW-Authenticate: Bearer`. Token de sessão de usuário responde 401 com o mesmo corpo. `aud` `lhamalog-core` com `sub` fora da lista do #20 responde 403 `{"codigo":"ACESSO_NEGADO"}`. `aud` só `lhamalog-modulo-ocorrencia` não autentica `POST /internal/v1/alertas/finder`. `aud` só `lhamalog-core`, com a propriedade `core-audience` aplicada, autentica a rota de alerta e não autentica `POST /internal/v1/ocorrencias/consultas` nem `POST /internal/v1/resumos-analise`. Um token cujo `aud` contém a audiência do Módulo e `lhamalog-core` autentica a rota de alerta e as rotas do Módulo, porque o #20 confere `aud` por "contém". Sem o ajuste por rota, um token só com `lhamalog-core` recebe 401 em todo `/internal/v1/**`. O token das duas audiências é montado no teste; não alterar `scripts/InternalJwt.java`, que gera um token com uma única audiência (`--aud`). Segredo só por ambiente, nunca no teste versionado. Decisão aplicada: opção B da pendência 20 (um token, duas audiências). A recomendação A do Especialista (dois tokens) não vale. (fonte: spec.md, US1 cenário 6, FR-005 e SC-007; contracts/alerta-finder.md, Autenticação; constituição, V; research.md, R4 e R14; decisoes-joao-llt201.md, pendências 6 e 20, formulário 1; respostas-pendentes-llt201.md, resumo, fato de código)
- [x] T018 [US1] Acrescentar em `src/test/java/br/com/lhamalog/core/finderalert/` o teste de aceite (SC-001, SC-005, SC-008, FR-002, FR-003, FR-004, FR-014): motivo `consulta_openobserve_indisponivel` e produto do tenant; primeira resposta 201; repetição 200; corpo exatamente `{aceito: true, tenantId, productId, batchId, motivo}` sem `repeticao` e sem outro campo; duas primeiras chamadas da mesma chave deixam um aceite só pela API (201 para quem gravou, 200 com a mesma identidade para quem releu); motivo fora da lista, corpo incompleto ou campo a mais respondem 422 `{"codigo":"DADOS_INVALIDOS"}` sem gravação; JSON ilegível responde 400 `{"codigo":"REQUISICAO_INVALIDA"}`; produto que não pertence ao tenant responde 403 `{"codigo":"ACESSO_NEGADO"}` sem gravação; a primeira gravação avisa a tela no canal `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`, e não publica em `lhamalog:occurrence-events` nem em `lhamalog:notifications:events`; a repetição (200) não avisa de novo; a contagem de documentos usa a coleção `finder_alerts` (T008); a resposta cabe em 2 s (`FINDER_CORE_TIMEOUT`). Se o POST falha, quem mantém o lote pendente e reenvia é o Finder; o Core, no reenvio, responde por esta deduplicação. (fonte: spec.md, US1, SC-001, SC-005 e SC-008; quickstart.md, seções 1, 2 e 3; research.md, R14; respostas-pendentes-llt201.md, pendências 4, 8, 16 e 19; origem da 19: DF-FINDER §2 e §3; decisoes-joao-llt201.md, pendências 1, 9 e 12, formulário 1)

### Implementation for User Story 1

- [x] T019 [US1] Em `src/main/java/br/com/lhamalog/core/config/InternalServiceSecurityConfiguration.java`, exigir audiência por rota na cadeia única de T004. `POST /internal/v1/alertas/finder` exige `aud` contendo `lhamalog-core`, lida da propriedade `core-audience` de T015. `POST /internal/v1/ocorrencias/consultas` e `POST /internal/v1/resumos-analise` continuam exigindo a audiência do #20 (padrão `lhamalog-modulo-ocorrencia`). Um token cujo `aud` contém as duas audiências autentica as duas, porque a conferência é por "contém". Segredo, `iss` e `sub` permanecem `lhamalog.occurrence.internal-auth.*` (padrões `lhamalog-internal` e `lhamalog-finder`; ambiente `OCCURRENCE_INTERNAL_JWT_SECRETS`; segredo com mínimo 32 bytes, como o filtro do #20 já exige). Não criar segunda cadeia. Não alargar a audiência das rotas do #20 para qualquer uma das duas em toda rota. Não alterar `scripts/InternalJwt.java`. Decisão aplicada: pendências 6 B e 20 B. A recomendação A da pendência 20 (dois tokens) não vale. (fonte: spec.md, FR-005; data-model.md, Credencial interna do alerta; research.md, R4; decisoes-joao-llt201.md, pendências 6 e 20, formulário 1; respostas-pendentes-llt201.md, resumo, fato de código)
- [x] T020 [US1] Gravar o alerta no Mongo, no pacote `src/main/java/br/com/lhamalog/core/finderalert/`, um documento por `(tenantId, productId, batchId, motivo)`, índice único no estilo de `src/main/java/br/com/lhamalog/core/occurrence/infrastructure/OccurrenceDocument.java`, sem transação multi-documento. Não reusar `Ocorrencia` nem `ReciboResumoAnalise`. Quem perde a corrida relê e a rota responde 200 (T010). A coleção é `finder_alerts` (T008). (fonte do nome: decisoes-joao-llt201.md, Rodada 3). (fonte: spec.md, FR-003; data-model.md, Alerta do Finder; research.md, R3 e R5; constituição, Stack; respostas-pendentes-llt201.md, pendência 7; origem: padrão do #20 + DF-FINDER §3; decisoes-joao-llt201.md, pendência 9, formulário 1)
- [x] T021 [US1] Atender `POST /internal/v1/alertas/finder` no pacote de T014. O HTTPS é a URL que o Finder recebe via Caddy; o processo Spring segue em HTTP na porta 8080 (FR-011, T039). Corpo `{tenantId, productId, batchId, motivo}`, sucesso 201/200 de T009, corrida de T010, recusas de T012, 403 de T013, audiência de T019, gravação em `finder_alerts` (T020) e aviso de tela de T011. A rota responde dentro de 2 s (`FINDER_CORE_TIMEOUT`), alvo distinto dos 3 s do aviso de ocorrência (T026). O reenvio depois de falha do POST é a mesma deduplicação: o Finder mantém o lote pendente e reenvia; o Core não cria outro alerta. Não alterar o Finder. (fonte: spec.md, US1, FR-001, FR-002 e FR-004; contracts/alerta-finder.md; research.md, R3 e R6; respostas-pendentes-llt201.md, pendências 8, 16 e 19; origem da 16: DF-FINDER §3 e §8; origem da 19: DF-FINDER §2 e §3; origem do cliente: contexto-llt201.md, R9 e §3, `aviso.go`)
- [x] T022 [US1] Registrar o log da rota no pacote `src/main/java/br/com/lhamalog/core/finderalert/`, com os eventos de T016 (`occurrence.alert.received` no pedido recebido, `occurrence.alert.accepted` no aceite 201, `occurrence.alert.duplicate` na repetição 200, `occurrence.alert.rejected` na recusa 4xx), data com fuso, `cid` do filtro de T003 e função, em INFO de negócio e em WARN/ERROR. Sem segredo, token, corpo do alerta nem trecho de log. Não usar outro literal. (fonte: spec.md, FR-012; constituição, princípios V e VI; research.md, R12; decisoes-joao-llt201.md, pendência 14, formulário 2, e Rodada 4)

**Checkpoint**: A história 1 fica testável no contrato HTTP, na coleção `finder_alerts`, na audiência, no aviso `FINDER_ALERT` e nos quatro eventos de log quando T008, T011, T017, T018, T019, T020 e T022 estiverem implementadas.

---

## Phase 4: User Story 2 - Avisar quando a ocorrência vira recorrente (Priority: P1)

**Goal**: No aviso que o #24 já publica, um aviso por ocorrência por entrega, com a mudança mais forte: nova > reaberta > virou recorrente. Criação já recorrente publica só `CREATED`. FECHADA que reabre e fica recorrente na mesma entrega publica só `REOPENED`. A passagem para recorrente, quando é a mais forte, usa `RECURRENT` em `Change`. Ausente ou `null` conta como `false`. O aviso sai em até 3 s e leva a gravidade. A precedência `CREATED` / `REOPENED` / `RECURRENT` é desta história. A gravação de `gravidade` é a história 3 (T034, T035). (spec.md, US2, FR-006, FR-007 e FR-008)

**Independent Test**: Entregar um resumo que faz uma ocorrência já existente, que não é criação nem reabertura, passar a recorrente e ver um aviso no canal `lhamalog:occurrence-events` e no WebSocket `/ws/occurrences`, em até 3 s, com gravidade igual à classe. Uma entrega que só aumenta a contagem não produz aviso novo. Criação já recorrente publica só `CREATED`. Reabertura que também fica recorrente publica só `REOPENED`. (spec.md, US2, Independent Test)

### Decisões aplicadas na história 2

- [x] T023 [P] [US2] A mudança "virou recorrente" é a constante `RECURRENT` no enum `Change` de `src/main/java/br/com/lhamalog/core/occurrence/application/OccurrenceEvent.java`, no estilo de `CREATED` e `REOPENED`, distinta das duas, fio com `name()`. O listener em `src/main/java/br/com/lhamalog/core/occurrence/stream/RedisOccurrenceEventListener.java` só entrega `change` que exista nesse enum. Decisão aplicada: opção A da pendência 10, com o literal da Rodada 3. (fonte: respostas-pendentes-llt201.md, pendência 10; origem: constituição, princípio IV + enum `Change` do #24; spec.md, FR-007; research.md, R8; literal: decisoes-joao-llt201.md, Rodada 3). Não usar valor em português. Não reusar `CREATED` nem `REOPENED` para essa mudança.
- [x] T024 [P] [US2] Em `src/main/java/br/com/lhamalog/core/occurrence/application/OccurrenceChangeDetector.java`, ausente ou `null` em `recurring` conta como `false`. A passagem "virou recorrente" é quando `before.recurring` não é TRUE e `after.recurring` é TRUE, e essa mudança é a mais forte (T025). Decisão aplicada: opção A da pendência 11. A constante emitida é `RECURRENT` (T023). (fonte: respostas-pendentes-llt201.md, pendência 11; origem: data-model da spec 012, "Ausente = `false`"; spec.md, FR-008; data-model.md, Ocorrência)
- [x] T025 [US2] Em `src/main/java/br/com/lhamalog/core/occurrence/application/OccurrenceChangeDetector.java`, um aviso por ocorrência por entrega, com a mudança mais forte: nova > reaberta > virou recorrente. `before` nulo, inclusive criação já recorrente, publica só `CREATED`. Ocorrência que estava oculta e volta a `ABERTA`, inclusive FECHADA que reabre e fica recorrente na mesma entrega, publica só `REOPENED`. A terceira mudança é `RECURRENT` (T023). Decisão aplicada: opção B da pendência 5. A elaboração alinhada a essa opção, e ao texto que o João repetiu, é a ordem acima. (fonte: decisoes-joao-llt201.md, pendência 5, formulário 1; elaboração: respostas-pendentes-llt201.md, pendência 5; spec.md, FR-007; contracts/aviso-ocorrencia.md; data-model.md, transição)
- [x] T026 [P] [US2] O aviso "virou recorrente" herda o teto de 3 s da spec 015, SC-001: até 3 s depois do 201, pelo mesmo publicador em `src/main/java/br/com/lhamalog/core/occurrence/stream/RedisOccurrenceEventPublisher.java`. Não criar outro número para esse aviso. A rota de alerta, noutro alvo, responde dentro dos 2 s de `FINDER_CORE_TIMEOUT` (T021). Os dois valem. Não há outra meta numérica de vazão. Decisão aplicada: opção A da pendência 16 para o aviso e opção C como complemento para a rota. (fonte: respostas-pendentes-llt201.md, pendência 16; origem: spec 015, SC-001 + DF-FINDER §3 e §8; spec.md, FR-001 e FR-007; research.md, R15; plan.md, Performance Goals)

### Tests for User Story 2

- [x] T027 [US2] Acrescentar em `src/test/java/br/com/lhamalog/core/occurrence/OccurrenceChangeDetectorTest.java` o caso em que `before` não é nulo, a entrega não cria nem reabre, `before.recurring` não é TRUE (ausente ou `null` conta como `false`) e `after.recurring` é TRUE. O `change` é `RECURRENT` (T023). Não reescrever os casos `CREATED` e `REOPENED` já cobertos pelo #24, salvo para afirmar a precedência de T025 nesses literais. A precedência `CREATED` / `REOPENED` / `RECURRENT` é testável nesta história. (fonte: spec.md, US2 cenário 4, SC-002 e FR-008; research.md, R8 e R14; quickstart.md, seção 4; respostas-pendentes-llt201.md, pendências 10 e 11)
- [x] T028 [US2] Acrescentar em `src/test/java/br/com/lhamalog/core/occurrence/AnalysisSummaryDeliveryServiceTest.java` e `src/test/java/br/com/lhamalog/core/occurrence/OccurrenceStreamIntegrationTests.java` a guarda e a precedência, sem reimplementar o publicador do #24: entrega 201 que só aumenta contagem de ocorrência já aberta não publica aviso novo; entrega repetida (200, mesmo recibo) ou recusada não publica aviso, inclusive o de recorrente; criação já recorrente publica só `CREATED`; FECHADA que reabre e fica recorrente na mesma entrega publica só `REOPENED`; falha do Redis não falha a entrega. A precedência `CREATED` / `REOPENED` / `RECURRENT` continua testável nesta história (T027). O trecho que afirma `gravidade` igual à classe, no aviso de `CREATED`, de `REOPENED` e de `RECURRENT`, depende de T034 e T035. O aviso "virou recorrente" cabe em 3 s (T026). (fonte: spec.md, US2 cenários 2, 3, 5 e 6, SC-002 e SC-003; spec 015, FR-005 e FR-008; research.md, R14; contracts/aviso-ocorrencia.md; decisoes-joao-llt201.md, pendência 5, formulário 1; respostas-pendentes-llt201.md, pendências 2, 5 e 16)

### Implementation for User Story 2

- [x] T029 [US2] Acrescentar `RECURRENT` em `src/main/java/br/com/lhamalog/core/occurrence/application/OccurrenceEvent.java` (T023) e, em `src/main/java/br/com/lhamalog/core/occurrence/application/OccurrenceChangeDetector.java`, emitir essa constante só na passagem de T024, quando ela for a mais forte segundo T025, numa entrega que já chegou a `notifyScreens`. `before` nulo continua só `CREATED`. Reabertura continua só `REOPENED`. Não alterar o sentido de `CREATED` nem de `REOPENED`. O listener já faz `Change.valueOf`; não mudar o canal em `src/main/java/br/com/lhamalog/core/occurrence/stream/RedisOccurrenceEventPublisher.java`, `src/main/java/br/com/lhamalog/core/occurrence/stream/RedisOccurrenceEventListener.java` nem `src/main/java/br/com/lhamalog/core/occurrence/stream/OccurrenceConnections.java`. (fonte: spec.md, FR-006, FR-007 e FR-008; data-model.md, Aviso de ocorrência; research.md, R8; plan.md, Structure Decision; decisoes-joao-llt201.md, pendência 5, formulário 1; respostas-pendentes-llt201.md, pendências 5, 10 e 11)
- [x] T030 [US2] Em `src/main/java/br/com/lhamalog/core/occurrence/application/AnalysisSummaryDeliveryService.java`, manter `notifyScreens` como único ponto de publicação e fazer a lista de `OccurrenceChangeDetector` seguir por esse caminho, inclusive `RECURRENT` quando T029 a fornecer, só depois do 201. Não criar segundo `publish`. Entrega repetida ou recusada continua sem o método. Redis indisponível continua sem falhar a entrega (`event=occurrence.stream.publish_failed` já usado pelo #24). O caminho único já vale para `CREATED`, `REOPENED` e `RECURRENT`. (fonte: spec.md, FR-007 e Edge Cases; research.md, R1 e R12; contracts/aviso-ocorrencia.md; spec 015, FR-008)

**Checkpoint**: A precedência `CREATED` / `REOPENED` / `RECURRENT` e o caminho único de publicação ficam testáveis nesta história. O trecho de `gravidade` em T028 depende de T034 e T035.

---

## Phase 5: User Story 3 - A gravidade passa a ser calculada no Módulo (Priority: P2)

**Goal**: O Módulo, dentro do Core, copia a classe ERROR, WARNING ou INFO para o campo `gravidade` e mantém o tipo. O mesmo valor aparece no documento da ocorrência e no aviso do #24. O Finder não calcula. (spec.md, US3, FR-009 e SC-006)

**Independent Test**: Gravar uma ocorrência ERROR, WARNING e INFO e ver `gravidade` igual à classe no documento e no aviso do #24, com o tipo preservado. (spec.md, US3, Independent Test)

### Decisões aplicadas na história 3

- [x] T031 [P] [US3] O valor calculado aparece no documento da ocorrência e no aviso do #24 (Redis `lhamalog:occurrence-events` e WebSocket `/ws/occurrences`). Decisão aplicada: opção C da pendência 2. A spec 015, FR-007 (aviso sem gravidade) fica como emenda pendente (T050). Não editar a spec 015. Arquivos: `src/main/java/br/com/lhamalog/core/occurrence/infrastructure/OccurrenceDocument.java`, `src/main/java/br/com/lhamalog/core/occurrence/application/OccurrenceEvent.java`, `src/main/java/br/com/lhamalog/core/occurrence/stream/RedisOccurrenceEventPublisher.java`, `src/main/java/br/com/lhamalog/core/occurrence/stream/OccurrenceConnections.java`. (fonte: respostas-pendentes-llt201.md, pendência 2; origem: Ocorrência.mmd (Drive) + 009-5 revisto; spec.md, FR-009; plan.md, Conflitos registrados, item 2)
- [x] T032 [US3] A gravidade é a classe: copiar ERROR, WARNING ou INFO para o campo `gravidade` e manter o tipo. Não usar a escala "critico"/"alto". Não calcular criticidade. Quem calcula é o Módulo, no pacote `br.com.lhamalog.core.occurrence`, não o Finder. Decisão aplicada: opção D da pendência 3. Conflito ADR_062 × esta regra: vale o João. O alinhamento da ADR é documento (T051). (fonte: decisoes-joao-llt201.md, pendência 3, formulário 1; respostas-pendentes-llt201.md, conflito 3; spec.md, FR-009 e SC-006; research.md, R9; origem: contexto-llt201.md, R2 e ADR_062)

### Tests for User Story 3

- [x] T033 [P] [US3] Em `src/test/java/br/com/lhamalog/core/occurrence/`, afirmar que `gravidade` é a classe ERROR, WARNING ou INFO, que o tipo permanece, e que o mesmo valor está no documento e no aviso do #24 (SC-006). Não afirmar escala "critico"/"alto". Não criar teste no repositório do Finder. (fonte: spec.md, US3, FR-009 e SC-006; research.md, R9 e R14; quickstart.md, seção 4; decisoes-joao-llt201.md, pendência 3, formulário 1; respostas-pendentes-llt201.md, pendência 2)

### Implementation for User Story 3

- [x] T034 [US3] Acrescentar o campo `gravidade` em `src/main/java/br/com/lhamalog/core/occurrence/infrastructure/OccurrenceDocument.java` (coleção `Ocorrencia`), em `src/main/java/br/com/lhamalog/core/occurrence/application/OccurrenceEvent.java`, na mensagem de `src/main/java/br/com/lhamalog/core/occurrence/stream/RedisOccurrenceEventPublisher.java` (`toMessage`) e na mensagem de `src/main/java/br/com/lhamalog/core/occurrence/stream/OccurrenceConnections.java` (`dispatch`). O campo observável chama-se `gravidade`. Os valores são `ERROR`, `WARNING` ou `INFO`, iguais à classe. `type` / `occurrenceType` permanecem. Sem título, assinatura, trecho de log ou `tenantId` na mensagem da tela. A emenda da constituição para esse nome de campo é T045; não traduzir o campo. (fonte: spec.md, FR-009; data-model.md, Ocorrência e Aviso; contracts/aviso-ocorrencia.md; plan.md, Divergência 2; decisoes-joao-llt201.md, pendência 3, formulário 1; respostas-pendentes-llt201.md, pendência 2)
- [x] T035 [US3] Em `src/main/java/br/com/lhamalog/core/occurrence/application/OccurrenceMerge.java`, ao gravar a ocorrência, copiar a classe (`finding.key().type()`, já escrita em `type`) para `gravidade` e manter `type`. O Finder não calcula. Não calcular criticidade. Não aplicar a matriz da ADR_062. (fonte: spec.md, FR-009 e US3; research.md, R9; decisoes-joao-llt201.md, pendência 3, formulário 1; respostas-pendentes-llt201.md, pendência 2; origem: Ocorrência.mmd + 009-5 revisto)

**Checkpoint**: A história 3 fica testável com `gravidade` igual à classe no documento e no aviso. T028 só afirma essa `gravidade` depois de T034 e T035.

---

## Phase 6: User Story 4 - Homologar o Finder com o Core, inclusive o TLS local (Priority: P2)

**Goal**: Percorrer os cinco cenários do DN-009-2 com o cliente do Finder e as rotas que o #20 já entrega, e chamar `POST /internal/v1/alertas/finder` pelo mesmo cliente, em `https://` via Caddy, com o token das duas audiências (SC-005, US4 cenário 4). No teste local, o Caddy no compose do Core fala HTTPS na frente do Core em HTTP na porta 8080. O Finder só recebe a URL `https://`. (spec.md, US4, FR-010, FR-011 e SC-005)

**Independent Test**: Subir Finder e Core com o Caddy no compose do Core e executar os cinco cenários, sem uma segunda implementação das rotas de consulta e de resumo. (spec.md, US4, Independent Test)

### Decisões aplicadas na história 4

- [x] T036 [P] [US4] O proxy TLS do teste local é o Caddy, no compose do Core, na frente do Core que segue em HTTP na porta 8080. A URL do Finder é `https://`, sem usuário, query, fragmento nem caminho na base. O proxy é do lado do Core. Não terminar TLS no processo Spring. Não escolher outro produto. Decisão aplicada: opção A da pendência 13. (fonte: decisoes-joao-llt201.md, pendência 13, formulário 2; spec.md, FR-011 e US4 cenário 3; research.md, R11; origem: contexto-llt201.md, §7 Q7 opção A e §5 conflito 7)
- [x] T037 [P] [US4] O arquivo do proxy é o compose do Core que esta spec registra: `compose.yaml` na raiz, que declara o serviço `api` com `SERVER_PORT` 8080 e inclui `infra/local/compose.yaml` (Mongo e Redis). O Caddy entra em `compose.yaml`, na frente desse `api`. Não criar um terceiro arquivo de compose. Não colocar o proxy em `infra/local/compose.yaml` nem no Finder. Decisão aplicada: pendência 17, o proxy, o produto e o arquivo são os que a spec 016 registra (Caddy no compose do Core). (fonte: respostas-pendentes-llt201.md, pendência 17; origem: ADR_164 + arquitetura-modulos; decisoes-joao-llt201.md, pendência 13, formulário 2; plan.md, Constraints; spec.md, US4 cenário 3)

### Tests for User Story 4

A homologação não é uma segunda suíte das rotas do #20. O portão de código continua `./mvnw verify` (T046). Os cinco cenários são prova com o cliente do Finder. (fonte: spec.md, FR-010; research.md, R14; constituição, princípio III)

- [ ] T038 [US4] Executar uma vez, com o cliente real do Finder e o Core da pilha mergeada, os cinco cenários do DN-009-2 descritos em `specs/016-alerta-finder-evento-ocorrencia/quickstart.md` (seção 5) e na spec.md, US4 cenário 2, usando só `POST /internal/v1/ocorrencias/consultas` e `POST /internal/v1/resumos-analise` já entregues em `src/main/java/br/com/lhamalog/core/occurrence/api/InternalOccurrenceController.java`. Não criar outra versão dessas rotas. No local, o Finder usa `https://` através do Caddy de T036, T037 e T039 (SC-005): 1) a consulta distingue ocorrência ausente de falha e rejeita outro tenant ou produto; 2) a primeira entrega grava um efeito e retorna 201; reenvio, inclusive depois de reinício do Finder, retorna 200 e o mesmo `reciboId`, sem duplicar; 3) dois lotes na mesma revisão preservam os incrementos; revisão desatualizada num achado rejeita a entrega inteira antes de qualquer escrita; 4) confirmação inválida ou inesperada mantém o lote pendente no Finder; um recibo válido antecede a conclusão do lote; o passo de confirmação da fila é do Finder; 5) o JSON mantém as três classes, só trechos protegidos, `linhas` vazias quando for o caso, e a API rejeita tipos e contadores inválidos. A CA local do teste via `SSL_CERT_FILE` é da spec do Finder (T044), não regra deste repositório. (fonte: spec.md, US4, FR-010 e SC-004; quickstart.md, seções 5 e 6; origem: contexto-llt201.md, §1 e §3, `proposta-dn-009-2.md`; decisoes-joao-llt201.md, pendência 18, formulário 2)
- [ ] T052 [US4] Com o cliente do Finder, chamar `POST /internal/v1/alertas/finder` em `https://` através do Caddy em `compose.yaml` (T036, T037 e T039), com o token cujo `aud` contém a audiência do Módulo e `lhamalog-core` (SC-005, US4 cenário 4, quickstart.md seção 6). A chamada conclui por esse proxy dentro de 2 s (`FINDER_CORE_TIMEOUT`). A primeira aceitação responde 201 e a repetição 200, no contrato da rota em `src/main/java/br/com/lhamalog/core/finderalert/` (T009 e T021). O processo Spring segue em HTTP na porta 8080. Não criar outra implementação da rota. Esta tarefa segue T039 e T021. (fonte: spec.md, US4 cenário 4 e SC-005; decisoes-joao-llt201.md, pendências 13 e 20, formulários 2 e 1; analyze-016.md, MEDIUM G1)

### Implementation for User Story 4

- [x] T039 [US4] Incluir o Caddy em `compose.yaml`, na frente do serviço `api` que segue em HTTP na porta 8080, para o Finder usar base `https://` sem caminho. Não terminar TLS no processo Spring. Não alterar o cliente do Finder. Não colocar segredo no compose. Não editar `infra/local/compose.yaml` para hospedar o proxy. A confiança na CA local (`SSL_CERT_FILE`, CA só no teste) não entra neste arquivo: é da spec do Finder. (fonte: spec.md, FR-011 e SC-005; research.md, R11; quickstart.md, seção 6; decisoes-joao-llt201.md, pendências 13 e 18, formulário 2; respostas-pendentes-llt201.md, pendência 17)

**Checkpoint**: A história 4 fica demonstrável quando o Caddy estiver em `compose.yaml`, os cinco cenários tiverem sido percorridos no cliente do Finder e T052 tiver chamado a rota de alerta em `https://` com o token das duas audiências.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentos que esta lista não cria, portão de merge e o que continua sem número novo de escala.

- [ ] T040 [P] DOCUMENTAÇÃO pendente, sem criar arquivo neste repositório: ADR nova "Módulo Ocorrência integrado ao Core", no Drive e numa branch nova do lhamalog-docs, para registrar que a Ocorrência fica dentro do Core e substituir a parte da ADR_054 que a trata como serviço e repositório separados (spec.md, FR-013; plan.md, Pendências — documentos necessários; fonte: decisoes-joao-llt201.md, registro de que a Ocorrência fica dentro do Core; origem: contexto-llt201.md, §5 conflito 1 e §7 Q8 opção A)
- [ ] T041 [P] DOCUMENTAÇÃO pendente, sem criar arquivo neste repositório: fechar no Drive o trecho pendente da ADR_051 com o protocolo Finder → Core já decidido (`POST /internal/v1/alertas/finder`, URL `https://` via Caddy, JWT, gravar o alerta em `finder_alerts` e, na primeira gravação 201, avisar a tela no canal `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`, sem evento novo para a Notificação). Não escrever a ADR aqui. O contrato já decidido pode ser registrado nesse documento; a história 1 não espera a ADR para usar esse corpo (spec.md, tabela de documentos; plan.md, Pendências — documentos necessários; fonte: decisoes-joao-llt201.md, pendência 1, formulário 1, e Rodada 3; origem: contexto-llt201.md, §8)
- [ ] T042 DOCUMENTAÇÃO pendente, sem editar os arquivos nesta lista: quando a implementação chegar aqui, atualizar `README.md` e `.env.example` na raiz (o `infra/local/.env.example` aponta o modelo da raiz) com a propriedade `core-audience` (audiência `lhamalog-core`, só na rota de alerta), o token único cujo `aud` contém a audiência do Módulo e `lhamalog-core`, e o Caddy em `compose.yaml`, sem segredo no repositório (constituição, princípio V; ambiente `OCCURRENCE_INTERNAL_JWT_SECRETS`). `scripts/InternalJwt.java` continua gerando um token com uma única audiência (`--aud`); o registro das duas audiências fica neste documento, não num segundo token. (fonte: spec.md, FR-011 e tabela de documentos; plan.md, Pendências — documentos necessários; decisoes-joao-llt201.md, pendências 6, 13 e 20, formulários 1 e 2; respostas-pendentes-llt201.md, resumo, fato de código; origem: contexto-llt201.md, §8)
- [ ] T043 [P] DOCUMENTAÇÃO pendente, sem criar arquivo neste repositório nem no lhamalog-docs por esta lista: alinhar `endpoints-ocorrencia.md` (pasta citada `planejamento/LLT-145-endpoints-dashboard/` no contexto-llt201.md, §6), `endpoints-finder.md` e `endpoints-core-notificacao.md` ao DN-009-2 e à rota de alerta. A pasta dos dois últimos nomes não está fixada além do material do PR de documentação citado no plan.md. (fonte: plan.md, Pendências — documentos necessários; spec.md, tabela de documentos; origem: contexto-llt201.md, §6 e §8)
- [ ] T044 [P] DOCUMENTAÇÃO pendente, sem criar arquivo neste repositório: spec da mesma feature no lhamalog-finder. Este `tasks.md` é só o do Core. Não alterar o cliente do Finder. Nessa spec do Finder ficam: se o POST do alerta falha, o lote permanece pendente e o aviso é reenviado no reprocessamento (o Core atende o reenvio pela deduplicação de T021); e a CA local do teste via `SSL_CERT_FILE`, CA só no teste. (fonte: spec.md, Assumptions e Fora de escopo; plan.md, Pendências — documentos necessários; decisoes-joao-llt201.md, rota POST e pendência 18, formulário 2; respostas-pendentes-llt201.md, pendência 19; origem da 19: DF-FINDER §2 e §3)
- [ ] T045 [P] DOCUMENTAÇÃO pendente de governança, sem editar `.specify/memory/constitution.md`: emenda do fluxo de branch (aceitar `LLT-<chave>-<descricao>`, ou exceção para `LLT-201-publicar-evento-de-ocorrencia-nova-ou-atualizada`) e emenda do princípio IV para o contrato HTTP já consumido pelo Finder (`/internal/v1/alertas/finder`, `motivo`, `aceito`, e o DN-009-2) e para o campo observável `gravidade`. Não tratar a divergência como licença permanente. CRITICAL C1 (branch `feature/…`) e C2 (princípio IV) ficam só listados aqui; esta lista não emenda a constituição. (fonte: plan.md, Constitution Check, Divergências 1 e 2, e Complexity Tracking; constituição, princípios IV e Fluxo de desenvolvimento; analyze-016.md, CRITICAL C1 e C2)
- [ ] T046 Rodar `./mvnw verify` na máquina do desenvolvedor, com Testcontainers em tag fixa, depois que o código das histórias existir. O comando cobre o comportamento fechado, inclusive a coleção `finder_alerts`, o literal `RECURRENT` e os eventos de log `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected`. Não usar imagem `latest`. (fonte: constituição, princípio III; plan.md, Testing e Constitution Check; research.md, R14; decisoes-joao-llt201.md, Rodada 4)
- [x] T047 [P] Não adotar meta numérica de escala além do teto já existente de 1000 achados por resumo (`lhamalog.occurrence.delivery.max-findings` em `src/main/java/br/com/lhamalog/core/occurrence/application/OccurrenceModuleProperties.java`). Os tetos desta feature são os de T026: 3 s no aviso "virou recorrente" e 2 s na rota de alerta. Não há outro número nas fontes. (fonte: plan.md, Performance Goals e Scale/Scope; research.md, R15; respostas-pendentes-llt201.md, pendência 16)
- [x] T048 Conferir que segredo de JWT não entrou em código, teste versionado, `README.md` nem `.env.example`, e que log novo não leva token nem trecho de log, nos arquivos que T019 a T022, T029, T030, T034, T035 e T039 tiverem alterado. (fonte: spec.md, FR-012 e Edge Cases; constituição, princípios V e VI)
- [ ] T049 Percorrer `specs/016-alerta-finder-evento-ocorrencia/quickstart.md` como validação. Não tratar como sucesso: coleção diferente de `finder_alerts`, literal de "virou recorrente" diferente de `RECURRENT`, campo `repeticao` na resposta do alerta, segundo aviso `FINDER_ALERT` na repetição 200, literal de log da rota de alerta fora de `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected`, envelope em `lhamalog:notifications:events`, gravidade na escala "critico"/"alto", nem aviso "virou recorrente" somado a `CREATED` ou a `REOPENED` na mesma entrega. (fonte: quickstart.md, seções 1 a 7; spec.md, SC-006)
- [ ] T050 [P] DOCUMENTAÇÃO pendente, sem editar a spec 015 neste repositório: emenda da spec 015, FR-007, para o aviso do #24 passar a levar `gravidade` igual à classe, além dos campos que a 015 já lista. Vale o João. O conflito 009-5 revisto × FR-007 fica registrado; a 015 não é editada aqui. (fonte: respostas-pendentes-llt201.md, pendência 2 e conflito 2; origem: 009-5 revisto; plan.md, Pendências — documentos necessários; spec.md, FR-009)
- [ ] T051 [P] DOCUMENTAÇÃO pendente, sem criar arquivo neste repositório: alinhar a ADR_062 no Drive. A matriz "critico"/"alto" continua pendente na ADR. A regra adotada é gravidade = classe ERROR, WARNING ou INFO. Vale o João (pendência 3 D). Esta pendência não espera o nome da coleção, o literal de `Change` nem o contrato WebSocket do alerta: `finder_alerts`, `RECURRENT` e o aviso `/ws/occurrences` / `FINDER_ALERT` / `{tenantId, productId, batchId, motivo}` já estão fechados e não são o objeto desta ADR. (fonte: decisoes-joao-llt201.md, pendência 3, formulário 1, e Rodada 3; respostas-pendentes-llt201.md, conflito 3; plan.md, Pendências — documentos necessários)

---

## Pendências — documentos necessários

Nenhum destes arquivos é criado por esta lista. As tarefas T040, T041, T042, T043, T044, T045, T050 e T051 só registram a pendência.

| Documento | Onde | Por quê | Tarefa |
|-----------|------|---------|--------|
| ADR nova "Módulo Ocorrência integrado ao Core" | Drive e branch nova no lhamalog-docs | Registrar que a Ocorrência fica dentro do Core e substituir a parte da ADR_054 que a trata como serviço separado. (fonte: decisoes-joao-llt201.md, registro; spec.md, FR-013; origem: contexto-llt201.md, §5 conflito 1 e §7 Q8 opção A) | T040 |
| Fechamento do trecho pendente da ADR_051 | Drive | A rota, o HTTPS via Caddy, o JWT, a coleção `finder_alerts` e o aviso (`/ws/occurrences`, `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`, só no 201) ficaram decididos; a ADR não foi atualizada e pode registrar esse contrato. (fonte: decisoes-joao-llt201.md, pendência 1, formulário 1, e Rodada 3; origem: contexto-llt201.md, §8) | T041 |
| `README.md`, `.env.example` (raiz) e o Caddy em `compose.yaml` | lhamalog-core, na implementação | Propriedade `core-audience`, token único com as duas audiências e proxy TLS local, sem segredo. (fonte: plan.md, Pendências — documentos necessários; decisoes-joao-llt201.md, pendências 6, 13 e 20; constituição, V) | T042, T039 |
| `endpoints-ocorrencia.md`, `endpoints-finder.md`, `endpoints-core-notificacao.md` | lhamalog-docs, material do PR de documentação; a pasta citada para o primeiro é `planejamento/LLT-145-endpoints-dashboard/` (contexto-llt201.md, §6) | Citar o DN-009-2 e a rota de alerta. (fonte: plan.md; origem: contexto-llt201.md, §8) | T043 |
| Spec da mesma feature | lhamalog-finder | A decisão pede specs nos dois repositórios. O POST que falha e a CA via `SSL_CERT_FILE` ficam nessa spec. (fonte: decisoes-joao-llt201.md, rota POST e pendência 18, formulário 2; respostas-pendentes-llt201.md, pendência 19) | T044 |
| Emenda da constituição | `.specify/memory/constitution.md` | Fluxo da branch e princípio IV, inclusive o campo `gravidade`. CRITICAL C1 (branch `feature/…`) e C2 (princípio IV) ficam só listados. Não editar neste comando. (fonte: plan.md, Divergências; analyze-016.md, CRITICAL C1 e C2) | T045 |
| Emenda da spec 015, FR-007 | spec 015, noutra execução | O aviso do #24 passa a levar gravidade. Esta lista não edita a 015. (fonte: respostas-pendentes-llt201.md, pendência 2; origem: 009-5 revisto) | T050 |
| Alinhamento da ADR_062 | Drive | A regra adotada é gravidade = classe. A matriz "critico"/"alto" não foi atualizada na ADR. (fonte: decisoes-joao-llt201.md, pendência 3, formulário 1) | T051 |

Não criar ADR_146 nem documento de envelope novo. O aviso de ocorrência desta feature é o do #24. O alerta do Finder, na primeira gravação (201), avisa a tela no canal `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`. (fonte: decisoes-joao-llt201.md, evento para a Notificação, pendência 1, formulário 1, e Rodada 3; spec.md, última linha da tabela de documentos)

### Pendências residuais sem fonte

Nenhuma. O residual dos sufixos de `event=occurrence.alert.*` fechou: `occurrence.alert.received` (pedido recebido), `occurrence.alert.accepted` (aceite 201), `occurrence.alert.duplicate` (repetição 200) e `occurrence.alert.rejected` (recusa 4xx). T016, T022 e o trecho de log de T046 usam esses quatro eventos. (fonte: decisoes-joao-llt201.md, Rodada 4)

### Conflitos registrados

Não se resolvem de outro modo. Vale o João.

- ADR_165 (ocorrências pela Notificação via stream) × pendência 1 A (gravar o alerta e avisar por WebSocket, sem evento novo para a Notificação). Vale o João. (fonte: respostas-pendentes-llt201.md, conflito 1; decisoes-joao-llt201.md, pendência 1, formulário 1)
- 009-5 revisto (gravidade no aviso) × spec 015, FR-007. Vale o João. A emenda é T050. (fonte: respostas-pendentes-llt201.md, pendência 2 e conflito 2)
- ADR_062 (escala "critico"/"alto", matriz pendente) × gravidade = classe ERROR, WARNING ou INFO. Vale o João (pendência 3 D). O alinhamento é T051. (fonte: decisoes-joao-llt201.md, pendência 3, formulário 1; respostas-pendentes-llt201.md, conflito 3)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependência de código novo.
- **Foundational (Phase 2)**: depende do Setup. Preserva #20 e #24 antes de alterar detector, cadeia ou rota.
- **User Stories (Phase 3+)**: dependem da Phase 2. US1 e US2 são as duas P1 e não dependem uma da outra para o que já está fechado. US3 não depende de US1. O aviso da US2 leva a gravidade da US3 quando as duas estiverem juntas; o trecho de T028 que afirma `gravidade` depende de T034 e T035. A precedência `CREATED` / `REOPENED` / `RECURRENT` continua testável na US2. US4 usa as rotas do #20 no DN-009-2; T052 chama a rota de alerta em `https://` pelo Caddy de T039.
- **Polish (Phase 7)**: T046 e T049 depois do código que existir. T040, T041, T042, T043, T044, T045, T050 e T051 não autorizam criar os documentos nesta lista.

### User Story Dependencies

- **User Story 1 (P1)**: depois da Phase 2. Não depende de US2, US3 nem US4. T008, T018, T020 e T021 usam a coleção `finder_alerts`. T016 e T022 escrevem `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected`.
- **User Story 2 (P1)**: depois da Phase 2. Não depende de US1. T023, T027, T028 e T029 usam `RECURRENT`. O trecho de `gravidade` em T028 depende de T034 e T035.
- **User Story 3 (P2)**: depois da Phase 2. Não espera US1. T034 e T035 aplicam destino e regra já fechados e desbloqueiam a afirmação de `gravidade` em T028.
- **User Story 4 (P2)**: depois da Phase 2. T038 percorre o DN-009-2 nas rotas do #20. T039 inclui o Caddy em `compose.yaml`. T052 chama `POST /internal/v1/alertas/finder` em `https://` com o token das duas audiências e usa a rota de T021.

### Within Each User Story

- Os eventos de log da rota de alerta são `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected`. T016 e T022 escrevem esses quatro. (fonte: decisoes-joao-llt201.md, Rodada 4)
- Teste da constituição III antes do merge. A coleção `finder_alerts` e o literal `RECURRENT` entram nas assertivas de T008, T018, T023 e T027.
- Modelo de alerta: o pacote é `finderalert` e a coleção é `finder_alerts` (T008).

### Parallel Opportunities

- T002 em paralelo com T001.
- T003, T004, T005, T006 e T007 em paralelo entre si, depois do Setup.
- T014 (pacote `finderalert`), T015 (propriedade em arquivos de configuração) e T017 (teste de audiência) em paralelo, porque os arquivos são diferentes. T009 a T013, T016 e T021 escrevem a rota nesse pacote e seguem T014 no mesmo código. T008 e T020 gravam em `finder_alerts`. T016 escreve os quatro eventos `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected`.
- T017 em paralelo com T019, porque o contrato de autenticação já está fechado e o arquivo de teste é outro.
- Depois da Phase 2, US1, US2, US3 e US4 podem ser tocadas por pessoas diferentes.
- T023, T024 e T026 em paralelo com a precedência de T028 (arquivos diferentes). T025 segue T024 no mesmo `OccurrenceChangeDetector.java`. O trecho de `gravidade` em T028 espera T034 e T035.
- T031 e T033 em paralelo (aviso/documento contra o teste). T032 e T034 seguem nos mesmos arquivos do documento e do aviso. T035 grava `gravidade` em `OccurrenceMerge.java` depois de T034.
- T036 e T037 em paralelo; T039 junta as duas em `compose.yaml`. T052 chama `POST /internal/v1/alertas/finder` em `https://` depois de T039 e de T021, com o token das duas audiências e o teto de 2 s.
- T040, T041, T043, T044, T045, T047, T050 e T051 em paralelo. T042 espera a implementação da propriedade e do Caddy para descrever o que ficou no repositório, e não cria esses arquivos nesta lista.

---

## Parallel Example: User Story 1

```bash
# Contrato já fechado, arquivos diferentes:
Task: "T015 propriedade core-audience em OccurrenceModuleProperties.java e application.properties"
Task: "T017 InternalAuthIntegrationTests.java um token com as duas audiências"
Task: "T014 pacote src/main/java/br/com/lhamalog/core/finderalert/"

Task: "T008 coleção finder_alerts"
```

## Parallel Example: User Story 2

```bash
Task: "T024 recurring ausente ou null como false em OccurrenceChangeDetector.java"
Task: "T026 teto de 3 s no aviso e de 2 s na rota"
Task: "T028 precedência CREATED / REOPENED / RECURRENT; a gravidade espera T034 e T035"

Task: "T023 RECURRENT em OccurrenceEvent.java"
```

## Parallel Example: User Story 3

```bash
Task: "T031 gravidade no documento e no aviso do #24"
Task: "T033 teste em src/test/java/br/com/lhamalog/core/occurrence/"
```

## Parallel Example: User Story 4

```bash
Task: "T036 Caddy no compose do Core"
Task: "T037 arquivo compose.yaml na frente do serviço api"

# Em seguida, nesta ordem: T039 grava o Caddy em compose.yaml; T052 chama a rota de alerta.
Task: "T039 Caddy em compose.yaml na frente do serviço api em HTTP na porta 8080"
Task: "T052 POST /internal/v1/alertas/finder em https:// com o token das duas audiências, dentro de 2 s"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 e Phase 2.
2. Phase 3, história 1. Audiência, pacote `finderalert`, coleção `finder_alerts`, 201/200, corrida, motivo, 403, aviso `FINDER_ALERT` e os eventos `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected` já têm decisão. (fonte: decisoes-joao-llt201.md, Rodada 4)
3. Validar a história 1 pelo Independent Test da spec (201, 200, um aceite em `finder_alerts`, corpo sem campo extra, aviso `FINDER_ALERT` só no 201, sem evento novo para a Notificação).
4. US2, US3 e US4 são incrementos seguintes. US2 também é P1 e não espera US1. O literal de "virou recorrente" é `RECURRENT`.

### Incremental Delivery

1. Setup + Foundational.
2. US1, com a gravação em `finder_alerts` e o aviso `FINDER_ALERT` só no 201.
3. US2, com a precedência `CREATED` / `REOPENED` / `RECURRENT`.
4. US3 copia a classe para `gravidade` no documento e no aviso e libera o trecho de `gravidade` de T028.
5. US4 homologa as rotas do #20, inclui o Caddy em `compose.yaml` e chama a rota de alerta em `https://` (T052).

### Parallel Team Strategy

1. Todos fazem Phase 1 e Phase 2.
2. Em seguida:
   - Quem pega US1 faz T008, T014, T015, T017, T019, T020 e T022. T022 escreve os quatro eventos de T016.
   - Quem pega US2 faz T023, T024, T025, T028 e T030, com `RECURRENT`. O trecho de `gravidade` em T028 espera a US3.
   - Quem pega US3 faz T033, T034 e T035.
   - Quem pega US4 inclui o Caddy em `compose.yaml`, percorre os cinco cenários no cliente do Finder e executa T052.

---

## Notes

- [P] = arquivos diferentes, sem depender de tarefa ainda aberta.
- História no rótulo só nas fases US1–US4.
- T008, T020 e T021 gravam na coleção `finder_alerts`.
- T023, T027 e T029 usam o literal `RECURRENT`.
- T016 e T022 escrevem `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected`. (fonte: decisoes-joao-llt201.md, Rodada 4)
- T052 chama a rota de alerta em `https://` via Caddy, com o token das duas audiências.
- Não recriar #19, #20, #21 nem #24. Não criar envelope ADR_146.
- Não marcar tarefa como feita nesta lista.
- Identificadores Java novos em inglês (constituição, IV), no pacote `finderalert`. O caminho e o JSON do alerta permanecem os do Finder. O campo observável `gravidade` permanece esse nome (plan.md, Divergência 2).
- Rastreio de origem anterior conservado nas tarefas que já citavam contexto-llt201.md, a pilha #19–#24 e os arquivos do #20 e do #24.

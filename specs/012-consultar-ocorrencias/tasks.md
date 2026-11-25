# Tasks: Consultar ocorrências (lista, filtros e detalhe)

**Input**: artefatos em `specs/012-consultar-ocorrencias/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: obrigatórios (constituição III). Em cada história, escrever os testes primeiro e vê-los falhar.

**Organization**: por história da spec. US1, US2 e US3 são P1; US4 é P2, mas a T008 (fixture de documentos) serve aos testes desde o início.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência)
- **[Story]**: história da spec (US1 a US4)

## Phase 1: Setup

- [X] T001 Criar o pacote `src/main/java/br/com/lhamalog/core/occurrence/` com as pastas `api`, `application` e `infrastructure`, e a pasta de teste `src/test/java/br/com/lhamalog/core/occurrence/`

---

## Phase 2: Foundational (bloqueia as histórias)

- [X] T002 [P] Criar `OccurrenceType` (ERROR, WARNING, INFO) e `OccurrenceStatus` (ABERTA, EM_ANALISE, FECHADA) em `occurrence/application/`, cada um com `parse(String)` que devolve `Optional` sem lançar exceção
- [X] T003 [P] Criar `OccurrenceDocument` em `occurrence/infrastructure/OccurrenceDocument.java`: `@Document("Ocorrencia")`, campos do data-model revisado em 22/11 (`type` e `status` como `String`, para ler valores desconhecidos sem erro; `samples` como `List<String>`; `hostName`, `traceId`, `correlationId`, `moduleId`, `lineNumber`, `extractedFields`, `sensitiveData`, `summary`, `rulesVersion` e `revision`), record aninhado `StatusChange` (com `ChangedBy`), e os índices `occurrence_tenant_product_last_seen` e `occurrence_unique_key` (único) por `@CompoundIndexes`
- [X] T004 [P] Criar `OccurrenceRecord` em `occurrence/application/OccurrenceRecord.java`: a ocorrência válida, com tipos fortes (`OccurrenceType`, `OccurrenceStatus`, `Instant`), mais `samples`, os campos técnicos e `statusHistory`
- [X] T004a [P] Escrever `OccurrenceSignatureTest.java` e criar `OccurrenceSignature` em `occurrence/application/`: separar `nível|mensagem|serviço|host` respeitando `\|`; título de reserva = mensagem (ou a assinatura inteira se não houver quatro partes); serviço = terceira parte ou vazio (research R13)
- [X] T005 Criar a porta `OccurrenceQueryRepository` em `occurrence/application/OccurrenceQueryRepository.java` com `Page page(String tenantId, Collection<String> productIds, OccurrenceFilter filter)` (itens + total) e `Optional<OccurrenceRecord> findById(String tenantId, String id)`, mais o resultado `Invalid` para o detalhe distinguir documento inválido (depende de T002, T004)
- [X] T006 Criar `OccurrenceResponses` em `occurrence/api/OccurrenceResponses.java` com os records do contrato: `Page(items, page, size, totalItems)`, `ListItem`, `ProductRef(id, name)`, `Detail` (com `summary`, `samples` como `List<String>` e `sensitiveData`), `AttributeView(name, value)`, `StatusChangeView(from, to, changedAt, changedBy)` e `ChangedByView(type, id)`
- [X] T007 Criar o esqueleto de `OccurrenceController` (`@RestController`, `@RequestMapping("/api/occurrences")`) e de `OccurrenceService` com o `ProductService` e o `CompanySessionResolver` injetados, sem as rotas ainda
- [X] T008 Criar a fixture de teste `OccurrenceFixtures` em `src/test/java/br/com/lhamalog/core/occurrence/OccurrenceFixtures.java`, que grava documentos `Ocorrencia` pelo `MongoTemplate` (tenant, produto, assinatura no formato do Finder, tipo, status, título e resumo opcionais, datas, contagem, exemplos, campos técnicos, histórico) e permite gravar documento inválido (sem `status`, `type` desconhecido)

**Checkpoint**: modelo, porta, respostas e fixture prontos.

---

## Phase 3: User Story 1 - Listar ocorrências com paginação (P1) 🎯 MVP

**Goal**: `GET /api/occurrences` com página, tamanho, ordem, total e regra de acesso (FR-001 a FR-005, FR-019).

**Independent Test**: quickstart Q1, Q2, Q3, Q12.

### Tests

- [X] T009 [P] [US1] Em `OccurrenceFilterTest.java`: página padrão 1 e tamanho padrão 20; tamanho 10, 20 e 50 aceitos; tamanho 15 e página 0 recusados com a mensagem do contrato em `fieldErrors`
- [X] T010 [US1] Em `OccurrenceQueryIntegrationTests.java`: (a) dono vê ocorrências de todos os produtos, inclusive arquivado, ordenadas por `lastSeenAt` desc com desempate por `id` desc; (b) membro com acesso a um produto vê só ele e o total conta só ele; (c) percorrer as páginas com `size=10` devolve cada ocorrência uma vez; (d) página além da última dá `items` vazio e o total real; (e) empresa sem ocorrências e membro sem produtos dão `items` vazio e total 0; (f) ocorrência de outra empresa nunca aparece; (g) `size=15` dá 400 com `fieldErrors.size`; (h) documento inválido fica fora da lista e do total; (i) ocorrência sem `title` aparece com a mensagem da assinatura

### Implementation

- [X] T011 [US1] Implementar `OccurrenceFilter.parse(...)` em `occurrence/application/OccurrenceFilter.java` para `page` e `size`, juntando os erros num `FieldValidationException`
- [X] T012 [US1] Implementar `MongoOccurrenceQueryRepository.page` em `occurrence/infrastructure/MongoOccurrenceQueryRepository.java`: `tenantId`, `productId $in`, critérios de documento válido (R7), ordem `lastSeenAt` desc e `_id` desc, `skip`/`limit`, `count` com o mesmo `Criteria`, conversão para `OccurrenceRecord`
- [X] T013 [US1] Implementar `OccurrenceService.list(session, filter)`: produtos visíveis = `ProductService.list(session, false)` + `list(session, true)`; sem produtos visíveis, página vazia sem consultar a coleção; nomes dos produtos na resposta
- [X] T014 [US1] Expor `GET /api/occurrences` no `OccurrenceController` com `page` e `size` como `String` opcionais, delegando ao `OccurrenceFilter`
- [X] T015 [US1] Rodar `OccurrenceFilterTest` e `OccurrenceQueryIntegrationTests` e deixar a US1 verde

**Checkpoint**: lista paginada funcionando e testada.

---

## Phase 4: User Story 2 - Filtrar e buscar (P1)

**Goal**: filtros combinados de período, tipo, status, produto e texto (FR-006 a FR-010).

**Independent Test**: quickstart Q4 a Q9.

### Tests

- [X] T016 [P] [US2] Em `OccurrenceFilterTest.java`: `type` e `status` repetidos aceitos; valor desconhecido recusado com a mensagem do contrato; `from`/`to` inválidos recusados; `from` depois de `to` recusado em `fieldErrors.to`; `q` com 201 caracteres recusado; `q` vazio ou só espaços vira "sem busca"
- [X] T017 [US2] Em `OccurrenceQueryIntegrationTests.java`: (a) tipos e status combinados; (b) período "aconteceram no período", com casos de borda (só início, só fim, ocorrência que começou antes e terminou dentro); (c) filtro por dois produtos; (d) produto de outra empresa ou sem acesso no filtro dá 404 com "Produto não encontrado ou sem acesso."; (e) busca no título, no resumo e na assinatura sem diferenciar maiúsculas (inclusive ocorrência sem título achada pela mensagem da assinatura); (f) busca pelo `id` exato; (g) busca com `(`, `.` e `*` não quebra e trata como texto; (h) filtros mantidos na página 2 (itens e total); (i) filtros que nada satisfaz dão total 0; (j) sem filtros = lista padrão

### Implementation

- [X] T018 [US2] Completar `OccurrenceFilter.parse(...)` com `type`, `status`, `productId`, `from`, `to` (ISO-8601 com fuso, `OffsetDateTime`) e `q` (trim, até 200 caracteres)
- [X] T019 [US2] Em `OccurrenceService.list`, recusar com 404 (`ResponseStatusException`, mensagem do contrato) quando algum `productId` do filtro não estiver entre os visíveis; senão, usar a interseção
- [X] T020 [US2] Em `MongoOccurrenceQueryRepository.page`, aplicar `type $in`, `status $in`, `firstSeenAt <= to`, `lastSeenAt >= from` e o texto como `$or` de `title`/`summary`/`signature` com `Pattern.quote` e `CASE_INSENSITIVE`, mais `_id` igual ao texto
- [X] T021 [US2] Receber `type`, `status`, `productId` (repetíveis), `from`, `to` e `q` no `OccurrenceController`
- [X] T022 [US2] Rodar os testes e deixar a US2 verde

**Checkpoint**: filtros combinados funcionando.

---

## Phase 5: User Story 3 - Ver o detalhe (P1)

**Goal**: `GET /api/occurrences/{id}` com exemplos, atributos e histórico, e 404 sem revelar existência (FR-011 a FR-015).

**Independent Test**: quickstart Q10, Q11, Q14.

### Tests

- [X] T023 [US3] Em `OccurrenceQueryIntegrationTests.java`: (a) detalhe com todos os campos do contrato; (b) 7 exemplos gravados viram os 5 últimos, do mais novo para o mais antigo; (c) linha de 600 caracteres volta com 512; (d) atributos em ordem alfabética, com `service.name` da assinatura, `host.name` e as chaves de `extractedFields`, sem atributos vazios; (e) histórico em ordem cronológica com `from: null` na criação; (f) sem exemplos, resumo e histórico → listas vazias, `summary` `null` e só `host.name`/`service.name` nos atributos; (f2) `sensitiveData` `true`, `false` e `null`; (g) outra empresa, produto sem acesso e id inexistente → 404 com a mesma mensagem; (h) documento inválido → 404 e WARN `event=occurrence.read.invalid` capturado no log (OutputCaptureExtension), sem o título nem as linhas

### Implementation

- [X] T024 [US3] Implementar `MongoOccurrenceQueryRepository.findById(tenantId, id)`: lê sem os critérios de validade e devolve válido ou `Invalid` com o motivo (campo ausente, tipo ou status desconhecido)
- [X] T025 [US3] Implementar `OccurrenceService.detail(session, id)`: 404 se não existe, se o produto não é visível ou se é inválido (com o WARN do plano); título pelo `OccurrenceSignature` quando faltar; os 5 últimos exemplos em ordem inversa, cortados em 512 caracteres; atributos montados conforme o data-model e ordenados por nome
- [X] T026 [US3] Expor `GET /api/occurrences/{id}` no `OccurrenceController`
- [X] T027 [US3] Rodar os testes e deixar a US3 verde

**Checkpoint**: lista, filtros e detalhe completos (MVP da LLT-135 no Core).

---

## Phase 6: User Story 4 - Carga de exemplo local (P2)

**Goal**: ocorrências de exemplo no perfil local, sem duplicar e nunca em produção (FR-017).

**Independent Test**: quickstart preparação e Q13.

### Tests

- [X] T028 [US4] Em `OccurrenceQueryIntegrationTests.java` (ou classe própria): chamar o `LocalOccurrenceSampleSeeder` duas vezes para dois produtos e conferir 12 ocorrências por produto, os três tipos, os três status, recorrentes, exemplos com `<EMAIL>`/`<TOKEN>`, e nenhuma duplicata na segunda chamada; conferir que o bean não existe sem o perfil `local`

### Implementation

- [X] T029 [US4] Implementar `LocalOccurrenceSampleSeeder` em `occurrence/infrastructure/`: para cada `Produto`, 12 documentos com `_id` `sample-<productId>-<n>` por `upsert` com `setOnInsert`, no formato do data-model revisado (assinaturas `nível|mensagem|serviço|host`, algumas sem `title`/`summary`, `samples` como textos), datas relativas ao momento da carga, log `INFO event=occurrence.sample.seeded`
- [X] T030 [US4] Registrar o bean e um `ApplicationRunner` no `LocalDevConfig`, condicionados a `lhamalog.occurrences.sample-data.enabled`; incluir `lhamalog.occurrences.sample-data.enabled=${OCCURRENCE_SAMPLE_DATA:false}` em `application-local.properties`
- [X] T031 [US4] Rodar os testes e deixar a US4 verde

---

## Phase 7: Polish

- [X] T032 [P] Documentar no `README.md` a seção "Ocorrências": rotas, parâmetros e a carga local (`OCCURRENCE_SAMPLE_DATA`)
- [X] T033 Rodar `.\mvnw.cmd verify` com o Docker ligado (Testcontainers) e registrar o resultado no fim deste arquivo
- [X] T034 Executar o roteiro do [quickstart.md](quickstart.md) (Q1 a Q14) contra o Core local e registrar o resultado
- [X] T035 [P] Medir o SC-004: gravar 10.000 ocorrências pela `OccurrenceFixtures` num tenant de teste e cronometrar a primeira página sem filtros, com filtros e com busca; registrar os tempos no fim deste arquivo
- [ ] T036 Revisão do Felipe: conferir cada arquivo contra a spec e o research (constituição II) e combinar o contrato `ocorrencia-documento.md` com quem for implementar o Módulo Ocorrência e com o João (finder)

---

## Dependencies & Execution Order

- Setup (T001) → Foundational (T002 a T008) → US1 → US2 → US3; US4 depende só da Foundational e pode correr junto de US2/US3.
- Dentro de cada história: testes primeiro (falhando), depois implementação, depois a rodada verde.
- US2 estende arquivos da US1 (`OccurrenceFilter`, repositório, controller); por isso vem depois.
- O web (spec 008 do lhamalog-web) pode começar quando o contrato `contracts/api.md` estiver fechado; a integração real precisa de US1 a US3 e da US4 no ambiente local.

### Parallel Opportunities

- T002, T003 e T004 em paralelo; T009 e T016 (teste unitário) em paralelo com os testes de integração da mesma fase.
- T032 e T035 em paralelo com T033.

## Implementation Strategy

1. MVP: Setup + Foundational + US1 → lista paginada demonstrável.
2. US2 e US3 completam a LLT-204 e a LLT-205.
3. US4 deixa o web testável sem o Módulo Ocorrência.
4. Polish: README, verify, roteiro manual e revisão.

## Resultados

### T015, T022, T027, T031 e T033 (25/11/2026)

`.\mvnw.cmd verify` com o Docker Desktop 29.8.1 ligado, depois do merge da `Development` com a LLT-120: **BUILD SUCCESS, 466 testes, 0 falhas, 0 erros, 0 ignorados**, em 1,8 min. Da feature: `OccurrenceFilterTest` 7, `OccurrenceSignatureTest` 5, `OccurrenceQueryIntegrationTests` 17 (US1 a US4) e `LocalDevConfigProfileTests` 2.

Na primeira rodada, dois testes de integração falharam e foram corrigidos:

- `sampleSeederOnlyExistsInTheLocalProfile` supunha que os testes não rodam no perfil `local`, mas `spring.profiles.default=local` vale para eles também; ali o seeder existe e fica desligado pela flag. A verificação de perfil foi para o `LocalDevConfigProfileTests` (local registra `LocalOccurrenceSampleSeeder` e `localOccurrenceSampleRunner`; prod não registra nenhum dos dois).
- `detailHasEveryPartOfTheContract`: o `insert` mapeado do Spring Data recusa chave de mapa com ponto (`http.route`). A `OccurrenceFixtures` passou a gravar `extractedFields` com `Update.set`, como o seeder; a leitura do documento com chaves com ponto funciona sem mudar o código de produção.

### T035 — SC-004 (25/11/2026)

`OccurrencePerformanceTests`, que só roda sob demanda: `.\mvnw.cmd test -Dtest=OccurrencePerformanceTests -Doccurrence.performance=true`. Um tenant com 3 produtos e 10.000 ocorrências gravadas pela `OccurrenceFixtures` (8,8 s de carga), Mongo do Testcontainers no Docker Desktop, requisição completa pelo `MockMvc` (segurança, service, consulta e JSON). Uma chamada de aquecimento e 5 medidas por caso; o teste falha se a mais lenta passar de 1 s.

| Caso | Parâmetros | `totalItems` | Medidas (ms) | Mediana | Máximo |
|---|---|---|---|---|---|
| Sem filtros | — | 10.000 | 44, 45, 44, 45, 42 | 44 ms | 45 ms |
| Com filtros | `type=ERROR&status=ABERTA&productId=<1 produto>&from&to` (30 dias) | 686 | 30, 27, 30, 28, 27 | 28 ms | 30 ms |
| Com busca | `q=timeout` | 200 | 86, 55, 55, 51, 47 | 55 ms | 86 ms |

Todos bem abaixo de 1 s. A busca é a mais cara porque o regex sem âncora percorre os documentos do tenant; com volumes muito maiores, o caminho é um índice de texto, fora desta entrega.

### T034 — roteiro do quickstart (25/11/2026)

Rodado por HTTP contra um Core de teste na porta 8081 (o jar desta branch, perfil `local`, mesmo Mongo e Redis do compose, `OCCURRENCE_SAMPLE_DATA=true`), com `CLERK_ISSUER` apontando para um emissor de tokens local que assina tokens no formato v2 da Clerk. Só para teste: o agente não entra na Clerk. Tenant da organização de desenvolvimento `org_3MFF…sgq7`, com 2 produtos e 24 ocorrências de exemplo.

| # | Resultado |
|---|---|
| Q1 | OK: 200, 20 itens, `lastSeenAt` decrescente, `totalItems` 24 (12 × 2 produtos) |
| Q2 | OK: `page=2&size=10` traz os itens 11 a 20 da mesma ordem |
| Q3 | OK: `size=15` → 400 `fieldErrors.size` "Use 10, 20 ou 50." |
| Q4 | OK: ERROR + ABERTA + 1 produto → 2 itens, todos coerentes |
| Q5 | OK: `q=TIMEOUT` → 2 itens (pelo resumo "Timeout na conexão…") |
| Q6 | OK: `q=(` → 200, sem erro de regex |
| Q7 | OK: `from` = hoje 00:00 → 16 itens, todos com `lastSeenAt` de hoje |
| Q8 | OK: `from` amanhã e `to` hoje → 400 `fieldErrors.to` |
| Q9 | OK: produto da empresa A pedido pela outra empresa → 404 "Produto não encontrado ou sem acesso." |
| Q10 | OK: os 24 detalhes com até 5 `samples`, `host.name` e `service.name` nos atributos e histórico; sem título → mensagem da assinatura |
| Q11 | OK: detalhe pedido pela outra empresa → 404 "Ocorrência não encontrada ou sem acesso." |
| Q12 | OK, por equivalência: empresa sem produtos visíveis → 200 com 0 itens; o membro com 1 produto vê só os 12 dele e recebe 404 no detalhe do outro produto. Não havia membro sem nenhum produto na base local |
| Q13 | OK: duas novas subidas com a carga ligada registraram `inserted=0` |
| Q14 | OK: sem `status`, a ocorrência some da lista (total 23) e o detalhe dá 404 com WARN `event=occurrence.read.invalid`; o `status` foi devolvido depois (total 24) |

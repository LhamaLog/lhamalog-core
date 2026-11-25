# Tasks: Ciclo de vida da ocorrência (alterar status e arquivar)

**Input**: artefatos em `specs/014-ciclo-de-vida-ocorrencias/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md; specs 012 e 013 (branch `LLT-293-…`)

**Tests**: obrigatórios (constituição III). Em cada história, escrever os testes primeiro e vê-los falhar.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Foundational

- [ ] T001 [P] Acrescentar `archivedAt` e `archivedBy` (record `ChangedBy`) em `OccurrenceDocument`, e `revision`, `archivedAt`, `archivedBy` em `OccurrenceRecord`; ler os campos novos no `MongoOccurrenceQueryRepository` (documento sem `revision` → `sem-revisao`)
- [ ] T002 [P] Acrescentar `archived` em `OccurrenceResponses.ListItem` e `revision`, `archived`, `archivedAt`, `archivedBy` em `OccurrenceResponses.Detail`, preenchidos pelo `OccurrenceService`
- [ ] T003 [P] Acrescentar `OCCURRENCES_MANAGE` em `access/application/Permission.java` (OWNER, ADMIN, MEMBER, nota "MEMBER só nos produtos liberados") e conferir o `AccessPolicyTest`/`AccessHttpTests` da LLT-120 com a permissão nova
- [ ] T004 Criar a porta `OccurrenceCommandRepository` em `occurrence/application/` e `MongoOccurrenceCommandRepository` em `occurrence/infrastructure/`: `changeStatus(tenantId, id, expectedRevision, status, change, newRevision)`, `archive(tenantId, id, expectedRevision, at, by, newRevision)` e `unarchive(tenantId, id, expectedRevision, newRevision)`, cada um um `updateOne` condicional (`_id`, `tenantId`, `revision`, com `sem-revisao` = sem campo) que devolve se alterou

**Checkpoint**: modelo e respostas prontos; spec 012 continua verde.

---

## Phase 2: User Story 1 - Alterar o status (P1) 🎯 MVP

### Tests

- [ ] T005 [US1] Escrever `OccurrenceLifecycleIntegrationTests.java` (parte US1): mudança ABERTA → EM_ANALISE com histórico (USER, id, data) e revisão nova; FECHADA → ABERTA; mesmo status sem histórico novo e com a mesma revisão; `RESOLVIDA` → 400 `fieldErrors.status`; revisão vazia → 400; revisão antiga → 409 `occurrence_changed` sem mudança; outra empresa, membro sem acesso e id inexistente → 404; membro com acesso consegue; filtro `status` da lista reflete a mudança; uma entrega do Finder com a revisão de antes da mudança → `409 REVISAO_DESATUALIZADA`

### Implementation

- [ ] T006 [US1] Criar `ChangeStatus(status, revision)` e a rota `PUT /api/occurrences/{id}/status` no `OccurrenceController`, com validação em `FieldValidationException` (research R1)
- [ ] T007 [US1] Implementar `OccurrenceService.changeStatus`: detalhe visível (mesma regra do `detail`), status igual → devolve o detalhe; senão `changeStatus` condicional com `{from, to, agora, USER, userId}` e revisão nova; zero alterados → 404 ou 409 (research R2); log `occurrence.status.changed` ou `occurrence.action.stale` com `userHash`
- [ ] T008 [US1] Rodar a US1 verde

---

## Phase 3: User Story 2 - Arquivar e desarquivar (P2)

### Tests

- [ ] T009 [P] [US2] Acrescentar `archived` ao `OccurrenceFilterTest` (`true`, `false`, ausente, inválido → 400)
- [ ] T010 [US2] Acrescentar a `OccurrenceLifecycleIntegrationTests`: arquivar (data e autor, status igual, revisão nova), some da lista padrão e do total e aparece com `archived=true` combinando com filtros e paginação; desarquivar volta com o mesmo status; arquivar de novo e desarquivar ativa → 200 sem mudança; detalhe de arquivada → 200 com `archived: true`; 409 e 404 como na US1

### Implementation

- [ ] T011 [US2] Acrescentar `archived` ao `OccurrenceFilter` (padrão `false`, inválido → `fieldErrors.archived`) e ao `MongoOccurrenceQueryRepository` (`archivedAt` ausente/nulo ou presente) e ao parâmetro da rota de lista
- [ ] T012 [US2] Criar `ChangeArchive(revision)`, as rotas `POST /api/occurrences/{id}/archive` e `/unarchive` e `OccurrenceService.archive`/`unarchive` (research R4), com logs `occurrence.archived`/`occurrence.unarchived`
- [ ] T013 [US2] Rodar a US2 verde

---

## Phase 4: User Story 3 - Achado novo desarquiva e reabre (P2)

- [ ] T014 [P] [US3] Acrescentar ao `OccurrenceMergeTest`: arquivada EM_ANALISE → sem arquivamento, ABERTA, uma entrada `EM_ANALISE → ABERTA` SYSTEM; arquivada FECHADA → uma entrada só; arquivada ABERTA → desarquiva sem entrada; ativa EM_ANALISE continua igual
- [ ] T015 [US3] Ajustar `OccurrenceMerge` (research R5)
- [ ] T016 [US3] Acrescentar a `AnalysisSummaryDeliveryIntegrationTests`: arquivar pela rota de usuário, consultar pelo Finder, entregar um achado → a ocorrência volta para a lista padrão como ABERTA; rodar a US3 verde

---

## Phase 5: Polish

- [ ] T017 [P] Atualizar o README (seção "Ocorrências") e os contratos `specs/012-consultar-ocorrencias/contracts/api.md` (parâmetro `archived`, campos novos) e `ocorrencia-documento.md` (`archivedAt`, `archivedBy` e a regra de desarquivar)
- [ ] T018 Rodar `.\mvnw.cmd verify` com o Docker ligado e registrar o resultado no fim deste arquivo
- [ ] T019 Executar o roteiro C1 a C11 do quickstart contra o Core local e registrar o resultado
- [ ] T020 Revisão do Felipe

## Dependencies & Execution Order

- Foundational (T001–T004) → US1 (T005–T008) → US2 (T009–T013) → US3 (T014–T016) → Polish.
- US3 só depende da T001 (campos) e pode correr junto da US2.
- O web (spec `013-ciclo-de-vida-ocorrencias` do lhamalog-web) depende do contrato `contracts/api.md`.

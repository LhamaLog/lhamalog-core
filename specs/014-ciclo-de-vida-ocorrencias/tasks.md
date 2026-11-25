# Tasks: Ciclo de vida da ocorrência (alterar status e arquivar)

**Input**: artefatos em `specs/014-ciclo-de-vida-ocorrencias/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md; specs 012 e 013 (branch `LLT-293-…`)

**Tests**: obrigatórios (constituição III). Em cada história, escrever os testes primeiro e vê-los falhar.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Foundational

- [X] T001 [P] Acrescentar `archivedAt` e `archivedBy` (record `ChangedBy`) em `OccurrenceDocument`, e `revision`, `archivedAt`, `archivedBy` em `OccurrenceRecord`; ler os campos novos no `MongoOccurrenceQueryRepository` (documento sem `revision` → `sem-revisao`)
- [X] T002 [P] Acrescentar `archived` em `OccurrenceResponses.ListItem` e `revision`, `archived`, `archivedAt`, `archivedBy` em `OccurrenceResponses.Detail`, preenchidos pelo `OccurrenceService`
- [X] T003 [P] Acrescentar `OCCURRENCES_MANAGE` em `access/application/Permission.java` (OWNER, ADMIN, MEMBER, nota "MEMBER só nos produtos liberados") e conferir o `AccessPolicyTest`/`AccessHttpTests` da LLT-120 com a permissão nova
- [X] T004 Criar a porta `OccurrenceCommandRepository` em `occurrence/application/` e `MongoOccurrenceCommandRepository` em `occurrence/infrastructure/`: `changeStatus(tenantId, id, expectedRevision, status, change, newRevision)`, `archive(tenantId, id, expectedRevision, at, by, newRevision)` e `unarchive(tenantId, id, expectedRevision, newRevision)`, cada um um `updateOne` condicional (`_id`, `tenantId`, `revision`, com `sem-revisao` = sem campo) que devolve se alterou

**Checkpoint**: modelo e respostas prontos; spec 012 continua verde.

---

## Phase 2: User Story 1 - Alterar o status (P1) 🎯 MVP

### Tests

- [X] T005 [US1] Escrever `OccurrenceLifecycleIntegrationTests.java` (parte US1): mudança ABERTA → EM_ANALISE com histórico (USER, id, data) e revisão nova; FECHADA → ABERTA; mesmo status sem histórico novo e com a mesma revisão; `RESOLVIDA` → 400 `fieldErrors.status`; revisão vazia → 400; revisão antiga → 409 `occurrence_changed` sem mudança; outra empresa, membro sem acesso e id inexistente → 404; membro com acesso consegue; filtro `status` da lista reflete a mudança; uma entrega do Finder com a revisão de antes da mudança → `409 REVISAO_DESATUALIZADA`

### Implementation

- [X] T006 [US1] Criar `ChangeStatus(status, revision)` e a rota `PUT /api/occurrences/{id}/status` no `OccurrenceController`, com validação em `FieldValidationException` (research R1)
- [X] T007 [US1] Implementar `OccurrenceService.changeStatus`: detalhe visível (mesma regra do `detail`), status igual → devolve o detalhe; senão `changeStatus` condicional com `{from, to, agora, USER, userId}` e revisão nova; zero alterados → 404 ou 409 (research R2); log `occurrence.status.changed` ou `occurrence.action.stale` com `userHash`
- [X] T008 [US1] Rodar a US1 verde

---

## Phase 3: User Story 2 - Arquivar e desarquivar (P2)

### Tests

- [X] T009 [P] [US2] Acrescentar `archived` ao `OccurrenceFilterTest` (`true`, `false`, ausente, inválido → 400)
- [X] T010 [US2] Acrescentar a `OccurrenceLifecycleIntegrationTests`: arquivar (data e autor, status igual, revisão nova), some da lista padrão e do total e aparece com `archived=true` combinando com filtros e paginação; desarquivar volta com o mesmo status; arquivar de novo e desarquivar ativa → 200 sem mudança; detalhe de arquivada → 200 com `archived: true`; 409 e 404 como na US1

### Implementation

- [X] T011 [US2] Acrescentar `archived` ao `OccurrenceFilter` (padrão `false`, inválido → `fieldErrors.archived`) e ao `MongoOccurrenceQueryRepository` (`archivedAt` ausente/nulo ou presente) e ao parâmetro da rota de lista
- [X] T012 [US2] Criar `ChangeArchive(revision)`, as rotas `POST /api/occurrences/{id}/archive` e `/unarchive` e `OccurrenceService.archive`/`unarchive` (research R4), com logs `occurrence.archived`/`occurrence.unarchived`
- [X] T013 [US2] Rodar a US2 verde

---

## Phase 4: User Story 3 - Achado novo desarquiva e reabre (P2)

- [X] T014 [P] [US3] Acrescentar ao `OccurrenceMergeTest`: arquivada EM_ANALISE → sem arquivamento, ABERTA, uma entrada `EM_ANALISE → ABERTA` SYSTEM; arquivada FECHADA → uma entrada só; arquivada ABERTA → desarquiva sem entrada; ativa EM_ANALISE continua igual
- [X] T015 [US3] Ajustar `OccurrenceMerge` (research R5)
- [X] T016 [US3] Acrescentar a `AnalysisSummaryDeliveryIntegrationTests`: arquivar pela rota de usuário, consultar pelo Finder, entregar um achado → a ocorrência volta para a lista padrão como ABERTA; rodar a US3 verde

---

## Phase 5: Polish

- [X] T017 [P] Atualizar o README (seção "Ocorrências") e os contratos `specs/012-consultar-ocorrencias/contracts/api.md` (parâmetro `archived`, campos novos) e `ocorrencia-documento.md` (`archivedAt`, `archivedBy` e a regra de desarquivar)
- [X] T018 Rodar `.\mvnw.cmd verify` com o Docker ligado e registrar o resultado no fim deste arquivo
- [X] T019 Executar o roteiro C1 a C11 do quickstart contra o Core local e registrar o resultado
- [ ] T020 Revisão do Felipe

## Dependencies & Execution Order

- Foundational (T001–T004) → US1 (T005–T008) → US2 (T009–T013) → US3 (T014–T016) → Polish.
- US3 só depende da T001 (campos) e pode correr junto da US2.
- O web (spec `013-ciclo-de-vida-ocorrencias` do lhamalog-web) depende do contrato `contracts/api.md`.

## Resultados

### T018 — verify (25/11/2026)

`.\mvnw.cmd verify` com o Docker Desktop: **BUILD SUCCESS, 523 testes, 0 falhas, 0 erros, 2 ignorados** (desempenho, sob demanda). Da feature: `OccurrenceLifecycleIntegrationTests` 7, `OccurrenceFilterTest` +1, `OccurrenceMergeTest` +3, `AnalysisSummaryDeliveryIntegrationTests` +1.

Achados na implementação:

- **Bug da spec 013 corrigido:** o `changedBy.id` gravado pelo Módulo era lido vazio, porque o Spring Data mapeia uma propriedade `id` de registro aninhado para `_id`. `OccurrenceDocument.ChangedBy` agora tem `@Field("id")`. A asserção que faltava entrou no teste da primeira entrega. Ocorrências de exemplo antigas do Mongo local (gravadas com `_id`) mostram o autor vazio até serem recriadas.
- **Testes da LLT-120 atualizados:** `AccessPolicyTest` e `AccessHttpTests` conferem a matriz exata; ganharam `OCCURRENCES_MANAGE` (o membro passa a ter 4 permissões).

### T019 — roteiro C1 a C11

Contra o jar desta branch na porta 8081, com o emissor de tokens só de teste (sessões no formato da Clerk) e um segredo interno gerado só para a sessão.

| # | Resultado |
|---|---|
| C1 | OK: detalhe com `revision`, `archived: false`, `archivedAt: null` |
| C2 | OK: ABERTA → EM_ANALISE, revisão nova, histórico por USER com o id do admin |
| C3 | OK: revisão antiga → 409 `occurrence_changed` |
| C4 | OK: `RESOLVIDA` → 400 `fieldErrors.status` |
| C5 | OK: aparece no filtro EM_ANALISE |
| C6 | OK: arquivada com data e autor; status continua EM_ANALISE |
| C7 | OK: total da lista padrão 24 → 23; aparece em `archived=true` |
| C8 | OK: desarquivada, volta com EM_ANALISE e total 24 |
| C9 | OK: arquivada de novo; entrega do Finder → 201, desarquivada, ABERTA, histórico `EM_ANALISE → ABERTA` por SYSTEM `modulo-ocorrencia` |
| C10 | OK: membro sem acesso ao produto → 404 |
| C11 | OK: matriz com `OCCURRENCES_MANAGE` para OWNER, ADMIN e MEMBER |

O roteiro mudou uma ocorrência de exemplo do "Produto da A (V4)" (`sample-…-3`): ela ficou ABERTA, com contagem + 1 e o histórico das mudanças.

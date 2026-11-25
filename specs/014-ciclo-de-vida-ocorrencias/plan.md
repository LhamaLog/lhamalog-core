# Implementation Plan: Ciclo de vida da ocorrência (alterar status e arquivar)

**Branch**: `LLT-136-ciclo-de-vida` | **Date**: 25/11/2026 | **Spec**: [spec.md](spec.md)

A branch sai da `LLT-293-gravar-ocorrencias-a-partir-do-resumo-de-analise-do-finder-modulo-ocorrencia` (`3d41f9b`, PR #20). O PR é empilhado sobre o #20: base `LLT-293-…` até o #20 entrar, depois `Development`, sempre com merge commit. Ordem: #19 → #20 → este.

## Summary

Três rotas novas no `OccurrenceController` da spec 012, com a mesma visibilidade do detalhe e controle de concorrência pela revisão da spec 013:

- `PUT /api/occurrences/{id}/status` `{status, revision}` → detalhe atualizado.
- `POST /api/occurrences/{id}/archive` e `POST /api/occurrences/{id}/unarchive` `{revision}` → detalhe atualizado (mesmo padrão de `POST /api/products/{id}/archive`).

A lista ganha `archived` (padrão `false`), o item ganha `archived`, e o detalhe ganha `revision`, `archived`, `archivedAt` e `archivedBy`. Cada ação é um `updateOne` condicional (`_id`, `tenantId`, `revision`) que troca a revisão; condição falhou → 409 `occurrence_changed`. A matriz da LLT-120 ganha `OCCURRENCES_MANAGE` para Dono, Administrador e Membro. O `OccurrenceMerge` da spec 013 passa a desarquivar e reabrir uma ocorrência arquivada. Decisões em [research.md](research.md).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data MongoDB (`MongoTemplate`). Nenhuma dependência nova.

**Storage**: coleção `Ocorrencia` (specs 012 e 013) com dois campos novos opcionais, `archivedAt` e `archivedBy`. Nenhum índice novo: o filtro de arquivadas usa o índice `{tenantId, productId, lastSeenAt, _id}` com filtro residual.

**Testing**: JUnit 5, MockMvc, `@CoreIntegrationTest` (Testcontainers, Docker ligado), `OccurrenceFixtures` e `InternalModuleWorld` das specs 012 e 013.

**Target Platform**: API HTTP do LhamaLog Core (porta 8080)

**Project Type**: web-service

**Performance Goals**: ação em até 500 ms com o Core local (SC-005): uma leitura do detalhe, um `updateOne` e a releitura.

**Constraints**: contrato DN-009-2 com o Finder sem mudança; respostas de erro no formato do `ApiExceptionHandler` (`fieldErrors` e `ApiStatusException` com código); constituição 2.1.0.

**Scale/Scope**: três rotas, uma porta de escrita nova (`OccurrenceCommandRepository`), mudanças pequenas em filtro, repositório de leitura, respostas, `OccurrenceMerge` e `Permission`; uma classe de teste de integração nova e testes acrescentados nas existentes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Spec Kit**: spec, clarify, plano, research, data-model, contrato, quickstart e tasks antes do código.
- **II. IA e revisão**: decisões no clarify e no research; o Felipe revisa antes do merge.
- **III. Testes**: `OccurrenceLifecycleIntegrationTests` (status, arquivar, desarquivar, 409, 404, permissões, lista), `OccurrenceMergeTest` e `AnalysisSummaryDeliveryIntegrationTests` acrescidos (achado de arquivada); `.\mvnw.cmd verify`.
- **IV. Idioma**: identificadores em inglês, documentação em português.
- **V. Segredos**: nada novo.
- **VI. Logs**: eventos `occurrence.status.changed`, `occurrence.archived`, `occurrence.unarchived` (INFO) e `occurrence.action.stale` (WARN), com `tenantId`, `occurrenceId` e `userHash` (como a LLT-120), sem título nem assinatura.

Resultado: sem violações.

## Project Structure

### Documentation (this feature)

```text
specs/014-ciclo-de-vida-ocorrencias/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/api.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/
├── access/application/Permission.java                 # + OCCURRENCES_MANAGE (OWNER, ADMIN, MEMBER)
└── occurrence/
    ├── api/
    │   ├── OccurrenceController.java                  # + PUT /{id}/status, POST /{id}/archive e /unarchive, ?archived
    │   └── OccurrenceResponses.java                   # ListItem.archived; Detail.revision, archived, archivedAt, archivedBy
    ├── application/
    │   ├── OccurrenceFilter.java                      # + archived (true/false, padrão false)
    │   ├── OccurrenceRecord.java                      # + revision, archivedAt, archivedBy
    │   ├── OccurrenceService.java                     # + changeStatus, archive, unarchive
    │   ├── OccurrenceCommandRepository.java           # porta: updates condicionais pela revisão
    │   └── OccurrenceMerge.java                       # arquivada: desarquiva e reabre (spec 013)
    └── infrastructure/
        ├── OccurrenceDocument.java                    # + archivedAt, archivedBy
        ├── MongoOccurrenceQueryRepository.java        # filtro de arquivadas; lê os campos novos
        └── MongoOccurrenceCommandRepository.java      # updateOne condicional, revisão nova

src/test/java/br/com/lhamalog/core/occurrence/
├── OccurrenceLifecycleIntegrationTests.java           # novo
├── OccurrenceFilterTest.java                          # + archived
├── OccurrenceMergeTest.java                           # + arquivada
└── AnalysisSummaryDeliveryIntegrationTests.java       # + achado de arquivada
```

**Structure Decision**: as ações ficam no `OccurrenceService` da spec 012, que já resolve a visibilidade; a escrita fica numa porta própria (`OccurrenceCommandRepository`) para não misturar com a escrita do Finder (`OccurrenceWriteRepository`, spec 013), que troca o documento inteiro.

## Complexity Tracking

> Sem violações.

# Implementation Plan: Upload manual de arquivos de logs (Core → Collector)

**Branch**: `feature/LLT-292-manual-upload` (Core, worktree `lhamalog-core-llt292`) | **Collector (depois)**: `feature/LLT-292-manual-upload` a partir da branch da LLT-290, worktree irmão `lhamalog-collector-llt292` | **Date**: 2026-11-20 | **Spec**: `specs/011-manual-upload/spec.md`

**Input**: Feature specification from `/specs/011-manual-upload/spec.md`

## Summary

O front chama `POST /api/manual/upload` no Core (JWT Clerk, até 100 MB por arquivo e por requisição). O Core autoriza o produto (403 se arquivado, outro tenant ou sem acesso), revalida extensão/tamanho e **espera** o Collector. O Collector expõe `POST /internal/manual/upload` na porta 8080 com `X-LhamaLog-Service-Token`, aplica dedup `FileSource.UPLOAD` / `signal=logs`, cota/rate/assinatura iguais ao OTLP, recusa binário óbvio, grava o arquivo no OpenObserve e publica `log.ingested` na fila de leitura com `fileSource=upload`. 202 se pelo menos um arquivo entra; 409 se todos são duplicata; recusas parciais vão no corpo do 202.

## Technical Context

**Language/Version**: Java 25, Spring Boot (Core e Collector, mesmas linhas já no `pom.xml` de cada repo)

**Primary Dependencies**: Spring Security (Clerk JWT no Core; filtro de service token no Collector), Spring Web / `RestClient`, Redis (dedup + stream), Mongo (cota e produto), OpenObserve (conteúdo)

**Storage**: MongoDB (produtos, cota — sem coleção nova obrigatória), Redis (dedup + `lhamalog:stream:logs`), OpenObserve (documento cru por arquivo)

**Testing**: JUnit 5, AssertJ, MockMvc / RestClient de teste no Core; Testcontainers (Redis, Mongo, OpenObserve) no Collector, no estilo das acceptance da LLT-290/186

**Target Platform**: serviço Linux (local e Railway), portas já usadas (Core 8081 local / 8080 default; Collector `server.port` 8080)

**Project Type**: dois web-services (Core + Collector)

**Performance Goals**: aceitar um arquivo de até 100 MB sem estouro de heap; timeout explícito no repasse

**Constraints**: não alterar `.env`; não tocar nos checkouts/worktrees alheios; não mudar o limite de 5 MB do OTLP; `schemaVersion` da fila permanece 1; B1 da LLT-290 ainda aberta

**Scale/Scope**: MVP de ingestão manual; UI, WebSocket e Finder fora

## Constitution Check

- Spec Kit seguido: specify → clarify (12/12 fechadas) → plan → tasks → analyze. A implementação do Core começou e está no worktree `lhamalog-core-llt292` (Collector ainda bloqueado pela LLT-290).
- IA pode implementar 100% depois do OK; commit/push só com pedido do Michel.
- Testes fazem parte das tasks (SC-001 a SC-008).
- Código em inglês, docs em pt-BR, commits no formato `LLT-292: descricao` quando houver OK.
- Logs: princípio VI (cid, função, aviso, data com fuso).
- Segredos só por env (`LHAMALOG_CORE_COLLECTOR_TOKEN`, `LHAMALOG_COLLECTOR_SERVICE_TOKEN`).

Re-check pós-desenho: aprovado, com o risco B1 e a dependência da LLT-290 registrados no research e nas tasks.

## Project Structure

### Documentation (this feature)

```text
specs/011-manual-upload/
├── spec.md
├── clarify-questions.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── analyze-report.md
├── tasks.md
├── checklists/
│   └── requirements.md
└── contracts/
    ├── core-manual-upload.md
    └── collector-internal-upload.md
```

A pasta canônica nesta etapa está no Core (`lhamalog-core-llt292` e cópia em `/workspace/llt292/`). A mesma pasta deve ser copiada para o worktree irmão do Collector quando ele for criado.

### Source Code (Core)

```text
src/main/java/br/com/lhamalog/core/
├── config/ManualUploadProperties.java
├── config/ManualUploadConfiguration.java
└── upload/
    ├── ManualUploadController.java
    ├── ManualUploadService.java
    ├── ManualUploadValidator.java
    ├── ProductUploadAuthorizer.java
    ├── CollectorManualUploadClient.java
    └── ManualUploadResponses.java
src/main/resources/application.properties          # multipart 100MB, extensions
src/test/java/br/com/lhamalog/core/upload/
```

### Source Code (Collector — worktree irmão, depois da LLT-290)

```text
src/main/java/br/com/lhamalog/collector/
├── config/ManualUploadSecurityConfiguration.java
├── config/ManualUploadProperties.java
└── upload/
    ├── ManualUploadController.java
    ├── ManualUploadService.java
    ├── ObviousBinaryDetector.java
    ├── ManualUploadOpenObserveWriter.java
    └── ManualUploadPublisher.java          # reusa publisher/stream existente
src/main/resources/application.properties          # multipart 100MB, token env
src/test/java/br/com/lhamalog/collector/upload/
```

Reuso obrigatório: `dedup/FileDeduplication`, `FileSource.UPLOAD`, `FileSignal.LOGS`, `quota/QuotaGate`, rate limiter de `IngestionControls`, `TelemetryIngestedPublisher` / cliente OpenObserve, `CorrelationId`.

## Implementation phases

1. **Core (pode começar já)** no worktree `lhamalog-core-llt292`: contrato público, autorização 403, validador, cliente, mapeamento de status. Testes com Collector dublado.
2. **Collector (bloqueado pela LLT-290 commitada)**: worktree irmão, endpoint interno, dedup/cota/OO/fila, testes de aceitação.
3. **Integração**: apontar o Core de teste/local para o Collector; um teste de contrato ponta a ponta se o ambiente permitir, sem mexer nos outros worktrees.

## Complexity Tracking

- Integração com a fila exige escrita no OpenObserve (R1): complexidade herdada do contrato 002/004, não é scope creep.
- 403 vs 404 (R3): decisão explícita, não inconsistência esquecida.

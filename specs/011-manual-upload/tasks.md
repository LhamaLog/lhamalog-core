# Tasks: Upload manual de arquivos de logs (LLT-292)

**Input**: `specs/011-manual-upload/` (spec, plan, research, data-model, contracts)

**Repos**: Core (`lhamalog-core-llt292`, branch `feature/LLT-292-manual-upload`, base `23a69d9`) e Collector (worktree irmão **ainda não criado**, branch a partir da LLT-290).

**Estimativa**: Core ~16 h (2 dias úteis); Collector ~24 h (3 dias úteis); total ~40 h / 5 dias. Não inclui a espera pelos commits da LLT-290.

**Formato**: `- [ ] Tnnn [P?] [US?] [Core|Coll] Descrição com path`

**Testes**: obrigatórios (constituição + SC-001–SC-008). Commit/push só com OK do Michel.

## Fase 0: Worktrees

- [X] T001 [Core] Copiar `specs/011-manual-upload/` para `E:\Facul\Escola_TI\repositorios\lhamalog-core-llt292\specs\011-manual-upload\` (worktree já criado em 20/11/2026 a partir de `origin/Development` `23a69d9`, upstream removido). Não alterar o checkout `lhamalog-core` (LLT-145).
- [ ] T002 [Coll] **Bloqueada** até os commits da LLT-290 existirem em `feature/LLT-290-file-hash-dedup`. Então criar `E:\Facul\Escola_TI\repositorios\lhamalog-collector-llt292` com `git worktree add -b feature/LLT-292-manual-upload ..\lhamalog-collector-llt292 feature/LLT-290-file-hash-dedup` e `git branch --unset-upstream`. **Não** tocar em `lhamalog-collector-llt290` nem no checkout principal (LLT-186, `tasks.md` sujo, `stash@{0}`). Copiar a mesma pasta `specs/011-manual-upload/`.

**Checkpoint**: Core pode seguir T003+ agora. Collector só depois de T002.

---

## Fase 1: Fundação Core (~6 h)

**Purpose**: propriedades, autorização, cliente e rota pública. Sem dependência do Collector real (dublê nos testes).

- [X] T003 [Core] Elevar em `src/main/resources/application.properties` (e confirmar perfis) `spring.servlet.multipart.max-file-size=100MB`, `spring.servlet.multipart.max-request-size=100MB` e o teto do Tomcat (`server.tomcat.max-http-form-post-size=100MB`, `max-swallow-size` se necessário). Nenhum `.env`.
- [X] T004 [P] [Core] Criar `src/main/java/br/com/lhamalog/core/config/ManualUploadProperties.java` e `ManualUploadConfiguration.java`: `allowed-extensions` (txt,json,csv,log), `collector-url`, `timeout`, leitura de `LHAMALOG_CORE_COLLECTOR_TOKEN` só por env.
- [X] T005 [P] [US1] [Core] Implementar `src/main/java/br/com/lhamalog/core/upload/ProductUploadAuthorizer.java`: visível no tenant como `ProductService.get()` (admin vs `ProductAccess`) **e** não arquivado; recusa com 403 (id desconhecido, outro tenant, sem acesso, arquivado). Não alterar `ProductService.get()`.
- [X] T006 [P] [US3] [Core] Implementar `src/main/java/br/com/lhamalog/core/upload/ManualUploadValidator.java`: extensão da lista e tetos de 100 MB (arquivo e soma da requisição) → 400 / 413.
- [X] T007 [US1] [Core] Implementar `src/main/java/br/com/lhamalog/core/upload/CollectorManualUploadClient.java` com `RestClient`, header `X-LhamaLog-Service-Token`, `X-Correlation-Id`, **sem** `Authorization`, multipart, timeout e streaming/baixo buffer (research R2).
- [X] T008 [US1] [Core] Mapear respostas do Collector para o front em `ManualUploadResponses.java`: 202/400/409/413/429/503/504; corpo `loteId` + `files[]` conforme `contracts/core-manual-upload.md`.
- [X] T009 [US1] [Core] Implementar `ManualUploadController.java` (`POST /api/manual/upload`) e `ManualUploadService.java`: ordem sessão → 403 produto → validação → espera do Collector (Q12). Reusar `CompanySessionResolver`.
- [X] T010 [P] [Core] Logs no padrão VI (`event=manual_upload.… | aviso | chave=valor`, cid, prefixo de MD5 só se o Core chegar a calculá-lo — de preferência não calcula; quem hasheia é o Collector). Propagar `X-Correlation-Id`.

**Checkpoint**: rota pública responde com Collector dublado.

---

## Fase 2: Testes Core (~8 h) — US1–US4 no contrato público

- [X] T011 [US1] [Core] Testes em `src/test/java/br/com/lhamalog/core/upload/`: JWT ausente/inválido → 401; arquivo válido + produto visível → 202; cliente chamado com service token, cid e `productId`/`files`; Collector não chamado quando a validação/autorização falha.
- [X] T012 [US1] [Core] Testes 403: produto de outro tenant, sem `ProductAccess` (não admin), arquivado, id inexistente.
- [X] T013 [US3] [Core] Testes 400 (extensão) e 413 (arquivo > 100 MB e soma da requisição > 100 MB).
- [X] T014 [US2] [US4] [Core] Testes de mapeamento: Collector 409 (lote todo duplicata) → 409; 202 parcial (aceito + `DUPLICATE`) → 202 com o corpo; 429 → 429; 503 → 503; timeout/conexão recusada → 503/504, nunca 202.
- [X] T015 [US4] [Core] Teste multipart com dois arquivos válidos: um único POST, um `loteId`, dois itens encaminhados.

**Checkpoint**: suíte do Core verde com dublê. Estimativa parcial Core: ~16 h (T001+T003–T015, mais a fatia de T032/T033).

---

## Fase 3: Fundação Collector (~10 h) — só depois de T002

**Purpose**: primeiro `@RestController` na porta 8080; reusa `dedup`, cota e fila da LLT-290/186.

- [ ] T016 [Coll] Elevar multipart/Tomcat para 100 MB em `src/main/resources/application.properties` do Collector (sem mudar o teto de 5 MB do receptor OTLP).
- [ ] T017 [P] [Coll] `ManualUploadProperties.java`: extensões, e token só via `LHAMALOG_COLLECTOR_SERVICE_TOKEN`.
- [ ] T018 [Coll] `config/ManualUploadSecurityConfiguration.java`: matcher `/internal/manual/upload`, header `X-LhamaLog-Service-Token`, recusa com 401 se faltar token, se o token não bater, ou se vier `Authorization` (espelhar `UsageFactsSecurityConfiguration`). Log com SHA-256/tamanho, nunca o valor.
- [ ] T019 [P] [US3] [Coll] `upload/ObviousBinaryDetector.java` com magic bytes MZ, ELF e Mach-O (research R6).
- [ ] T020 [US1] [Coll] `ManualUploadService.java`: por arquivo, ordem extensão → tamanho → binário óbvio → `FileDeduplication.claim(tenantId, productId, FileSignal.LOGS, FileSource.UPLOAD, bytes)` → rate/cota → escrita OO → publicação → `commit`/`confirm` (ou `release`/`refund`). `signal=logs`. Redis fora: fail-open do claim (WARN), como na spec 010.
- [ ] T021 [P] [US1] [Coll] `ManualUploadOpenObserveWriter.java`: um documento no stream `logs` por arquivo aceito (`lhamalog_batch_id`, `lhamalog_record_id`, nome, `raw`, tenant, produto, `file_source=upload`).
- [ ] T022 [P] [US1] [Coll] Publicar `log.ingested` em `lhamalog:stream:logs` reusando o publisher existente: `schemaVersion` 1, `metadata.fileMd5` / `fileSource=upload`, `idempotencyId = UUIDv3("<batchId>:logs:upload:<md5>")`. Um `batchId`/mensagem por arquivo aceito.
- [ ] T023 [US1] [US4] [Coll] `ManualUploadController.java` em `POST /internal/manual/upload`: lê `tenantId`, `productId`, `loteId`, `files`; devolve o corpo do contrato; 202 / 409 / 400 / 413 / 429 / 503 conforme FR-012.
- [ ] T024 [P] [Coll] Logs no padrão VI, `source=upload`, `md5Prefix` de 8 caracteres, mesmo `X-Correlation-Id`. Métrica `lhamalog.ingestion.refused{reason=duplicate_file}` nas duplicatas.

**Checkpoint**: endpoint interno processa um arquivo válido contra Redis/OO de teste.

---

## Fase 4: Testes Collector (~12 h)

- [ ] T025 [US1] [Coll] Sem token, token errado ou `Authorization` presente → 401; nada publicado.
- [ ] T026 [US1] [Coll] Arquivo `.log` válido: 202, documento no OpenObserve, exatamente 1 `XADD` em `lhamalog:stream:logs` com `fileSource=upload`.
- [ ] T027 [US2] [Coll] Segundo upload do mesmo conteúdo (mesmo tenant/produto) → 409, sem segundo `XADD` e sem segunda reserva de cota. O mesmo MD5 com origem OTLP **não** bloqueia o upload (e vice-versa).
- [ ] T028 [US2] [Coll] Redis fora: claim fail-open (WARN), arquivo pode ser aceito (espelha a 010).
- [ ] T029 [US3] [Coll] Extensão inválida → 400; > 100 MB → 413; conteúdo MZ/ELF/Mach-O com extensão `.log` → recusa `OBVIOUS_BINARY`. OTLP continua recusando payload > 5 MB no caminho antigo.
- [ ] T030 [US4] [Coll] Lote misto (válido + duplicata) → 202, só o válido na fila; lote só de duplicatas → 409. Dois arquivos iguais no mesmo POST: o segundo é duplicata.
- [ ] T031 [US1] [Coll] Sem saldo → 429; sem plano → 503; reserva só depois do claim; duplicata não chama reserva.
- [ ] T032 [Coll] Teste de caracterização da B1 (dois uploads idênticos concorrentes): documentar 409 enquanto T041 da LLT-290 estiver aberta; não implementar 503 próprio.

**Checkpoint**: suíte do Collector verde. Estimativa parcial Collector: ~24 h (T002+T016–T032, mais a fatia de T033/T034).

---

## Fase 5: Docs e verify (~2 h)

- [X] T033 [P] [Core] [Coll] Nota curta em pt-BR no README de cada repo (rota, limites, service token por env, 403 de produto). Sem OpenAPI (pós-MVP). Sem segredos.
- [X] T034 [Core] [Coll] `./mvnw -B verify` (ou `mvnw.cmd`) em cada worktree, em segundo plano; zero falhas novas. Não rodar no checkout principal nem no worktree da LLT-290.

---

## Dependências entre tarefas

```text
T001 → T003–T015 → (T033 Core, T034 Core)
T002 (após commits LLT-290) → T016–T032 → (T033 Coll, T034 Coll)
T007 depende de T004; T009 depende de T005–T008
T020 depende de T019 e do pacote dedup da LLT-290
T023 depende de T018 e T020–T022
```

Core e Collector podem ser implementados em paralelo **depois** de T002; até lá só o Core anda.

## Contagem

IDs únicos: T001–T034 (**34** tarefas).

| Repo | Tasks exclusivas | Compartilhadas | Total no repo | Estimativa |
| --- | ---: | ---: | ---: | --- |
| Core | 14 (T001, T003–T015) | T033, T034 | 16 | ~16 h |
| Collector | 18 (T002, T016–T032) | T033, T034 | 20 | ~24 h |
| **Únicos** |  |  | **34** | **~40 h** |

# Tasks: Manter contexto do produto

**Input**: Design documents from `/specs/009-contexto-produto/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Incluídos — constituição III e `quickstart.md` exigem cobertura do comportamento novo antes do merge.

**Organization**: Tasks grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)

**Restrição de toda a feature**: não alterar `specs/001-empresa-produtos/` nem `specs/002-editar-arquivar-produto/`; não usar os bancos `lhamalog_core` e `lhamalog_teste`; não chamar a LLM; não juntar o contexto da empresa.

---

## Phase 1: Setup

- [X] T001 Confirmar o alcance: `src/main/java/br/com/lhamalog/core/product/` (Product, ProductService, ProductController e três arquivos novos), `ProductContextIntegrationTests.java` novo e o campo de contexto em `CompanyProductIntegrationTests.java` e `ProductEditArchiveIntegrationTests.java`; sem `pom.xml`, sem `ApiExceptionHandler`, sem pacote novo

---

## Phase 2: Foundational

**Purpose**: Novo modelo de contexto, usado por todas as histórias.

- [X] T002 Em `src/main/java/br/com/lhamalog/core/product/Product.java`, trocar os campos de `Context` por `functionalDescription`, `personality`, `responseLanguage`, `architecture`, `scope` e `sensitiveData`, com `@Size`/`@Pattern` conforme `data-model.md`, o enum `Personality` (`SIMPLE`, `STANDARD`, `DETAILED`, com rótulo em português), o construtor compacto de normalização, `Context.empty()` e `hasContent()`
- [X] T003 [P] Em `src/test/java/br/com/lhamalog/core/company/CompanyProductIntegrationTests.java`, trocar `problemSolved` por `functionalDescription` no teste `creatorReceivesAccessButOtherMembersAndTenantsDoNot`, sem outra mudança
- [X] T004 [P] Em `src/test/java/br/com/lhamalog/core/product/ProductEditArchiveIntegrationTests.java`, trocar `problemSolved` por `functionalDescription` (helper `create`, asserções e PUT de contexto), sem outra mudança
- [X] T004b [P] Em `src/test/java/br/com/lhamalog/core/product/ProductUniqueNameIntegrationTests.java` (da 003), trocar `problemSolved` por `functionalDescription` no helper `create` e no PUT de contexto, sem outra mudança
- [X] T005 [P] Criar `src/test/java/br/com/lhamalog/core/product/ProductContextIntegrationTests.java` no padrão de `ProductEditArchiveIntegrationTests` (mesmas anotações, `session`, `organization`, `json`, `create`, `changeArchive`)

---

## Phase 3: User Story 1 - Descrever o produto para a IA (P1)

- [X] T006 [US1] Testes: contexto completo persiste normalizado; sem contexto aplica os padrões; limites e personalidade desconhecida respondem 400 sem alterar nada (cenários 1, 2 e 5 do quickstart)
- [X] T007 [US1] Garantir que `create` e `updateContext` de `ProductService.java` gravam o `Context` já normalizado (vem do construtor do record; sem lógica extra)

---

## Phase 4: User Story 2 - Histórico de versões (P1)

- [X] T008 [US2] Testes: versão 1 no cadastro com conteúdo; nenhuma no cadastro vazio; versão nova só com mudança; versão antiga preservada; 404 de versão inexistente; 404 sem acesso e de outra empresa; arquivado mantém versões e recusa PUT sem versão nova (cenários 1, 3, 4, 6, 7 e 9)
- [X] T009 [P] [US2] Criar `ProductContextRevision.java` (documento `ProductContextRevision`, índice único `productId` + `revision`, `of(...)`, visões `View` e `ViewList`)
- [X] T010 [P] [US2] Criar `ProductContextRevisionRepository.java` (lista por produto em ordem decrescente, busca por número, última versão)
- [X] T011 [US2] Em `ProductService.java`, gravar a versão depois do produto em `create` (se `hasContent`) e em `updateContext` (se o contexto mudou); métodos `contextRevisions` e `contextRevision` com o mesmo controle de acesso de `get`
- [X] T012 [US2] Em `ProductController.java`, `GET /api/products/{id}/context/revisions` e `GET /api/products/{id}/context/revisions/{revision}`

---

## Phase 5: User Story 3 - Texto para a IA (P2)

- [X] T013 [US3] Testes: o texto contém nome e campos com rótulos; quebra de linha vira espaço; `executed` falso; disponível para arquivado (cenários 8 e 9)
- [X] T014 [P] [US3] Criar `ProductContextPrompt.java` (`from(Product)`, uma linha por campo, sem caracteres de controle, aviso de dado do cliente)
- [X] T015 [US3] Em `ProductService.java`, método `contextPrompt`; em `ProductController.java`, `GET /api/products/{id}/context/prompt`

---

## Phase 6: Polish

- [ ] T016 Executar o verify do `quickstart.md` com `TEST_MONGODB_URI` em banco novo, confirmar 0 pulados e registrar o resultado em "Resultados executados"
- [ ] T017 Conferir com `git status` que só mudaram os arquivos de T001 e `specs/009-contexto-produto/`; conferir ausência de segredos e de chamada à LLM
- [ ] T018 Avisar o time do front que o `context` do produto mudou de campos (ver `quickstart.md`) e confirmar que nenhum ambiente tem produto gravado com `context.problemSolved`

---

## Phase 7: Ajustes da revisão do PR #9 (17/11/2026)

- [X] T019 Mesclar a `Development` atual (com LLT-123, LLT-177, #13 e #6/LLT-163) na branch; resolver o conflito do `create` em `ProductService.java` mantendo `created = saveWithUniqueName("product.create.name_in_use", ...)` e o de `CompanyProductIntegrationTests` com a versão do #6 e `functionalDescription`; trocar `sessions.resolve(jwt)` por `sessions.current()` nos endpoints de contexto e levar `ProductContextIntegrationTests` para `@CoreIntegrationTest` e `TestTenants`
- [X] T020 Renomear `specs/004-contexto-produto/` para `specs/009-contexto-produto/` (a 004 já é `004-convidar-usuario-empresa`; 006, 007 e 008 já estão em uso); atualizar as referências nesta pasta e os Javadocs "feature 004" do código e dos testes
- [X] T021 Em `ProductService.java`, compensar a falha da versão: no `create`, remover produto e acesso; no `updateContext`, gravar de novo o contexto anterior; logs WARN `event=product.context.revision.failed` e ERROR `event=product.context.revision.undo_failed`
- [X] T022 Em `ProductService.java`, log INFO `event=product.context.revision.created` com `tenantId`, `productId` e `revision`, sem texto do contexto (constituição VI)
- [X] T023 Testes: `ProductContextRevisionCompensationTests` (unitário, sem Mongo: cadastro desfeito, contexto anterior devolvido, falha ao desfazer anexada) e `newVersionIsLoggedWithEventCodeWithoutContextText` em `ProductContextIntegrationTests`
- [ ] T024 Registrar no card LLT-178 que a junção com o contexto da empresa (`CompanyContextPrompt`) fica para a integração com a Sugestão, ou implementá-la

---

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → US3 → Polish
- T003, T004 e T005 podem correr juntos; T009, T010 e T014 também (arquivos distintos)
- Os testes de cada história são escritos antes do código daquela história

## Notes

- T001–T015 implementados pela IA em 10/11/2026, sem execução local (ambiente sem Maven); T016 confirma
- T019–T023 implementados pela IA em 17/11/2026, também sem execução no ambiente da IA (Maven Central bloqueado); T016 confirma
- Commits `LLT-178: <descricao>`; branch `feature/LLT-178-contexto-produto`

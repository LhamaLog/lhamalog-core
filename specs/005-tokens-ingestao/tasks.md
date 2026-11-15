# Tasks: Tokens de ingestão do produto

**Input**: Design documents from `/specs/005-tokens-ingestao/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Incluídos — unitários sem banco, integração com Mongo e API.

**Restrição de toda a feature**: não alterar o Collector, `Produto`, `products`, `specs/001` a `specs/004`; nunca registrar o valor do token.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [X] T001 Confirmar o alcance: pacote novo `src/main/java/br/com/lhamalog/core/ingestiontoken/` e testes em `src/test/java/br/com/lhamalog/core/ingestiontoken/`; sem `pom.xml`, sem mudança em `product/` e `shared/`

## Phase 2: Foundational

- [X] T002 [P] `application/TokenSecrets.java`: geração `llt_` + 32 bytes `SecureRandom` em Base64 URL e `sha256Hex` (hex minúsculo, UTF-8)
- [X] T003 [P] `application/IngestionToken.java`: modelo, `Status`, `State`, `usableAt`, `stateAt`, `issue`, `revoke`, `rotateOut`
- [X] T004 [P] `application/TokenAuditEntry.java`: registro com `Action`
- [X] T005 [P] Portas `IngestionTokenRepository`, `TokenAuditRepository` e `ProductCatalog`
- [X] T006 [P] Testes `TokenSecretsTest` (formato, aleatoriedade, vetor conhecido do SHA-256) e repositórios em memória para os testes do serviço

## Phase 3: US1 Gerar token (P1)

- [X] T007 [US1] Testes em `IngestionTokenServiceTest`: gera com segredo exibido uma vez e hash gravado; listagem sem valor/hash; 403 membro; 404 outra empresa; 409 arquivado; 400 nome inválido; auditoria `CREATED`
- [X] T008 [US1] `IngestionTokenService.list` e `create` com `InstantSource` injetável

## Phase 4: US2 Revogar (P1)

- [X] T009 [US2] Testes: revoga com data/autor; repetição sem auditoria; produto arquivado aceita; 404 token de outro produto
- [X] T010 [US2] `IngestionTokenService.revoke`

## Phase 5: US3 Rotacionar (P2)

- [X] T011 [US3] Testes com relógio controlado: padrão 24 h; 1 h com os dois válidos até o fim; 409 revogado/expirado/em transição; 400 fora de 1–168; 409 arquivado; ordem das gravações
- [X] T012 [US3] `IngestionTokenService.rotate`

## Phase 6: US4 Auditoria (P2)

- [X] T013 [US4] Testes: registros na ordem inversa, sem valor nem hash
- [X] T014 [US4] `IngestionTokenService.audit`

## Phase 7: Infraestrutura e API

- [X] T015 [P] `infrastructure/IngestionTokenDocument.java` (coleção `product_tokens`, índices, `@Version`) e `MongoIngestionTokenRepository`
- [X] T016 [P] `infrastructure/TokenAuditDocument.java` (coleção `ProductTokenAudit`) e `MongoTokenAuditRepository`
- [X] T017 [P] `infrastructure/ProductCatalogAdapter.java` sobre `ProductRepository.findByIdAndTenantId`
- [X] T018 `api/IngestionTokenController.java` e `api/TokenResponses.java`
- [X] T019 Teste `MongoIngestionTokenRepositoryIntegrationTest`: documento cru no formato do Collector, índice único, versão
- [X] T020 Teste `IngestionTokenControllerIntegrationTest`: fluxo HTTP gerar → listar → rotacionar → revogar → auditoria; 403, 404, 409, 400

## Phase 8: Polish

- [ ] T021 Rodar o verify do `quickstart.md` com `TEST_MONGODB_URI` em banco novo; registrar em "Resultados executados"
- [ ] T022 Conferir com `git status` o alcance de T001 e que nenhum teste ou log contém token fixo
- [ ] T023 Criar no Drive a ADR de `proposta-adr.md` e levar ao time o desencontro `Produto` × `products`

## Dependencies

- Setup → Foundational → US1 → US2 → US3 → US4 → Infraestrutura/API → Polish
- Testes de cada história antes do código da história

## Notes

- T001–T020 implementados pela IA em 12/11/2026 sem execução de Maven (ambiente sem acesso ao repositório Maven). A camada `application` foi compilada à parte e os 22 testes unitários (`TokenSecretsTest`, `IngestionTokenServiceTest`) passaram. Prova de que os testes pegam erro: três defeitos introduzidos de propósito (token expirado aceito, membro comum autorizado, token gravado no lugar do hash) foram todos detectados. Os testes com Mongo e a API dependem de T021.
- Nenhum arquivo existente do repositório é alterado por esta feature (só arquivos novos), então ela não conflita com a 003 (LLT-177) nem com a 004 (LLT-178).
- Commits `LLT-179: <descricao>`; branch `feature/LLT-179-tokens-ingestao`

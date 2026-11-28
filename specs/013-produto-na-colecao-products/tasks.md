# Tasks: Produto gravado em `products`

**Input**: Design documents from `/specs/013-produto-na-colecao-products/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Incluídos — integração com Mongo (Testcontainers) e a suíte existente.

**Restrição de toda a feature**: não mudar o record `Product`, a API de produtos, `product_tokens` nem o Coletor.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [X] T001 Linha de base: `.\mvnw.cmd -B clean verify` na `411f79c`, anotada no `quickstart.md`

## Phase 2: US1 Chave gerada no Core é aceita pelo Coletor (P1)

- [X] T002 [US1] Testes primeiro em `src/test/java/br/com/lhamalog/core/product/ProductCollectorContractIntegrationTests.java`: criar produto grava em `products` com `productId` = `_id` e `tenantId`, nada em `Produto`; editar, alterar contexto, arquivar e desarquivar mantêm o `productId`; produto e chave pela API passam na busca do Coletor (`product_tokens.tokenHash` e `products.productId`) com o `tenantId` da empresa; o índice `product_collector_product_id` existe, único e parcial. Ver falhar.
- [X] T003 [US1] `Product.java`: `@Document("products")` e o índice `product_collector_product_id`
- [X] T004 [US1] `ProductCollectorFields.java`: `BeforeSaveCallback<Product>` que escreve `productId` = `_id`
- [X] T005 [US1] Javadoc de `ProductCatalogAdapter.java` (`Produto` → `products`)
- [X] T006 [US1] `.\mvnw.cmd -B clean verify` verde, anotado no `quickstart.md`. Parar para revisão.

## Phase 3: US2 Migração dos bancos existentes (P2)

- [X] T007 [US2] Rodar o script do `quickstart.md` num banco local descartável com documentos em `Produto` (casos: só `Produto`; `products` vazia e `Produto`; as duas com documentos; segunda execução), anotar o resultado

## Phase 4: Fechamento

- [X] T008 Analyze (spec, plano e tasks contra o código) e ajustes. Resultado em 27/11: FR-001 a FR-007 e SC-001 a SC-003 cobertos (teste novo, suíte existente sem mudança, script testado); SC-004 depende da Parte D. Nenhum achado crítico. Observação: o caminho "desfazer por falha de revisão" (FR-002) não tem teste de integração próprio; ele usa o mesmo `save` que o callback cobre.
- [X] T009 Rascunho do PR (fora do repositório, em `LhamaLog/claude/rascunhos-pr-llt124.md`, PR 0) com o aviso ao Michel (configuração do Coletor) e ao time (rodar o script antes de subir); mensagem de commit `LLT-179: grava o produto na colecao products que o coletor le`

## Dependencies

T001 → T002 → T003/T004/T005 → T006 → T007 → T008 → T009

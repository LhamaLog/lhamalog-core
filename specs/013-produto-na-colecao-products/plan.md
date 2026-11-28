# Plano Produto gravado em `products`

**Branch**: `LLT-179-core-grava-products` | **Data**: 27/11/2026 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/013-produto-na-colecao-products/spec.md`

Orientação do desenvolvedor: D1 = B (o Core grava em `products`, com `productId`); o Coletor não muda de código; branch saindo da `origin/Development` (`411f79c`).

## Summary

O `Product` passa a ser gravado em `products` (`@Document("products")`). Um `BeforeSaveCallback<Product>` novo, `ProductCollectorFields`, escreve `productId` igual ao `_id` em toda gravação, sem mudar o record. Um índice único e parcial em `productId` atende a busca do Coletor. O script mongosh de migração fica no `quickstart.md`.

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data MongoDB 5.1.1 (`BeforeSaveCallback`, `@CompoundIndex` com `partialFilter`)

**Storage**: MongoDB; coleção `Produto` renomeada para `products`

**Testing**: integração com Mongo via Testcontainers (`@CoreIntegrationTest`) e tokens Clerk reais (`TestTenants`); a suíte existente de produto roda sem mudança

**Target Platform**: API HTTP do LhamaLog Core (porta 8080)

**Project Type**: web-service

**Performance Goals**: nenhuma meta nova; a busca do Coletor usa índice

**Constraints**: constituição 2.1.0; record `Product` e API de produtos sem mudança; sem mudança em `product_tokens` e no Coletor; sem dependência Maven nova

**Scale/Scope**: 1 anotação alterada, 1 classe nova, 1 Javadoc corrigido, 1 classe de teste nova

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Spec Kit**: spec, plano, artefatos e tasks antes do código.
- **II. IA e revisão**: código escrito pela IA; decisões em `research.md`; o Pedro revisa.
- **III. Testes**: teste de integração novo cobre a gravação de `productId` e a busca do Coletor; `clean verify` antes do merge.
- **IV. Idioma**: código e comentários em inglês; documentação em português.
- **V. Segredos**: o teste usa chaves geradas na hora; o script não tem senha (o URI fica no terminal de quem roda).
- **VI. Logs**: nenhum log novo (o callback não registra nada).
- **Stack**: sem mudança.
- **Fluxo**: commits `LLT-179: <descricao>`. A branch não tem o prefixo `feature/`: segue o padrão das branches do Pedro (D4 da LLT-119). A emenda da constituição está com o time; registrado também no checklist.

Nenhuma violação nova. Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/013-produto-na-colecao-products/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md        # inclui o script de migração
├── tasks.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/product/
├── Product.java                      # @Document("products") + índice de productId
└── ProductCollectorFields.java       # novo: BeforeSaveCallback<Product>
src/main/java/br/com/lhamalog/core/ingestiontoken/infrastructure/
└── ProductCatalogAdapter.java        # só o Javadoc (Produto -> products)
src/test/java/br/com/lhamalog/core/product/
└── ProductCollectorContractIntegrationTests.java   # novo
```

**Structure Decision**: tudo no módulo `product`, onde o modelo já está; o módulo `ingestiontoken` só tem o comentário corrigido.

## Complexity Tracking

Vazio.

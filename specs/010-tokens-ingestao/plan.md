# Plano Tokens de ingestão do produto

**Branch**: `feature/LLT-179-tokens-ingestao` | **Data**: 12/11/2026 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-tokens-ingestao/spec.md`

Orientação do desenvolvedor: só hash e exibição única; rotação com prazo; gravar no contrato do Collector; sinalizar o desencontro `Produto` × `products`; token com nome, sem Produtor. A branch parte de `feature/LLT-123-editar-arquivar-produto` (precisa do `archivedAt`).

**Atualização de 18/11/2026**: a pasta nasceu como 005, mas a 005 ficou com a LLT-168 e a `Development` foi até a 009 (LLT-178). Por isso ela virou 010. Na mesma data, a branch recebeu a `Development` com a LLT-163, e três pontos mudaram:

- A sessão vem do token já validado pela camada de segurança (`CompanySessionResolver.current()`), e não mais do `Jwt` no controller. O `tenantId` passa a ser o id interno do tenant, e não o id da organização na Clerk. O serviço não mudou.
- O 403 de negócio usa `ApiStatusException` com o código `not_admin` (spec 008, FR-010). Sem código, a Web desloga a pessoa.
- Os testes de integração usam `@CoreIntegrationTest` (Testcontainers) e `TestTenants` (tokens Clerk reais), como o resto da suíte. Não usam mais `TEST_MONGODB_URI`.

## Summary

Módulo novo `br.com.lhamalog.core.ingestiontoken`, separado em `application` (modelo, portas e serviço), `infrastructure` (Mongo) e `api` (controller), no mesmo desenho do módulo `usage` do Michel. O serviço gera o token com `SecureRandom`, grava só o SHA-256 em `product_tokens` no formato que o Collector já lê, controla revogação e rotação (o antigo ganha `expiresAt`) e registra cada operação na coleção `ProductTokenAudit`. O relógio é injetado (`InstantSource`) para os testes unitários controlarem o tempo da transição.

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data MongoDB, Spring Security OAuth2 Resource Server, Bean Validation; JDK (`SecureRandom`, `MessageDigest`, `HexFormat`, `Base64`)

**Storage**: MongoDB; `product_tokens` (contrato do Collector, índice único em `tokenHash`) e `ProductTokenAudit` (nova)

**Testing**: JUnit 5 unitário com repositórios em memória (sem banco); integração com Mongo via Testcontainers (`@CoreIntegrationTest`) para o contrato de `product_tokens`; MockMvc com tokens Clerk reais (`TestTenants`) para o fluxo HTTP

**Target Platform**: API HTTP do LhamaLog Core (porta 8080)

**Project Type**: web-service

**Performance Goals**: nenhuma meta; operações administrativas raras

**Constraints**: constituição 2.1.0; nunca registrar o valor nem o hash do token; sem alterar Collector, `Produto` ou `products`; sem dependência Maven nova

**Scale/Scope**: módulo novo com 3 camadas; 1 classe de teste unitário, 1 de integração Mongo e 1 de API

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Spec Kit**: spec, plano, artefatos e tasks antes do código.
- **II. IA e revisão**: código escrito pela IA; decisões em `research.md`; o desenvolvedor revisa.
- **III. Testes**: unitários sem banco cobrem as regras; integração confere o formato lido pelo Collector; API confere o fluxo e as recusas.
- **IV. Idioma**: código em inglês (`IngestionToken`, `TokenSecrets`); documentação em português.
- **V. Segredos**: o token é segredo do cliente; nunca entra em log, auditoria, teste com valor fixo de produção ou documentação. Testes usam valores gerados na hora.
- **VI. Logs**: gerar, revogar e rotacionar registram em INFO `event=ingestion_token.created`, `event=ingestion_token.revoked` e `event=ingestion_token.rotated`, com `tenantId`, `productId` e `tokenId`. O log não leva o valor, o hash nem o id do usuário (o autor fica na auditoria).
- **Stack**: sem mudança de Java, Spring Boot ou Maven.
- **Fluxo**: branch `feature/LLT-179-tokens-ingestao`; commits `LLT-179: <descricao>`.

Nenhuma violação. Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/010-tokens-ingestao/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── proposta-adr.md
├── contracts/
│   └── api.md
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/ingestiontoken/
├── application/
│   ├── IngestionToken.java            # modelo do token, regra de validade igual à do Collector
│   ├── TokenAuditEntry.java           # registro de auditoria
│   ├── TokenSecrets.java              # gera o token e calcula o SHA-256
│   ├── IngestionTokenRepository.java  # porta
│   ├── TokenAuditRepository.java      # porta
│   ├── ProductCatalog.java            # porta: produto da empresa e se está arquivado
│   └── IngestionTokenService.java     # regras: listar, gerar, revogar, rotacionar, auditoria
├── infrastructure/
│   ├── IngestionTokenDocument.java    # documento em product_tokens
│   ├── IngestionTokenMongoRepository.java
│   ├── MongoIngestionTokenRepository.java
│   ├── TokenAuditDocument.java        # documento em ProductTokenAudit
│   ├── TokenAuditMongoRepository.java
│   ├── MongoTokenAuditRepository.java
│   └── ProductCatalogAdapter.java     # lê o Produto pelo ProductRepository
└── api/
    ├── IngestionTokenController.java
    └── TokenResponses.java            # visões sem hash e resposta com o token exibido uma vez

src/test/java/br/com/lhamalog/core/ingestiontoken/
├── application/
│   ├── InMemoryIngestionTokenRepository.java
│   ├── InMemoryTokenAuditRepository.java
│   ├── IngestionTokenServiceTest.java       # unitário, sem banco
│   └── TokenSecretsTest.java                # unitário, sem banco
├── infrastructure/
│   └── MongoIngestionTokenRepositoryIntegrationTest.java  # formato lido pelo Collector
└── api/
    └── IngestionTokenControllerIntegrationTest.java       # fluxo HTTP completo
```

**Structure Decision**: módulo novo em camadas, como o `usage`. O módulo `product` não muda; o acesso ao produto passa pela porta `ProductCatalog`. `ApiExceptionHandler` não muda: `ApiStatusException` (403 com código), `ResponseStatusException` e `OptimisticLockingFailureException` já viram as respostas certas.

## Complexity Tracking

> Sem violações da constituição.

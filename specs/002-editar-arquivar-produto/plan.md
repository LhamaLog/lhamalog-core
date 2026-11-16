# Plano Editar e arquivar Produto

**Branch**: `002-editar-arquivar-produto` | **Data**: 06/11/2026 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-editar-arquivar-produto/spec.md`

Orientação do usuário nesta etapa: campo `archivedAt` no Produto, sem alterar `status`; seguir `ProductController`, `ProductService`, concorrência por `version` e isolamento por empresa da 001.

## Summary

Acrescentar edição dos dados gerais do produto e ciclo arquivo/desarquivo no Core, reutilizando o módulo `product` da 001. Arquivado é `archivedAt` preenchido; ativo é `archivedAt` nulo. `status` permanece `CONFIGURING` nesta feature. Listagem padrão exclui arquivados; `archived=true` lista só arquivados. Mutações de cadastro e contexto recusam produto arquivado. Arquivar/desarquivar só quando `CompanySession.administrator()` (papéis em `CLERK_ADMIN_ROLES`, padrão `admin`, incluindo slug de DONO quando configurado).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Security OAuth2 Resource Server, Spring Data MongoDB, Bean Validation

**Storage**: MongoDB (coleção `Produto` existente; campo novo `archivedAt`)

**Testing**: JUnit 5, MockMvc, Testcontainers com tags de imagem fixas; persistência com `TEST_MONGODB_URI` como na 001

**Target Platform**: API HTTP do LhamaLog Core (porta 8080)

**Project Type**: web-service (backend único neste repositório)

**Performance Goals**: nenhum meta numérica além dos critérios da spec; sem SLAs novos

**Constraints**: constituição 2.0.0; inglês no código, português na documentação; sem exclusão definitiva; sem mudança de ingestão no Collector; sem interface neste repositório

**Scale/Scope**: extensão do módulo `product` (controller, service, repository, testes de integração); sem novos pacotes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Spec Kit**: spec existente; este plano e artefatos de Phase 0/1; tasks ficam para `/speckit-tasks`. Sem código de feature nesta etapa.
- **II. IA e revisão**: decisões em `research.md` e neste plano; implementação 100% permitida depois das tasks; desenvolvedor revisa.
- **III. Testes**: suíte local `.\mvnw.cmd verify`; novos casos em `CompanyProductIntegrationTests` (ou classe irmã no mesmo pacote) cobrindo edição, arquivo, filtro, papéis e isolamento. Imagens de Testcontainers com tag fixa.
- **IV. Idioma**: identificadores `archivedAt`, `update`, `archive`, `unarchive`; documentação desta pasta em português.
- **V. Segredos**: reutilizar JWT de teste e `TEST_MONGODB_URI`; nenhum token Clerk real nos artefatos.
- **Stack**: sem mudança de Java, Spring Boot ou Maven. Pacote `br.com.lhamalog.core.product`.
- **Branch de merge**: constituição pede `feature/LLT-123-<descricao>` no merge; o diretório Spec Kit permanece `specs/002-editar-arquivar-produto`.

Nenhuma violação. Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/002-editar-arquivar-produto/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── api.md
└── spec.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/product/
├── Product.java
├── ProductController.java
├── ProductService.java
├── ProductRepository.java
├── ProductAccess.java
└── ProductAccessRepository.java

src/main/java/br/com/lhamalog/core/shared/
├── CompanySession.java
├── CompanySessionResolver.java
└── ApiExceptionHandler.java

src/test/java/br/com/lhamalog/core/company/
└── CompanyProductIntegrationTests.java
```

**Structure Decision**: módulo `product` já existente (mesmo padrão da 001). Sem frontend neste repositório. Testes novos na suíte de integração Mongo já usada para produtos.

## Complexity Tracking

> Sem violações da constituição.

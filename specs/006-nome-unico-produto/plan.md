# Plano Nome único de produto por empresa

**Branch**: `feature/LLT-177-nome-unico-produto` | **Data**: 09/11/2026 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-nome-unico-produto/spec.md`

Orientação do usuário nesta etapa: garantir a unicidade com campo normalizado (minúsculas com `Locale.ROOT`, sem espaços nas pontas) e índice único composto (`tenantId` + campo normalizado) no Mongo; tratar `DuplicateKeyException` como 409 de nome em uso com mensagem própria; sem dados de produção, o antigo FR-013 vira nota no quickstart; atualizar `validatesProductNameAndAcceptsEmptyOptionalContext`, que cria 5 produtos com o mesmo nome. Restrição: nada desta feature pode apagar ou alterar o que pertence a outras branches ou features LLT.

Ajustes antes do merge (16/11/2026), pedidos na revisão: a pasta foi renumerada de `specs/003-nome-unico-produto` para `specs/006-nome-unico-produto`, porque a 003 já é `003-manter-perfil-usuario` na Development e a 007 está reservada para a LLT-183 na constituição; o plano foi alinhado à constituição 2.1.0 (princípio VI), e a recusa por nome em uso passou a gerar log no padrão `event=`; o quickstart ganhou o procedimento obrigatório antes de qualquer deploy em banco com produtos (checagem de duplicatas por empresa e backfill de `normalizedName`). Tasks T017 a T023.

## Summary

O Produto ganha `normalizedName`, derivado do nome em cada gravação que o define, e a coleção ganha o índice único `product_tenant_normalized_name` (`tenantId`, `normalizedName`). O índice é a única fonte da garantia: não há consulta prévia, o que cobre gravações simultâneas (FR-011) e faz a recusa por nome acontecer depois das recusas já existentes (FR-012). O `ProductService` traduz a `DuplicateKeyException` das duas gravações que definem o nome em 409 com mensagem própria; o handler global continua igual para as demais origens. O índice não considera `archivedAt`, então produto arquivado continua ocupando o nome (FR-006). Cada recusa por nome gera um log INFO `event=product.create.name_in_use` ou `event=product.update.name_in_use`, sem o nome digitado e sem stack trace (constituição VI).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data MongoDB 5.1.1, Spring Security OAuth2 Resource Server, Bean Validation

**Storage**: MongoDB 8.0.34 (coleção `Produto` existente; campo novo `normalizedName`; índice único novo, criado na subida por `spring.data.mongodb.auto-index-creation=true`)

**Testing**: JUnit 5, MockMvc, persistência com `TEST_MONGODB_URI` como na 001 e na 002, em banco próprio desta feature

**Target Platform**: API HTTP do LhamaLog Core (porta 8080)

**Project Type**: web-service (backend único neste repositório)

**Performance Goals**: nenhuma meta numérica além dos critérios da spec; a checagem é o próprio índice, sem consulta extra por gravação

**Constraints**: constituição 2.1.0; inglês no código, português na documentação; sem migração de dados; sem apagar ou alterar bancos, volumes ou artefatos de outras branches e features; sem limite de produtos por plano; sem mudança de ingestão no Collector; sem interface neste repositório

**Scale/Scope**: extensão do módulo `product`: um campo, um índice, dois pontos de tradução de exceção, um log de evento na recusa e testes; sem pacotes novos

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Spec Kit**: spec existente; este plano e os artefatos das fases 0 e 1; tasks ficam para `/speckit-tasks`. Sem código de feature nesta etapa.
- **II. IA e revisão**: decisões registradas em `research.md` e neste plano; implementação 100% permitida depois das tasks; o desenvolvedor revisa.
- **III. Testes**: suíte local `.\mvnw.cmd verify` com `TEST_MONGODB_URI`; classe nova de integração cobrindo a 006, inclusive o log da recusa, e ajuste do teste existente que cria nomes repetidos. Nenhuma imagem nova de Testcontainers.
- **IV. Idioma**: identificadores `normalizedName`, `normalizeName`, `product_tenant_normalized_name` em inglês; documentação desta pasta em português.
- **V. Segredos**: JWT de teste como na 001; os artefatos não trazem credenciais.
- **VI. Padrão de logs**: a recusa por nome em uso é condição esperada de negócio, então vai em INFO, sem stack trace, começando com `event=product.create.name_in_use` ou `event=product.update.name_in_use`, seguido do aviso e de `tenantId` e `productId`. O log não leva o nome digitado nem outro dado do corpo da requisição. Data com fuso, `cid`, thread e `classe.metodo` vêm de `logging.pattern.console` e `logging.pattern.file`, já configurados em `application.properties`; esta feature não muda o pattern nem outros logs.
- **Stack**: sem mudança de Java, Spring Boot ou Maven. Pacote `br.com.lhamalog.core.product`.
- **Fluxo**: a branch `feature/LLT-177-nome-unico-produto` segue o padrão; commits no formato `LLT-177: <descricao>`.

Nenhuma violação. Reavaliado após a Phase 1: o design não acrescenta dependência, pacote ou exceção à constituição. Reavaliado em 16/11/2026 contra a constituição 2.1.0, com os seis princípios, o stack e o fluxo: o único ajuste foi o log da recusa (VI). Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/006-nome-unico-produto/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── api.md
├── checklists/
│   └── requirements.md
└── spec.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/product/
├── Product.java                # normalizedName oculto na resposta, normalizeName(), índice único
└── ProductService.java         # grava e preserva normalizedName; traduz DuplicateKeyException em create e update e loga a recusa com event=

src/test/java/br/com/lhamalog/core/company/
└── CompanyProductIntegrationTests.java      # validatesProductNameAndAcceptsEmptyOptionalContext com nomes distintos

src/test/java/br/com/lhamalog/core/product/
└── ProductUniqueNameIntegrationTests.java   # novo: cenários da 006 e log da recusa
```

**Structure Decision**: módulo `product` já existente, no mesmo padrão da 001 e da 002. `ApiExceptionHandler` não muda: a recusa por nome chega a ele como `ResponseStatusException`, que já responde com o motivo informado. O único teste existente alterado é o indicado pelo usuário: só os nomes dos 5 produtos mudam, e o que ele comprova continua igual. Os demais testes de produto usam um nome por empresa. `specs/001-empresa-produtos` e `specs/002-editar-arquivar-produto` não são alterados. A renumeração para 006 só move esta pasta e atualiza as referências a ela; nenhuma pasta de outra feature muda.

## Complexity Tracking

> Sem violações da constituição.

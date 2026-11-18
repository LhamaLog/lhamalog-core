# Plano Manter contexto do produto

**Branch**: `feature/LLT-178-contexto-produto` | **Data**: 10/11/2026 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-contexto-produto/spec.md`

Orientação do usuário nesta etapa: campos da tela "Contexto para IA" do Figma; versionamento no padrão do contexto da empresa (LLT-176); texto para a IA só com o produto nesta entrega. A branch parte de `feature/LLT-177-nome-unico-produto` (a 003, publicada sobre a 002 da LLT-123). Por isso `ProductService` mantém o `saveWithUniqueName` e o `normalizedName` da 003 em todas as gravações.

Ajustes antes do merge (17/11/2026), pedidos na revisão do PR #9: a pasta foi renumerada de `specs/004-contexto-produto` para `specs/009-contexto-produto`, porque a 004 já é `004-convidar-usuario-empresa` na Development e a 006, a 007 e a 008 já estão em uso (LLT-177, LLT-173 e `008-login-organizacao-teste` da LLT-163); a branch recebeu a Development com a LLT-123, a LLT-177, o #13 e o #6 (LLT-163), e os endpoints e testes de contexto foram adaptados ao `sessions.current()` e ao `TestTenants` (tenantId interno); o plano foi alinhado à constituição 2.1.0 (princípio VI) e cada versão nova gera log INFO `event=product.context.revision.created`; a gravação da versão ganhou compensação: se ela falhar depois da gravação do produto, o cadastro é desfeito (produto e acesso removidos) e a edição devolve o contexto anterior, com log WARN `event=product.context.revision.failed` ou ERROR `event=product.context.revision.undo_failed`. O índice único `productId` + `revision` já existia desde a primeira versão. Tasks T019 a T024.

## Summary

O `Product.Context` troca os cinco campos da 001 pelos seis do Figma: `functionalDescription`, `personality` (`SIMPLE`, `STANDARD`, `DETAILED`), `responseLanguage`, `architecture`, `scope` e `sensitiveData`. O construtor compacto do record normaliza textos e listas e aplica os padrões. Cada mudança de contexto grava um documento na coleção nova `ProductContextRevision`, numerado por produto, no mesmo modelo da `TenantContextRevision` da LLT-176. Três endpoints de leitura entram no `ProductController`: lista de versões, versão por número e texto para a IA (`{prompt, executed:false}`).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data MongoDB, Spring Security OAuth2 Resource Server, Bean Validation

**Storage**: MongoDB (coleção `Produto` existente, com o subdocumento `context` reformulado; coleção nova `ProductContextRevision` com índice único `productId` + `revision`)

**Testing**: JUnit 5, MockMvc, persistência com `TEST_MONGODB_URI` como na 001 e na 002

**Target Platform**: API HTTP do LhamaLog Core (porta 8080)

**Project Type**: web-service (backend único neste repositório)

**Performance Goals**: nenhuma meta numérica; uma consulta extra (última versão) só quando o contexto muda

**Constraints**: constituição 2.1.0 (era 2.0.0 no início da feature); inglês no código, português na documentação; sem chamada à LLM; sem junção com o contexto da empresa; sem migração de dados; sem mudança de ingestão; sem interface neste repositório

**Scale/Scope**: módulo `product`: um record alterado, três arquivos novos, service e controller estendidos, um teste novo e o ajuste do campo de contexto em dois testes existentes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Spec Kit**: spec, plano, artefatos das fases 0 e 1 e tasks nesta pasta antes do código.
- **II. IA e revisão**: código escrito pela IA; decisões registradas em `research.md`; o desenvolvedor revisa cada arquivo.
- **III. Testes**: classe nova `ProductContextIntegrationTests`; os testes da 001, da 002 e da 003 que usam o campo antigo `problemSolved` passam a usar `functionalDescription`, sem mudar o que comprovam. Suíte local `.\mvnw.cmd verify` com `TEST_MONGODB_URI`.
- **IV. Idioma**: identificadores em inglês (`functionalDescription`, `ProductContextRevision`, `ProductContextPrompt`); documentação em português.
- **V. Segredos**: JWT de teste como na 001; nenhum segredo nos artefatos.
- **Stack**: sem mudança de Java, Spring Boot ou Maven. Pacote `br.com.lhamalog.core.product`.
- **VI. Padrão de logs**: cada versão nova é evento de negócio e vai em INFO com `event=product.context.revision.created`, seguido do aviso e de `tenantId`, `productId` e `revision`. A falha ao gravar a versão, com a gravação do produto desfeita, vai em WARN com `event=product.context.revision.failed` e `operation` (`create` ou `update`); se nem o desfazer funcionar, ERROR com `event=product.context.revision.undo_failed`, porque produto e versões ficam divergentes e o time precisa saber. Nenhum log leva texto do contexto (pode ter dado sensível do cliente). Data com fuso, `cid`, thread e `classe.metodo` vêm do pattern já configurado em `application.properties`.
- **Fluxo**: branch `feature/LLT-178-contexto-produto`; commits `LLT-178: <descricao>`. A branch publicada se chama `LLT-178-contexto-produto` (sem o prefixo `feature/`); trocar o nome exigiria um PR novo, e o padrão de branch ainda está sendo alinhado com o time.

Nenhuma violação. Complexity Tracking vazio. Reavaliado em 17/11/2026 contra a constituição 2.1.0: os ajustes foram o log das versões (VI) e a compensação da gravação.

## Project Structure

### Documentation (this feature)

```text
specs/009-contexto-produto/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── api.md
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/product/
├── Product.java                            # Context com os campos do Figma, Personality, normalização
├── ProductContextRevision.java             # novo: documento da versão + visões de resposta
├── ProductContextRevisionRepository.java   # novo: consultas das versões
├── ProductContextPrompt.java               # novo: texto para a IA
├── ProductService.java                     # grava versões; consulta versões e texto
└── ProductController.java                  # 3 endpoints GET novos

src/test/java/br/com/lhamalog/core/product/
├── ProductContextIntegrationTests.java     # novo: cenários da 004
└── ProductEditArchiveIntegrationTests.java # campo do contexto: problemSolved → functionalDescription

src/test/java/br/com/lhamalog/core/company/
└── CompanyProductIntegrationTests.java     # campo do contexto: problemSolved → functionalDescription
```

**Structure Decision**: mesmo módulo `product` da 001 e da 002. `ApiExceptionHandler` não muda: validação já responde 400 com `fieldErrors`, e corpo ilegível (personalidade desconhecida) já responde 400. `specs/001` e `specs/002` não são alterados.

## Complexity Tracking

> Sem violações da constituição.

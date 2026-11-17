# Implementation Plan: Login e organização de teste para desenvolvimento

**Branch**: `LLT-163-validar-o-token-do-clerk-no-core` (mesma do PR #6, padrão do Jira) | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-login-organizacao-teste/spec.md`

## Summary

No perfil `local`, a variável `DEV_TENANT_CLERK_ORG_ID`, que hoje aceita uma organização, passa a aceitar uma lista separada por vírgula. O valor atual de uma organização continua valendo. O `LocalDevConfig` normaliza a lista: tira espaços e repetidos e ignora valores que não são `org_...`, com WARN. Para cada organização, chama o `LocalDevTenantSeeder`, que já cria o Tenant `ACTIVE` sem mudar o existente. Os logs do seeder passam ao padrão `event=... | aviso | chave=valor` (constituição VI), com um evento a mais para o Tenant que já existe e não está ativo. Um teste prova que, fora do perfil `local`, nada é provisionado. Os dois 403 de negócio que ainda saem sem código (cadastro da empresa e arquivar produto) passam a ter `not_admin` (FR-010), para o Web não deslogar um membro por engano. O README ganha a configuração de várias organizações, e o `quickstart.md` desta feature vira o roteiro de validação com a Clerk real, cujo resultado vai para o PR #6 antes do merge. Decisões em [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (Temurin 25.0.5)

**Primary Dependencies**: Spring Boot 4.1.1 (data-mongodb, test). **Nenhuma dependência nova.**

**Storage**: MongoDB, coleção `tenant` (escrita só por upsert com `setOnInsert`, já existente).

**Testing**: JUnit 5, AssertJ, `ApplicationContextRunner` para os perfis, Testcontainers (tags fixas) via `@CoreIntegrationTest`, `OutputCaptureExtension` para os logs.

**Target Platform**: Core local (Docker Compose ou `mvnw.cmd spring-boot:run`) com a instância de desenvolvimento da Clerk.

**Project Type**: web-service.

**Performance Goals**: não se aplica (roda uma vez por subida, para poucas organizações).

**Constraints**: só no perfil `local`; nunca alterar um Tenant existente; nenhum segredo, login ou `org_id` real versionado; logs no padrão VI, sem dado pessoal.

**Scale/Scope**: até cerca de 10 organizações por desenvolvedor.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Situação |
|---|---|
| I. Spec Kit | specify, clarify, plan, research, data-model, contrato, quickstart e tasks antes do código. OK |
| II. Revisão | decisões em research.md; o roteiro do quickstart é a evidência revisada pelo Felipe. OK |
| III. Testes | teste de unidade da normalização da lista, integração do seeder com Testcontainers, teste de perfil; `.\mvnw.cmd clean verify` antes do merge. OK |
| IV. Idiomas | código e logs em inglês; README, spec, plano e roteiro em português. OK |
| V. Segredos | `org_id`, login e chaves só no `.env` local (ignorado); exemplos usam valores fictícios (`org_exemplo...`). OK |
| VI. Logs | eventos `dev_tenant.seed.*` no formato `event=... \| aviso \| chave=valor`; sem dado pessoal. OK |
| Stack | inalterada. OK |
| Fluxo | branch no padrão do Jira, como o resto do PR #6 (desvio já registrado no PR, a constituição pede `feature/...`). Ressalva aceita pelo Felipe. |

Re-check pós-design: sem violações novas.

## Project Structure

### Documentation (this feature)

```text
specs/008-login-organizacao-teste/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md          # roteiro de validação com a Clerk real (US2)
├── contracts/
│   └── dev-tenant-config.md
├── checklists/requirements.md
└── tasks.md               # /speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/
├── config/
│   ├── LocalDevConfig.java              # lê a lista e chama o seeder (perfil local)
│   └── DevTenantOrganizations.java      # novo: normaliza e valida a lista (sem Spring)
├── tenant/adapter/out/mongo/
│   └── LocalDevTenantSeeder.java        # logs no padrão VI e evento de Tenant não ativo
├── company/CompanyController.java        # 403 de negócio com code not_admin (FR-010)
└── product/ProductService.java           # 403 de negócio com code not_admin (FR-010)

src/main/resources/application-local.properties   # comentário da lista

src/test/java/br/com/lhamalog/core/
├── config/
│   ├── DevTenantOrganizationsTests.java  # novo: unidade
│   └── LocalDevConfigProfileTests.java   # novo: local cria o runner; prod não
├── tenant/adapter/out/mongo/
│   └── LocalDevTenantSeederTests.java    # + logs e Tenant não ativo
├── company/CompanyProductIntegrationTests.java     # + code not_admin no 403 do cadastro
├── product/ProductEditArchiveIntegrationTests.java # + code not_admin no 403 de arquivar
└── shared/BusinessForbiddenHasCodeTests.java       # novo: nenhum 403 de negócio sem code

README.md                                  # várias organizações e link do roteiro
.env.example                               # comentário da lista
```

**Structure Decision**: o projeto é o serviço único do Core, com pacotes por módulo. A normalização da lista fica em `config`, como função pura, porque é regra de configuração e não de domínio. O seeder continua no adaptador Mongo do módulo `tenant`.

## Complexity Tracking

Sem violações a justificar.

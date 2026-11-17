# Implementation Plan: Manter dados da empresa

**Branch**: `002-manter-dados-empresa` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-manter-dados-empresa/spec.md`

**Note**: Este comando não cria branch, não troca de branch e não faz commit. A spec continua identificando a entrega como `LLT-175-manter-dados-da-empresa`.

## Summary

O administrador da organização cadastra, altera e exclui o cadastro da empresa ativa. O membro da mesma organização consulta e recebe 403 ao tentar gravar ou excluir. Nenhuma operação alcança o cadastro de outra organização.

A implementação reaproveita `CompanyController`, `CompanyService` e `CompanySession.administrator()`, já alimentado por `clerk.administrator-roles` (`CLERK_ADMIN_ROLES`, padrão `admin`). A exclusão nova apaga somente os documentos do cadastro da organização da sessão: `Tenant`, `TenantLogo` e `TenantContextRevision`. Produtos, vínculos e a organização no serviço de identidade permanecem.

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Security OAuth2 Resource Server, Spring Data MongoDB, Maven Wrapper

**Storage**: MongoDB. Coleções existentes `Tenant`, `TenantLogo` e `TenantContextRevision`. `Produto` e `AcessoProduto` permanecem intactas.

**Testing**: JUnit 5 e MockMvc. Os testes HTTP novos importam `TestcontainersConfiguration` (`mongo:8.0.34`, tag fixa). `.\mvnw.cmd verify` é o portão local.

**Target Platform**: API do LhamaLog Core, perfil local ou prod, porta padrão 8080

**Project Type**: web-service

**Performance Goals**: Um cadastro por organização. A meta da spec é concluir cadastro ou alteração em até 2 minutos na operação do administrador. Sem meta nova de vazão.

**Constraints**: Organização vem só da sessão (`o.id`, com o fallback já existente `org_id`). Papel administrativo é o slug já comparado por `CompanySession`, depois de remover o prefixo `org:`. Sem segredo em código, teste ou spec. Consulta de CEP, exportação de PDF e sincronização seguem 501.

**Scale/Scope**: Uma empresa por organização. A entrega altera o controle das gravações já existentes e acrescenta a exclusão do cadastro. Sem tela nova e sem mudança de produto.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição do Core 2.0.0, conferida antes da pesquisa:

- **I. Spec Kit**: spec e esclarecimento já existem. Este comando produz o plano e o desenho. Código fica para tasks e implement.
- **II. Revisão do desenvolvedor**: as decisões ficam em `research.md`, neste plano e nos contratos. Tasks ainda não são criadas.
- **III. Testes antes do merge**: a evidência é teste HTTP no `verify`, com Testcontainers e tag fixa. Os testes atuais que gravam empresa com papel `member` passam a usar administrador, para continuarem válidos quando `TEST_MONGODB_URI` estiver definido.
- **IV. Inglês no código, português na documentação**: artefatos deste plano em português. Nomes novos de método, teste e endpoint em inglês.
- **V. Segredos**: testes usam JWT sintético do MockMvc. Nenhum token, senha ou chave entra no repositório.
- **Stack**: Java 25, Spring Boot 4.1.1 e MongoDB permanecem. Sem emenda da constituição.

**Revisão após o desenho**: contratos, modelo e quickstart mantêm o mesmo stack, o mesmo isolamento por sessão e a mesma regra de papel. A exclusão não introduz coleção, serviço externo nem segredo. O portão de teste continua sendo `.\mvnw.cmd verify`. O desvio de nome de branch registrado abaixo permanece, por instrução explícita de não criar nem trocar de branch.

## Project Structure

### Documentation (this feature)

```text
specs/002-manter-dados-empresa/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── company-registration.md
└── tasks.md              # criado por /speckit-tasks, não por este comando
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/company/
├── CompanyController.java                 # exige administrador antes de gravar ou excluir; novo DELETE
├── CompanyService.java                    # exclusão do cadastro da organização da sessão
└── CompanyContextRevisionRepository.java  # remoção das revisões dessa organização

src/main/java/br/com/lhamalog/core/shared/
├── CompanySession.java                    # administrator() já existente
└── CompanySessionResolver.java            # CLERK_ADMIN_ROLES já existente

src/main/resources/application.properties  # clerk.administrator-roles já mapeado; sem chave nova

src/test/java/br/com/lhamalog/core/company/
├── CompanyRegistrationHttpTests.java      # novo: HTTP com Testcontainers
└── CompanyProductIntegrationTests.java    # gravações de empresa passam a usar papel admin
```

**Structure Decision**: O cadastro continua no pacote `company`. O papel continua em `shared`. Não há módulo, coleção nem propriedade novos. `Produto` e `AcessoProduto` não entram no fluxo de exclusão.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Branch atual `002-manter-dados-empresa` difere de `feature/LLT-175-...` | O pedido deste comando proíbe criar ou trocar de branch | Renomear agora contraria a instrução explícita. A entrega continua identificada como LLT-175 na spec. |

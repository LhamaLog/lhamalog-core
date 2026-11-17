# Implementation Plan: Listar, editar e desativar usuários

**Branch**: `LLT-168-listar-editar-e-desativar-usuarios` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-listar-editar-usuarios/spec.md`

**Note**: Este comando não implementa a feature. A branch já é `LLT-168-listar-editar-e-desativar-usuarios`, criada a partir do HEAD de `LLT-167-convidar-usuario-para-a-empresa`. Não há pull request.

## Summary

O administrador vê a equipe na tela `/equipe` do Web, junto com os convites. Nome, e-mail e cargo vêm da organização na sessão do administrador. O Core entrega status e último acesso por `userId` dessa empresa, desativa e reativa. A pessoa desativada continua na organização. A próxima requisição autenticada dela nesta empresa é recusada mesmo com token válido, porque o Core confere a marca em todo `/api/**` com empresa.

O último acesso grava no máximo a cada 5 minutos, salvo quando o cargo do token mudou ou ainda não há registro. A troca de cargo entre `admin` e `member` é `organization.updateMember` no Web. O Core não troca cargo. O Web conta os administradores em atividade antes de rebaixar ou desativar. O Core recusa desativar o último administrador ativo que ele viu num token aceito. Não há chave secreta nem configuração nova de dashboard. O detalhe está em [research.md](research.md), no [modelo](data-model.md) e no [contrato](contracts/company-members.md).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Security OAuth2 Resource Server, Spring Data MongoDB, Maven Wrapper. Sem SDK da Clerk e sem cliente HTTP novo.

**Storage**: MongoDB. Documentos novos `CompanyMember` e `CompanyAdminCount`. As coleções já existentes permanecem. Sem Redis novo e sem segredo.

**Testing**: JUnit 5 e MockMvc, com `TestcontainersConfiguration` (`mongo:8.0.34`, tag fixa, nunca `latest`). O relógio e o intervalo de observação são injetáveis no teste. `./mvnw verify` e `.\mvnw.cmd verify` são o portão local. Esta execução não implementa e não roda o `verify`.

**Target Platform**: API do LhamaLog Core, perfil local ou prod, porta padrão 8080. A tela `/equipe` fica no Web, em outro repositório.

**Project Type**: web-service

**Performance Goals**: Uma leitura da marca por requisição autenticada com empresa. Escrita de último acesso no máximo uma vez a cada 5 minutos por pessoa e empresa, fora a escrita em que o cargo observado muda. A meta da spec é o administrador ler a linha em até 1 minuto.

**Constraints**: Sem `CLERK_SECRET_KEY`, sem valor que comece com `sk_`, sem chamada à Clerk e sem item novo de dashboard. `CLERK_ADMIN_ROLES` permanece com o padrão `admin`. Cargos continuam só `admin` e `member`. A marca não espera o intervalo de 5 minutos. O erro ganha `code` opcional (`user_deactivated`, `last_admin`, `not_admin`); sem código, o JSON segue só `message` e `fieldErrors`, e os contratos já publicados não mudam. LLT-169 a LLT-174 ficam fora, com a troca `admin`/`member` desta história no Web.

**Scale/Scope**: Três caminhos novos sob `/api/company/members` e um interceptor em `/api/**`. Uma empresa pequena: a lista do Core é o conjunto observado, não um catálogo externo.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição do Core 2.0.0, conferida antes da pesquisa:

- **I. Spec Kit**: a spec e o esclarecimento desta execução já existem. Este comando produz o plano e o desenho. Código fica para tasks e implement, e esta execução para antes do implement.
- **II. Revisão do desenvolvedor**: as decisões ficam em `research.md`, neste plano, no modelo, no contrato e nas tasks. Tasks não levam marca de autoria.
- **III. Testes antes do merge**: a evidência prevista é teste HTTP no `verify`, com Testcontainers e tag fixa. Esta execução não roda o `verify` porque não há código da feature.
- **IV. Inglês no código, português na documentação**: artefatos deste plano em português. Nomes de classe, método, campo e caminho em inglês.
- **V. Segredos**: testes usam JWT sintético do MockMvc. Nenhum token, senha ou chave entra no repositório. `CLERK_SECRET_KEY` não é criada.
- **Stack**: Java 25, Spring Boot 4.1.1 e MongoDB permanecem. Sem emenda da constituição. Sem dependência nova.
- **Fluxo de branch e commit**: o nome da branch e a mensagem de commit desta execução divergem do texto da constituição. O desvio está justificado abaixo, por instrução explícita. A constituição não é emendada aqui.

**Revisão após o desenho**: o contrato acrescenta três caminhos e dois documentos, sem SDK, sem cliente HTTP e sem segredo. A conferência da marca fica num interceptor de `/api/**`, para o token ainda válido não passar por company, produtos ou perfil. A contagem do último administrador é um documento por empresa com condição atômica, sem transação entre documentos, porque o Mongo do compose local não é replica set. O portão de teste continua sendo o `verify` com `mongo:8.0.34`. O desvio de branch e de commit permanece, pela instrução desta execução.

## Project Structure

### Documentation (this feature)

```text
specs/005-listar-editar-usuarios/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── company-members.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/member/
├── CompanyMember.java
├── CompanyMemberRepository.java
├── CompanyAdminCount.java
├── CompanyAdminCountRepository.java
├── CompanyMemberService.java
├── CompanyMemberController.java
├── MemberAccessInterceptor.java
└── MemberAccessConfig.java

src/main/java/br/com/lhamalog/core/shared/
├── CompanySession.java              # permanece a leitura atual de empresa e cargo
└── ApiExceptionHandler.java         # code opcional; ausente nos erros já publicados

src/test/java/br/com/lhamalog/core/member/
└── CompanyMemberHttpTests.java
```

**Structure Decision**: Pacote novo `member`, ao lado de `company`, `product` e `profile`. O interceptor vê todo `/api/**`. `CompanySession` e `ClerkTokenValidator` não mudam de regra. Não há tela neste repositório.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Branch `LLT-168-listar-editar-e-desativar-usuarios` em vez de `feature/LLT-168-...` | O pedido desta execução exige esse nome, a partir do HEAD de `LLT-167-convidar-usuario-para-a-empresa` | Renomear agora contraria a instrução. A entrega continua identificada como LLT-168. |
| Commit `LLT-168 Listar, editar e desativar usuários`, sem dois-pontos | O pedido exige essa mensagem exata | A constituição pede `<CHAVE>: <descrição>`. Esta execução segue a mensagem pedida e não emenda a constituição. |
| Documento `CompanyAdminCount` além de `CompanyMember` | Duas desativações simultâneas não podem zerar o administrador ativo conhecido | Contar e só depois gravar deixa as duas passarem. Transação entre documentos exige replica set, e o compose local não tem. |

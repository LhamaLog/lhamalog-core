# Implementation Plan: Melhorar a tela de perfil do usuário

**Branch**: `LLT-295-melhorar-a-tela-de-perfil-do-usuario` | **Date**: 2026-11-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/295-melhorar-a-tela-de-perfil-do-usuario/spec.md`

**Note**: Este comando não implementa a feature. A branch já existe, um commit à frente de `origin/Development` (`5b230f0`). Não há rebase, force push nem pull request.

## Summary

O perfil que já existe passa a guardar `bio` (até 160 caracteres) e `jobTitle` (até 80). Os dois são texto puro, no mesmo tratamento do nome: trim, vazio permitido, caractere de controle recusado exceto tabulação, sem quebra de linha, e o Core não interpreta HTML. Omitir o campo mantém o valor. `""` ou `null` apaga. O `GET /api/user/profile` sempre devolve `""` quando não há texto, inclusive num documento antigo que não tem as chaves.

Não há caminho novo, coleção nova nem migração. A entrega estende `UserProfile`, `UserProfileView`, `UserProfileRules` e o `PUT`/`GET` já servidos por `UserProfileService` e `UserProfileController`. O contrato vigente continua [user-profile.md](../003-manter-perfil-usuario/contracts/user-profile.md), atualizado nesta feature. O delta está em [contracts/user-profile.md](contracts/user-profile.md).

Tema, acessibilidade, foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões ficam fora do documento. O Core não ganha segredo nem cliente de conta.

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data MongoDB, Spring Security OAuth2 Resource Server, Maven Wrapper. Sem dependência nova e sem SDK da Clerk.

**Storage**: MongoDB, coleção já existente `UserProfile`. Documento sem `bio` e sem `jobTitle` continua legível. Sem migração.

**Testing**: JUnit 5 e MockMvc, na classe já existente `UserProfileHttpTests`, com `@CoreIntegrationTest`. Esse apoio importa `TestcontainersConfiguration` (`mongo:8.0.34`, tag fixa, nunca `latest`). `./mvnw verify` é o portão local. Esta rodada não o executa, porque não há código da feature.

**Target Platform**: API do LhamaLog Core, perfil local ou prod

**Project Type**: web-service

**Performance Goals**: Um perfil por pessoa, com dois textos curtos a mais. A meta da spec é confirmar bio e cargo na consulta seguinte, no mesmo fluxo, em até 1 minuto. Sem meta nova de vazão.

**Constraints**: Pessoa continua `CompanySession.userId()` (`sub`). Empresa continua obrigatória na sessão. Nome, idioma, fuso e versão otimista não mudam de regra. `bio` máximo 160 e `jobTitle` máximo 80, contados como o nome, depois do trim. Omitir mantém; `""` ou `null` apaga; consulta sem texto devolve `""`. Sem tema, sem acessibilidade, sem dado de conta e sem segredo. Sem log de bio, cargo ou nome. O 403 de sessão sem empresa permanece o que `UserProfileHttpTests.sessionRulesStayDistinct` já trava.

**Scale/Scope**: Dois campos opcionais no recurso `/api/user/profile`. Sem tela neste repositório. Sem segundo endpoint.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição do Core 2.1.0, conferida antes da pesquisa:

- **I. Spec Kit**: a spec e as cinco respostas já estão em `spec.md`. Este comando produz o plano e o desenho. Código fica para tasks e implement, e esta execução para antes do implement.
- **II. Revisão do desenvolvedor**: as decisões ficam em `research.md`, neste plano, no modelo e no contrato. Tasks descrevem o que a implementação fará, sem marca de autoria.
- **III. Testes antes do merge**: a evidência prevista estende `UserProfileHttpTests`, que já sobe Mongo `mongo:8.0.34` pelo Testcontainers. Esta execução não roda o `verify` porque não há código novo.
- **IV. Inglês no código, português na documentação**: artefatos deste plano em português. Os nomes novos no JSON e no código são `bio` e `jobTitle`.
- **V. Segredos**: nenhum token, senha ou chave entra no repositório. `CLERK_SECRET_KEY` não é criada. O nome da variável ausente não traz valor.
- **VI. Logs**: esta entrega não acrescenta log. Bio, cargo e nome são dado pessoal e não podem aparecer em log se alguém acrescentar uma linha depois.
- **Stack**: Java 25, Spring Boot 4.1.1 e MongoDB permanecem. Sem emenda da constituição.
- **Fluxo de branch e commit**: a branch já é `LLT-295-melhorar-a-tela-de-perfil-do-usuario` e a mensagem pedida nesta execução não usa dois-pontos. O desvio está justificado abaixo. A constituição não é emendada aqui.

**Revisão após o desenho**: o contrato, o modelo e o quickstart estendem o mesmo `GET` e `PUT`, o mesmo documento e o mesmo Testcontainers. Não há coleção nova, SDK, segredo, tela nem chamada externa. O portão de teste continua o `verify` com `mongo:8.0.34`. O desvio de branch e de commit permanece, pela instrução desta execução.

## Project Structure

### Documentation (this feature)

```text
specs/295-melhorar-a-tela-de-perfil-do-usuario/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── user-profile.md
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md
```

O contrato canônico do recurso, atualizado nesta entrega, permanece em `specs/003-manter-perfil-usuario/contracts/user-profile.md`.

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/profile/
├── UserProfile.java              # ganha bio e jobTitle, ambos anuláveis
├── UserProfileView.java          # os dois campos; null vira ""
├── UserProfileRules.java         # limite, texto puro, omissão e apagamento
├── UserProfileService.java       # GET e PUT passam a ler e gravar os dois
└── UserProfileController.java    # mesmos GET e PUT /api/user/profile

src/test/java/br/com/lhamalog/core/profile/
└── UserProfileHttpTests.java     # cenários novos no teste HTTP já existente
```

**Structure Decision**: Nada de pacote, classe, coleção ou caminho novo. `UserProfileRepository`, `SecurityConfig`, `ApiExceptionHandler` e `SessionRevokingAccessDeniedHandler` permanecem. O teste novo entra na classe que já usa `@CoreIntegrationTest`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Branch `LLT-295-melhorar-a-tela-de-perfil-do-usuario` em vez de `feature/LLT-295-...` | A branch já existe com esse nome e o pedido manda trabalhar nela, sem rebase | Renomear agora contraria a instrução e reescreveria o histórico já publicado. |
| Commit `LLT-295 Melhorar a tela de perfil do usuario`, sem dois-pontos | O pedido desta execução fixa essa mensagem | A constituição pede `<CHAVE>: <descrição>`. Esta execução segue a mensagem pedida e não emenda a constituição. |

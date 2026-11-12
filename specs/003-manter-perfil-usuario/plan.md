# Implementation Plan: Manter perfil do usuário

**Branch**: `LLT-174-manter-perfil-do-usuario` | **Date**: 2026-11-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-manter-perfil-usuario/spec.md`

**Note**: Este comando não implementa a feature. A branch já é `LLT-174-manter-perfil-do-usuario`, criada a partir do HEAD de `LLT-175-manter-dados-da-empresa`. Não há pull request.

## Summary

A pessoa autenticada consulta e grava o próprio nome, idioma e fuso horário. O perfil é um por pessoa, não por empresa. A sessão continua exigindo empresa selecionada. Membro e administrador gravam o próprio perfil. O cadastro da empresa não muda.

O fuso fica só no perfil. Não há painel, agendamento de varredura nem envio de e-mail neste Core, então nenhum consumidor é ligado. O idioma é gravado e devolvido; as mensagens atuais permanecem em português.

O nome gravado é o que a consulta devolve. A Clerk, neste repositório, só valida o JWT da sessão. Não há cliente nem segredo para atualizar a pessoa. Esse write-back fica em aberto e não vira integração nova. O detalhe está em [research.md](research.md).

O Web, em outro repositório, consome o contrato [user-profile.md](contracts/user-profile.md).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Security OAuth2 Resource Server, Spring Data MongoDB, Maven Wrapper. Sem SDK da Clerk e sem cliente HTTP novo.

**Storage**: MongoDB. Coleção nova `UserProfile`, um documento por pessoa. `Tenant`, `TenantLogo`, `TenantContextRevision`, `Produto` e `AcessoProduto` permanecem intactas.

**Testing**: JUnit 5 e MockMvc. Os testes HTTP novos importam `TestcontainersConfiguration` (`mongo:8.0.34`, tag fixa, nunca `latest`). `./mvnw verify` e `.\mvnw.cmd verify` são o portão local.

**Target Platform**: API do LhamaLog Core, perfil local ou prod, porta padrão 8080

**Project Type**: web-service

**Performance Goals**: Um perfil por pessoa. A meta da spec é consultar e confirmar a alteração em até 1 minuto. Sem meta nova de vazão.

**Constraints**: Pessoa vem de `sub` do JWT, via `CompanySession.userId()`. Empresa continua obrigatória na sessão (`o.id`, com o fallback já existente `org_id`); sem empresa, a mensagem segue "Selecione uma empresa para continuar." Sem segredo em código, teste ou spec. Sem escrita na Clerk. Sem painel, agenda ou e-mail novos. Fuso e idioma da empresa não são lidos nem gravados por este fluxo.

**Scale/Scope**: Três campos e a versão. Sem tela neste repositório. Sem tradução do produto. LLT-172 e LLT-173 ficam fora.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição do Core 2.0.0, conferida antes da pesquisa:

- **I. Spec Kit**: a spec e o esclarecimento já existem. Este comando produz o plano e o desenho. Código fica para tasks e implement, e esta execução para antes do implement.
- **II. Revisão do desenvolvedor**: as decisões ficam em `research.md`, neste plano, no modelo e no contrato. Tasks descrevem o que a implementação fará, sem marca de autoria.
- **III. Testes antes do merge**: a evidência prevista é teste HTTP no `verify`, com Testcontainers e tag fixa. Esta execução não roda o `verify` porque não há código da feature.
- **IV. Inglês no código, português na documentação**: artefatos deste plano em português. Nomes novos de classe, método, campo e caminho em inglês.
- **V. Segredos**: testes usam JWT sintético do MockMvc. Nenhum token, senha ou chave entra no repositório. O gap da Clerk cita o nome da variável ausente, nunca um valor.
- **Stack**: Java 25, Spring Boot 4.1.1 e MongoDB permanecem. Sem emenda da constituição.
- **Fluxo de branch e commit**: o nome da branch e a mensagem de commit desta spec divergem do texto da constituição. O desvio está justificado abaixo, por instrução explícita desta execução. A constituição não é emendada aqui.

**Revisão após o desenho**: contrato, modelo e quickstart mantêm o mesmo stack, o mesmo JWT e o mesmo isolamento por pessoa da sessão. A coleção nova não traz SDK, segredo nem chamada externa. O portão de teste continua sendo o `verify` com `mongo:8.0.34`. O write-back da Clerk permanece item em aberto, sem desenho de cliente. O desvio de branch e de commit permanece, pela instrução desta execução.

## Project Structure

### Documentation (this feature)

```text
specs/003-manter-perfil-usuario/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── user-profile.md
├── checklists/
│   └── requirements.md
├── tasks.md
└── analysis.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/profile/
├── UserProfile.java              # documento UserProfile, id = sub da sessão
├── UserProfileRepository.java    # MongoRepository por id
├── UserProfileRules.java         # validação de nome, idioma, fuso e versão
├── UserProfileService.java       # consulta e gravação; sem cliente Clerk
└── UserProfileController.java    # GET e PUT /api/user/profile

src/main/java/br/com/lhamalog/core/shared/
├── CompanySession.java           # userId já é o sub; sem mudança de regra
├── CompanySessionResolver.java   # reusado; sem papel novo
└── ApiExceptionHandler.java      # 400, 403, 409 e 503 já existentes; sem handler novo

src/main/java/br/com/lhamalog/core/config/
└── SecurityConfig.java           # anyRequest autenticado já cobre /api/**; sem mudança

src/main/resources/application.properties
                                  # chaves clerk.* atuais; sem propriedade de segredo

src/test/java/br/com/lhamalog/core/profile/
└── UserProfileHttpTests.java     # HTTP com Testcontainers
```

**Structure Decision**: O perfil entra no pacote novo `profile`, separado de `company`, para não passar pelo merge do cadastro da empresa. Sessão, erro e segurança são os que já existem. Não há módulo Maven novo.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Branch `LLT-174-manter-perfil-do-usuario` em vez de `feature/LLT-174-...` | O pedido desta execução exige esse nome, a partir do HEAD de `LLT-175-manter-dados-da-empresa` | Renomear agora contraria a instrução. A entrega continua identificada como LLT-174. |
| Commit de spec `LLT-174 Especificar manter perfil do usuário`, sem dois-pontos | O pedido reserva `LLT-174 Manter perfil do usuário` para a implementação e pede esta mensagem na spec | A constituição pede `<CHAVE>: <descrição>`. Esta execução segue a mensagem pedida e não emenda a constituição. |

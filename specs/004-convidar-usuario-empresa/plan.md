# Implementation Plan: Convidar usuário para a empresa

**Branch**: `LLT-167-convidar-usuario-para-a-empresa` | **Date**: 2026-11-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-convidar-usuario-empresa/spec.md`

**Note**: Este comando não implementa a feature. A branch já é `LLT-167-convidar-usuario-para-a-empresa`, criada a partir do HEAD de `LLT-174-manter-perfil-do-usuario`. Não há pull request.

## Summary

O administrador convida uma pessoa para a empresa como Administrador (`admin`) ou Membro (`member`). Quem cria o convite é o Web, na sessão do navegador, pelo SDK da Clerk. A Clerk envia o e-mail, guarda o convite, aplica o prazo de 7 dias, lista os pendentes, revoga e reenvia. A pessoa aceita nas telas de entrada que já existem. Não há rota nova.

O Core não envia e-mail, não chama a Clerk, não guarda convite nem vínculo e não ganha endpoint. Depois do aceite, o vínculo é a sessão: a organização vem de `o.id` (ou `org_id`) e o cargo vem de `o.rol` (ou `org_role`). Com `CLERK_ADMIN_ROLES` no padrão `admin`, `admin` e `org:admin` são administrador; `member` e `org:member` não são. Essa regra já existe em `CompanySession`. Esta entrega a trava com testes e documenta o que outra pessoa configura no dashboard da Clerk. O detalhe está em [research.md](research.md) e no [contrato](contracts/organization-invite.md).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Security OAuth2 Resource Server, Maven Wrapper. Sem SDK da Clerk, sem cliente HTTP novo e sem biblioteca de e-mail.

**Storage**: Nenhum armazenamento novo. MongoDB permanece com `Tenant`, `TenantLogo`, `TenantContextRevision`, `Produto`, `AcessoProduto` e `UserProfile`. Convite e vínculo não viram coleção.

**Testing**: JUnit 5 e MockMvc. Os testes novos de sessão não precisam de Mongo. O teste HTTP que prova membro e administrador nas rotas já existentes importa `TestcontainersConfiguration` (`mongo:8.0.34`, tag fixa, nunca `latest`). `./mvnw verify` e `.\mvnw.cmd verify` são o portão local.

**Target Platform**: API do LhamaLog Core, perfil local ou prod, porta padrão 8080

**Project Type**: web-service

**Performance Goals**: Nenhuma meta nova de vazão. O convite não passa pelo Core. A meta da spec é o administrador concluir o envio no Web em até 2 minutos.

**Constraints**: Sem endpoint de convite, aceite, listagem, revogação ou reenvio. Sem `CLERK_SECRET_KEY`, sem valor que comece com `sk_`, sem chamada à Clerk e sem envio de e-mail. `CLERK_ADMIN_ROLES` permanece com o padrão `admin`. Cargos do convite são só `admin` e `member`. Prazo de 7 dias é configuração do dashboard, feita por outra pessoa. LLT-168 a LLT-174 ficam fora.

**Scale/Scope**: Nenhum campo novo e nenhuma tela neste repositório. A entrega do Core é a prova da sessão recém-aceita e a documentação do dashboard.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição do Core 2.0.0, conferida antes da pesquisa:

- **I. Spec Kit**: a spec e o esclarecimento já existem. Este comando produz o plano e o desenho. Código fica para tasks e implement, e esta execução para antes do implement.
- **II. Revisão do desenvolvedor**: as decisões ficam em `research.md`, neste plano, no modelo e no contrato. Tasks descrevem o que a implementação fará, sem marca de autoria.
- **III. Testes antes do merge**: a evidência prevista é teste de sessão e teste HTTP no `verify`, com Testcontainers e tag fixa quando o HTTP sobe o Mongo. Esta execução não roda o `verify` porque não há código da feature.
- **IV. Inglês no código, português na documentação**: artefatos deste plano em português. Nomes de teste, método e classe em inglês.
- **V. Segredos**: testes usam JWT sintético do MockMvc. Nenhum token, senha ou chave entra no repositório. `CLERK_SECRET_KEY` não é criada. O nome da variável ausente não traz valor.
- **Stack**: Java 25, Spring Boot 4.1.1 e MongoDB permanecem. Sem emenda da constituição. Sem dependência nova.
- **Fluxo de branch e commit**: o nome da branch e a mensagem de commit desta execução divergem do texto da constituição. O desvio está justificado abaixo, por instrução explícita. A constituição não é emendada aqui.

**Revisão após o desenho**: contrato, modelo e quickstart não acrescentam coleção, endpoint, SDK, cliente HTTP nem segredo. O vínculo continua sendo a sessão já resolvida por `CompanySession`. O portão de teste continua sendo o `verify` com `mongo:8.0.34` no fluxo HTTP. O desvio de branch e de commit permanece, pela instrução desta execução.

## Project Structure

### Documentation (this feature)

```text
specs/004-convidar-usuario-empresa/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── organization-invite.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/shared/
├── CompanySession.java            # já lê o.id/o.rol e o fallback org_id/org_role
└── CompanySessionResolver.java    # já compara CLERK_ADMIN_ROLES, padrão admin

src/main/java/br/com/lhamalog/core/company/
└── CompanyController.java         # administrador já exigido para alterar o cadastro; sem rota nova

src/main/resources/application.properties
                                   # clerk.administrator-roles permanece; sem chave de segredo

src/test/java/br/com/lhamalog/core/config/
└── CompanySessionTests.java       # papéis admin, org:admin, member e org:member

src/test/java/br/com/lhamalog/core/company/
└── OrganizationInviteBoundaryTests.java
                                   # sessão recém-aceita nas rotas já existentes; sem caminho de convite
```

**Structure Decision**: Não há pacote novo, coleção nova nem controlador novo. A sessão e o cadastro da empresa permanecem onde estão. Os testes novos travam o comportamento já existente. A documentação do dashboard fica neste diretório de spec, no contrato.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Branch `LLT-167-convidar-usuario-para-a-empresa` em vez de `feature/LLT-167-...` | O pedido desta execução exige esse nome, a partir do HEAD de `LLT-174-manter-perfil-do-usuario` | Renomear agora contraria a instrução. A entrega continua identificada como LLT-167. |
| Commit `LLT-167 Convidar usuário para a empresa`, sem dois-pontos | O pedido exige essa mensagem exata | A constituição pede `<CHAVE>: <descrição>`. Esta execução segue a mensagem pedida e não emenda a constituição. |

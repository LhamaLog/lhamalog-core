# Implementation Plan: Validar e fechar a Gestão de usuários

**Branch**: `LLT-119-validar-gestao-usuarios` | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-validar-gestao-usuarios/spec.md`

**Note**: Esta feature não muda produto. Ela acrescenta uma classe de teste e os documentos da validação. A branch foi criada pelo Pedro a partir da `Development` (`a64559d`), que já contém a LLT-167 e a LLT-168. Sem commit, push nem PR nesta sessão (instrução do Pedro em 17/11).

## Summary

Provar os critérios AC-01 a AC-20 da Gestão de usuários (LLT-167 e LLT-168), cobrir com testes de aceite as três lacunas do Core (AT-01 a AT-03) e investigar o R-01 com um teste exploratório (AT-04). Também deixar o roteiro E2E pronto, confirmar ou descartar os achados R-01 a R-14 com evidência e entregar o relatório de aceite, o rascunho dos comentários de revisão e a lista de bloqueios.

A abordagem tem três frentes:

1. **Core, automatizado**: uma classe `CompanyMemberAcceptanceTests` com o mesmo arranque da `CompanyMemberHttpTests`, rodada dentro da suíte inteira (`verify`). A suíte roda também com `TEST_MONGODB_URI` para incluir Produto.
2. **Web e Clerk, manual**: o roteiro E2E-01 a E2E-18 do [quickstart.md](quickstart.md), que o Pedro executa e dita. Hoje está Bloqueado, porque o Web não está na branch da 168 e a D3 está aberta.
3. **Revisão**: a evidência em [research.md](research.md), o resultado em `relatorio-aceite.md` e os comentários em `revisao-prs.md`.

## Technical Context

**Language/Version**: Java 25 (JDK 26.0.2 instalado na máquina) no Core. Node 26.12.1 (`.node-version`) no Web, que não é executado nesta sessão.

**Primary Dependencies**: As que o Core já tem: Spring Boot 4.1.1, Spring Security Test (`SecurityMockMvcRequestPostProcessors.jwt()`), Spring Data MongoDB, JUnit 5 e Testcontainers. Nenhuma dependência nova.

**Storage**: MongoDB no container próprio do Testcontainers (`mongo:8.0.34`). Para a T009 com `TEST_MONGODB_URI`, o container local `mongo-teste` (porta 27019) com o banco dedicado `lhamalog_llt119`. Nunca `lhamalog_core` nem `lhamalog_teste`.

**Testing**: `.\mvnw.cmd -B clean verify` no PowerShell. O AT-04 usa `@RepeatedTest(20)` e `@Tag("investigacao")`. O `pom.xml` não filtra tags, então ele roda no `verify`. A falha dele fica registrada como investigação e não muda o código de produção.

**Target Platform**: A API do Core em teste (MockMvc, sem porta). No E2E, o Core local na 8080 e o Web local na 5173.

**Project Type**: web-service (Core) com validação manual do frontend (Web, outro repositório).

**Performance Goals**: Nenhuma meta nova. O AT-04 dispara 2 threads por repetição.

**Constraints**:

- Sem mudar código de produção do Core e do Web.
- Sem tocar as branches LLT-167, LLT-168 e LLT-163.
- Sem Git no `lhamalog-web`.
- Sem segredo em arquivo novo.
- Sem commit, push nem PR nesta sessão.
- Os testes seguem o contrato da 168 que está hoje na `Development`, e não o da LLT-163 (ver Clarifications da spec).

**Scale/Scope**: 1 arquivo de teste novo (4 cenários, sendo o AT-04 repetido 20 vezes) e 8 arquivos de documentação na pasta da spec.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição do Core 2.1.0 (16/11/2026):

- **I. Spec Kit**: spec, clarify, plan, tasks e analyze antes do implement. O implement só escreve teste e documentos. **Passa.**
- **II. IA implementa com revisão**: cada teste é explicado em português no `relatorio-aceite.md`, para o Pedro revisar e conseguir explicar. As decisões ficam em `research.md` e neste plano. Tasks sem autoria. **Passa.**
- **III. Testes antes do merge**: o `verify` local é a prova. A linha de base **já falha** na `Development` (R-14, anterior à 168). Esta feature não pode deixar a suíte verde sem corrigir código de outra feature, o que está fora do escopo. **Passa com ressalva**: o merge desta branch fica bloqueado até o R-14 ser resolvido por quem é dono dele (D2) ou até o time aceitar o risco por escrito. Está no checklist de fechamento.
- **IV. Inglês no código, português na documentação**: nomes de teste e helpers em inglês; specs, relatório e revisão em português. **Passa.**
- **V. Segredos**: os testes usam JWT sintético do MockMvc. O roteiro manda pegar o token só no console, sem gravá-lo. O `.env.local` do Web leva só a chave publicável (`pk_test_`). A T015 procura `sk_`, `CLERK_SECRET_KEY` e JWT nos arquivos novos. **Passa.**
- **VI. Padrão de logs**: nenhum log novo (não há código de produção). A ausência de log de negócio na 168 é o achado R-05/D5. **Passa.**
- **Stack**: sem mudança. **Passa.**
- **Fluxo (branch e commit)**: a branch `LLT-119-validar-gestao-usuarios` **não tem o prefixo `feature/`** que a constituição exige. Ela foi criada assim pelo Pedro, e a instrução é operar nela. Não há commit nesta sessão. **Violação registrada** em Complexity Tracking e no R-06/D4.

**Revisão após o desenho**: o desenho não acrescenta dependência, contrato nem código de produção. Os gates não mudam: III com ressalva (R-14) e Fluxo com a violação de branch registrada.

## Project Structure

### Documentation (this feature)

```text
specs/007-validar-gestao-usuarios/
├── spec.md                  # specify + clarify
├── plan.md                  # este arquivo
├── research.md              # Fase 0: evidência arquivo:linha de R-01 a R-14 e linha de base
├── quickstart.md            # Fase 1: passo 0 e roteiro E2E-01 a E2E-18
├── tasks.md                 # speckit-tasks
├── relatorio-aceite.md      # resultado: matriz AC, achados, bloqueios, explicação dos testes
├── revisao-prs.md           # rascunho dos comentários de revisão (não publicado)
└── checklists/
    └── requirements.md      # qualidade da spec
```

Sem `data-model.md`: a feature não tem dado persistido. As entidades (AC, AT, E2E, R, D) são linhas do relatório e estão descritas na seção Key Entities da spec. Sem `contracts/`: nenhum contrato novo. A referência é [company-members.md](../005-listar-editar-usuarios/contracts/company-members.md) (168) e [organization-invite.md](../004-convidar-usuario-empresa/contracts/organization-invite.md) (167).

### Source Code (repository root)

```text
src/test/java/br/com/lhamalog/core/member/
├── CompanyMemberHttpTests.java          # existente (Vinicius), não alterado
└── CompanyMemberAcceptanceTests.java    # novo: AT-01, AT-02, AT-03 e AT-04 (@Tag("investigacao"))
```

**Structure Decision**: Só `src/test`. O mesmo pacote `br.com.lhamalog.core.member` dá acesso ao `MemberClock` (setters de pacote), ao `CompanyMemberRepository` e ao `CompanyAdminCountRepository`. Nada em `src/main`.

## Sequência

1. **Linha de base (T001)**: feita em 17/11 às 19:47. Deu 180 testes, 1 falha (R-14) e 34 ignorados. A falha já existia antes da 168 (bisseção em `research.md`).
2. **Leitura (T002 a T004)**: Core e LLT-163 feitos no `research.md`. Web Bloqueado.
3. **Testes AT-01 a AT-03**, depois AT-04, e então a suíte inteira com e sem `TEST_MONGODB_URI` (T009).
4. **Web (T010)**: Bloqueado até a troca de branch pelo Pedro.
5. **E2E (T011 e T012)**: Bloqueado pela D3. O roteiro fica pronto no `quickstart.md`.
6. **Fechamento (T013 a T016)**: relatório, revisão, varredura de segredo e checklist de merge.

## Riscos desta validação

- **A LLT-163 entra antes do fechamento.** A classe de aceite deixa de compilar contra a cadeia nova, porque usa o `jwt()` sintético e espera "Selecione uma empresa..." no AT-03. A migração segue o que o Felipe fez na `CompanyMemberHttpTests` (`@CoreIntegrationTest` e `TestTenants`). Fica registrado como passo pendente no relatório.
- **O AT-04 fica instável.** Ele é investigação, e o número de repetições que violam a regra entra no relatório. Se ele falhar por instabilidade e não por regra, isso também é registrado.
- **O ambiente local do Pedro.** Os 34 testes com `TEST_MONGODB_URI` dependem do `mongo-teste` estar ligado. Ligar o container é seguro (é dele, e o banco usado é dedicado). Mesmo assim, ele foi encontrado parado.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Branch `LLT-119-validar-gestao-usuarios` sem `feature/` | Foi criada assim pelo Pedro, e a instrução é operar nas branches existentes | Criar ou renomear a branch mexeria em algo que o Pedro não pediu. Fica para a D4 e para o Pedro decidir antes do PR |
| Suíte não verde no merge (III) | O R-14 é de outra feature (LLT-175) e anterior à 168 | Corrigir aqui violaria o escopo ("sem mudar código de produção") e a D2 |
| Seis `[NEEDS CLARIFICATION]` na spec, acima do limite de 3 | O documento-base manda marcar D1 a D6 | Já resolvidas no clarify: D1 decidida, D2 a D6 pendentes com o time |

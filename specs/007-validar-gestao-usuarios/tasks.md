---

description: "Tasks da validação da Gestão de usuários (LLT-119)"
---

# Tasks: Validar e fechar a Gestão de usuários

**Input**: Design documents from `/specs/007-validar-gestao-usuarios/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: Pedidos explicitamente na spec (FR-002, SC-003 e SC-004): AT-01 a AT-04, só em código de teste.

**Organization**: Por história da spec. US1 é a prova automatizada no Core, US2 o roteiro E2E, US3 os achados e US4 os bloqueios.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo (arquivo diferente, sem dependência pendente)
- **[Story]**: História da spec (US1 a US4)
- Caminhos relativos à raiz do `lhamalog-core`, salvo quando indicado `lhamalog-web/`

---

## Phase 1: Setup (linha de base)

**Purpose**: Saber o estado da suíte antes de qualquer teste novo.

- [X] T001 Rodar `.\mvnw.cmd -B clean verify` sem `TEST_MONGODB_URI` na `Development` (`a64559d`) e registrar testes rodados, falhas e ignorados na seção "Linha de base" de `specs/007-validar-gestao-usuarios/relatorio-aceite.md`
- [X] T002 Isolar a falha da linha de base, se houver: rodar a classe que falha em `7acb33d` (antes da 168) e em `6ac2f01` (merge da 168), exportados com `git archive` para o scratchpad, e registrar a conclusão como R-14 em `specs/007-validar-gestao-usuarios/research.md`

---

## Phase 2: Foundational (leitura e evidência)

**Purpose**: Evidência arquivo:linha para cada achado, antes de escrever teste ou relatório.

**⚠️ CRITICAL**: Os testes de aceite (US1) e o relatório (US3, US4) dependem desta fase.

- [X] T003 [P] Ler o pacote `src/main/java/br/com/lhamalog/core/member/`, `src/main/java/br/com/lhamalog/core/shared/ApiExceptionHandler.java` e `ApiStatusException.java` e preencher R-01 a R-08 em `specs/007-validar-gestao-usuarios/research.md`
- [ ] T004 [P] Ler `lhamalog-web/src/features/team/*`, `TeamPage.tsx` e `lhamalog-web/tests/team-*.test.mjs` na branch da 168 (sem Git; o Pedro troca a branch) e preencher R-09, R-10, R-11 e R-13 em `specs/007-validar-gestao-usuarios/research.md` — **Bloqueado**: Web local na `main`; o Pedro não trocou de branch (clarify de 17/11)
- [X] T005 [P] Comparar `origin/LLT-163-validar-o-token-do-clerk-no-core` com a `Development` (pacote `member`, `CompanySession`, `.env.example`, ADR_022/ADR_122) e preencher R-12 em `specs/007-validar-gestao-usuarios/research.md`

**Checkpoint**: research.md com todos os achados lidos, ou com o motivo do bloqueio.

---

## Phase 3: User Story 1 - Prova automatizada do aceite no Core (Priority: P1) 🎯 MVP

**Goal**: Cobrir AC-08 (escrita), AC-10 e AC-18 com teste e investigar o R-01.

**Independent Test**: `.\mvnw.cmd -B test '-Dtest=CompanyMemberHttpTests,CompanyMemberAcceptanceTests'` passa, ou a falha vira achado.

- [X] T006 [US1] Criar `src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java` com o arranque da `CompanyMemberHttpTests` e os helpers copiados (`touch`, `list`, `lastAccess`, `accessOf`, `statusOf`, `current`, `organization`)
- [X] T007 [US1] AT-01 `deactivatedRequestKeepsLastAccessAndDoesNotPromoteRole` em `src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java` (AC-10)
- [X] T008 [US1] AT-02 `deactivatedAdministratorLosesMemberManagement` em `src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java` (AC-18)
- [X] T009 [US1] AT-03 `writeRoutesRequireSessionAndCompany` em `src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java` (AC-08). Se o POST sem JWT der 403, registrar como achado e não mudar o teste
- [X] T010 [US1] Rodar `CompanyMemberHttpTests` e `CompanyMemberAcceptanceTests` e registrar o resultado de AT-01 a AT-03 em `specs/007-validar-gestao-usuarios/relatorio-aceite.md`
- [X] T011 [US1] AT-04 `firstAccessRaceKeepsCounterAndOneAdministrator` com `@Tag("investigacao")` e `@RepeatedTest(20)` em `src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java`, comparando também o `CompanyAdminCount` com a contagem real (R-01)
- [X] T012 [US1] T-SUITE: `.\mvnw.cmd -B clean verify` sem `TEST_MONGODB_URI` e depois com `TEST_MONGODB_URI=mongodb://127.0.0.1:27019/lhamalog_llt119` (`docker start mongo-teste`), registrando rodados, falhas e ignorados de cada execução em `specs/007-validar-gestao-usuarios/relatorio-aceite.md`

**Checkpoint**: AC-08 a AC-10, AC-14, AC-16 a AC-18 e AC-20 com prova automatizada registrada.

---

## Phase 4: User Story 2 - Roteiro de ponta a ponta com o Web e a Clerk (Priority: P1)

**Goal**: Provar o que só existe no Web e na Clerk.

**Independent Test**: Cada E2E-xx do `quickstart.md` com resultado OK, Falhou ou Bloqueado no relatório.

- [ ] T013 [P] [US2] Em `lhamalog-web/`, na branch da 168 (troca pelo Pedro), rodar só `npm ci`, `npm test` e `npm run build` e registrar o resultado em `specs/007-validar-gestao-usuarios/relatorio-aceite.md` — **Bloqueado**: Web local na `main`
- [ ] T014 [US2] Conferir os pré-requisitos do roteiro em `specs/007-validar-gestao-usuarios/quickstart.md`: instância Clerk de dev, contas, Empresas A e B, 7 dias, `.env.local` só com `pk_test_` (D3) — **Bloqueado**: D3 pendente com o time
- [ ] T015 [US2] Executar E2E-01 a E2E-18 (o Pedro no navegador, ditando) e registrar cada um em `specs/007-validar-gestao-usuarios/relatorio-aceite.md`, sem gravar token — **Bloqueado**: D3 pendente com o time

**Checkpoint**: AC-01 a AC-07, AC-11 a AC-15, AC-17 e AC-19 com prova de ponta a ponta, ou Bloqueados com motivo.

---

## Phase 5: User Story 3 - Achados de revisão com destino (Priority: P2)

**Goal**: Cada R-xx com status, evidência e destino; comentários de revisão em rascunho.

**Independent Test**: Tabela de achados do relatório completa e `revisao-prs.md` com arquivo:linha.

- [X] T016 [US3] Preencher a tabela de achados R-01 a R-14 (status, evidência, destino sugerido) em `specs/007-validar-gestao-usuarios/relatorio-aceite.md`
- [X] T017 [P] [US3] Escrever o rascunho dos comentários de revisão por arquivo:linha para o Core (168, já mergeada: vira card ou follow-up) e para o Web (168, PR aberto) em `specs/007-validar-gestao-usuarios/revisao-prs.md`
- [X] T018 [US3] Explicar cada teste de aceite em português simples, linha a linha, na seção "Testes de aceite explicados" de `specs/007-validar-gestao-usuarios/relatorio-aceite.md` (constituição II)

---

## Phase 6: User Story 4 - Lista do que impede fechar a LLT-119 (Priority: P1)

**Goal**: Dizer se a LLT-119 fecha e, se não, o que falta.

**Independent Test**: Seção "Bloqueios" e checklist de fechamento no relatório.

- [X] T019 [US4] Consolidar a matriz AC-01 a AC-20 (status e prova), as decisões D1 a D6 e os bloqueios com responsável sugerido em `specs/007-validar-gestao-usuarios/relatorio-aceite.md`
- [X] T020 [US4] Marcar o checklist da seção 10 do documento-base (fechamento da LLT-119) em `specs/007-validar-gestao-usuarios/relatorio-aceite.md`

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T021 Procurar `sk_`, `CLERK_SECRET_KEY` com valor e JWT (`eyJ`) em `specs/007-validar-gestao-usuarios/` e em `src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java`, e varrer o Core inteiro para o E2E-18
- [X] T022 Conferir o checklist de merge da constituição (Spec Kit completo, revisão do desenvolvedor, build verde, branch e commit no padrão, sem segredo) e registrar o resultado em `specs/007-validar-gestao-usuarios/relatorio-aceite.md`
- [X] T023 Conferir com `git status` e `git diff --stat` que nada fora de `specs/007-validar-gestao-usuarios/` e de `CompanyMemberAcceptanceTests.java` mudou (sem código de produção)

---

## Dependencies & Execution Order

- **Setup (T001, T002)**: primeiro. O T002 só existe se o T001 falhar.
- **Foundational (T003 a T005)**: depois do Setup. Os três são independentes entre si [P]. O T004 depende da troca de branch do Web pelo Pedro.
- **US1 (T006 a T012)**: depende do T003. T006 → T007/T008/T009 (mesmo arquivo, então em sequência e sem [P]) → T010 → T011 → T012.
- **US2 (T013 a T015)**: T013 depende da branch do Web. T014 e T015 dependem da D3.
- **US3 (T016 a T018)**: T016 depende de T003 a T005 e de T010 a T012. T017 pode andar junto. T018 depende de T007 a T011.
- **US4 (T019, T020)**: depende de US1 a US3 (com os bloqueados registrados como tal).
- **Polish (T021 a T023)**: por último.

### Parallel Opportunities

- T003, T004 e T005 (arquivos e repositórios diferentes).
- T013 (Web) em paralelo com US1 (Core).
- T017 (`revisao-prs.md`) em paralelo com T016.

## Parallel Example: Foundational

```text
Task: "T003 Ler o pacote member e preencher R-01..R-08 em research.md"
Task: "T005 Comparar a LLT-163 com a Development e preencher R-12 em research.md"
```

## Implementation Strategy

1. **MVP = US1**: linha de base, AT-01 a AT-03, AT-04 e a suíte. É o que dá para provar sem o Web e sem a Clerk.
2. US3 e US4 em seguida, com o que estiver Bloqueado registrado como tal.
3. US2 quando o Pedro trocar o Web de branch e a D3 for resolvida.

## Comparação com a lista esperada (seção 8 do documento-base)

| Documento-base | Aqui | Diferença |
|---|---|---|
| T001 linha de base verde | T001 | A linha de base veio **vermelha** (R-14). Foi acrescentado o T002 para isolar a falha |
| T002 ler a 168 do Core (R-01..R-08) | T003 | Igual |
| T003 ler o Web (R-09..R-11, R-13) | T004 | Igual, mas Bloqueado: Web na `main` |
| T004 comparar a LLT-163 (R-12) | T005 | Igual, com a 163 atualizada em 16/11 às 23:44 |
| T005 a T007 [P] AT-01 a AT-03 | T006 a T009 | Sem [P]: os três estão no mesmo arquivo. A criação da classe virou task própria (T006). Foi acrescentado o T010 para registrar o resultado antes do AT-04 |
| T008 AT-04 | T011 | Também compara o contador com a contagem real |
| T009 T-SUITE com e sem `TEST_MONGODB_URI` | T012 | Igual. Inclui `docker start mongo-teste`, que estava parado |
| T010 Web `npm` | T013 | Igual. Bloqueado |
| T011 pré-requisitos E2E | T014 | Igual. Bloqueado (D3) |
| T012 E2E-01 a E2E-18 | T015 | Igual. Bloqueado (D3) |
| T013 relatório | T016, T019, T020 | Dividido: achados (US3), matriz e bloqueios (US4) e checklist de fechamento |
| T014 `revisao-prs.md` | T017 | Igual. Para o Core, a 168 já foi mergeada: os comentários viram follow-up ou card |
| — | T018 | Novo: explicação dos testes em português (constituição II e passo 7 do documento-base) |
| T015 sem segredo | T021 | Igual, mais a varredura do E2E-18 no Core |
| T016 checklist de merge | T022 | Igual |
| — | T023 | Novo: provar que nenhum código de produção mudou |

## Follow-up fora desta entrega

- Quando a LLT-163 entrar na `Development`: migrar a `CompanyMemberAcceptanceTests` para `@CoreIntegrationTest` e `TestTenants`, e trocar a expectativa "sem empresa" do AT-03 pelo 403 `{"error":"forbidden"}`, como o Felipe fez na `CompanyMemberHttpTests`.
- Abrir o PR desta branch só depois da confirmação do Pedro, com a D4 resolvida (a branch não tem `feature/`).

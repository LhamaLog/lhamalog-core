# Relatório de aceite: Gestão de usuários (LLT-119)

**Data**: 17/11/2026
**Branch**: `LLT-119-validar-gestao-usuarios` (= `Development` `a64559d`, que já contém a LLT-167 e a LLT-168), mais a classe de teste nova, ainda **sem commit**
**Spec**: [spec.md](spec.md) · **Evidência**: [research.md](research.md) · **Roteiro**: [quickstart.md](quickstart.md) · **Revisão**: [revisao-prs.md](revisao-prs.md)

## Veredito

**A LLT-119 não pode fechar hoje.** O que depende só do Core está provado: 5 critérios OK e mais 7 com a parte do Core OK. O resto esbarra em quatro bloqueios:

1. **D1 decidida, mas não aplicada.** O time escolheu o modelo da LLT-163. Falta a ADR, unificar o pacote `member` (o `CompanyMember` da 168 com o `member` da 163), atualizar os contratos da 167/168 e migrar os dados.
2. **A suíte do Core está vermelha na `Development`** (R-14). A falha é da LLT-175, anterior à 168, e impede o AC-20.
3. **O Web não está mergeado.** A 167 e a 168 continuam em branch (D6, com risco do R-13), e a T010 não rodou porque o Web local está na `main`.
4. **O E2E não rodou.** Faltam a instância Clerk de dev, as contas e os 7 dias (D3).

## Linha de base (T001, T002)

| Execução | Quando | Rodados | Falhas | Erros | Ignorados | Resultado |
|---|---|---|---|---|---|---|
| `verify` do Pedro no `cmd`, com `TEST_MONGODB_URI` e o `mongo-teste` **parado** | 17/11 19:32 | 189 | 1 | 34 | 0 | Ambiente: 34 erros de conexão com `127.0.0.1:27019`, e 9 relatórios antigos sem `clean` |
| `.\mvnw.cmd -B clean verify` sem `TEST_MONGODB_URI` (Testcontainers) | 17/11 19:47 | 180 | **1** | 0 | 34 | **BUILD FAILURE** |
| `CompanyRegistrationHttpTests` em `7acb33d` (antes da 168) | 17/11 | 7 | 1 | 0 | 0 | Mesma falha |
| `CompanyRegistrationHttpTests` em `6ac2f01` (merge da 168) | 17/11 | 7 | 1 | 0 | 0 | Mesma falha |

A falha é `CompanyRegistrationHttpTests.adminDeletesRegistrationAndMemberCannot:163` ("Status expected:<409> but was:<200>"). **Ela não vem da 168** e virou o achado R-14. Os 34 ignorados são os testes de Produto que exigem `TEST_MONGODB_URI`.

## Suíte com os testes de aceite (T010, T012)

| Execução | Rodados | Falhas | Erros | Ignorados | Resultado |
|---|---|---|---|---|---|
| `-Dtest=CompanyMemberHttpTests,CompanyMemberAcceptanceTests` | 29 (6 + 23) | 0 | 0 | 0 | BUILD SUCCESS |
| `clean verify` sem `TEST_MONGODB_URI` (17/11, 5min14s) | 203 | 1 | 0 | 34 | BUILD FAILURE (só o R-14) |
| `clean verify` com `TEST_MONGODB_URI=mongodb://127.0.0.1:27019/lhamalog_llt119` (17/11, 6min28s) | 203 | 1 | 0 | 0 | BUILD FAILURE (só o R-14) |

Os 203 testes são os 180 da linha de base mais os 23 novos (AT-01, AT-02, AT-03 e as 20 repetições do AT-04). Com o banco externo, os 34 de Produto (`CompanyProductIntegrationTests` 5, `ProductEditArchiveIntegrationTests` 10 e `ProductUniqueNameIntegrationTests` 19) passam com o interceptor global da 168 ativo. **Fora o R-14, a suíte está verde: 202 de 203.** O container `mongo-teste`, que estava parado, foi ligado para esta execução e continua ligado. O banco `lhamalog_llt119` tem só dados de teste e pode ser apagado.

## Matriz de aceite (AC-01 a AC-20)

Legenda: **OK** = provado; **Parcial** = a parte do Core está provada e a do Web/E2E está Bloqueada; **Bloqueado** = sem como provar hoje; **Falhou** = prova executada com resultado contrário.

| ID | Critério | Prova executada | Status |
|---|---|---|---|
| AC-01 | Admin convida com `admin`/`member` | E2E-02 não executado (D3) | **Bloqueado** |
| AC-02 | E-mail vazio/inválido recusado no Web sem chamar a Clerk | `tests/team-invitations.test.mjs` e E2E-03 não executados (Web na `main`, D3) | **Bloqueado** |
| AC-03 | Convite de 7 dias; expirado/revogado sem acesso | E2E-05/06 não executados (D3) | **Bloqueado** |
| AC-04 | Pendentes, revogar, reenviar (renova 7 dias) | E2E-04/05/07 não executados (D3) | **Bloqueado** |
| AC-05 | Quem aceita fica só na empresa do convite, com o cargo | E2E-08 não executado (D3) | **Bloqueado** |
| AC-06 | Membro não convida/lista/revoga/reenvia | E2E-16 não executado (D3) | **Bloqueado** |
| AC-07 | Lista com nome, e-mail, cargo, status, último acesso, só da empresa | Core: `listsMembersForTheAdministratorOfTheCompany` OK (só a própria empresa; o Core não devolve nome, e-mail nem cargo). Web: E2E-09 Bloqueado | **Parcial** |
| AC-08 | Membro 403 `not_admin`; sem sessão 401; sem empresa "Selecione..." | `listsMembersForTheAdministratorOfTheCompany` (GET) e `deactivatesAndReactivatesWithoutLeavingTheCompany` (POST/DELETE de membro) OK; **AT-03** (POST/DELETE sem sessão e sem empresa) OK. Atenção: muda com a LLT-163 (D1) | **OK** |
| AC-09 | Último acesso regravado só após 5 min ou troca de cargo | `keepsLastAccessUntilFiveMinutesPass` e `acceptedTokenUpdatesRoleInsideTheIntervalAndRolePathStaysUnmapped` OK | **OK** |
| AC-10 | Requisição recusada por desativação não mexe em último acesso nem cargo | **AT-01** OK | **OK** |
| AC-11 | Desativado perde acesso na próxima requisição; os outros seguem | Core: `deactivatesAndReactivatesWithoutLeavingTheCompany` OK (`/api/company`, `/api/products` e `/api/user/profile` respondem 403 `user_deactivated`; o admin segue com 200). E2E-11 Bloqueado | **Parcial** |
| AC-12 | Desativar na A não afeta a B | Core: mesmo teste, `GET /api/company` na outra organização dá 200. E2E-12 Bloqueado | **Parcial** |
| AC-13 | Repetir desativar/reativar não inverte; reativar devolve acesso | Core: mesmo teste OK. E2E-13 Bloqueado | **Parcial** |
| AC-14 | Não desativa o último admin ativo (Core e Web) | Core: `refusesToDeactivateTheLastKnownActiveAdministrator`, AT-01 (último passo) e AT-04 OK. Web: E2E-14 Bloqueado | **Parcial** |
| AC-15 | Não rebaixa o último admin ativo (Web) | `tests/team-members.test.mjs` e E2E-15 não executados | **Bloqueado** |
| AC-16 | Duas desativações simultâneas mantêm um admin | `simultaneousDeactivationKeepsOneActiveAdministrator` OK | **OK** |
| AC-17 | Troca de cargo só no Web; Core sem rota de cargo (404) | Core: `acceptedTokenUpdatesRoleInsideTheIntervalAndRolePathStaysUnmapped` (POST `/role` dá 404) OK. E2E-10 Bloqueado | **Parcial** |
| AC-18 | Admin desativado não lista e não reativa | **AT-02** OK | **OK** |
| AC-19 | Nenhum segredo versionado (Core e Web) | Core: nenhum valor `sk_`, `CLERK_SECRET_KEY=` com valor ou JWT no `HEAD`, nos arquivos novos nem no histórico da `Development`. Web: não verificado (sem a branch da 168) | **Parcial** |
| AC-20 | Suíte do Core verde em Development+168 | 203 testes com e sem `TEST_MONGODB_URI`: 202 verdes (Produto incluído) e 1 falha, o R-14, anterior à 168 | **Falhou** (por causa externa à Feature) |

**Totais**: 5 OK · 7 Parcial · 7 Bloqueado · 1 Falhou.

## Testes de aceite explicados (T018)

Arquivo: `src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java`. Só teste: nada em `src/main` mudou.

**Como a classe arranca.** As anotações `@Import(TestcontainersConfiguration.class)`, `@SpringBootTest(...)` e `@AutoConfigureMockMvc` são as mesmas da `CompanyMemberHttpTests`. Elas sobem o Core inteiro num Mongo descartável do Testcontainers (`mongo:8.0.34`), sem banco local, e dão um `MockMvc` que faz requisições HTTP falsas sem abrir porta. As propriedades desligam o envio de métricas e traces. A classe fica no pacote `member` para poder usar o `MemberClock` (que fixa o "agora") e os dois repositórios (`CompanyMemberRepository` e `CompanyAdminCountRepository`). `@BeforeEach` e `@AfterEach` devolvem o relógio ao normal, para um teste não contaminar o outro.

**O `current(org, user, role)`** monta um JWT sintético com `sub = user` e a claim `o = {id: org, rol: role}`, o mesmo formato do token v2 da Clerk que o Core lê. **O `organization()`** gera um id de empresa novo a cada teste, então os testes não se enxergam. **O `touch(...)`** faz um `GET /api/products` com aquele token: é a forma de "a pessoa usou o sistema" e passa pelo interceptor, que grava o último acesso. **O `list(...)`** chama `GET /api/company/members` como administrador. **O `lastAccess(...)`** lê na lista o `lastAccessAt` de uma pessoa.

### AT-01: `deactivatedRequestKeepsLastAccessAndDoesNotPromoteRole` (AC-10)

1. Cria uma empresa nova e fixa o relógio em 16/11/2026 às 12:00 UTC.
2. `user_admin` (admin) e `user_m` (membro) usam o sistema. O Core grava os dois, com último acesso às 12:00.
3. O admin desativa o `user_m`: 200.
4. Guarda o último acesso do `user_m` como aparece na lista (`before`).
5. Avança o relógio 10 minutos. Passou do intervalo de 5 minutos, então, se a requisição fosse aceita, o Core regravaria.
6. O `user_m` tenta usar o sistema com um token que diz **admin**. Esperado: 403, `code` `user_deactivated` e a frase "Seu acesso a esta empresa foi desativado.".
7. Na lista, o último acesso do `user_m` continua igual ao `before`, e o status continua `deactivated`.
8. Lê o documento direto no Mongo: `administrator` continua `false` e `lastAccessAt` continua 12:00. Se o Core tivesse gravado o token recusado, o membro teria virado admin.
9. Prova final: o `user_admin` tenta se desativar e recebe 403 `last_admin`. Isso só acontece se o Core ainda acha que existe um único admin ativo, ou seja, o `user_m` não foi promovido.

**Resultado**: passou. A recusa acontece antes da gravação (`MemberAccessInterceptor.java:37-40`).

### AT-02: `deactivatedAdministratorLosesMemberManagement` (AC-18)

1. Empresa nova com dois admins, `user_a` e `user_b`, que usam o sistema. O Core conhece os dois como admins ativos.
2. O `user_b` desativa o `user_a`: 200, porque sobra um admin.
3. O `user_a`, ainda com token de admin, tenta **listar**: 403 `user_deactivated`.
4. Tenta **se reativar** (`DELETE .../user_a/deactivation`): 403 `user_deactivated`.
5. Tenta **desativar o `user_b`**: 403 `user_deactivated`. O cargo de admin no token não vale para quem está desativado.
6. Pela lista do `user_b`: o `user_a` está `deactivated` e o `user_b` está `active`.

**Resultado**: passou. O interceptor barra o desativado antes do controlador, inclusive nas rotas de gestão.

### AT-03: `writeRoutesRequireSessionAndCompany` (AC-08)

1. `POST` e `DELETE` de desativação **sem token**: 401 nos dois. Era aqui que um CSRF ligado daria 403, mas o CSRF está desligado (`SecurityConfig.java:38`).
2. Monta um token **sem empresa** (só `sub = user_z`, sem a claim `o`).
3. `POST` com esse token: 403, frase "Selecione uma empresa para continuar." e **sem** `code`, como pede o contrato.
4. `DELETE` com esse token: o mesmo.

**Resultado**: passou. Atenção: na LLT-163 a sessão sem empresa vira 403 `{"error":"forbidden"}` com revogação de sessão, e este teste terá de ser migrado (D1).

### AT-04: `firstAccessRaceKeepsCounterAndOneAdministrator` (R-01, investigação)

`@Tag("investigacao")` marca o teste como exploratório. `@RepeatedTest(20)` roda o teste 20 vezes, cada vez numa empresa nova.

1. Duas threads ficam prontas (`ready`) e esperam o mesmo sinal (`start`). Com isso o primeiro acesso do `user_a` e o do `user_b`, os dois admins, saem praticamente juntos.
2. As duas requisições precisam dar 200.
3. Compara duas contagens: os registros de admin ativo no Mongo (`known`, que tem de ser 2) e o número no contador `CompanyAdminCount` (`counted`). Se o contador inflasse (o R-01), `counted` seria 3 ou mais e o teste falharia com "admin counter differs...".
4. O `user_a` desativa o `user_b`: 200.
5. O `user_a` tenta se desativar: 403 `last_admin`.
6. Sobra exatamente um admin ativo.

**Resultado**: 20 de 20 repetições passaram. O R-01 não se reproduziu. Isso é evidência probabilística: a corrida real depende de como as threads são escalonadas. A leitura do código (em `research.md`) explica por que o cenário não infla.

## Achados (R-01 a R-14)

| ID | Sev. | Status | Evidência | Destino sugerido |
|---|---|---|---|---|
| R-01 | Média | **Descartado** no cenário do documento-base. Risco residual: contador menor após queda (falha segura) e maior com dados antigos | AT-04 20/20; `CompanyMemberService.java:57-58,63,117-120,140-149` | Comentário no código (revisão #7). Sem card |
| R-02 | Média | **Risco aceito** (documentado no contrato) | `CompanySession.java:13,19`; `MemberAccessInterceptor.java:40`; `company-members.md:230`. E2E-17 Bloqueado | Na unificação da D1, a trava passa a usar a coleção `member` sincronizada |
| R-03 | Baixa | **Descartado** como falha da trava; divergência temporária aceita | `CompanyMemberService.java:82-89`; `MemberObservation.java:15`; `company-members.md:231` | Comentário no código (revisão #4) |
| R-04 | Baixa | **Confirmado**: 2 leituras por requisição (não 1), sem índice por `tenantId`, contagem a cada `ensureCount` | `MemberAccessConfig.java:20`; `CompanyMemberService.java:43,48,141`; `CompanyMember.java:9-11` | Card de melhoria (revisão #1 a #3) |
| R-05 | Baixa | **Confirmado** (padrão e falta de log) | `MemberClock.java:11-33`; `QuotaReservationService.java:30,47`; nenhum log no pacote | Card de melhoria; logs dependem da D5 |
| R-06 | Processo | **Confirmado**. A branch do Pedro também não tem `feature/` | Commits `420d4dc`, `4f32c77`, `a976683`, `bc01566`, `bd3f8da`, `4921c65`, `1ecf435` | D4 |
| R-07 | Processo | **Confirmado** e maior que no documento-base | Dois `002` na `Development`; `004`, `005`, `002` e `003` repetidos nas branches 178, 179 e 163; esta spec virou `007` | D4 |
| R-08 | Processo | **Confirmado e verificado**: nenhuma falha atribuível à 168 no estado mesclado | Bisseção do R-14; `CompanyMemberHttpTests` 6/6 | — |
| R-09 | Processo | **Pendente de confirmação** (Web na `main`) | Documento-base | D6 |
| R-10 | Baixa | **Pendente de confirmação** | Documento-base; E2E-09 Bloqueado | Vinicius (revisão #16) |
| R-11 | Info | **Pendente de confirmação** | Documento-base; E2E-07 Bloqueado | Texto na tela (revisão #17) |
| R-12 | Bloqueio | **Confirmado; D1 decidida (vale a LLT-163)** | `merge-base` = `a64559d`; diferenças de sessão sem empresa, `tenantId`, coleção `member` e `CLERK_SECRET_KEY`; ADR_022 e ADR_122 | Time: ADR, unificação, contratos e migração de dados (revisão #10 a #12) |
| R-13 | Média | **Pendente de confirmação** | Documento-base; E2E-04 Bloqueado | Vinicius, D6 (revisão #14) |
| R-14 | Bloqueio do AC-20 | **Confirmado**, anterior à 168 | `CompanyRegistrationHttpTests.java:163`; `CompanyService.java:40,98-104,131-134`; mesma falha em `7acb33d` e `6ac2f01` | Dono da LLT-175 (D2) (revisão #13) |

## Roteiro E2E (T014, T015)

Nenhum passo executado. **Todos Bloqueados** pela D3 (instância Clerk, contas e 7 dias) e pelo Web fora da branch da 168. A exceção é a parte Core do E2E-18.

| ID | Resultado | Motivo |
|---|---|---|
| E2E-01 a E2E-17, E2E-14b | ☐ Bloqueado | D3 aberta; Web na `main` |
| E2E-18 | ◐ Core OK, Web pendente | Core: varredura sem nenhum valor secreto (`git grep` e histórico da `Development`). Web: sem a branch da 168 |

## Web (T013)

**Bloqueado.** O `lhamalog-web` local está na `main` (sem `/equipe`, sem `tests/` e sem o script `test` no `package.json`). O Pedro preferiu não trocar de branch agora. Quando trocar, a ordem é `npm ci`, `npm test` e `npm run build`, sem Git. Observação: `lhamalog-web/docs/instrucoes-agentes.md` diz "não instale dependências npm automaticamente". O `npm ci` só roda com a confirmação do Pedro naquele momento.

## Decisões (D1 a D6)

| ID | Estado |
|---|---|
| D1 | **Decidida: vale o modelo da LLT-163** (resposta do Pedro em 17/11). Falta: ADR, unificação do pacote `member`, contratos da 167/168 e migração dos `CompanyMember` gravados com o id da organização |
| D2 | Pendente com o time |
| D3 | Pendente com o time |
| D4 | Pendente com o time |
| D5 | Pendente com o time |
| D6 | Pendente com o time |

## Bloqueios para fechar a LLT-119

| # | Bloqueio | Responsável sugerido |
|---|---|---|
| 1 | Registrar a D1 em ADR | Time (Michel, como autor das ADRs de Clerk) |
| 2 | Unificar o modelo de membro: `CompanyMember`/`CompanyAdminCount` (168) com a coleção `member` (163). Inclui a trava do último admin usar o cargo sincronizado (fecha o R-02) | Felipe e Vinicius |
| 3 | Atualizar os textos da 167 (FR-004, FR-014) e da 168 (FR-016, "Fronteira") que contradizem a D1 | Vinicius |
| 4 | Migrar ou descartar os `CompanyMember` com `_id` no formato org id/user id quando o `tenantId` virar o id interno (163) | Felipe |
| 5 | Deixar a suíte da `Development` verde: R-14 (LLT-175) | Dono da LLT-175 (D2) |
| 6 | Mergear a 167 e a 168 do Web na ordem da D6, sem perder a `f9a1a5f` (R-13) | Vinicius |
| 7 | Rodar a T013 no Web (`npm test` e `npm run build`) | Pedro (troca de branch) |
| 8 | Prover a instância Clerk, as contas e os 7 dias (D3) e executar o E2E | Time e Pedro |
| 9 | Decidir a D4 (inclui o `feature/` da branch do Pedro e a renumeração das specs) e a D5 (logs) | Time |
| 10 | Depois da 163: migrar a `CompanyMemberAcceptanceTests` | Pedro |

## Checklist de fechamento (seção 10 do documento-base) (T020)

- [ ] D1 resolvida pelo time e registrada em ADR. **Resolvida; ADR pendente.**
- [x] 168 do Core mergeada na `Development` (PR #10). **O tratamento do padrão de commit e branch (D4) continua pendente.**
- [ ] 167 e 168 do Web mergeadas, na ordem definida em D6.
- [ ] AC-01 a AC-20 com status OK no relatório, ou com o risco aceito por escrito. **5 OK, 7 Parcial, 7 Bloqueado, 1 Falhou.**
- [ ] Achados R-xx confirmados com dono e destino. **Status e destino sugeridos; dono depende da D2.**
- [ ] `.\mvnw.cmd verify` verde em Development+168+testes de aceite; `npm test` e `npm run build` verdes no Web. **Core vermelho por causa do R-14; Web não rodou.**
- [ ] Nenhum segredo versionado. **Core OK; Web não verificado.**
- [ ] PR da branch do Pedro aberto depois da 168, com o Spec Kit completo e revisado pelo Pedro. **A 168 já entrou; o PR espera a confirmação do Pedro e a D4.**

## Checklist de merge da constituição (T022)

| Item | Estado |
|---|---|
| Spec Kit completo | spec, clarify, plan, tasks, analyze e implement feitos. As tasks bloqueadas estão marcadas como tal |
| Revisão do desenvolvedor | Pendente: o Pedro revisa os testes (seção "Testes de aceite explicados") |
| Build e testes locais verdes | **Não**: 202/203; a única falha é o R-14 |
| Branch e commit no padrão | **Branch não**: sem `feature/`. Commit: nenhum feito nesta sessão |
| Sem segredos | OK (T021) |

## O que mudou no repositório (T023)

Só arquivos novos, nenhum commit:

- `specs/007-validar-gestao-usuarios/` (spec, plan, research, quickstart, tasks, relatório, revisão e checklist)
- `src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java`

O `.specify/feature.json` aponta para a 007, mas é ignorado pelo Git (`.specify/.gitignore:6`).

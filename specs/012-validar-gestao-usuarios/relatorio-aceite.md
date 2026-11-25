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

> **Veredito final de 25/11/2026 (T039): pode fechar.** Decisão do Pedro: "fecha tudo".
> - **19 de 20 critérios OK.** O AC-03 entra com risco aceito: a validade de 7 dias e o convite expirado não dão para conferir localmente (E2E-06).
> - **AC-17 falhou no Web** (R-17): trocar o cargo na `/equipe` dá 404 "Organization role not found". A parte do Core passou: o Core não tem rota de cargo, e o Web não chama o Core. O bug está no código do Web que já está na `Development`, fora desta branch. Segue no **card 7**, para o Felipe, e o E2E-10 é refeito depois da correção.
> - **Riscos aceitos:** E2E-06 (AC-03) e R-02 (E2E-17 não executado: depende do R-17 e de uma empresa sem Dono; a trava passa a usar o cargo sincronizado no card 2).
> - **Continuação:** os 7 cards do `cards-continuacao.md`, além da emenda da constituição pelo time (analyze, C1).
>
> **Atualização de 25/11/2026 (Phase 8):** os bloqueios 2 e 3 caíram. O 1 (ADR e unificação) continua com o time, e o 4 vai para a Phase 10. O veredito final sai na Phase 11 (T039). Detalhes na seção "Atualização com a Development (25/11)" abaixo.

## Atualização com a Development (25/11, T024 a T027)

**Branch:** `LLT-119-validar-gestao-usuarios` com a `Development` mesclada pelo Pedro (`dc05b73`, sem conflito). A `Development` já tem a LLT-163 (PR #6), a LLT-289 e a LLT-120 (PR #18). Spec renumerada de `007` para `012`.

**Testes de aceite migrados (T026).** Desde a LLT-163, o Core só aceita token assinado e grava os membros com o id interno do tenant. A classe passou a usar o mesmo arranque da `CompanyMemberHttpTests` (`@CoreIntegrationTest` e `TestTenants`). A intenção dos quatro testes não mudou. As mudanças por teste estão na seção "Testes de aceite explicados".

| Execução | Rodados | Falhas | Erros | Ignorados | Resultado |
|---|---|---|---|---|---|
| `-Dtest=CompanyMemberAcceptanceTests,CompanyMemberHttpTests,CompanyRegistrationHttpTests` (25/11) | 36 (23 + 6 + 7) | 0 | 0 | 0 | BUILD SUCCESS |
| `.\mvnw.cmd -B clean verify` (25/11, 4min46s, Testcontainers) | **460** | **0** | **0** | **0** | **BUILD SUCCESS** |

- **R-14 resolvido:** o `CompanyRegistrationHttpTests` passa (7/7). O Felipe mexeu nesse teste e no `CompanyService` na LLT-163.
- **Os 460** são os 437 da `Development` (a contagem do `verify` da LLT-120) mais os 23 de aceite. Não há mais testes ignorados: os de Produto rodam no Testcontainers, sem `TEST_MONGODB_URI`.
- **LLT-120 junto:** os testes de aceite continuam verdes com o `owner_protected` da LLT-120. As empresas de teste não têm Dono, então a desativação segue a regra do último admin.

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
| AC-01 | Admin convida com `admin`/`member` | **25/11 (E2E-02):** convites como Membro e Administrador aparecem Pendentes, e os e-mails chegaram | **OK** (25/11) |
| AC-02 | E-mail vazio/inválido recusado no Web sem chamar a Clerk | **25/11:** `tests/team-invitations.test.mjs` OK ("email and role follow the company email rule and the two invite roles", na suíte 160/160 da `Development` do Web). **E2E-03 OK:** vazio e `abc` recusados sem chamar a Clerk (o texto do vazio é "Informe um e-mail válido.") | **OK** (25/11) |
| AC-03 | Convite de 7 dias; expirado/revogado sem acesso | **25/11:** E2E-05 OK (link revogado recusado pela Clerk). E2E-06: o Web mostra 7 dias; a validade real na Clerk e o convite expirado ficam como risco aceito proposto | **OK com risco aceito** (proposto, 25/11) |
| AC-04 | Pendentes, revogar, reenviar (renova 7 dias) | **25/11:** E2E-04, E2E-05 e E2E-07 OK: os pendentes persistem, o revogado some, e o reenviar revoga o antigo e cria um novo com prazo novo | **OK** (25/11) |
| AC-05 | Quem aceita fica só na empresa do convite, com o cargo | **25/11: E2E-08 OK** (Bruno só na A como membro, Carla só na A como admin) e E2E-12 (o Bruno mantém a B) | **OK** (25/11) |
| AC-06 | Membro não convida/lista/revoga/reenvia | **25/11: E2E-16 OK** (Web sem convite nem gestão; API 403 `not_admin`) | **OK** (25/11) |
| AC-07 | Lista com nome, e-mail, cargo, status, último acesso, só da empresa | Core: `listsMembersForTheAdministratorOfTheCompany` OK (só a própria empresa; o Core não devolve nome, e-mail nem cargo). **25/11: E2E-09 OK** (três linhas, nada da B) | **OK** (25/11) |
| AC-08 | Membro 403 `not_admin`; sem sessão 401; sem empresa ~~"Selecione..."~~ 403 `forbidden` com revogação (LLT-163) | `listsMembersForTheAdministratorOfTheCompany` (GET) e `deactivatesAndReactivatesWithoutLeavingTheCompany` (POST/DELETE de membro) OK; **AT-03** (POST/DELETE sem sessão e sem empresa) OK. **25/11:** AT-03 migrado: sem empresa, agora 403 `{"error":"forbidden"}` e sessão revogada | **OK** |
| AC-09 | Último acesso regravado só após 5 min ou troca de cargo | `keepsLastAccessUntilFiveMinutesPass` e `acceptedTokenUpdatesRoleInsideTheIntervalAndRolePathStaysUnmapped` OK | **OK** |
| AC-10 | Requisição recusada por desativação não mexe em último acesso nem cargo | **AT-01** OK | **OK** |
| AC-11 | Desativado perde acesso na próxima requisição; os outros seguem | Core: `deactivatesAndReactivatesWithoutLeavingTheCompany` OK (`/api/company`, `/api/products` e `/api/user/profile` respondem 403 `user_deactivated`; o admin segue com 200). **25/11: E2E-11 OK** | **OK** (25/11) |
| AC-12 | Desativar na A não afeta a B | Core: mesmo teste, `GET /api/company` na outra organização dá 200. **25/11: E2E-12 OK** | **OK** (25/11) |
| AC-13 | Repetir desativar/reativar não inverte; reativar devolve acesso | Core: mesmo teste OK. **25/11: E2E-13 OK** | **OK** (25/11) |
| AC-14 | Não desativa o último admin ativo (Core e Web) | Core: `refusesToDeactivateTheLastKnownActiveAdministrator`, AT-01 (último passo) e AT-04 OK. Web: **25/11, E2E-14 adaptado OK**: na Empresa B (sem Dono), o último admin não se desativa pelo Web; com Dono, a linha do Dono não tem Desativar | **OK** (25/11) |
| AC-15 | Não rebaixa o último admin ativo (Web) | **25/11:** `tests/team-members.test.mjs` OK ("TeamPage refuses demoting and deactivating the last administrator..." e "two active administrators may be demoted or deactivated..."). **E2E-15 OK** na Empresa B (sem Dono), sem chamar a Clerk | **OK** (25/11) |
| AC-16 | Duas desativações simultâneas mantêm um admin | `simultaneousDeactivationKeepsOneActiveAdministrator` OK | **OK** |
| AC-17 | Troca de cargo só no Web; Core sem rota de cargo (404) | Core: `acceptedTokenUpdatesRoleInsideTheIntervalAndRolePathStaysUnmapped` (POST `/role` dá 404) OK. **25/11: E2E-10 falhou** (R-17): o Web não chama o Core (certo), mas a troca na Clerk dá 404 "Organization role not found" | **Falhou no Web**, com card 7 (25/11) |
| AC-18 | Admin desativado não lista e não reativa | **AT-02** OK | **OK** |
| AC-19 | Nenhum segredo versionado (Core e Web) | Core: nenhum valor `sk_`, `CLERK_SECRET_KEY=` com valor ou JWT no `HEAD`, nos arquivos novos nem no histórico da `Development`. Web: não verificado (sem a branch da 168). **25/11 (T031):** Web na `Development` (`309e417`), sem nenhum segredo em `src/`, `tests/`, `.env.example` e `README.md`. Só aparecem o valor falso `sk_test_shouldnotappear` em `tests/public-config.test.mjs` e o código que recusa `sk_` (`src/shared/config.ts:57`) | **OK** (25/11) |
| AC-20 | Suíte do Core verde em Development+168 | 17/11: 203 testes, 202 verdes e 1 falha (R-14, anterior à 168). **25/11:** `clean verify` com a `Development` atual: 460 testes, 0 falhas, 0 erros, 0 ignorados | **OK** (25/11) |

**Totais (17/11)**: 5 OK · 7 Parcial · 7 Bloqueado · 1 Falhou.
**Totais (25/11, depois da Phase 8)**: 6 OK · 7 Parcial · 7 Bloqueado · 0 Falhou. O AC-20 passou para OK. A matriz final sai na T036.
**Totais (25/11, depois da Phase 9)**: 7 OK · 8 Parcial · 5 Bloqueado · 0 Falhou. O AC-19 passou para OK, e o AC-02 e o AC-15 passaram para Parcial (testes do Web OK; falta o E2E).
**Totais (25/11, Phase 10 parcial, sem Bruno e Carla)**: 13 OK (o AC-03 com risco aceito proposto) · 5 Parcial (AC-07, 11, 12, 13, 17) · 2 Bloqueado (AC-05, 06) · 0 Falhou. Passaram para OK: AC-01, 02, 03, 04, 14 e 15.
**Totais finais (25/11, T036)**: 19 OK (o AC-03 com risco aceito) · 0 Parcial · 0 Bloqueado · **1 Falhou no Web (AC-17, R-17, card 7)**. Passaram para OK: AC-05, 06, 07, 11, 12 e 13. O E2E-17 não foi executado (R-02, risco aceito).

## Testes de aceite explicados (T018)

Arquivo: `src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java`. Só teste: nada em `src/main` mudou.

> **Migração de 25/11 (T026), o que mudou:**
> - **Arranque:** as três anotações viraram uma só, `@CoreIntegrationTest`, a mesma de todos os testes de integração do Core desde a LLT-163. Ela sobe o mesmo Core no Testcontainers e acrescenta a ligação de teste com a Clerk (chaves de teste e um "falso" da revogação de sessão).
> - **`current(org, user, role)`:** antes montava um JWT falso, sem assinatura. Agora usa o `TestTenants.session(...)`, que gera um **token assinado** pelas chaves de teste (`TestJwks`) e cria um tenant ATIVO para a organização. Desde a LLT-163, o Core recusa token sem assinatura válida e empresa sem tenant.
> - **Id do tenant:** a `CompanyMember` passou a ser gravada com o **id interno** do tenant, e não mais com o id da organização da Clerk. Por isso o AT-01 e o AT-04 leem o banco com `tenants().tenantId(organization)`.
> - **AT-03:** veja a seção dele.
> - **AT-04:** o tenant é criado **antes** da corrida, para as duas requisições disputarem só o registro do membro, que é o que o teste investiga.
>
> O texto abaixo é o de 17/11. Ele continua valendo para a intenção e para os passos de cada teste.

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

**Migrado em 25/11:** os passos 1 e 2 continuam iguais. O token sem empresa agora é **assinado** (`TestTenants.withoutOrganization("user_z")`). Nos passos 3 e 4, a resposta esperada é **403 `{"error":"forbidden"}`**: a camada de segurança da LLT-163 recusa antes de chegar ao controlador de membros. No fim, o teste confere no "falso" da revogação (`RecordingSessionRevocation`) que as sessões do `user_z` foram revogadas, porque na vida real o Core manda a Clerk derrubar as sessões dessa pessoa. **Resultado:** passou.

### AT-04: `firstAccessRaceKeepsCounterAndOneAdministrator` (R-01, investigação)

`@Tag("investigacao")` marca o teste como exploratório. `@RepeatedTest(20)` roda o teste 20 vezes, cada vez numa empresa nova.

1. Duas threads ficam prontas (`ready`) e esperam o mesmo sinal (`start`). Com isso o primeiro acesso do `user_a` e o do `user_b`, os dois admins, saem praticamente juntos.
2. As duas requisições precisam dar 200.
3. Compara duas contagens: os registros de admin ativo no Mongo (`known`, que tem de ser 2) e o número no contador `CompanyAdminCount` (`counted`). Se o contador inflasse (o R-01), `counted` seria 3 ou mais e o teste falharia com "admin counter differs...".
4. O `user_a` desativa o `user_b`: 200.
5. O `user_a` tenta se desativar: 403 `last_admin`.
6. Sobra exatamente um admin ativo.

**Resultado**: 20 de 20 repetições passaram. O R-01 não se reproduziu. Isso é evidência probabilística: a corrida real depende de como as threads são escalonadas. A leitura do código (em `research.md`) explica por que o cenário não infla.

## Achados (R-01 a R-17)

| ID | Sev. | Status | Evidência | Destino sugerido |
|---|---|---|---|---|
| R-01 | Média | **Descartado** no cenário do documento-base. Risco residual: contador menor após queda (falha segura) e maior com dados antigos | AT-04 20/20; `CompanyMemberService.java:57-58,63,117-120,140-149` | Comentário no código (revisão #7). Sem card |
| R-02 | Média | **Risco aceito** (documentado no contrato) | `CompanySession.java:13,19`; `MemberAccessInterceptor.java:40`; `company-members.md:230`. E2E-17 não executado (25/11); risco aceito pelo Pedro no veredito | Na unificação da D1, a trava passa a usar a coleção `member` sincronizada |
| R-03 | Baixa | **Descartado** como falha da trava; divergência temporária aceita | `CompanyMemberService.java:82-89`; `MemberObservation.java:15`; `company-members.md:231` | Comentário no código (revisão #4) |
| R-04 | Baixa | **Confirmado**: 2 leituras por requisição (não 1), sem índice por `tenantId`, contagem a cada `ensureCount` | `MemberAccessConfig.java:20`; `CompanyMemberService.java:43,48,141`; `CompanyMember.java:9-11` | Card de melhoria (revisão #1 a #3) |
| R-05 | Baixa | **Confirmado** (padrão e falta de log) | `MemberClock.java:11-33`; `QuotaReservationService.java:30,47`; nenhum log no pacote | Card de melhoria; logs dependem da D5 |
| R-06 | Processo | **Confirmado**. A branch do Pedro também não tem `feature/` | Commits `420d4dc`, `4f32c77`, `a976683`, `bc01566`, `bd3f8da`, `4921c65`, `1ecf435` | D4 |
| R-07 | Processo | **Confirmado** e maior que no documento-base | Dois `002` na `Development`; `004`, `005`, `002` e `003` repetidos nas branches 178, 179 e 163; esta spec virou `007` | D4 |
| R-08 | Processo | **Confirmado e verificado**: nenhuma falha atribuível à 168 no estado mesclado | Bisseção do R-14; `CompanyMemberHttpTests` 6/6 | — |
| R-09 | Processo | **Resolvido (25/11)**: a 122, a 176, a 174, a 167 e a 168 do Web já estão na `Development` | Web `Development` `309e417`: `src/features/team/*`, `/equipe`, `tests/team-*.test.mjs` | — |
| R-10 | Baixa | **Confirmado no código (25/11)**: a coluna E-mail usa o `publicUserData.identifier` | `src/features/team/members.ts:23-24,39` (`memberEmail(clerk.identifier)`). Só aparece como problema se a instância aceitar login por telefone ou username | Card de melhoria (revisão #16) |
| R-11 | Info | **Confirmado no código (25/11)**: Reenviar revoga os pendentes e cria outro convite; a tela diz "Ele expira em 7 dias", mas não avisa que o link antigo deixa de valer | `src/features/team/invitations.ts:46-56` (`resendPlan`); `TeamPage.tsx:262-293` | Texto na tela (revisão #17), card de melhoria |
| R-12 | Bloqueio | **Confirmado; D1 decidida (vale a LLT-163)** | `merge-base` = `a64559d`; diferenças de sessão sem empresa, `tenantId`, coleção `member` e `CLERK_SECRET_KEY`; ADR_022 e ADR_122 | Time: ADR, unificação, contratos e migração de dados (revisão #10 a #12) |
| R-13 | Média | **Descartado no código (25/11)**: a lista de convites carrega por uma chave estável (`teamLoadKey`) e lê a organização por `organizationRef`, e não pelo objeto da Clerk, que muda a cada renderização. Esse é o padrão que evita o carregamento infinito. Sem Git no Web, não dá para confirmar o commit `f9a1a5f`, mas o comportamento está coberto. No teste manual da LLT-120 (25/11), a lista de convites carregou e parou ("Não há convite pendente.") | `TeamPage.tsx:165,179-204`; `tests/team-members.test.mjs` ("switching organization drops the previous invitation...") | E2E-04 confirma (Phase 10) |
| R-14 | Bloqueio do AC-20 | **Resolvido (25/11)**: `CompanyRegistrationHttpTests` 7/7 com a `Development` atual (LLT-163). Em 17/11: confirmado, anterior à 168 | `CompanyRegistrationHttpTests.java:163`; `CompanyService.java:40,98-104,131-134`; mesma falha em `7acb33d` e `6ac2f01` | Dono da LLT-175 (D2) (revisão #13) |
| R-15 | Info | **Novo, confirmado no E2E-05 (25/11)**: convite sem `redirectUrl`; o link do e-mail abre o portal da Clerk em inglês, e as mensagens em português de ticket não aparecem | `TeamPage.tsx:231,287`; `invitations.ts:71-74` | Card 5 (Felipe) |
| R-16 | Info | **Novo, confirmado no E2E-14 (25/11)**: o `confirm` do Desativar vem antes da trava do último admin | `TeamPage.tsx:449` e `:461-472` | Card 6 |
| R-17 | Alta | **Novo, confirmado no E2E-10 (25/11)**: trocar cargo na `/equipe` falha com 404 "Organization role not found", porque o Web manda `admin`/`member` em vez de `org:admin`/`org:member` | `TeamPage.tsx:410`; `members.ts:27-29`; `TeamPage.tsx:612-615` | Bug no Web (dono da 168; avisar o Felipe) |

## Roteiro E2E (T014, T015)

**Situação de 17/11:** nenhum passo executado. **Todos Bloqueados** pela D3 (instância Clerk, contas e 7 dias) e pelo Web fora da branch da 168. A exceção é a parte Core do E2E-18.

| ID | Resultado | Motivo |
|---|---|---|
| E2E-01 a E2E-17, E2E-14b | ☐ Bloqueado | D3 aberta; Web na `main` |
| E2E-18 | ◐ Core OK, Web pendente | Core: varredura sem nenhum valor secreto (`git grep` e histórico da `Development`). Web: sem a branch da 168 |

### Execução de 25/11/2026 (Phase 10, T033 e T034)

**Ambiente:** Core na `LLT-119-validar-gestao-usuarios` (`dc05b73`, mesmo código de produção da `Development`), banco `lhamalog_llt119_local` no `mongo-teste`, sem `CLERK_SECRET_KEY`, OTLP desligado. Web na `Development` (`309e417`), `npm run dev` na 5173. Instância Clerk de desenvolvimento do time. Executado no Chrome do Pedro, por automação de navegador.

**Adaptações:**
- **Ana = conta do Pedro**, que já estava logada no Chrome. O agente não digita senha nem cria conta na Clerk (regra de segurança do agente), então não houve uma conta nova para a Ana. A **Empresa A** é a organização nova `Empresa A LLT-119`, criada pelo Pedro e ativada pelo "Simular pagamento" (plano Pro, simulador local).
- Pela LLT-120, quem cria a empresa é o **Dono**, e não "Administrador" como o roteiro foi escrito.
- **Bruno e Carla** são apelidos novos do Gmail do Pedro (`+bruno119` e `+carla119`). As contas nascem quando o Pedro fizer o cadastro pelo link do convite.
- Para ter três sessões no mesmo Chrome, cada pessoa usa um endereço: `localhost:5173` (Pedro/Ana), `bruno.localhost:5173` e `carla.localhost:5173`. O Core local aceita as quatro origens (`CLERK_AUTHORIZED_PARTIES`).

| ID | Resultado | Evidência |
|---|---|---|
| E2E-01 | ✅ OK | `/equipe` carrega com as seções Convites pendentes e Usuários. O Pedro aparece como **Dono**, Ativo, com último acesso 25/11/2026 14:31 |
| E2E-02 | ✅ OK | Convites para `+bruno119` (Membro) e `+carla119` (Administrador) aparecem como Pendentes, validade 02/12/2026. Os dois e-mails "[Development] Invitation to join Empresa A LLT-119" chegaram no Gmail |
| E2E-03 | ✅ OK, com diferença de texto | Vazio e `abc` são recusados sem nenhuma chamada de convite à Clerk (rede conferida). O texto é "Informe um e-mail válido." nos **dois** casos (`src/features/team/validation.ts:11`); o roteiro esperava "Informe o e-mail." para o vazio |
| E2E-04 | ✅ OK | Depois de recarregar, os dois pendentes continuam na lista, sem carregamento infinito. R-13 descartado também no navegador |
| E2E-05 | ✅ OK | Convite para `+revogar119` enviado e revogado ("Convite revogado."; some dos pendentes). O link do e-mail abre `…accounts.dev/sign-up` com "This invitation has been revoked and cannot be used anymore." **Achado R-15** abaixo |
| E2E-06 | ⚠ Risco aceito (proposto) | O Web mostra validade de 7 dias (02/12/2026), calculada como envio + 7 dias. O objeto do convite na Clerk (Frontend API) não traz `expiresAt`, e o e-mail não cita prazo. O dashboard da Clerk não foi aberto. Registro: "validade padrão da Clerk configurada pelo time". O convite expirado não dá para esperar 7 dias |
| E2E-07 | ✅ OK | Reenviar o convite da Carla revoga o antigo (confirmado na lista `revoked` da Clerk), cria um novo com `createdAt` novo (prazo recomeça) e chega um novo e-mail. O link antigo fica revogado e é recusado como no E2E-05 |
| E2E-18 | ✅ OK (Core e Web) | Core (25/11): a busca do quickstart não acha nenhum valor; só o placeholder `<sua secret key...>` em `specs/002-validacao-token-clerk/quickstart.md:19` e o próprio texto deste relatório. `.env` é ignorado pelo Git. Web: T031 |
| E2E-14 (adaptado) | ◐ Parte do último admin OK; desativar a Carla pendente | Com Dono, a regra do último admin nunca dispara: o Dono é sempre admin ativo e a linha dele não tem "Salvar cargo" nem "Desativar" (Empresa A: só o botão "Cargos"). A trava foi testada na **Empresa B (Peters Crew)**, que não tem Dono e onde o Pedro é o único admin: Desativar a si mesmo mostra "Não é possível desativar o último administrador." e nenhuma chamada ao Core sai. Para ter segurança, a rede de rebaixar/desativar ficou interceptada no navegador durante o teste, e nada foi interceptado. **Achado R-16** abaixo |
| E2E-15 | ✅ OK | Peters Crew, Pedro único admin: escolher Membro e "Salvar cargo" mostra "Não é possível rebaixar o último administrador.", sem nenhuma chamada à Clerk; o cargo continua `org:admin`. Na Empresa A, o Dono não tem controle de cargo (LLT-120) |
| E2E-08 | ✅ OK | **25/11, fim da tarde.** O Pedro cadastrou o Bruno e a Carla pela `/sign-up` do Web em `bruno.localhost` e `carla.localhost` (o portal da Clerk recusou as senhas dele, ver R-15) e aceitou os convites. A Clerk mostra o Bruno só na `Empresa A LLT-119` como `org:member` e a Carla só na A como `org:admin`. O "sem perder a B" ficou para o E2E-12, porque o Bruno ainda não estava na B |
| E2E-09 | ✅ OK | `/equipe` da Ana mostra três linhas: Carla (Administrador, Ativo, 25/11/2026 17:40), Bruno (Membro, Ativo, 17:37) e Pedro (Dono, Ativo, 17:40). Nada da Peters Crew. A coluna E-mail mostra o e-mail (`identifier`, R-10). Bruno e Carla não informaram nome, então a coluna Nome repete o e-mail |
| E2E-10 | ❌ **Falhou** | Ana escolhe Administrador na linha do Bruno e clica em "Salvar cargo". A Clerk responde **404 "Organization role not found"** (`PATCH /v1/organizations/…/memberships/…`), a tela mostra esse texto em inglês e o Bruno continua Membro. Nenhuma chamada a `/api/company/members/{id}/role` (essa parte está certa). Causa: `TeamPage.tsx:410` chama `organization.updateMember({ role: next })` com `'admin'`/`'member'` (`members.ts:27-29`, `TeamPage.tsx:147,612-615`), mas a instância só tem `org:admin`/`org:member`, como o convite usa (`types.ts:1`). Prova: pelo console da Ana, `updateMember` com `org:admin` funcionou, e o cargo voltou para `org:member` em seguida. Nenhum teste do Web cobre o `updateMember`. **Achado R-17** abaixo |
| E2E-11 | ✅ OK | Ana desativa o Bruno ("Usuário desativado."; status Desativado; `POST …/deactivation` 200). O Bruno, com a sessão aberta, abre `/produtos` e vê "Seu acesso a esta empresa foi desativado.". A Carla abre `/produtos` normalmente, e a Ana segue na `/equipe` |
| E2E-12 | ✅ OK | A Ana convidou o Bruno para a Peters Crew como Membro (pela Clerk, conta da Ana, que é admin da B), e o convite foi aceito na sessão do Bruno. Desativado na A, o Bruno troca pelo seletor de organização para a Peters Crew e abre `/produtos` normalmente. **O Bruno ficou como membro da Peters Crew**: remover depois, se o Pedro quiser |
| E2E-13 | ✅ OK | Com o Bruno já desativado, um segundo `POST …/deactivation` (token da Ana, pela página) dá 200 e continua `deactivated`. "Reativar" na tela dá "Acesso reativado." e Ativo; um segundo `DELETE …/deactivation` dá 200 e continua `active`. De volta à A, o Bruno abre `/produtos` normalmente |
| E2E-14 (Carla) | ✅ OK | Ana desativa a Carla ("Usuário desativado."; Desativado). A Carla abre `/produtos` e vê "Seu acesso a esta empresa foi desativado.". A Ana (Dono) não tem Desativar na própria linha; a trava do último admin já tinha passado na Peters Crew. A Carla foi reativada no fim (Ativo) |
| E2E-14b | ✅ Registrado (leitura do código) | O Web conta os admins pela Clerk (`TeamPage.tsx:362-375`, `members.ts:46-55`): todo `org:admin` conta, menos quem o Core marca como `deactivated`. Um admin que aceitou e ainda não acessou (sem `CompanyMember`) **conta** como admin ativo no Web. No Core, ele só entra na contagem no primeiro acesso (R-03). Não testado no navegador: precisaria de outra conta, e na A a regra não vale (tem Dono) |
| E2E-16 | ✅ OK | O Bruno (Membro) abre `/equipe` e vê "Você não tem acesso. Selecione uma empresa válida.", sem convite e sem gestão. `GET /api/company/members` com o token dele dá **403** `{"code":"not_admin","message":"Somente o administrador da empresa pode gerenciar os usuários."}`. Observação: a mensagem do Web para o Membro fala em "empresa válida", o que engana um pouco |
| E2E-17 | ⚠ Não executado; R-02 como risco aceito | Rebaixar a Carla pelo Web não funciona (R-17). Além disso, na Empresa A a Ana é Dono e a trava do último admin não vale, então o R-02 só se reproduziria numa empresa sem Dono. O R-02 já é risco aceito no contrato da 168 e fecha no card 2. Aceito pelo Pedro no veredito (25/11) |

**Achado R-17 (Alta, Web):** trocar o cargo na `/equipe` sempre falha. O `updateMember` manda `'admin'`/`'member'` (`TeamPage.tsx:410`, valor vindo de `roleToUpdate` em `members.ts:27-29` e das opções em `TeamPage.tsx:612-615`), mas a instância Clerk só conhece `org:admin`/`org:member`. A Clerk responde 404 "Organization role not found", que aparece em inglês na tela. Correção provável: mandar `org:${next}` no `updateMember` e criar um teste para isso. Isso também quebra a trava de rebaixar só no caminho feliz: o bloqueio do último admin (E2E-15) continua certo, porque acontece antes da chamada. Destino sugerido: bug para o dono da LLT-168 no Web (Vinicius). A integração com a Clerk é do Felipe, por isso vale avisar os dois. Bloqueia o AC-17 e o E2E-17.

**Achado R-16 (Info):** no Desativar, o Web pergunta "Desativar …?" (`window.confirm`, `TeamPage.tsx:449`) **antes** de conferir a trava do último admin (`:461-472`). Quem é o último admin confirma e só depois recebe a recusa. Destino sugerido: card de melhoria (checar a trava antes do `confirm`). Junto vai uma observação de teste: o `confirm()` do navegador trava a automação por extensão.

**Achado R-15 (Info):** o convite é criado sem `redirectUrl` (`TeamPage.tsx:231` e `:287`). Por isso, o link do e-mail leva ao portal da Clerk (`accounts.dev`, em inglês), e não à tela de cadastro do LhamaLog. As mensagens em português de convite expirado ou revogado (`invitationTicketErrors`, `src/features/team/invitations.ts:71-74`) não aparecem nesse caminho. Destino sugerido: card de melhoria para o Felipe (integração com a Clerk), passando `redirectUrl` para a `/sign-up` do Web.

## Web (T013)

**25/11/2026 (T029 a T031):** o Web está na `Development` atualizada pelo Pedro (`309e417`). O `node_modules` bate com o `package-lock.json`, só faltam os binários opcionais de outros sistemas, então não precisou de `npm ci`.

| Execução | Rodados | Passaram | Falhas | Ignorados | Resultado |
|---|---|---|---|---|---|
| `npm test` | 160 | 160 | 0 | 0 | OK |
| `npm run build` (`tsc --noEmit` + Vite) | — | — | — | — | OK (só o aviso de pacote JS acima de 500 kB) |

Achados do Web: R-09 resolvido, R-10 e R-11 confirmados no código e R-13 descartado (tabela de achados). Segredos (AC-19): nenhum.

O texto abaixo é o de 17/11.

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

**Atualização final de 25/11/2026 (T037):**

| ID | Estado em 25/11 |
|---|---|
| D1 | **Aplicada no código** pela LLT-163 (PR #6): sessão validada pelo Core, `tenantId` interno, coleção `member`. **Pendentes com o time:** ADR (card 1), unificação com a `CompanyMember` (card 2) e migração dos dados antigos (card 3) |
| D2 | **Pendente com o time.** Os responsáveis sugeridos estão em `cards-continuacao.md`. O R-14 (LLT-175) foi resolvido, então sai da lista |
| D3 | **Resolvida na prática:** E2E feito com a instância Clerk de desenvolvimento do time (a do `.env`), contas por apelido do Gmail do Pedro e as empresas Empresa A LLT-119 e Peters Crew. A validade de 7 dias na Clerk e o convite expirado ficam como **risco aceito** (E2E-06, aceito pelo Pedro em 25/11) |
| D4 | **Decidida:** branch e commit no padrão `LLT-XXX-slug`, conferido pela validação de PR da LLT-285. Spec renumerada para `012` |
| D5 | **Pendente com o time** (log de negócio no pacote `member` ou auditoria da LLT-170). Card 4 |
| D6 | **Resolvida:** a 122, 176, 174, 167 e 168 do Web estão na `Development` (`309e417`). O roteiro E2E desta spec cobre a T011 do Web |

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

**Situação em 25/11/2026:** os bloqueios 5 a 8 e 10 caíram. Os bloqueios 1 a 4 e a parte da D5 do bloqueio 9 viraram os cards 1 a 4 do `cards-continuacao.md`, e não impedem fechar a LLT-119 (veredito do Pedro). A D4 foi decidida, mas a constituição ainda precisa de emenda pelo time (analyze, C1).

## Checklist de fechamento (seção 10 do documento-base) (T020)

**Atualização final de 25/11/2026:** D1 decidida e aplicada (ADR no card 1). 167 e 168 do Web na `Development`. 19 de 20 critérios OK, o AC-03 com risco aceito e o AC-17 falhou no Web com o card 7. Achados com destino e responsável sugerido (D2 continua com o time). `clean verify` 460/0/0/0 e Web `npm test` 160/160 com build OK. Nenhum segredo. O PR vai aberto com o Spec Kit completo. A lista abaixo é a de 17/11, mantida como histórico.

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

- `specs/012-validar-gestao-usuarios/` (spec, plan, research, quickstart, tasks, relatório, revisão e checklist)
- `src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java`

O `.specify/feature.json` aponta para a 007, mas é ignorado pelo Git (`.specify/.gitignore:6`).

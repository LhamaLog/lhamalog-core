# Research: Validar e fechar a Gestão de usuários

Leitura feita em 17/11/2026 na branch `LLT-119-validar-gestao-usuarios`, que está igual à `Development` (`a64559d`). Os caminhos são relativos à raiz do `lhamalog-core`. Os números de linha valem para esse commit.

## Estado validado

- **Decision**: Validar a `Development` atual (`a64559d`), sem merge local.
- **Rationale**: A LLT-168 do Core entrou na `Development` pelo PR #10 (`6ac2f01`, "Merge pull request #10 from LhamaLog/LLT-168-listar-editar-e-desativar-usuarios"). A branch do Pedro foi criada depois disso e está no mesmo commit: `git rev-parse LLT-119-validar-gestao-usuarios origin/Development` devolve `a64559d` para as duas. O passo 0.4 do documento-base (merge local da 168) ficou sem objeto.
- **Alternatives considered**: Mesclar `origin/LLT-168-...` de novo. Não muda nada, porque a 168 já está no histórico.

## Numeração da spec

- **Decision**: `specs/012-validar-gestao-usuarios`.
- **Rationale**: `init-options.json` usa `feature_numbering: sequential`. Na `Development` já existem `001` a `006`, e o `006` foi ocupado por `006-nome-unico-produto` (LLT-177, commit `afa883b`). O documento-base pedia "a próxima depois de 005", que hoje é a `007`.
- **Alternatives considered**: `006-validar-gestao-usuarios`. Duplicaria o número. A constituição cita uma "spec 007 (LLT-183)" no Sync Impact Report, mas essa pasta não existe em nenhuma branch do Core nem no Collector. Fica registrado no R-07/D4.
- **Atualização (25/11/2026)**: renumerada de `007` para `012` depois do merge da `Development`, que já usava de `007` a `011` (`007` LLT-173, `008` LLT-163, `009` LLT-178, `010` LLT-179 e LLT-289, `011` LLT-120).

## Linha de base da suíte (T001)

- **Decision**: Registrar a linha de base como **vermelha** e separar o que é ambiente, o que é anterior à 168 e o que é da 168.
- **Evidência**:
  - `.\mvnw.cmd -B clean verify` sem `TEST_MONGODB_URI` (Testcontainers), 17/11 às 19:47: **180 testes, 1 falha, 0 erros, 34 ignorados**, BUILD FAILURE. Log em `scratchpad/t001-baseline.log` (fora do repositório).
  - Os 34 ignorados são `CompanyProductIntegrationTests` (5), `ProductEditArchiveIntegrationTests` (10) e `ProductUniqueNameIntegrationTests` (19). Os três têm `@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI")` (por exemplo, `src/test/java/br/com/lhamalog/core/product/ProductUniqueNameIntegrationTests.java:46`).
  - A falha é `CompanyRegistrationHttpTests.adminDeletesRegistrationAndMemberCannot:163`: "Status expected:<409> but was:<200>".
  - O `verify` que o Pedro rodou no `cmd` às 19:32 deu 189 testes, 1 falha e 34 erros. Os 34 erros eram de ambiente: `TEST_MONGODB_URI` apontava para `127.0.0.1:27019` e o container `mongo-teste` estava parado ("Connection refused"). Os 9 testes a mais eram um relatório antigo (`ProductContextIntegrationTests`, de 16/11 às 22:25) que ficou em `target/surefire-reports` sem `clean`.
- **Bisseção da falha (R-14)**: `CompanyRegistrationHttpTests`, exportado com `git archive` para o scratchpad, falha igual em `7acb33d` (a `Development` imediatamente antes da 168) e em `6ac2f01` (o merge da 168). **A falha não foi introduzida pela 168.**
- **Causa provável (leitura)**: `CompanyService.save()` compara a versão pedida com a atual (`src/main/java/br/com/lhamalog/core/company/CompanyService.java:40` e `:131-134`). Depois de `deleteRegistration()` (`:98-104`), o documento é recriado e a `@Version` recomeça (`Company.java:14`). Por isso a versão antiga do teste volta a coincidir com a nova, e o PUT passa com 200. O teste é da LLT-175 (`8efb4f7`, de 11/11, que entrou na `Development` antes da 167).
- **Alternatives considered**: Corrigir o teste ou o serviço. Fora do escopo (código de outra feature e de outra pessoa). Fica como achado R-14, que bloqueia o AC-20.

## Lacunas da matriz que viram teste (AT-01 a AT-04)

- **Decision**: Uma classe só de teste, `src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java`, no mesmo pacote do `MemberClock`. O arranque é igual ao da `CompanyMemberHttpTests`: `@Import(TestcontainersConfiguration.class)`, `@SpringBootTest` com OTLP desligado e `@AutoConfigureMockMvc`. Os helpers são copiados, porque são privados naquela classe.
- **Rationale**: `MemberClock.setClock`, `setInterval` e `reset` são de pacote (`src/main/java/br/com/lhamalog/core/member/MemberClock.java:22-33`). A classe precisa estar no mesmo pacote para fixar o relógio no AT-01. Testcontainers usa `mongo:8.0.34` (`src/test/java/br/com/lhamalog/core/TestcontainersConfiguration.java:19`), um container próprio que nunca toca `lhamalog_core` nem `lhamalog_teste`.
- **Por que cada AT cobre uma lacuna real**:
  - **AT-01 (AC-10)**: a `CompanyMemberHttpTests` prova que o desativado é recusado (`:116-121`), mas não que a recusa deixa intactos o último acesso e o cargo. Pela leitura, o interceptor lança a recusa antes de `observe()` (`MemberAccessInterceptor.java:37-40`). O teste usa um token com cargo `admin` para o desativado: se o Core gravasse, ele viraria administrador ativo e mudaria a contagem do último administrador.
  - **AT-02 (AC-18)**: nenhum teste chama a lista ou a reativação com o próprio administrador desativado. A `refusesToDeactivateTheLastKnownActiveAdministrator` só cobre `/api/products` (`CompanyMemberHttpTests.java:167-168`).
  - **AT-03 (AC-08)**: o 401 e o "Selecione uma empresa..." estão provados só no `GET` (`CompanyMemberHttpTests.java:83-87`). Faltam o `POST` e o `DELETE`. O CSRF está desligado (`src/main/java/br/com/lhamalog/core/config/SecurityConfig.java:38`), então o esperado é 401 e não 403.
  - **AT-04 (R-01)**: exploratório, com `@Tag("investigacao")` e `@RepeatedTest(20)`. Além do status HTTP, compara o contador `CompanyAdminCount` com a contagem real de registros, o que torna visível uma inflação mesmo quando o 403 aparece por outro motivo.
- **Alternatives considered**: Acrescentar os casos na `CompanyMemberHttpTests`. Mexeria num arquivo do Vinicius. A regra é só acrescentar teste novo.

## Achados do documento-base (R-01 a R-13) e o novo R-14

### R-01 Contador `CompanyAdminCount` inflado no primeiro acesso simultâneo

- **Leitura**: Os dois caminhos que tornam alguém administrador ativo chamam `ensureCount()` **antes** do `save()`: `observe()` em `CompanyMemberService.java:57-58` (o save fica na `:63`) e `reactivate()` em `:117-118` (o save fica na `:120`). O `ensureCount()` conta e faz `upsert` com `setOnInsert` (`:140-144`). Assim, o documento do contador sempre existe antes de qualquer registro de administrador ser gravado, e o valor inicial é contado antes do `save()` da própria thread. O `increment()` (`:146-149`) é `updateFirst` sem upsert, mas o documento já existe nesse ponto. O `save()` que perde a corrida (`OptimisticLockingFailureException` ou `DuplicateKeyException`) sai sem incrementar (`:64-67`).
- **Onde o contador ainda erra**:
  - (a) **Para menos**, se o processo cair entre o `save()` e o `increment()`, ou entre o `findAndModify` que decrementa (`:96-97`) e o `save()` (`:101`). Isso é falha segura: o Core recusa mais desativações, não menos.
  - (b) **Para mais**, só se já existirem registros de administrador ativo sem o documento do contador: dados anteriores ao contador ou o documento apagado à mão. Nesse caso o `ensureCount()` conta os existentes, e os incrementos posteriores somam em cima.
- **Status**: depende do AT-04 (resultado no relatório). A leitura indica que o cenário do documento-base (dois primeiros acessos simultâneos) não infla.

### R-02 O Core só conhece o cargo pelo último token aceito

- **Leitura**: O cargo vem do token na hora da requisição (`src/main/java/br/com/lhamalog/core/shared/CompanySession.java:13` e `:19`) e só é gravado quando a pessoa faz uma requisição aceita (`MemberAccessInterceptor.java:40`, `MemberObservation.java:15`). Se o Web rebaixa a Carla e ela não navega, o Core continua contando a Carla como administradora ativa. Com isso, aceita que a Ana se desative pela API (o contador vai de 2 para 1).
- **Status**: **Risco aceito**, já documentado no contrato da 168 (`specs/005-listar-editar-usuarios/contracts/company-members.md:230`). A prova de ponta a ponta é o E2E-17 (Bloqueado: D3). Com a D1 (vale a LLT-163), a sincronização de cargo por webhook da 163 é o caminho natural para fechar o risco, se o contador passar a usar a coleção `member` sincronizada. Isso fica para a unificação.

### R-03 Desativar quem nunca entrou grava `administrator=false`

- **Leitura**: Confirmado em `CompanyMemberService.java:82-89`, que grava `false` e `DEACTIVATED`. Mas quem chama é sempre um administrador ativo, e esse administrador é observado pelo próprio interceptor na mesma requisição, antes do controlador (`MemberAccessInterceptor.java:40` roda antes do `CompanyMemberController.java:37-40`). Uma chamada desse tipo nunca deixa a empresa sem administrador ativo, porque quem chamou continua lá. Depois de reativada, a pessoa é promovida no primeiro acesso: `MemberObservation.shouldWrite` devolve `true` quando o cargo difere (`MemberObservation.java:15`), e `observe()` incrementa (`CompanyMemberService.java:71-72`).
- **Status**: **Descartado** como falha da trava do último administrador. Sobra a divergência temporária de dado, já aceita no contrato (`company-members.md:231`). O E2E-14b (Bloqueado: D3) registra o comportamento do Web.

### R-04 Custo do interceptor global e índice

- **Leitura**: O interceptor está em `/api/**` (`MemberAccessConfig.java:20`). Por requisição com empresa, ele faz **duas** leituras por `_id`, não uma: `isDeactivated()` (`CompanyMemberService.java:43`) e de novo `observe()` (`:48`). Ele grava no máximo a cada 5 minutos (`MemberObservation.java:18`). As rotas `/internal/usage-facts/**` usam outra cadeia (`UsageFactsSecurityConfiguration.java:29-37`), sem JWT de empresa, então o interceptor sai sem ler nada (`MemberAccessInterceptor.java:33-34`). O `CompanyMember` não tem `@Indexed` nem `@CompoundIndex` (`CompanyMember.java:9-11`). A lista por `tenantId` (`CompanyMemberRepository.java:8`) e a contagem (`:10`) varrem a coleção. A contagem também roda a cada `ensureCount()`, mesmo quando o contador já existe (`CompanyMemberService.java:141`). A rota `/api/products/{id}/tokens` citada no documento-base ainda não está na `Development` (é da LLT-179, PR #8).
- **Status**: **Confirmado (baixa)**. A suíte de Produto roda verde com o interceptor ativo (linha de base), então não há regressão funcional. O impacto é de desempenho.

### R-05 Padrão do pacote `member`

- **Leitura**: O pacote é plano: 11 classes em `core/member`, enquanto o `usage` se divide em `api`, `application` e `infrastructure`. O relógio usa campos `volatile` trocáveis por setters de pacote (`MemberClock.java:11-12` e `:22-33`). O padrão do módulo `usage` é `InstantSource` no construtor (`src/main/java/br/com/lhamalog/core/usage/application/QuotaReservationService.java:30` e `:47`), com o `Clock` como bean em `config/TimeConfiguration.java:11-12`. Não há nenhum log no pacote `member`: `grep -rn "Logger\|LoggerFactory\|event=" src/main/java/br/com/lhamalog/core/member/` não devolve nada. Desativar, reativar e a recusa `last_admin` não geram `event=` do princípio VI.
- **Status**: **Confirmado (padrão)**. O log de negócio depende da D5 (pendente com o time). Os setters são de pacote, não públicos, o que reduz mas não elimina o problema.

### R-06 Padrão de commit e de branch

- **Leitura**: Os commits da 168 são `LLT-168 Listar, editar e desativar usuários` (`420d4dc`, `4f32c77`, `a976683`, `bc01566`) e os da 167 são `LLT-167 Convidar usuário para a empresa` (`bd3f8da`, `4921c65`, `1ecf435`). Todos sem `:`, com maiúscula e acento, e com autor `Cursor Agent`. Só o `0132ffb` está no formato (`LLT-168: atualiza com Development...`). Nenhuma das duas branches tem `feature/`. **A branch do próprio Pedro, `LLT-119-validar-gestao-usuarios`, também não tem `feature/`**, ao contrário do que a constituição (Fluxo de desenvolvimento) e o documento-base pediam (`feature/LLT-119-validar-gestao-usuarios`).
- **Status**: **Confirmado (processo)**. A 167 e a 168 já foram mergeadas assim. Decisão D4, pendente com o time.

### R-07 Numeração das specs

- **Leitura**: A `Development` tem `002-editar-arquivar-produto` e `002-manter-dados-empresa`, os dois com `002`. Nas branches abertas: a LLT-178 traz `004-contexto-produto` (colide com `004-convidar-usuario-empresa`), a LLT-179 (Pedro, PR #8) traz `005-tokens-ingestao` (colide com `005-listar-editar-usuarios`), e a LLT-163 traz `002-validacao-token-clerk` e `003-webhooks-clerk` (colidem com `002-*` e `003-manter-perfil-usuario`). No Web, a mesma 167/168 é `003`/`004`, segundo o documento-base.
- **Status**: **Confirmado (processo)**, e pior do que no documento-base. D4, pendente com o time.

### R-08 A suíte no estado mesclado

- **Leitura**: A `Development` atual já contém a 168, a 183, a 185, a 123 e a 177. A única falha da suíte (R-14) é anterior à 168, como mostrou a bisseção. Os testes do `member` passam (6/6) e os de Produto que rodam sem `TEST_MONGODB_URI` também.
- **Status**: **Confirmado e verificado**: não há falha atribuível à 168 no estado mesclado. A execução com `TEST_MONGODB_URI` (T009) completa a prova para Produto.

### R-09, R-10, R-11 e R-13 (Web)

- **Leitura**: Não verificados nesta sessão. O `lhamalog-web` local está na `main`, que não tem `features/team`, `TeamPage.tsx` nem `tests/`, e o agente não roda Git nesse repositório (`lhamalog-web/docs/instrucoes-agentes.md`). O Pedro preferiu não trocar de branch agora (clarify de 17/11).
- **Status**: **Pendente de confirmação**. Ficam com a leitura do documento-base, de 16/11 no GitHub, até a T003 rodar com o Web na branch da 168.
- **Leitura de 25/11 (T029)**: Web na `Development` (`309e417`), lido sem Git.
  - **R-09 (processo): resolvido.** As histórias que vinham juntas na branch da 168 (122, 176, 174 e 167) já estão na `Development`.
  - **R-10 (baixa): confirmado.** `members.ts:23-24` devolve o `identifier` como e-mail, e a linha 39 usa isso na coluna. Com login só por e-mail, como na instância de teste, o valor é um e-mail. Com telefone ou username, a coluna mostraria outra coisa.
  - **R-11 (info): confirmado.** `resendPlan` (`invitations.ts:46-56`) revoga todos os pendentes do e-mail e monta um convite novo. O `TeamPage.tsx` (262-293) executa isso e mostra "Convite enviado para ... Ele expira em 7 dias.", sem avisar que o link anterior deixa de funcionar.
  - **R-13 (média): descartado no código.** O efeito que carrega os convites (`TeamPage.tsx:179-204`) depende de `invitationKey` (string montada por `teamLoadKey`, linha 165), `organizationId` e `page`, e lê a organização por `organizationRef`. Assim, uma organização com nova identidade a cada renderização não dispara o efeito de novo, que era a causa do carregamento infinito. O commit `f9a1a5f` não pode ser conferido sem Git, mas o comportamento existe. O E2E-04 confirma na Phase 10.

### R-12 LLT-163 × 167/168 (D1)

- **Mudança desde o documento-base**: A branch `origin/LLT-163-validar-o-token-do-clerk-no-core` foi atualizada em 16/11 às 23:44 (`d7ef2f3`, Felipe). O commit `43734ba` ("junta a Development e adapta membros e produtos à sessão validada pelo Core") já tem a `Development` com a 168: `git merge-base origin/Development origin/LLT-163-...` devolve `a64559d`. Mergear a 163 na `Development` hoje seria um avanço direto (fast-forward), sem conflito de Git. O conflito que sobra é de modelo e de contrato:
  - **Sessão sem empresa**: na 163 a cadeia de segurança responde 403 `{"error":"forbidden"}` e revoga as sessões (o teste da 163 troca o "Selecione uma empresa para continuar." por `jsonPath("$.error").value("forbidden")` na `CompanyMemberHttpTests`). Isso muda o AC-08 e o contrato da 168 (`company-members.md:47`).
  - **`tenantId`**: na 163 o `CompanySession` vem do `RequestContext`, e o `tenantId` é o id interno da coleção `tenant`, "nunca o id da organização da Clerk" (`CompanySession.java` da 163). Os registros `CompanyMember` gravados com o id da organização (`_id = orgId/userId`) ficam órfãos depois do merge. É preciso migrar ou descartar esses dados.
  - **Dois modelos de membro no mesmo pacote**: a 163 cria `member/domain/Member.java`, `MemberRole` (DONO/ADMIN/MEMBRO) e `member/adapter/out/mongo/MemberDocument.java` (coleção `member`, com `role`, `name`, `email`, sincronizada por webhook). Eles convivem com `CompanyMember` e `CompanyAdminCount` da 168. O `MemberAccessInterceptor` e o `CompanyMemberController` da 168 foram adaptados para `ClerkAuthentication` e `sessions.current()`.
  - **Chave secreta**: a 163 acrescenta `CLERK_SECRET_KEY` e `CLERK_WEBHOOK_SIGNING_SECRET` ao `.env.example` (vazios) e `ClerkBackendSessionRevocation` (Backend API). Isso contradiz a 167 (FR-004, FR-014), a 168 (FR-016) e a seção "Fronteira" do contrato.
  - **ADRs**: a ADR_022 (aceita em 02/10/2026) prevê `CLERK_SECRET_KEY` e sincronização por webhook. A ADR_122 (aceita em 11/10/2026) define a coleção Membro com o papel DONO/ADMIN/MEMBRO. As duas estão em `lhamalog_docs/Documentações/Diagrama ADR/`. A ADR_123 não está na cópia local (só no Drive, não lida).
- **Decisão**: D1 respondida pelo Pedro no clarify: **vale o modelo da LLT-163**.
- **Status**: **Confirmado, com decisão tomada**. Continua bloqueando a LLT-119 até: (1) a D1 ser registrada em ADR; (2) o time definir como o `CompanyMember`/`CompanyAdminCount` se encaixa na coleção `member` (ou é substituído por ela); (3) os textos da 167/168 contrários à 163 serem atualizados; (4) os dados `CompanyMember` com o id da organização serem tratados.

### R-14 (novo) A suíte da `Development` já falha fora da Feature

- Ver "Linha de base da suíte" acima. **Confirmado**. É anterior à 168, de dono a definir (LLT-175), e bloqueia o AC-20.

## Contrato

- **Decision**: Nenhum contrato novo. A referência é `specs/005-listar-editar-usuarios/contracts/company-members.md` (Core) e o contrato da LLT-167 em `specs/004-convidar-usuario-empresa/contracts/organization-invite.md`.
- **Rationale**: Esta feature não cria nem muda interface.

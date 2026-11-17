# Rascunho: comentários de revisão (LLT-119)

**Status**: rascunho, **não publicado**. O Pedro decide se publica e onde (PR, card ou conversa com o Vinicius e o Felipe). Quem corrige é a D2 (pendente com o time; o sugerido é o Vinicius).

Linhas de `lhamalog-core` no commit `a64559d` (`Development`). A 168 do Core já foi mergeada (PR #10), então os comentários do Core viram **follow-up ou card**, não pedido de mudança no PR. Os do Web valem para o PR da 168 do Web, que ainda está aberto.

---

## Core: LLT-168 (PR #10, mergeado)

### 1. Duas leituras por requisição no interceptor (R-04, baixa)

`src/main/java/br/com/lhamalog/core/member/MemberAccessInterceptor.java:37-40`

> Toda requisição com empresa faz `findById` duas vezes no mesmo documento: uma em `isDeactivated()` (`CompanyMemberService.java:43`) e outra no começo de `observe()` (`CompanyMemberService.java:48`). Como o interceptor está em todo `/api/**` (`MemberAccessConfig.java:20`), isso dobra a leitura de Mongo de toda rota, inclusive Produto. Sugestão: ler uma vez e passar o documento para `observe()`, ou fazer `observe()` devolver se a pessoa está desativada.

### 2. `CompanyMember` sem índice por empresa (R-04, baixa)

`src/main/java/br/com/lhamalog/core/member/CompanyMember.java:9-11`

> `findByTenantIdOrderByUserIdAsc` (`CompanyMemberRepository.java:8`) e `countByTenantIdAndAdministratorTrueAndStatus` (`:10`) não têm índice: só o `_id` é indexado. Com `spring.data.mongodb.auto-index-creation=true`, um `@CompoundIndex(def = "{'tenantId': 1, 'userId': 1}")` resolve a lista. A contagem pode usar `{'tenantId': 1, 'administrator': 1, 'status': 1}`.

### 3. A contagem roda a cada `ensureCount()` (R-04, baixa)

`src/main/java/br/com/lhamalog/core/member/CompanyMemberService.java:140-144`

> O `countBy...` da linha 141 roda sempre, mesmo quando o documento `CompanyAdminCount` já existe e o `setOnInsert` é descartado. Sugestão: tentar o `upsert` só se o `findById` do contador não achar nada, ou aceitar o custo e documentar.

### 4. Desativar quem nunca entrou grava `administrator=false` (R-03, baixa)

`src/main/java/br/com/lhamalog/core/member/CompanyMemberService.java:82-89`

> Na validação da LLT-119 isso não deixa a empresa sem administrador, porque quem chama é um administrador observado na mesma requisição. E a pessoa é promovida no primeiro acesso depois de reativada. Sugestão: um comentário no código explicando esse invariante, já que o `false` parece um bug para quem lê.

### 5. Relógio de teste no código de produção (R-05, padrão)

`src/main/java/br/com/lhamalog/core/member/MemberClock.java:11-33`

> `setClock`, `setInterval` e `reset` existem só para teste. O padrão do módulo `usage` é receber `InstantSource` no construtor (`usage/application/QuotaReservationService.java:30` e `:47`), com o `Clock` como bean em `config/TimeConfiguration.java:11-12`. Sugestão: `CompanyMemberService` recebe `InstantSource` e `Duration interval`, e o teste injeta um relógio fixo por `@TestConfiguration`.

### 6. Sem log de negócio (R-05, princípio VI, depende da D5)

`src/main/java/br/com/lhamalog/core/member/CompanyMemberService.java:79-126`

> Desativar, reativar e a recusa `last_admin` não geram nenhum log. A constituição 2.1.0 (princípio VI) pede `event=<dominio.acao.resultado> | <aviso> | chave=valor` para evento de negócio em INFO e para WARN. Por exemplo: `event=member.deactivation.succeeded`, `event=member.deactivation.last_admin_refused` e `event=member.access.deactivated_refused`. Sem dado pessoal (`userId` só como hash, se o time considerar o id da Clerk como dado pessoal). Depende da D5 (log de negócio ou auditoria da LLT-170).

### 7. Contador de administradores: invariante e janelas (R-01, informativo)

`src/main/java/br/com/lhamalog/core/member/CompanyMemberService.java:56-76` e `:94-106`

> O teste exploratório da LLT-119 (`CompanyMemberAcceptanceTests.firstAccessRaceKeepsCounterAndOneAdministrator`, 20 repetições) não reproduziu a inflação do contador no primeiro acesso simultâneo. Pela leitura, o `ensureCount()` sempre roda antes do `save()`. Ficam duas janelas, que valem um comentário no código:
>
> - (a) uma queda do processo entre `save()` e `increment()`, ou entre o `findAndModify` e o `save()`, deixa o contador **menor** que o real. A falha é segura: o Core recusa mais, não menos.
> - (b) registros de administrador ativo gravados sem o documento do contador (dados antigos, ou o contador apagado à mão) inflam o contador no primeiro `ensureCount()`.
>
> Sugestão: documentar o invariante "o contador nasce antes de qualquer administrador" e, se o time quiser, reconciliar o contador com a contagem real quando ele divergir.

### 8. Cargo conhecido só pelo último token (R-02, risco aceito)

`specs/005-listar-editar-usuarios/contracts/company-members.md:230`

> O risco está documentado e foi aceito. Com a D1 decidida pelo modelo da LLT-163, a coleção `member` sincronizada por webhook (`organizationMembership.updated`) passa a conhecer o rebaixamento sem esperar a próxima requisição. Sugestão para a unificação: o cálculo do último administrador usar a coleção `member` da 163, e não o cargo observado no token.

### 9. Commits fora do padrão (R-06, processo)

Commits `420d4dc`, `4f32c77`, `a976683` e `bc01566` (168) e `bd3f8da`, `4921c65` e `1ecf435` (167)

> As mensagens estão como `LLT-168 Listar, editar e desativar usuários`, sem `:`, com maiúscula e acento, e com autor `Cursor Agent`. A constituição (Fluxo) e a ADR_148 pedem `LLT-168: descricao`. As branches também não têm `feature/`. Já foram mergeadas: fica para a D4 decidir se vale reescrever ou só registrar.

### 10. Contrato da 168 contra a D1 (R-12, bloqueio)

`specs/005-listar-editar-usuarios/contracts/company-members.md:7-20` (Fronteira) e `specs/005-listar-editar-usuarios/spec.md` (FR-016)

> O time decidiu pelo modelo da LLT-163 (D1). A seção "Fronteira", o "Não criar `CLERK_SECRET_KEY`" e o FR-016 ("O Core MUST NOT chamar a plataforma de identidade") deixam de valer quando a 163 entrar. O mesmo vale para a 167 (FR-004 e FR-014 em `specs/004-convidar-usuario-empresa/spec.md`). Sugestão: atualizar os dois contratos junto com o merge da 163 e registrar a D1 em ADR.

---

## Core: LLT-163 (Felipe, PR não aberto ou não mergeado)

### 11. Dados `CompanyMember` com o id da organização (R-12)

`src/main/java/br/com/lhamalog/core/shared/CompanySession.java` (na branch da 163)

> Na 163, o `tenantId` do `CompanySession` passa a ser o id interno do tenant, "nunca o id da organização da Clerk". Os documentos `CompanyMember` (`_id = tenantId/userId`) e `CompanyAdminCount` (`_id = tenantId`) gravados antes do merge usam o id da organização e ficam órfãos: as marcas de desativado deixam de valer, e quem estava desativado volta a ter acesso. Sugestão: uma migração (org id → tenant id) ou uma decisão explícita de descartar esses dados no merge.

### 12. Testes de aceite da LLT-119 a migrar

`src/test/java/br/com/lhamalog/core/member/CompanyMemberAcceptanceTests.java` (novo, branch `LLT-119-validar-gestao-usuarios`)

> A classe usa o `jwt()` sintético e espera "Selecione uma empresa para continuar." no AT-03. Quando a 163 entrar, ela precisa da mesma migração feita na `CompanyMemberHttpTests` (`@CoreIntegrationTest`, `TestTenants`, e `$.error = forbidden` para a sessão sem empresa).

---

## Core: LLT-175 (fora da Feature)

### 13. Teste vermelho na `Development` (R-14)

`src/test/java/br/com/lhamalog/core/company/CompanyRegistrationHttpTests.java:162-163` e `src/main/java/br/com/lhamalog/core/company/CompanyService.java:40,98-104,131-134`

> O `adminDeletesRegistrationAndMemberCannot` espera 409 ao enviar a `version` da primeira criação depois de excluir e recriar o cadastro, mas recebe 200. A `@Version` do documento recriado recomeça, então a versão antiga volta a coincidir. Ele falha igual antes e depois do merge da 168 (`7acb33d` e `6ac2f01`). O time precisa decidir se o teste está errado (a versão pode repetir depois da exclusão) ou se o serviço precisa impedir isso (por exemplo, mantendo um contador de versão que sobrevive à exclusão).

---

## Web: LLT-168 (PR aberto)

Os itens abaixo vêm da leitura do documento-base de 16/11 e **ainda não foram confirmados** nesta sessão. O Web local está na `main`, e o agente não roda Git no `lhamalog-web`. Os arquivos e linhas exatos entram quando a T004 rodar.

### 14. Falta a correção `f9a1a5f` da 167 (R-13, média)

`src/features/team/TeamPage.tsx` (caminho a confirmar)

> A branch da 168 sai da 167 antes da correção `f9a1a5f` (carregamento infinito da lista de convites, 16/11). Mergear a 168 como está traz o bug de volta, ou gera conflito se a 167 entrar depois. Sugestão: trazer a 167 atualizada para dentro da 168 antes do PR, ou definir a ordem de merge (D6).

### 15. A branch carrega 122, 176, 174 e 167 (R-09, processo)

> São 20 commits e 109 arquivos. Sugestão: mergear em cadeia (122 → 176 → 174 → 167 → 168) ou abrir um PR único com a lista de histórias. Decisão D6.

### 16. E-mail exibido é `publicUserData.identifier` (R-10, baixa)

`src/features/team/members.ts` (caminho a confirmar)

> Se a instância permitir login por telefone ou username, a coluna "E-mail" mostra outra coisa. Sugestão: usar o e-mail principal quando existir, ou mudar o rótulo para "Identificador".

### 17. "Reenviar" é revogar e criar outro convite (R-11, info)

`TeamPage.tsx` (`resendPlan`)

> O SDK do navegador não tem reenvio, então o link antigo deixa de valer e o prazo recomeça. Sugestão: deixar isso explícito no texto da tela ("o link anterior deixa de funcionar").

### 18. T011 (roteiro manual com a Clerk) pendente

> O roteiro E2E da LLT-119 (`specs/007-validar-gestao-usuarios/quickstart.md`) cobre a T011 do Web. Se ele substitui a T011 é a decisão D6.

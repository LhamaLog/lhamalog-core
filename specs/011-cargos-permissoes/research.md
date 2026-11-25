# Pesquisa: Cargos e permissões

Levantamento feito em 17/11/2026: ADRs no Drive (só leitura), planilha "LhamaLog casos de uso", diagrama `sequencia-tenant-multitenant.md` v11, telas do Figma em `lhamalog_docs/Design` e código da `Development` do Core e do Web, onde as LLT-167, 168 e 177 já estão mergeadas.

## R1. Base: ADRs × código mergeado

- **Conflito:** as ADR_122/123 colocam o papel DONO/ADMIN/MEMBRO numa coleção Membro do Core. As LLT-167/168 (mergeadas) só conhecem `admin` e `member` pelo token, sem chave secreta. A LLT-163 (Felipe, não mergeada) sincroniza Membro por webhook, com `CLERK_SECRET_KEY`.
- **Decisão (Pedro, 17/11, antes da LLT-163 entrar; ver R2b):** seguir as ADRs **sem chave secreta**. ADMIN e MEMBER continuam vindo do token, como nas LLT-167/168. Só o DONO passa a ser gravado no Core (`CompanyOwner`).
- **Por quê:**
  - fica compatível com o código mergeado;
  - não depende da LLT-163;
  - cumpre o mapeamento da ADR_123 (`org:admin` = DONO ou ADMIN).
- **Alternativas descartadas:**
  - só `admin`/`member`, que diverge das ADRs no DONO;
  - esperar o time.
- **Risco:** quando a LLT-163 for decidida, o `CompanyOwner` pode virar um campo da coleção Membro. O serviço `AccessService` isola isso: só ele lê e grava o Dono.

## R2. Como nasce o Dono (C1)

- **O que as fontes dizem:** pela ADR_137 e pelo diagrama v11, o comprador vira Dono na compra da assinatura. Esse fluxo ainda não existe no Core: Assinatura e Pagamento estão em andamento (LLT-231, André).
- **Recomendação:** primeiro administrador que resolver o papel (`GET /api/company/access/me`, que o Web chama ao abrir) vira o Dono, com a origem `FIRST_ADMIN`.
- **Garantia de unicidade:** `insert` com `_id = tenantId`.
- **Futuro:** o fluxo de compra chama `AccessService.assignOwner(tenantId, userId, PURCHASE)`.
- **Risco aceito:** numa empresa que já existe, o Dono pode ficar sendo um administrador que não foi o comprador. A correção manual (troca do documento) fica no quickstart. Transferir o Dono pela tela fica fora.

## R2b. Atualização de 18/11: Dono na coleção `member` da LLT-163 (substitui R1 e R2)

- **O que mudou:** a LLT-163 entrou na `Development` em 17/11 (PR #6) com a coleção `member` e o papel `DONO`/`ADMIN`/`MEMBRO` (spec 003). No mesmo dia, a D1 do time passou a valer o modelo de membro da 163, e o Pedro registrou: "na LLT-120, partir do modelo da 163, não do `CompanyMember`".
- **Conflito com o pacote:** o R1/R2 do pacote criam a coleção `CompanyOwner` e fazem o primeiro administrador virar Dono. A spec 003 decidiu, pela ADR_121 (11/10), que só o fluxo de compra atribui DONO e que o webhook nunca atribui.
- **Decisão aplicada (aguarda confirmação do Pedro):** pela regra "ADR mais recente é a fonte de verdade", o Dono é o `member` DONO ativo, e esta entrega só lê. Sem coleção nova e sem "primeiro admin".
- **Papel de outra pessoa:** `member` ativo primeiro; sem ele, `CompanyMember.administrator` (LLT-168, gravado a cada requisição pelo interceptor); sem nenhum, MEMBER. A ponte existe porque a `member` só é preenchida pelos webhooks da Clerk, e a unificação das duas coleções ainda está pendente (D1).
- **Isolamento:** a porta `MemberRoleDirectory` concentra essas leituras. O módulo `member` da LLT-163 não muda.
- **Consequências:**
  - US1 deixa de ter o cenário de corrida entre dois administradores, porque nada é gravado;
  - para testar à mão, o Dono é marcado no banco local (`quickstart.md`);
  - enquanto não houver compra, empresas reais ficam sem Dono, e as permissões exclusivas do Dono (`MEMBERS_INVITE_ADMIN`, `SUBSCRIPTION_MANAGE`, `PENDING_ACCESS_APPROVE`) não aparecem para ninguém.
- **Alternativa, se o Pedro preferir:** promover o primeiro administrador a `DONO` na própria coleção `member`. Exige mudar a regra da spec 003 com o Felipe.
- **Nota de 22/11/2026 (LLT-289):** o fluxo de compra existe. A LLT-289 (Felipe, spec `010-criar-tenant-pagamento`, FR-009) entrou na `Development` e, na confirmação do pagamento, grava o `member` DONO de quem comprou (`Member.owner(...)`, ou `promotedToOwner()` se um evento da Clerk criou o `member` antes). O Pedro confirmou a C1 em 22/11. Esta feature continua só lendo, e nenhum código dela mudou com o merge. A consequência "empresas reais ficam sem Dono" vale agora só para a organização de desenvolvimento (`DEV_TENANT_CLERK_ORG_ID`), que nasce com Tenant e sem compra. O `MongoMemberRoleDirectory` continua sendo o único ponto a trocar quando o time unificar `member` e `CompanyMember` (D1). Prova: `AccessPurchaseOwnerIntegrationTests` (T040).
- **Também da LLT-289:** organização sem Tenant passou a receber 403 `subscription_required`, sem revogar a sessão (spec 010, FR-006). As rotas desta feature seguem essa regra sem mudança, porque não entraram nas rotas liberadas sem Tenant (`/api/billing/**`).
## R3. Dono rebaixado fora do sistema

- **Caso:** se alguém troca o Dono para `member` direto no dashboard da Clerk, o token dele chega como membro.
- **Decisão:** a sessão vale como MEMBER (o token manda no poder), com um log WARN `event=access.owner.not_admin`, e o registro do Dono continua guardado.
- **Por quê:** é o comportamento mais seguro. No Web, a tela já impede esse rebaixamento.

## R4. Matriz fixa no código

- **Decisão:** enum `Permission` + `AccessPolicy` com uma tabela estática.
- **Por quê:** a ADR_123 descartou papéis customizados de acesso, porque o add-on B2B da Clerk é pago. Só os cargos de notificação são customizáveis (ADR_137). Uma matriz editável não tem respaldo em ADR.
- **O que a tela "Permissões" do Figma mostra:** perfis editáveis (Analista, Operador, Leitor), mas é uma inspiração genérica, sem ADR. Ela vira só uma tela de leitura da matriz.
- **Rotas existentes:** continuam com `session.administrator()` (equivalente a OWNER ou ADMIN), para não mudar comportamento já testado (FR-005).

## R5. Cargo × papel de acesso

- **Fonte:** a USID_052 é explícita: "os cargos só definem quem recebe cada alerta; eles não dão permissão de acesso". A ADR_140 diz que os membros de Dono e Administrador vêm do papel de acesso.
- **Consequência:** "atribuir cargo" (LLT-170) significa incluir a pessoa num cargo de notificação. Promover para administrador continua no Web (LLT-168, `organization.updateMember`).
- **Divergência registrada:** a spec da LLT-168 descrevia a LLT-170 como "o cargo define cada ação, com auditoria". As ADRs mais recentes (136 a 140) e a USID_052 prevalecem, conforme a regra de que a ADR é a fonte de verdade.

## R6. Rotas em inglês (C2)

- **Conflito:** a ADR_139 propõe `/papeis` (Status: Aceito, mas o próprio texto diz "proposta do assistente"). O princípio IV da constituição 2.1.0 (16/11) pede endpoints em inglês.
- **Recomendação:** `/api/company/notification-roles`, citando a equivalência no contrato.

## R7. Listas de gravidade e tipo (C3, C4)

- **Gravidade:** a ADR_170 (14/11) fixa ERROR, WARNING e INFO como a taxonomia vigente da classificação, com ordem de gravidade ERROR > WARNING > INFO. É a lista mais recente e oficial para a "gravidade" do alerta.
- **Tipo de alerta:** está pendente nas ADR_136/139. A ADR_060 lista o que gera notificação: Ocorrências, estados críticos e alertas do sistema. Daí vêm `OCCURRENCE`, `CRITICAL_STATE` e `SYSTEM_ALERT`.
- **Valores iniciais dos padrão:** também pendentes (ADR_136). Gestor, Supervisor e Membro nascem vazios, para não inventar valores, e o fallback da ADR_138 entrega ao Dono e aos Administradores enquanto ninguém configurar.

## R8. Listas embutidas (C5)

- **Decisão:** `severities`, `alertTypes` e `memberUserIds` como arrays no documento `NotificationRole`.
- **Por quê:**
  - a ADR_142 exige N:N, e arrays em documento atendem;
  - a leitura pelo módulo Notificação fica num documento só;
  - incluir e retirar membro é atômico (`$addToSet`/`$pull`).
- **Alternativa:** as coleções `PapelSeveridade` e `PapelTipoAlerta` da ADR_134/136. Elas pedem mais joins e mais consistência manual.

## R9. AcessoProduto que já existe

- **O que já existe:** `ProductAccess` (collection `AcessoProduto`, índice único `tenantId + clerkUserId + productId`). Já é usado por `ProductService.list/get` e gravado em `create`.
- **Decisão:** reaproveitar a coleção sem mudar o formato, e criar `ProductAccessRepository.deleteByTenantIdAndClerkUserIdAndProductIdIn` e `findByTenantIdAndClerkUserId` (este já existe).
- **Substituição da lista:** calcula a diferença entre a lista atual e a nova, insere o que falta (ignorando `DuplicateKeyException`) e remove o que sobra. Não precisa de transação: o resultado final é idempotente.
- **Resposta para produto sem acesso:** 404, que é o comportamento atual e serve como negação simples (ADR_126).

## R10. Proteger o Dono na desativação (C6)

- **Onde muda:** é preciso uma checagem em `CompanyMemberController.deactivate` (módulo do Vinicius, LLT-168), antes de chamar o serviço: `accessService.isOwner(tenantId, userId)` resulta em 403 `owner_protected`.
- **Tamanho da mudança:** uma linha e uma dependência, avisada no PR.

## R11. Padrão de código

- **Modelo:** o módulo `usage` (Michel), com `api` / `application` / `infrastructure`, e o serviço recebe `Clock` ou `InstantSource` no construtor.
- **Testes:** HTTP com `@CoreIntegrationTest` (Testcontainers) e tokens Clerk reais do `TestJwks` via `TestTenants`, como no `CompanyMemberHttpTests` depois da LLT-163. O `jwt()` sintético não passa mais pela camada de segurança. Não precisam de `TEST_MONGODB_URI`.
- **Logs:** constituição VI (`event=...`), sem `userId` em claro no log: usar `userHash` (SHA-256 truncado) ou só as contagens.

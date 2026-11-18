# Feature Specification: Cargos e permissões

**Feature Branch**: `LLT-120-cargos-permissoes`

**Created**: 2026-11-17

**Status**: Draft (pré-preenchida no claude.ai; passa por clarify e analyze no Claude Code antes do implement)

**Input**: "LLT-120 Cargos e permissões (épico Usuários, cargos e configurações do usuário; item E03.F2 do backlog de 14/10/2026; fonte: Dono + Drive). Itens: definir o catálogo de cargos e a matriz de permissões (LLT-169); atribuir cargo a um usuário (LLT-170); restringir o acesso de usuários por produto (LLT-171)."

**Base escolhida pelo Pedro (17/11/2026)**: seguir as ADRs, sem chave secreta da Clerk. Os papéis de acesso são DONO, ADMIN e MEMBRO, e a Clerk continua só com `admin` e `member`. **Ajuste de 18/11 (clarify, C1):** o DONO é lido da coleção `member` da LLT-163, que já está na `Development` (D1). Esta feature não usa `CLERK_SECRET_KEY`, não chama a Clerk e não grava DONO.

## Fontes

- **ADRs:**
  - ADR_003: cargos ligados ao usuário.
  - ADR_109: tokens só para administradores.
  - ADR_114: convite e promoção.
  - ADR_122: coleções Membro e AcessoProduto.
  - ADR_123: papéis na coleção Membro; `org:admin` corresponde a DONO ou ADMIN, `org:member` a MEMBRO.
  - ADR_126: negação simples.
  - ADR_136 a ADR_140: cargos de notificação.
  - ADR_170: classes ERROR, WARNING e INFO.
- **Casos de uso:** USID_029.1 Manter Membro, USID_030.1 Controle de Acesso dos Membros aos Produtos, USID_052 Manter Cargos.
- **Diagrama:** `sequencia-tenant-multitenant.md` v11 (30/10/2026), seções 2, 4, 5 e 8.
- **Código já mergeado:**
  - `member`: LLT-168, a marca de desativado e o `administrator` visto no token (coleção `CompanyMember`);
  - `member`: LLT-163/164, a coleção `member` com o papel DONO/ADMIN/MEMBRO sincronizado por webhook (spec 003);
  - `product`: `AcessoProduto` já filtra a lista e o detalhe de produtos para quem não é admin;
  - `CompanySessionResolver`: `admin` e `org:admin` são administrador.

## Clarifications

Pontos que o Claude Code precisa confirmar com o Pedro no `speckit-clarify`. Cada um traz uma recomendação; até a confirmação, a spec segue a recomendação.

- **C1** [RESPONDIDA em 18/11, ver Session 2026-11-18; a recomendação abaixo foi substituída]: Como o Core sabe quem é o DONO enquanto a compra da assinatura ainda não existe?
  - **Recomendação:** o primeiro administrador da empresa que usar o Core vira o DONO. É um registro único por empresa, gravado de forma atômica. Quando o fluxo de compra existir, ele grava o DONO pelo mesmo serviço, com a origem `PURCHASE`.
  - **Fora desta entrega:** transferir o DONO.
- **C2** [NEEDS CLARIFICATION]: Rotas em inglês ou em português?
  - **Recomendação:** inglês, `/api/company/notification-roles`, porque o princípio IV da constituição pede endpoints em inglês. Na documentação, fica registrado que a rota equivale à `/papeis` da ADR_139, que é só uma proposta.
- **C3** [NEEDS CLARIFICATION]: Quais são as listas fechadas de gravidade e de tipo de alerta?
  - **Recomendação:** gravidades `ERROR`, `WARNING` e `INFO` (ADR_170).
  - **Recomendação:** tipos de alerta `OCCURRENCE`, `CRITICAL_STATE` e `SYSTEM_ALERT`, que são as três categorias de notificação da ADR_060.
- **C4** [NEEDS CLARIFICATION]: Com que gravidades e tipos nascem os cargos padrão Gestor, Supervisor e Membro? A ADR_136 deixa isso pendente.
  - **Recomendação:** nascem vazios, sem inventar valores, e o Dono ou um Administrador configura.
  - Dono e Administrador recebem tudo, de forma fixa (ADR_137).
- **C5** [NEEDS CLARIFICATION]: As gravidades, os tipos e os membros ficam como listas dentro do documento do cargo ou em coleções de ligação (`PapelSeveridade`, `PapelTipoAlerta`, ADR_134/142)?
  - **Recomendação:** listas dentro do documento. Isso continua sendo uma relação N:N e atende à ADR_142.
  - Avisar o responsável pelo módulo Notificação, que vai ler esse documento.
- **C6** [NEEDS CLARIFICATION]: Para proteger o DONO na desativação, é preciso mudar o código do módulo `member` (LLT-168, do Vinicius).
  - **Recomendação:** fazer uma mudança pequena, na branch do Pedro, e avisar o Vinicius no PR.
- **C7** [NEEDS CLARIFICATION]: Um MEMBRO continua podendo criar produto? É o comportamento atual: quem cria ganha acesso ao produto.
  - **Recomendação:** manter e registrar na matriz. Mudar exige decisão do time.

### Session 2026-11-18

Clarify feito sem o Pedro presente, a pedido dele ("prossiga, deixe só as decisões importantes"). Cada resposta é a recomendação aplicada e **aguarda a confirmação do Pedro**. As marcadas com ⚠ divergem do pacote de 17/11 e são as decisões que ele precisa olhar primeiro.

**Fato novo desde o pacote:** a LLT-163 (Felipe) entrou na `Development` em 17/11 (PR #6). Ela trouxe a coleção `member`, com o papel `DONO`/`ADMIN`/`MEMBRO` sincronizado pelos webhooks da Clerk (spec 003). A spec 003 decidiu, com base na ADR_121 (11/10): "o webhook nunca atribui DONO; só o fluxo de compra atribui DONO; um Membro DONO mantém DONO em qualquer sincronização". No mesmo dia 17/11, a decisão D1 do time (registrada no clarify da LLT-119) passou a valer o modelo de membro da 163 sobre o da 167/168, e o Pedro pediu explicitamente: "na LLT-120, partir do modelo da 163, não do `CompanyMember`". O pacote (R1) foi montado antes disso e cria uma coleção própria, `CompanyOwner`.

- Q: ⚠ Onde fica e como nasce o Dono enquanto não existe o fluxo de compra? (C1) → A: **O Dono é o `member` com papel `DONO` da coleção da LLT-163 (ADR_122/123). Esta entrega só lê esse papel e não grava Dono.** Quem atribui DONO é o fluxo de compra (ADR_121, spec 003). Para testar enquanto a compra não existe, o Dono é marcado à mão no banco local (comando no `quickstart.md`). A coleção `CompanyOwner` e o "primeiro administrador vira Dono" do pacote ficam descartados, porque contrariam a ADR_121 e a spec 003, que são as fontes mais recentes. *Alternativa, se o Pedro preferir:* promover o primeiro administrador a `DONO` na própria coleção `member`, o que exige mudar a regra da spec 003 com o Felipe.
- Q: ⚠ De onde vem o papel de uma pessoa que não é a da sessão (alvo das rotas de acesso por produto e de cargos)? (decorre da C1) → A: Da coleção `member` (LLT-163), quando a pessoa tem registro ativo nela (`removedAt` e `deletedAt` vazios): `DONO` → OWNER, `ADMIN` → ADMIN, `MEMBRO` → MEMBER. Sem registro na `member`, vale o `administrator` da `CompanyMember` (LLT-168, gravado a cada requisição). Sem nenhum dos dois, MEMBER. A leitura fica isolada num único componente (`MemberRoleDirectory`), o ponto a trocar quando o time unificar as duas coleções (pendência da D1).
- Q: As rotas novas ficam em inglês? (C2) → A: Sim, `/api/company/notification-roles` (constituição IV). Equivale à `/papeis` da ADR_139, que é só uma proposta.
- Q: Quais são as listas fechadas de gravidade e de tipo de alerta? (C3) → A: Gravidades `ERROR`, `WARNING` e `INFO` (ADR_170). Tipos `OCCURRENCE`, `CRITICAL_STATE` e `SYSTEM_ALERT` (ADR_060).
- Q: Com que valores nascem Gestor, Supervisor e Membro? (C4) → A: Com as listas vazias. Dono e Administrador recebem tudo, de forma fixa (ADR_137).
- Q: As listas ficam dentro do documento do cargo? (C5) → A: Sim, arrays no documento `NotificationRole` (atende à ADR_142). Avisar quem cuida do módulo Notificação.
- Q: Pode mudar o `CompanyMemberController` (LLT-168) para proteger o Dono? (C6) → A: Sim, a mudança mínima na branch do Pedro, avisando o Vinicius no PR.
- Q: Um Membro continua podendo criar produto? (C7) → A: Sim, é o comportamento atual e fica registrado na matriz. Mudar exige decisão do time.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Saber o papel de acesso e o que ele permite (Priority: P1)

Como pessoa da empresa, quero que o sistema saiba se eu sou Dono, Administrador ou Membro e o que cada papel pode fazer, para que as telas mostrem só o que eu posso usar.

**Why this priority**: As outras histórias dependem do papel. A matriz é o "catálogo de cargos e matriz de permissões" da LLT-169, do lado dos papéis de acesso.

**Independent Test**:
- `GET /api/company/access/me` devolve o papel e as permissões de quem chamou.
- `GET /api/company/access` devolve, só para o Dono ou um Administrador, o catálogo dos três papéis, a matriz completa e o `ownerUserId`.
- O Dono é quem tem o papel `DONO` na coleção `member` (C1). Nos testes, ele é gravado direto no banco.

**Acceptance Scenarios**:

1. **Given** a pessoa da sessão tem `member` ativo com papel `DONO` e o token é de administrador, **When** ela chama `GET /api/company/access/me`, **Then** a resposta traz `role` igual a `OWNER` e as permissões do Dono.
2. **Given** a sessão é de administrador sem `member` DONO, **When** ela chama, **Then** a resposta traz `ADMIN`, e nenhum Dono é gravado (só o fluxo de compra grava, ADR_121).
3. **Given** a sessão é de membro, **When** ele chama, **Then** a resposta traz `MEMBER`.
4. **Given** a empresa tem um `member` DONO, **When** um administrador chama `GET /api/company/access`, **Then** `ownerUserId` traz o `clerkUserId` do Dono. Sem `member` DONO ativo, `ownerUserId` é `null`.
5. **Given** o `member` DONO chega num token com cargo `member` (rebaixado fora do sistema), **When** ele chama, **Then** a resposta traz `MEMBER` e é gravado um log WARN `event=access.owner.not_admin`.
6. **Given** a sessão é de membro, **When** ele chama `GET /api/company/access`, **Then** recebe 403 `not_admin`.
7. **Given** não há sessão, a resposta é 401. **Given** a sessão não tem empresa ativa, a camada de segurança da LLT-163 responde 403 `{"error":"forbidden"}`.
8. **Given** o `member` DONO foi removido da organização ou apagado (`removedAt` ou `deletedAt`), **When** ele chama, **Then** não vale como Dono.

---

### User Story 2 - Restringir o acesso de um membro por produto (Priority: P1)

Como Dono ou Administrador, quero escolher quais produtos cada Membro pode acessar, para que cada pessoa veja só os produtos liberados para ela (USID_030.1, LLT-171).

**Why this priority**: É a restrição de acesso que já é usada pela lista de produtos. Hoje só existe a gravação automática para quem cria o produto, e falta como o administrador libera ou retira.

**Independent Test**: O Dono libera os produtos A e B para um Membro. O Membro lista os produtos e vê só A e B, e o detalhe de C responde 404. O Dono retira B, e o Membro passa a ver só A.

**Acceptance Scenarios**:

1. **Given** um Membro sem acessos, **When** o Dono grava `PUT /api/company/members/{userId}/products` com `[A, B]`, **Then** o `GET` do mesmo caminho devolve `[A, B]` e a lista de produtos do Membro traz só A e B.
2. **Given** o Membro acessa `[A, B]`, **When** o Dono grava `[A]`, **Then** o acesso a B é retirado e o detalhe de B responde 404 para o Membro.
3. **Given** a lista enviada contém um produto de outra empresa ou inexistente, **When** o Dono grava, **Then** a resposta é 404 `product_not_found` e nada muda.
4. **Given** o alvo é o Dono ou um administrador conhecido pelo Core, **When** alguém tenta gravar os acessos dele, **Then** a resposta é 409 `full_access` "Este membro acessa todos os Produtos." O `GET` devolve `allProducts: true`.
5. **Given** a sessão é de membro, **When** ele tenta ler ou gravar acessos, **Then** recebe 403 `not_admin` e a sessão continua válida.
6. **Given** a mesma lista é gravada duas vezes, **When** a segunda gravação termina, **Then** o resultado é o mesmo e não aparecem registros duplicados.
7. **Given** um produto arquivado, **When** o Dono o inclui na lista, **Then** o acesso é gravado. Arquivar não apaga acessos.

---

### User Story 3 - Manter o catálogo de cargos de notificação (Priority: P2)

Como Dono ou Administrador, quero criar, renomear, configurar e excluir os cargos de notificação da empresa, para que cada alerta chegue às pessoas certas (USID_052, ADR_136 a ADR_140, LLT-169). Os cargos não dão permissão de acesso.

**Why this priority**: Sem os cargos, não existe a quem atribuir (US4), e o módulo Notificação não tem matriz para ler.

**Independent Test**:
- O primeiro `GET /api/company/notification-roles` cria os cinco cargos padrão: Dono e Administrador imutáveis; Gestor, Supervisor e Membro editáveis.
- Criar um cargo personalizado com gravidades e tipos.
- Renomear, trocar as listas e excluir.
- Tentar alterar o Dono ou o Administrador responde 409.

**Acceptance Scenarios**:

1. **Given** uma empresa sem cargos, **When** o Dono lista, **Then** recebe Dono, Administrador, Gestor, Supervisor e Membro, com `isDefault: true`, e `immutable: true` só em Dono e Administrador. Chamar de novo, ou duas vezes ao mesmo tempo, não duplica.
2. **Given** um nome novo e pelo menos uma gravidade e um tipo, **When** o Dono cria, **Then** recebe 201 com `isDefault: false` e `immutable: false`.
3. **Given** já existe um cargo não excluído com o mesmo nome (ignorando maiúsculas e espaços nas pontas), **When** alguém cria ou renomeia para esse nome, **Then** a resposta é 409 `role_name_in_use`.
4. **Given** o cargo é o Dono ou o Administrador, **When** alguém tenta renomear, excluir ou trocar listas e membros, **Then** a resposta é 409 `immutable_role`, ou `members_from_access_role` no caso dos membros.
5. **Given** um cargo editável, **When** o Dono exclui, **Then** recebe 204, o cargo some da lista para sempre e qualquer rota com o id dele responde 404 `role_not_found`.
6. **Given** um cargo de outra empresa, **When** alguém usa o id dele, **Then** recebe 404 `role_not_found`.
7. **Given** uma lista de gravidades ou de tipos vazia, ou com valor fora das listas fechadas, **When** alguém grava, **Then** a resposta é 400 com o campo indicado.
8. **Given** a `version` enviada está desatualizada, **When** alguém grava, **Then** a resposta é 409 "Os dados foram alterados. Recarregue antes de salvar novamente."
9. **Given** a sessão é de membro, **When** ele chama qualquer rota de cargos, **Then** recebe 403 `not_admin`.

---

### User Story 4 - Atribuir cargo a um usuário (Priority: P2)

Como Dono ou Administrador, quero incluir e retirar pessoas de cada cargo de notificação e ver em quais cargos uma pessoa está (USID_052, ADR_139/140, LLT-170).

**Why this priority**: É o "atribuir cargo" do card. Sem membros, o cargo não entrega alerta.

**Independent Test**: O Dono inclui o Membro X no cargo Gestor. Depois, `GET /api/company/members/X/notification-roles` traz o Gestor. Retirar X faz o Gestor sair da lista. Substituir a lista inteira também funciona.

**Acceptance Scenarios**:

1. **Given** um cargo editável, **When** o Dono chama `POST /api/company/notification-roles/{roleId}/members/{userId}`, **Then** a pessoa entra no cargo. Repetir a chamada não duplica.
2. **Given** a pessoa está no cargo, **When** o Dono chama `DELETE` do mesmo caminho, **Then** ela sai, e repetir não dá erro.
3. **Given** uma lista nova e a `version` atual, **When** o Dono grava `PUT .../members`, **Then** a lista é trocada inteira.
4. **Given** o cargo é Dono ou Administrador, **When** alguém tenta mudar os membros, **Then** a resposta é 409 `members_from_access_role` "Os membros deste cargo vêm do papel de acesso." (ADR_140, USID_052 FA03).
5. **Given** a pessoa está em dois cargos, **When** o Dono consulta os cargos dela, **Then** recebe os dois, sem os excluídos.
6. **Given** um cargo é excluído, **When** alguém consulta os cargos das pessoas que estavam nele, **Then** esse cargo não aparece. A pessoa continua na empresa e nos outros cargos (ADR_137/138).
7. **Given** um `userId` vazio ou com mais de 200 caracteres, **When** alguém grava, **Then** a resposta é 400 "Informe o usuário.".

---

### User Story 5 - Proteger o Dono (Priority: P3)

Como Dono, quero que ninguém me desative pelo sistema, para que a empresa nunca perca o Dono.

**Why this priority**: É complemento da US1 e depende do Dono existir.

**Independent Test**: Um Administrador tenta desativar o Dono pelo `POST /api/company/members/{dono}/deactivation` (LLT-168) e recebe 403 `owner_protected`. O Dono continua ativo.

**Acceptance Scenarios**:

1. **Given** o alvo é o Dono, **When** um Administrador tenta desativá-lo, **Then** a resposta é 403 `owner_protected` "Não é possível desativar o Dono da empresa." e nada muda.
2. **Given** o alvo não é o Dono, **When** um Administrador desativa, **Then** vale a regra da LLT-168, sem mudança.
3. O rebaixamento do Dono na Clerk é bloqueado no Web (spec do Web). O Core não troca cargo na Clerk.

### Edge Cases

- **Sessão e empresa:** sem sessão, 401. Sem empresa ativa, a camada de segurança da LLT-163 responde 403 `{"error":"forbidden"}`. Identificador de outra empresa no corpo é ignorado e vale sempre a empresa da sessão.
- **Pessoa desativada (LLT-168):** recebe 403 `user_deactivated` antes de qualquer regra desta feature. O interceptor já faz isso.
- **Promoção e rebaixamento:**
  - os registros de AcessoProduto de um Membro promovido a Administrador continuam guardados, mas são ignorados enquanto ele for Administrador;
  - se ele voltar a Membro, os acessos antigos voltam a valer.
- **Papel de outra pessoa (alvo):** vem da coleção `member` (LLT-163) quando há registro ativo; sem ele, do `administrator` da `CompanyMember` (LLT-168); sem nenhum dos dois, a pessoa é tratada como Membro nas rotas de acesso por produto. O Web impede que se configurem acessos para quem é admin na Clerk.
- **Cargos de pessoas desativadas:** uma pessoa desativada continua nos cargos. Quem entrega os alertas (módulo Notificação) precisa pular desativados. Isso fica registrado no contrato.
- **Exclusão de cargo:**
  - exclusão lógica e definitiva (`deleted`, `deletedAt`), sem restauração (ADR_139);
  - o nome de um cargo excluído pode ser reutilizado;
  - o aviso de exclusão ao Dono e aos Administradores (ADR_138) depende do módulo Notificação e não entra nesta entrega. Fica só o log `event=notification_role.deleted`.
- **Inclusão de membro em cargo:** é uma operação atômica (`$addToSet`/`$pull`), sem `version`. As trocas de lista e de nome usam `version`.
- **Limites de tamanho:**
  - nome do cargo de 1 a 60 caracteres;
  - até 200 membros por cargo, conferido no `PUT` e no `POST` de membro. Com o cargo cheio, o `POST` responde 409 `role_members_limit` "O cargo já tem 200 membros." (analyze A5);
  - até 500 produtos por gravação de acesso.

## Requirements *(mandatory)*

### Functional Requirements

**Papéis de acesso e matriz (US1, US5)**

- **FR-001**: O Core MUST reconhecer três papéis de acesso: `OWNER` (Dono), `ADMIN` (Administrador) e `MEMBER` (Membro). Para a pessoa da sessão, eles são resolvidos assim:
  - `OWNER` se ela tem `member` ativo com papel `DONO` na empresa da sessão **e** o token é de administrador;
  - `ADMIN` se o token é de administrador;
  - `MEMBER` nos demais casos.
- **FR-002**: O Dono MUST ser lido da coleção `member` da LLT-163 (papel `DONO`, `removedAt` e `deletedAt` vazios). Esta entrega MUST NOT gravar nem alterar o papel `DONO`: quem atribui é o fluxo de compra (ADR_121, spec 003). Se houver mais de um `member` DONO ativo na empresa (dado inválido), vale o de `clerkUserId` menor, com um log WARN `event=access.owner.duplicated` (C1).
- **FR-003**: O `member` DONO que chega com token de membro MUST ser tratado como `MEMBER`, e o caso MUST gerar um log WARN `event=access.owner.not_admin`. O registro dele MUST NOT ser alterado.
- **FR-004**: A matriz de permissões MUST ser fixa no código (enum `Permission` × `AccessRole`), conforme a tabela de `data-model.md`, e MUST ser exposta:
  - em `GET /api/company/access/me`, para qualquer sessão com empresa, com o papel e a lista de permissões de quem chamou;
  - em `GET /api/company/access`, só para o Dono ou um Administrador, com os papéis, a matriz completa e o `ownerUserId`.
- **FR-005**: As rotas novas desta feature MUST autorizar pela matriz (`AccessPolicy.require`). As rotas já existentes continuam com a regra atual de administrador, que é equivalente a `OWNER` ou `ADMIN`. Esta entrega MUST NOT mudar o comportamento de rotas existentes, exceto o FR-020.

**Acesso por produto (US2)**

- **FR-006**: `GET /api/company/members/{userId}/products` MUST devolver os `productIds` liberados para a pessoa na empresa da sessão e `allProducts`. `allProducts` é `true` quando o alvo é Dono ou administrador conhecido, pela regra de papel do alvo (clarify 18/11): `member` ativo da LLT-163 primeiro; sem ele, o `administrator` da `CompanyMember` (LLT-168).
- **FR-007**: `PUT /api/company/members/{userId}/products` MUST substituir os acessos da pessoa pela lista enviada, criando os que faltam e removendo os que sobram, sem duplicar. A coleção usada é a `AcessoProduto` que já existe, com id `productId:userId`.
- **FR-008**: Todos os produtos da lista MUST pertencer à empresa da sessão, arquivados inclusive. Se algum não pertencer, a resposta MUST ser 404 `product_not_found` "Produto não encontrado nesta empresa." e nada MUST ser gravado.
- **FR-009**: Gravar acessos do Dono ou de um administrador conhecido MUST responder 409 `full_access` "Este membro acessa todos os Produtos.".
- **FR-010**: A lista e o detalhe de produtos MUST continuar respeitando o AcessoProduto para `MEMBER`, como já fazem. Produto sem acesso MUST responder 404, que é a negação simples da ADR_126, sem encerrar a sessão.

**Cargos de notificação (US3)**

- **FR-011**: Cada empresa MUST ter os cargos padrão Dono, Administrador, Gestor, Supervisor e Membro.
  - Dono e Administrador MUST ser `immutable`.
  - Os cinco MUST ser `isDefault`.
  - A criação MUST ser idempotente e segura sob concorrência, com índice único (`tenantId`, `defaultKey`). Ela acontece no primeiro uso das rotas de cargos pela empresa.
- **FR-012**: O Dono ou um Administrador MUST poder:
  - listar os cargos não excluídos;
  - ver o detalhe de um cargo (gravidades, tipos e membros; para Dono e Administrador, `membersSource: ACCESS_ROLE`);
  - criar um cargo personalizado com nome, uma ou mais gravidades e um ou mais tipos;
  - renomear;
  - trocar as gravidades;
  - trocar os tipos;
  - excluir.
- **FR-013**: As gravidades válidas são `ERROR`, `WARNING` e `INFO`, e os tipos válidos são `OCCURRENCE`, `CRITICAL_STATE` e `SYSTEM_ALERT` (C3). Lista vazia ou com valor fora dessas listas MUST responder 400 com o campo indicado. Os cargos padrão Gestor, Supervisor e Membro MUST nascer com as listas vazias (C4).
- **FR-014**: O nome MUST ter de 1 a 60 caracteres depois de tirar os espaços das pontas e MUST ser único por empresa entre os não excluídos, sem diferenciar maiúsculas (normalização com `Locale.ROOT`, como na LLT-177). Nome repetido MUST responder 409 `role_name_in_use` "Já existe um cargo com este nome nesta empresa.".
- **FR-015**: Renomear, excluir, trocar listas ou mudar membros de Dono e Administrador MUST responder 409. O código é `immutable_role` "Este cargo é padrão e não pode ser alterado.", ou `members_from_access_role` no caso dos membros.
- **FR-016**: A exclusão MUST ser lógica e definitiva (`deleted = true`, `deletedAt`) e responder 204. Cargo excluído ou de outra empresa MUST responder 404 `role_not_found` "Cargo não encontrado." em todas as rotas.
- **FR-017**: As trocas de nome e de listas MUST exigir `version`. Uma `version` divergente MUST responder 409 com a mensagem padrão do handler.

**Atribuição de cargo (US4)**

- **FR-018**: O Dono ou um Administrador MUST poder:
  - incluir uma pessoa num cargo editável (idempotente);
  - retirar uma pessoa (idempotente);
  - substituir a lista de membros (com `version`, até 200).
  
  `userId` vazio ou com mais de 200 caracteres MUST responder 400 "Informe o usuário.".
- **FR-019**: `GET /api/company/members/{userId}/notification-roles` MUST devolver os cargos não excluídos em que a pessoa está. Para Dono e Administrador, a inclusão vem do papel de acesso.

**Proteção do Dono (US5)**

- **FR-020**: `POST /api/company/members/{userId}/deactivation` (LLT-168) MUST recusar o Dono com 403 `owner_protected` "Não é possível desativar o Dono da empresa.", antes da regra de último administrador e sem gravar nada (C6).

**Gerais**

- **FR-021**: Permissão negada MUST responder 403 `not_admin` "Somente o Dono ou um Administrador pode fazer esta ação." e MUST NOT encerrar a sessão (ADR_109/126).
- **FR-022**: Os eventos de negócio MUST gerar log INFO no padrão `event=` da constituição VI, sem dado pessoal:
  - `access.owner.not_admin` e `access.owner.duplicated` (WARN);
  - `product_access.updated`, com as contagens de incluídos e removidos;
  - `notification_role.created`, `notification_role.updated` e `notification_role.deleted`;
  - `notification_role.members.updated`.
- **FR-023**: Esta entrega MUST NOT usar `CLERK_SECRET_KEY` nem chamar a API da Clerk, e MUST NOT trocar cargo na Clerk. A troca entre `admin` e `member` continua no Web (LLT-168).
- **FR-024**: Fora do escopo:
  - transferir o Dono;
  - gravar o Dono (o fluxo de compra cria o `member` DONO, ADR_121);
  - o ACESSO_PENDENTE (ADR_129/135);
  - a entrega e o aviso de alertas pelo módulo Notificação;
  - a auditoria em tela;
  - o limite de membros por plano;
  - qualquer mudança no convite (LLT-167), além do que o Web faz na spec do Web.

### Key Entities

- **Papel de acesso (`AccessRole`)**: OWNER, ADMIN ou MEMBER. Corresponde a `Membro.papel` DONO/ADMIN/MEMBRO da ADR_122. Para a sessão, é resolvido pelo token e pelo `member` DONO; para outra pessoa, pela `member` e, na falta dela, pela `CompanyMember`.
- **Dono da empresa**: o `member` (LLT-163) com papel `DONO`, ativo. Não há coleção nova para o Dono (C1).
- **Permissão (`Permission`)**: uma ação do sistema. A matriz liga cada permissão aos papéis que a têm.
- **Acesso a produto (`AcessoProduto`, já existe)**: `tenantId`, `productId`, `clerkUserId`. Vale só para MEMBER.
- **Cargo de notificação (`NotificationRole`)**: corresponde ao `PapelNotificacao` da ADR_134/136. Campos: `tenantId`, `name`, `normalizedName`, `defaultKey`, `isDefault`, `immutable`, `severities`, `alertTypes`, `memberUserIds`, `deleted`, `deletedAt`, `version`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% das chamadas, o papel OWNER só aparece para quem tem `member` DONO ativo e token de administrador, e nenhuma rota desta feature grava ou altera o papel DONO.
- **SC-002**: Em 100% das consultas de um Membro, a lista de produtos traz só os liberados, e o detalhe de um produto não liberado responde 404.
- **SC-003**: Um administrador configura os produtos de um Membro e a mudança vale na próxima requisição do Membro, sem novo login.
- **SC-004**: Em 100% das tentativas de alterar Dono ou Administrador no catálogo de cargos, nada muda e a resposta é 409.
- **SC-005**: Em 100% das tentativas de desativar o Dono, ele continua ativo.
- **SC-006**: Depois da entrega, nenhum segredo da Clerk aparece no repositório, e `.\mvnw.cmd verify` fica verde.

## Assumptions

- **Branch:** `LLT-120-cargos-permissoes`, criada a partir da `Development` atualizada (que já tem LLT-167, 168 e 177). Commits no formato `LLT-120: descricao`.
- **Pasta da spec:** `specs/011-cargos-permissoes`. O pacote sugeria a 009, mas em 18/11/2026 a `Development` já ia até a 009 (007 LLT-173, 008 LLT-163, 009 LLT-178) e a 010 é da LLT-179 (PR #8). A LLT-119 ainda usa `007-validar-gestao-usuarios` na branch dela e vai precisar de outro número.
- **Cargo vs. permissão:** "cargo" no card significa cargo de notificação (USID_052: "os cargos não dão permissão de acesso"). A permissão vem do papel de acesso.
- **Telas:** as telas desta feature estão na spec do Web (`lhamalog-web/specs/005-cargos-permissoes`).
- **Notificação:** o módulo Notificação, de outra pessoa, vai ler a coleção `NotificationRole` e o `AcessoProduto` para entregar os alertas (ADR_165).

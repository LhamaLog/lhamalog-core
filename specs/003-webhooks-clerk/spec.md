# Feature Specification: Webhooks da Clerk

**Feature Branch**: `feature/LLT-164-webhooks-clerk` (a ser criada pelo desenvolvedor; diretório de spec `specs/003-webhooks-clerk`)

**Created**: 2026-11-06

**Status**: Draft

**Jira**: LLT-164

**Input**: "Receber os webhooks da Clerk (Svix) em `POST /webhooks/clerk`, verificar a assinatura sobre o corpo bruto, deduplicar por `svix-id`, tolerar eventos fora de ordem e manter a coleção Membro a partir dos eventos de usuário e de vínculo com organização."

**Fontes**: ADR_022, ADR_033, ADR_121, ADR_122, ADR_123, ADR_141, ADR_159 (as mais novas prevalecem), constituição v2.0.0. Depende da LLT-163 (`specs/002-validacao-token-clerk`): rota pública, Tenant e `TenantRepository`.

## Clarifications

### Session 2026-11-06

Decisões tomadas pela IA por delegação do desenvolvedor ("o que achar melhor", "olhe as ADRs e decida").

- Q: Verificar a assinatura com `com.svix:svix` ou com o JDK? → A: Com o JDK (`HmacSHA256`, comparação em tempo constante), seguindo o algoritmo documentado pelo Svix. Nenhuma dependência nova.
- Q: O webhook cria o Tenant (ADR_033) ou não (ADR_121)? → A: Não cria. A ADR_121 (11/10) é mais nova que a ADR_033 (02/10): o Tenant nasce na compra da assinatura, e o comprador vira Membro DONO pelo fluxo de compra. Evento de vínculo para organização sem Tenant: 2xx, ignorado e registrado em log.
- Q: Onde guardar o usuário? → A: No Membro (ADR_159: "o Membro passa a ser a referência de pessoa; o clerkUserId fica só no Membro"). Não existe coleção de usuário; todo Membro tem `tenantId`.
- Q: Como distinguir DONO de ADMIN no `org:admin`? → A: O webhook nunca atribui DONO. `org:admin` → ADMIN, `org:member` → MEMBRO. Um Membro já DONO mantém DONO em qualquer evento de sincronização.
- Q: Branch base? → A: Mesmo working tree da LLT-163; o desenvolvedor cria branches e commits.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Só webhooks autênticos são aceitos (Priority: P1)

A Clerk envia eventos pelo Svix. O Core aceita só requisições com assinatura válida do segredo da instância e timestamp recente, verificadas sobre o corpo bruto.

**Why this priority**: o endpoint é público (ADR_141); sem a verificação qualquer um poderia alterar Membros.

**Independent Test**: requisições assinadas no teste com um segredo fictício; variações de assinatura, corpo e timestamp.

**Acceptance Scenarios**:

1. **Given** assinatura válida e timestamp dentro da tolerância, **When** chega o webhook, **Then** 2xx.
2. **When** a assinatura não confere (segredo errado ou corpo alterado), **Then** 400 e nada é gravado.
3. **When** o timestamp está fora da tolerância (passado ou futuro), **Then** 400.
4. **When** falta algum header `svix-id`, `svix-timestamp` ou `svix-signature`, **Then** 400.

---

### User Story 2 - Membros sincronizados com os vínculos da Organization (Priority: P1)

Quando um usuário entra, muda de papel ou sai de uma Organization que corresponde a um Tenant, o Membro correspondente é criado, atualizado ou marcado como removido.

**Why this priority**: o Membro é a base de papel e de acesso (ADR_121/122).

**Independent Test**: eventos `organizationMembership.*` assinados → estado da coleção `member`.

**Acceptance Scenarios**:

1. **Given** Tenant para `org_A`, **When** chega `organizationMembership.created` com `org:admin`, **Then** existe Membro (tenantId, clerkUserId) com papel ADMIN; com `org:member`, papel MEMBRO.
2. **Given** Membro DONO, **When** chega `organizationMembership.updated` com `org:member` ou `org:admin`, **Then** o papel continua DONO.
3. **When** chega `organizationMembership.deleted`, **Then** o Membro fica marcado como removido (soft), e um evento de vínculo mais antigo que a remoção não o reativa.
4. **Given** organização sem Tenant, **When** chega um evento de vínculo, **Then** 2xx e nada é gravado.

---

### User Story 3 - Entrega repetida e fora de ordem não corrompe o estado (Priority: P1)

O Svix pode repetir e entregar fora de ordem. O Core processa cada `svix-id` uma vez e ignora eventos mais antigos que o estado salvo.

**Independent Test**: reenvio do mesmo `svix-id` e eventos com `updated_at` invertido.

**Acceptance Scenarios**:

1. **When** o mesmo `svix-id` chega duas vezes, **Then** o segundo é respondido com 2xx sem reprocessar.
2. **When** chega um evento com `updated_at` menor ou igual ao já aplicado, **Then** é ignorado.
3. **When** `organizationMembership.deleted` chega antes do `created` do mesmo vínculo, **Then** o `created` atrasado não reativa o Membro.

---

### User Story 4 - Dados e exclusão do usuário (Priority: P2)

`user.updated` atualiza nome e e-mail do usuário em todos os seus Membros; `user.deleted` faz soft delete (`deletedAt`) em todos e o usuário nunca é recriado por eventos posteriores.

**Acceptance Scenarios**:

1. **When** chega `user.updated` mais novo, **Then** nome e e-mail principal são atualizados em todos os Membros do usuário; um `user.updated` mais antigo é ignorado.
2. **When** chega `user.deleted`, **Then** todos os Membros do usuário recebem `deletedAt`.
3. **Given** usuário apagado, **When** chega qualquer evento de vínculo ou `user.updated` dele, **Then** nada é recriado nem reativado (inclusive se o `user.deleted` chegou antes de qualquer vínculo).

---

### User Story 5 - Erros e eventos desconhecidos (Priority: P2)

**Acceptance Scenarios**:

1. **When** chega um tipo de evento não tratado (ex.: `session.created`), **Then** 2xx e ignorado.
2. **When** o processamento falha (ex.: banco recusa a escrita), **Then** 5xx, o `svix-id` não é marcado como processado e um reenvio posterior é processado.

### Edge Cases

- `svix-signature` com várias assinaturas separadas por espaço (rotação de segredo): basta uma válida; versões diferentes de `v1` são ignoradas.
- Segredo não configurado: todo webhook é rejeitado com 400 e aviso no log (em prod a aplicação não sobe sem ele).
- Corpo assinado mas JSON inválido ou sem os campos obrigatórios do tipo tratado: 400.
- `user.created` sem nenhum Membro ainda: nada a gravar (o vínculo trará os dados).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `POST /webhooks/clerk` MUST ser público quanto a sessão e protegido pela assinatura Svix (`svix-id`, `svix-timestamp`, `svix-signature`) com `CLERK_WEBHOOK_SIGNING_SECRET`, calculada sobre o corpo bruto (`id.timestamp.corpo`, HMAC-SHA256, Base64).
- **FR-002**: Timestamp fora de ±5 min (configurável) MUST ser rejeitado.
- **FR-003**: Assinatura inválida, headers ausentes, timestamp fora da tolerância ou payload inválido MUST responder 400 com corpo básico.
- **FR-004**: Cada `svix-id` processado com sucesso MUST ser registrado em coleção própria com índice único e TTL; um `svix-id` já registrado MUST responder 2xx sem reprocessar.
- **FR-005**: Eventos tratados: `user.created`, `user.updated`, `user.deleted`, `organizationMembership.created`, `organizationMembership.updated`, `organizationMembership.deleted`. Outros tipos MUST responder 2xx e ser ignorados.
- **FR-006**: Escritas MUST ser upsert/condicionais e ignorar evento com versão (`updated_at`, ou o `timestamp` do evento quando o dado não tem `updated_at`) menor ou igual à aplicada.
- **FR-007**: Papéis (ADR_123): `org:admin` → ADMIN, `org:member` → MEMBRO; papel desconhecido → MEMBRO (menor privilégio) com aviso; DONO MUST NOT ser alterado por evento de sincronização.
- **FR-008**: `user.deleted` MUST fazer soft delete (`deletedAt`) de todos os Membros do usuário e registrar o usuário como apagado, para que nenhum evento posterior o recrie ou reative.
- **FR-009**: O webhook MUST NOT criar Tenant. Evento de vínculo de organização sem Tenant MUST responder 2xx e não gravar nada.
- **FR-010**: Falha de processamento MUST responder 5xx sem registrar o `svix-id`, para o Svix reenviar.
- **FR-011**: Todo documento novo MUST levar `tenantId` (ADR_121); registros que não pertencem a um tenant (usuário apagado, eventos de usuário) levam `tenantId` nulo de forma explícita.
- **FR-012**: Logs MUST NOT conter segredo, assinatura, corpo do evento nem e-mail; só `svix-id`, tipo e ids da Clerk.
- **FR-013**: Segredo só por variável de ambiente; obrigatório em prod.
- **FR-014**: O README MUST documentar como expor o endpoint em desenvolvimento (túnel) e cadastrar o endpoint na Clerk, sem URLs nem segredos reais.

### Fora do escopo

- Criação de Tenant e do Membro DONO (fluxo de compra, ADR_121).
- Eventos de organização (`organization.*`), sessão e e-mail.
- Uso do Membro na autorização das requisições (o papel DONO/ADMIN/MEMBRO na cadeia da LLT-163 fica para feature própria).
- Filtro por IP do Svix.

### Key Entities

- **Membro** (`member`): tenantId, clerkUserId, clerkMembershipId, papel (DONO|ADMIN|MEMBRO), nome, e-mail, versões aplicadas (vínculo e usuário), removedAt, deletedAt.
- **Webhook processado** (`clerk_webhook_event`): svix-id, tipo, tenantId (ou nulo), processedAt (TTL).
- **Usuário apagado** (`deleted_clerk_user`): clerkUserId, deletedAt, tenantId nulo.

## Success Criteria *(mandatory)*

- **SC-001**: Todos os cenários de aceitação cobertos por testes que passam em `.\mvnw.cmd clean verify`.
- **SC-002**: Nenhum webhook sem assinatura válida altera dados (verificado por teste).
- **SC-003**: Reenvio e ordem trocada levam ao mesmo estado final da entrega em ordem (verificado por teste).
- **SC-004**: Nenhuma dependência nova.

## Assumptions

- Formato dos eventos da Clerk: envelope `{type, data, object: "event", timestamp (ms), instance_id}`; `data.updated_at` em ms para usuário e vínculo; `user.deleted` traz `{id, deleted: true}`; vínculo traz `organization.id`, `public_user_data.user_id`, `role` no formato `org:<papel>`.
- O MongoDB local/teste é standalone (sem transações); a consistência vem de escritas idempotentes e condicionais.
- Tolerância de 5 min, igual à das bibliotecas oficiais do Svix.
- TTL de 7 dias para os `svix-id`: cobre a janela de retentativas do Svix (cerca de 1 dia) e reenvios manuais próximos.

# Tasks: Webhooks da Clerk (LLT-164)

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/clerk-webhook.md](contracts/clerk-webhook.md)

**Testes**: obrigatórios. Cada fase termina com `.\mvnw.cmd clean verify` verde.

## Phase 1: Setup

- [x] T001 Criar `src/main/java/br/com/lhamalog/core/config/ClerkWebhookProperties.java` (`clerk.webhook.signing-secret`, `clerk.webhook.tolerance` padrão 5m) e as propriedades em `application.properties` (`CLERK_WEBHOOK_TOLERANCE:5m`), `application-local.properties` (`CLERK_WEBHOOK_SIGNING_SECRET:` vazio) e `application-prod.properties` (`CLERK_WEBHOOK_SIGNING_SECRET` sem padrão)
- [x] T002 [P] Criar `src/test/java/br/com/lhamalog/core/support/TestSvix.java` (segredo fictício em memória e assinatura `v1` independente da implementação) e registrar o segredo em `ClerkTestConfiguration.java` por `DynamicPropertyRegistrar`

## Phase 2: Foundational

- [x] T003 [P] Criar `src/main/java/br/com/lhamalog/core/webhook/adapter/in/web/SvixSignatureVerifier.java` (research R1: `id.timestamp.corpo`, `whsec_` + Base64, HmacSHA256, várias assinaturas, tempo constante, ±tolerância)
- [x] T004 [P] Teste `src/test/java/br/com/lhamalog/core/webhook/adapter/in/web/SvixSignatureVerifierTests.java`: válida; várias com uma válida; segredo errado; corpo alterado; timestamp antigo e futuro; headers ausentes; timestamp não numérico; só versões diferentes de `v1`; segredo vazio
- [x] T005 [P] Criar `src/main/java/br/com/lhamalog/core/member/domain/MemberRole.java` (`DONO`, `ADMIN`, `MEMBRO`; `org:admin` → ADMIN, `org:member` → MEMBRO, desconhecido → MEMBRO; "DONO nunca é alterado por sincronização"), `MembershipSnapshot.java`, `UserProfile.java` e `Member.java` com as regras da tabela de data-model.md
- [x] T006 [P] Teste de unidade `src/test/java/br/com/lhamalog/core/member/domain/MemberTests.java`: mapeamento de papéis; DONO mantido; versão menor ou igual ignorada; remoção e reativação; apagado nunca muda
- [x] T007 Criar portas `src/main/java/br/com/lhamalog/core/member/application/port/out/MemberRepository.java` e `DeletedUserRepository.java`, e `src/main/java/br/com/lhamalog/core/webhook/application/port/out/ProcessedWebhookRepository.java`
- [x] T008 Criar adaptadores Mongo em `src/main/java/br/com/lhamalog/core/member/adapter/out/mongo/` (`member` com índice único `member_tenant_user` e índice `member_clerk_user`; escrita condicional pelas versões lidas; `deleted_clerk_user`) e `src/main/java/br/com/lhamalog/core/webhook/adapter/out/mongo/` (`clerk_webhook_event` com TTL `clerk_webhook_event_ttl` de 7 dias; chave duplicada ignorada)

## Phase 3: User Story 1 — Só webhooks autênticos (P1)

- [x] T009 [US1] Criar `src/main/java/br/com/lhamalog/core/webhook/adapter/in/web/ClerkWebhookController.java` (`POST /webhooks/clerk`, `@RequestBody byte[]`, 400/500/200 da research R6) e `ClerkWebhookEventParser.java`
- [x] T010 [US1] Teste `src/test/java/br/com/lhamalog/core/webhook/ClerkWebhookTests.java`: assinatura válida 200; inválida 400 sem gravar; corpo alterado 400; timestamp fora da tolerância 400; headers ausentes 400; JSON inválido assinado 400

## Phase 4: User Story 2 e 3 — Membros, idempotência e ordem (P1)

- [x] T011 [US2] Criar `src/main/java/br/com/lhamalog/core/member/application/MemberSynchronizer.java` (Tenant por `clerkOrgId`; sem Tenant → ignorado; tombstone; regras do domínio; escrita condicional) e `src/main/java/br/com/lhamalog/core/webhook/application/ClerkWebhookProcessor.java` (dedup antes, registro depois do sucesso) e `src/main/java/br/com/lhamalog/core/config/WebhookConfig.java`
- [x] T012 [US2] Em `ClerkWebhookTests.java`: created `org:admin` → ADMIN e `org:member` → MEMBRO; DONO não é rebaixado; deleted marca removido; organização sem Tenant → 200 e nada gravado; registro do `svix-id` com `tenantId`
- [x] T013 [US3] Em `ClerkWebhookTests.java`: mesmo `svix-id` processado uma vez; `updated_at` antigo ignorado; deleted antes de created não reativa; created mais novo reativa
- [x] T014 [US3] Teste do índice TTL e do índice único em `src/test/java/br/com/lhamalog/core/webhook/adapter/out/mongo/MongoProcessedWebhookRepositoryTests.java`

## Phase 5: User Story 4 e 5 — Usuário, desconhecidos e falhas (P2)

- [x] T015 [US4] Em `ClerkWebhookTests.java`: `user.updated` atualiza nome/e-mail de todos os Membros; mais antigo ignorado; `user.deleted` marca `deletedAt` em todos; vínculo e `user.updated` posteriores não recriam nem reativam; `user.deleted` antes de qualquer vínculo bloqueia criação
- [x] T016 [US5] Em `ClerkWebhookTests.java`: evento desconhecido 200; falha real de escrita (validator `collMod` em `member`) → 500 sem registrar o `svix-id`; reenvio após remover o validator → 200 e aplicado

## Phase 6: Polish

- [x] T017 [P] Atualizar `infra/local/.env.example` (`CLERK_WEBHOOK_SIGNING_SECRET`, `CLERK_WEBHOOK_TOLERANCE`, só nomes) e `README.md` (seção Webhooks: comportamento, variáveis, túnel em dev sem URLs reais)
- [x] T018 Varredura de segredos/logs e `.\mvnw.cmd clean verify` final; atualizar research.md com ajustes da implementação

## Dependências

Phase 1 → 2 → US1 → US2/US3 → US4/US5 → Polish. Paralelos: T002; T003/T004/T005/T006; T017.

## Sugestão de commits (feitos pelo desenvolvedor, ADR_148)

1. `LLT-164: adiciona spec, plan e tasks dos webhooks da clerk`
2. `LLT-164: verifica assinatura svix dos webhooks`
3. `LLT-164: adiciona membro e repositorios mongo`
4. `LLT-164: sincroniza membros pelos webhooks da clerk`
5. `LLT-164: documenta webhooks e tunel em desenvolvimento`

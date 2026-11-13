# Tasks: Validação do session token da Clerk no Core (LLT-163)

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/http-security.md](contracts/http-security.md), [quickstart.md](quickstart.md)

**Testes**: obrigatórios (constituição, princípio III). Cada fase termina com `.\mvnw.cmd clean verify` verde (Docker aberto).

**Pré-condição de ambiente**: JDK 25 disponível e `JAVA_HOME` definido (research R13).

Formato: `- [ ] Txxx [P?] [História?] descrição com caminho`. `[P]` = pode ser feita em paralelo (arquivos diferentes, sem dependência pendente).

## Phase 1: Setup (configuração)

- [x] T001 Criar `src/main/java/br/com/lhamalog/core/config/ClerkProperties.java`: record `@ConfigurationProperties("clerk") @Validated` com `issuer` (`@NotBlank`), `jwksUri` (opcional; padrão `issuer + "/.well-known/jwks.json"`), `authorizedParties` (`List<String>`), `clockSkew` (`Duration`, padrão 5s), `secretKey` (opcional), `apiUrl` (padrão `https://api.clerk.com`), `connectTimeout` (2s), `readTimeout` (3s) — research R11
- [x] T002 Adicionar as propriedades `clerk.*` com `${VARIAVEL}` em `src/main/resources/application.properties`, `application-local.properties` (`CLERK_AUTHORIZED_PARTIES:http://localhost:5173`, `CLERK_SECRET_KEY:` vazio) e `application-prod.properties` (sem padrão para `CLERK_AUTHORIZED_PARTIES` e `CLERK_SECRET_KEY`; atualizar comentário de obrigatórias)
- [x] T003 [P] Criar `src/test/resources/config/application.properties` com `clerk.issuer` e `clerk.authorized-parties` fictícios (não secretos) para os testes de contexto subirem
- [x] T004 [P] Criar `src/test/java/br/com/lhamalog/core/support/TestJwks.java`: par RSA 2048 em memória, `HttpServer` do JDK servindo o JWK público em porta aleatória, e builder de tokens RS256 com claims padrão válidas (`iss`, `azp`, `sts=active`, `sub`, `sid`, `iat`, `nbf`, `exp` +60 s, `v=2`, `o`) e métodos para alterar/remover claims ou assinar com outra chave

## Phase 2: Foundational (bloqueia as histórias)

- [x] T005 [P] Criar `src/main/java/br/com/lhamalog/core/tenant/domain/Tenant.java` (record `tenantId`, `clerkOrgId`, `name`, `status`) e `TenantStatus.java` (`ACTIVE`, `SUSPENDED`; "só `ACTIVE` dá acesso")
- [x] T006 [P] Criar a porta `src/main/java/br/com/lhamalog/core/tenant/application/port/out/TenantRepository.java` (`Optional<Tenant> findByClerkOrgId(String clerkOrgId)`)
- [x] T007 Criar `src/main/java/br/com/lhamalog/core/tenant/adapter/out/mongo/TenantDocument.java` (`@Document("tenant")`, `_id` = tenantId) e `MongoTenantRepository.java` implementando a porta com `MongoTemplate`, criando na subida o índice único `tenant_clerk_org_id` em `clerkOrgId` (idempotente) — research R8
- [x] T008 Teste `src/test/java/br/com/lhamalog/core/tenant/adapter/out/mongo/MongoTenantRepositoryTests.java` (Testcontainers): encontra por `clerkOrgId`, retorna vazio quando não existe, índice único rejeita `clerkOrgId` duplicado
- [x] T009 [P] Criar em `src/main/java/br/com/lhamalog/core/identity/domain/`: `RequestContext.java`, `OrganizationRole.java` (`ADMIN`, `MEMBER`; `Optional<OrganizationRole> fromClerk(String)` aceitando `admin`/`org:admin`/`member`/`org:member`), `ClerkSessionClaims.java`, `AccessDecision.java` (`Granted(RequestContext)` | `Denied(reason)` com `NO_ACTIVE_ORGANIZATION`, `UNKNOWN_ROLE`, `TENANT_NOT_FOUND`, `TENANT_INACTIVE`)
- [x] T010 [P] Criar as portas `src/main/java/br/com/lhamalog/core/identity/application/port/CurrentRequestContext.java` e `src/main/java/br/com/lhamalog/core/identity/application/port/out/SessionRevocation.java` (`void revokeAllActiveSessions(String clerkUserId)`)
- [x] T011 [P] Teste de unidade `src/test/java/br/com/lhamalog/core/identity/domain/OrganizationRoleTests.java` (com e sem prefixo `org:`, valor desconhecido, nulo)

**Checkpoint**: `clean verify` verde.

## Phase 3: User Story 2 — Token inválido responde 401 (P1) 🎯 MVP

**Goal**: nenhuma rota protegida aceita requisição sem token válido; 401 uniforme.

**Independent Test**: `ClerkTokenAuthenticationTests` cenários 401 + `SecurityConfigTests`.

- [x] T012 [P] [US2] Criar `src/main/java/br/com/lhamalog/core/identity/adapter/in/security/AuthorizedPartyValidator.java` (`azp` obrigatório e na lista) e `ActiveSessionValidator.java` (`sts` obrigatório e igual a `active`), ambos `OAuth2TokenValidator<Jwt>` — research R3
- [x] T013 [P] [US2] Criar `src/main/java/br/com/lhamalog/core/identity/adapter/in/security/BasicErrorAuthenticationEntryPoint.java`: 401, `WWW-Authenticate: Bearer`, `{"error":"unauthorized"}` — research R4
- [x] T014 [US2] Alterar `src/main/java/br/com/lhamalog/core/config/SecurityConfig.java`: `JwtDecoder` (`NimbusJwtDecoder.withJwkSetUri`, RS256) com `JwtTimestampValidator(clockSkew)`, `JwtIssuerValidator`, T012; `oauth2ResourceServer` com o entry point da T013; rotas públicas da FR-011; remover `httpBasic`; manter stateless e CSRF desligado; habilitar `ClerkProperties`
- [x] T015 [US2] Atualizar `src/test/java/br/com/lhamalog/core/config/SecurityConfigTests.java`: health e info públicos; `/api/anything` sem token → 401 com `{"error":"unauthorized"}`; `POST /webhooks/clerk` não exige token (não retorna 401)
- [x] T016 [US2] Criar `src/test/java/br/com/lhamalog/core/identity/adapter/in/security/ClerkTokenAuthenticationTests.java` (MockMvc + Testcontainers + `TestJwks` via `@DynamicPropertySource clerk.jwks-uri`) com os casos 401: sem token, esquema diferente de Bearer, assinatura de outra chave, expirado, `nbf` futuro, `iss` diferente, `azp` fora da lista, `azp` ausente, `sts=pending`, `sts` ausente; e verificação de corpo idêntico e `WWW-Authenticate` sem `error_description`

**Checkpoint**: `clean verify` verde.

## Phase 4: User Story 1 — Token válido com Tenant ativo responde 200 (P1)

**Goal**: requisição válida passa e o contexto chega ao código de negócio pela porta.

**Independent Test**: rota de teste devolve o `RequestContext` esperado.

- [x] T017 [US1] Criar `src/main/java/br/com/lhamalog/core/identity/application/AccessResolver.java` (sem Spring): regras da research R5, usando `TenantRepository`
- [x] T018 [P] [US1] Teste de unidade `src/test/java/br/com/lhamalog/core/identity/application/AccessResolverTests.java` com repositório em memória: concedido; sem organização; papel desconhecido; Tenant inexistente; Tenant `SUSPENDED`
- [x] T019 [US1] Criar `src/main/java/br/com/lhamalog/core/identity/adapter/in/security/ClerkAuthentication.java` e `ClerkAuthenticationConverter.java` (Jwt → `ClerkSessionClaims` → `AccessResolver` → `ClerkAuthentication` com a `AccessDecision`)
- [x] T020 [US1] Criar `src/main/java/br/com/lhamalog/core/identity/adapter/in/security/TenantAccessAuthorizationManager.java` (concede só `Granted`) e `SecurityContextCurrentRequestContext.java` (implementa `CurrentRequestContext`)
- [x] T021 [US1] Criar `src/main/java/br/com/lhamalog/core/config/IdentityConfig.java` com os beans de `AccessResolver`, converter e porta; ligar converter e authorization manager no `SecurityConfig.java`
- [x] T022 [P] [US1] Criar `src/test/java/br/com/lhamalog/core/support/ProtectedTestController.java` (só teste) com `GET /test/context` devolvendo o `RequestContext` da porta
- [x] T023 [US1] Em `ClerkTokenAuthenticationTests.java`: token válido + Tenant `ACTIVE` (fixture) → 200 e JSON com `clerkUserId`, `sessionId`, `clerkOrgId`, `role`, `tenantId`; papel `org:admin` também normaliza para `ADMIN`

**Checkpoint**: `clean verify` verde.

## Phase 5: User Story 3 — Acesso negado responde 403 e revoga sessões (P2)

**Goal**: 403 básico e revogação de todas as sessões, sem que falha da Clerk altere a resposta.

**Independent Test**: fake da porta registra a revogação; fake que lança exceção mantém o 403.

- [x] T024 [US3] Criar `src/main/java/br/com/lhamalog/core/identity/adapter/in/security/SessionRevokingAccessDeniedHandler.java`: chama `SessionRevocation` com o `sub` (captura qualquer exceção, log `WARN` sem segredo) e responde 403 `{"error":"forbidden"}`; ligar no `SecurityConfig.java`
- [x] T025 [P] [US3] Criar `src/test/java/br/com/lhamalog/core/support/RecordingSessionRevocation.java` (fake que registra `clerkUserId` e pode ser configurado para falhar), registrado como `@Primary` nos testes de segurança
- [x] T026 [US3] Em `ClerkTokenAuthenticationTests.java`: sem claim `o` → 403 e revogação; `o.id` sem Tenant → 403 e revogação; Tenant `SUSPENDED` → 403; `o.rol` desconhecido → 403; fake falhando → continua 403; casos 401 não acionam revogação
- [x] T027 [US3] Criar `src/main/java/br/com/lhamalog/core/identity/adapter/out/clerk/ClerkBackendSessionRevocation.java` com `RestClient` (base `clerk.api-url`, `Authorization: Bearer` da secret key, timeouts de T001): `GET /v1/sessions?user_id=&status=active&limit=500` (array ou `{data}`), `POST /v1/sessions/{id}/revoke`; sem secret key → `WARN` e retorna; registrar bean em `IdentityConfig.java` — research R6
- [x] T028 [US3] Teste `src/test/java/br/com/lhamalog/core/identity/adapter/out/clerk/ClerkBackendSessionRevocationTests.java` contra `HttpServer` do JDK: query e header corretos; revoga cada sessão (array e `{data}`); erro HTTP na listagem/revogação lança exceção tratável; secret key vazia não chama o servidor

**Checkpoint**: `clean verify` verde.

## Phase 6: Polish & transversais

- [x] T029 Configurar CORS em `src/main/java/br/com/lhamalog/core/config/SecurityConfig.java` (origens = `clerk.authorized-parties`, métodos e headers da research R10, sem credenciais) e teste de preflight `OPTIONS` da origem permitida (200 com `Access-Control-Allow-Origin`) e de origem não permitida (sem o header) em `SecurityConfigTests.java`
- [x] T030 [P] Atualizar `infra/local/.env.example` com os nomes comentados `CLERK_ISSUER`, `CLERK_AUTHORIZED_PARTIES`, `CLERK_SECRET_KEY`, `CLERK_JWKS_URI`, `CLERK_API_URL`, `CLERK_CLOCK_SKEW`, sem valores
- [x] T031 [P] Atualizar `README.md` (seção Segurança): 401/403, revogação, variáveis novas, como obter o issuer, Tenant de teste no Mongo local, link para o quickstart
- [x] T032 Revisar logs e código em busca de token/segredo (`grep` por `sk_`, `whsec_`, `eyJ`) e rodar `.\mvnw.cmd clean verify` final
- [x] T033 Atualizar `research.md`/`spec.md` com o que mudou na implementação e com o resultado do teste manual (formato real de `o.rol`), se feito

## Dependências e ordem

- Phase 1 → Phase 2 → US2 → US1 → US3 → Polish.
- US1 depende do `SecurityConfig` da US2; US3 depende do converter da US1.
- Paralelos: T003/T004; T005/T006/T009/T010/T011; T012/T013; T018/T022; T025; T030/T031.

## Sugestão de commits (feitos pelo desenvolvedor, ADR_148)

1. `LLT-163: adiciona spec, plan e tasks da validacao do token da clerk`
2. `LLT-163: adiciona configuracao da clerk`
3. `LLT-163: adiciona tenant e repositorio mongo`
4. `LLT-163: valida token da clerk e responde 401 basico`
5. `LLT-163: resolve tenant e expoe contexto da requisicao`
6. `LLT-163: responde 403 e revoga sessoes na clerk`
7. `LLT-163: configura cors e documenta variaveis da clerk`

# Implementation Plan: Validação do session token da Clerk no Core

**Branch**: `feature/LLT-163-validacao-token-clerk` (criada pelo desenvolvedor) | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-validacao-token-clerk/spec.md`

## Summary

O Core passa a aceitar só requisições com session token da Clerk válido no header `Authorization: Bearer`. A validação usa o Spring Resource Server com o JWKS da instância (RS256) e validadores de `exp`/`nbf` (tolerância de 5 s), `iss`, `azp` e `sts = active`. Qualquer falha resulta em 401 com corpo básico. Com o token válido, um serviço de aplicação resolve o Tenant pela claim `o.id`. Se falta organização, Tenant ativo ou papel conhecido, o Core responde 403 e revoga as sessões do usuário na Clerk pela Backend API, usando `RestClient`. O restante do Core lê a identidade da requisição pela porta `CurrentRequestContext`. Detalhes e alternativas estão em [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (constituição); ver R13 sobre o JDK da máquina.

**Primary Dependencies**: Spring Boot 4.1.1: security, oauth2-resource-server (Nimbus JOSE), webmvc (`RestClient`), data-mongodb, validation. **Nenhuma dependência nova.**

**Storage**: MongoDB, coleção `tenant` (só leitura nesta feature).

**Testing**: JUnit 5, Spring Boot Test + MockMvc, Testcontainers (`mongo:8.0.34`, `redis:8.2.10`, `grafana/otel-lgtm:0.37.0`), `HttpServer` do JDK para JWKS e Backend API falsos.

**Target Platform**: serviço web Linux/contêiner; desenvolvimento em Windows.

**Project Type**: web-service (API REST).

**Performance Goals**: validação local, sem chamada à Clerk por requisição (JWKS em cache).

**Constraints**: 401/403 sem detalhar motivo; nenhum token ou segredo em log; revogação com timeouts de 2 s (conexão) e 3 s (leitura).

**Scale/Scope**: até 100 organizações e 20 membros cada (limites do plano gratuito, ADR_123).

## Constitution Check

| Princípio | Situação |
|---|---|
| I. Spec Kit | spec, clarify (seção Clarifications), plan e research nesta pasta; tasks em seguida; nenhum código antes. OK |
| II. IA 100% com revisão | decisões registradas em research.md; explicação ao fim da feature; tasks sem autoria. OK |
| III. Testes | um teste por cenário de aceitação, Testcontainers com tags fixas, `clean verify` local. **Pendente R13 (JDK)** |
| IV. Idiomas | código e logs em inglês; docs em português. OK |
| V. Segredos | `CLERK_SECRET_KEY` só por variável; testes com chaves geradas em memória e valores fictícios. OK |
| Stack | sem mudança de Java, Boot ou build. OK |
| Fluxo | branch e commits feitos pelo desenvolvedor (decisão de 2026-11-06); sugestão de commits no relatório final. OK |

Re-check pós-design: sem violações; Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/002-validacao-token-clerk/
├── spec.md
├── checklists/requirements.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/http-security.md
└── tasks.md            # próxima etapa
```

### Source Code (repository root)

Hexagonal leve por módulo de negócio: `domain` (records e regras puras), `application` (serviços e portas, sem Spring), `adapter/in` e `adapter/out` (Spring, Mongo, HTTP, Clerk). Os serviços de aplicação viram beans por `@Bean` em `config`, sem anotação do Spring dentro de `domain`/`application`.

```text
src/main/java/br/com/lhamalog/core/
├── config/
│   ├── SecurityConfig.java              # (alterado) cadeia stateless, resource server, CORS, handlers
│   ├── ClerkProperties.java             # @ConfigurationProperties("clerk"), validado
│   ├── IdentityConfig.java              # beans de AccessResolver e adaptadores
│   └── StartupLogger.java               # (inalterado)
├── tenant/
│   ├── domain/
│   │   ├── Tenant.java                  # record: tenantId, clerkOrgId, name, status
│   │   └── TenantStatus.java            # ACTIVE, SUSPENDED
│   ├── application/port/out/
│   │   └── TenantRepository.java        # Optional<Tenant> findByClerkOrgId(String)
│   └── adapter/out/mongo/
│       ├── TenantDocument.java          # @Document("tenant")
│       └── MongoTenantRepository.java   # implementa a porta + cria índice único
└── identity/
    ├── domain/
    │   ├── RequestContext.java          # clerkUserId, sessionId, clerkOrgId, role, tenantId
    │   ├── OrganizationRole.java        # ADMIN, MEMBER + normalização
    │   ├── ClerkSessionClaims.java      # claims já validadas, sem tipos Spring/Clerk
    │   └── AccessDecision.java          # Granted(RequestContext) | Denied(reason)
    ├── application/
    │   ├── AccessResolver.java          # regras de 403 (R5)
    │   └── port/
    │       ├── CurrentRequestContext.java   # porta de entrada para o restante do Core
    │       └── out/SessionRevocation.java   # revokeAllActiveSessions(clerkUserId)
    └── adapter/
        ├── in/security/
        │   ├── AuthorizedPartyValidator.java
        │   ├── ActiveSessionValidator.java
        │   ├── ClerkAuthenticationConverter.java
        │   ├── ClerkAuthentication.java
        │   ├── TenantAccessAuthorizationManager.java
        │   ├── BasicErrorAuthenticationEntryPoint.java   # 401
        │   ├── SessionRevokingAccessDeniedHandler.java   # 403 + revogação
        │   └── SecurityContextCurrentRequestContext.java # implementa a porta
        └── out/clerk/
            └── ClerkBackendSessionRevocation.java        # RestClient

src/test/java/br/com/lhamalog/core/
├── config/SecurityConfigTests.java       # (alterado) health público, 401 sem token
├── identity/
│   ├── domain/OrganizationRoleTests.java
│   ├── application/AccessResolverTests.java              # unidade, sem Spring
│   ├── adapter/in/security/ClerkTokenAuthenticationTests.java   # MockMvc: 200/401/403
│   └── adapter/out/clerk/ClerkBackendSessionRevocationTests.java
├── tenant/adapter/out/mongo/MongoTenantRepositoryTests.java
└── support/
    ├── TestJwks.java                     # par RSA, HttpServer JWKS, emissão de tokens
    ├── RecordingSessionRevocation.java   # fake da porta
    └── ProtectedTestController.java      # rota protegida só de teste
src/test/resources/config/application.properties   # issuer/origens fictícios para os testes
```

**Structure Decision**: módulos `tenant` e `identity` sob o pacote base, cada um com `domain`/`application`/`adapter`. O `identity.application` depende da porta `tenant.application.port.out.TenantRepository`, e nunca do adaptador. Não há código comum entre serviços (ADR_145).

## Fluxo da requisição

```text
Request ─▶ CorsFilter ─▶ BearerTokenAuthenticationFilter
             │ sem token / JWT inválido (assinatura, exp, nbf, iss, azp, sts)
             └──▶ BasicErrorAuthenticationEntryPoint ─▶ 401 {"error":"unauthorized"}
           JwtDecoder OK ─▶ ClerkAuthenticationConverter
             └─ AccessResolver(claims, TenantRepository) ─▶ AccessDecision
           AuthorizationFilter ─▶ TenantAccessAuthorizationManager
             │ Denied ─▶ SessionRevokingAccessDeniedHandler
             │            ├─ SessionRevocation.revokeAllActiveSessions(sub)  (erros só em log)
             │            └─ 403 {"error":"forbidden"}
             └ Granted ─▶ controller ─▶ CurrentRequestContext.current()
```

## Mudanças de configuração e documentação

- `application.properties`: `clerk.issuer=${CLERK_ISSUER}`, `clerk.jwks-uri=${CLERK_JWKS_URI:}`, `clerk.clock-skew=${CLERK_CLOCK_SKEW:5s}`, `clerk.api-url=${CLERK_API_URL:https://api.clerk.com}`.
- `application-local.properties`: `clerk.authorized-parties=${CLERK_AUTHORIZED_PARTIES:http://localhost:5173}`, `clerk.secret-key=${CLERK_SECRET_KEY:}`.
- `application-prod.properties`: `clerk.authorized-parties=${CLERK_AUTHORIZED_PARTIES}`, `clerk.secret-key=${CLERK_SECRET_KEY}`; comentário de obrigatórias atualizado.
- `infra/local/.env.example`: só os nomes comentados (`CLERK_ISSUER`, `CLERK_AUTHORIZED_PARTIES`, `CLERK_SECRET_KEY`, `CLERK_JWKS_URI`, `CLERK_API_URL`, `CLERK_CLOCK_SKEW`), sem valores.
- `README.md`: seção "Segurança" reescrita (401/403, variáveis, como obter o issuer, inserir Tenant de teste no Mongo local).

## Fatias de implementação (cada uma com testes e `clean verify`)

1. Configuração (`ClerkProperties` + properties de teste) e decoder com validadores; 401 uniforme; `SecurityConfigTests` atualizado; remoção do `httpBasic`.
2. Domínio `tenant` + adaptador Mongo com índice.
3. `identity` domínio/aplicação (`AccessResolver`, `OrganizationRole`) com testes de unidade.
4. Converter, AuthorizationManager e handler de 403 com fake de revogação; porta `CurrentRequestContext`; testes MockMvc de 200/403.
5. Adaptador `ClerkBackendSessionRevocation` com teste contra `HttpServer`.
6. CORS, documentação (README, `.env.example`, quickstart).

## Riscos

- Formato real de `o.rol` e `sts` só confirmado no teste manual (quickstart).
- Conflito de merge com a `Development` (R14).
- JDK 25 ausente na máquina (R13): bloqueia o `verify` até a decisão.

## Complexity Tracking

Sem violações.

# Implementation Plan: Webhooks da Clerk

**Branch**: `feature/LLT-164-webhooks-clerk` (criada pelo desenvolvedor) | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

## Summary

O endpoint público `POST /webhooks/clerk` recebe o corpo bruto e verifica a assinatura Svix com o JDK (HMAC-SHA256, tolerância de ±5 min). Depois converte o JSON em eventos de domínio e os aplica à coleção `member` (Membro, ADR_122) com escritas condicionais por versão, protegidas pela deduplicação por `svix-id` (coleção com TTL). O webhook não cria Tenant (ADR_121). Decisões em [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (Temurin 25.0.5)

**Primary Dependencies**: Spring Boot 4.1.1 (webmvc, data-mongodb, Jackson 3). **Nenhuma dependência nova.**

**Storage**: MongoDB: `member`, `clerk_webhook_event` (TTL), `deleted_clerk_user`; leitura de `tenant` (LLT-163).

**Testing**: JUnit 5, MockMvc, Testcontainers (tags fixas), assinatura Svix gerada no teste.

**Project Type**: web-service.

**Constraints**: corpo bruto para a assinatura; sem transações (Mongo standalone); logs sem corpo, e-mail ou segredo.

## Constitution Check

| Princípio | Situação |
|---|---|
| I. Spec Kit | spec, clarify, plan, research e tasks antes do código. OK |
| II. Revisão | decisões em research.md; relatório final. OK |
| III. Testes | um teste por cenário; `clean verify` local. OK |
| IV. Idiomas | código em inglês; docs em português. OK |
| V. Segredos | segredo só por variável; teste gera segredo fictício em memória. OK |
| Stack | inalterada. OK |

## Project Structure

```text
src/main/java/br/com/lhamalog/core/
├── config/
│   ├── ClerkWebhookProperties.java         # clerk.webhook.*
│   └── WebhookConfig.java                  # beans dos serviços de aplicação
├── member/
│   ├── domain/
│   │   ├── Member.java                     # record + regras de aplicação de eventos
│   │   ├── MemberRole.java                 # DONO, ADMIN, MEMBRO + mapeamento ADR_123
│   │   ├── MembershipSnapshot.java         # dados de um vínculo vindos da Clerk
│   │   └── UserProfile.java                # nome/e-mail vindos da Clerk
│   ├── application/
│   │   ├── MemberSynchronizer.java         # casos de uso de sincronização
│   │   └── port/out/
│   │       ├── MemberRepository.java
│   │       └── DeletedUserRepository.java
│   └── adapter/out/mongo/
│       ├── MemberDocument.java / MongoMemberRepository.java
│       └── DeletedUserDocument.java / MongoDeletedUserRepository.java
└── webhook/
    ├── domain/ClerkWebhookEvent.java       # eventos já interpretados (sealed)
    ├── application/
    │   ├── ClerkWebhookProcessor.java      # dedup + despacho
    │   └── port/out/ProcessedWebhookRepository.java
    └── adapter/
        ├── in/web/
        │   ├── ClerkWebhookController.java
        │   ├── SvixSignatureVerifier.java
        │   └── ClerkWebhookEventParser.java
        └── out/mongo/
            ├── ProcessedWebhookDocument.java
            └── MongoProcessedWebhookRepository.java
```

**Structure Decision**: módulos `member` (Membro e suas regras) e `webhook` (entrada Svix e orquestração), no mesmo padrão `domain`/`application`/`adapter` da LLT-163. `webhook.application` depende das portas de `member` e de `tenant`.

## Fluxo

```text
POST /webhooks/clerk (byte[] bruto + headers svix-*)
  └─ SvixSignatureVerifier ── inválido ─▶ 400
  └─ ClerkWebhookEventParser ── JSON inválido ─▶ 400
  └─ ClerkWebhookProcessor
       ├─ svix-id já registrado ─▶ 200 (duplicado)
       ├─ MemberSynchronizer (Tenant? tombstone? versão? DONO?) ─ exceção ─▶ 500 (sem registrar)
       └─ registra svix-id (TTL 7 d) ─▶ 200
```

## Arquivos da LLT-163 tocados

- `application*.properties`, `infra/local/.env.example`, `README.md`: linhas/seções novas.
- `src/test/.../support/ClerkTestConfiguration.java`: registro do segredo de teste do webhook.

## Fatias

1. Propriedades e verificador de assinatura (unidade).
2. Domínio `member` (papéis, versões, regras) com testes de unidade.
3. Adaptadores Mongo (`member`, `deleted_clerk_user`, `clerk_webhook_event` com TTL).
4. Parser, processador e controller com testes MockMvc de todas as histórias.
5. Documentação (README com túnel, `.env.example`, quickstart).

## Complexity Tracking

Sem violações.

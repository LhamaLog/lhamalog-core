# Implementation Plan: Aviso em tempo real de ocorrências novas

**Branch**: `LLT-137-atualizacao-em-tempo-real` | **Date**: 25/11/2026 | **Spec**: [spec.md](spec.md)

A branch sai da `LLT-136-ciclo-de-vida` em `cf79a2f` (PR #21). PR empilhado: ordem #19 → #20 → #21 → este, com merge commit.

## Summary

Um WebSocket `/ws/occurrences` autenticado por mensagem (token da Clerk validado como nas rotas `/api`), que repassa avisos `CREATED`/`REOPENED` às conexões da empresa que veem o produto. Os avisos nascem no `AnalysisSummaryDeliveryService` depois do recibo (spec 013), a partir do diário da entrega, e passam entre instâncias pelo Redis pub/sub. Decisões em [research.md](research.md).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1 — `spring-boot-starter-websocket` e `spring-boot-starter-data-redis`, já no `pom.xml`. Nenhuma dependência nova.

**Storage**: nenhum; Redis pub/sub (sem persistência).

**Testing**: JUnit 5; `@SpringBootTest(webEnvironment = RANDOM_PORT)` com `StandardWebSocketClient` para o WebSocket; Redis e Mongo do Testcontainers; `TestJwks` para os tokens.

**Performance Goals**: aviso em até 3 s (SC-001); publicação fora do caminho da resposta ao Finder (SC-004).

**Constraints**: token nunca na URL nem em log; aviso sem conteúdo de ocorrência; constituição 2.1.0; a mudança na `SecurityConfig` (LLT-163) se limita a liberar o aperto de mão de `/ws/occurrences`.

**Scale/Scope**: pacote `occurrence/stream` com cerca de 8 classes, uma linha na `SecurityConfig`, um ponto de publicação no serviço de entrega, propriedades novas e três classes de teste.

## Constitution Check

- **I/II**: spec, clarify, plano, research, contrato, quickstart e tasks antes do código; revisão do Felipe.
- **III**: `OccurrenceStreamIntegrationTests`, `OccurrenceChangeDetectorTest`, `OccurrenceConnectionsTest`; `.\mvnw.cmd verify`.
- **IV**: código em inglês, documentação em português.
- **V**: o token só trafega na mensagem e nunca é registrado; nada de segredo novo.
- **VI**: eventos `occurrence.stream.connected`, `.authenticated`, `.refused` (WARN), `.closed`, `.publish_failed` (WARN), com `tenantId`, `userHash` e código; sem token, título ou assinatura.

Sem violações.

## Project Structure

```text
src/main/java/br/com/lhamalog/core/
├── config/
│   ├── SecurityConfig.java                       # + permitAll do aperto de mão /ws/occurrences
│   └── OccurrenceStreamConfiguration.java        # registro do handler, origens, listener Redis, executor
└── occurrence/
    ├── application/
    │   ├── AnalysisSummaryDeliveryService.java   # + publica os avisos depois do recibo
    │   ├── OccurrenceChangeDetector.java         # diário → CREATED/REOPENED (função pura)
    │   ├── OccurrenceEvent.java                  # aviso (tenant, produto, id, tipo, mudança, data)
    │   └── OccurrenceEventPublisher.java         # porta
    └── stream/
        ├── RedisOccurrenceEventPublisher.java    # publica no canal (assíncrono, WARN em falha)
        ├── RedisOccurrenceEventListener.java     # recebe do canal e repassa às conexões locais
        ├── OccurrenceConnections.java            # conexões autenticadas, produtos visíveis, expiração
        ├── OccurrenceStreamHandler.java          # TextWebSocketHandler: auth, renovação, fechamento
        └── StreamAuthenticator.java              # token → RequestContext → produtos visíveis (mesma regra das /api)

src/test/java/br/com/lhamalog/core/occurrence/
├── OccurrenceStreamIntegrationTests.java
├── OccurrenceChangeDetectorTest.java
└── OccurrenceConnectionsTest.java
```

**Structure Decision**: a parte de transporte fica num subpacote `stream`, separada da regra de quando avisar (`OccurrenceChangeDetector`, na aplicação), para a regra ser testada sem WebSocket nem Redis.

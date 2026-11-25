# Research: Aviso em tempo real de ocorrências novas

**Feature**: `015-tempo-real-ocorrencias` | **Data**: 25/11/2026

Fontes: cards LLT-137 e LLT-208; specs 012, 013 e 014; `SecurityConfig`, `AccessResolver`, `TenantAccessAuthorizationManager` e `ClerkAuthenticationConverter` (LLT-163/168); `spring-boot-starter-websocket` e `spring-boot-starter-data-redis`, já no `pom.xml`.

## R1. Transporte

- **Decisão**: WebSocket simples (`TextWebSocketHandler` do Spring) em `/ws/occurrences`, mensagens JSON. Sem STOMP nem SockJS.
- **Motivo**: clarify; o fluxo é só servidor → tela, com uma mensagem de autenticação; STOMP exigiria biblioteca nova no web e broker no Core.

## R2. Autenticação

- **Decisão**: o aperto de mão é público (`/ws/occurrences` com `permitAll` na chain da Clerk), limitado às origens de `CLERK_AUTHORIZED_PARTIES`. A conexão fica "pendente" até a mensagem `{"type":"auth","token":"…"}`; o token é validado pelo mesmo `JwtDecoder` das rotas `/api` e convertido em `RequestContext` pelo `AccessResolver`, com a mesma checagem de membro desativado do `TenantAccessAuthorizationManager`. Sem `auth` válido em 10 s → close 4401. Token válido sem acesso (sem empresa ativa, sem Tenant, desativado) → 4403. Resposta de sucesso `{"type":"ready","expiresAt":"…"}`.
- **Renovação**: `auth` de novo na mesma conexão; `sub` e empresa precisam ser os mesmos (senão 4403). Uma tarefa a cada 10 s fecha com 4401 as conexões com `exp + 30 s` vencido.
- **Motivo**: clarify (token fora da URL); o navegador não manda `Authorization` no WebSocket.
- **Alternativas**: token na URL (vaza em logs), cookie de sessão (o web não usa cookie da Clerk para a API).

## R3. Quais mudanças viram aviso

- **Decisão**: no `AnalysisSummaryDeliveryService`, depois do `confirm` (201), para cada item do diário: `before == null` → `CREATED`; `before` com status diferente de ABERTA ou com `archivedAt`, e `after` ABERTA → `REOPENED`. Entrega repetida (200), recusada (409/422/500) ou desfeita não publica.
- **Motivo**: clarify ("novas e reabertas"); o diário já tem o antes e o depois.

## R4. Entre instâncias

- **Decisão**: Redis pub/sub no canal `lhamalog:occurrence-events` (configurável), mensagem JSON `{tenantId, productId, occurrenceId, type, change, at}`. Cada instância assina o canal (`RedisMessageListenerContainer`) e repassa às conexões locais da mesma empresa que veem o produto. A publicação é assíncrona (executor próprio, fila limitada) e falha só registra WARN.
- **Motivo**: FR-006; o Redis já existe no Core; pub/sub não guarda mensagem, o que é aceitável porque a tela sincroniza ao reconectar.
- **Alternativas**: Redis Streams (durável, mas exige grupo de consumo e limpeza por instância), só memória (não funciona com mais de uma instância).

## R5. Produtos visíveis

- **Decisão**: no `ready` e depois a cada 60 s, a conexão guarda os ids dos produtos que a pessoa vê (`ProductService.list` ativos e arquivados, como o detalhe da spec 012). O aviso só vai para a conexão que tem o produto na lista.
- **Motivo**: evita consultar o banco a cada aviso; a perda de acesso vale em até 1 minuto (caso de borda da spec).

## R6. Mensagens

| Direção | Mensagem |
|---|---|
| tela → Core | `{"type":"auth","token":"<jwt>"}` |
| Core → tela | `{"type":"ready","expiresAt":"2026-11-25T18:00:00Z"}` |
| Core → tela | `{"type":"occurrence","change":"CREATED","occurrenceId":"…","productId":"…","occurrenceType":"ERROR","at":"…"}` |
| Core fecha | 4401 (sem autenticação válida ou token vencido), 4403 (acesso negado ou troca de pessoa/empresa), 1001 (Core desligando) |

## R7. Testes

- **Decisão**: `OccurrenceStreamIntegrationTests` com o cliente `StandardWebSocketClient` contra o servidor do teste (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, sem MockMvc), Redis do Testcontainers e tokens do `TestJwks`; testes unitários do `OccurrenceChangeDetector` (diário → avisos) e do registro de conexões.
- **Motivo**: o MockMvc não fala WebSocket; o Redis do teste já sobe pelo `TestcontainersConfiguration`.

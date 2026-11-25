# Contrato: WebSocket de ocorrências

`wss://<core>/ws/occurrences` (local: `ws://localhost:8080/ws/occurrences`). Origem precisa estar em `CLERK_AUTHORIZED_PARTIES`.

## Sequência

1. A tela abre a conexão e, em até 10 s, manda:
   ```json
   { "type": "auth", "token": "<token de sessão da Clerk>" }
   ```
2. O Core responde:
   ```json
   { "type": "ready", "expiresAt": "2026-11-25T18:00:00Z" }
   ```
3. A tela renova antes de `expiresAt` mandando `auth` de novo (mesma pessoa e empresa); o Core responde outro `ready`.
4. O Core manda os avisos:
   ```json
   { "type": "occurrence", "change": "CREATED", "occurrenceId": "…", "productId": "…", "occurrenceType": "ERROR", "at": "2026-11-25T17:59:01Z" }
   ```
   `change`: `CREATED` (ocorrência nova) ou `REOPENED` (voltou a ABERTA por um achado novo, inclusive desarquivada).

## Fechamentos

| Código | Quando |
|---|---|
| 4401 | `auth` não chegou em 10 s; primeira mensagem não é `auth`; token inválido; token vencido há mais de 30 s sem renovação |
| 4403 | token válido sem acesso (sem empresa ativa, sem Tenant, membro desativado); renovação com outra pessoa ou empresa |
| 1001 | Core desligando |

Nenhum fechamento leva o motivo detalhado. Depois do `ready`, mensagens que não são `auth` são ignoradas.

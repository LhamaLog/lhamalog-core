# Data Model: Aviso em tempo real de ocorrências novas

**Feature**: `015-tempo-real-ocorrencias` | **Data**: 25/11/2026

Nada é gravado. Estruturas em memória e no canal do Redis:

## OccurrenceEvent (canal `lhamalog:occurrence-events`)

| Campo | Tipo | Descrição |
|---|---|---|
| `tenantId` | string | empresa (usado só para rotear; não vai para a tela) |
| `productId` | string | produto da ocorrência |
| `occurrenceId` | string | `_id` da ocorrência |
| `occurrenceType` | string | ERROR, WARNING ou INFO |
| `change` | string | `CREATED` ou `REOPENED` |
| `at` | data | quando a entrega foi confirmada |

## Conexão (memória de cada instância)

| Campo | Descrição |
|---|---|
| sessão WebSocket | a conexão |
| `tenantId`, `userId` | da autenticação; fixos na conexão |
| produtos visíveis | ids, atualizados a cada 60 s |
| `expiresAt` | `exp` do último token aceito |
| estado | `PENDING` (sem `auth`) ou `READY` |

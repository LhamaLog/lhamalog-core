# Data Model: Ciclo de vida da ocorrência

**Feature**: `014-ciclo-de-vida-ocorrencias` | **Data**: 25/11/2026

## Ocorrência (coleção `Ocorrencia`)

Campos novos, opcionais (documento sem eles = ativo):

| Campo | Tipo | Descrição |
|---|---|---|
| `archivedAt` | date | Quando foi arquivada; ausente = ativa |
| `archivedBy` | objeto `{type, id}` | Quem arquivou (`type = USER`, `id` = id do usuário) |

Campos que as ações mudam:

| Ação | `status` | `statusHistory` | `archivedAt`/`archivedBy` | `revision` |
|---|---|---|---|---|
| Alterar status (diferente) | novo | + `{from, to, changedAt, changedBy: {USER, id}}` | igual | nova |
| Alterar status (igual) | igual | igual | igual | igual |
| Arquivar (ativa) | igual | igual | definidos | nova |
| Desarquivar (arquivada) | igual | igual | removidos | nova |
| Achado do Finder em arquivada (spec 013) | `ABERTA` | + `{anterior → ABERTA, SYSTEM}` se o status mudou | removidos | nova |

## Respostas

- `ListItem` + `archived` (boolean).
- `Detail` + `revision` (string, `sem-revisao` quando o documento não tem), `archived` (boolean), `archivedAt` (data ou `null`), `archivedBy` (`{type, id}` ou `null`).

## Permissão (LLT-120)

| Permissão | Papéis | Nota |
|---|---|---|
| `OCCURRENCES_MANAGE` | OWNER, ADMIN, MEMBER | MEMBER só nos produtos liberados |

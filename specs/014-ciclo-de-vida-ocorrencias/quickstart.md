# Quickstart: Ciclo de vida da ocorrência

Roteiro de validação da feature 014. Use o ambiente local do README, com a carga de exemplo (`OCCURRENCE_SAMPLE_DATA=true`) e um token de sessão da empresa de teste em `$token`.

## Roteiro

| # | Passo | Esperado |
|---|---|---|
| C1 | `GET /api/occurrences/{id}` de uma ocorrência ABERTA | 200 com `revision`, `archived: false`, `archivedAt: null` |
| C2 | `PUT /api/occurrences/{id}/status` com `EM_ANALISE` e a revisão de C1 | 200; status EM_ANALISE, revisão nova, histórico com `ABERTA → EM_ANALISE` por USER e o id do usuário |
| C3 | Repetir C2 com a revisão antiga | 409 `occurrence_changed` |
| C4 | `PUT …/status` com `RESOLVIDA` | 400 `fieldErrors.status` |
| C5 | `GET /api/occurrences?status=EM_ANALISE` | a ocorrência de C2 aparece |
| C6 | `POST /api/occurrences/{id}/archive` com a revisão atual | 200 com `archived: true`, `archivedAt` e `archivedBy` |
| C7 | `GET /api/occurrences` e `GET /api/occurrences?archived=true` | some da primeira (total menos 1) e aparece na segunda |
| C8 | `POST …/unarchive` | volta para a lista padrão com o mesmo status |
| C9 | Arquivar de novo; entregar pelo Finder (spec 013, M9) um achado dela com a revisão atual | 201; ela volta para a lista padrão como ABERTA, com `EM_ANALISE → ABERTA` por SYSTEM |
| C10 | Membro sem acesso ao produto: `PUT …/status` | 404 |
| C11 | `GET /api/company/access` | matriz com `OCCURRENCES_MANAGE` para os três papéis |

## Testes automatizados

```powershell
.\mvnw.cmd verify
```

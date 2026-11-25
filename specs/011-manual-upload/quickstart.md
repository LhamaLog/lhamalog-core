# Quickstart: LLT-292

Worktree do Core já criado (20/11/2026, America/Sao_Paulo):

- `E:\Facul\Escola_TI\repositorios\lhamalog-core-llt292`
- branch `feature/LLT-292-manual-upload` (sem upstream)
- base `23a69d958511f87f4129785aff34fcce47933011` (`origin/Development`)


O lado do Core já está implementado neste worktree (sem commit). O endpoint responde 503 enquanto o Collector desta feature não existir. Depois dos commits da LLT-290, criar o worktree do Collector e completar T016–T032 / T033–T034 Coll.

O checkout principal do Core permanece em `LLT-145-consulta-metricas-dashboard` e não deve ser usado.

Worktree do Collector **ainda não criado**. Só depois dos commits da LLT-290 em `feature/LLT-290-file-hash-dedup`, a partir do repositório, sem tocar em `lhamalog-collector-llt290` nem no checkout principal:

```powershell
cd E:\Facul\Escola_TI\repositorios\lhamalog-collector
git worktree add -b feature/LLT-292-manual-upload ..\lhamalog-collector-llt292 feature/LLT-290-file-hash-dedup
cd ..\lhamalog-collector-llt292
git branch --unset-upstream
```

Verificar depois: `mvnw -B test` em cada worktree, com Mongo/Redis/OpenObserve já usados nas suítes. Não commitar sem OK do Michel.

Smoke (quando implementado):

1. JWT Clerk válido, produto visível, `POST /api/manual/upload` com um `.log` pequeno → 202.
2. Repetir o mesmo arquivo → 409.
3. Sem token / produto arquivado → 401 / 403.
4. POST no Collector sem service token → 401.

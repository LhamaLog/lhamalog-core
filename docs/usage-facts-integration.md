# Integração de fatos de uso

O Collector chama `POST /internal/usage-facts` e consulta `GET /internal/usage-facts`
e `GET /internal/usage-facts/current-cycle`. Todas as rotas exigem exclusivamente
`X-LhamaLog-Service-Token`; o valor vem de `LHAMALOG_CORE_USAGE_TOKEN` e não deve ser
registrado, versionado ou enviado por `Authorization`.

O Core mantém o fato pelo `usageFactId` durante sete dias. Uma repetição idêntica recebe
`200` sem nova agregação; corpo diferente com o mesmo identificador recebe `409`. Consultas
por intervalo usam `[from,to)`. O ciclo usa UTC e `firstPaymentDate` obrigatório: semanal
para planos semanais e mensal para planos mensais ou semestrais.

## Validação

Em 2026-11-09, `.\mvnw.cmd verify` foi executado no worktree Core após as tarefas T032–T041.

## Pendências para a próxima implementação

O detalhamento normativo destas pendências está na spec do Collector (`lhamalog-collector/specs/005-tenant-product-volume/spec.md`, seção "Pendências para a próxima implementação"):

- Ensaio E2E T046 com Collector e Core juntos ainda não executado.
- Durabilidade condicional do Redis (AOF `everysec`, RPO de ~1 s após HTTP 200; `appendfsync always` não decidido; Lua sem rollback/`AMBIGUOUS`; Redis Cluster não suportado).
- Fonte externa de `firstPaymentDate` indefinida.
- Rotação operacional de `LHAMALOG_CORE_USAGE_TOKEN` / `LHAMALOG_COLLECTOR_CORE_TOKEN`.
- Os 4 testes `CompanyProductIntegrationTests` deste repositório foram pulados por falta de `TEST_MONGODB_URI`.

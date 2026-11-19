# Validação — Consulta de métricas para dashboards

Core apenas. Detalhes: [data-model.md](data-model.md), [contracts/dashboard-metrics.md](contracts/dashboard-metrics.md), [research.md](research.md).

## Requisitos

Java 25, Maven Wrapper, Docker ligado (Testcontainers). Tokens Clerk de teste via `TestTenants` / `@CoreIntegrationTest`. OpenObserve de integração: stub ou compose de observability conforme o adaptador (credenciais só no `.env` local; ver `.env.example`).

## Configuração relevante

| Propriedade / env | Padrão | Uso |
| --- | --- | --- |
| `lhamalog.dashboard.aggregation.cron` | `0 0 * * * *` | Frequência do job SSID_011 (a cada 1 h) |
| `OPENOBSERVE_AUTH` | (vazio) | Auth Basic da leitura; segredo nunca versionado |
| `OPENOBSERVE_LOGS_STREAM` / `lhamalog.openobserve.logs-stream-prefix` | `logs` | Prefixo de stream (mesmo do Collector; LLT-185) |
| `OPENOBSERVE_TENANT_FIELD` etc. | `tenantid` / `productid` / `severitynumber` / `severitytext` / `servicename` | Campos SQL (OO minúsculas) |

Documentar o cron, o prefixo de stream e os campos no `.env.example` (comentário) e no README na seção de configuração do Core, no padrão das demais propriedades.

### Conferência manual SQL (exemplo LLT-187)

Tenant `llt187-dash-tenant`, produto `llt187-dash-product`, de `2026-11-16T00:00:00Z` a `2026-11-18T00:00:00Z` — o adapter gera um único SQL de contagem por hora × severidade × serviço (ranking de serviços deriva dos mesmos buckets):

```sql
SELECT histogram(_timestamp, '1 hour') AS hour_start, severitynumber AS severitynumber, severitytext AS severitytext, servicename AS service_name, count(*) AS cnt FROM "logs_llt187_dash_tenant_94439d83" WHERE tenantid = 'llt187-dash-tenant' AND productid = 'llt187-dash-product' GROUP BY hour_start, severitynumber, severitytext, service_name ORDER BY hour_start
```

`start_time` / `end_time` do body `_search`: microssegundos dos instantes acima. Teste que imprime: `OpenObserveLogQueryAdapterParseTest.printsConferenceSqlForLlt187Sample`.

## Testes

Portão local:

```powershell
.\mvnw.cmd -B test
```

Suíte completa antes do merge:

```powershell
.\mvnw.cmd -B clean verify
```

## Cenários que a suíte deve comprovar

1. **Produto (US1)**: DONO/ADMIN com produto e dados → 200 com totais, série, ranking ≤ 5, `retentionDays`, `volumeIngestaoBytes` (bytes); eco de `X-Correlation-Id`.
2. **Filtro severities**: `ERROR,WARNING` reflete só essas; severidade desconhecida → 400 `invalid_filter`.
3. **Sem dados**: 200 com zeros / série vazia / ranking vazio.
4. **Produto outro tenant / inexistente / MEMBRO sem Acesso**: **404**, sem vazamento.
5. **Período inválido** (`from` ≥ `to` ou acima de `min(30, retentionDays)`): 400.
6. **`occurrences` ausente** em todos os pontos; sem selo de impacto.
7. **Empresa (US2)**: consolidado só com produtos autorizados; MEMBRO sem acesso → 200 vazio; `productIds` inválido/outro tenant → 400/404.
8. **Job (US3)**: após execução sobre intervalo conhecido, agregados batem com logs (`_search` SQL); consulta híbrida mescla e expõe `dadosConsolidadosAte`; cron padrão `0 0 * * * *`; override da propriedade altera o agendamento.
9. **Falha OpenObserve** no intervalo aberto: 5xx + log com correlation id; agregados intactos.
10. **Authz (US4)**: sem token → 401; MEMBRO sem acesso ao produto → **404**; DONO = ADMIN na visão de produtos; planos só mudam `retentionDays`/teto.

## Interpretação SSID_011

**Confirmado (Michel, 18/11 22:02)**: job lê **logs** agora; passa a ler Ocorrências depois da LLT-104. Consulta híbrida com `dadosConsolidadosAte`.

## Resultados executados

- **Data**: 18/11/2026 (T010a — alinhamento Collector/OO)
- **Comando**: `.\mvnw.cmd -B clean verify`
- **Resultado**: BUILD SUCCESS
- **Dashboard**: unitários (`LogSeverityTest`, `OpenObserveStreamNamesTest`, `OpenObservePropertiesTest`, `OpenObserveLogQueryAdapterParseTest`, …) + integração `DashboardMetricsControllerIntegrationTest` (OpenObserve via `@MockitoBean`, sem instância real)
- **T029**: portão Felipe ainda pendente (revisão Clerk/papéis)

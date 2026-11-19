# Pesquisa e decisões técnicas — Consulta de métricas (dashboard)

## Fonte dos dados (MVP)

- **Decision**: Contagens de logs → OpenObserve via Core (ADR_168). Volume usado (`volumeUsedBytes`) → soma de `acceptedBytes` dos `UsageFact` com `domain=LOGS` no intervalo. Traces → soma de `acceptedRecordCount` dos `UsageFact` com domínio/tipo **traces** no intervalo (não bytes de traces). `volumeIngestaoBytes` → cota do plano como **inteiro em bytes** (`PlanSubscription.allowanceGigabytes` × 2^30 quando a origem for em GiB; `unlimitedUsage` → valor sentinela ou omissão documentada no contrato interno alinhada ao Web). Conversão para GiB (÷ 2^30) **só na tela do Web** — API nunca usa base 10^9. `retentionDays` → `TenantRetentionResolver` / `PlanTier` (ADR_131). Web nunca acessa OpenObserve (ADR_141).
- **Rationale**: Clarification 2026-11-18 (U2/U3) e FR-005/006; fatos de uso e plano já existem (`UsageFactsController`, `PlanSubscription`).
- **Alternatives considered**: Web consultando OpenObserve direto (proibido ADR_141); derivar volume só do OpenObserve (não cobre traces aceitos / cota); trafegar GiB na API (rejeitado — Web converte na UI).

## Cliente OpenObserve (U1)

- **Decision**: Adaptador `OpenObserveLogQueryAdapter` com `RestClient` (mesmo padrão de `ClerkBackendSessionRevocation`), org `lhamalog`, stream por tenant. Leitura via **API de search SQL**: `POST /api/{org}/_search` com SQL de contagem agregada por severidade / serviço / intervalo (e filtros de `productId` quando aplicável) sobre o stream do tenant. Credenciais/URL só por propriedades e env (`OPENOBSERVE_*` / `OPENOBSERVE_AUTH`). Sem SDK Maven novo.
- **Rationale**: Constituições V e stack; OTLP já aponta para OpenObserve no perfil local, mas consulta de logs ainda não existe no Core — precisa de cliente HTTP de leitura. Search SQL evita varrer documentos um a um e alinha job + intervalo aberto da consulta híbrida.
- **Alternatives considered**: SDK oficial OpenObserve (dependência nova); Mongo como fonte de logs (contradiz ADR_168); API de listagem sem agregação SQL (custo alto em períodos longos).

## Nomes reais OpenObserve (Collector + OO v1.1.2 — confirmado Michel 18/11)

- **Decision**: Prefixo `lhamalog.openobserve.logs-stream-prefix` = env `OPENOBSERVE_LOGS_STREAM` (padrão `logs`), mesma regra `TenantStreamNames` / `OpenObserveStreamNames` (LLT-185). OO normaliza campos para minúsculas: `tenantid`, `productid`, `severitynumber`, `severitytext`, `servicename`, `_timestamp` (µs). SQL filtra `tenantid` + `productid` no stream do tenant; `start_time`/`end_time` do `_search` em microssegundos. Ex.: `llt187-dash-tenant` → stream `logs_llt187_dash_tenant_94439d83`.
- **Rationale**: Consulta real ao OO local e código do Collector (`OpenObserveLogsSink`); padrões antigos (`product_id`/`level`/`service_name`/template `{tenantId}`) não batem com o que é gravado.
- **Alternatives considered**: Manter template `{tenantId}` (só se produzir exatamente os nomes LLT-185); CASE de severidade no SQL (mapeamento no Core é mais simples de testar).

## Severidade (ADR_170)

- **Decision**: Preferir `severitynumber` OTel: 17–24 → `ERROR` (inclui FATAL), 13–16 → `WARNING`, 1–12 → `INFO`; 0/ausente → `severitytext` se houver, senão `INFO`. Texto livre (`severitytext` / level) permanece como fallback (`error`/`warn`/`info`…). Filtro de query só aceita os três enums; desconhecido → 400 `invalid_filter`.
- **Rationale**: FR-007; Collector grava número sempre (1–24); contrato Web consome só enums normalizados.
- **Alternatives considered**: Devolver texto bruto; normalizar no Web (rejeitado — contrato exige Core); só texto (ignora o campo canônico do Collector).

## Job SSID_011 e consulta híbrida (confirmado Michel 18/11)

- **Decision**: Job lê **logs** no OpenObserve (via `POST /api/{org}/_search` + SQL de contagem) e grava agregados por `tenantId`, `productId`, `severity` (ERROR|WARNING|INFO), `serviceName`, `hourStart`. Consulta usa agregados até `dadosConsolidadosAte` e conta na hora só o intervalo aberto. Ocorrências / campo `occurrences` **ausentes** até LLT-104; **depois da LLT-104** o job passa a ler Ocorrências. Confirmação operacional Michel (18/11 22:02 / A1-C1) — marcas `[A CONFIRMAR]` removidas.
- **Rationale**: Clarifications e FR-008/010/015; ADR_166 no MVP.
- **Alternatives considered**: Só contagem na hora (sem agregados); job a partir de Ocorrências já neste MVP (bloqueado por LLT-104).

## Frequência do job (FR-009 / I1)

- **Decision**: Propriedade `lhamalog.dashboard.aggregation.cron` com padrão `0 0 * * * *` (a cada hora, no minuto 0 — cron de 6 campos do Spring). Documentar em `application.properties`, `.env.example` (comentário do nome/override) e README se a seção de config do Core listar propriedades semelhantes. Alternativa de intervalo (`fixedDelay`) rejeitada como primária.
- **Rationale**: Mesmo estilo de scheduling já habilitado (`@EnableScheduling`); reapers usam `fixedDelayString`, mas FR-009/I1 fixam cron explicitamente para agregação horária alinhada a buckets.
- **Alternatives considered**: `fixedDelayString=${...:1h}` como `QuotaReservationReaper` (válido, mas buckets horários fecham melhor com cron no minuto 0).

## Autorização e papéis

- **Decision**: `CompanySessionResolver.current()`; `administrator() == true` (Clerk `org:admin`) = DONO/ADMIN → todos os produtos do tenant via `ProductService.list`/`get`; MEMBRO → só `AcessoProduto`. MEMBRO sem produtos no endpoint da empresa → 200 com totais/listas vazios. Produto inacessível (outra empresa, inexistente ou MEMBRO sem AcessoProduto) → **404** (mesmo padrão de `ProductService.get`) — **não** 403 de negócio. Cross-tenant → 404 sem vazamento (LLT-158). 403 permanece para falhas de segurança (ex.: sem Tenant). Portão: revisão do Felipe antes de fechar tasks de Clerk/papéis.
- **Rationale**: FR-011/012/019; decisão Michel 18/11 (produto inacessível = 404).
- **Alternatives considered**: Diferenciar DONO e ADMIN (rejeitado na clarification); 403 genérico para MEMBRO sem produtos (rejeitado); 403 de negócio para produto sem acesso (rejeitado — alinhamento a `ProductService.get`).

## Bucket da série (FR-014)

- **Decision**: `hour` se `(to - from) ≤ 48h`; senão `day`. Agregados horários são agrupados em dias quando o bucket for `day`.
- **Rationale**: Contrato Web / FR-014.
- **Alternatives considered**: Sempre hora; bucket escolhido pelo cliente (fora do contrato).

## Erros e correlation id

- **Decision**: Reutilizar `CorrelationIdFilter` (eco de `X-Correlation-Id`). 400 filtro → `{code,message}` (`invalid_filter`). 401/403 segurança já da camada identity. Produto inacessível → 404 (não `ApiStatusException` 403 de negócio para esse caso). Falha OpenObserve → 5xx recuperável + log `event=dashboard.openobserve.failed` (ou equivalente), sem corromper agregados.
- **Rationale**: FR-017/018; constituição VI; decisão 404 alinhada a `ProductService.get`.
- **Alternatives considered**: Novo filtro de correlation id (desnecessário); 403 negócio com `code` para sem acesso ao produto (rejeitado).

## Planos e teto de período

- **Decision**: Dashboard idêntico em todos os planos; validar `to - from ≤ min(30, retentionDays)` dias; acima → 400. FREE 7 / Starter 15 / Pro 30 / Business|Enterprise 90 via `PlanTier` + overrides `lhamalog.plans.retention-days`.
- **Rationale**: FR-013; ADR_131; `TenantRetentionResolver` já resolve retenção do tenant.
- **Alternatives considered**: Campos extras por plano; teto fixo 30 sem `retentionDays`.

## Módulo e testes

- **Decision**: Pacote `br.com.lhamalog.core.dashboard` em camadas; testes unitários com portas falsas; integração `@CoreIntegrationTest` + `TestTenants`; stub do OpenObserve (`_search` SQL).
- **Rationale**: Padrão das specs 009/010; constituição III.
- **Alternatives considered**: Estender `product` ou `usage` (mistura responsabilidades).

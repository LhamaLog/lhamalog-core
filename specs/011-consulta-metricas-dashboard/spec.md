# Feature Specification: Consulta de métricas para dashboards (produto e empresa)

**Feature Branch**: `LLT-145-consulta-metricas-dashboard`

**Created**: 18/11/2026

**Status**: Draft

**Input**: User description: "Consulta de métricas para os dashboards por produto e da empresa do Web (Jira LLT-145 / contrato LLT-159). O Core expõe dois GET (`/api/dashboard/products/{productId}/metrics` e `/api/dashboard/company/metrics`) conforme o contrato do Web (spec 007), com contagens de logs por severidade (ERROR/WARNING/INFO, mapeamento ADR_170 no Core), produto, serviço e período, série temporal por hora ou dia, ranking de serviços, totais por produto, volume de ingestão (`volumeIngestaoBytes` em bytes inteiros) a partir dos fatos de uso (`/internal/usage-facts`), `retentionDays` do plano (ADR_131) e `dadosConsolidadosAte`. Fonte dos logs: OpenObserve (ADR_168), streams por tenant, org `lhamalog`. Agregações pré-calculadas SSID_011 (ADR_166): job que grava agregados por tenant, produto, severidade, serviço e hora lendo os logs; consulta híbrida (agregados consolidados + contagem na hora só do intervalo aberto). Autorização: tenant do token Clerk; DONO/ADMIN veem todos os produtos; MEMBRO só AcessoProduto; produto de outra empresa ou MEMBRO sem AcessoProduto → 404 (padrão `ProductService.get`); MEMBRO sem produtos → 200 com lista vazia. Header `X-Correlation-Id` já tratado pelo Core. Campo opcional de ocorrências ausente até a LLT-104."

## Clarifications

### Session 2026-11-18

- Q: Qual é a fonte dos dados do MVP para as métricas do dashboard? → A: OpenObserve via Core (contagens de logs por severidade, produto, serviço e período) + fatos de uso já existentes (`/internal/usage-facts`); o Web nunca acessa o OpenObserve (ADR_141).
- Q: O job SSID_011 (ADR_166) entra nesta entrega? Consulta só na hora ou híbrida? → A: Job construído nesta entrega, lendo **logs** (não Ocorrências). Consulta híbrida (agregados consolidados + contagem na hora do intervalo aberto) com campo `dadosConsolidadosAte`. Confirmado por Michel (18/11, 22:02): o job lê logs agora e passa a ler Ocorrências depois da LLT-104.
- Q: Com que frequência o job de agregados deve rodar? → A: A cada 1 hora por padrão, via cron Spring de 6 campos na propriedade `lhamalog.dashboard.aggregation.cron` (padrão `0 0 * * * *`) — requisito FR-009 / I1.
- Q: DONO e ADMIN diferem no dashboard? O que vê um MEMBRO sem produtos? → A: DONO = ADMIN (todos os produtos da empresa). MEMBRO sem produtos → 200 com lista/totais vazios, sem erro de autorização genérico.
- Q: O dashboard muda por plano (FREE vs demais)? → A: Dashboard igual para todos os planos; só muda o período máximo `min(30, retentionDays)` (ADR_131).
- Q: O Core deve enviar selos Crítico/Atenção/Estável? → A: Não. Selos são derivados no Web (Crítico ERROR ≥10%; Atenção 2–10%; Estável <2%); o Core não envia selo.
- Q: Quem implementa o Core e quem revisa Clerk/papéis? → A: Implementação 100% pelo agente de desenvolvimento; o Felipe revisa o que tocar em Clerk/papéis (FR-019).
- Q: Como tratar Ocorrências enquanto a LLT-104 não existe? → A: Campo opcional `occurrences` no contrato permanece ausente até a LLT-104; o Web mostra “não implementado ainda”.
- Q: A interpretação híbrida do SSID_011 (job lê logs; Ocorrências depois da LLT-104) fica confirmada? → A: **Confirmado (Michel, 18/11 22:02 / A1-C1)**. O job SSID_011 lê logs agora; passa a ler Ocorrências depois da LLT-104. Marcas `[A CONFIRMAR]` removidas.
- Q: Em que unidade a API trafega volume/cota? → A: **Bytes inteiros** (`volumeIngestaoBytes` e `volumeUsedBytes`). Cota do plano em bytes na API; conversão para GiB (base 2^30) só na tela do Web — nunca misturar 10^9 (U2).
- Q: Produto inacessível (outra empresa ou MEMBRO sem AcessoProduto) responde 403 ou 404? → A: **404**, alinhado a `ProductService.get` — não 403 de negócio (decisão 18/11 22:02).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar métricas do dashboard por produto (Priority: P1)

Um cliente autenticado do Web (ou outro consumidor autorizado) pede as métricas de um produto acessível no intervalo `from`/`to`, com filtro opcional de severidades. O Core devolve totais de logs (ERROR, WARNING, INFO), traces e volume usado no período (bytes), a cota `volumeIngestaoBytes` do plano (inteiro em bytes; conversão para GiB 2^30 só no Web), `retentionDays`, série temporal (ERROR/WARNING por hora ou dia), ranking dos até cinco serviços com mais ERROR e, quando aplicável, até quando os dados estão consolidados (`dadosConsolidadosAte`). Severidades brutas dos logs são normalizadas no Core (ADR_170) antes da resposta. O campo opcional de ocorrências por ponto da série fica ausente enquanto a LLT-104 não existir.

**Why this priority**: é o contrato LLT-159 para a LLT-222; sem este GET o dashboard por produto do Web não tem dados.

**Independent Test**: com tenant, produto acessível e logs/fatos no período, chamar `GET /api/dashboard/products/{productId}/metrics` com `from`/`to` válidos e conferir payload 200 (totais, série, ranking, `retentionDays`, `volumeIngestaoBytes`) e eco de `X-Correlation-Id`.

**Acceptance Scenarios**:

1. **Given** um DONO ou ADMIN da empresa e um produto da mesma empresa com dados no período, **When** consulta métricas do produto com `from`/`to` válidos, **Then** recebe 200 com `productId`, intervalo, totais (`logs`, `error`, `warning`, `info`, `traces`, `volumeUsedBytes`), `volumeIngestaoBytes`, `retentionDays`, série com `bucket` `hour` ou `day` e pontos com `error`/`warning`, e ranking de até 5 serviços por `error` desc.
2. **Given** filtro `severities=ERROR,WARNING`, **When** a consulta é feita, **Then** totais e série refletem só essas severidades (INFO fora do recorte de filtro quando aplicável às contagens filtráveis).
3. **Given** período sem logs nem fatos de uso, **When** a consulta é feita, **Then** recebe 200 com totais zerados, série vazia ou pontos zerados e ranking vazio — sem inventar valores.
4. **Given** produto inexistente, de outro tenant ou inacessível ao MEMBRO (sem AcessoProduto), **When** alguém consulta, **Then** recebe 404 (padrão `ProductService.get`), sem vazar se o produto existe em outra empresa.
5. **Given** `from` ≥ `to`, severidade desconhecida ou período acima do permitido pelo plano, **When** a consulta é feita, **Then** recebe 400 com código/mensagem de filtro inválido.
6. **Given** uma requisição com `X-Correlation-Id` válido, **When** a resposta é devolvida, **Then** o mesmo id volta no header (comportamento já do Core).
7. **Given** o MVP sem Módulo de Ocorrências (LLT-104), **When** a série é montada, **Then** o campo `occurrences` nos pontos está ausente (opcional no contrato).

---

### User Story 2 - Consultar métricas do dashboard da empresa (Priority: P1)

O consumidor pede métricas consolidadas da empresa no intervalo, opcionalmente restritas a uma lista de `productIds` e severidades. O Core devolve totais consolidados, `productsMonitored`, série por produto, lista de produtos com contagens (logs, error, warning), `retentionDays` e `dadosConsolidadosAte` quando houver consolidação. Só entram produtos autorizados à pessoa: DONO/ADMIN = todos da empresa; MEMBRO = AcessoProduto. MEMBRO sem produtos recebe 200 com totais zerados e listas vazias (não é erro de autorização genérico).

**Why this priority**: é o contrato LLT-159 para a LLT-223; completa a entrega multi-repositório com o Web.

**Independent Test**: com acesso a dois produtos e dados no período, chamar `GET /api/dashboard/company/metrics` e conferir consolidado, `seriesByProduct` e `products[]` só dos autorizados; repetir como MEMBRO sem AcessoProduto e obter 200 vazio.

**Acceptance Scenarios**:

1. **Given** um DONO ou ADMIN com vários produtos e dados, **When** consulta métricas da empresa sem `productIds`, **Then** recebe 200 com totais somados, `productsMonitored` igual à quantidade de produtos da empresa no recorte, `seriesByProduct` e `products[]` cobrindo esses produtos.
2. **Given** um MEMBRO com AcessoProduto só a um subconjunto, **When** consulta sem filtro de produtos, **Then** só esses produtos entram nos totais, séries e lista.
3. **Given** `productIds` com ids autorizados, **When** consulta, **Then** o consolidado reflete só esse subconjunto; ids inacessíveis ou de outro tenant no filtro MUST ser rejeitados (400 ou 404) sem misturar dados.
4. **Given** um MEMBRO sem nenhum produto vinculado, **When** consulta métricas da empresa (ou tenta o fluxo equivalente), **Then** recebe 200 com totais zerados / listas vazias, sem 403 genérico de segurança.
5. **Given** DONO e ADMIN da mesma empresa, **When** consultam o mesmo intervalo, **Then** veem o mesmo conjunto de produtos (não há diferença operacional entre os dois papéis neste MVP).
6. **Given** pedido cujo contexto de tenant não corresponde à empresa dos dados (ex.: produto de outro tenant no path), **When** a consulta é feita, **Then** recebe 404 sem vazamento (LLT-158 / alinhado a `ProductService.get`).

---

### User Story 3 - Agregar logs periodicamente e responder de forma híbrida (Priority: P1)

O Core mantém um job de agregações pré-calculadas (SSID_011 / ADR_166) que, periodicamente, lê logs no OpenObserve e grava agregados por tenant, produto, severidade (ERROR/WARNING/INFO), serviço e hora. A consulta de métricas usa os agregados já consolidados e conta na hora no OpenObserve só o intervalo ainda aberto (hora corrente ou o que o job ainda não processou). A resposta informa `dadosConsolidadosAte` quando houver consolidação. O volume e traces aceitos vêm dos fatos de uso já existentes (`/internal/usage-facts`), não do OpenObserve. Frequência do job: cron Spring via `lhamalog.dashboard.aggregation.cron` (padrão `0 0 * * * *` = a cada 1 hora). Confirmado (Michel, 18/11): o job lê **logs** agora; Ocorrências entram depois da LLT-104.

**Why this priority**: a ADR_166 mantém agregações no MVP; sem o job e a consulta híbrida o Web não tem `dadosConsolidadosAte` nem desempenho aceitável em períodos longos.

**Independent Test**: rodar o job sobre um intervalo conhecido de logs, consultar métricas cobrindo horas consolidadas e a hora aberta, e conferir merge + `dadosConsolidadosAte`; alterar `lhamalog.dashboard.aggregation.cron` e conferir que o agendamento usa o novo valor (padrão `0 0 * * * *`).

**Acceptance Scenarios**:

1. **Given** logs no OpenObserve para um tenant/produto em horas passadas, **When** o job executa, **Then** existem agregados por tenant, produto, severidade, serviço e hora correspondentes a esses logs (lendo logs, não Ocorrências).
2. **Given** agregados consolidados até um instante T e logs novos após T, **When** a consulta cobre um intervalo que inclui ambos, **Then** a resposta mescla agregados até T com contagem na hora do intervalo aberto e informa `dadosConsolidadosAte` coerente com T (ou o último bucket fechado processado).
3. **Given** a configuração padrão, **When** o serviço sobe sem override, **Then** o job está agendado com cron `0 0 * * * *` (a cada 1 hora); com `lhamalog.dashboard.aggregation.cron` alterada, **Then** a frequência segue a configuração.
4. **Given** falha transitória ao ler o OpenObserve no intervalo aberto, **When** a consulta híbrida roda, **Then** o erro é tratado de forma observável (log com correlation id da requisição ou do job) e a API responde com falha recuperável (5xx) sem corromper agregados já gravados.
5. **Given** o SSID_011 original que previa Ocorrências, **When** esta entrega é avaliada, **Then** o job agrega apenas logs; Ocorrências ficam para depois da LLT-104 (confirmação Michel 18/11 22:02).

---

### User Story 4 - Autorizar por papel e isolar por tenant (Priority: P1)

Toda consulta de métricas resolve o tenant a partir do token Clerk. `org:admin` mapeia DONO/ADMIN (todos os produtos da empresa); `org:member` mapeia MEMBRO (só AcessoProduto). Sem autenticação válida → 401. Produto inexistente, de outra empresa ou MEMBRO sem AcessoProduto → **404** (alinhado a `ProductService.get`), não 403 de negócio. Sem contexto de tenant na camada de segurança → 403 de segurança. Dashboard igual para todos os planos; a única diferença por plano é o teto de período via `retentionDays` (`min(30, retentionDays)` na validação do intervalo pedido).

**Why this priority**: isolamento e papéis são critérios de aceite (LLT-158, ADR_122/123/140); revisão do Felipe no que tocar em Clerk/papéis.

**Independent Test**: matriz DONO, ADMIN, MEMBRO com/sem AcessoProduto, outro tenant e token inválido contra os dois GET.

**Acceptance Scenarios**:

1. **Given** token inválido ou ausente, **When** qualquer dos GET é chamado, **Then** recebe 401.
2. **Given** MEMBRO sem acesso ao `productId` do path (ou produto de outra empresa), **When** consulta métricas do produto, **Then** recebe **404**, sem dados e sem vazamento.
3. **Given** DONO ou ADMIN, **When** consulta qualquer produto da própria empresa, **Then** a consulta é autorizada (demais regras de período/existência iguais).
4. **Given** planos diferentes (FREE, Starter, Pro, Business/Enterprise), **When** as métricas são consultadas, **Then** o formato da resposta é o mesmo; só `retentionDays` (e o teto aceito de `from`/`to`) muda conforme ADR_131.
5. **Given** alteração de papéis ou de AcessoProduto, **When** a próxima consulta ocorre, **Then** as regras vigentes são aplicadas na hora (sem cache de permissão no Core que ignore revogação recente além do que o token/Clerk já impõem).

---

### Edge Cases

- MEMBRO sem produtos: 200 com listas/totais vazios nos fluxos de empresa; no por produto, sem `productId` acessível a consulta específica continua **404**.
- Produto arquivado: se ainda for “visível” pelas regras atuais de produto, entra nas métricas; se as features 001/002 o ocultam do acesso, trata-se como inacessível (**404**) — sem inventar exceção nova nesta feature.
- `productIds` malformado ou misturando ids de outro tenant: 400 ou 404; nunca mistura tenancy.
- Período maior que `min(30, retentionDays)`: 400.
- OpenObserve sem dados no stream do tenant: 200 com zeros, não 404.
- Job atrasado: `dadosConsolidadosAte` reflete o último consolidado; intervalo aberto cresce até o job recuperar.
- Campo `occurrences`: sempre omitido neste MVP; o Web trata ausência como “não implementado ainda”.
- Selos Crítico/Atenção/Estável: **não** são enviados pelo Core; o Web deriva dos totais por produto.
- Correlation id inválido ou ausente: o Core gera um e devolve (já existente); o Web nesta feature sempre envia um válido.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O Core MUST expor `GET /api/dashboard/products/{productId}/metrics` com query obrigatória `from` e `to` (ISO-8601 instant, `to` > `from`) e `severities` opcional (`ERROR`, `WARNING`, `INFO`; omitido = todos), autenticado por Bearer do Clerk.
- **FR-002**: O Core MUST expor `GET /api/dashboard/company/metrics` com `from`/`to` obrigatórios, `productIds` opcional (lista; omitido/vazio = todos os produtos autorizados à pessoa) e `severities` opcional, nas mesmas regras de autenticação.
- **FR-003**: A resposta 200 do endpoint por produto MUST incluir: `productId`, `from`, `to`, `retentionDays`, `volumeIngestaoBytes`, `totals` (logs, error, warning, info, traces, volumeUsedBytes), `series` (`bucket` `hour`|`day`, `points[]` com `start`, `end`, `error`, `warning`) e `serviceRanking` (até 5 itens `{ serviceName, error }` ordenados por `error` desc). MUST incluir `dadosConsolidadosAte` quando houver consolidação aplicável.
- **FR-004**: A resposta 200 do endpoint da empresa MUST incluir: `from`, `to`, `retentionDays`, `totals` (logs, error, warning, productsMonitored), `seriesByProduct[]` (productId, productName, bucket, points) e `products[]` (productId, productName, logs, error, warning). MUST incluir `dadosConsolidadosAte` quando houver consolidação aplicável.
- **FR-005**: Contagens de logs MUST vir do OpenObserve (ADR_168: streams por tenant, org `lhamalog`; documentos com tenantId, productId, _timestamp, severityText/severityNumber, attributes de serviço). O Web MUST NOT acessar o OpenObserve; só o Core.
- **FR-006**: Volume usado e traces aceitos no período MUST vir dos fatos de uso já existentes no Core (recurso `/internal/usage-facts`: registros e bytes aceitos por produto, logs e traces). `volumeUsedBytes` MUST ser inteiro em bytes; `traces` MUST ser a soma de `acceptedRecordCount` dos fatos com domínio/tipo traces. `volumeIngestaoBytes` MUST refletir a cota do plano (ADR_131) como **inteiro em bytes** (fator 2^30 a partir de GiB de plano, se a origem for em GiB). A conversão para GiB (÷ 2^30) para exibição MUST ocorrer só na tela do Web — a API MUST NOT misturar base 10^9.
- **FR-007**: Severidades MUST ser normalizadas no Core antes da resposta (ADR_170: erro/error/fatal → ERROR; alerta/warn/warning → WARNING; info/debug/trace → INFO). A API MUST devolver só `ERROR` | `WARNING` | `INFO`; valor de filtro desconhecido MUST gerar 400.
- **FR-008**: O Core MUST construir nesta entrega o job de agregações SSID_011 (ADR_166): gravar agregados por tenant, produto, severidade, serviço e hora a partir dos **logs** do OpenObserve (não a partir de Ocorrências neste MVP). Depois da LLT-104 o job poderá passar a ler Ocorrências (confirmação Michel 18/11).
- **FR-009**: O job MUST rodar com frequência definida por cron Spring de 6 campos na propriedade `lhamalog.dashboard.aggregation.cron`, padrão `0 0 * * * *` (a cada 1 hora, no minuto 0).
- **FR-010**: A consulta MUST ser híbrida: usar agregados consolidados para intervalos fechados e contar na hora no OpenObserve só o intervalo ainda não consolidado; a resposta MUST expor `dadosConsolidadosAte` coerente com o último intervalo consolidado quando aplicável. Confirmado (Michel 18/11): job lê logs agora; Ocorrências / evolução do job após LLT-104.
- **FR-011**: Autorização MUST usar o `tenantId` do token. `org:admin` = DONO/ADMIN veem todos os produtos da empresa; `org:member` = MEMBRO vê só AcessoProduto (ADR_122/123/140). DONO e ADMIN MUST ter a mesma visão de produtos. Produto de outra empresa, inexistente ou inacessível ao papel MUST resultar em **404** (alinhado a `ProductService.get` / LLT-158) — não 403 de negócio.
- **FR-012**: MEMBRO sem produtos vinculados MUST receber 200 com totais zerados e listas vazias no endpoint da empresa (sem 403 genérico de segurança). No endpoint por produto, produto inacessível MUST responder **404**.
- **FR-013**: O período solicitado MUST ser validado contra `min(30, retentionDays)` do plano (ADR_131: FREE 7, Starter 15, Pro 30, Business/Enterprise 90). Acima do teto ou intervalo inválido MUST responder 400. O dashboard (formato e campos) MUST ser igual para todos os planos; só o teto/`retentionDays` muda.
- **FR-014**: `series.bucket` / buckets por produto MUST ser `hour` quando a duração do período for ≤ 48 horas e `day` quando for maior, alinhado ao contrato consumido pelo Web.
- **FR-015**: O campo opcional `occurrences` nos pontos da série MUST permanecer ausente nesta entrega até a LLT-104; o Core MUST NOT inventar ocorrências.
- **FR-016**: O Core MUST NOT enviar selo de impacto (Crítico/Atenção/Estável); esses limiares são derivados no Web.
- **FR-017**: Headers: `Authorization` obrigatório; `X-Correlation-Id` aceito e ecoado pelo mecanismo `CorrelationId` já existente no Core.
- **FR-018**: Erros MUST seguir o contrato: 400 filtro inválido (`code`/`message`); 401 não autorizado; 403 só para falhas de segurança (ex.: sem Tenant); **404** para produto inexistente, de outro tenant ou MEMBRO sem AcessoProduto. Falhas de dependência (OpenObserve, etc.) MUST resultar em erro recuperável sem corromper agregados.
- **FR-019**: Implementação desta feature MAY ser 100% pelo agente de desenvolvimento; o que tocar em Clerk/papéis MUST passar por revisão do Felipe antes de fechar a entrega.
- **FR-020**: Esta feature MUST NOT implementar exportação (LLT-224), mobile (LLT-226), lista de Ocorrências (LLT-104) nem acesso do Web ao OpenObserve.

### Key Entities

- **Métricas do produto**: recorte temporal de um produto com totais, série, ranking de serviços, volume/cota e retenção.
- **Métricas da empresa**: consolidado multiproduto autorizado, com séries e linhas por produto.
- **Agregado horário (SSID_011)**: contagem pré-calculada por tenant, produto, severidade, serviço e hora, derivada de logs.
- **Fato de uso**: registro já existente de bytes/registros aceitos (fonte de volume e traces na resposta).
- **Dados consolidados até**: instante de negócio indicando até quando os buckets do job estão fechados para a consulta híbrida.
- **Severidade**: enum ERROR | WARNING | INFO após mapeamento ADR_170.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% das consultas autorizadas com dados no período, os dois GET devolvem 200 com os campos obrigatórios do contrato (incluindo `retentionDays` e, no por produto, `volumeIngestaoBytes`).
- **SC-002**: Em 100% dos testes de isolamento, dados de outro tenant ou produto inacessível não aparecem (**404** no por produto); em 100% dos casos MEMBRO sem produtos no endpoint da empresa, a resposta é 200 vazia sem erro de autorização genérico.
- **SC-003**: Em 100% das execuções bem-sucedidas do job sobre um intervalo conhecido, os agregados batem com as contagens esperadas por severidade/serviço/hora a partir dos logs.
- **SC-004**: Em 100% das consultas que cobrem intervalos consolidados e abertos, a resposta híbrida mescla as duas fontes e, quando há consolidação, inclui `dadosConsolidadosAte`.
- **SC-005**: Em 100% das respostas deste MVP, `occurrences` está ausente e nenhum selo Crítico/Atenção/Estável é enviado pelo Core.
- **SC-006**: Em 100% das requisições com `X-Correlation-Id` válido, o mesmo id retorna no header da resposta.
- **SC-007**: Em 100% dos períodos acima de `min(30, retentionDays)` ou com filtro inválido, a API responde 400 sem devolver métricas parciais enganosas.

## Assumptions

- Entrega multi-repositório: Web spec `007-dashboards-produto-empresa` + esta spec 011; contrato espelho em `lhamalog-web/.../contracts/dashboard-metrics.md` (LLT-159).
- Fonte do MVP: OpenObserve via Core + fatos de uso internos; o Web nunca acessa o OpenObserve (ADR_141 / ADR_168).
- **Confirmado (Michel, 18/11 22:02 / A1-C1)**: job SSID_011 lê **logs** agora (agregados + intervalo aberto + `dadosConsolidadosAte`); passa a ler Ocorrências depois da LLT-104. O SSID_011 original previa Ocorrências; a evolução fica pós-LLT-104.
- Frequência do job (I1): cron `lhamalog.dashboard.aggregation.cron`, padrão `0 0 * * * *` (FR-009).
- Unidades (U2): API trafega bytes inteiros (`volumeIngestaoBytes`, `volumeUsedBytes`); cotas de exemplo em múltiplos de 2^30; conversão para GiB (2^30) só na tela do Web — sem 10^9.
- Produto inacessível / outra empresa → **404** (não 403), alinhado a `ProductService.get`.
- DONO = ADMIN na visão de produtos; MEMBRO sem produtos → lista vazia 200.
- Dashboard igual em todos os planos; só muda o período máximo via `retentionDays`.
- Selos de impacto são responsabilidade exclusiva do Web (limiares já aprovados lá).
- Ocorrências: campo opcional ausente até a LLT-104.
- Papéis e tenant reutilizam o modelo já existente (Clerk, Membro.papel, AcessoProduto); Felipe revisa o que tocar nisso.
- Agente de desenvolvimento implementa 100%; constituição II aplica-se.
- Fora de escopo: UI Web, exportação, mobile, módulo de Ocorrências, mudança no Collector, Grafana operacional.

## Dependências

- OpenObserve acessível ao Core (credenciais fora do código; princípio V da constituição).
- Fatos de uso / `/internal/usage-facts` e plano com `retentionDays` / cota de volume (ADR_131) já existentes.
- Cadastro de produtos, AcessoProduto e papéis (features 001+).
- Contrato do Web (spec 007) alinhado a esta spec.
- LLT-104 (Ocorrências) para preencher `occurrences` e eventual evolução do job — fora deste MVP.
- Revisão do Felipe (Clerk/papéis) como portão antes de fechar a fase de autorização/testes de isolamento.

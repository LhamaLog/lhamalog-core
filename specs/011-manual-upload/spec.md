# Feature Specification: Upload manual de arquivos de logs (Core → Collector)

**Feature Branch**: `feature/LLT-292-manual-upload` (diretório de spec `specs/011-manual-upload`)

**Created**: 2026-11-20

**Status**: Implementação em andamento no Core (T003–T015, T033/T034 Core). Collector ainda bloqueado pela LLT-290. Review do Core em 20/11/2026: correções B1 e I/S aplicadas no worktree, ainda sem commit.

**Jira**: LLT-292

**Input**: "LLT-292: o frontend chama o Core `POST /manual/upload`, autenticado com JWT da Clerk, arquivos de até 100 MB, resposta 202. O Core repassa o arquivo ao Collector, que reaproveita o dedup da LLT-290 com `source=upload`. Seguir as ADRs. Rotas e autenticação interna seguem o código: prefixo `/api` em inglês e `X-LhamaLog-Service-Token` no Core→Collector."

**Fontes**: decisões do Michel em 20/11/2026 (16:59, 21:37 e clarify 22:45); ADRs 015, 022, 026, 028, 030, 032, 041–056, 054 (somente leitura no Drive); spec 010 do Collector (`010-file-hash-dedup`, somente leitura); código em `origin/Development` `23a69d9` do Core e o pacote `dedup` da LLT-290. Constituição do Core v2.1.0.

## Contexto e alinhamento

O upload manual resolve o conflito USID_006 × SSID_013 a favor do USID_006 (arquivo entra pelo Collector e vai para a fila; não há caminho do Web direto ao Finder). O Backend Java e a API Core são o mesmo serviço (ADR_054): o front fala só com o Core.

O card LLT-292 (18/11) ainda descreve um endpoint **no Collector**, autenticado com token de ingestão OTLP e limite de **5 MB**. Isso foi superado em 20/11: o caminho é Front → Core (JWT Clerk, 100 MB, 202) → Collector (dedup `source=upload`). Esta spec segue as decisões de 20/11 e as ADRs. O Jira não é alterado nesta etapa.

A LLT-290 (spec 010 do Collector) já reserva a origem `upload` e exige que o serviço de hash seja reaproveitável. O endpoint de importação no Collector ficou **fora** da 010 e entra nesta feature. A implementação desse endpoint depende do pacote `dedup` da LLT-290 (`FileSource.UPLOAD`, `FileDeduplication.claim(...)`), inclusive da decisão B1 ainda aberta (claim em andamento).

## Clarifications

### Session 2026-11-20 (22:45, America/Sao_Paulo)

O Michel confirmou as 12 recomendações do `clarify-questions.md`, exatamente como escritas.

- Q1: A rota pública no Core é `POST /api/manual/upload`? → **A:** Sim. Autenticação com `Authorization: Bearer` (JWT da Clerk). 401/403 seguem a ADR_032.
- Q2: Quais formatos entram no MVP? → **A:** `.txt`, `.json`, `.csv` e `.log`, lista extensível por configuração, revalidada no Core e no Collector.
- Q3: Uma requisição pode trazer vários arquivos? → **A:** Sim, multipart com um ou mais arquivos, agrupados num lote (ADR_045). Sem teto de quantidade de negócio; o teto é o da Q4. Na implementação, o Tomcat (Boot 4.1.1) aplica `server.tomcat.max-part-count=50` (campo `productId` + no máximo 49 arquivos) e `server.tomcat.max-part-header-size=512B`, valores deixados explícitos em `application.properties`.
- Q4: Qual o teto de tamanho da requisição? → **A:** 100 MB por arquivo e, neste MVP, teto **provisório** de 100 MB também para a requisição inteira (`max-file-size` e `max-request-size` em properties/perfil). Não é limite de negócio permanente. Nunca em `.env`. Um arquivo de exatamente 100 MB é recusado pelo contêiner, porque o envelope multipart (boundaries, cabeçalhos e o campo `productId`) conta no `max-request-size`.
- Q5: O upload entra na mesma cota, rate limit e assinatura da ingestão OTLP? → **A:** Sim para saldo, assinatura e rate limit. O teto de tamanho deste caminho é 100 MB, não 5 MB. Sem saldo → 429; sem plano → 503; o Core espelha o status ao front.
- Q6: Path, porta e segredo do endpoint interno no Collector? → **A:** `POST /internal/manual/upload` na porta 8080 (`server.port`), só com `X-LhamaLog-Service-Token` (recusar `Authorization` de usuário). Token OTLP de produtor não autentica este path. Segredos só por variável de ambiente: `LHAMALOG_CORE_COLLECTOR_TOKEN` (Core envia) e `LHAMALOG_COLLECTOR_SERVICE_TOKEN` (Collector confere); na operação podem ser o mesmo valor. Nenhum valor é escrito em artefato.
- Q7: Qual `signal` e de quais bytes é o MD5? → **A:** `signal=logs`. MD5 dos bytes do arquivo recebido (sem gzip de transporte OTLP).
- Q8: Se só parte do lote for duplicata ou inválida? → **A:** Por arquivo (espírito da ADR_048). Os aceitos entram na fila; os recusados aparecem no corpo do 202 com o motivo. Se nenhum for aceito: 409 quando todos forem duplicata; 413/400 conforme o motivo.
- Q9: Fila de leitura existente ou FilaOrdenada das ADRs? → **A:** Neste MVP, a fila de leitura já existente (Redis stream da spec 004), com `fileSource=upload` e `idempotencyId` composto. FilaOrdenada / Finder ficam para depois do MVP.
- Q10: Proteção contra malware e prompt injection no MVP? → **A:** No MVP, o Collector revalida extensão e tamanho e recusa binário óbvio (magic bytes). Malware avançado e prompt injection ficam para depois, ainda no Collector, não no front.
- Q11: Quem pode enviar? → **A:** Qualquer usuário autenticado que já possa ver o produto (`GET /api/products/{id}`) no tenant da sessão. Produto arquivado ou de outro tenant → 403.
- Q12: O 202 sai antes ou depois do Collector? → **A:** O Core **espera** a decisão do Collector (aceite, 409, 413, 429, 503) e só então responde 202 ou o erro. O 202 significa “enfileirado”.

**Nota de alinhamento com o código (Q11):** `ProductService.get()` hoje devolve 404 para produto inexistente ou sem acesso, e permite *ler* produto arquivado (o 409 só aparece na edição). O upload **não** copia esse comportamento: segue a Q11 (403 para arquivado, outro tenant ou sem acesso), inclusive tratando id desconhecido como 403, para não abrir um 404 só neste caminho e respeitar a decisão confirmada. Um produto em status `CONFIGURING` pode receber uploads; só arquivado (e outro tenant / sem acesso / id desconhecido) é recusado.

## Decisões fechadas (não reabrir)

1. **Caminho**: front → Core → Collector. Sem caminho do Web ao Finder nesta entrega.
2. **Rota pública**: `POST /api/manual/upload`, JWT da Clerk, prefixo `/api` em inglês.
3. **Tamanho**: 100 MB por arquivo e 100 MB provisórios por requisição. O limite de 5 MB do OTLP não se aplica a este caminho.
4. **Resposta de aceite**: 202 quando pelo menos um arquivo é enfileirado, depois da decisão do Collector (ADR_053 + Q12).
5. **Repasse**: API interna com `X-LhamaLog-Service-Token` (não se introduz o JWT interno da ADR_028).
6. **Dedup**: `FileDeduplication.claim(..., FileSource.UPLOAD, bytesDoArquivo)`, chave `lhamalog:dedup:file:{tenantId}:{productId}:{signal}:{source}:{md5}`, `signal=logs`, MD5 dos bytes recebidos. Duplicata → 409 + `ALREADY_EXISTS`. Redis fora → dedup omitido com WARN (fail-open da spec 010).
7. **Não-colisão com OTLP**: o mesmo conteúdo por `source=otlp` e por `source=upload` não é duplicata (spec 010, decisão 11 / R13).
8. **`idempotencyId`**: `UUIDv3("<batchId>:<signal>:<source>:<md5>")`, metadata `fileMd5` e `fileSource=upload` (spec 010). Um `batchId` (e uma mensagem na fila) **por arquivo aceito**; o `loteId` da requisição agrupa a resposta HTTP.
9. **Logs**: correlation id (= request id; `X-Correlation-Id`, MDC `correlationId`), referência à função, aviso e data com fuso `America/Sao_Paulo`. O Core propaga o mesmo cid ao Collector. Sem payload, segredo ou MD5 completo (só prefixo de 8 caracteres).
10. **Pós-MVP**: WebSocket, `/papeis`, eventos-gateway, Ocorrência/Sugestão, OpenAPI, FilaOrdenada/Finder, malware/prompt injection avançados (Michel 20/11 16:59 e Q9–Q10).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enviar um arquivo válido pelo Core e vê-lo enfileirado (Priority: P1)

Um usuário autenticado, no card de um Produto da empresa, envia um arquivo de log aceito. O Core autentica a sessão Clerk, confere que o `productId` é visível no tenant e não está arquivado, revalida extensão e tamanho, e espera o Collector. O Collector aplica dedup (`source=upload`), rate/cota/assinatura, grava o conteúdo no OpenObserve (contrato da fila de leitura) e publica `log.ingested` no stream. O Core responde 202 com `loteId` e o arquivo aceito.

**Why this priority**: é o caminho oficial do MVP; sem ele o upload manual não existe.

**Independent Test**: POST autenticado com um `.log` pequeno e produto visível devolve 202; o OpenObserve tem o lote; a fila de leitura recebe exatamente uma mensagem com `fileSource=upload`.

**Acceptance Scenarios**:

1. **Given** um usuário com sessão Clerk ativa e um produto visível do seu tenant, **When** envia `POST /api/manual/upload` com um arquivo `.log` de 1 MB e o `productId` do card, **Then** o Core responde 202 e o Collector publica uma mensagem `log.ingested` na fila de leitura com `source=upload`.
2. **Given** a mesma pré-condição, **When** a requisição não tem `Authorization: Bearer` válido, **Then** o Core responde 401 e nada é encaminhado ao Collector.
3. **Given** uma sessão válida, **When** o `productId` é de outro tenant, não há acesso ou o produto está arquivado (ou o id é desconhecido), **Then** o Core responde 403 e nada é encaminhado ao Collector.
4. **Given** um arquivo aceito, **When** o Core encaminha ao Collector, **Then** a chamada interna leva `X-LhamaLog-Service-Token` e o mesmo `X-Correlation-Id` da requisição do front, sem header `Authorization`.

---

### User Story 2 - Recusar duplicata com 409 e não republicar (Priority: P1)

O mesmo arquivo, para o mesmo tenant e produto, enviado de novo pelo upload, é recusado. A fila não ganha uma segunda mensagem. O conteúdo idêntico que tenha entrado só por OTLP não conta como duplicata deste caminho.

**Why this priority**: contrato já reservado na LLT-290 e critério central da LLT-292.

**Independent Test**: dois POSTs iguais → 202 depois 409; `XADD` na fila +1, não +2. Um export OTLP com o mesmo MD5 continua aceito (origem diferente).

**Acceptance Scenarios**:

1. **Given** um arquivo já aceito por upload no produto P do tenant T, **When** o mesmo conteúdo é enviado de novo por `POST /api/manual/upload` para T/P, **Then** a resposta é 409 com `ALREADY_EXISTS` e a fila não recebe nova mensagem.
2. **Given** o mesmo conteúdo já ingerido só por OTLP (`source=otlp`) em T/P, **When** ele chega por upload (`source=upload`), **Then** é aceito (202) e gera uma mensagem de upload.
3. **Given** o mesmo conteúdo já enviado por upload em T/P, **When** é enviado para outro produto ou outro tenant, **Then** é aceito.
4. **Given** Redis indisponível no Collector, **When** chega um upload, **Then** o dedup é omitido (WARN), o arquivo pode ser aceito e vale o log/métrica `store_unavailable` da spec 010.

---

### User Story 3 - Validar tamanho, formato, binário óbvio e autenticação interna (Priority: P1)

O front faz a checagem rápida (ADR_043), mas o backend revalida. Arquivo acima de 100 MB, requisição acima de 100 MB, extensão não permitida ou binário óbvio é recusado. Chamada ao Collector sem service token válido é recusada. O limite de 5 MB do OTLP não é usado neste caminho.

**Why this priority**: impede bypass do front e alinha o limite de 100 MB com as ADRs.

**Independent Test**: 100 MB + 1 byte → 413; extensão fora da lista → 400; PE/ELF no conteúdo → 400; Collector sem token ou com `Authorization` → 401.

**Acceptance Scenarios**:

1. **Given** um arquivo de 100 MB + 1 byte (ou uma requisição cuja soma passa de 100 MB), **When** o Core (ou o Collector) recebe o envio, **Then** a resposta é 413 e nada entra na fila.
2. **Given** um arquivo com extensão fora da lista configurada, **When** é enviado ao Core, **Then** a resposta é 400 e o arquivo não entra na fila.
3. **Given** um arquivo com extensão `.log` cujo conteúdo começa com magic bytes de executável (MZ/ELF/Mach-O), **When** chega ao Collector, **Then** é recusado (400 no lote ou listado como recusado no 202 se houver outros aceitos) e não entra na fila.
4. **Given** um POST direto ao endpoint interno do Collector sem `X-LhamaLog-Service-Token` válido, **When** a requisição chega, **Then** o Collector responde 401 e não publica.
5. **Given** as propriedades atuais `spring.servlet.multipart.max-file-size=2MB` no Core, **When** esta feature for implementada, **Then** `max-file-size` e `max-request-size` permitem 100 MB neste caminho (só em `application.properties` / perfil, nunca em `.env`).

---

### User Story 4 - Vários arquivos na mesma requisição (Priority: P1)

Um ou mais arquivos seguem em uma única requisição (ADR_045, Q3/Q8). O Collector trata cada arquivo; o lote agrupa o resultado. Os válidos entram; os recusados aparecem no corpo do 202. Se todos forem duplicata, 409.

**Why this priority**: confirmado no clarify; faz parte do contrato de aceite do MVP.

**Independent Test**: multipart com um `.log` válido e um duplicado devolve 202, a fila recebe só o válido, e o corpo lista o duplicado.

**Acceptance Scenarios**:

1. **Given** dois arquivos válidos (soma ≤ 100 MB) na mesma requisição, **When** o Core aceita o envio, **Then** responde um único 202 com um `loteId` e dois arquivos `ACCEPTED`; a fila recebe duas mensagens (`fileSource=upload`) ligadas a esse lote.
2. **Given** um arquivo válido e um duplicado na mesma requisição, **When** o Collector processa o lote, **Then** o válido é enfileirado, o duplicado é listado no corpo, e o status permanece 202.
3. **Given** todos os arquivos do lote duplicados, **When** o Collector termina, **Then** o Core responde 409 e a fila não recebe mensagem nova.

---

### Edge Cases

- Collector indisponível ou timeout no repasse: o Core não responde 202; devolve 503 (ou 504) no formato `ApiError`, sem `files[]`. O 504 significa resultado desconhecido: o Collector pode ter enfileirado depois que o Core desistiu. Isso se soma ao risco herdado da B1 da LLT-290 (claim em andamento hoje responde 409); o Core **não** inventa um 503 local para esse caso.
- 413 do contêiner (tamanho da requisição, `max-part-count` ou `max-part-header-size`): `ApiError` sem `files[]`. A mensagem não descreve um estouro de partes ou de cabeçalho como “100 MB”.
- Nome de arquivo: o Core preserva o nome enviado pelo front (ADR_044); a identidade posterior é o id gerado no Collector.
- Requisição sem arquivo ou com parte multipart vazia: 400.
- Não há gzip de transporte OTLP neste caminho; o MD5 é dos bytes recebidos (Q7).
- `lhamalog.file-dedup.enabled=false`: mesmo comportamento da spec 010 (sem claim; `idempotencyId = batchId`; sem metadata `fileMd5`/`fileSource`).
- Claim em andamento (B1 da LLT-290, ainda aberta): o upload herda o comportamento atual (SET NX trata “em andamento” como 409) até o Michel decidir a T041. Ver Riscos.
- Dois arquivos iguais na mesma requisição: o segundo é duplicata do primeiro (mesmo tenant/produto/`logs`/`upload`/MD5).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O Core MUST expor `POST /api/manual/upload` autenticado pelo session token da Clerk (mesmo filtro das rotas `/api/**` de produto). 401/403 seguem a ADR_032.
- **FR-002**: O `productId` MUST vir do card (campo de formulário). O Core MUST verificar que o produto existe no tenant da sessão, que o usuário já poderia vê-lo em `GET /api/products/{id}` (admin vê todos os do tenant; demais precisam de `ProductAccess`) e que **não está arquivado**. Caso contrário MUST responder 403 (Q11), inclusive para id desconhecido. Produto em `CONFIGURING` MAY receber upload.
- **FR-003**: O Core MUST aceitar `multipart/form-data` com um ou mais arquivos e MUST revalidar extensão e tamanho. Limite: 100 MB por arquivo e 100 MB por requisição. Formatos iniciais: `.txt`, `.json`, `.csv`, `.log`, lista configurável (mesma ideia no Collector).
- **FR-004**: Quando pelo menos um arquivo é enfileirado, o Core MUST responder 202 Accepted com JSON (`loteId` + lista de arquivos: nome, id, status, motivo se recusado). Sem WebSocket nesta entrega (FR-015).
- **FR-005**: Depois das validações de sessão, produto, extensão e tamanho, o Core MUST repassar os arquivos ao Collector e **esperar** a decisão (Q12). A chamada MUST usar `X-LhamaLog-Service-Token` (valor de `LHAMALOG_CORE_COLLECTOR_TOKEN`), MUST encaminhar `X-Correlation-Id`, `tenantId`, `productId` e o nome original de cada arquivo, e MUST NOT enviar `Authorization`.
- **FR-006**: O Collector MUST expor `POST /internal/manual/upload` em `server.port` 8080. MUST autenticar só pelo service token (`LHAMALOG_COLLECTOR_SERVICE_TOKEN`). Presença de `Authorization` ou token ausente/errado → 401. Token OTLP de produtor MUST NOT autenticar este path. O Collector confia em `tenantId`/`productId` já autorizados pelo Core.
- **FR-007**: Para cada arquivo, o Collector MUST calcular o MD5 dos bytes recebidos e chamar `FileDeduplication.claim(tenantId, productId, FileSignal.LOGS, FileSource.UPLOAD, bytes)` **antes** da reserva de cota. Duplicata → `ALREADY_EXISTS`, sem reserva, sem OpenObserve, sem publicação, sem fato de uso. Métrica `lhamalog.ingestion.refused{reason=duplicate_file}` e log INFO da spec 010, com `source=upload`.
- **FR-008**: Arquivo aceito MUST ser gravado no OpenObserve (stream de logs, contrato da spec 002/004: a mensagem da fila é só uma referência) e em seguida publicado em `lhamalog:stream:logs` como `log.ingested`, `schemaVersion` 1, `fileSource=upload`, `idempotencyId = UUIDv3("<batchId>:logs:upload:<md5>")`. Um `batchId` e uma mensagem por arquivo aceito; o `loteId` só agrupa a resposta HTTP. `schemaVersion` não muda.
- **FR-009**: Regras de saldo, assinatura e rate limit são as da ingestão (LLT-183/186), com teto de arquivo de 100 MB (não 5 MB). Recusas: 429 / `RESOURCE_EXHAUSTED` sem saldo; 503 / `UNAVAILABLE` sem plano. O Core MUST espelhar esses status. A reserva ocorre depois do claim, por arquivo.
- **FR-010**: O Core e o Collector MUST elevar `spring.servlet.multipart.max-file-size` e `max-request-size` para 100 MB (hoje 2 MB no Core). Também MUST ajustar o teto do contêiner HTTP (`server.tomcat.max-http-form-post-size` / `max-swallow-size`) e declarar de forma explícita `server.tomcat.max-part-count=50` e `max-part-header-size=512B`. Os pares `spring.servlet.multipart.max-*` e `lhamalog.manual-upload.max-*` MUST permanecer iguais (as mensagens de 413 saem do segundo). Nenhum `.env` versionado é alterado; `.env.example` e compose só declaram placeholders.
- **FR-011**: O Collector MUST recusar binário óbvio por magic bytes (PE/MZ, ELF, Mach-O) depois da extensão e do tamanho, antes do claim. Malware avançado e prompt injection ficam fora.
- **FR-012**: Processamento por arquivo no espírito da ADR_048 / Q8: aceitos entram; recusados (duplicata, extensão, tamanho, binário) saem no corpo. 409 somente quando **todos** os arquivos do lote são duplicata. 400/413 quando o lote inteiro cai num desses motivos e nenhum é aceito.
- **FR-013**: Logs novos no Core e no Collector MUST seguir o princípio VI (`event=… | aviso | chave=valor`, `cid`, data com fuso, sem segredo nem MD5 completo).
- **FR-014**: Esta feature atravessa dois repositórios. A spec 011 no Core é a fonte do contrato ponta a ponta. A implementação dos FR de Collector MUST viver num worktree irmão (`lhamalog-collector-llt292`), criado a partir da branch da LLT-290 **depois** que o código da 290 estiver nessa branch (hoje ele ainda está só no worktree `lhamalog-collector-llt290`, sem commit), **sem** modificar esse worktree nem o checkout principal do Collector.
- **FR-015**: Alertas por WebSocket (ADR_047/051/052/055), gestão de papéis, eventos-gateway, Ocorrência/Sugestão, FilaOrdenada/Finder e OpenAPI MUST NOT entrar nesta entrega.
- **FR-016**: O Core MUST encaminhar o corpo em streaming (ou equivalente de baixo buffer) e aplicar timeout explícito no cliente HTTP, compatível com 100 MB: `connectTimeout` curto (5 s) e timeout de leitura/troca de 120 s. Se o Collector não decidir a tempo ou estiver fora, 503/504, nunca 202. 504 = resultado desconhecido.

### Key Entities

- **ManualUploadRequest**: sessão Clerk + `productId` + lista de arquivos (nome, bytes, tamanho, extensão).
- **ManualUploadLote**: agrupamento HTTP dos arquivos de uma requisição (`loteId`, tenant, produto, origem `upload`).
- **ManualUploadFile**: um arquivo do lote (nome preservado, `fileId`, `batchId` no OpenObserve/fila, status aceito/duplicado/recusado, `fileMd5`).
- **CollectorImportRequest**: chamada interna Core → Collector (service token, cid, tenantId, productId, arquivos).
- **FileIdentity (existente, LLT-290)**: `signal=logs` + `source=upload` + fingerprint MD5.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um arquivo válido e autenticado é aceito (202), aparece no OpenObserve e gera exatamente 1 mensagem nova na fila de leitura.
- **SC-002**: O segundo envio do mesmo arquivo, mesmo tenant e produto, responde 409 e a fila permanece com +1 mensagem, não +2.
- **SC-003**: O mesmo conteúdo já visto só em OTLP é aceito no upload (origens diferentes).
- **SC-004**: Arquivo ou requisição acima de 100 MB responde 413 e não entra na fila; extensão inválida ou binário óbvio responde 400 (ou item recusado no 202).
- **SC-005**: Sem JWT Clerk, JWT inválido, produto de outro tenant, sem acesso ou arquivado, o Core recusa (401/403) e o Collector não é chamado (0 requisições no dublê).
- **SC-006**: Sem service token (ou com token errado / `Authorization`) no endpoint interno do Collector, a resposta é 401 e nada é publicado.
- **SC-007**: Um lote misto (válido + duplicado) devolve 202, publica só o válido e lista o duplicado no corpo.
- **SC-008**: Suítes de testes do Core e do Collector (worktree irmão) passam, sem regressão das specs já mergeadas. O teto de 5 MB do OTLP permanece no caminho OTLP.

## Assumptions

- O front (`lhamalog-web`) fará a checagem rápida de extensão/tamanho e enviará o `productId` do card; a UI em si está fora desta implementação.
- `CompanySessionResolver` e `TenantAccessAuthorizationManager` já resolvem tenant e 401/403 de sessão; esta feature reutiliza esse caminho.
- A LLT-290 entrega `FileDeduplication` e `FileSource.UPLOAD` reutilizáveis; esta feature não reinsere a lógica de hash.
- O contrato da fila de leitura exige gravação confirmada no OpenObserve **antes** do `XADD`. O upload MVP grava um documento por arquivo aceito no stream de logs (conteúdo cru + nome), sem o `LogNormalizado` da ADR_016 (ainda pendente).
- O 202 desta entrega substitui o alerta “aceito” da ADR_055; os alertas 2 e 3 (proteção e relatório final) dependem de WebSocket e ficam pós-MVP.
- A decisão B1 da LLT-290 permanece aberta; o upload herda o 409 para claim em andamento até lá.

## Out of Scope

- UI do `lhamalog-web` (além do contrato da API).
- WebSocket, canal de notificação, `/papeis`, eventos-gateway, módulos Ocorrência/Sugestão/Finder/Varredura (FilaOrdenada).
- SDK Java e executável com cron.
- Autenticação do upload com token OTLP de produtor.
- Alterar o limite de 5 MB do caminho OTLP.
- JWT interno assinado da ADR_028.
- Proteção avançada de malware/prompt injection e normalização ADR_016.
- OpenAPI, alteração de ADRs, alteração de Jira, alteração de `.env`.
- Qualquer mudança no worktree `lhamalog-collector-llt290`, no checkout principal do Collector, ou no checkout `lhamalog-core` da LLT-145.

## Documentação irmã

Rascunhos das decisões que divergem de ADRs aceitas (e ADRs novas, ainda sem número) ficam em `planejamento/LLT-292-manual-upload/` no `lhamalog-docs`. Este repositório só aponta o caminho; não inventa números de ADR.

## Riscos

- **LLT-290 ainda não commitada**: o worktree `lhamalog-collector-llt290` tem o pacote `dedup` e a spec 010 só como alterações locais (HEAD ainda é `5fb3160`). O worktree irmão da LLT-292 **não** foi criado agora, para não ramificar de um HEAD incompleto nem copiar arquivos desse worktree. A implementação Collector só começa depois dos commits da 290 na branch `feature/LLT-290-file-hash-dedup` (ou após o merge em Development).
- **B1 aberta**: um segundo upload idêntico enquanto o claim provisório (TTL 10 min) está vivo recebe 409, mesmo que o primeiro ainda não tenha dado `commit`. Herdado da 290; não reabrir aqui.
- **Card LLT-292 desatualizado** (token OTLP, 5 MB, endpoint público no Collector).
- **Heap / proxy / timeout** com 100 MB: o Core e o Collector precisam de streaming e timeout explícito; o teto provisório da requisição reduz, mas não elimina, o risco.
- **Documento cru no OpenObserve**: sem ADR_016, o Finder futuro pode precisar de outro formato; o `schemaVersion` da fila não muda, e o conteúdo fica recuperável por `batchId`.
- **403 vs 404 no restante do Core**: o upload devolve 403 onde `ProductService.get()` devolveria 404; está fechado na Q11 e deve aparecer nos testes e no research.

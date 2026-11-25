# Research: Upload manual (LLT-292)

**Data**: 2026-11-20 (America/Sao_Paulo)

## R1. Destino da fila e OpenObserve

**Decisão**: cada arquivo aceito gera (1) um documento no stream `logs` do OpenObserve com o conteúdo cru e o nome original, e (2) uma mensagem `log.ingested` em `lhamalog:stream:logs`, `schemaVersion` 1, `fileSource=upload`. Sem bump de schema.

**Por quê**: o contrato da spec 002/004 só publica depois da gravação confirmada no OpenObserve, e o corpo da mensagem é uma referência (`openobserve.batchId`), não o conteúdo. A ADR_016 (`LogNormalizado`) ainda está pendente; normalizar linhas/JSON/CSV ficaria fora do MVP e atrasaria a fila. O Finder é pós-MVP, mas o lote precisa ser recuperável por `batchId`.

**Um mensagem por arquivo, não por lote HTTP**: o `idempotencyId` da LLT-290 é `UUIDv3("<batchId>:<signal>:<source>:<md5>")` — um MD5 por arquivo. O `loteId` da requisição só agrupa a resposta 202.

## R2. Cliente HTTP no Core e streaming

**Decisão**: `RestClient` (já usado em `ClerkBackendSessionRevocation` / `IdentityConfig`), com `HttpClient` de timeout configurável e envio multipart, sem carregar o lote duas vezes na heap se o container permitir. Timeout padrão alto o bastante para 100 MB (propriedade, ex. 120 s), mapeado para 504/503. URL base: `lhamalog.manual-upload.collector-url` (local aponta para `http://localhost:8080`).

**Por quê**: não há WebClient obrigatório no pom; o padrão do Core é RestClient. O Core **espera** a decisão (Q12).

## R3. Autorização de produto (403)

**Decisão**: reusar a regra de visibilidade de `ProductService.get()` (admin vê todos os produtos do tenant; demais precisam de `ProductAccess`), **mais** recusa de arquivado, e responder **403** (não 404) para id desconhecido, outro tenant, sem acesso ou arquivado.

**Por quê**: Q11 confirmada. Diverge de `get()` (404 e leitura de arquivado). Fica documentado e testado; não se altera `ProductService.get()`.

## R4. Rate limit e cota no caminho de upload

**Decisão**: no Collector, reusar `IngestionControls` / rate limiter e `QuotaGate` já existentes, depois do claim, por arquivo, com o tamanho real do arquivo (teto 100 MB, não o 5 MB do OTLP). Chave de rate no tenant (ou tenant+produto, o que o limiter atual já usar quando não há token OTLP — se o limiter atual for só por token de produtor, aplicar a mesma janela por `tenantId`). O Core apenas espelha 429/503.

**Por quê**: Q5. Evita furo de plano pelo upload.

## R5. Service token inbound

**Decisão**: filtro no estilo de `UsageFactsSecurityConfiguration` (token constante por header, recusa se vier `Authorization`, comparação em tempo constante, log só com SHA-256 e tamanho). Propriedades/env: `LHAMALOG_COLLECTOR_SERVICE_TOKEN` no Collector e `LHAMALOG_CORE_COLLECTOR_TOKEN` no Core. Sem valor em arquivo.

**Por quê**: Q6 e o código já existente na direção oposta.

## R6. Magic bytes de binário óbvio

**Decisão**: recusar se os primeiros bytes forem `MZ` (PE/Windows), `\x7fELF` (ELF) ou o prefixo Mach-O (`0xFEEDFACE` / `0xFEEDFACF` / `0xCAFEBABE` / `0xCFFAEDFE` / `0xCEFAEDFE`). Não tratar ZIP/`PK` como binário (falso positivo em escritórios compactados, e `.zip` já não está na lista de extensões).

**Por quê**: Q10 pede “binário óbvio” no MVP, sem biblioteca de malware.

## R7. Worktree do Collector e dependência da LLT-290

**Decisão**: não criar `lhamalog-collector-llt292` enquanto a branch `feature/LLT-290-file-hash-dedup` no worktree existente tiver o código só como working tree sujo (HEAD `5fb3160`, pacote `dedup/` untracked). Quando os commits da 290 existirem nessa branch, criar o worktree irmão com `git worktree add -b feature/LLT-292-manual-upload ..\lhamalog-collector-llt292 feature/LLT-290-file-hash-dedup` a partir do repositório, **sem** checkout, stash ou edição em `lhamalog-collector-llt290` nem no checkout principal (que permanece em LLT-186, com `tasks.md` sujo e `stash@{0}`).

**Por quê**: ramificar de `5fb3160` perderia o `FileDeduplication`; copiar do worktree sujo violaria a regra de não tocar nele.

## R8. B1 herdada

**Decisão**: o upload não inventa comportamento próprio para claim em andamento. Continua o SET NX atual (409). Quando a T041 da 290 fechar, o caminho `upload` acompanha. Teste de caracterização: dois uploads iguais simultâneos; documentar o resultado atual (409) como conhecido.

## R9. Multipart 2 MB e Tomcat

**Decisão**: no Core e no Collector, `spring.servlet.multipart.max-file-size=100MB`, `spring.servlet.multipart.max-request-size=100MB`, e `server.tomcat.max-http-form-post-size=100MB` (e `max-swallow-size` se o default cortar o body). Só properties/perfil.

## R10. Portas locais

O Collector já usa `server.port=8080` e chama o Core em `http://localhost:8081` (`lhamalog.usage.core.url`). O cliente do Core deve apontar para 8080, não para a porta do próprio Core. Sem colisão se os perfis locais já separam 8080/8081.

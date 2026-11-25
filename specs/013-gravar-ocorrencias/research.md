# Research: Gravar ocorrências a partir do resumo de análise do Finder

**Feature**: `013-gravar-ocorrencias` | **Data**: 25/11/2026

Fontes: contrato DN-009-2 do Finder (`proposta-dn-009-2.md`, `proposta-dn-009-2.openapi.yaml`, `resumo-analise-handoff.md`, `internal/adapter/out/modocorrencia/cliente.go` do PR #13), spec 012 deste repositório (`data-model.md` e `contracts/ocorrencia-documento.md`), decisões das specs 003, 005, 009 e 010 sobre concorrência sem transação, e a `UsageFactsSecurityConfiguration`.

## R1. Autenticação interna: JWT HS256

- **Decisão**: `SecurityFilterChain` própria (`@Order` antes da chain da Clerk) com `securityMatcher("/internal/v1/**")`, `oauth2ResourceServer().jwt()` e um `JwtDecoder` que aceita HS256 com até dois segredos (`OCCURRENCE_INTERNAL_JWT_SECRETS`, separados por vírgula, cada um com pelo menos 32 bytes). O decoder tenta o segredo atual e depois o anterior. Validadores: `exp` e `nbf` (com folga de 30 s), `iss` igual a `OCCURRENCE_INTERNAL_JWT_ISSUER` (padrão `lhamalog-internal`), `aud` contendo `OCCURRENCE_INTERNAL_JWT_AUDIENCE` (padrão `lhamalog-modulo-ocorrencia`) e `sub` presente. A autorização por operação confere `sub` contra `OCCURRENCE_INTERNAL_JWT_SUBJECTS` (padrão `lhamalog-finder`); outro `sub` válido recebe 403. Só `HS256` é aceito (`alg` diferente, inclusive `none`, é 401).
- **Sem segredo configurado**: a chain sobe e recusa tudo com 401, com WARN na subida (`event=occurrence.auth.not_configured`). O Core não deixa de subir, para não quebrar ambientes que ainda não usam o Módulo.
- **Respostas**: 401 com `WWW-Authenticate: Bearer` e `{"codigo":"NAO_AUTENTICADO"}`; 403 com `{"codigo":"ACESSO_NEGADO"}`. O token da Clerk não passa (emissor e algoritmo diferentes); o JWT interno não passa na chain da Clerk (o `JwtDecoder` dela usa o JWKS da Clerk).
- **Geração do token**: `scripts/InternalJwt.java`, executado com `java scripts/InternalJwt.java --sub lhamalog-finder --days 90` (Java 25, sem dependências: `javax.crypto.Mac`). Lê o segredo de `OCCURRENCE_INTERNAL_JWT_SECRET` e imprime só o token, que vai para `FINDER_MODULO_OCORRENCIA_JWT`. Rotação: acrescentar o segredo novo na frente da lista, gerar o token novo, trocar no Finder, remover o antigo.
- **Motivo**: decisão do Felipe no clarify; o cliente do Finder recebe um token pronto e não o emite, então um segredo compartilhado com tokens de validade longa e rotação por lista resolve sem servidor de chaves. Nimbus já está no classpath (Clerk).
- **Alternativas**: RS256 com JWKS (exige emissor e publicação de chaves, que não existem); token fixo `X-LhamaLog-Service-Token` (foge do contrato Bearer JWT).

## R2. Tudo ou nada sem transação: recibo, diário e desfazer

- **Decisão**: coleção `ReciboResumoAnalise`, um documento por chave de entrega (índice único em `tenantId`, `productId`, `batchId`, `execId`; `execId` ausente gravado como `""`). Fluxo da entrega:
  1. Validar corpo, autenticação e produto do tenant (nada gravado até aqui).
  2. Ler o recibo da chave. `CONFIRMED` → 200 com o recibo original e `repeticao: true` (FR-005, antes de olhar revisões). `PENDING` com prazo vigente → 409 `ENTREGA_EM_ANDAMENTO`. `PENDING` vencido → **recuperar** (passo 7) e seguir como entrega nova.
  3. **Reservar**: inserir o recibo `PENDING` com `attemptId` novo, `reciboId` novo e `leaseUntil = agora + 30 s`. Chave duplicada na inserção (corrida) → reler e responder como no passo 2.
  4. **Conferir**: ler de uma vez as ocorrências das chaves dos achados (`tenantId`, `productId`, `signature ∈ …`) e comparar cada `revisaoConsultada` (`null` exige ausência). Divergência → apagar o recibo (condição `attemptId`) e 409 `REVISAO_DESATUALIZADA`. Mais de um documento para a mesma chave → apagar o recibo, ERROR e 500.
  5. **Diário**: calcular o depois de cada ocorrência (R4) e gravar no recibo a lista `{occurrenceId, expectedRevision, newRevision, before, after}` (condição `attemptId`).
  6. **Aplicar**: para cada item, `replaceOne({_id, revision: expectedRevision}, after)` em existente ou `insert(after)` em nova (o índice único da chave segura a corrida de criação). Uma condição que falhar (outro lote gravou entre 4 e 6) → **desfazer** os itens já aplicados (passo 7) → apagar o recibo → 409 `REVISAO_DESATUALIZADA`.
  7. **Desfazer** (corrida ou recuperação): para cada item do diário, se a ocorrência ainda estiver com `newRevision`, voltar para `before` (`replaceOne` condicional) ou apagar a criada (`deleteOne({_id, revision: newRevision})`). Itens que já não estão com `newRevision` foram mudados depois e ficam como estão (só acontece se a reserva tiver vencido durante o processamento, o que o prazo de 30 s contra o limite de 5 s do cliente torna improvável; é registrado em WARN).
  8. **Confirmar**: `updateOne({_id, attemptId, state: PENDING}, {state: CONFIRMED, confirmedAt, expiresAt: agora + 30 dias, journal: unset})`. Se a condição falhar (reserva perdida), responder 500 sem recibo; o Finder reenvia e a recuperação resolve.
- **Recuperação depois de queda**: uma entrega interrompida deixa o recibo `PENDING` com diário. O próximo envio da chave, depois do prazo, desfaz pelo diário e aplica de novo do zero, com as revisões consultadas originais (que voltam a valer depois do desfazer). Resultado final: uma aplicação completa ou nenhuma (FR-009).
- **Motivo**: decisão do Felipe no clarify; segue o padrão das specs 003, 005, 009 e 010 (escrita condicional, sem replica set). A conferência prévia de todas as revisões responde 409 sem efeito no caso comum; o diário cobre só a corrida estreita entre conferir e gravar e a queda.
- **Visibilidade intermediária**: durante os poucos milissegundos da aplicação, a tela pode ler parte dos achados já gravados. A spec exige tudo ou nada no resultado final, não isolamento de leitura; a tela não depende de consistência entre ocorrências diferentes.
- **Alternativas**: transação Mongo (exige replica set, rejeitada no clarify); aplicar sem diário e confiar no reenvio (deixaria efeito parcial em corrida); trava pessimista por ocorrência (mais escritas e risco de trava órfã).
- **Como é testado**: queda no meio, corrida entre conferir e gravar, recuperação depois do prazo e desfazer que encontra uma ocorrência mudada depois ficam em `AnalysisSummaryDeliveryServiceTest`, com repositórios em memória que têm as mesmas condições do Mongo (um teste de integração não consegue derrubar o Core no meio de uma entrega). A concorrência real (mesma chave em paralelo e dois lotes com a mesma revisão) fica em `AnalysisSummaryDeliveryIntegrationTests`, repetida 20 vezes.

## R3. Revisão

- **Decisão**: `revision` é um UUID novo a cada gravação feita pelo Módulo. Ocorrências da carga local (`<id>-r1`) e de qualquer outra origem valem como revisão opaca, e a conferência é por igualdade de texto. Documento sem `revision` (gravado fora do Módulo) é lido como revisão `sem-revisao`: a consulta devolve esse valor e a entrega o aceita para esse documento, que passa a ter um UUID na primeira gravação.
- **Motivo**: o contrato só exige que a revisão mude quando o estado muda; UUID evita colisão entre lotes.

## R4. Como o achado vira ocorrência (OccurrenceMerge)

- **Decisão**: função pura `(antes | null, achado, resumo, agora, newRevision) → depois`, seguindo as regras 1 a 9 de `contracts/ocorrencia-documento.md` da spec 012:
  - Nova: `_id` UUID, `status` ABERTA, `statusHistory` = `[{from: null, to: ABERTA, changedAt: agora, changedBy: {SYSTEM, "modulo-ocorrencia"}}]`.
  - Existente FECHADA: `status` ABERTA e `statusHistory` + `{FECHADA → ABERTA}`. ABERTA ou EM_ANALISE: status e histórico iguais. Status desconhecido (documento inválido para a LLT-135): vira ABERTA com `{from: <valor>, to: ABERTA}`.
  - `count`, `firstSeenAt`, `lastSeenAt`, `recurring`, `rulesVersion` (do resumo): valores do achado.
  - `title`, `summary`, `hostName`, `traceId`, `correlationId`, `moduleId`, `lineNumber`, `extractedFields`, `sensitiveData`: valor do achado quando não `null`; senão o anterior. `extractedFields` é substituído por inteiro quando vem (não mesclado), como "valor do último achado".
  - `samples`: anteriores + `linhas`, mantendo as 20 últimas; `linhas: []` não muda.
  - `revision`: `newRevision`.
- **Contagem menor que a anterior**: grava como veio e registra WARN `event=occurrence.delivery.count_decreased` (o Finder é a fonte da soma).
- **Motivo**: função pura testável sem banco (`OccurrenceMergeTest`); as regras já estão no contrato aceito pela tela.

## R5. Validação e formato de erro

- **Decisão**: o corpo é lido como árvore JSON (`JsonNode`) e conferido por `InternalOccurrenceRequests` (camada `api`), campo a campo, com lista de campos permitidos e obrigatórios. Ajuste da implementação (25/11): `@JsonIgnoreProperties(ignoreUnknown = false)` não força a recusa de campos a mais, porque o Spring Boot desliga `FAIL_ON_UNKNOWN_PROPERTIES`; a leitura manual devolve os códigos do contrato e nunca o 400 genérico. JSON ilegível ou raiz que não é objeto → 400 `REQUISICAO_INVALIDA`; qualquer outro problema num JSON legível (campo a mais ou ausente, tipo JSON errado, regra do contrato) → 422 `DADOS_INVALIDOS`, como diz o OpenAPI ("JSON legível com campos ou invariantes do contrato inválidos"). Mais de 1.000 achados → 422 `RESUMO_GRANDE_DEMAIS`. O limite de 5 MB do corpo não foi implementado: o de 1.000 achados já protege o Core.
- **Regras**: horários `^…Z$` e parseáveis; `contagem` ≥ 1; `numeroLinha` ≥ 1 quando presente; `execId` no padrão do contrato; `tipo` em ERROR/WARNING/INFO; `primeiraOcorrencia` ≤ `ultimaOcorrencia`; identificadores não vazios; `linhasProcessadas = erros + warnings + informacao`; `identificacaoRegras.versaoUnica = versaoRegras` quando presente; `revisaoConsultada` nula ou não vazia; nenhuma chave (assinatura, tipo) repetida no resumo.
- **Formato**: `{"codigo": "..."}` num `@RestControllerAdvice(assignableTypes = InternalOccurrenceController.class)`; o `ApiExceptionHandler` geral não muda. Nenhuma mensagem leva assinatura, trecho de log ou token.

## R6. Produto do tenant

- **Decisão**: `ProductRepository.findByIdAndTenantId(productId, tenantId)`; ausente → 403 `ACESSO_NEGADO` (mesma resposta para produto de outro tenant, tenant inexistente e produto inexistente). Produto arquivado vale. O status do tenant não bloqueia.
- **Motivo**: o produto já carrega o `tenantId`; não há porta de leitura de tenant por id e ela não é necessária.

## R7. Retenção dos recibos

- **Decisão**: `expiresAt = confirmedAt + 30 dias` (`lhamalog.occurrence.delivery.receipt-retention=30d`) e índice TTL `expireAfterSeconds: 0` em `expiresAt`. Recibos `PENDING` não têm `expiresAt` e não expiram (são resolvidos pelo próximo envio da chave).
- **Motivo**: clarify de 25/11.

## R8. Desempenho

- **Decisão**: uma leitura em lote das ocorrências do resumo (`signature $in` dentro de tenant e produto, coberta pelo índice `occurrence_unique_key`), uma gravação do diário, uma escrita por achado e a confirmação. Para 100 achados: cerca de 103 operações, dentro de 2 s no local.
- **Alternativa**: `bulkWrite` ordenado (menos idas ao banco, mas perde a detecção por item da condição de revisão; fica para depois se o tempo medido pedir).

## R9. Homologação com o Finder

- **Decisão**: quickstart com o Core local, segredo de teste em `.env` local, token gerado pelo script e o Finder com `FINDER_MODULO_OCORRENCIA_URL`. O cliente do Finder só monta `https://`; no local, um proxy TLS na frente do Core (por exemplo `caddy reverse-proxy --from https://localhost:8443 --to :8080`) ou um certificado de desenvolvimento. Os cinco cenários do contrato ficam no roteiro, junto com o João.

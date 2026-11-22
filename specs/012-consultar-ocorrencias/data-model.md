# Data Model: Consultar ocorrências

**Feature**: `012-consultar-ocorrencias` | **Data**: 19/11/2026 | **Revisado**: 22/11/2026 (contrato DN-009-2 aprovado)

## Ocorrência (coleção `Ocorrencia`, gravada pelo Módulo Ocorrência)

Os campos seguem o `AchadoAnalise` do contrato aprovado do Finder (`proposta-dn-009-2.openapi.yaml`), traduzidos para os nomes em inglês do Core. A coluna "Origem" mostra de onde o Módulo tira cada valor.

| Campo | Tipo | Obrig. | Origem no contrato do Finder | Descrição |
|---|---|---|---|---|
| `_id` | string | sim | gerado pelo Módulo | Identificador opaco (UUID) |
| `tenantId` | string | sim | `ResumoAnalise.tenantId` | Tenant interno do Core |
| `productId` | string | sim | `ResumoAnalise.productId` | `_id` do `Produto` |
| `signature` | string | sim | `assinatura` | `nível\|mensagem\|serviço\|host` (DN-008-8 A, `\|` escapado como `\\|`) |
| `type` | string | sim | `tipo` | `ERROR`, `WARNING` ou `INFO` |
| `title` | string | não | `titulo` | Título próprio; ausente → o Core usa a mensagem da assinatura |
| `summary` | string | não | `resumo` | Resumo da ocorrência, já mascarado |
| `status` | string | sim | Módulo | `ABERTA`, `EM_ANALISE` ou `FECHADA` (ADR_082) |
| `recurring` | boolean | não | `recorrente` | Ausente = `false` |
| `count` | long | sim | `contagem` | Total já somado pelo Finder |
| `firstSeenAt` | date | sim | `primeiraOcorrencia` | UTC |
| `lastSeenAt` | date | sim | `ultimaOcorrencia` | UTC |
| `samples` | array de string | não | `linhas` | Trechos mascarados; o Módulo guarda os 20 mais recentes, do mais antigo para o mais novo |
| `hostName` | string | sim | `hostName` | Host do log |
| `traceId` | string | não | `traceId` | |
| `correlationId` | string | não | `correlationId` | |
| `moduleId` | string | não | `moduloId` | |
| `lineNumber` | integer | não | `numeroLinha` | Base 1 |
| `extractedFields` | objeto nome → texto | não | `camposExtraido` | Já mascarados |
| `sensitiveData` | boolean | não | `dadosSensiveis` | `null` = desconhecido |
| `rulesVersion` | string | não | `ResumoAnalise.versaoRegras` | Última versão das regras que tocou a ocorrência |
| `revision` | string | sim | Módulo | Revisão opaca da consulta (concorrência DN-009-2); o Core não usa |
| `statusHistory` | array de `StatusChange` | não | Módulo | Mudanças de status em ordem cronológica |

Regras de leitura:

- Válido para a lista: `type` e `status` com valores conhecidos e `productId`, `signature`, `count`, `firstSeenAt` e `lastSeenAt` presentes.
- Título exibido: `title`; sem ele, a segunda parte da `signature` (separada por `|` não escapado, com `\|` desfeito); se a assinatura não tiver quatro partes, a assinatura inteira.
- Serviço exibido: terceira parte da `signature`, quando ela tiver quatro partes.
- Uma ocorrência por (`tenantId`, `productId`, `signature`, `type`), garantida por índice único.

### StatusChange

| Campo | Tipo | Descrição |
|---|---|---|
| `from` | string ou null | Status anterior; `null` na criação |
| `to` | string | Status novo |
| `changedAt` | date | Data da mudança |
| `changedBy` | objeto | `{type: "USER" | "SYSTEM", id: <clerkUserId ou "modulo-ocorrencia">}` |

### Índices

| Nome | Definição | Uso |
|---|---|---|
| `occurrence_tenant_product_last_seen` | `{tenantId: 1, productId: 1, lastSeenAt: -1, _id: -1}` | lista e ordenação |
| `occurrence_unique_key` | `{tenantId: 1, productId: 1, signature: 1, type: 1}`, único | uma ocorrência por chave |

## Atributos devolvidos no detalhe

Lista `{name, value}` em ordem alfabética, montada a partir do documento:

| `name` | Fonte |
|---|---|
| `host.name` | `hostName` |
| `service.name` | terceira parte da `signature` |
| `trace_id` | `traceId` |
| `correlation_id` | `correlationId` |
| `module_id` | `moduleId` |
| `line_number` | `lineNumber` |
| cada chave de `extractedFields` | valor correspondente |

Atributo sem valor fica fora da lista.

## Classes no Core (somente leitura)

- `OccurrenceDocument` (`infrastructure`): mapeamento da coleção, com `StatusChange` aninhado.
- `OccurrenceSignature` (`application`): separa as partes da assinatura respeitando o escape `\|`.
- `OccurrenceType` e `OccurrenceStatus` (`application`): enums dos valores válidos.
- `OccurrenceFilter` (`application`): página, tamanho, tipos, status, produtos, início, fim e texto, já validados. Corresponde a `FiltroOcorrenciaEmpresarial`.
- `OccurrenceQueryRepository` (`application`, porta) e `MongoOccurrenceQueryRepository` (`infrastructure`): `page(tenantId, visibleProductIds, filter)` e `findById(tenantId, id)`.
- `OccurrenceService` (`application`): resolve os produtos visíveis pelo `ProductService`, valida o filtro de produto, chama o repositório e monta as visões (título, atributos, exemplos).
- `OccurrenceController` (`api`) e `OccurrenceResponses` (`api`): rotas e formatos de resposta.
- `LocalOccurrenceSampleSeeder` (`infrastructure`, só perfil `local`): carga de exemplo.

## Estados

O Core não muda o estado. Transições ABERTA → EM_ANALISE → FECHADA e a reabertura FECHADA → ABERTA são do Módulo (finder 008-4 B) e da LLT-206.

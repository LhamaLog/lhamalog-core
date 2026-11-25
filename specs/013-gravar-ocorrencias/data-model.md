# Data Model: Gravar ocorrências a partir do resumo de análise do Finder

**Feature**: `013-gravar-ocorrencias` | **Data**: 25/11/2026

## Ocorrência (coleção `Ocorrencia`)

Sem mudança de formato: é o documento de `specs/012-consultar-ocorrencias/data-model.md`. Esta feature passa a gravá-lo. Mapeamento do achado (contrato DN-009-2) para o documento:

| Achado / resumo | Documento | Regra |
|---|---|---|
| `ResumoAnalise.tenantId`, `productId` | `tenantId`, `productId` | chave; produto conferido no tenant |
| `assinatura`, `tipo` | `signature`, `type` | chave; gravados como vieram |
| `contagem` | `count` | sempre o valor do achado (já somado) |
| `primeiraOcorrencia`, `ultimaOcorrencia` | `firstSeenAt`, `lastSeenAt` | sempre os valores do achado |
| `recorrente` | `recurring` | sempre o valor do achado |
| `ResumoAnalise.versaoRegras` | `rulesVersion` | sempre o valor do resumo |
| `titulo`, `resumo` | `title`, `summary` | só quando não `null` |
| `hostName` | `hostName` | sempre (obrigatório no contrato) |
| `traceId`, `correlationId`, `moduloId`, `numeroLinha` | `traceId`, `correlationId`, `moduleId`, `lineNumber` | só quando presentes |
| `camposExtraido` | `extractedFields` | substitui o anterior quando não `null`; nomes com ponto gravados como vieram |
| `dadosSensiveis` | `sensitiveData` | só quando não `null` |
| `linhas` | `samples` | anexadas no fim; ficam as 20 últimas; `[]` não muda |
| — | `status` | nova = `ABERTA`; `FECHADA` → `ABERTA`; demais iguais |
| — | `statusHistory` | criação e reabertura com `changedBy = {SYSTEM, "modulo-ocorrencia"}` |
| — | `revision` | UUID novo a cada gravação |
| — | `_id` | UUID na criação |

Gravação com `MongoTemplate` e `org.bson.Document` (o `insert` mapeado recusa chave de mapa com ponto; ver T015 da spec 012).

## Recibo de entrega (coleção `ReciboResumoAnalise`)

| Campo | Tipo | Descrição |
|---|---|---|
| `_id` | string | UUID do documento |
| `tenantId` | string | chave |
| `productId` | string | chave |
| `batchId` | string | chave |
| `execId` | string | chave; `""` quando o resumo não traz `execId` |
| `reciboId` | string | UUID opaco devolvido ao Finder; estável para a chave |
| `state` | string | `PENDING` (em processamento) ou `CONFIRMED` |
| `attemptId` | string | UUID da tentativa dona da reserva; condição de todas as escritas no recibo |
| `leaseUntil` | date | fim da reserva (`agora + 30 s`); `PENDING` vencido é recuperado |
| `createdAt` | date | início da tentativa |
| `confirmedAt` | date | quando virou `CONFIRMED` |
| `expiresAt` | date | `confirmedAt + 30 dias`; só em `CONFIRMED` (TTL) |
| `findingCount` | int | quantidade de achados aplicados (para log e suporte) |
| `journal` | array de `JournalEntry` | só em `PENDING`; removido na confirmação |

### JournalEntry

| Campo | Tipo | Descrição |
|---|---|---|
| `occurrenceId` | string | `_id` da ocorrência |
| `expectedRevision` | string ou null | revisão antes da gravação; `null` = ocorrência criada por esta entrega |
| `newRevision` | string | revisão gravada por esta entrega |
| `before` | documento ou null | ocorrência antes (para desfazer); `null` na criação |
| `after` | documento | ocorrência depois |

### Índices

| Nome | Definição | Uso |
|---|---|---|
| `delivery_receipt_key` | `{tenantId: 1, productId: 1, batchId: 1, execId: 1}`, único | uma entrega por chave; reserva atômica |
| `delivery_receipt_expiry` | `{expiresAt: 1}`, `expireAfterSeconds: 0` | retenção de 30 dias dos confirmados |

### Estados

```text
(nenhum) --reservar--> PENDING --conferir ok, aplicar, confirmar--> CONFIRMED --30 dias--> (removido)
                          |  revisão desatualizada ou corrida: desfazer + apagar  --> (nenhum)  [409]
                          |  queda: fica PENDING até o prazo; próximo envio desfaz e recomeça
```

## Configuração

| Propriedade | Variável | Padrão |
|---|---|---|
| `lhamalog.occurrence.internal-auth.secrets` | `OCCURRENCE_INTERNAL_JWT_SECRETS` | vazio (rotas internas recusam tudo) |
| `lhamalog.occurrence.internal-auth.issuer` | `OCCURRENCE_INTERNAL_JWT_ISSUER` | `lhamalog-internal` |
| `lhamalog.occurrence.internal-auth.audience` | `OCCURRENCE_INTERNAL_JWT_AUDIENCE` | `lhamalog-modulo-ocorrencia` |
| `lhamalog.occurrence.internal-auth.subjects` | `OCCURRENCE_INTERNAL_JWT_SUBJECTS` | `lhamalog-finder` |
| `lhamalog.occurrence.delivery.lease` | — | `30s` |
| `lhamalog.occurrence.delivery.receipt-retention` | — | `30d` |
| `lhamalog.occurrence.delivery.max-findings` | — | `1000` |
| `lhamalog.occurrence.delivery.max-samples` | — | `20` |

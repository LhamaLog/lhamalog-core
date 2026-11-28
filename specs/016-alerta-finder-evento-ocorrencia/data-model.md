# Data Model: Alerta do Finder e aviso de ocorrência recorrente

**Feature**: `specs/016-alerta-finder-evento-ocorrencia` | **Data**: 2026-11-26

**Base**: pilha #19–#24 assumida mergeada. Este modelo só descreve o acréscimo. Campos já gravados pelo #20/#24 não são redesenhados, salvo `gravidade` e a leitura de `recurring` que esta feature passa a usar. Fontes de cada bloco estão na linha seguinte ao título.

Não há transição de estado da ocorrência além da que o #20/#21/#24 já gravam. Esta feature observa o journal `before`/`after` e publica um aviso. (fonte: spec.md, FR-007 e FR-008)

## Alerta do Finder

(fonte: spec.md, Key Entities e FR-001 a FR-004 e FR-014; respostas-pendentes-llt201.md, pendências 4, 7 e 8; decisoes-joao-llt201.md, pendências 1, 9 e 15, formulário 1; research.md, R3, R5, R6 e R7)

Aceite durável no pacote `finderalert` (exemplo `br.com.lhamalog.core.finderalert`). Uma linha por chave. A segurança não é atributo deste documento: continua na cadeia `/internal/v1/**` do #20.

| Campo | Onde | Regra |
|---|---|---|
| `tenantId` | pedido, resposta e gravação | parte da chave; devolvido igual ao pedido |
| `productId` | pedido, resposta e gravação | parte da chave; devolvido igual ao pedido. Se não pertence ao `tenantId`, a rota responde 403 `{"codigo":"ACESSO_NEGADO"}` e não grava |
| `batchId` | pedido, resposta e gravação | parte da chave; devolvido igual ao pedido |
| `motivo` | pedido, resposta e gravação | parte da chave. O único valor aceito é `consulta_openobserve_indisponivel` |
| `aceito` | só a resposta de sucesso | `true`. Não é coluna obrigatória do documento: a spec pede o campo na resposta, não um atributo persistido com esse nome |

Não entra na resposta: `repeticao`, `reciboId`, gravidade, ou qualquer outro nome. (fonte: spec.md, FR-002; respostas-pendentes-llt201.md, pendência 8)

**Validação do pedido**, antes da gravação (fonte: respostas-pendentes-llt201.md, pendência 4; origem: DF-FINDER §3 + padrão do #20, spec 013; analyze-016.md, LOW I3 — o 400 usa o mesmo envelope `{"codigo":...}` do 422):

| Situação | Status | Corpo | Grava |
|---|---|---|---|
| JSON ilegível | 400 | `{"codigo":"REQUISICAO_INVALIDA"}` | não |
| Corpo incompleto, campo a mais ou motivo fora da lista | 422 | `{"codigo":"DADOS_INVALIDOS"}` | não |
| `productId` não pertence ao `tenantId` | 403 | `{"codigo":"ACESSO_NEGADO"}` | não |

Não há outros códigos nesta rota.

**Chave**: `(tenantId, productId, batchId, motivo)`, única. Índice único no Mongo, no estilo de `occurrence_unique_key` em `OccurrenceDocument`. Sem transação multi-documento. (fonte: spec.md, FR-003; research.md, R5; `git show origin/LLT-137-atualizacao-em-tempo-real:src/main/java/br/com/lhamalog/core/occurrence/infrastructure/OccurrenceDocument.java`)

**Coleção**: `finder_alerts`, distinta da coleção do recibo e de `Ocorrencia`. (fonte: respostas-pendentes-llt201.md, pendência 7; decisoes-joao-llt201.md, Rodada 3)

**Estado**:

| De | Para | Quando | HTTP |
|---|---|---|---|
| ausente | um documento | primeira aceitação da chave | 201 |
| um documento | o mesmo documento | reenvio da mesma chave | 200, mesma identidade, sem segunda gravação |
| corrida de duas primeiras | um documento | quem grava responde 201; quem perde a corrida relê e responde 200 | um documento só |

Não há estado “em processamento”. (fonte: decisoes-joao-llt201.md, pendência 9, formulário 1; respostas-pendentes-llt201.md, pendências 8 e 9)

**Depois da primeira gravação (201)**: a tela é avisada no canal WebSocket `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`. A repetição (200) não avisa de novo. Não há evento novo nem envelope para a Notificação, e este alerta não entra no canal `lhamalog:occurrence-events` nem no stream `lhamalog:notifications:events`. (fonte: decisoes-joao-llt201.md, pendência 1, formulário 1, e Rodada 3; spec.md, FR-006; analyze-016.md, LOW A1)

## Credencial interna do alerta

(fonte: spec.md, Key Entities e FR-005; decisoes-joao-llt201.md, pendências 6 e 20, formulário 1; respostas-pendentes-llt201.md, resumo, fato de código; spec 013, data-model de configuração, no `git show`)

Não é documento Mongo. É um JWT HS256 com o segredo e o emissor do #20. Um token só, cujo `aud` contém as duas audiências.

| Claim / dado | Regra |
|---|---|
| segredo | o mesmo de `lhamalog.occurrence.internal-auth.secrets` (ambiente `OCCURRENCE_INTERNAL_JWT_SECRETS`); mínimo 32 bytes, como o filtro do #20 já exige |
| `iss` | o mesmo emissor; padrão `lhamalog-internal` |
| `sub` | a mesma lista; padrão `lhamalog-finder`. `sub` fora da lista continua 403 na cadeia existente |
| `aud` | contém a audiência do Módulo e `lhamalog-core` |
| rota de alerta | exige `lhamalog-core`, lida da propriedade `core-audience`, usada só nesta rota. Ilustração de caminho, não nome extra decidido: `lhamalog.occurrence.internal-auth.core-audience` |
| rotas de consulta e de resumo | continuam exigindo a audiência do #20 (padrão `lhamalog-modulo-ocorrencia`) |
| token de sessão de usuário | não autentica esta rota |

Fato de código: no #20, `InternalServiceSecurityConfiguration` cobre todo `/internal/v1/**` e exige que o `aud` contenha `lhamalog.occurrence.internal-auth.audience`; a conferência é por "contém". Sem o ajuste por rota, um token só com `lhamalog-core` recebe 401. `scripts/InternalJwt.java` gera um token com uma única audiência (`--aud`). (fonte: respostas-pendentes-llt201.md, resumo, fato de código)

## Ocorrência (já existente; o que esta feature acrescenta)

(fonte: spec.md, Key Entities, FR-008 e FR-009; decisoes-joao-llt201.md, pendências 3 e 5, formulário 1; respostas-pendentes-llt201.md, pendências 2 e 11; `OccurrenceDocument` e `OccurrenceMerge` no `git show`)

Coleção `Ocorrencia`. Chave única já existente: `(tenantId, productId, signature, type)`. Status `ABERTA`, `EM_ANALISE`, `FECHADA`, mais arquivamento. O atributo recorrente é o campo `recurring` (booleano), separado do status. `OccurrenceMerge` copia `finding.recurring()` para `recurring` em toda entrega aceita. Esta feature não muda essa escrita. Ausente ou `null` conta como `false`. (fonte: respostas-pendentes-llt201.md, pendência 11; origem: data-model da spec 012, “Ausente = `false`”)

| Campo | Uso nesta feature |
|---|---|
| `recurring` antes | não é TRUE (ausente ou `null` contam como `false`) |
| `recurring` depois | TRUE nessa mesma entrega 201 |
| `type` | permanece `ERROR`, `WARNING` ou `INFO` |
| `gravidade` | recebe a mesma classe (`ERROR`, `WARNING` ou `INFO`). O tipo não é substituído por este campo |
| `status` / `archivedAt` | continuam definindo `CREATED` e `REOPENED` como o #24 já faz. Esta feature não altera essas duas regras |

Criticidade não entra. A escala "critico"/"alto" da ADR_062 fica em conflito registrado; vale gravidade = classe. (fonte: decisoes-joao-llt201.md, pendência 3, formulário 1; respostas-pendentes-llt201.md, conflito 3)

**Transição que esta feature passa a observar** (um aviso por ocorrência por entrega; precedência nova > reaberta > virou recorrente):

| Condição fechada na entrega 201 | Aviso |
|---|---|
| `before` nulo, inclusive se já nasce recorrente | só `CREATED` |
| já existia, estava oculta e volta a `ABERTA`, inclusive se a mesma entrega também a torna recorrente | só `REOPENED` |
| já existia, esta entrega não cria nem reabre, `before.recurring` não é TRUE e `after.recurring` é TRUE | `RECURRENT` |
| já aberta e a entrega só muda contagem, sem a passagem acima | nenhum aviso |
| entrega recusada ou recibo repetido (200 do #20) | nenhum aviso |

(fonte: decisoes-joao-llt201.md, pendência 5, formulário 1; elaboração: respostas-pendentes-llt201.md, pendência 5; spec.md, FR-007 e FR-008)

## Aviso de ocorrência (mensagem já existente)

(fonte: spec.md, Key Entities e FR-006, FR-007 e FR-009; respostas-pendentes-llt201.md, pendências 2, 10 e 16; spec 015, data-model e `contracts/websocket.md`; `RedisOccurrenceEventPublisher.toMessage` e `OccurrenceConnections.dispatch` no `git show`)

Nada disso é gravado no Mongo. O #24 já define as duas representações. Esta feature acrescenta o valor `RECURRENT` em `change` e o campo `gravidade`. A spec 015, FR-007, ainda não inclui a gravidade; a emenda está listada e a 015 não foi editada. (fonte: respostas-pendentes-llt201.md, pendência 2 e conflito 2; decisoes-joao-llt201.md, Rodada 3)

### Redis pub/sub, canal `lhamalog:occurrence-events`

| Campo | Tipo | Regra |
|---|---|---|
| `tenantId` | string | só para rotear entre instâncias; a tela não recebe |
| `productId` | string | produto |
| `occurrenceId` | string | `_id` da ocorrência |
| `occurrenceType` | string | `ERROR`, `WARNING` ou `INFO` (o que o documento já tem em `type`) |
| `change` | string | `CREATED`, `REOPENED` ou `RECURRENT` |
| `gravidade` | string | a mesma classe do campo `gravidade` do documento |
| `at` | data | instante em que a entrega foi confirmada, como o #24 já envia |

### WebSocket `/ws/occurrences`, depois do `ready` — mensagem de ocorrência

Este quadro é a mensagem de tipo `occurrence`. O mesmo caminho também leva, na primeira gravação do alerta (201), a mensagem de tipo `FINDER_ALERT` com corpo `{tenantId, productId, batchId, motivo}`, descrita em Alerta do Finder. A repetição (200) não a reenvia. Essa mensagem não entra no canal Redis `lhamalog:occurrence-events`. (fonte: decisoes-joao-llt201.md, Rodada 3; spec.md, FR-006)

| Campo | Regra |
|---|---|
| `type` | `occurrence` |
| `change` | o mesmo `name()` do enum |
| `occurrenceId`, `productId`, `occurrenceType`, `at` | os mesmos do #24 |
| `gravidade` | a mesma classe, igual à do documento e à da mensagem Redis |

Sem título, assinatura, trecho de log ou `tenantId` na mensagem da tela (fonte: spec 015, FR-007, no que essa spec já lista; spec.md, Assumptions). O acréscimo de `gravidade` é a emenda pendente da 015, aplicada no aviso desta feature e não escrita na spec 015.

O aviso "virou recorrente" sai em até 3 s depois do 201. (fonte: respostas-pendentes-llt201.md, pendência 16; origem: spec 015, SC-001)

**Estado da conexão** (inalterado, spec 015): `PENDING` até `auth`; `READY` depois. Esta feature não muda fechamentos 4401, 4403 e 1001.

## O que este modelo não cria

- Envelope ADR_146, `eventId`, stream `lhamalog:notifications:events` ou `notificacao-eventos` (fonte: decisoes-joao-llt201.md, evento para a Notificação e pendência 1, formulário 1; spec.md, FR-006).
- Segunda versão de `ResumoAnalise`, `AchadoAnalise` ou `Confirmacao` (fonte: spec.md, FR-010; contrato DN-009-2 citado na spec 013).
- Outro literal de log além de `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected`. Esses quatro não são entidade deste modelo. (fonte: decisoes-joao-llt201.md, Rodada 4)

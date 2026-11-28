# Contrato: acréscimo no aviso de ocorrência do #24

**Feature**: `specs/016-alerta-finder-evento-ocorrencia` | **Data**: 2026-11-26

Este arquivo não substitui `specs/015-tempo-real-ocorrencias/contracts/websocket.md`. Esse contrato continua valendo para a conexão, o `auth`, o `ready`, os fechamentos e os valores `CREATED` e `REOPENED`. Aqui está só o que a LLT-201 acrescenta. (fonte: decisoes-joao-llt201.md, evento para a Notificação; spec.md, FR-006 e Assumptions)

Este aviso de ocorrência não ganha canal novo nem envelope ADR_146. O tipo da mensagem de ocorrência continua `occurrence`. O stream `lhamalog:notifications:events` não é o transporte deste aviso. A mensagem `FINDER_ALERT` do alerta do Finder está no contrato `alerta-finder.md`. (fonte: spec.md, FR-006; decisoes-joao-llt201.md, Rodada 3)

A spec 015, FR-007, ainda descreve o aviso sem gravidade. Este contrato acrescenta `gravidade` porque a pendência 2 fecha o aviso com esse campo. A emenda da 015 fica listada no `plan.md` e a 015 não é editada aqui. (fonte: respostas-pendentes-llt201.md, pendência 2 e conflito 2; origem: 009-5 revisto)

## Quando publica

Só depois que `POST /internal/v1/resumos-analise` confirma a entrega pela primeira vez (201) e o journal dessa entrega mostra a condição. Há um aviso por ocorrência por entrega, com a mudança mais forte: nova > reaberta > virou recorrente. Entrega recusada ou repetida (200 com o recibo já emitido) não publica, inclusive “virou recorrente”. (fonte: spec.md, FR-007; spec 015, FR-005; decisoes-joao-llt201.md, pendência 5, formulário 1; elaboração: respostas-pendentes-llt201.md, pendência 5; `AnalysisSummaryDeliveryService` no `git show`: `notifyScreens` só no caminho confirmado)

| Condição na entrega 201 | `change` |
|---|---|
| ocorrência criada (`before` nulo), mesmo se já nasce recorrente | só `CREATED` |
| passou a `ABERTA` vindo de outro status ou de arquivada, mesmo se a mesma entrega também a torna recorrente | só `REOPENED` |
| já existia, esta entrega não cria nem reabre, `before.recurring` não é TRUE e `after.recurring` é TRUE | `RECURRENT` |
| só aumenta contagem de ocorrência já aberta, sem as passagens acima | não publica |

Ausente ou `null` em `recurring` conta como `false`. (fonte: respostas-pendentes-llt201.md, pendência 11; origem: data-model da spec 012)

O aviso "virou recorrente" sai em até 3 s depois do 201. (fonte: respostas-pendentes-llt201.md, pendência 16; origem: spec 015, SC-001)

Falha do Redis não falha a entrega. O aviso pode se perder, como o #24 já admite. (fonte: spec 015, casos de borda e FR-008; spec.md, Edge Cases)

O literal de "virou recorrente" é `RECURRENT`, distinto de `CREATED` e de `REOPENED`. (fonte: decisoes-joao-llt201.md, Rodada 3; respostas-pendentes-llt201.md, pendência 10; origem: constituição IV + enum `Change` do #24)

## Redis

Canal: `lhamalog:occurrence-events` (`lhamalog.occurrence.stream.channel`).

```json
{
  "tenantId": "tenant",
  "productId": "product",
  "occurrenceId": "id",
  "occurrenceType": "ERROR",
  "gravidade": "ERROR",
  "change": "CREATED",
  "at": "2026-11-26T17:00:00Z"
}
```

`change` é `CREATED`, `REOPENED` ou `RECURRENT`. `gravidade` é a classe, igual ao campo do documento, e o `occurrenceType` permanece. Os nomes dos outros campos são os de `RedisOccurrenceEventPublisher.toMessage` no `git show`. O listener só aceita `change` que exista no enum `OccurrenceEvent.Change`. (fonte: decisoes-joao-llt201.md, pendência 3, formulário 1, e Rodada 3; respostas-pendentes-llt201.md, pendência 2)

O exemplo acima usa `CREATED` porque esse literal já existia no #24. A mudança "virou recorrente" usa `"change": "RECURRENT"`.

## WebSocket

`/ws/occurrences`. Mensagem para a conexão `READY` da empresa que vê o produto:

```json
{
  "type": "occurrence",
  "change": "CREATED",
  "occurrenceId": "id",
  "productId": "product",
  "occurrenceType": "ERROR",
  "gravidade": "ERROR",
  "at": "2026-11-26T17:00:00Z"
}
```

Fonte do formato atual: spec 015, `contracts/websocket.md`, e `OccurrenceConnections.dispatch` no `git show`. `tenantId` não vai para a tela. Não entram título, assinatura nem trecho de log (spec 015, FR-007). `gravidade` é o acréscimo desta feature, a mesma classe do documento. (fonte: spec.md, FR-009)

## Fora deste contrato

- Aviso gerado pela rota `POST /internal/v1/alertas/finder`. Esse alerta, na primeira gravação (201), usa o mesmo caminho `/ws/occurrences` com tipo `FINDER_ALERT` e corpo `{tenantId, productId, batchId, motivo}`, e não publica neste canal Redis. O contrato dessa mensagem está em `alerta-finder.md`. (fonte: decisoes-joao-llt201.md, pendência 1, formulário 1, e Rodada 3; spec.md, FR-006)

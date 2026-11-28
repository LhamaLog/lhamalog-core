# Quickstart: validar o alerta do Finder e o aviso “virou recorrente”

**Feature**: `specs/016-alerta-finder-evento-ocorrencia` | **Data**: 2026-11-26

Guia de validação da rodada 3. Não é implementação, não é suíte de teste e não altera compose, README nem `.env.example`. Esses arquivos estão em `plan.md`, seção “Pendências — documentos necessários”.

Contratos: [alerta-finder.md](./contracts/alerta-finder.md), [aviso-ocorrencia.md](./contracts/aviso-ocorrencia.md). Modelo: [data-model.md](./data-model.md).

## Pré-requisitos

- Core com a pilha #19, #20, #21 e #24 já mergeada. Esta validação não recria as rotas de consulta e de resumo nem o WebSocket de ocorrência. (fonte: decisoes-joao-llt201.md, base da branch; spec.md, FR-010)
- MongoDB e Redis do ambiente local do Core. Sem replica set, a deduplicação do alerta não pode depender de transação. (fonte: contexto-llt201.md, §2)
- Segredo do JWT só em ambiente (`OCCURRENCE_INTERNAL_JWT_SECRETS`), nunca no repositório. Emissor e lista de `sub` iguais aos do #20. Um token cujo `aud` contém a audiência do Módulo e `lhamalog-core`. A rota de alerta lê `lhamalog-core` da propriedade `core-audience`. As rotas do Módulo continuam na audiência do #20. O script já existente `scripts/InternalJwt.java` lê o segredo do ambiente e aceita um `--aud`. (fonte: spec.md, FR-005; constituição, V; decisoes-joao-llt201.md, pendências 6 e 20, formulário 1; respostas-pendentes-llt201.md, resumo, fato de código)
- Finder apontando a base em `https://`, sem caminho na base. No local, o Caddy no compose do Core fica na frente do Core, que continua em HTTP na porta 8080. O Finder só recebe essa URL. (fonte: decisoes-joao-llt201.md, pendência 13, formulário 2; respostas-pendentes-llt201.md, pendência 17; spec.md, FR-011)
- A coleção do alerta é `finder_alerts`. O `change` de "virou recorrente" é `RECURRENT`. (fonte: decisoes-joao-llt201.md, Rodada 3)
- Os eventos de log da rota de alerta são `occurrence.alert.received` (pedido recebido), `occurrence.alert.accepted` (aceite 201), `occurrence.alert.duplicate` (repetição 200) e `occurrence.alert.rejected` (recusa 4xx). (fonte: decisoes-joao-llt201.md, Rodada 4)

O portão de merge, quando houver código, é `./mvnw verify` com Testcontainers em tag fixa (constituição, III). Este guia não executa esse comando.

## 1. Alerta aceito uma vez

Chamar `POST /internal/v1/alertas/finder` com o token das duas audiências e corpo `{tenantId, productId, batchId, motivo}` em que `motivo` é `consulta_openobserve_indisponivel` e o produto pertence ao tenant.

Esperado: HTTP 201, corpo exatamente `{aceito: true, tenantId, productId, batchId, motivo}` iguais ao pedido, um único registro na coleção `finder_alerts`, e aviso à tela no canal `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`, sem evento novo e sem envelope no stream `lhamalog:notifications:events` nem no canal `lhamalog:occurrence-events`. A resposta cabe em 2 s. (fonte: spec.md, SC-001, SC-005 e SC-008; FR-001 e FR-002; contrato `alerta-finder.md`; decisoes-joao-llt201.md, pendência 1, formulário 1, e Rodada 3; respostas-pendentes-llt201.md, pendência 16)

Repetir a mesma chave. Esperado: HTTP 200, a mesma identidade, sem campo `repeticao`, ainda um único registro, e nenhum aviso `FINDER_ALERT` novo. (fonte: analyze-016.md, LOW A1)

Duas primeiras chamadas da mesma chave ao mesmo tempo. Esperado: um documento; 201 para quem gravou; 200 com a mesma identidade para quem releu. (fonte: decisoes-joao-llt201.md, pendência 9, formulário 1)

Se o POST falha, o lote permanece pendente no Finder e o aviso é reenviado no reprocessamento. O Core, no reenvio, responde pela deduplicação acima. (fonte: respostas-pendentes-llt201.md, pendência 19; origem: DF-FINDER §2 e §3)

## 2. Corpo inválido e produto alheio

| Chamada | Esperado |
|---|---|
| JSON ilegível | 400, `{"codigo":"REQUISICAO_INVALIDA"}`, sem gravação |
| corpo sem um dos quatro campos, campo a mais, ou `motivo` diferente de `consulta_openobserve_indisponivel` | 422, `{"codigo":"DADOS_INVALIDOS"}`, sem gravação |
| token válido e `productId` que não pertence ao `tenantId` | 403, `{"codigo":"ACESSO_NEGADO"}`, sem gravação |

(fonte: respostas-pendentes-llt201.md, pendência 4; origem: DF-FINDER §3 + padrão do #20; analyze-016.md, LOW I3; decisoes-joao-llt201.md, pendência 12, formulário 1; spec.md, FR-004 e FR-014)

## 3. Autenticação da rota de alerta

| Chamada | Esperado |
|---|---|
| sem JWT, JWT inválido, ou token de sessão de usuário | 401, `{"codigo":"NAO_AUTENTICADO"}` |
| `aud` com as duas audiências e `sub` fora da lista do #20 | 403, `{"codigo":"ACESSO_NEGADO"}` |
| token cujo `aud` contém a audiência do Módulo e `lhamalog-core` em `POST /internal/v1/alertas/finder` | segue para o aceite da seção 1 |
| o mesmo token em `POST /internal/v1/ocorrencias/consultas` ou `POST /internal/v1/resumos-analise` | autentica essas rotas pela audiência do Módulo |
| token só com `aud` `lhamalog-modulo-ocorrencia` nesta rota de alerta | 401 |
| token só com `aud` `lhamalog-core` nesta rota, com a propriedade `core-audience` aplicada | autentica a rota de alerta e não autentica as rotas do Módulo |
| token só com `aud` `lhamalog-core` sem o ajuste por rota | 401, porque a cadeia atual exige a audiência do Módulo em todo `/internal/v1/**` |

(fonte: spec.md, SC-007 e FR-005; decisoes-joao-llt201.md, pendências 6 e 20, formulário 1; respostas-pendentes-llt201.md, resumo, fato de código)

## 4. Aviso só na passagem para recorrente, com gravidade

Usar a entrega do #20 (`POST /internal/v1/resumos-analise`), não uma rota nova. `gravidade` no documento e no aviso é a classe (`ERROR`, `WARNING` ou `INFO`); o tipo permanece. (fonte: decisoes-joao-llt201.md, pendência 3, formulário 1; respostas-pendentes-llt201.md, pendência 2)

| Entrega | Esperado no canal `lhamalog:occurrence-events` e no WebSocket `/ws/occurrences` |
|---|---|
| 201 que cria a ocorrência, mesmo se já nasce recorrente | só `change` `CREATED`, com `gravidade` igual à classe |
| 201 que reabre fechada ou desarquivada e, na mesma entrega, a torna recorrente | só `change` `REOPENED`, com `gravidade` igual à classe |
| 201 que faz uma ocorrência já existente, que não é criação nem reabertura, passar a recorrente (`before.recurring` não é TRUE; ausente ou `null` conta como `false`; `after.recurring` é TRUE) | um `change` `RECURRENT`, em até 3 s, com `gravidade` igual à classe |
| 201 que só aumenta a contagem de uma ocorrência já aberta | nenhum aviso novo |
| repetição (200, mesmo recibo) ou entrega recusada | nenhum aviso novo, inclusive o de recorrente |

Campos do aviso: os do contrato [aviso-ocorrencia.md](./contracts/aviso-ocorrencia.md). Sem título, sem assinatura, sem trecho de log. (fonte: spec.md, SC-002, SC-003 e SC-006; decisoes-joao-llt201.md, pendência 5, formulário 1; respostas-pendentes-llt201.md, pendências 5, 11 e 16)

O `POST /internal/v1/alertas/finder` não publica no canal `lhamalog:occurrence-events` nem mensagem de tipo `occurrence`. O aviso desse POST é o da seção 1: no mesmo caminho `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`, só na primeira gravação (201). A repetição (200) não avisa de novo. (fonte: decisoes-joao-llt201.md, pendência 1, formulário 1, e Rodada 3)

## 5. Cinco cenários do DN-009-2

Com o cliente real do Finder e o Core da pilha mergeada, uma vez cada (fonte: spec.md, US4 e SC-004):

1. A consulta distingue ocorrência ausente de falha e rejeita acesso a outro tenant ou produto.
2. A primeira entrega grava exatamente um efeito e retorna 201. Reenvio depois de perda de resposta, inclusive depois de reinício do Finder, retorna 200 e o mesmo `reciboId`, sem duplicar.
3. Dois lotes que consultam a mesma revisão preservam todos os incrementos. Revisão desatualizada em um achado rejeita a entrega inteira antes de qualquer escrita.
4. Confirmação inválida ou inesperada mantém o lote pendente no Finder. Um recibo válido antecede a conclusão do lote. O passo de confirmação da fila é do Finder; o Core entra ao devolver o recibo válido.
5. O JSON mantém as três classes, carrega somente trechos protegidos e conserva `linhas` vazias quando for o caso. A API rejeita tipos e contadores inválidos.

As rotas são as do #20. Não há segunda implementação. O detalhe de pedido e resposta dessas rotas está no contrato da spec 013 e na proposta DN-009-2 citada pela spec 016, não neste quickstart.

## 6. TLS local

Subir o Caddy no compose do Core (`compose.yaml`), na frente do Core, que segue em HTTP na porta 8080 (o compose não é escrito por este plano). O cliente do Finder conclui `POST /internal/v1/alertas/finder` e uma entrega de resumo usando URL `https://`, com o token cujo `aud` contém as duas audiências. A base não leva caminho, usuário, query nem fragmento. A rota de alerta responde dentro de 2 s. A CA local do teste via `SSL_CERT_FILE` é da spec do Finder. (fonte: spec.md, SC-005 e FR-011; decisoes-joao-llt201.md, pendências 13 e 20, formulários 2 e 1, e pendência 18, formulário 2; respostas-pendentes-llt201.md, pendências 16 e 17; analyze-016.md, MEDIUM G1)

## 7. O que não validar como sucesso

- Uma coleção diferente de `finder_alerts`.
- Um literal de "virou recorrente" diferente de `RECURRENT`.
- Um campo `repeticao` na resposta do alerta.
- Um segundo aviso `FINDER_ALERT` na repetição 200.
- Um literal de log da rota de alerta fora de `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected`.
- Envelope no stream `lhamalog:notifications:events` como efeito do alerta ou do aviso de ocorrência.
- Gravidade na escala "critico"/"alto". O valor observável é a classe.
- Aviso "virou recorrente" somado a `CREATED` ou a `REOPENED` na mesma entrega.

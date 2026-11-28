# Contrato: POST /internal/v1/alertas/finder

**Feature**: `specs/016-alerta-finder-evento-ocorrencia` | **Data**: 2026-11-26

Contrato novo desta feature. As rotas `POST /internal/v1/ocorrencias/consultas` e `POST /internal/v1/resumos-analise` não mudam; o contrato delas continua o da spec 013 (`contracts/internal-api.md` na pilha). (fonte: spec.md, FR-010; decisoes-joao-llt201.md, base da branch)

Cliente que já chama esta rota: `lhamalog-finder/internal/adapter/out/core/aviso.go`, via `confirmacao/http.go`. Este contrato descreve o que esse cliente exige e o que a spec 016 fecha. Não altera o Finder. (fonte: spec.md, Fora de escopo e US1 cenário 7)

O código desta rota fica no pacote `finderalert` (exemplo `br.com.lhamalog.core.finderalert`). A segurança continua na cadeia `/internal/v1/**` do #20. (fonte: decisoes-joao-llt201.md, pendência 15, formulário 1; spec.md, FR-001)

A rota responde dentro de 2 s (`FINDER_CORE_TIMEOUT`). (fonte: respostas-pendentes-llt201.md, pendência 16; origem: DF-FINDER §3 e §8)

## Pedido

`POST /internal/v1/alertas/finder`

`Content-Type: application/json`

`Authorization: Bearer <JWT HS256>`

```json
{
  "tenantId": "tenant",
  "productId": "product",
  "batchId": "batch",
  "motivo": "consulta_openobserve_indisponivel"
}
```

Os quatro nomes são obrigatórios. O único `motivo` aceito é `consulta_openobserve_indisponivel`. (fonte: spec.md, FR-002 e FR-004; respostas-pendentes-llt201.md, pendência 4; origem: DF-FINDER §3)

## Autenticação

Mesma cadeia de `/internal/v1/**` do #20 (`InternalServiceSecurityConfiguration` no `git show` de `origin/LLT-137-atualizacao-em-tempo-real`). Sem segunda cadeia. (fonte: spec.md, FR-005; research.md, R4)

| Claim | Nesta rota | Nas rotas de consulta e de resumo |
|---|---|---|
| segredo HS256 | `lhamalog.occurrence.internal-auth.secrets` | o mesmo |
| `iss` | o emissor dessa configuração (padrão `lhamalog-internal`) | o mesmo |
| `sub` | a lista dessa configuração (padrão `lhamalog-finder`) | a mesma |
| `aud` exigido | contém `lhamalog-core`, lida da propriedade `core-audience` (só nesta rota) | contém `lhamalog-modulo-ocorrencia` |

Ilustração de caminho da propriedade, não nome extra decidido: `lhamalog.occurrence.internal-auth.core-audience`. (fonte: decisoes-joao-llt201.md, pendência 6, formulário 1)

Um único token cujo `aud` contém a audiência do Módulo e `lhamalog-core` autentica esta rota e as rotas do Módulo. Fato de código: no #20 a cadeia cobre todo `/internal/v1/**`, o `aud` é conferido por "contém", e um token só com `lhamalog-core`, sem o ajuste por rota, recebe 401. `scripts/InternalJwt.java` gera um token com uma única audiência (`--aud`). (fonte: decisoes-joao-llt201.md, pendência 20, formulário 1; respostas-pendentes-llt201.md, resumo, fato de código)

Token de sessão de usuário (Clerk) não autentica esta rota. (fonte: spec.md, FR-005)

| Situação | Status | Corpo |
|---|---|---|
| Sem cabeçalho, token malformado, assinatura inválida, `alg` diferente de HS256, expirado, `nbf` futuro, `iss` errado, `aud` sem a audiência exigida na rota, token da Clerk | 401 e `WWW-Authenticate: Bearer` | `{"codigo":"NAO_AUTENTICADO"}` |
| `sub` fora da lista de serviços | 403 | `{"codigo":"ACESSO_NEGADO"}` |
| `productId` não pertence ao `tenantId` | 403 | `{"codigo":"ACESSO_NEGADO"}` |

Os corpos 401 e 403 de autenticação são os que a cadeia do #20 já escreve para `/internal/v1/**`. O 403 de produto alheio é a mesma checagem das rotas do Módulo e não grava alerta. (fonte: spec 013, FR-016; decisoes-joao-llt201.md, pendência 12, formulário 1)

`aud` com só `lhamalog-modulo-ocorrencia` não autentica esta rota. `aud` com só `lhamalog-core` autentica esta rota depois do ajuste por `core-audience` e não autentica consulta nem resumo. (fonte: spec.md, FR-005)

## Sucesso

HTTP 201 na primeira gravação da chave. HTTP 200 na repetição. O cliente trata os dois como sucesso e rejeita o resto (`confirmacao/http.go`). (fonte: respostas-pendentes-llt201.md, pendência 8)

Corpo, sem outro campo:

```json
{
  "aceito": true,
  "tenantId": "tenant",
  "productId": "product",
  "batchId": "batch",
  "motivo": "consulta_openobserve_indisponivel"
}
```

A identidade é a do pedido. `aceito` diferente de `true`, nome a mais, nome faltando ou identidade diferente não é aceite para o cliente (`aviso.go`). Não há `repeticao`. (fonte: spec.md, FR-002 e US1 cenário 7)

Efeito durável: um único registro da chave `(tenantId, productId, batchId, motivo)`, na coleção Mongo `finder_alerts`. O reenvio da mesma chave responde 200 e não grava outro. (fonte: spec.md, FR-003; respostas-pendentes-llt201.md, pendência 7; decisoes-joao-llt201.md, Rodada 3)

Duas primeiras requisições da mesma chave ao mesmo tempo: um documento. Quem grava responde 201. Quem perde a corrida relê o registro e responde 200 com a mesma identidade. (fonte: decisoes-joao-llt201.md, pendência 9, formulário 1; respostas-pendentes-llt201.md, pendências 8 e 9)

Se o POST falha, o Finder mantém o lote pendente e reenvia o aviso no reprocessamento do lote. O Core atende esse reenvio por esta deduplicação. (fonte: respostas-pendentes-llt201.md, pendência 19; origem: DF-FINDER §2 e §3)

## Recusa de corpo

(fonte: respostas-pendentes-llt201.md, pendência 4; origem: DF-FINDER §3 + padrão do #20, spec 013 `contracts/internal-api.md`; analyze-016.md, LOW I3 — o 400 e o 422 usam o mesmo envelope `{"codigo":...}`)

| Situação | Status | Corpo | Grava |
|---|---|---|---|
| JSON ilegível | 400 | `{"codigo":"REQUISICAO_INVALIDA"}` | não |
| Corpo sem os quatro campos, campo a mais, ou `motivo` diferente de `consulta_openobserve_indisponivel` | 422 | `{"codigo":"DADOS_INVALIDOS"}` | não |

Não há outros códigos neste contrato.

## Depois do aceite durável

A primeira gravação (201) avisa a tela no canal WebSocket `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`. A repetição (200) não avisa de novo. Não há evento novo nem envelope para a Notificação. Este alerta não é publicado no canal `lhamalog:occurrence-events` nem no stream `lhamalog:notifications:events`. (fonte: decisoes-joao-llt201.md, pendência 1, formulário 1, e Rodada 3; spec.md, FR-006 e SC-008; analyze-016.md, LOW A1)

```json
{
  "tenantId": "tenant",
  "productId": "product",
  "batchId": "batch",
  "motivo": "consulta_openobserve_indisponivel"
}
```

O caminho é o mesmo `/ws/occurrences` do aviso de ocorrência do #24. O tipo desta mensagem é `FINDER_ALERT`. O aviso de ocorrência continua com tipo `occurrence` e segue o contrato `aviso-ocorrencia.md`. O fechamento do documento da ADR_051 está em `plan.md`, Pendências — documentos necessários; esse documento pode registrar este contrato já decidido.

O log de negócio desta rota usa exatamente `occurrence.alert.received` (pedido recebido), `occurrence.alert.accepted` (aceite 201), `occurrence.alert.duplicate` (repetição 200) e `occurrence.alert.rejected` (recusa 4xx). Não há outro literal. (fonte: decisoes-joao-llt201.md, Rodada 4; spec.md, FR-012)

## TLS

O cliente só monta a URL se a base é `https`, sem usuário, query, fragmento nem caminho além de `/` (`confirmacao/http.go`). No teste local o Core segue em HTTP na porta 8080 e o Caddy no compose do Core oferece o HTTPS. O proxy é do lado do Core. O Finder só recebe a URL `https://`. Este arquivo não define o compose. A CA local do teste via `SSL_CERT_FILE` é da spec do Finder. (fonte: decisoes-joao-llt201.md, pendência 13, formulário 2, e pendência 18, formulário 2; respostas-pendentes-llt201.md, pendência 17; spec.md, FR-011)

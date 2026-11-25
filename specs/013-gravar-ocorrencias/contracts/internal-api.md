# Contrato HTTP: rotas internas do Módulo Ocorrência

Fonte da verdade: `lhamalog-finder/specs/009-publicar-evento-ocorrencia/contracts/proposta-dn-009-2.openapi.yaml` (aprovado em 20/11/2026). Este arquivo registra como o Core implementa cada resposta; nomes de rota, campos e códigos não mudam.

## Autenticação (as duas rotas)

`Authorization: Bearer <JWT>` HS256 com `iss`, `aud`, `sub`, `exp` (e `nbf` opcional) válidos para a configuração do Core.

| Situação | Status | Corpo |
|---|---|---|
| Sem cabeçalho, token malformado, assinatura inválida, `alg` diferente de HS256, expirado, `nbf` futuro, `iss` ou `aud` errados, token da Clerk | 401 + `WWW-Authenticate: Bearer` | `{"codigo":"NAO_AUTENTICADO"}` |
| `sub` fora da lista de serviços autorizados | 403 | `{"codigo":"ACESSO_NEGADO"}` |
| `productId` não pertence ao `tenantId` (ou não existe) | 403 | `{"codigo":"ACESSO_NEGADO"}` |

## POST /internal/v1/ocorrencias/consultas

Corpo `ConsultaAchado`: `{tenantId, productId, assinatura, tipo}`, sem campos a mais.

| Situação | Status | Corpo |
|---|---|---|
| Sem ocorrência na chave | 200 | `{"estado": null}` |
| Ocorrência encontrada | 200 | `{"estado": {"contagemAcumulada": 13, "primeiraOcorrencia": "…Z", "ultimaOcorrencia": "…Z", "status": "ABERTA", "revisao": "…"}}` |
| JSON ilegível ou raiz que não é objeto | 400 | `{"codigo":"REQUISICAO_INVALIDA"}` |
| Campo a mais ou ausente, tipo JSON errado, obrigatório vazio, tipo fora da classe | 422 | `{"codigo":"DADOS_INVALIDOS"}` |
| Mais de uma ocorrência na chave, ou ocorrência sem contagem, datas ou status válidos | 500 | `{"codigo":"OCORRENCIA_INVALIDA"}` |

## POST /internal/v1/resumos-analise

Corpo `ResumoAnalise` do contrato.

| Situação | Status | Corpo |
|---|---|---|
| Primeira entrega aceita da chave | 201 | `Confirmacao` com `repeticao: false` |
| Chave já confirmada (qualquer corpo válido) | 200 | `Confirmacao` original com `repeticao: true` |
| Revisão consultada desatualizada em algum achado | 409 | `{"codigo":"REVISAO_DESATUALIZADA"}` |
| Outra entrega da mesma chave em processamento | 409 | `{"codigo":"ENTREGA_EM_ANDAMENTO"}` |
| JSON ilegível ou raiz que não é objeto | 400 | `{"codigo":"REQUISICAO_INVALIDA"}` |
| Campo a mais ou ausente, tipo JSON errado ou regra do contrato violada (research R5) | 422 | `{"codigo":"DADOS_INVALIDOS"}` |
| Mais de 1.000 achados | 422 | `{"codigo":"RESUMO_GRANDE_DEMAIS"}` |
| Duplicidade na coleção para uma chave | 500 | `{"codigo":"OCORRENCIA_INVALIDA"}` |
| Reserva perdida antes da confirmação | 500 | `{"codigo":"ERRO_INTERNO"}` |

`Confirmacao`: `{"aceito": true, "reciboId": "…", "tenantId": "…", "productId": "…", "batchId": "…", "execId": "V…" (só quando veio), "repeticao": false}`. A identidade devolvida é sempre a da chave.

## O que o Core não faz nesta entrega

- `429`/`503` com `Retry-After`: previstos no contrato, sem gatilho no Core por enquanto (sem limite de taxa).
- Gravidade, criticidade, aviso ao Core (LLT-216) e aviso de falha do OpenObserve (ADR_051).

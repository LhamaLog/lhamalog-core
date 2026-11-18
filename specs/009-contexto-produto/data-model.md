# Modelo de dados

Estende o Produto da 001 e da 002. Empresa, AcessoProduto, sessão e papéis não mudam.

## Produto.context

Subdocumento `context` da coleção `Produto`. Substitui os campos da 001 (`problemSolved`, `targetAudience`, `differentiators`, `keywords`, `restrictions`).

| Campo | Tipo | Regras |
| --- | --- | --- |
| functionalDescription | string | opcional; sem espaços nas pontas; até 2.000 caracteres; vazio quando ausente |
| personality | enum | `SIMPLE`, `STANDARD` ou `DETAILED`; `STANDARD` quando ausente; valor desconhecido = 400 |
| responseLanguage | string | código de idioma (`pt-BR`, `en`, `es-419`); até 20; `pt-BR` quando ausente ou vazio |
| architecture | lista de string | até 30 itens de até 80 caracteres; itens com `trim`, vazios descartados |
| scope | lista de string | até 30 itens de até 200 caracteres; mesma limpeza |
| sensitiveData | lista de string | até 30 itens de até 200 caracteres; mesma limpeza |

Contexto **com conteúdo** = descrição não vazia ou algum item em uma das três listas. Personalidade e idioma sozinhos não contam.

Documentos gravados antes desta feature, com os campos antigos, são lidos com os campos novos vazios e os padrões aplicados. Não há migração (sem dados de produção).

## ProductContextRevision (coleção nova)

| Campo | Tipo | Regras |
| --- | --- | --- |
| id | string | `<productId>:<revision>` |
| tenantId | string | empresa do produto |
| productId | string | produto da versão |
| revision | long | 1, 2, 3… por produto; nunca repete |
| createdAt | instante | momento da gravação, em milissegundos |
| actorUserId | string | usuário da Clerk que gravou (`sub` do token) |
| context | Produto.context | cópia completa do contexto naquele momento |

Índice único `product_context_revision`: (`productId`, `revision`).

## Transições

```text
cadastro com contexto com conteúdo  --> versão 1
cadastro com contexto vazio         --> nenhuma versão
gravação de contexto diferente      --> versão N+1
gravação de contexto igual          --> nenhuma versão (produto salvo, version avança)
produto arquivado                   --> gravação recusada (409); versões continuam legíveis
```

## Validações

- Limite excedido ou idioma fora do formato: 400 com `fieldErrors` em `context.*`.
- Personalidade desconhecida: 400 "Os dados enviados são inválidos."
- `version` desatualizado: 409, sem versão nova.
- Sem acesso ou outra empresa: 404.
- Versão inexistente: 404 "Revisão de contexto não encontrada."

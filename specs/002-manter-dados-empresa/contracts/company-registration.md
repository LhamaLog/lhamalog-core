# Contrato: cadastro da empresa

Base já aceita: [settings da empresa](../../001-empresa-produtos/contracts/company-settings.md). Este contrato acrescenta quem pode gravar e a exclusão do cadastro. Corpo de sucesso, validação e conflito de versão permanecem os atuais.

A organização é sempre a da sessão autenticada. `id`, `clerkOrgId` e `organizationId` enviados pelo cliente continuam ignorados.

## Quem acessa

| Operação | Administrador | Membro da mesma organização | Sem sessão | Sessão sem organização |
|----------|---------------|-----------------------------|------------|------------------------|
| `GET /api/company` | 200 | 200 | 401 | 403, mensagem atual de empresa não selecionada |
| `GET /api/company/logo` | 200 ou 404 se não houver arquivo | igual ao administrador | 401 | 403, mensagem atual |
| `GET /api/company/context/revisions` | 200 | 200 | 401 | 403, mensagem atual |
| `GET /api/company/context/revisions/{revision}` | 200 ou 404 | igual ao administrador | 401 | 403, mensagem atual |
| `GET /api/company/context/prompt` | 200 | 200 | 401 | 403, mensagem atual |
| `PUT /api/company` | 200 ou erro atual de validação/409 | 403 | 401 | 403, mensagem atual |
| `POST /api/company/logo` | 200 ou erro atual de arquivo/409 | 403 | 401 | 403, mensagem atual |
| `DELETE /api/company/logo` | 200 ou 409 | 403 | 401 | 403, mensagem atual |
| `DELETE /api/company` | 200 | 403 | 401 | 403, mensagem atual |

O 403 do membro sai antes da validação do corpo, do arquivo e da versão. A sessão autenticada permanece. A mensagem desse 403 é diferente da mensagem de empresa não selecionada. Slug fora de `CLERK_ADMIN_ROLES`, inclusive um nome parecido com administrador, segue a coluna do membro.

`GET /api/company/postal-code/{cep}`, `POST /api/company/actions/export-pdf` e `POST /api/company/actions/sync` continuam 501 para administrador e membro.

## PUT /api/company

Permanece a gravação atual, agora só para administrador. Primeira gravação usa `version: null`. Alteração envia a versão vigente. Versão divergente responde 409 e não mistura conteúdos. Campos omitidos permanecem.

## DELETE /api/company

Sem corpo e sem versão. Remove o cadastro da organização da sessão:

- documento da empresa;
- logotipo;
- revisões de contexto dessa organização.

Resposta 200 com o mesmo corpo de um `GET /api/company` sem cadastro gravado. `version` vem nula. Os campos de cadastro vêm vazios. O logotipo vem ausente. `id` e `organizationId` são a organização da sessão.

Chamada quando o cadastro já está ausente também responde 200 com esse corpo vazio.

Depois da exclusão:

- `GET /api/company/logo` responde 404, o mesmo resultado de empresa sem logotipo;
- a lista de revisões vem vazia;
- uma revisão antiga responde 404;
- `Produto` e `AcessoProduto` dessa organização continuam;
- `PUT` com `version: null` grava um cadastro novo, sem os valores removidos;
- `PUT` com a versão anterior ao delete responde 409.

## Isolamento

Com sessões das organizações A e B:

- consulta, gravação e exclusão em A não alteram B;
- a resposta de A não contém valores gravados em B;
- um corpo que cita o id de B ainda grava ou exclui somente A.

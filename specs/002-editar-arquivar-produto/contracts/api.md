# Contrato REST Editar e arquivar Produto

Autenticação, empresa via `o.id`, erros 400/401/403/404/409/503 e formato `{message, fieldErrors}` permanecem os da 001 ([contrato 001](../../001-empresa-produtos/contracts/api.md)). Respostas de produto incluem `archivedAt` (nulo quando ativo). `status` não é gravável.

Papéis: edição exige acesso ao produto (administrador da org ou `ProductAccess`). `POST .../archive` e `POST .../unarchive` exigem `CompanySession.administrator()`.

## Endpoints existentes (comportamento adicional)

### GET /api/products

Query opcional `archived` (boolean). Padrão: `false`.

- `archived` ausente ou `false`: somente produtos da empresa com `archivedAt == null`, ordem nome/id, visibilidade da 001.
- `archived=true`: somente produtos da empresa com `archivedAt != null`, mesma visibilidade e ordem.

200 lista. Sem organização: 403. JWT inválido: 401.

### GET /api/products/{id}

200 o produto acessível da empresa, inclusive arquivado. 404 se inexistente na empresa, outro tenant ou sem acesso.

### POST /api/products

Inalterado. `archivedAt` gravado nulo. `status` continua `CONFIGURING`.

### PUT /api/products/{id}/context

Igual à 001, e recusa produto arquivado com 409 (`Os dados foram alterados. Recarregue antes de salvar novamente.` ou mensagem de produto arquivado, sem stacktrace). `version` obrigatório.

## Endpoints novos

### PUT /api/products/{id}

Atualiza só dados gerais. Não altera `context`, `status`, `tenantId`, `createdAt` nem `archivedAt`.

Corpo:

```json
{
  "name": "Nome",
  "description": "opcional",
  "category": "opcional",
  "productVersion": "opcional",
  "version": 0
}
```

Validações: `name` `@NotBlank` máx. 200; `description` máx. 10000; `category` e `productVersion` máx. 200; `version` `@NotNull`.

- 200 documento persistido; `version` avançado.
- 400 validação.
- 404 sem acesso / outro tenant.
- 409 versão velha ou produto arquivado.

### POST /api/products/{id}/archive

Corpo: `{ "version": 0 }`.

- Se `archivedAt == null` e `version` atual: grava `archivedAt` agora, avança `version`, 200.
- Se já arquivado: 200 documento atual, sem nova gravação ( `version` no corpo pode ser ignorado).
- 403 membro autenticado na empresa com acesso (ou admin check falhou) que não é administrador.
- 404 sem acesso / outro tenant.
- 409 `version` divergente na transição real.

### POST /api/products/{id}/unarchive

Corpo: `{ "version": 0 }`.

- Se `archivedAt != null` e `version` atual: zera `archivedAt`, avança `version`, 200.
- Se já ativo: 200 documento atual, sem nova gravação.
- 403/404/409 como no arquivo.

## Fora do contrato

Sem DELETE. Sem endpoint de ingestão. Sem concessão/revogação de acesso. Cliente não envia `archivedAt` nem `status` para forçar estado — só os POST de arquivo/desarquivo.

# Contrato REST Empresa e Produtos

Authorization: Bearer JWT Clerk. Empresa selecionada exclusivamente por o.id, compatibilidade com org_id v1. Rotas sem organização retornam 403. Membro válido da organização pode consultar e manter Empresa; Produto requer AcessoProduto ou papel administrador validado. IDs de outra empresa respondem 404, sem exposição de existência.

- GET /api/company → 200 {id, clerkOrgId, data, context, address, version} e, na extensão atual, preferences, shared, logo, summary, capabilities, limitations e audit. Cadastro ausente retorna estrutura vazia, version null, para primeiro preenchimento; não grava em GET.
- PUT /api/company ← {data, context, address, version} continua aceito. preferences é opcional. Chave ausente preserva o valor já gravado; string vazia limpa. O contrato dos campos novos está em [company-settings.md](company-settings.md). Version null apenas para criação inicial.
- GET /api/products → 200 lista somente dos produtos acessíveis da empresa, ordenada por nome/id.
- POST /api/products ← {name, description, category, productVersion, context} → 201 Produto. Empresa vem da sessão; criador recebe acesso. Sem limite por plano.
- GET /api/products/{id} → 200 Produto acessível.
- PUT /api/products/{id}/context ← {context, version} → 200 Produto atualizado, sem permitir alteração de tenantId ou campos cadastrais nesta operação.

Contextos/atributos em data-model.md. Corpo inválido/validação: 400 {message, fieldErrors}; JWT inválido: 401; ausência de organização: 403; recurso inacessível/inexistente: 404; conflito concorrente: 409. Falha de banco: 503 com mensagem genérica. Não retornar stacktrace nem token. Versão e dados preservados em falha de validação. Erro 403 não provoca logout no fluxo de negócio.

Frontend descarta respostas de organização anterior, bloqueia envio repetido, mantém valores após falha e só mostra sucesso após resposta 2xx. Não há DELETE nem endpoint de concessão/revogação de acesso nesta feature. AcessoProduto pode ser integrado à futura gestão de membros.

# Contrato REST Empresa e Produtos

Authorization: Bearer JWT Clerk (session token v2), validado pela camada de segurança da LLT-163 (specs/002-validacao-token-clerk). A empresa vem do Tenant da organização ativa (o.id); o tenantId é o id interno do Tenant. Sem organização, sem Tenant ou com Tenant inativo a resposta é 403 {"error":"forbidden"} e o Core revoga as sessões do usuário na Clerk (ADR_032). Membro válido da organização pode consultar e manter Empresa; Produto requer AcessoProduto ou papel admin da organização. IDs de outra empresa respondem 404, sem exposição de existência.

- GET /api/company → 200 {id, clerkOrgId, data, context, address, version}. id é o tenantId. Cadastro ausente retorna estrutura vazia, version null, para primeiro preenchimento; não grava em GET.
- PUT /api/company ← {data, context, address, version} → 200 documento persistido. Contextos/campos vazios permitidos. Version null apenas para criação inicial.
- GET /api/products → 200 lista somente dos produtos acessíveis da empresa, ordenada por nome/id.
- POST /api/products ← {name, description, category, productVersion, context} → 201 Produto. Empresa vem da sessão; criador recebe acesso. Sem limite por plano.
- GET /api/products/{id} → 200 Produto acessível.
- PUT /api/products/{id}/context ← {context, version} → 200 Produto atualizado, sem permitir alteração de tenantId ou campos cadastrais nesta operação.

Contextos/atributos em data-model.md. Corpo inválido/validação: 400 {message, fieldErrors}; JWT inválido: 401 {"error":"unauthorized"}; sem acesso ao Tenant: 403 {"error":"forbidden"}, com revogação das sessões; recurso inacessível/inexistente: 404; conflito concorrente: 409. Falha de banco: 503 com mensagem genérica. Não retornar stacktrace nem token. Versão e dados preservados em falha de validação. As regras de negócio não usam 403: produto sem acesso responde 404, para não encerrar a sessão do usuário.

Frontend descarta respostas de organização anterior, bloqueia envio repetido, mantém valores após falha e só mostra sucesso após resposta 2xx. Não há DELETE nem endpoint de concessão/revogação de acesso nesta feature. AcessoProduto pode ser integrado à futura gestão de membros.

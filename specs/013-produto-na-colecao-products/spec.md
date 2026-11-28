# Feature Specification: Produto gravado na coleção que o Coletor lê (`products`)

**Feature Branch**: `LLT-179-core-grava-products`

**Spec**: `specs/013-produto-na-colecao-products` (a 012 é da LLT-119, ainda fora da `Development`)

**Created**: 27/11/2026

**Status**: Draft

**Input**: User description: "Fazer as chaves de ingestão geradas pelo Core serem aceitas pelo Coletor (LLT-179, Feature LLT-124 'Credenciais de ingestão OpenTelemetry'). Hoje o Coletor procura o produto na coleção `products` pelo campo `productId`, e o Core grava o produto em `Produto`, com o id em `_id`; por isso nenhuma chave gerada é aceita (desencontro registrado na spec 010). Decisão D1 do desenvolvedor em 27/11/2026: opção B, o Core passa a gravar em `products`, com `productId`, e o Coletor não muda de código."

## Clarifications

### Session 2026-11-27

Perguntas resolvidas pela recomendação, com o "ok, pode seguir" do Pedro em 27/11/2026:

- Q: Uma cópia em `products` (mantendo `Produto`) ou a própria coleção do produto passa a ser `products`? → A: a própria coleção passa a ser `products`. Uma cópia duplica dado e obriga a sincronizar criar, editar, arquivar e desarquivar.
- Q: O `productId` entra como campo novo do `Product` (record)? → A: não. Ele é escrito no documento no momento de salvar, com o mesmo valor do `_id`. O record não muda, e o `new Product(...)` das branches abertas do time (135, 136, 137, 145, 292, 293 e 295) continua compilando.
- Q: Como os dados que já estão em `Produto` chegam a `products`? → A: script mongosh idempotente, documentado no `quickstart.md` e no PR, que o time roda em cada banco antes de subir esta versão. Não há migração automática na subida do Core: a renomeação disputaria com a criação automática de índices e com várias instâncias subindo ao mesmo tempo. Nenhum ambiente tem dados reais de cliente (situação registrada na spec 006).
- Q: O Coletor precisa mudar? → A: não no código. A configuração dele (`MONGODB_URI`) precisa apontar para o banco do Core, com usuário só de leitura, porque `product_tokens` também é gravado pelo Core. Isso vale para qualquer opção da D1 e vai como aviso ao Michel no PR.
- Q: Produto arquivado deve parar de ingerir? → A: fora de escopo (pergunta C3 do Coletor, continua pendente). O Coletor só lê `productId` e `tenantId`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Chave gerada no Core é aceita pelo Coletor (Priority: P1)

Um DONO ou ADMIN cria um produto e gera uma chave de ingestão para ele. O exporter OpenTelemetry envia logs com essa chave, e o Coletor encontra o produto e o `tenantId` da empresa, sem nenhum passo manual.

**Why this priority**: é o objetivo da Feature LLT-124. Sem isso, nenhuma chave funciona ponta a ponta.

**Independent Test**: criar produto e chave pela API do Core e executar, no banco, a mesma busca que o Coletor faz (`product_tokens` pelo SHA-256 da chave e `products` pelo `productId`); o `tenantId` encontrado é o da empresa.

**Acceptance Scenarios**:

1. **Given** um produto criado pela API, **When** o banco é consultado, **Then** o documento está em `products`, com `productId` igual ao `_id` e com o `tenantId` da empresa, e nada é gravado em `Produto`.
2. **Given** um produto e uma chave gerados pela API, **When** se faz a busca do Coletor (`product_tokens.tokenHash` e depois `products.productId`), **Then** o produto é encontrado e o `tenantId` é o da empresa.
3. **Given** um produto editado, com contexto alterado, arquivado e desarquivado, **When** o banco é consultado, **Then** o `productId` continua presente e igual ao `_id`.

---

### User Story 2 - Bancos existentes passam para `products` sem perder produto (Priority: P2)

Quem cuida de um ambiente (local, dev ou produção) roda o script de migração antes de subir esta versão. Os produtos que estavam em `Produto` passam a `products`, com `productId`, e continuam aparecendo no Web.

**Why this priority**: sem a migração, os produtos já criados somem da tela depois da subida.

**Independent Test**: num banco com produtos em `Produto`, rodar o script duas vezes; conferir que todos estão em `products`, com `productId`, e que a segunda execução não muda nada.

**Acceptance Scenarios**:

1. **Given** um banco com produtos em `Produto` e sem `products`, **When** o script roda, **Then** a coleção passa a se chamar `products` e todo documento ganha `productId` igual ao `_id`.
2. **Given** o script já rodado, **When** ele roda de novo, **Then** nada muda.
3. **Given** um banco em que o Core novo já subiu antes do script (existe `products` vazia), **When** o script roda, **Then** a `products` vazia é descartada e a `Produto` é renomeada.
4. **Given** um banco com `Produto` e `products` com documentos, **When** o script roda, **Then** ele para sem mexer em nada e avisa que é preciso conferir à mão.

---

### Edge Cases

- Documento antigo em `products` sem `productId` (gravado antes do script): o Coletor não o encontra; o script preenche. O índice único de `productId` é parcial e ignora documentos sem o campo, para a subida do Core nunca falhar por isso.
- Edição concorrente do mesmo produto: continua valendo o controle de versão das features 002 e 006; o `productId` é o mesmo nas duas gravações.
- Produto de outra empresa: o isolamento por `tenantId` das consultas do Core não muda.
- Os índices `product_tenant_name` e `product_tenant_normalized_name` são recriados em `products` pela criação automática de índices; a renomeação feita pelo script já os leva junto.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O produto MUST ser gravado na coleção `products`.
- **FR-002**: Toda gravação de produto (criação, edição, contexto, arquivar, desarquivar e desfazer por falha de revisão) MUST escrever no documento o campo `productId`, texto igual ao `_id`.
- **FR-003**: O `Product` (record) e a API HTTP de produtos MUST NOT mudar: mesmos campos, mesmas respostas, mesmos códigos.
- **FR-004**: `products` MUST ter índice único em `productId`, parcial (só documentos com o campo).
- **FR-005**: O Core MUST NOT gravar nada em `Produto`.
- **FR-006**: O repositório MUST trazer o script mongosh de migração (em `quickstart.md`), idempotente, com os casos das histórias e da US2.
- **FR-007**: Esta feature MUST NOT alterar o formato de `product_tokens` nem o código do Coletor.

### Key Entities

- **Produto**: mesmo modelo das features 001, 002, 006 e 009; muda só o nome da coleção e o campo extra `productId` no documento, que é o contrato com o Coletor (ao lado de `tenantId`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% dos produtos gravados pelo Core têm `productId` igual ao `_id` em `products`.
- **SC-002**: 100% das chaves ativas geradas pelo Core passam na busca do Coletor e devolvem o `tenantId` da empresa do produto.
- **SC-003**: O `clean verify` do Core passa sem mudar nenhum teste existente de produto além do nome da coleção.
- **SC-004**: No teste manual da Feature (Parte D), uma chave gerada no Web é aceita pelo Coletor (HTTP 200) e o log aparece no OpenObserve com o `productId` e o `tenantId` certos.

## Assumptions

- O Coletor (LLT-181) lê `product_tokens` e `products` do banco configurado no `MONGODB_URI` dele; em todos os ambientes esse banco passa a ser o do Core, com usuário só de leitura.
- Nenhum ambiente tem dados reais de cliente; o script basta como migração.
- O rascunho de ADR da LLT-124 (`ADR-tokens-ingestao.md`, fora do repositório) é revisto com a D1 = B; o time decide.
- Fora de escopo: bloquear ingestão de produto arquivado (C3), mudança no Coletor, renomear outras coleções do Core para um padrão único.

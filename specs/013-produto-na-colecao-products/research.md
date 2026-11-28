# Research: Produto gravado em `products`

**Data**: 27/11/2026 | **Spec**: [spec.md](spec.md)

## R1. Onde corrigir o desencontro (D1)

- **Decisão**: no Core (opção B), por escolha do Pedro em 27/11/2026.
- **Motivo**: o Coletor já está pronto e testado lendo `products.productId`; o Core é a fonte da verdade dos produtos (ADR_116) e passa a gravar no formato que o Coletor lê, como já faz com `product_tokens` (spec 010).
- **Alternativas**: A (o Coletor lê `Produto` por `_id`): muda o código do Coletor, do Michel. C (não corrigir): a Feature LLT-124 não fecha.

## R2. Coleção renomeada × cópia

- **Decisão**: renomear (`@Document("products")`).
- **Motivo**: uma fonte só; nada a sincronizar ao editar, arquivar ou desarquivar.
- **Alternativa rejeitada**: manter `Produto` e gravar uma cópia `{productId, tenantId}` em `products`. Duplica dado e cria um segundo ponto de falha em cada gravação.

## R3. Como gravar `productId` sem mudar o record

- **Decisão**: `BeforeSaveCallback<Product>` do Spring Data MongoDB (`ProductCollectorFields`), que escreve `productId = _id` no `org.bson.Document` logo antes de gravar.
- **Motivo**: o callback roda em `insert` e `save` do `MongoTemplate` e dos repositórios, que são os únicos caminhos de gravação de produto (conferido: não há `updateFirst`, `bulkOps` nem `insertAll` com `Product`). O id é um UUID gerado pelo `ProductService` antes do `insert`, então já existe no callback. O record fica igual, e as 7 branches abertas que usam `new Product(...)` continuam compilando.
- **Alternativa rejeitada**: campo `productId` no record. Quebra o construtor em todas as branches abertas e duplica o id no modelo.

## R4. Índice de `productId`

- **Decisão**: `@CompoundIndex(name = "product_collector_product_id", def = "{'productId': 1}", unique = true, partialFilter = "{'productId': {$exists: true}}")`.
- **Motivo**: o Coletor busca por `productId` em cada chave nova (cache de 30 s). Parcial para a criação automática de índices (`spring.data.mongodb.auto-index-creation=true`) nunca falhar num banco com documentos antigos sem o campo.

## R5. Migração

- **Decisão**: script mongosh idempotente no `quickstart.md`, rodado pelo time antes de subir a versão.
- **Motivo**: rodar na subida do Core disputaria com a criação automática de índices (que cria `products` vazia antes) e com várias instâncias. Sem dados reais de cliente (spec 006), o script basta.
- **Casos**: só `Produto` → renomeia; `products` vazia e `Produto` → descarta a vazia e renomeia; as duas com documentos → para e avisa; depois, preenche `productId` onde falta.

## R6. Coletor

- **Decisão**: nenhuma mudança de código. Os campos de `product_tokens` já batem (conferido em `IngestionTokenDocument` e `MongoProductTokenStore`; `status` só `ACTIVE`/`REVOKED` dos dois lados).
- **Configuração**: o `MONGODB_URI` do Coletor aponta para o banco do Core, com usuário só de leitura. Aviso ao Michel no PR.

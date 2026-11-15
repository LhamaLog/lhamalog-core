# Pesquisa e decisões técnicas

## Garantia de unicidade

- **Decisão**: campo `normalizedName` no documento Produto e índice único composto `product_tenant_normalized_name` (`tenantId`, `normalizedName`). Sem consulta prévia ao gravar.
- **Rationale**: pedido explícito desta etapa. O índice é atômico, então entre gravações simultâneas exatamente uma passa (FR-011, SC-002). Por ser composto com `tenantId`, a regra vale por empresa e o mesmo nome segue livre em outras (FR-007).
- **Alternatives considered**: consultar antes de gravar (corrida entre a consulta e a gravação, viola FR-011); índice com collation sobre `name` (a comparação ficaria na configuração do índice, em vez de uma regra única e visível no código; o campo normalizado foi o pedido).

## Normalização

- **Decisão**: `name.trim().toLowerCase(Locale.ROOT)`, em um único método estático `Product.normalizeName`.
- **Rationale**: `Locale.ROOT` torna o resultado independente do locale padrão da JVM. A conversão trata letras acentuadas ("AÇÃO" vira "ação") e mantém os acentos ("Acao" continua diferente de "Ação"), como exige FR-002. `trim()` é o mesmo corte que cadastro e edição já aplicam ao `name`, então espaços internos seguem diferenciando nomes.
- **Alternatives considered**: `strip()` (mudaria o critério de "espaço nas pontas" em relação ao `name` gravado); remover acentos ou colapsar espaços internos (fora do pedido).

## Tradução da exceção

- **Decisão**: capturar `DuplicateKeyException` no `ProductService`, somente em volta de `products.insert` em `create` e de `products.save` em `update`, e lançar `ResponseStatusException(CONFLICT, ...)` com a mensagem de nome em uso.
- **Rationale**: o handler global mapeia `DuplicateKeyException` para "Os dados foram alterados...", e atende também outras gravações (`ProductAccess` tem índice único próprio; `Company` grava com `_id` igual ao tenant). Mudar o handler daria a mensagem errada a elas. Nas duas gravações capturadas, a única chave única que pode ser violada é a do nome: o `_id` é um UUID novo em `create` e não muda em `update`. Por isso não é preciso inspecionar o texto da exceção. Em `create`, a tradução fica dentro do `try` existente, e o vínculo de acesso continua sendo removido quando o cadastro é recusado (FR-010).
- **Alternatives considered**: tratar no handler global inspecionando o nome do índice na mensagem do driver (frágil e desnecessário); código de erro legível por máquina na resposta (não pedido; a mensagem própria atende FR-009).

## Ordem das recusas

- **Decisão**: nenhuma lógica nova de ordem. A violação do índice só ocorre na gravação, que é o último passo.
- **Rationale**: validação (400), organização (403), acesso e isolamento (404), produto arquivado (409) e versão de concorrência (409) já acontecem antes de gravar. Quem não tem acesso nunca recebe a recusa de nome (FR-012).
- **Alternatives considered**: checar o nome antes do acesso (revelaria nomes a quem não pode operar o produto).

## Campo oculto na API

- **Decisão**: `@JsonIgnore` no componente `normalizedName`.
- **Rationale**: o controller devolve o record `Product` diretamente, então o campo vazaria nas respostas sem a anotação. O nome de comparação não é exibido ao usuário (Key Entities da spec). O record só é serializado: os corpos de requisição usam DTOs próprios, então o cliente não consegue enviar o campo.
- **Alternatives considered**: DTO de resposta separado (refatoração maior que o necessário nesta feature).

## Gravações que não mudam o nome

- **Decisão**: `updateContext` e `withArchivedAt` repassam `current.normalizedName()`; `create` e `update` recalculam a partir do nome recebido.
- **Rationale**: essas gravações reconstroem o record inteiro. Se o valor se perdesse, o documento ficaria com o campo nulo e sairia da regra. Por ser componente do record, o campo novo gera erro de compilação em cada `new Product(...)` (quatro pontos, todos no `ProductService`), então nenhum fica esquecido. A recusa não se aplica a essas operações (FR-013).
- **Alternatives considered**: recalcular a partir de `current.name()` em toda gravação (resultado igual com documentos já normalizados; repassar deixa explícito que o nome não mudou).

## Produto arquivado

- **Decisão**: índice sem filtro por `archivedAt`.
- **Rationale**: o arquivado permanece no índice, então continua ocupando o nome e o desarquivamento não tem como conflitar (FR-006). Renomear libera o nome antigo automaticamente, porque a entrada do índice muda.
- **Alternatives considered**: índice parcial só para produtos ativos (contraria o pedido e permitiria conflito ao desarquivar).

## Dados existentes e outras branches

- **Decisão**: sem migração, sem preenchimento retroativo e sem apagar dados. Esta branch roda e testa em banco próprio, vazio, separado do `lhamalog_core` compartilhado. Orientação em [quickstart.md](quickstart.md).
- **Rationale**: não há dados de produção (informado pelo usuário). A condição vai além de nomes repetidos: documentos antigos não têm `normalizedName`, e índice único trata campo ausente como nulo. Dois produtos antigos na mesma empresa colidem em (`tenantId`, nulo) mesmo com nomes diferentes, e a criação do índice falha na subida. Com um só, o índice é criado, mas o produto antigo não bloqueia cadastros com o mesmo nome. Além disso, um índice criado em banco compartilhado continua lá ao trocar de branch: branches sem esta feature gravam produtos sem o campo e passariam a receber 409 a partir do segundo produto da mesma empresa. O banco próprio evita os dois efeitos sem tocar no que as outras branches usam.
- **Alternatives considered**: limpar o banco local compartilhado (apagaria dados usados por outras branches); preenchimento retroativo na subida (código para dado que não existe em produção, e alteraria o banco compartilhado); índice parcial só para documentos com o campo (permitiria subir no banco compartilhado sem afetar outras branches, mas os produtos antigos ficariam fora da regra sem aviso; mantido o índice pedido).

## Testes

- **Decisão**: classe nova `ProductUniqueNameIntegrationTests`, no padrão de `ProductEditArchiveIntegrationTests` (MockMvc + Mongo, condição `TEST_MONGODB_URI`, empresa aleatória por teste). `validatesProductNameAndAcceptsEmptyOptionalContext` passa a criar os 5 produtos com nomes distintos, mantendo a contagem. Simultaneidade testada com requisições paralelas.
- **Rationale**: a constituição exige testes do comportamento novo. É o índice real que garante a regra, então testes só de service não a comprovariam. Os demais testes de produto criam um nome por empresa e não colidem.
- **Alternatives considered**: estender as classes existentes (misturaria escopos de features diferentes).

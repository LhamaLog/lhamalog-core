# Pesquisa e decisões técnicas

## Armazenamento e exibição

- **Decisão**: guardar só o SHA-256 (hex minúsculo dos bytes UTF-8); o token aparece uma vez, na resposta da geração ou da rotação.
- **Rationale**: decisão do desenvolvedor em 12/11/2026. É o que o card pede e o que o Collector implementa: ele calcula o mesmo SHA-256 e busca por `tokenHash` sem decifrar nada. Sem forma reversível, um vazamento do banco não expõe tokens e não há chave de criptografia para administrar.
- **Alternatives considered**: AES-256-GCM + hash e cópia a qualquer momento (ADR_106/113, USID_016.1). Exigiria chave em KMS ou variável de ambiente e mais superfície de ataque. Superada; ver [proposta-adr.md](proposta-adr.md).

## Formato do token

- **Decisão**: `llt_` + 32 bytes de `SecureRandom` em Base64 URL sem padding (47 caracteres).
- **Rationale**: 256 bits de entropia; o prefixo ajuda a reconhecer o token em varreduras de segredo e no `validarSeguranca()` (ADR_064); Base64 URL não tem caracteres que atrapalhem em cabeçalho HTTP ou YAML do Kubernetes.
- **Alternatives considered**: UUID (122 bits, sem prefixo); hex (mais longo).

## Contrato com o Collector

- **Decisão**: gravar em `product_tokens` os campos que o `MongoProductTokenStore` lê (`_id` texto, `productId`, `tokenHash`, `status` `ACTIVE`/`REVOKED`, `createdAt`, `revokedAt`, `expiresAt`), mais campos que ele ignora (`tenantId`, `name`, `createdBy`, `revokedBy`, `replacedBy`, `version`). Índice único em `tokenHash`.
- **Rationale**: o Collector já está implementado e testado; mudar o contrato exigiria mexer no módulo do Michel. A regra de validade dele (`ProductTokenValidity`) exige `ACTIVE` sem `revokedAt` ou `REVOKED` com `revokedAt`; o modelo do Core respeita essas invariantes. Datas `Instant` viram `Date` no Mongo, que o Collector converte.
- **Alternatives considered**: coleção própria do Core com cópia para o Collector (duas fontes da verdade).

## Desencontro da coleção de produtos

- **Decisão**: só sinalizar. O Collector procura `products.productId`; o Core grava `Produto._id`.
- **Rationale**: decisão do desenvolvedor; o nome da coleção é decisão do time com o Michel. Esta feature fica correta do lado de `product_tokens`.
- **Alternatives considered**: o Core manter cópia em `products` (duplica dado e decide pelo time).

## Revogação "em tempo definido"

- **Decisão**: até 30 segundos, sem mudança no Collector.
- **Rationale**: o Collector guarda tokens válidos em cache por 30 s (`DEFAULT_CACHE_TTL`). Depois disso a consulta ao banco vê `REVOKED`. O mesmo vale para o fim da transição da rotação.
- **Alternatives considered**: invalidar o cache do Collector por evento (exige mudança no Collector).

## Rotação

- **Decisão**: cria o token novo e marca no antigo `expiresAt = agora + transição` e `replacedBy = id do novo`. Transição padrão 24 h, de 1 a 168 h. Só token ativo, sem `expiresAt`, pode ser rotacionado. Ordem: grava o novo, depois atualiza o antigo, depois a auditoria.
- **Rationale**: decisão do desenvolvedor (rotação com prazo, como o card). O Collector já recusa token com `expiresAt` no passado, então nada muda nele. Gravar o novo primeiro garante que uma falha no meio nunca deixa o produto sem token válido.
- **Alternatives considered**: só criar e revogar à mão (ADR_107); permitir rotação em cadeia de token já em transição (três tokens válidos ao mesmo tempo).

## Produtor

- **Decisão**: não criar a coleção Produtor; o nome fica no próprio token.
- **Rationale**: decisão do desenvolvedor; o Collector liga o token direto ao produto.
- **Alternatives considered**: Produtor + TokenApi da ADR_122/115.

## Auditoria

- **Decisão**: coleção `ProductTokenAudit`, um documento por operação efetiva: `CREATED`, `REVOKED`, `ROTATED` (com o token novo e o fim da transição). Nunca guarda valor nem hash.
- **Rationale**: critério do card; pendência registrada na SSID_014.1. Revogação repetida não muda nada, então não gera registro.
- **Alternatives considered**: log de aplicação (não consultável pela API, some com o tempo).

## Concorrência e transação

- **Decisão**: `@Version` no documento do token; gravação concorrente vira 409 pelo `ApiExceptionHandler`. Sem transação multi-documento.
- **Rationale**: o Core não usa transações do Mongo hoje. As três gravações têm ordem segura (novo token → antigo → auditoria); o pior caso de falha deixa um token a mais válido e uma auditoria faltando, nunca um produto sem token.
- **Alternatives considered**: transação Mongo (exige replica set em todos os ambientes, inclusive testes).

## Camadas e testes

- **Decisão**: módulo `ingestiontoken` com `application`, `infrastructure` e `api`, e testes unitários com repositórios em memória e relógio controlado, como o módulo `usage`.
- **Rationale**: as regras (validade, rotação, auditoria, recusas) ficam testáveis em milissegundos, sem Mongo; a integração confere só o que depende do banco (formato do documento e índice único).
- **Alternatives considered**: seguir o estilo do módulo `product` (só integração).

## Permissões

- **Decisão**: só administrador da organização (`CompanySession.administrator()`); o restante recebe 403 com mensagem simples e, desde 18/11/2026, com o código `not_admin` (`ApiStatusException`, spec 008, FR-010).
- **Rationale**: ADR_109 (só administradores; negação simples, sem deslogar). O contrato da 001 já diz que 403 de negócio não provoca logout no front.

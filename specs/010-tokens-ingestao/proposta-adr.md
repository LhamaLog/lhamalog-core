# Proposta de ADR (criar no Drive com o próximo número livre)

**Título**: Token de ingestão guardado só como hash, exibido uma vez e rotação com prazo (substitui a ADR_113 e parte da ADR_106 e da ADR_107)

**Status**: Proposto

**Autor**: Pedro Alvaro

**Data**: 12/11/2026

## Contexto

A ADR_106 previa copiar o token de ingestão a qualquer momento, e a ADR_113 resolveu isso guardando o token cifrado com AES-256-GCM, mais um hash SHA-256 como índice. A ADR_107 dizia que não existe regeneração: troca-se criando outro token e revogando o antigo à mão. Depois disso, o card LLT-179 (backlog de 14/10) pediu token exibido uma única vez, guardado só como hash, e rotação com dois tokens válidos durante uma transição. O Collector (LLT-181/182) foi implementado lendo só `tokenHash`, `status`, `revokedAt` e `expiresAt` em `product_tokens`.

## Decisão

- O token é gerado com 32 bytes aleatórios e prefixo `llt_`, exibido só na resposta da geração ou da rotação.
- O banco guarda só o SHA-256 (hex minúsculo dos bytes UTF-8). Não há cópia posterior: quem perdeu o token gera ou rotaciona outro.
- Rotação: cria o token novo e dá ao antigo `expiresAt` = momento da rotação + transição (padrão 24 h, de 1 a 168 h). Os dois valem até o fim da transição.
- O token fica em `product_tokens`, ligado direto ao produto, com nome; a coleção Produtor da ADR_122/115 não é criada.
- Revogação vale no Collector em até 30 s (TTL do cache de tokens válidos).
- Toda geração, revogação e rotação fica em `ProductTokenAudit`.

## Consequências

- Some a necessidade de chave de criptografia (KMS) para tokens.
- A USID_016.1 e a SSID_014.1 precisam ser atualizadas (fim do "copiar a qualquer momento" e do "não existe exibição única").
- Pendente com o Collector: a busca do produto em `products.productId` não encontra os produtos do Core, gravados em `Produto._id`.

Relaciona-se com: ADR_106, ADR_107, ADR_109, ADR_113, ADR_115, ADR_122, ADR_155.

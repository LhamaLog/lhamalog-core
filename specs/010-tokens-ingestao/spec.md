# Feature Specification: Gerar, revogar e rotacionar token de ingestão do produto

**Feature Branch**: `feature/LLT-179-tokens-ingestao`

**Created**: 12/11/2026

**Status**: Draft

**Input**: User description: "Gerar, revogar e rotacionar token de ingestão do produto (LLT-179). Como administrador, quero gerenciar os tokens que os exporters OpenTelemetry usam para enviar logs. Critérios: o token é exibido uma única vez e armazenado apenas como hash; token revogado deixa de ser aceito pelo Coletor em tempo definido; rotação permite dois tokens válidos durante um período de transição; toda operação fica registrada em auditoria. Decisões do desenvolvedor em 12/11/2026: só hash e exibição única (segue o card e o Collector; a ADR_113 precisa ser substituída por ADR nova); rotação com prazo (o token antigo expira sozinho ao fim da transição); o Core grava no formato que o Collector já lê (`product_tokens`) e o desencontro da coleção de produtos (`Produto` × `products`) é só sinalizado; token com nome, sem coleção Produtor."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Gerar token (Priority: P1)

Um DONO ou ADMIN gera um token para um produto da empresa, dando um nome a ele (ex.: "cluster-producao"). O sistema mostra o token completo uma única vez, para ser copiado para o exporter OpenTelemetry. Depois disso, ninguém consegue ver o token de novo: o sistema guarda só o hash.

**Why this priority**: sem token nenhum log entra; é a base das outras histórias.

**Independent Test**: gerar um token, conferir que a resposta traz o valor completo, que a listagem não traz o valor nem o hash e que o banco guarda só o hash, no formato que o Collector lê.

**Acceptance Scenarios**:

1. **Given** um produto ativo e um DONO ou ADMIN, **When** ele gera um token com nome, **Then** a resposta traz o token completo e um aviso de que ele não será exibido de novo.
2. **Given** um token gerado, **When** os tokens do produto são listados, **Then** aparecem nome, situação, datas e autor, sem o valor do token e sem o hash.
3. **Given** um token gerado, **When** o banco é consultado, **Then** existe um documento em `product_tokens` com o SHA-256 do token em hexadecimal minúsculo, situação `ACTIVE` e data de criação, e o valor do token não aparece em lugar nenhum.
4. **Given** um membro comum, **When** ele tenta listar ou gerar tokens, **Then** recebe 403 com mensagem simples, sem encerrar a sessão (ADR_109).
5. **Given** um produto de outra empresa ou inexistente, **When** alguém tenta gerar ou listar tokens, **Then** recebe 404.
6. **Given** um produto arquivado, **When** alguém tenta gerar token, **Then** recebe 409 e nada é criado.
7. **Given** um nome vazio ou com mais de 100 caracteres, **When** alguém tenta gerar token, **Then** recebe 400.

---

### User Story 2 - Revogar token (Priority: P1)

Um DONO ou ADMIN revoga um token. O token passa a revogado, com data e autor, e o Coletor deixa de aceitá-lo em até 30 segundos (tempo do cache do Coletor). Token revogado nunca é apagado.

**Why this priority**: é a resposta a um vazamento de token; precisa existir desde o primeiro dia.

**Independent Test**: gerar, revogar e conferir situação, data, autor e o documento em `product_tokens` com `status = REVOKED` e `revokedAt`.

**Acceptance Scenarios**:

1. **Given** um token ativo, **When** um DONO ou ADMIN o revoga, **Then** ele fica `REVOKED`, com data e autor da revogação, e continua listado.
2. **Given** um token já revogado, **When** ele é revogado de novo, **Then** nada muda e nenhuma auditoria nova é criada.
3. **Given** um produto arquivado, **When** um token dele é revogado, **Then** a revogação é aceita.
4. **Given** um token de outro produto ou inexistente, **When** alguém tenta revogá-lo, **Then** recebe 404.

---

### User Story 3 - Rotacionar token (Priority: P2)

Um DONO ou ADMIN rotaciona um token ativo. O sistema gera um token novo (exibido uma única vez) e marca o antigo para expirar ao fim de um período de transição (padrão 24 horas, de 1 a 168 horas). Durante a transição, os dois tokens são aceitos; depois, só o novo.

**Why this priority**: permite trocar o token sem parar o envio de logs; depende de gerar e revogar.

**Independent Test**: rotacionar com 1 hora, conferir que os dois tokens são válidos agora e que o antigo deixa de ser válido depois de 1 hora (relógio controlado no teste).

**Acceptance Scenarios**:

1. **Given** um token ativo sem prazo, **When** ele é rotacionado sem informar período, **Then** surge um token novo exibido uma vez, e o antigo ganha data de expiração 24 horas à frente e referência ao novo.
2. **Given** a rotação com período de 1 hora, **When** se passa menos de 1 hora, **Then** os dois tokens são válidos; **When** se passa 1 hora, **Then** só o novo é válido.
3. **Given** um token revogado ou expirado, **When** alguém tenta rotacioná-lo, **Then** recebe 409.
4. **Given** um token já em transição, **When** alguém tenta rotacioná-lo de novo, **Then** recebe 409.
5. **Given** um período fora de 1 a 168 horas, **When** alguém tenta rotacionar, **Then** recebe 400 e nada muda.
6. **Given** um produto arquivado, **When** alguém tenta rotacionar, **Then** recebe 409.

---

### User Story 4 - Auditoria (Priority: P2)

Toda geração, revogação e rotação fica registrada: ação, token, autor e momento; na rotação, também o token novo e o fim da transição. Um DONO ou ADMIN consulta a auditoria do produto, da mais recente para a mais antiga. A auditoria nunca contém o valor nem o hash do token.

**Why this priority**: critério do card e base para investigar uso indevido.

**Independent Test**: gerar, rotacionar e revogar; consultar a auditoria e conferir três registros na ordem certa, sem valor ou hash.

**Acceptance Scenarios**:

1. **Given** uma geração, uma rotação e uma revogação, **When** a auditoria é consultada, **Then** existem os registros `CREATED`, `ROTATED` e `REVOKED` (e o `CREATED` do token novo), do mais recente para o mais antigo.
2. **Given** um membro comum ou outra empresa, **When** consulta a auditoria, **Then** recebe 403 ou 404.

---

### Edge Cases

- Dois administradores revogam e rotacionam o mesmo token ao mesmo tempo: uma operação vence e a outra recebe 409 (controle de versão).
- Token expirado aparece com situação "expirado" na listagem, mesmo continuando `ACTIVE` no banco (é assim que o Collector entende expiração).
- Falha no meio da rotação: o token novo é gravado antes de o antigo ganhar prazo; se a segunda etapa falhar, os dois continuam válidos (nunca fica sem token).
- O valor do token nunca é registrado em log, auditoria ou resposta, exceto na resposta da geração e da rotação.
- Produto sem tokens: lista vazia.
- Desencontro conhecido: o Collector procura o produto na coleção `products` pelo campo `productId`, e o Core grava em `Produto` com `_id`. Até o time resolver, os tokens ficam corretos em `product_tokens`, mas o Collector não acha o produto.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Só DONO e ADMIN (papel administrador da organização) MUST listar, gerar, revogar, rotacionar tokens e consultar a auditoria; os demais MUST receber 403 com mensagem simples.
- **FR-002**: Produto de outra empresa ou inexistente MUST responder 404 em todas as operações.
- **FR-003**: O token MUST ser gerado com fonte criptograficamente segura (32 bytes aleatórios), com prefixo `llt_`, e MUST ser exibido só na resposta da geração ou da rotação.
- **FR-004**: O sistema MUST guardar apenas o SHA-256 do token, em hexadecimal minúsculo dos bytes UTF-8, nunca o valor nem uma forma reversível.
- **FR-005**: Cada token MUST ser gravado em `product_tokens` com os campos que o Collector lê (`_id`, `productId`, `tokenHash`, `status`, `createdAt`, `revokedAt`, `expiresAt`), mais `tenantId`, `name`, `createdBy`, `revokedBy`, `replacedBy` e versão.
- **FR-006**: O token MUST ter nome obrigatório, de 1 a 100 caracteres, sem espaços nas pontas.
- **FR-007**: Produto arquivado MUST recusar geração e rotação com 409; listagem, revogação e auditoria continuam permitidas.
- **FR-008**: Revogar MUST mudar a situação para `REVOKED` e preencher data e autor; revogar de novo MUST NOT mudar nada.
- **FR-009**: Token revogado MUST NOT ser apagado.
- **FR-010**: Rotacionar MUST gerar um token novo e dar ao antigo uma data de expiração igual ao momento da rotação mais o período de transição (padrão 24 horas; permitido de 1 a 168 horas), além da referência ao token novo.
- **FR-011**: Só token ativo, não revogado, não expirado e sem expiração marcada MUST poder ser rotacionado; os demais MUST receber 409.
- **FR-012**: Toda geração, revogação efetiva e rotação MUST gerar registro de auditoria com ação, token, produto, empresa, autor e momento; na rotação, também o token novo e o fim da transição. A auditoria MUST NOT conter valor nem hash.
- **FR-013**: A listagem MUST mostrar a situação calculada: ativo, em transição (expiração futura), expirado ou revogado.
- **FR-014**: Operações concorrentes sobre o mesmo token MUST resultar em uma vitória e um 409, sem estado misturado.
- **FR-015**: Esta feature MUST NOT alterar o Collector nem a coleção de produtos.

### Key Entities

- **Token de ingestão**: credencial de um produto para o envio OTLP; tem nome, hash, situação, datas, autores e, na rotação, prazo e referência ao sucessor.
- **Registro de auditoria**: fato imutável de uma operação sobre token.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% das gerações e rotações, o valor do token aparece só na resposta daquela operação; listagem, auditoria e banco nunca o contêm.
- **SC-002**: 100% dos documentos gravados em `product_tokens` passam na regra de validade do Collector quando ativos e falham nela quando revogados ou expirados.
- **SC-003**: Em 100% das rotações, os dois tokens são válidos durante a transição e só o novo depois dela.
- **SC-004**: 100% das operações efetivas geram exatamente um registro de auditoria por token afetado.
- **SC-005**: 100% das tentativas por membro comum ou de outra empresa são recusadas (403 ou 404).

## Assumptions

- "Tempo definido" para a revogação = até 30 segundos, o TTL do cache de tokens válidos do Collector (`MongoProductTokenStore`); nada muda no Collector.
- As ADR_106 e ADR_113 (token copiável, cifrado com AES-256-GCM) e a USID_016.1/SSID_014.1 são superadas por decisão do desenvolvedor a favor do card e do Collector. A ADR_107 (sem regeneração) é superada pela rotação com prazo. Proposta de ADR nova em [proposta-adr.md](proposta-adr.md).
- O modelo Produtor + TokenApi da ADR_122/115 não é criado: o Collector liga o token direto ao produto.
- Cadastro, arquivamento, papéis e isolamento das features 001 e 002 permanecem válidos.
- Fora de escopo: tela no front, mudança no Collector, correção da coleção `products`, bloqueio de ingestão de produto arquivado, rotação automática por agendamento e notificação de token prestes a expirar.

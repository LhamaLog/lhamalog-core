# Feature Specification: Aviso em tempo real de ocorrências novas

**Feature Branch**: `LLT-137-atualizacao-em-tempo-real`

**Created**: 25/11/2026

**Status**: Draft

**Input**: User description: "Feature LLT-137 (Atualização em tempo real, épico LLT-104): LLT-208 atualizar a lista quando surgir ocorrência nova. Nova ocorrência aparece ou é sinalizada em poucos segundos via WebSocket; se a conexão cair, a página reconecta e sincroniza."

## Contexto

- O Módulo Ocorrência (spec 013) grava os achados do Finder; a tela (spec 012/LLT-135 e spec 014/LLT-136) só vê o que existe quando consulta.
- Esta branch sai da `LLT-136-ciclo-de-vida` (PR #21, empilhado sobre o #20 e o #19). O PR desta feature é empilhado sobre o #21.
- A tela fica na spec `014-tempo-real-ocorrencias` do lhamalog-web.

## Clarifications

### Session 2026-11-25

- Q: Como a lista reage a uma ocorrência nova? → A: Com um aviso "N ocorrências novas · Atualizar" no topo; a lista só muda quando a pessoa clica (Felipe, 25/11).
- Q: O que conta como novidade? → A: Ocorrência criada pelo Finder e ocorrência que voltou a ficar Aberta por um achado novo (reaberta ou desarquivada). Aumento de contagem de uma que já está aberta não avisa (Felipe, 25/11).
- Q: Como o navegador se autentica no WebSocket? → A: WebSocket simples; logo depois de conectar, a tela manda o token da Clerk numa mensagem, e o Core o valida como nas rotas `/api`. O token não vai na URL (Felipe, 25/11).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receber o aviso de ocorrência nova (Priority: P1)

Com a tela de ocorrências aberta, a pessoa fica conectada ao Core. Quando o Finder grava uma ocorrência nova ou reabre uma, de um produto que ela vê, o Core a avisa em poucos segundos.

**Why this priority**: é o pedido do card; sem o aviso, a pessoa só descobre a ocorrência recarregando.

**Independent Test**: conectar, autenticar, entregar um resumo pelo Finder e conferir a mensagem recebida.

**Acceptance Scenarios**:

1. **Given** uma conexão autenticada de quem vê o produto P, **When** o Finder grava uma ocorrência nova de P, **Then** a conexão recebe em até 3 s um aviso com o tipo de mudança (`CREATED`), o produto, o tipo da ocorrência e o id.
2. **Given** a mesma conexão, **When** o Finder reabre uma ocorrência FECHADA ou desarquiva uma arquivada de P, **Then** chega um aviso `REOPENED`.
3. **Given** a mesma conexão, **When** o Finder só aumenta a contagem de uma ocorrência que já estava ABERTA ou EM_ANALISE e ativa, **Then** nenhum aviso chega.
4. **Given** um membro sem acesso ao produto P, ou uma pessoa de outra empresa, **When** surge ocorrência de P, **Then** ela não recebe aviso.
5. **Given** uma entrega recusada (409, 422) ou repetida (200 com o mesmo recibo), **When** ela termina, **Then** nenhum aviso é enviado.
6. **Given** vários Cores atrás de um balanceador, **When** a entrega cai num Core e a pessoa está conectada a outro, **Then** o aviso chega do mesmo jeito.

---

### User Story 2 - Conexão autenticada e segura (Priority: P1)

A conexão só recebe avisos depois de mandar um token válido de uma sessão com empresa ativa. O token expira em pouco tempo, então a tela manda um token novo de tempos em tempos; sem isso, o Core encerra a conexão.

**Why this priority**: os avisos dizem que há ocorrências em produtos da empresa; não podem vazar.

**Independent Test**: conectar sem token, com token inválido, com token de empresa sem Tenant e com token válido; deixar o token expirar sem renovar.

**Acceptance Scenarios**:

1. **Given** uma conexão nova, **When** a primeira mensagem não chega em 10 s ou não é uma autenticação, **Then** o Core fecha a conexão com o código 4401, sem enviar nada.
2. **Given** um token inválido, expirado, sem empresa ativa, de empresa sem Tenant ou de membro desativado, **When** ele é enviado, **Then** o Core fecha com 4401 (ou 4403 quando o token é válido mas o acesso é negado), sem revelar o motivo além do código.
3. **Given** um token válido, **When** ele é enviado, **Then** o Core responde `ready` e passa a enviar avisos.
4. **Given** uma conexão autenticada, **When** o token expira e nenhum token novo chega em até 30 s, **Then** o Core fecha com 4401; um token novo da mesma pessoa e empresa mantém a conexão.
5. **Given** um token novo de outra pessoa ou outra empresa na mesma conexão, **When** ele chega, **Then** o Core fecha com 4403.
6. **Given** uma conexão aberta de uma origem que não é a do web, **When** ela tenta, **Then** o aperto de mão é recusado.

---

### User Story 3 - Reconectar e sincronizar (Priority: P2)

Se a conexão cair (rede, Core reiniciado), a tela reconecta sozinha. Esta parte fica toda no web (spec 014 do lhamalog-web); o Core só precisa aceitar a reconexão como uma conexão nova.

**Independent Test**: derrubar o Core com a tela aberta e subir de novo.

**Acceptance Scenarios**:

1. **Given** o Core reiniciado, **When** a tela reconecta e se autentica, **Then** a conexão volta a receber avisos.

---

### Edge Cases

- Membro perde o acesso ao produto (LLT-120) com a conexão aberta: deixa de receber avisos daquele produto em até 1 minuto.
- Resumo com 1.000 achados novos: um aviso por ocorrência, sem travar a entrega (a publicação acontece depois do recibo e não atrasa a resposta ao Finder).
- Redis indisponível: a entrega do Finder continua e responde normalmente; o aviso se perde, com WARN; a tela recupera ao sincronizar.
- Muitas conexões da mesma pessoa (várias abas): cada uma recebe os avisos.
- Mensagem do cliente que não é autenticação (ou JSON inválido): ignorada depois de autenticada; antes, fecha com 4401.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O Core MUST oferecer um WebSocket em `/ws/occurrences` para a tela de ocorrências.
- **FR-002**: A conexão MUST se autenticar com uma mensagem `auth` contendo o token de sessão da Clerk em até 10 s; a validação MUST ser a mesma das rotas `/api` (assinatura, emissor, partes autorizadas, sessão ativa, empresa ativa com Tenant, membro não desativado). Falha MUST fechar com 4401 (token) ou 4403 (acesso).
- **FR-003**: A conexão MUST aceitar `auth` de novo para renovar o token; o token renovado MUST ser da mesma pessoa e empresa. Token expirado sem renovação em 30 s MUST fechar a conexão.
- **FR-004**: O aperto de mão MUST aceitar só as origens autorizadas do web (as mesmas do CORS).
- **FR-005**: Depois de cada entrega aceita (201) do Módulo Ocorrência, o Core MUST publicar um aviso para cada ocorrência criada (`CREATED`) e para cada uma que passou a ABERTA vindo de outro status ou de arquivada (`REOPENED`); entregas recusadas ou repetidas MUST NOT publicar.
- **FR-006**: O aviso MUST chegar a todas as conexões autenticadas da empresa que veem o produto, em qualquer instância do Core, e MUST NOT chegar a outras.
- **FR-007**: O aviso MUST conter só o tipo de mudança, o id da ocorrência, o produto e o tipo da ocorrência (ERROR, WARNING, INFO); MUST NOT conter título, assinatura, trechos de log ou dados do tenant além do produto.
- **FR-008**: Falha ao publicar MUST NOT falhar a entrega do Finder.
- **FR-009**: Os logs MUST seguir a constituição VI (`occurrence.stream.*`), sem token nem conteúdo de ocorrência.

### Key Entities

- **Aviso de ocorrência**: tipo de mudança (`CREATED`, `REOPENED`), id da ocorrência, produto, tipo e data.
- **Conexão de tela**: pessoa, empresa, produtos que ela vê (recalculados periodicamente) e validade do token.

## Success Criteria *(mandatory)*

- **SC-001**: Em 100% dos testes, a conexão que vê o produto recebe o aviso em até 3 s depois da resposta 201 ao Finder.
- **SC-002**: Em 100% dos testes, conexões de outra empresa ou sem acesso ao produto não recebem nada.
- **SC-003**: Em 100% dos testes, conexão sem autenticação válida não recebe nenhum aviso e é fechada.
- **SC-004**: A resposta ao Finder não fica mais lenta que 50 ms por causa da publicação, com 100 achados.

## Assumptions

- O Redis do Core (já usado nas cotas e nas notificações) leva os avisos entre instâncias (pub/sub); um aviso perdido é aceitável, porque a tela sincroniza ao reconectar e o aviso não é a fonte da verdade.
- Só mudanças feitas pelo Finder geram aviso; as ações da LLT-136 são da própria pessoa e já aparecem na tela dela.
- A sessão da Clerk dura cerca de 60 s por token; a tela renova a cada 50 s.
- Outras telas (painel, notificações) não usam este canal nesta entrega.

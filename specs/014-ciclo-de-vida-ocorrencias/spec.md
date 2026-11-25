# Feature Specification: Ciclo de vida da ocorrência (alterar status e arquivar)

**Feature Branch**: `LLT-136-ciclo-de-vida`

**Created**: 25/11/2026

**Status**: Draft

**Input**: User description: "Feature LLT-136 (Ciclo de vida, épico LLT-104 Ocorrências): LLT-206 alterar status da ocorrência, só com os status da lista aprovada e registrando quem fez e quando; LLT-207 arquivar ocorrência, que sai da lista padrão, aparece no filtro de arquivadas e pode ser desarquivada. Continua as specs 012 (consulta, LLT-135) e 013 (gravação pelo Módulo Ocorrência, LLT-293)."

## Contexto

- A consulta (spec 012) lista e detalha ocorrências; o Módulo Ocorrência (spec 013) grava os achados do Finder com revisão e reabre FECHADA como ABERTA.
- Esta branch sai da `LLT-293-…` (PR #20, empilhado sobre o #19). O PR desta feature é empilhado sobre o #20.
- A tela fica na spec `013-ciclo-de-vida-ocorrencias` do lhamalog-web.

## Clarifications

### Session 2026-11-25

- Q: Quais status e mudanças valem (o card cita "Em análise, Resolvida, Não resolvida")? → A: Os da ADR_082, que o Core e o Finder já usam: ABERTA, EM_ANALISE e FECHADA. O usuário pode ir de qualquer um para qualquer outro, inclusive reabrir FECHADA. Na tela: Aberta, Em análise e Fechada. O contrato com o Finder não muda (Felipe, 25/11).
- Q: Quem pode alterar o status e arquivar? → A: Quem vê o produto da ocorrência: Dono, Administrador e o Membro com acesso ao produto. Entra uma permissão nova na matriz da LLT-120 para os três papéis (Felipe, 25/11).
- Q: E se o Finder mandar um achado novo de uma ocorrência arquivada? → A: Ela é desarquivada e reaberta como ABERTA, com o registro no histórico, para o problema não ficar escondido (Felipe, 25/11).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Alterar o status de uma ocorrência (Priority: P1)

No detalhe de uma ocorrência, o usuário escolhe outro status entre Aberta, Em análise e Fechada. A mudança vale na hora, aparece no histórico com o nome de quem fez e a data, e a lista passa a mostrar o status novo.

**Why this priority**: é o primeiro passo para tratar uma ocorrência; sem ele a tela só observa.

**Independent Test**: mudar o status de uma ocorrência e conferir o detalhe, o histórico e o filtro de status da lista.

**Acceptance Scenarios**:

1. **Given** uma ocorrência ABERTA de um produto que o usuário vê, **When** ele muda para EM_ANALISE informando a revisão que está vendo, **Then** a resposta traz o detalhe com status EM_ANALISE, revisão nova e o histórico com `{ABERTA → EM_ANALISE}`, a data e o autor (tipo usuário, id do usuário).
2. **Given** uma ocorrência FECHADA, **When** o usuário muda para ABERTA, **Then** ela é reaberta com o registro no histórico.
3. **Given** um status fora da lista (por exemplo, RESOLVIDA), **When** o usuário tenta mudar, **Then** a resposta é 400 com o erro no campo `status`, sem mudança.
4. **Given** o mesmo status que a ocorrência já tem, **When** o usuário confirma, **Then** a resposta é o detalhe sem mudança e sem entrada nova no histórico.
5. **Given** que o Finder ou outra pessoa mudou a ocorrência depois que o usuário abriu o detalhe, **When** ele tenta mudar o status com a revisão antiga, **Then** a resposta é 409 `occurrence_changed` e nada muda.
6. **Given** uma ocorrência de outra empresa, de produto sem acesso ou inexistente, **When** o usuário tenta mudar, **Then** a resposta é 404, igual à do detalhe.
7. **Given** a mudança feita, **When** o Finder entrega um resumo com a revisão que consultou antes, **Then** recebe `409 REVISAO_DESATUALIZADA` e consulta de novo (o contrato DN-009-2 já prevê isso).

---

### User Story 2 - Arquivar e desarquivar (Priority: P2)

O usuário arquiva uma ocorrência que não interessa mais. Ela sai da lista padrão e aparece quando ele escolhe ver as arquivadas, de onde pode desarquivar.

**Why this priority**: limpa a lista; depende de nada além da consulta, mas vale menos que tratar o status.

**Independent Test**: arquivar, conferir a lista padrão e a lista de arquivadas, desarquivar.

**Acceptance Scenarios**:

1. **Given** uma ocorrência ativa, **When** o usuário arquiva com a revisão atual, **Then** ela fica com a data e o autor do arquivamento, sai da lista padrão e do total, e aparece na lista com `archived=true`.
2. **Given** uma ocorrência arquivada, **When** o usuário desarquiva, **Then** ela volta para a lista padrão, com o mesmo status de antes.
3. **Given** uma ocorrência já arquivada, **When** o usuário arquiva de novo, **Then** a resposta é o detalhe sem mudança (o mesmo vale para desarquivar uma ativa).
4. **Given** a lista com `archived=true` e outros filtros, **When** o usuário pagina, **Then** só arquivadas que satisfazem os filtros aparecem, com o total delas.
5. **Given** uma ocorrência arquivada, **When** alguém abre o detalhe pelo link, **Then** o detalhe aparece, com a indicação de arquivada.
6. **Given** revisão desatualizada, ocorrência de outra empresa ou sem acesso, **When** o usuário arquiva ou desarquiva, **Then** valem as mesmas respostas da US1 (409 e 404).

---

### User Story 3 - Achado novo desarquiva e reabre (Priority: P2)

Quando o Finder grava um achado novo de uma ocorrência arquivada, o Módulo desarquiva e reabre como ABERTA, registrando a reabertura no histórico, para o problema voltar a aparecer.

**Why this priority**: sem isso, arquivar esconderia um problema que voltou a acontecer.

**Independent Test**: arquivar uma ocorrência, entregar um resumo com achado dela e conferir a lista padrão e o histórico.

**Acceptance Scenarios**:

1. **Given** uma ocorrência arquivada com status EM_ANALISE, **When** o Finder entrega um achado dela com a revisão atual, **Then** ela fica sem arquivamento, com status ABERTA e o histórico com `{EM_ANALISE → ABERTA}` feito pelo sistema.
2. **Given** uma ocorrência arquivada e FECHADA, **When** chega um achado, **Then** ela é desarquivada e reaberta com uma única entrada nova no histórico (`FECHADA → ABERTA`).
3. **Given** uma ocorrência ativa ABERTA ou EM_ANALISE, **When** chega um achado, **Then** nada muda no status nem no arquivamento (regra da spec 013).

---

### Edge Cases

- Duas pessoas mudam o status ao mesmo tempo com a mesma revisão: uma muda, a outra recebe 409 e recarrega.
- O Finder grava entre a abertura do detalhe e o clique: 409 para o usuário; ele recarrega e vê a contagem nova.
- Documento inválido para a leitura da spec 012 (sem status, por exemplo): continua 404 no detalhe, e as ações também respondem 404.
- Membro perde o acesso ao produto (LLT-120) entre abrir o detalhe e agir: 404.
- Produto arquivado: as ocorrências dele podem mudar de status e ser arquivadas normalmente.
- O status FECHADA não arquiva, e arquivar não fecha: são independentes.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O Core MUST permitir mudar o status de uma ocorrência para ABERTA, EM_ANALISE ou FECHADA, de qualquer um para qualquer outro; outro valor MUST responder 400 com erro no campo `status`.
- **FR-002**: Cada mudança de status MUST entrar no histórico com o status anterior, o novo, a data e o autor (`changedBy.type = USER`, `id` = id do usuário da sessão). Mudar para o mesmo status MUST NOT criar entrada.
- **FR-003**: O Core MUST permitir arquivar e desarquivar uma ocorrência, guardando a data e o autor do arquivamento; arquivar MUST NOT mudar o status, e desarquivar MUST NOT mudar o status.
- **FR-004**: A lista padrão (sem `archived`, ou `archived=false`) MUST NOT incluir arquivadas nem contá-las no total; `archived=true` MUST listar só as arquivadas, combinando com os demais filtros e a paginação da spec 012.
- **FR-005**: O detalhe MUST informar a revisão atual e, quando arquivada, a data e o autor do arquivamento. A lista MUST informar se cada item está arquivado.
- **FR-006**: Toda ação (status, arquivar, desarquivar) MUST receber a revisão que o usuário está vendo e MUST ser recusada com 409 `occurrence_changed` quando ela não for a atual, sem mudar nada. Toda ação que muda a ocorrência MUST gerar uma revisão nova.
- **FR-007**: As ações MUST valer para Dono, Administrador e Membro com acesso ao produto da ocorrência, pela mesma regra de visibilidade do detalhe; fora dela, 404 igual ao do detalhe. A matriz de permissões da LLT-120 MUST ganhar a permissão de gerenciar ocorrências para os três papéis.
- **FR-008**: Um achado do Finder para uma ocorrência arquivada MUST desarquivá-la e deixá-la ABERTA, com uma entrada `SYSTEM` no histórico quando o status mudar (spec 013, regra de reabertura ampliada).
- **FR-009**: Os logs MUST seguir a constituição VI, com eventos `occurrence.status.changed`, `occurrence.archived`, `occurrence.unarchived` e `occurrence.action.stale`, sem título, assinatura ou trechos de log.

### Key Entities

- **Ocorrência**: ganha o arquivamento (data e autor) e passa a ter mudanças de status feitas por usuários no histórico.
- **Mudança de status**: já existe (spec 012); agora também com autor do tipo usuário.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos testes, uma mudança de status aparece no detalhe e no histórico com autor e data, e o filtro de status da lista reflete o status novo.
- **SC-002**: Em 100% dos testes, uma ocorrência arquivada não aparece na lista padrão nem no total, e aparece com `archived=true`.
- **SC-003**: Em 100% dos testes com revisão desatualizada, nada muda.
- **SC-004**: Em 100% dos testes, um achado novo de ocorrência arquivada a traz de volta para a lista padrão como ABERTA.
- **SC-005**: Uma ação responde em até 500 ms com o Core local.

## Assumptions

- Os rótulos "Resolvida" e "Não resolvida" do card ficam fora; os status são os da ADR_082 (clarify).
- Não há comentário ou motivo na mudança de status nesta entrega.
- O histórico guarda o id do usuário (Clerk); a tela mostra o nome quando conseguir resolvê-lo pela organização, senão o id.
- Mudança em lote (várias ocorrências de uma vez) fica fora.
- A atualização em tempo real da lista é a LLT-137/LLT-208.

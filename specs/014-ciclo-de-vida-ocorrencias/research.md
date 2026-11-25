# Research: Ciclo de vida da ocorrência

**Feature**: `014-ciclo-de-vida-ocorrencias` | **Data**: 25/11/2026

Fontes: cards LLT-136, LLT-206 e LLT-207; specs 012 e 013 deste repositório; `ProductController` (arquivar produto, spec 002); `Permission` e `AccessService` (LLT-120); clarify de 25/11.

## R1. Status

- **Decisão**: os três status da ADR_082 (`ABERTA`, `EM_ANALISE`, `FECHADA`), com qualquer transição. Mesmo status → sem mudança e sem histórico (idempotente). Valor desconhecido → 400 `fieldErrors.status` "Status desconhecido. Use ABERTA, EM_ANALISE ou FECHADA." (mesmo texto do filtro da spec 012).
- **Motivo**: clarify; o Finder e o Módulo já usam esses valores, e a reabertura FECHADA → ABERTA da spec 013 continua igual.
- **Alternativas**: status novos do card (Resolvida, Não resolvida), rejeitados no clarify por mudarem o contrato com o Finder.

## R2. Concorrência

- **Decisão**: o cliente manda a `revision` que está vendo (devolvida no detalhe). A ação é `updateOne({_id, tenantId, revision}, …)` que também grava uma revisão nova (UUID). Zero documentos alterados → reler: se a ocorrência não existe ou não é visível, 404; senão, 409 `occurrence_changed` "A ocorrência mudou desde que você abriu. Recarregue e tente de novo." Documento sem `revision` usa o valor `sem-revisao` da spec 013.
- **Motivo**: o mesmo controle otimista do arquivar produto (`version`) e da entrega do Finder (`revisaoConsultada`); como a revisão muda, uma entrega do Finder baseada na consulta anterior recebe `409 REVISAO_DESATUALIZADA` e consulta de novo, sem perder a mudança do usuário.
- **Alternativas**: aplicar sem revisão (a última escrita ganharia: o Finder poderia apagar o status do usuário ao trocar o documento inteiro).

## R3. Rotas

- **Decisão**: `PUT /api/occurrences/{id}/status` `{status, revision}`; `POST /api/occurrences/{id}/archive` e `POST /api/occurrences/{id}/unarchive` `{revision}`. Todas respondem 200 com o detalhe atualizado.
- **Motivo**: mesmo formato de arquivar produto (`POST /{id}/archive` e `/unarchive` com a versão no corpo); o detalhe na resposta evita uma segunda chamada na tela.

## R4. Arquivamento

- **Decisão**: campos `archivedAt` (data) e `archivedBy` (`{type: USER, id}`) no documento; desarquivar remove os dois. Arquivar não muda o status. Lista: `archived` ausente ou `false` → `archivedAt` ausente ou nulo; `true` → `archivedAt` presente. Arquivar a já arquivada (ou desarquivar a ativa) → 200 sem mudança e sem trocar a revisão.
- **Motivo**: card LLT-207 (sai da lista padrão, aparece no filtro de arquivadas, pode ser desarquivada); campos ausentes nos documentos antigos valem como ativos, sem migração.

## R5. Achado de ocorrência arquivada (spec 013)

- **Decisão**: no `OccurrenceMerge`, se o documento antes tem `archivedAt`, o depois fica sem `archivedAt` e `archivedBy` e com status `ABERTA`; se o status anterior não era `ABERTA`, entra uma única mudança `{anterior → ABERTA}` com `SYSTEM`. Para ocorrência ativa, a regra da spec 013 não muda (só FECHADA ou desconhecido reabre).
- **Motivo**: clarify ("desarquiva e reabre").

## R6. Permissão

- **Decisão**: `Permission.OCCURRENCES_MANAGE` com Dono, Administrador e Membro e a nota "MEMBER só nos produtos liberados", como `PRODUCTS_EDIT`. A checagem efetiva é a visibilidade do detalhe (`OccurrenceService.visibleProducts`), que já limita o Membro aos produtos liberados; a permissão aparece na matriz (`GET /api/company/access`) para o Web.
- **Motivo**: clarify; reaproveita a regra de acesso sem duplicá-la.

## R7. Autor

- **Decisão**: `changedBy = {type: USER, id: <clerkUserId da sessão>}` (formato da spec 012). Os logs usam `userHash` (`UserHash.of`, LLT-120), não o id.
- **Motivo**: o card pede "quem fez e quando"; o id em claro fica só no dado, que a tela já mostra.

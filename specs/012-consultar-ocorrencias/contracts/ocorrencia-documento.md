# Contrato: documento da Ocorrência (Módulo Ocorrência → coleção `Ocorrencia`)

**Status**: alinhado em 22/11/2026 ao contrato Finder → Módulo aprovado em 20/11 (DN-009-2 e N3 A, `lhamalog-finder/specs/009-publicar-evento-ocorrencia/contracts/proposta-dn-009-2.openapi.yaml`). O Módulo Ocorrência é do Felipe; o Finder é do João.

O Core lê a coleção `Ocorrencia` para a lista e o detalhe da LLT-135. O Módulo grava a partir do `ResumoAnalise`; o Core nunca grava fora da carga local de exemplo.

**Implementação (25/11/2026)**: o Módulo Ocorrência mora no próprio Core e grava este formato desde a spec 013 (`specs/013-gravar-ocorrencias`, LLT-293). Nomes com ponto em `extractedFields` (`http.route`) são gravados como vêm, pelo driver do MongoDB, porque o `insert` mapeado do Spring Data os recusa.

## Formato

Ver a tabela de campos e a coluna "Origem" em [../data-model.md](../data-model.md). Regras que o Módulo MUST garantir ao gravar:

1. `tenantId` e `productId` são os ids internos do Core, os mesmos que o Coletor resolve pelo token e o Finder repassa. O Módulo confere que o produto pertence ao tenant.
2. Uma ocorrência por (`tenantId`, `productId`, `signature`, `type`). O Core cria o índice único `occurrence_unique_key`. Mudar só a `versaoRegras` atualiza a mesma ocorrência.
3. `count`, `firstSeenAt` e `lastSeenAt` recebem os valores do achado como vieram: o Finder já somou com a contagem consultada. O Módulo não soma de novo; ele confere a `revisaoConsultada` contra `revision` (409 `REVISAO_DESATUALIZADA` se divergir).
4. `type` e `status` em maiúsculas, nos valores do data-model. Achado novo cria a ocorrência com `ABERTA`; achado de ocorrência `FECHADA` reabre para `ABERTA`. As duas mudanças entram no `statusHistory` com `changedBy.type = SYSTEM`.
5. `title` e `summary` recebem `titulo` e `resumo` quando não forem `null`; com `null`, o valor anterior é mantido.
6. `samples` recebe as `linhas` do achado no fim da lista, mantendo só as 20 mais recentes. `linhas: []` não apaga os exemplos anteriores.
7. `hostName`, `traceId`, `correlationId`, `moduleId`, `lineNumber`, `extractedFields` e `sensitiveData` recebem o valor do último achado quando ele vier preenchido.
8. `recurring` recebe o `recorrente` do último achado.
9. `revision` muda a cada gravação da ocorrência.
10. Documentos fora deste formato ficam invisíveis para o usuário, e o detalhe gera `WARN event=occurrence.read.invalid` no Core.

## Fora deste contrato

- Rotas `/internal/v1/ocorrencias/consultas` e `/internal/v1/resumos-analise`, JWT interno, recibo e idempotência: feature do Módulo (gravação), não da LLT-135.
- Retenção dos recibos, agendamento de reenvio e aviso de falha do OpenObserve (ADR_051): em aberto do lado do Finder e da operação.
- Gravidade/criticidade (Módulo, ADR_062) e arquivamento (LLT-207).

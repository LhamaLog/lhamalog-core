# Research: Manter dados da empresa

## Controle de administrador

- **Decision**: Reusar `CompanySession.administrator()`. O resolver já lê `clerk.administrator-roles` (`CLERK_ADMIN_ROLES`, padrão `admin`), separa a lista por vírgula e compara o slug do claim `o.rol` (fallback `org_role`) depois de remover o prefixo `org:`.
- **Rationale**: A regra pedida já existe e já cobre o slug configurado. Nome exibido não entra nessa comparação.
- **Alternatives considered**: Criar outro papel, outra propriedade ou uma checagem só pelo texto `admin`. Isso duplicaria a configuração e ignoraria slugs acrescentados em `CLERK_ADMIN_ROLES`.

## Onde a recusa acontece

- **Decision**: `CompanyController` chama a exigência de administrador no início de `PUT /api/company`, `POST /api/company/logo`, `DELETE /api/company/logo` e `DELETE /api/company`, antes de interpretar corpo, arquivo ou versão. A resposta é 403 com mensagem própria, diferente de "Selecione uma empresa para continuar." `CompanyService` segue dono da gravação, da validação e da exclusão dos documentos.
- **Rationale**: O membro precisa receber 403 mesmo com JSON inválido ou arquivo ausente. Os testes de unidade de `save` e `saveLogo` continuam exercitando validação e versão sem ganhar um parâmetro de papel.
- **Alternatives considered**: Exigir o papel dentro de `CompanyService.save`. Seria o único caminho de escrita, mas obrigaria a reescrever os testes de unidade que hoje gravam direto no serviço. Filtro HTTP por verbo também recusaria CEP, PDF e sincronização, que a spec mantém como estão.

## Exclusão do cadastro

- **Decision**: `DELETE /api/company` remove, para o id da organização da sessão, o documento `Tenant`, o documento `TenantLogo` e as revisões `TenantContextRevision`. A resposta é 200 com a mesma representação que `GET /api/company` devolve quando nada foi gravado: `version` nula e campos de cadastro vazios. Se o documento já não existe, a operação responde 200 com essa representação vazia e não altera outra organização.
- **Rationale**: A consulta seguinte precisa mostrar ausência de cadastro, e um novo `PUT` com `version: null` precisa criar o cadastro de novo. Apagar o documento é o que o `load` atual já trata como empresa vazia.
- **Alternatives considered**: Exclusão lógica com flag. A consulta teria de esconder o documento e o novo cadastro teria de reaproveitar a mesma chave com dados velhos ainda armazenados. Resposta 404 impediria o cliente de receber o mesmo estado vazio que o GET já usa.

## Corrida entre exclusão e gravação

- **Decision**: A exclusão não recebe versão. Uma gravação que carregou a versão anterior e chega depois da exclusão continua no `ensureVersion` atual: a empresa carregada de novo está vazia (`version` nula), a versão enviada não confere, e a resposta segue 409. Um cadastro novo envia `version: null`.
- **Rationale**: O conflito de versão já existe em `CompanyService` e já vira 409 em `ApiExceptionHandler`. Não surge um segundo protocolo só para a exclusão.
- **Alternatives considered**: Exigir a versão também no DELETE. A spec trata a exclusão sem cadastro como operação sem efeito, e o administrador não precisa conhecer a versão para esvaziar o cadastro.

## O que permanece

- **Decision**: A exclusão não chama repositório de `Produto` nem de `AcessoProduto` e não chama a Clerk. CEP, PDF e sincronização permanecem 501, inclusive para membro.
- **Rationale**: O esclarecimento de 2026-11-09 limitou a exclusão ao cadastro. A spec deixa os três caminhos indisponíveis fora desta entrega.
- **Alternatives considered**: Apagar o tenant inteiro ou bloquear os caminhos 501 com 403. Os dois aumentam o alcance além da spec.

## Observação operacional

- **Decision**: Sem métrica, log de auditoria ou evento novo. O 403 e o 409 continuam no `ApiExceptionHandler`.
- **Rationale**: A spec não pede telemetria. O cadastro removido deixa de ter `updatedByUserId` porque o documento some.
- **Alternatives considered**: Gravar um evento de exclusão em outra coleção. Seria histórico novo, fora do aceite.

## Testes que já gravam como membro

- **Decision**: Em `CompanyProductIntegrationTests`, as gravações de empresa que hoje usam papel `member` passam a usar `admin`. O `PUT` de membro com corpo inválido passa a esperar 403. A validação 400 fica no fluxo do administrador. Os testes HTTP novos entram em classe com `@Import(TestcontainersConfiguration.class)`, para o `verify` executá-los com `mongo:8.0.34`.
- **Rationale**: Hoje o membro grava a empresa com sucesso. A LLT-175 muda esse comportamento. A classe atual só roda com `TEST_MONGODB_URI`; o portão da constituição é o `verify` com Testcontainers.
- **Alternatives considered**: Deixar a classe antiga como única prova. Sem a variável, o `verify` omite o aceite. Copiar a configuração de container com tag `latest` violaria a constituição.

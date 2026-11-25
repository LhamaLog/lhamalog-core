# Research: Login e organização de teste para desenvolvimento

## R1. Várias organizações na mesma variável

- **Decision**: `DEV_TENANT_CLERK_ORG_ID` (propriedade `lhamalog.dev-tenant.clerk-org-id`) passa a aceitar uma lista separada por vírgula. Uma organização só continua sendo um valor válido.
- **Rationale**: ninguém precisa mudar o `.env` que já existe, e o `compose.yaml` já repassa a variável. Uma variável nova com nome no plural obrigaria todo o time a migrar.
- **Alternatives considered**: variável nova `DEV_TENANT_CLERK_ORG_IDS` (quebra o que existe); arquivo JSON de organizações (exagero para uma lista de identificadores); script de Mongo manual (o que a spec quer evitar, US3).

## R2. Normalização e validação

- **Decision**: o `DevTenantOrganizations` separa por vírgula, tira espaços, descarta itens vazios e repetidos e mantém a ordem. Item que não casa com `^org_[A-Za-z0-9]+$` é descartado, com um WARN por item. Lista vazia, ou só com inválidos, não provisiona nada e não impede a subida.
- **Rationale**: erro de digitação no `.env` não pode derrubar o ambiente de ninguém (FR-004). O padrão segue o formato dos identificadores de organização da Clerk, e o log mostra o valor descartado para facilitar a correção.
- **Alternatives considered**: falhar a subida com valor inválido (pune o caso comum de vírgula sobrando); aceitar qualquer texto (cria Tenant para um id que nunca chega num token, confunde).

## R3. Logs do provisionamento

- **Decision**: o seeder registra um evento por organização, em inglês, no formato da constituição VI:
  - INFO `event=dev_tenant.seed.created | Development tenant created | clerkOrgId=<id> tenantId=<uuid>`
  - INFO `event=dev_tenant.seed.exists | Development tenant already exists | clerkOrgId=<id> status=ACTIVE`
  - WARN `event=dev_tenant.seed.inactive | Development tenant exists but is not active; access stays denied | clerkOrgId=<id> status=<status>`
  - WARN `event=dev_tenant.seed.invalid_org_id | Ignored value that is not a Clerk organization id | value=<valor>`
- **Rationale**: o desenvolvedor confirma o provisionamento pelo log (spec US1, cenários 1 e 3). O Tenant suspenso vira WARN porque explica o 403 que a pessoa vai ver (edge case da spec). O `org_id` não é segredo nem dado pessoal.
- **Alternatives considered**: manter o texto livre atual (viola o princípio VI, que vale para código tocado).

## R4. Proteção fora do perfil local

- **Decision**: manter o `@Profile("local")` no `LocalDevConfig` e provar com um teste de `ApplicationContextRunner`: com o perfil `local` e a variável preenchida, o runner existe; com o perfil `prod` e a mesma variável, não existe nenhum bean do provisionamento.
- **Rationale**: a proteção já existe; faltava evidência automatizada (FR-005, SC-005). O `ApplicationContextRunner` testa a configuração sem subir a aplicação inteira com as variáveis de produção.
- **Alternatives considered**: subir o contexto completo com `prod` (exige todas as variáveis obrigatórias de produção, frágil); checagem em runtime dentro do seeder (duplica a regra do perfil).

## R5. Roteiro de validação com a Clerk real

- **Decision**: o `quickstart.md` desta feature é o roteiro único, com um identificador por item (V1 a V10). O resultado vai num comentário do PR #6, numa tabela com item, data, resultado e observação. Ele usa o Web da `Development` com o PR #7 aplicado e cobre também o webhook pelo túnel (quickstart da spec 003).
- **Rationale**: a constituição exige evidência local antes do merge e não há CI. Os itens exigem conta com segundo fator e chaves que só o desenvolvedor tem.
- **Alternatives considered**: teste automatizado com a Clerk real (exige credenciais no CI, que não existe, e violaria o princípio V); checklist solto no PR (sem identificador estável e fácil de esquecer itens).

## R6. Login por desenvolvedor, e-mails de teste

- **Decision**: cada desenvolvedor usa o próprio login. Contas extras (para membro, convite ou isolamento) usam e-mails com `+clerk_test` e o código fixo `424242` da instância de desenvolvimento. O guia não traz nenhuma conta real.
- **Rationale**: conta compartilhada circularia senha e segundo fator (princípio V). Os e-mails de teste são recurso padrão das instâncias de desenvolvimento da Clerk.
- **Alternatives considered**: conta de demonstração compartilhada no cofre do time (o time não tem cofre definido; fica para quando houver).

## R8. Código em todo 403 de negócio

- **Decision**: `CompanyController.requireAdministrator` (LLT-175) e `ProductService.requireAdministrator` (LLT-123) passam de `ResponseStatusException` para `ApiStatusException` com o código `not_admin`, mantendo o texto atual. Um teste simples, que varre o código-fonte, impede um `ResponseStatusException(HttpStatus.FORBIDDEN` novo em `src/main/java`.
- **Rationale**: o Web (spec 006) separa o 403 de segurança (`{"error":"forbidden"}`, sem código, com revogação) do 403 de negócio pelo código. Sem o código, um membro seria deslogado por tentar uma ação de administrador. O código `not_admin` já existe na LLT-168 com o mesmo sentido.
- **Alternatives considered**: códigos diferentes por tela (`not_company_admin`, `not_product_admin`), que o Web não precisa distinguir; deixar o Web decidir só pela mensagem (o Web também faz isso, por defesa, mas o contrato deve ser explícito).

## R7. Revogação de sessão no roteiro

- **Decision**: o item de 403 do roteiro exige `CLERK_SECRET_KEY` (`sk_test_...`) no `.env` local. O guia avisa que, sem ela, o Web volta ao login, mas a sessão continua válida na Clerk.
- **Rationale**: a revogação é parte do contrato da LLT-163 (ADR_032), e só a chave secreta a habilita.
- **Alternatives considered**: deixar o item como opcional (o contrato ficaria sem evidência).

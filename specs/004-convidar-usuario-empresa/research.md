# Research: Convidar usuário para a empresa

## Terminologia

- **Decision**: Na spec, o nome de negócio é cargo. Os identificadores do convite são `admin` e `member`. Na sessão, o cargo chega em `o.rol` ou, na forma antiga, em `org_role`.
- **Rationale**: A história fala em cargo. O código e a Clerk falam em role. Os dois nomes designam o mesmo dado, não dois cadastros.
- **Alternatives considered**: Usar só "papel" na spec. O texto do Jira e a resposta do produto dizem cargo.

## O que cabe ao Core

- **Decision**: O Core não cria endpoint, coleção, cliente nem tela. A entrega neste repositório é travar, com testes, o tratamento da sessão de quem acabou de aceitar e documentar a configuração do dashboard da Clerk. Envio, lista de pendentes, revogação, reenvio e aceite ficam no Web e na Clerk.
- **Rationale**: A decisão do produto coloca o convite no SDK do navegador. A API só reconhece a pessoa quando a sessão já traz a organização. Um endpoint de convite repetiria a Clerk e pediria segredo.
- **Alternatives considered**: Endpoint no Core que chama a Backend API da Clerk. Exige `CLERK_SECRET_KEY` e um cliente que a LLT-174 não criou. E-mail próprio do Core. Não há biblioteca de e-mail e o aceite ainda precisaria colocar a pessoa na organização da Clerk para a sessão existir.

## Canal e chave secreta

- **Decision**: O Web cria o convite de organização com o SDK do navegador, na sessão do administrador. A Clerk envia o e-mail e aplica validade, revogação e reenvio. O Core não adiciona SDK, cliente HTTP, `JavaMail` nem `CLERK_SECRET_KEY`. `pom.xml`, `application.properties`, `.env.example`, `.env.prod.example`, `compose.yaml` e `compose.prod.yaml` não ganham variável de segredo.
- **Rationale**: A sessão do navegador já está autenticada como administrador da organização. O segredo de backend não entra neste repositório.
- **O que já existe e permanece**:
  - `SecurityConfig` valida o JWT com `clerk.issuer`, `clerk.jwks-uri` e `clerk.authorized-parties`.
  - `ClerkTokenValidator` exige `sub`, expiração, not-before, `azp` autorizado e `sts` diferente de `pending`.
  - `CompanySession` lê a organização em `o.id` e o papel em `o.rol`. Sem o mapa `o`, usa `org_id` e `org_role`. Remove um prefixo `org:` do papel.
  - `clerk.administrator-roles` (`CLERK_ADMIN_ROLES`, padrão `admin`) é a única lista de administrador.
  - `pom.xml` não declara SDK da Clerk nem starter de e-mail.
- **Alternatives considered**: Guardar a chave só em variável de ambiente e chamá-la no Core. A decisão fecha esse caminho: o Core não chama a Clerk.

## Vínculo

- **Decision**: Não há documento de convite nem de membro da empresa. Depois do aceite, o vínculo é a sessão. `tenantId` é a organização. `userId` é o `sub`. `administrator()` é verdadeiro só quando o papel, sem `org:`, está em `CLERK_ADMIN_ROLES`.
- **Rationale**: É o modelo que Empresa e Produtos e o perfil já usam. Um segundo registro no Mongo divergiria do papel que a próxima requisição lê no JWT.
- **Alternatives considered**: Coleção `OrganizationMembership` gravada no aceite. Não há aceite no Core, e a sessão seguinte não consultaria essa coleção.

## Cargos

- **Decision**: O convite só oferece `admin` e `member`. No Core, `admin` e `org:admin` são administrador. `member` e `org:member` não são. O nome exibido "Administrador" continua sem poder de administrador, como o teste de cadastro já faz com o slug `Administrador`. `CLERK_ADMIN_ROLES` não ganha `member` e o padrão continua `admin`.
- **Rationale**: A Clerk costuma emitir o papel da organização com ou sem o prefixo `org:`. O código já trata os dois. A LLT-169, ainda não feita, é o lugar de Gestor, Visualizador e da matriz.
- **Alternatives considered**: Tratar qualquer papel diferente de `member` como administrador. `owner` passaria a administrar sem estar na lista. Incluir `member` em `CLERK_ADMIN_ROLES`. O membro alteraria o cadastro da empresa.

## Prazo, lista, revogação e reenvio

- **Decision**: O prazo é de 7 dias, contados do envio. O reenvio recomeça os 7 dias. Lista de pendentes, revogação e reenvio são do Web, via Clerk. O Core não guarda data, não lista e não reenvia. A configuração do número 7 é do dashboard e de outra pessoa. O contrato deste repositório só registra a lista.
- **Rationale**: A Clerk já recusa convite vencido ou revogado e só então deixaria de emitir sessão. O Core não vê o convite que não virou sessão.
- **Alternatives considered**: Cron no Core para expirar convites. Não há convite gravado aqui. Endpoint de reenvio. A decisão o coloca no Web.

## Aceite

- **Decision**: A pessoa aceita nas telas de entrada já existentes. Não há rota nova no Web nem no Core. A prova no Core é a sessão seguinte: `o.rol` ou `org_role` com `member` ou `admin`, e a organização do convite.
- **Rationale**: A decisão pede as telas de entrada existentes. Uma rota `/aceitar-convite` no Core seria um endpoint que a decisão tornou desnecessário.
- **Alternatives considered**: Página nova de aceite servida pelo Core. O Core não serve a interface, e o aceite é da Clerk.

## E-mail inválido

- **Decision**: E-mail vazio é recusado no Web com "Informe o e-mail." E-mail irreconhecível é recusado no Web com "Informe um e-mail válido." O Web não chama a Clerk nesses casos. O Core não recebe esse e-mail e não repete a validação.
- **Rationale**: Não há formulário de convite no Core. A frase de e-mail inválido já existe no cadastro da empresa; o Web reutiliza o mesmo texto.
- **Alternatives considered**: Validar o e-mail num endpoint do Core antes de o Web chamar a Clerk. Seria o endpoint que esta decisão evita.

## Testes

- **Decision**: `CompanySessionTests` cobre `o.rol` e `org_role` para `admin`, `org:admin`, `member` e `org:member`. `OrganizationInviteBoundaryTests` sobe o Mongo de teste já usado no cadastro e mostra que a sessão de membro lê a empresa e não a altera, que a sessão de administrador altera, e que um pedido autenticado a um caminho de convite inexistente responde 404. Nenhum dos dois testes define segredo. A imagem continua `mongo:8.0.34`.
- **Rationale**: O comportamento de papel já está em `CompanySession` e em `CompanyController`. O teste novo nomeia o caso do convite aceito para que uma mudança futura não trate `member` como administrador nem abra um caminho de convite. O teste deve passar sem código de produção novo. Se falhar, a correção fica no mapeamento do papel, não num endpoint.
- **Alternatives considered**: Teste que chama a API real da Clerk. Exigiria segredo e rede. Teste que espera falhar até existir um controlador de convite. Esse controlador não será criado.

## O que não muda

- **Decision**: `SecurityConfig` permanece com `anyRequest` autenticado. `CompanyController`, produtos, perfil, CEP, PDF e sincronização 501 permanecem. `CLERK_ADMIN_ROLES` permanece. Não há métrica nem auditoria novas.
- **Rationale**: A spec não pede telemetria nem mudança de cadastro. O membro recém-aceito cai nas mesmas regras de quem já entra com papel `member`.
- **Alternatives considered**: Filtro novo só para convite. Não há caminho novo para filtrar.

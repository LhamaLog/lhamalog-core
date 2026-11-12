# Research: Manter perfil do usuário

## Terminologia

- **Decision**: Na spec, os nomes de negócio são nome, idioma e fuso horário. No JSON, os campos são `displayName`, `language` e `timezone`. A pessoa da sessão é o `sub`, chamado `userId` em `CompanySession`.
- **Rationale**: A spec fica legível para o produto. O contrato fica alinhado ao camelCase já usado em `legalName` e nos demais corpos.
- **Alternatives considered**: Usar "nome" também no JSON. O Web e o Core passariam a ter um campo genérico que já existe em produto (`name`) com outro significado.

## Caminho do perfil

- **Decision**: `GET /api/user/profile` e `PUT /api/user/profile`. Sem identificador no caminho. Sem `POST` e sem `DELETE`. O corpo de sucesso tem `displayName`, `language`, `timezone` e `version`.
- **Rationale**: O prefixo `/api/` e o substantivo no singular seguem `/api/company` e `/api/products`. O recurso é a pessoa da sessão, não uma coleção. O Web desta entrega combina com esse caminho.
- **Alternatives considered**: `/api/profile` esconde que o dono é o usuário. `/api/users/{id}` abriria o perfil de outra pessoa. `/api/company` já guarda fuso e idioma da empresa; reutilizar esse caminho misturaria os dois cadastros.

## Quem é a pessoa e qual empresa

- **Decision**: Reusar `CompanySessionResolver`. `CompanySession.userId()` já é o `sub` do JWT. `CompanySession.tenantId()` continua vindo de `o.id`, com fallback `org_id`. Sem empresa, a resposta segue 403 com "Selecione uma empresa para continuar." Sem JWT, o Spring Security segue 401. O documento do perfil usa só o `userId` como id. O `tenantId` não é gravado.
- **Rationale**: A sessão atual já exige empresa em toda operação de negócio. O nome na Clerk é da pessoa, não da organização, então a chave não pode ser a empresa. Duas empresas da mesma pessoa leem o mesmo documento. Duas pessoas da mesma empresa não.
- **Alternatives considered**: Liberar o perfil sem empresa. Isso criaria uma sessão diferente da regra atual. Gravar por par empresa+pessoa faria o nome divergir entre organizações e contradiz a identidade única da pessoa.

## Papel

- **Decision**: Não chamar `CompanySession.administrator()` neste fluxo. Membro e administrador consultam e gravam o próprio perfil. `CLERK_ADMIN_ROLES` não muda e não é lido por este pacote.
- **Rationale**: A história é "como usuário", não "como administrador". O gate de administrador do cadastro da empresa permanece só em `CompanyController`.
- **Alternatives considered**: Exigir administrador para alterar o nome. Isso impediria o membro de ajustar o próprio perfil, que é o objetivo da LLT-174.

## Validação

- **Decision**: `UserProfileRules` valida os três campos e a versão, no mesmo estilo de `CompanyProfiles`, e lança `FieldValidationException`. Não chama `CompanyProfiles` nem `CompanyService`.
  - `displayName`: obrigatório depois do trim; máximo 200 caracteres; caractere de controle (código menor que 32, exceto tabulação) recusado com "O texto excede o limite permitido."; vazio com "Informe o nome."; tipo não textual com "Confira o preenchimento deste campo."
  - `language`: obrigatório; aliases "Português (Brasil)" e "Portugues (Brasil)" viram `pt-BR`; o valor normalizado precisa casar `^[a-z]{2}(-[A-Z]{2})?$`; vazio com "Informe o idioma."; fora do formato com "Informe um idioma válido."
  - `timezone`: obrigatório; aliases "(UTC-03:00) Brasília" e "(UTC-03:00) Brasilia" viram `America/Sao_Paulo`; o valor normalizado precisa ser aceito por `java.time.ZoneId.of`; a resposta grava `ZoneId.of(valor).getId()`; vazio com "Informe o fuso horário."; inválido com "Informe um fuso horário válido."
  - `version`: ausente ou `null` significa versão ausente; número integral é a versão pedida; outro tipo responde em `version` com "Confira o preenchimento deste campo."
- **Rationale**: O cadastro da empresa já reconhece esses rótulos, esse formato de idioma e esse identificador de fuso. Repetir a regra no pacote `profile` evita que a gravação do perfil entre no merge da empresa. O limite 200 é `CompanyProfiles` texto curto.
- **Alternatives considered**: Lista fechada só com `pt-BR` e `en`. O exemplo do produto cita esses dois, e o cadastro da empresa já aceita etiqueta mais ampla; fechar a lista agora rejeitaria `en-US`, que o mesmo formato já aceita. Bean Validation genérica perderia as mensagens específicas. Aceitar fuso vazio, como a empresa aceita, deixaria o perfil sem o dado que a história pede.

## Versão

- **Decision**: Campo `@Version` no documento. A primeira gravação envia `version` nula e o documento ainda não existe. A alteração envia a versão vigente. Divergência lança `OptimisticLockingFailureException`. Duas primeiras gravações da mesma pessoa que passam da leitura antes do insert esbarram na chave duplicada. `OptimisticLockingFailureException` e `DuplicateKeyException` já caem no `ApiExceptionHandler` como 409 com "Os dados foram alterados. Recarregue antes de salvar novamente." O serviço não captura essas exceções. A consulta sem documento devolve `version` nula e não insere.
- **Rationale**: É o mesmo conflito já usado em `CompanyService.ensureVersion`. Não surge outra mensagem.
- **Alternatives considered**: Última escrita vence, sem versão. Duas abas misturariam nome e fuso. Exigir versão também na primeira gravação obrigaria o cliente a inventar um número.

## Consulta sem perfil

- **Decision**: `GET` sem documento devolve `displayName` vazio, `language` `pt-BR`, `timezone` `America/Sao_Paulo` e `version` nula. Não há insert.
- **Rationale**: O produto e a documentação estão em português e o exemplo de fuso da história é `America/Sao_Paulo`. O nome fica vazio porque a Clerk não é lida. A empresa também não grava na consulta.
- **Alternatives considered**: Gravar o padrão no primeiro GET. Uma consulta passaria a criar documento e a disputar versão. Devolver idioma e fuso vazios empurraria o padrão para cada cliente.

## Gap da escrita do nome na Clerk

- **Decision**: Não atualizar a Clerk nesta entrega. Não acrescentar dependência, propriedade, cliente HTTP, webhook nem outro autenticador. `UserProfileService` só lê e grava `UserProfile`. A consulta devolve `displayName` persistido. O gap fica registrado aqui e no contrato, como item em aberto.
- **Rationale**: A integração existente não consegue escrever o nome. Inventar o cliente seria a plataforma nova que o pedido proíbe.
- **O que já existe**:
  - `SecurityConfig` valida o JWT com `clerk.issuer` (`CLERK_ISSUER`), `clerk.jwks-uri` (`CLERK_JWKS_URI`) e `clerk.authorized-parties` (`CLERK_AUTHORIZED_PARTIES`).
  - `ClerkTokenValidator` exige `sub`, expiração, not-before, `azp` autorizado e `sts` diferente de `pending`. Não lê nome.
  - `CompanySession` usa o `sub` como `userId`.
  - `clerk.administrator-roles` (`CLERK_ADMIN_ROLES`, padrão `admin`) só distingue administrador da empresa.
  - `pom.xml` não declara SDK da Clerk. O README marca o SDK como pendente e diz que a chave secreta não entra neste Core.
- **O que falta, e por isso o write-back não cabe nesta entrega**:
  - Não há variável de segredo da Clerk em `application.properties`, `.env.example`, `.env.prod.example`, `compose.yaml` nem `compose.prod.yaml`. Nenhum valor de segredo deve ser escrito para fechar esse buraco aqui.
  - Não há cliente nem método que chame a API de backend da Clerk.
  - O JWT validado hoje não traz o nome. Não há leitura de usuário na Clerk.
  - A atualização pública de usuário da Clerk, se um dia a integração existir, seria `PATCH https://api.clerk.com/v1/users/{user_id}` com o `user_id` igual ao `sub` e autorização pelo segredo. O corpo dessa API separa `first_name` e `last_name`. Este contrato tem um único `displayName`. Como o cliente não será criado agora, este plano não define como partir o nome.
- **Alternatives considered**: Adicionar `CLERK_SECRET_KEY` e um `RestClient` nesta história. Isso desenha a integração que o pedido manda não criar. Ler o nome de `unsafeMetadata` contradiz o modelo já aceito em Empresa e Produtos. Webhook de usuário é plataforma Clerk, fora desta entrega.

## Fuso horário e consumidores

- **Decision**: O fuso do perfil só é gravado e devolvido em `/api/user/profile`. Nenhum código em `src/main/java` implementa painel, agendamento de varredura ou envio de e-mail. `Company.Preferences.timezone` e `CompanySettingsState.Data.timeZone` são campos do cadastro da empresa e não são aplicados a esses consumidores. Esta entrega não os altera e não faz o pacote `company` ler `UserProfile`.
- **Rationale**: A história manda usar o fuso só onde o consumidor já existe e proíbe criar os que faltam. A busca no código não achou esses três consumidores.
- **Alternatives considered**: Copiar o fuso da pessoa para a empresa. Os dois cadastros passariam a se sobrescrever. Criar um agendador ou um enviador de e-mail só para "usar" o fuso amplia a LLT-174 para entregas que não são desta história. Redis e WebSocket estão no `pom.xml` sem uso de painel, agenda ou e-mail; não são consumidores desta feature.

## Idioma

- **Decision**: Gravar e devolver `language`. Não criar catálogo de mensagens, não traduzir `ApiExceptionHandler` e não alterar textos do cadastro da empresa. A aplicação na interface é do Web.
- **Rationale**: A decisão do produto limita o Core a persistir a preferência.
- **Alternatives considered**: Trocar as mensagens da API conforme o idioma do perfil. Isso traduz o produto nesta história.

## O que não muda

- **Decision**: `SecurityConfig` permanece com `anyRequest` autenticado. CORS de `/api/**` já permite `GET` e `PUT`. `CompanyController`, produtos, CEP, PDF e sincronização 501 permanecem. Não há métrica nem evento de auditoria novos.
- **Rationale**: O caminho novo já nasce autenticado. A spec não pede telemetria. Os caminhos 501 da empresa não são desta entrega.
- **Alternatives considered**: Filtro de segurança só para o perfil. Duplicaria a regra que já protege `/api/**`.

## Testes

- **Decision**: `UserProfileHttpTests` importa `TestcontainersConfiguration`, usa `@SpringBootTest` e `@AutoConfigureMockMvc`, JWT sintético com `sub` e `o.id`/`o.rol`, e não define segredo da Clerk. A imagem de Mongo continua `mongo:8.0.34`. A classe entra no `verify`.
- **Rationale**: É o mesmo portão da LLT-175 e o princípio de testes antes do merge. Sem a classe no `verify`, o aceite fica opcional.
- **Alternatives considered**: Teste só de unidade do serviço, sem HTTP. Não prova 401, 403 e o isolamento como o cliente faz. Teste com token real da Clerk exigiria segredo.

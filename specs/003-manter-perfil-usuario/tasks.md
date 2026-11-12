---

description: "Task list for manter perfil do usuário"
---

# Tasks: Manter perfil do usuário

**Input**: Design documents from `/specs/003-manter-perfil-usuario/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/user-profile.md, quickstart.md

**Tests**: A spec pede testes HTTP de consulta, gravação, validação, versão, isolamento, sessão e cadastro da empresa intacto. Os testes desta lista são obrigatórios e devem falhar antes da implementação correspondente.

**Organization**: Uma fase por história de usuário, para implementar e testar cada uma em separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: História à qual a tarefa pertence (US1, US2, US3, US4)
- Cada tarefa cita o caminho do arquivo

## Path Conventions

- Código em `src/main/java/br/com/lhamalog/core/`
- Testes em `src/test/java/br/com/lhamalog/core/`
- Documentos desta feature em `specs/003-manter-perfil-usuario/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar que a entrega reusa o Core e não abre stack, SDK nem segredo

- [ ] T001 Confirmar que `pom.xml` permanece em Java 25 e Spring Boot 4.1.1, sem dependência nova e sem SDK da Clerk
- [ ] T002 [P] Confirmar que `src/main/resources/application.properties` mantém só `clerk.issuer`, `clerk.jwks-uri`, `clerk.authorized-parties` e `clerk.administrator-roles`, e que `.env.example`, `.env.prod.example`, `compose.yaml` e `compose.prod.yaml` não ganham variável de segredo da Clerk. Não alterar `src/main/java/br/com/lhamalog/core/config/SecurityConfig.java`: `anyRequest` autenticado já cobre `/api/**`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Documento e regras compartilhados pelas histórias de gravação, isolamento e não regressão

**⚠️ CRITICAL**: Nenhuma história começa antes desta fase

- [ ] T003 Criar `src/main/java/br/com/lhamalog/core/profile/UserProfile.java` como `@Document("UserProfile")` com `id`, `displayName`, `language`, `timezone`, `updatedAt` e `@Version Long version`, e `src/main/java/br/com/lhamalog/core/profile/UserProfileRepository.java` estendendo `MongoRepository<UserProfile, String>`. O `id` é o `sub` da sessão. Não gravar empresa, e-mail nem notificação
- [ ] T004 [P] Criar `src/main/java/br/com/lhamalog/core/profile/UserProfileRules.java` sem chamar `CompanyProfiles` nem `CompanyService`. `displayName` obrigatório após trim, máximo 200 caracteres, caractere de controle (código menor que 32, exceto tabulação) recusado com "O texto excede o limite permitido.", vazio com "Informe o nome.", tipo não textual com "Confira o preenchimento deste campo." `language` obrigatório; "Português (Brasil)" e "Portugues (Brasil)" viram `pt-BR`; o valor normalizado casa `^[a-z]{2}(-[A-Z]{2})?$`; vazio com "Informe o idioma."; fora do formato com "Informe um idioma válido." `timezone` obrigatório; "(UTC-03:00) Brasília" e "(UTC-03:00) Brasilia" viram `America/Sao_Paulo`; o valor normalizado precisa ser aceito por `java.time.ZoneId.of` e o valor devolvido é `ZoneId.of(valor).getId()`; vazio com "Informe o fuso horário."; inválido com "Informe um fuso horário válido." `version` ausente ou `null` significa versão ausente; número integral é a versão pedida; outro tipo marca `version` com "Confira o preenchimento deste campo." Campo ausente entre os três marca esse campo com "Confira o preenchimento deste campo." Erros lançam `FieldValidationException` com mensagem "Confira os campos informados."

**Checkpoint**: O documento e a validação existem, ainda sem endpoint

---

## Phase 3: User Story 1 - Ajustar o próprio perfil (Priority: P1) 🎯 MVP

**Goal**: A pessoa consulta e grava nome, idioma e fuso do próprio perfil, com validação e conflito de versão

**Independent Test**: `GET` sem documento devolve nome vazio, `pt-BR`, `America/Sao_Paulo` e `version` nula, e o segundo `GET` continua assim. `PUT` com `version` nula grava. O `GET` seguinte devolve os três valores. Um `PUT` com a versão vigente muda um campo e mantém os outros dois enviados. Um `PUT` com a versão antiga responde 409. Nome, idioma ou fuso inválido responde 400 e a consulta fica no último salvamento

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T005 [US1] Criar `src/test/java/br/com/lhamalog/core/profile/UserProfileHttpTests.java` com `@Import(TestcontainersConfiguration.class)`, `@SpringBootTest` e `@AutoConfigureMockMvc`, sem `TEST_MONGODB_URI`, sem tag `latest` e sem segredo da Clerk. O teste `savesAndReadsOwnProfile` usa JWT sintético `sub` e `o.id`/`o.rol` e falha até T007. Cobrir: `GET /api/user/profile` inicial com `displayName` "", `language` "pt-BR", `timezone` "America/Sao_Paulo" e `version` nula, e um segundo `GET` ainda com `version` nula; `PUT` com `version` nula; `GET` devolve os três valores; segundo `PUT` com a versão vigente muda só o valor reenviado diferente e mantém os outros dois; `PUT` com a versão antiga responde 409 e a consulta fica no segundo salvamento; nome vazio responde 400 `fieldErrors.displayName` "Informe o nome."; nome acima de 200 caracteres responde 400 "O texto excede o limite permitido."; idioma "pt_BR" responde 400 "Informe um idioma válido."; idioma vazio responde 400 "Informe o idioma."; nome só com espaços responde 400 "Informe o nome."; fuso "Nao/Existe" responde 400 "Informe um fuso horário válido."; campo omitido responde 400 e o perfil não muda; "Português (Brasil)" volta como "pt-BR"; "(UTC-03:00) Brasília" volta como "America/Sao_Paulo"

### Implementation for User Story 1

- [ ] T006 [US1] Implementar `get` e `save` em `src/main/java/br/com/lhamalog/core/profile/UserProfileService.java`, usando só `UserProfileRepository` e `UserProfileRules`. `get` sem documento devolve nome vazio, `pt-BR`, `America/Sao_Paulo` e `version` nula, sem insert. `save` exige versão igual à corrente (`null` só quando o documento não existe); divergência lança `OptimisticLockingFailureException` para o handler já responder 409 "Os dados foram alterados. Recarregue antes de salvar novamente." Não capturar `DuplicateKeyException`: duas primeiras gravações da mesma pessoa também respondem 409 por esse handler. Gravação válida substitui os três campos e `updatedAt`. Não chamar a Clerk nem o pacote `company`
- [ ] T007 [US1] Adicionar `GET /api/user/profile` e `PUT /api/user/profile` em `src/main/java/br/com/lhamalog/core/profile/UserProfileController.java`. Resolver a sessão com `CompanySessionResolver` antes de ler o corpo. Usar só `session.userId()`. Corpo ilegível responde 400 "Os dados enviados são inválidos.", no mesmo tratamento de JSON inválido já usado em `CompanyController`. Não exigir administrador

**Checkpoint**: A pessoa grava e relê o próprio perfil. A história se prova sem o teste de duas pessoas

---

## Phase 4: User Story 2 - Só o próprio perfil (Priority: P1)

**Goal**: Cada sessão lê e grava somente o próprio perfil. Membro também grava. Sem sessão ou sem empresa, ninguém acessa

**Independent Test**: Ana e Bruno, na mesma empresa, não veem o perfil um do outro. Um corpo com `userId` ou `organizationId` de Bruno ainda grava o perfil de Ana. A mesma Ana, em outra empresa, vê o mesmo perfil. Membro grava com sucesso. Sem JWT a resposta é 401. JWT sem empresa responde 403 "Selecione uma empresa para continuar." e não grava

### Tests for User Story 2 ⚠️

- [ ] T008 [US2] Acrescentar em `src/test/java/br/com/lhamalog/core/profile/UserProfileHttpTests.java` os testes `profilesStayWithTheSessionUser`, `memberCanUpdateOwnProfile` e `sessionRulesStayDistinct`, falhando até T009. Duas pessoas da mesma empresa: cada `GET` e `PUT` vê só o próprio `displayName`. Corpo com `userId`, `clerkUserId`, `organizationId` e `tenantId` de outra pessoa ainda grava só o `sub` da sessão, e esses campos não voltam na resposta. A mesma pessoa com dois `o.id` recebe o mesmo perfil. Papel `member` recebe 200 no `GET` e no `PUT`. Pedido sem JWT responde 401. JWT sem `o.id` e sem `org_id` responde 403 com "Selecione uma empresa para continuar." e o perfil não muda

### Implementation for User Story 2

- [ ] T009 [US2] Garantir em `src/main/java/br/com/lhamalog/core/profile/UserProfileController.java` e `src/main/java/br/com/lhamalog/core/profile/UserProfileService.java` que a chave é somente `CompanySession.userId()`. Ignorar `userId`, `id`, `clerkUserId`, `organizationId` e `tenantId` do JSON em `UserProfileRules`. Não gravar `tenantId` em `UserProfile`. Não consultar `administrator()`

**Checkpoint**: Duas pessoas e duas empresas se provam sem depender do gap da Clerk

---

## Phase 5: User Story 3 - Nome alinhado à identidade (Priority: P1)

**Goal**: O nome gravado é o nome da consulta. A entrega não cria integração com a Clerk para escrever esse nome de volta

**Independent Test**: Um `PUT` de nome novo, sem qualquer segredo da Clerk configurado no teste, responde 200. O `GET` seguinte devolve esse nome. O serviço não ganha cliente HTTP nem dependência nova

### Tests for User Story 3 ⚠️

- [ ] T010 [US3] Acrescentar `nameIsStoredWithoutIdentityCall` em `src/test/java/br/com/lhamalog/core/profile/UserProfileHttpTests.java`, falhando até T011. O teste não define segredo da Clerk. `PUT` com `displayName` novo responde 200 e o `GET` devolve esse nome, com idioma e fuso iguais aos enviados. Um segundo `GET`, sem outro `PUT`, devolve o mesmo nome. Nenhuma propriedade de teste introduz cliente da Clerk

### Implementation for User Story 3

- [ ] T011 [US3] Manter `src/main/java/br/com/lhamalog/core/profile/UserProfileService.java` sem cliente HTTP, sem SDK e sem leitura de nome no JWT além do `sub` que já identifica a pessoa. Não adicionar classe de integração Clerk, não mapear segredo em `src/main/resources/application.properties` e não alterar `pom.xml`. O gap de `PATCH https://api.clerk.com/v1/users/{user_id}` permanece só descrito em `specs/003-manter-perfil-usuario/research.md`. Não implementar essa chamada e não definir como partir `displayName` em `first_name` e `last_name`

**Checkpoint**: O nome persiste no Core e a Clerk continua só validando o JWT já existente

---

## Phase 6: User Story 4 - Fuso e idioma sem mudar o resto do produto (Priority: P1)

**Goal**: O fuso do perfil não altera a empresa e não cria painel, agenda de varredura nem e-mail. O idioma não traduz as mensagens

**Independent Test**: A empresa tem fuso e idioma próprios. O perfil grava outros valores. `GET /api/company` permanece igual. Não surge código de painel, agendamento ou envio de e-mail, e o pacote `company` não lê `UserProfile`

### Tests for User Story 4 ⚠️

- [ ] T012 [US4] Acrescentar `profileDoesNotChangeCompany` em `src/test/java/br/com/lhamalog/core/profile/UserProfileHttpTests.java`, falhando até T013. Com papel `admin`, gravar a empresa com fuso `America/Sao_Paulo` e idioma `pt-BR`. Gravar o perfil com `timezone` `Europe/Lisbon` e `language` `en`. `GET /api/user/profile` devolve os valores do perfil. `GET /api/company` mantém `preferences.timezone` e `settings.data.timeZone` em `America/Sao_Paulo`, e o idioma da empresa em `pt-BR`

### Implementation for User Story 4

- [ ] T013 [US4] Confirmar que `src/main/java/br/com/lhamalog/core/profile/UserProfileService.java` não chama repositório de `Tenant`, `Produto` ou `AcessoProduto`, e que nenhum arquivo em `src/main/java/br/com/lhamalog/core/company/` referencia `UserProfile`. Não criar classe de painel, agendamento, varredura ou envio de e-mail. Não adicionar campo de e-mail nem de notificação em `UserProfile.java`

**Checkpoint**: As quatro histórias passam na classe HTTP nova

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Fechar mensagens, escopo e o portão de teste

- [ ] T014 [P] Confirmar que `src/main/java/br/com/lhamalog/core/shared/ApiExceptionHandler.java` não ganha handler novo e que as mensagens desta feature continuam em português, inclusive "Confira os campos informados.", "Os dados enviados são inválidos.", "Selecione uma empresa para continuar." e "Os dados foram alterados. Recarregue antes de salvar novamente."
- [ ] T015 [P] Confirmar que `src/main/java/br/com/lhamalog/core/profile/UserProfile.java` não tem campo de e-mail nem de notificação, e que LLT-172 e LLT-173 não são implementados neste pacote
- [ ] T016 Executar `./mvnw verify` (ou `.\mvnw.cmd verify` no Windows) conforme `specs/003-manter-perfil-usuario/quickstart.md` e registrar o resultado nesse arquivo

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: começa imediatamente
- **Foundational (Phase 2)**: depende da Phase 1 e bloqueia todas as histórias
- **User Stories (Phase 3+)**: dependem da Phase 2
- **Polish (Phase 7)**: depende das quatro histórias

### User Story Dependencies

- **User Story 1 (P1)**: começa após a Phase 2. Não depende das outras histórias
- **User Story 2 (P1)**: começa após a Phase 2. O teste usa o endpoint da US1; a prova independente é o isolamento e a sessão
- **User Story 3 (P1)**: começa após a Phase 2. O teste usa a gravação da US1; a prova independente é o nome persistido sem cliente Clerk
- **User Story 4 (P1)**: começa depois da US1, porque o teste grava perfil e empresa. Não depende da US2 nem da US3

### Within Each User Story

- O teste HTTP falha antes da implementação
- As regras ficam prontas antes do serviço
- O serviço fica pronto antes do endpoint que o chama
- A história fecha no checkpoint antes da próxima

### Parallel Opportunities

- T001 e T002 podem rodar juntos
- T003 e T004 podem rodar juntos
- T014 e T015 podem rodar juntos
- Os testes das histórias editam o mesmo arquivo `UserProfileHttpTests.java`, então não são paralelos entre si
- US2 e US3 podem ser feitas em paralelo depois da US1, por pessoas diferentes, se coordenarem o mesmo arquivo de teste

---

## Parallel Example: Foundational

```bash
Task: "Criar UserProfile e UserProfileRepository em src/main/java/br/com/lhamalog/core/profile/"
Task: "Criar UserProfileRules em src/main/java/br/com/lhamalog/core/profile/UserProfileRules.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1 e Phase 2
2. Completar Phase 3 (US1)
3. Parar e validar consulta, gravação, validação e 409
4. Seguir para o isolamento só depois desse checkpoint

### Incremental Delivery

1. Setup e fundação
2. US1: a pessoa grava e relê o próprio perfil
3. US2: duas pessoas, duas empresas e a sessão
4. US3: o nome fica no Core e a Clerk não ganha cliente
5. US4: a empresa e os consumidores inexistentes permanecem como estão
6. Phase 7: mensagens atuais e `./mvnw verify`

### Parallel Team Strategy

1. Uma pessoa confirma T001 e outra confirma T002
2. Em seguida, uma pessoa faz T003 e outra faz T004
3. US1 fecha o endpoint
4. US2 e US3 podem seguir em paralelo se não editarem o mesmo teste ao mesmo tempo
5. US4 fecha a não regressão da empresa

---

## Notes

- [P] só em arquivos diferentes e sem dependência em aberto
- Não criar módulo Maven, SDK da Clerk, propriedade de segredo nem chamada à API de backend da Clerk
- Não gravar segredo em teste, código ou documento
- Não criar painel, agendamento de varredura, envio de e-mail nem tradução das mensagens
- Identificadores novos em inglês; texto de mensagem e desta lista em português
- A mensagem `LLT-174 Manter perfil do usuário` fica reservada ao commit de implementação, não a estas tasks

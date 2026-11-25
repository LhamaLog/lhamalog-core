---

description: "Task list for melhorar a tela de perfil do usuário"
---

# Tasks: Melhorar a tela de perfil do usuário

**Input**: Design documents from `/specs/295-melhorar-a-tela-de-perfil-do-usuario/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/user-profile.md, quickstart.md, e o contrato canônico `specs/003-manter-perfil-usuario/contracts/user-profile.md`

**Tests**: A spec pede testes HTTP de bio e cargo, limite, quebra de linha, perfil antigo, omissão, apagamento, versão e ausência de chamada à conta. Os testes desta lista são obrigatórios e devem falhar antes da implementação correspondente.

**Organization**: Uma fase por história de usuário, para implementar e testar cada uma em separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: História à qual a tarefa pertence (US1, US2, US3)
- Cada tarefa cita o caminho do arquivo

## Path Conventions

- Código em `src/main/java/br/com/lhamalog/core/profile/`
- Testes em `src/test/java/br/com/lhamalog/core/profile/UserProfileHttpTests.java`
- Contrato canônico em `specs/003-manter-perfil-usuario/contracts/user-profile.md`
- Delta em `specs/295-melhorar-a-tela-de-perfil-do-usuario/contracts/user-profile.md`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar que a entrega reusa o Core e não abre stack, SDK nem segredo

- [X] T001 [P] Confirmar que `pom.xml` permanece em Java 25 e Spring Boot 4.1.1, sem dependência nova e sem SDK da Clerk
- [X] T002 [P] Confirmar que `src/main/resources/application.properties`, `.env.example`, `.env.prod.example`, `compose.yaml` e `compose.prod.yaml` não ganham `CLERK_SECRET_KEY` nem outra variável de segredo. Não alterar `src/main/java/br/com/lhamalog/core/config/SecurityConfig.java` nem `src/main/java/br/com/lhamalog/core/identity/adapter/in/security/SessionRevokingAccessDeniedHandler.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Campos e regras compartilhados pelas três histórias

**⚠️ CRITICAL**: Nenhuma história começa antes desta fase

- [X] T003 [P] Criar os componentes `bio` e `jobTitle` em `src/main/java/br/com/lhamalog/core/profile/UserProfile.java`, ambos `String` anulável, na ordem `id`, `displayName`, `language`, `timezone`, `bio`, `jobTitle`, `updatedAt`, `version`. Não acrescentar tema, acessibilidade, foto, e-mail, função, organização, senha nem sessão. Não alterar `src/main/java/br/com/lhamalog/core/profile/UserProfileRepository.java`
- [X] T004 [P] Estender `src/main/java/br/com/lhamalog/core/profile/UserProfileRules.java` com leitura opcional de `bio` e `jobTitle`, sem chamar `CompanyProfiles` nem `CompanyService`. Cada campo: chave ausente não é erro (`present` falso); `null`, `""` ou texto que o trim deixa vazio fica presente e vazio; texto restante é o trim. Máximo 160 em `bio` e 80 em `jobTitle`, por `String.length()` depois do trim. Caractere de controle (código menor que 32, exceto tabulação), inclusive quebra de linha, ou texto acima do limite, marca o campo com "O texto excede o limite permitido." Tipo que não é texto marca o campo com "Confira o preenchimento deste campo." `<` e `>` permanecem. A mensagem geral continua "Confira os campos informados." `displayName`, `language`, `timezone` e `version` não mudam de regra

**Checkpoint**: O documento aceita os dois campos e a validação distingue omissão, vazio e texto

---

## Phase 3: User Story 1 - Informar bio e cargo (Priority: P1) 🎯 MVP

**Goal**: A pessoa grava bio e cargo e a consulta seguinte devolve os dois textos, com a versão otimista de sempre

**Independent Test**: Com sessão válida e empresa selecionada, gravar bio de até 160 caracteres e cargo de até 80 junto de nome, idioma, fuso e versão vigente. O `GET` devolve os dois textos e a versão nova. Bio de 161, cargo de 81, quebra de linha ou versão antiga não altera o perfil

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T005 [P] [US1] Acrescentar `savesBioAndJobTitle` em `src/test/java/br/com/lhamalog/core/profile/UserProfileHttpTests.java`, na classe que já tem `@CoreIntegrationTest` (Testcontainers, `mongo:8.0.34`, nunca `latest`, sem `TEST_MONGODB_URI` e sem segredo). O teste falha até T007. Cobrir: `PUT /api/user/profile` com `bio` de 160 caracteres e `jobTitle` de 80, mais nome, idioma, fuso e versão vigente, e o `GET` devolve esses textos; `jobTitle` "Administrador" não cria `role`, `function`, `organization` nem `memberSince`; bio de 161 responde 400 `fieldErrors.bio` "O texto excede o limite permitido."; cargo de 81 responde 400 `fieldErrors.jobTitle` com a mesma mensagem; bio com quebra de linha responde 400 nessa mensagem e o `GET` fica no último salvamento; bio `"<b>oi</b>"` é aceita e devolvida com `<` e `>`; bio numérica responde 400 "Confira o preenchimento deste campo."; `PUT` com a versão antiga e outra bio responde 409 "Os dados foram alterados. Recarregue antes de salvar novamente." e a bio anterior permanece

### Implementation for User Story 1

- [X] T006 [P] [US1] Incluir `bio` e `jobTitle` em `src/main/java/br/com/lhamalog/core/profile/UserProfileView.java`, depois de `timezone` e antes de `version`. `empty()` devolve `""` nos dois. `from` converte `null` ou branco em `""`. Não omitir string vazia na serialização
- [X] T007 [US1] Gravar e ler os dois campos em `src/main/java/br/com/lhamalog/core/profile/UserProfileService.java`. Chave ausente mantém o valor corrente, ou `null` quando ainda não há documento, para os `PUT` antigos de `UserProfileHttpTests` continuarem válidos. Texto presente e vazio grava `null`. Texto presente e não vazio grava o trim. Nome, idioma, fuso e a versão otimista permanecem: divergência lança `OptimisticLockingFailureException` e o envio inteiro, inclusive bio e cargo, não grava. Não logar `bio`, `jobTitle` nem `displayName`

**Checkpoint**: A pessoa grava e relê bio e cargo. A suíte anterior, que omite as duas chaves, continua aceita

---

## Phase 4: User Story 2 - Perfil antigo continua válido (Priority: P1)

**Goal**: Documento sem as chaves continua legível. Omitir mantém. `""` ou `null` apaga. A consulta devolve `""` quando não há texto

**Independent Test**: Inserir um BSON de perfil sem `bio` e sem `jobTitle`. O `GET` devolve `""` nos dois e não cria outro documento. Um `PUT` sem as chaves mantém o texto já guardado. `""`, `null` ou só espaços apaga só o campo enviado

### Tests for User Story 2 ⚠️

- [X] T008 [US2] Acrescentar `legacyProfileStaysReadable` e `optionalTextKeepsOrClears` em `src/test/java/br/com/lhamalog/core/profile/UserProfileHttpTests.java`. O primeiro falha até T009: inserir com o `MongoTemplate` já injetado um documento na coleção `UserProfile` sem as chaves `bio` e `jobTitle`, e o `GET /api/user/profile` devolve `bio` `""`, `jobTitle` `""` e o nome gravado; um segundo `GET` não cria versão nova além da que o documento já tinha. O segundo falha até T007 ter o vazio gravado como `null`: gravar os dois textos, omitir `jobTitle` num `PUT` que muda `bio` e ver o cargo permanecer, enviar `jobTitle` `""` e ver o `GET` com `""`, enviar `bio` `null` e ver `""`, e enviar bio só com espaços e ver `""`. `GET` sem documento devolve os dois como `""` e `UserProfileRepository.findById` continua vazio

### Implementation for User Story 2

- [X] T009 [US2] Ajustar `src/main/java/br/com/lhamalog/core/profile/UserProfile.java` e `src/main/java/br/com/lhamalog/core/profile/UserProfileService.java` para o documento sem as chaves desserializar com `null`, e para `""`, `null` e só espaços gravarem `null`. `src/main/java/br/com/lhamalog/core/profile/UserProfileView.java` segue devolvendo `""`. Sem migração e sem backfill

**Checkpoint**: Perfil antigo, omissão e apagamento se provam no mesmo `GET` e `PUT`

---

## Phase 5: User Story 3 - Identidade e segurança continuam fora do Core (Priority: P1)

**Goal**: Bio e cargo não puxam conta, tema nem acessibilidade para o Core

**Independent Test**: Gravar bio e cargo. A resposta não tem foto, e-mail, função, organização, membro desde, senha, sessões, tema nem acessibilidade. Um corpo com `theme` não grava esse dado. O Core não chama a conta e não cria segredo

### Tests for User Story 3 ⚠️

- [X] T010 [P] [US3] Acrescentar `accountAndAppearanceStayOut` em `src/test/java/br/com/lhamalog/core/profile/UserProfileHttpTests.java`, falhando até T011 se a resposta passar a expor esses dados. `PUT` com `bio`, `jobTitle` e `theme` responde 200 sem `theme`, `accessibility`, `photo`, `email`, `role`, `organization`, `memberSince`, `password` ou `sessions`. O `GET` seguinte também não os traz. Reusar `assertCoreDoesNotCallClerk`. Não definir `CLERK_SECRET_KEY`

### Implementation for User Story 3

- [X] T011 [P] [US3] Confirmar que `src/main/java/br/com/lhamalog/core/profile/UserProfileController.java` permanece com `GET /api/user/profile` e `PUT /api/user/profile` apenas, e que `UserProfile`, `UserProfileView`, `UserProfileRules` e `UserProfileService` não ganham tema, acessibilidade, cliente HTTP, SDK nem campo de conta. Não criar tela. Não alterar pacotes de notificação nem `src/main/java/br/com/lhamalog/core/company/`

**Checkpoint**: A fronteira da conta e do navegador permanece, com bio e cargo gravados

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Mensagens atuais e o portão de teste

- [X] T012 [P] Confirmar que `src/main/java/br/com/lhamalog/core/shared/ApiExceptionHandler.java` não ganha handler novo e que as mensagens continuam "Confira os campos informados.", "O texto excede o limite permitido.", "Confira o preenchimento deste campo." e "Os dados foram alterados. Recarregue antes de salvar novamente."
- [X] T013 Executar `./mvnw verify` (ou `.\mvnw.cmd verify` no Windows) conforme `specs/295-melhorar-a-tela-de-perfil-do-usuario/quickstart.md` e registrar o resultado nesse arquivo. A tag do Mongo permanece `mongo:8.0.34`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: começa imediatamente
- **Foundational (Phase 2)**: depende da Phase 1 e bloqueia todas as histórias
- **User Stories (Phase 3+)**: dependem da Phase 2
- **Polish (Phase 6)**: depende das três histórias

### User Story Dependencies

- **User Story 1 (P1)**: começa após a Phase 2. Não depende das outras histórias
- **User Story 2 (P1)**: começa após a Phase 2. O teste usa o endpoint da US1; a prova independente é o documento antigo, a omissão e o apagamento
- **User Story 3 (P1)**: o teste grava bio e cargo, então começa depois de T007. A prova independente é a ausência de conta, tema e acessibilidade. T011 pode seguir em paralelo com T010
- US2 deve entrar antes do merge. O mesmo `PUT` é o contrato de compatibilidade, mesmo com a US1 já demonstrável sozinha

### Within Each User Story

- O teste HTTP falha antes da implementação
- As regras ficam prontas antes do serviço
- A view fica pronta antes do serviço que a devolve
- A história fecha no checkpoint antes da próxima

### Parallel Opportunities

- T001 e T002 podem rodar juntos
- T003 e T004 podem rodar juntos
- T005 e T006 podem rodar juntos, depois da Phase 2
- T010 e T011 podem rodar juntos, depois da US1
- T012 pode rodar junto da revisão final, antes de T013
- Os testes T005, T008 e T010 editam o mesmo arquivo, então não são paralelos entre si

---

## Parallel Example: User Story 1

```bash
Task: "Acrescentar savesBioAndJobTitle em src/test/java/br/com/lhamalog/core/profile/UserProfileHttpTests.java"
Task: "Incluir bio e jobTitle em src/main/java/br/com/lhamalog/core/profile/UserProfileView.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1 e Phase 2
2. Completar Phase 3 (US1)
3. Parar e validar gravação, consulta, limite, quebra de linha e 409
4. Seguir para o perfil antigo antes do merge

### Incremental Delivery

1. Setup e fundação
2. US1: bio e cargo gravados e relidos
3. US2: documento antigo, omissão que mantém e vazio que apaga
4. US3: conta, tema e acessibilidade continuam fora
5. Phase 6: mensagens atuais e `./mvnw verify`

### Parallel Team Strategy

1. Uma pessoa confirma T001 e outra confirma T002
2. Em seguida, uma pessoa faz T003 e outra faz T004
3. US1 fecha a gravação
4. US2 fecha o arquivo de teste antes de T010. T011 pode seguir em paralelo com T010, porque não edita esse arquivo

---

## Notes

- [P] marca arquivo diferente, sem dependência de tarefa incompleta
- Cada história fecha no próprio teste HTTP
- Os testes desta lista falham antes da implementação correspondente
- Não há tela, migração, coleção nova nem caminho novo
- Não registrar bio, cargo nem nome em log
- Parar em qualquer checkpoint para validar a história sozinha

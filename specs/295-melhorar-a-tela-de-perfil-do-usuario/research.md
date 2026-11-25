# Research: Melhorar a tela de perfil do usuário

## Onde os campos entram

- **Decision**: Estender o documento `UserProfile`, a view, as regras e o `GET`/`PUT /api/user/profile` que já existem. Sem classe nova, sem coleção nova, sem `POST` e sem caminho com identificador.
- **Rationale**: A spec estende o perfil da LLT-174. O controlador já lê o corpo cru e o serviço já troca nome, idioma e fuso com versão otimista. Dois textos opcionais cabem nesse fluxo.
- **Alternatives considered**: Outro recurso `/api/user/profile/about`. O Web passaria a fazer duas gravações para uma tela só, e a versão otimista se partiria.

## Nomes

- **Decision**: No JSON e no código, `bio` e `jobTitle`. Na spec, bio e cargo. `jobTitle` não é papel de acesso nem cargo de notificação.
- **Rationale**: É o camelCase de `displayName`, e a resposta Q4 fixou esses nomes.
- **Alternatives considered**: `job_title` ou `position`. A Q4 recusou os dois.

## Tamanho e contagem

- **Decision**: Depois do trim, `bio` aceita até 160 caracteres e `jobTitle` até 80. A contagem é `String.length()`, a mesma já usada para `displayName` em `UserProfileRules` (unidades UTF-16). O valor no limite é aceito. Um além do limite, ou um caractere de controle recusado, responde no campo com "O texto excede o limite permitido."
- **Rationale**: A Q1 fixou 160 e 80. Inventar contagem por grafema mudaria a regra do nome no mesmo recurso.
- **Alternatives considered**: 280/120 e 200/200, recusados na Q1. Contar pontos de código Unicode criaria dois critérios no mesmo perfil.

## Texto puro

- **Decision**: Reusar o predicado do nome: recusar código menor que 32, exceto tabulação. Quebra de linha (`\n`, `\r`) cai nessa regra. Trim antes de medir e de gravar. Vazio depois do trim é permitido. `<` e `>` permanecem. Não há sanitizador de HTML, não há escape na gravação e o Core não interpreta marcação. Tipo que não é texto (número, objeto, lista ou booleano) marca o campo com "Confira o preenchimento deste campo." A validação continua antes da checagem de versão, como hoje: corpo inválido responde 400 e o documento não muda.
- **Rationale**: A Q2 pediu o mesmo texto puro do nome, com vazio permitido e sem quebra de linha. A mensagem de limite já existe para o nome e evita texto novo no handler.
- **Alternatives considered**: Recusar também `<` e `>`, ou aceitar quebra de linha na bio. A Q2 ficou na opção do nome. Recusar tabulação também divergiria do nome, que a permite.

## Omissão, apagamento e consulta

- **Decision**: Cada um dos dois campos tem três entradas. Chave ausente: manter o valor já guardado; na primeira gravação, isso é ausência. `null`, `""` ou texto que o trim deixa vazio: apagar. Texto não vazio: gravar o trim. Ausência de texto no documento é `null` (a chave pode nem existir no BSON). `UserProfileView` converte `null` em `""`. O `GET` e o `PUT` aceito sempre incluem `bio` e `jobTitle`. A view não usa inclusão condicional que omita string vazia.
- **Rationale**: A Q5 separa "o cliente antigo não mandou o campo" de "o cliente pediu para apagar". A consulta com forma estável é o que a Q5 pediu. Guardar `null` faz o documento apagado parecer o documento antigo, e os dois leem como `""`.
- **Alternatives considered**: Omitir a chave na consulta quando não há texto (opção C da Q5). O cliente deixaria de saber se o campo existe. Tratar omissão como apagamento (opção B) faria o cliente antigo zerar a bio num `PUT` que só manda nome, idioma e fuso.

## Perfil antigo

- **Decision**: Sem migração e sem script. Um documento `UserProfile` gravado antes, só com `id`, `displayName`, `language`, `timezone`, `updatedAt` e `version`, desserializa com `bio` e `jobTitle` nulos. O `GET` devolve `""`. Um `PUT` sem as duas chaves, com a versão vigente, continua aceito e não cria texto. O teste insere esse BSON cru pelo `MongoTemplate` e lê pela API.
- **Rationale**: A coleção é schemaless e o record Java aceita `null`. O teste com Testcontainers prova a leitura, em vez de confiar só no mapeamento.
- **Alternatives considered**: Backfill que grava `""` em todo documento. Mexeria em perfil que a pessoa não editou e não muda o JSON da consulta.

## Tema, acessibilidade e conta

- **Decision**: `theme`, acessibilidade, foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões não ganham campo. Chave desconhecida no JSON continua ignorada, como `userId` e `organizationId` já são. O serviço não ganha cliente HTTP nem SDK. `CLERK_SECRET_KEY` não é criada. Cargo gravado como "Administrador" é só `jobTitle`.
- **Rationale**: A Q3 deixou tema e acessibilidade no navegador. A fronteira da conta já está na LLT-174 e na ADR_171, só apontada pela spec.
- **Alternatives considered**: Persistir o tema no perfil. A Q3 recusou.

## O que esta entrega não reabre

- **Decision**: `SecurityConfig`, `ApiExceptionHandler` e `SessionRevokingAccessDeniedHandler` não mudam. O teste `sessionRulesStayDistinct` continua esperando 401 sem JWT e 403 com corpo `{"error":"forbidden"}` quando não há empresa. Nome, idioma, fuso, aliases e versão otimista permanecem. `GET` sem documento não insere. Gravar o perfil não chama repositório de empresa, produto nem preferência de notificação. Não há linha de log nova; bio, cargo e nome não entram em log.
- **Rationale**: O pedido desta rodada é estender o perfil. A frase "Selecione uma empresa para continuar." segue na spec como isolamento herdado da LLT-174. O handler e o teste atuais travam outro corpo, desde a ADR_032. Trocar essa recusa não é desta entrega.
- **Alternatives considered**: Alterar o 403 para a frase antiga. Isso muda a segurança e o teste que já passa, fora do escopo de bio e cargo.

## Testes

- **Decision**: Acrescentar cenários em `src/test/java/br/com/lhamalog/core/profile/UserProfileHttpTests.java`, que já usa `@CoreIntegrationTest` e, por ele, `TestcontainersConfiguration` com `mongo:8.0.34`. Não criar outra classe de contexto Spring, não usar tag `latest` e não definir `TEST_MONGODB_URI` para pular o container. Os testes já existentes dessa classe continuam válidos: o corpo deles omite `bio` e `jobTitle`.
- **Rationale**: A constituição exige Testcontainers com tag fixa, e a classe nova de contexto repetiria a subida do Mongo. A spec pede teste de acesso, de perfil antigo e de ausência de chamada à conta.
- **Alternatives considered**: Teste só de unidade das regras, sem HTTP. Não prova o JSON do `GET`, o documento antigo nem o 409 como o cliente vê.

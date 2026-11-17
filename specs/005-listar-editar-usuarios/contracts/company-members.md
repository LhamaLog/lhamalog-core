# Contrato: usuários da empresa

Fonte da verdade para o Web. O Core expõe status e último acesso por `userId` da empresa, a desativação e a reativação. Nome, e-mail, cargo exibido e a troca de cargo ficam no Web, na sessão do administrador, sem chave secreta e sem configuração nova de dashboard.

Caminhos no estilo de `/api/company`. A empresa é a da sessão. O corpo não escolhe empresa.

## Fronteira

| Ação | Quem faz | O Core |
|---|---|---|
| Nome, e-mail e cargo da linha | Web, membros da organização na sessão do administrador | Não devolve esses campos |
| Status e último acesso | Core, por `userId` | `GET /api/company/members` |
| Montar a linha na tela `/equipe` | Web, ao lado dos convites da LLT-167 | Não serve a tela |
| Desativar | Web chama o Core depois da contagem | Grava a marca |
| Reativar | Web chama o Core | Tira a marca |
| Trocar cargo entre `admin` e `member` | Web, `organization.updateMember` na sessão do administrador | Não tem caminho |
| Conferir a marca | Core, em toda requisição autenticada com empresa | 403 se `deactivated` |
| Dashboard da Clerk | Ninguém nesta história | Não pede item novo |

Não criar `CLERK_SECRET_KEY`, SDK da Clerk, cliente de Backend API nem valor que comece com `sk_`.

## Recurso

| Método | Caminho | Corpo |
|---|---|---|
| `GET` | `/api/company/members` | sem corpo |
| `POST` | `/api/company/members/{userId}/deactivation` | sem corpo |
| `DELETE` | `/api/company/members/{userId}/deactivation` | sem corpo |

Não há `PUT`. Não há caminho de cargo, por exemplo `/api/company/members/{userId}/role`. Com sessão autenticada, esse caminho continua 404.

Autenticação: `Authorization: Bearer` com o JWT já aceito pelo Core. A origem continua em `CLERK_AUTHORIZED_PARTIES`.

## Quem acessa

| Operação | Administrador ativo | Membro ativo | Desativado nesta empresa | Sem sessão | Sem empresa |
|---|---|---|---|---|---|
| `GET /api/company/members` | 200 | 403 | 403, marca | 401 | 403, empresa |
| `POST .../deactivation` | 200, ou 403 do último administrador | 403 | 403, marca | 401 | 403, empresa |
| `DELETE .../deactivation` | 200 | 403 | 403, marca | 401 | 403, empresa |
| Qualquer outro `/api/**` com empresa | segue a regra já existente | segue a regra já existente | 403, marca | 401 | 403, empresa, onde a regra atual já exige empresa |

Mensagens:

- Membro em qualquer um dos três caminhos: 403, `code` `not_admin`, "Somente o administrador da empresa pode gerenciar os usuários." A sessão não é encerrada.
- Marca desativada, em qualquer `/api/**` com empresa: 403, `code` `user_deactivated`, "Seu acesso a esta empresa foi desativado."
- Sem empresa: 403, "Selecione uma empresa para continuar." Sem `code`.
- Último administrador ativo conhecido: 403, `code` `last_admin`, "Não é possível desativar o último administrador." O registro não muda.
- Sem JWT: 401, o comportamento atual do filtro.

`userId`, `organizationId` e `tenantId` enviados no corpo são ignorados. O `userId` que vale na desativação e na reativação é o do caminho.

## Campos

| Campo | Onde | Valores |
|---|---|---|
| `userId` | lista e resposta da marca | `sub`, texto de 1 a 200 caracteres no caminho |
| `status` | lista e resposta da marca | `active` ou `deactivated` |
| `lastAccessAt` | lista e resposta da marca | instante ISO-8601 em UTC, ou `null` |

O Web mostra `active` como Ativo e `deactivated` como Desativado. O Core não devolve o rótulo.

O Core não devolve `administrator`, nome, e-mail nem cargo. O cargo da linha é o da organização no Web. O cargo que o Core guarda por dentro serve só para reconhecer o último administrador ativo e não entra nesta resposta.

## GET /api/company/members

200, só a empresa da sessão:

```json
{
  "members": [
    {
      "userId": "user_ana",
      "status": "active",
      "lastAccessAt": "2026-11-15T22:10:00Z"
    },
    {
      "userId": "user_bruno",
      "status": "deactivated",
      "lastAccessAt": null
    }
  ]
}
```

A lista traz quem o Core já observou ou quem já foi desativado nesta empresa. Não traz a organização inteira. A ordem é a de `userId` ascendente.

O Web cria uma linha por membro da organização. Para cada `userId`:

- se o Core devolveu o par, usa `status` e `lastAccessAt`;
- se não devolveu, mostra Ativo e último acesso sem uso registrado;
- se o Core devolveu um `userId` que não está na organização, o Web não inventa linha.

Nome e e-mail vêm do membro da organização. Cargo exibido também. A tela é `/equipe`, no mesmo lugar dos convites.

Abrir esta lista, sendo administrador ativo, é uma requisição aceita: o próprio administrador passa a estar observável.

## POST /api/company/members/{userId}/deactivation

Sem corpo. Sucesso: 200 com o registro dessa pessoa nesta empresa.

```json
{
  "userId": "user_bruno",
  "status": "deactivated",
  "lastAccessAt": "2026-11-15T22:10:00Z"
}
```

Efeito:

- grava `deactivated` para a empresa da sessão e esse `userId`;
- não remove a pessoa da organização;
- não altera `lastAccessAt` nem o cargo observado;
- a próxima requisição dessa pessoa nesta empresa recebe `code` `user_deactivated` e "Seu acesso a esta empresa foi desativado.";
- repetir devolve 200 com `deactivated` e não reativa;
- a empresa da sessão da pessoa desativada em outro lugar não muda.

`userId` vazio ou acima de 200 caracteres: 400, "Informe o usuário." Nada é gravado. `userId` de outra empresa no sentido de organização diferente não é consultado na Clerk; a marca fica só na empresa da sessão.

## DELETE /api/company/members/{userId}/deactivation

Sem corpo. Sucesso: 200 com `status` `active` e o `lastAccessAt` que já estava gravado.

Efeito:

- tira a marca;
- a próxima requisição dessa pessoa nesta empresa volta a ser aceita;
- repetir sobre quem já está ativo devolve 200 com `active` e não desativa;
- não chama a Clerk e não muda o cargo.

A própria pessoa desativada não chega neste caminho. Outro administrador ativo chama.

## Último administrador

Antes de `POST` de desativação e antes de `organization.updateMember` que rebaixa, o Web conta as pessoas da organização cujo cargo, sem o prefixo `org:`, é `admin` e cujo `status` no Core não é `deactivated`. Ausência no `GET` conta como ativo. Se a ação deixasse a conta em zero:

- o Web não chama o `POST` e mostra "Não é possível desativar o último administrador.";
- o Web não chama `organization.updateMember` e mostra "Não é possível rebaixar o último administrador."

O Core, no `POST`, recusa com a mesma frase de desativar quando o alvo é administrador ativo conhecido e a contagem atômica desses conhecidos não é maior que um. O documento não muda.

Desativar a si mesmo é permitido quando essa contagem permanece pelo menos um.

O Core não recusa rebaixamento porque não há caminho de cargo. A trava de rebaixar é do Web.

## Troca de cargo no Web

`organization.updateMember` na sessão do administrador, com `userId` da pessoa e cargo `admin` ou `member`, os mesmos identificadores do convite. `org:admin` e `org:member` continuam equivalentes na leitura que o Core já faz do token; o Web envia o identificador sem prefixo, como no convite.

O Core não grava essa troca. Na próxima requisição aceita da pessoa, o token traz o cargo novo e a observação acompanha, mesmo dentro dos 5 minutos.

E-mail, idioma, fuso e o perfil da LLT-174 não entram neste recurso.

## Observação e 5 minutos

Em toda requisição autenticada com empresa e sem marca:

- o Core lê a marca;
- grava `lastAccessAt` agora quando não há registro, quando o instante gravado tem 5 minutos ou mais, ou quando o cargo do token difere do observado;
- dentro de 5 minutos, com o mesmo cargo, não grava de novo;
- a marca continua sendo lida mesmo nesse intervalo.

Requisição com marca não grava.

O intervalo de produção é 5 minutos, fixo, sem variável nova. No teste ele pode ser menor. Conflito de versão ou chave duplicada nessa gravação não altera o status da requisição: vale um dos instantes gravados.

## Erros

O `ApiError` de hoje tem `message` e `fieldErrors`. Esta história acrescenta `code` opcional, no mesmo objeto. A propriedade só vai no JSON quando existe código. Sem código, o corpo continua `{ "message", "fieldErrors" }`. Os erros já publicados de empresa, perfil e produto não ganham `code`, nem o 403 "Somente o administrador da empresa pode alterar este cadastro." nem o 403 "Selecione uma empresa para continuar." O 401 continua o do filtro atual, sem este corpo.

`not_admin` vale só nos três caminhos desta lista. A frase de rebaixar no Web ("Não é possível rebaixar o último administrador.") não é resposta do Core e não tem `code`.

Marca desativada, em qualquer `/api/**` com empresa:

```json
{
  "message": "Seu acesso a esta empresa foi desativado.",
  "fieldErrors": {},
  "code": "user_deactivated"
}
```

Último administrador ativo conhecido:

```json
{
  "message": "Não é possível desativar o último administrador.",
  "fieldErrors": {},
  "code": "last_admin"
}
```

Membro em `GET`, `POST` ou `DELETE` deste recurso:

```json
{
  "message": "Somente o administrador da empresa pode gerenciar os usuários.",
  "fieldErrors": {},
  "code": "not_admin"
}
```

Os demais continuam sem `code`:

```json
{
  "message": "Informe o usuário.",
  "fieldErrors": {}
}
```

| Situação | Status | `code` | `message` |
|---|---|---|---|
| `userId` vazio ou acima de 200 caracteres | 400 | ausente | Informe o usuário. |
| Sem JWT | 401 | ausente | o comportamento atual do filtro |
| Sem empresa | 403 | ausente | Selecione uma empresa para continuar. |
| Membro nos três caminhos | 403 | `not_admin` | Somente o administrador da empresa pode gerenciar os usuários. |
| Marca desativada em `/api/**` com empresa | 403 | `user_deactivated` | Seu acesso a esta empresa foi desativado. |
| Último administrador ativo conhecido | 403 | `last_admin` | Não é possível desativar o último administrador. |
| Caminho de troca de cargo | 404 | ausente | o comportamento atual de rota ausente |
| Falha de acesso aos dados | 503 | ausente | Não foi possível acessar os dados. Tente novamente. |

`fieldErrors` vai vazio nestes casos. Em todo 403 de último administrador, o status gravado permanece. O Web distingue os três 403 por `code`, não pela frase.

## Risco residual

Estes limites ficam de propósito. O Core não lê a organização.

- O cargo observado espera a próxima requisição aceita da pessoa. Até lá, o Core pode recusar desativar quem já não é o último administrador na Clerk, ou aceitar desativar quem passou a ser o último administrador na Clerk e ainda está observado como membro.
- Administrador que nunca teve requisição aceita não entra na contagem do Core. A tela o conta como ativo. Uma chamada direta ao `POST`, sem a contagem do Web, pode gravar a marca nesse `userId`.
- Uma chamada direta a `organization.updateMember`, sem a contagem do Web, pode rebaixar o último administrador. O Core não vê essa chamada.
- A marca corta este Core. A pessoa continua na organização. O token segue válido até expirar para o que não passa por estes caminhos.
- `lastAccessAt` pode estar até 5 minutos atrás se o cargo observado não mudou.
- A marca de um `userId` ausente da organização fica só na empresa da sessão. A tela não cria linha. Outra empresa não recebe a marca.

## Fora deste contrato

Convite, revogação e reenvio continuam no contrato da LLT-167. Catálogo além de `admin` e `member` (LLT-169), matriz, auditoria e 403 por ação (LLT-170), acesso por produto (LLT-171), e-mail (LLT-172), notificação (LLT-173) e o perfil da própria pessoa (LLT-174) continuam fora. Não há item novo de dashboard.

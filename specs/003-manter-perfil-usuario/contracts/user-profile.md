# Contrato: perfil do usuário

Contrato do Core para o Web. Caminhos no estilo já usado por `/api/company` e `/api/products`. A empresa da operação continua sendo a da sessão; a pessoa do perfil é o `sub` dessa sessão.

Este contrato não altera [o cadastro da empresa](../../002-manter-dados-empresa/contracts/company-registration.md).

## Recurso

| Método | Caminho | Corpo |
|--------|---------|-------|
| `GET` | `/api/user/profile` | sem corpo |
| `PUT` | `/api/user/profile` | JSON abaixo |

Não há `POST`, `DELETE` nem identificador no caminho. Não há lista de perfis.

Autenticação: `Authorization: Bearer` com o JWT Clerk já aceito pelo Core. A origem continua em `CLERK_AUTHORIZED_PARTIES`. Não há chave secreta neste contrato.

## Quem acessa

| Operação | Pessoa autenticada com empresa, membro ou administrador | Sem sessão | Sessão sem empresa |
|----------|----------------------------------------------------------|------------|--------------------|
| `GET /api/user/profile` | 200 | 401 | 403, "Selecione uma empresa para continuar." |
| `PUT /api/user/profile` | 200, ou 400 de validação, ou 409 de versão | 401 | 403, "Selecione uma empresa para continuar." |

O 403 de empresa não selecionada sai com a mensagem já usada no produto. A sessão autenticada permanece. Papel fora de `CLERK_ADMIN_ROLES` não muda estas colunas: membro grava o próprio perfil.

O 401 é o mesmo dos demais caminhos sem JWT. O teste afirma o status, como já faz o cadastro da empresa.

## Campos

| Campo | Obrigatório no `PUT` | Gravado | Consulta sem perfil |
|-------|----------------------|---------|---------------------|
| `displayName` | sim | nome com trim, 1 a 200 caracteres, sem caractere de controle exceto tabulação | `""` |
| `language` | sim | etiqueta `^[a-z]{2}(-[A-Z]{2})?$` | `pt-BR` |
| `timezone` | sim | identificador aceito por `ZoneId`, devolvido como `getId()` | `America/Sao_Paulo` |
| `version` | não; omitido ou `null` na primeira gravação | versão otimista depois da primeira gravação | `null` |

`userId`, `id`, `clerkUserId`, `organizationId` e `tenantId` enviados no JSON são ignorados. Não voltam na resposta.

Aliases aceitos e normalizados na gravação:

- idioma: "Português (Brasil)" e "Portugues (Brasil)" viram `pt-BR`;
- fuso: "(UTC-03:00) Brasília" e "(UTC-03:00) Brasilia" viram `America/Sao_Paulo`.

A resposta sempre devolve a etiqueta e o identificador, nunca o rótulo.

## GET /api/user/profile

Sem perfil gravado, 200 e nada é inserido:

```json
{
  "displayName": "",
  "language": "pt-BR",
  "timezone": "America/Sao_Paulo",
  "version": null
}
```

Com perfil gravado, 200:

```json
{
  "displayName": "Ana Souza",
  "language": "pt-BR",
  "timezone": "America/Sao_Paulo",
  "version": 0
}
```

O `displayName` é o último nome gravado no LhamaLog. Não é lido da Clerk.

## PUT /api/user/profile

Primeira gravação:

```json
{
  "displayName": "Ana Souza",
  "language": "pt-BR",
  "timezone": "America/Sao_Paulo",
  "version": null
}
```

Alteração: os três campos e a `version` devolvida na última resposta aceita. Campo omitido entre os três é inválido; não há merge parcial. Os três são substituídos pelos valores válidos enviados.

Sucesso: 200 com o mesmo formato do `GET`, já com a versão nova.

Versão divergente: 409, corpo de erro abaixo, documento inalterado.

## Erros

Formato já usado por `ApiExceptionHandler`:

```json
{
  "message": "Confira os campos informados.",
  "fieldErrors": {
    "timezone": "Informe um fuso horário válido."
  }
}
```

| Situação | Status | `message` | `fieldErrors` |
|----------|--------|-----------|---------------|
| Nome vazio | 400 | Confira os campos informados. | `displayName`: Informe o nome. |
| Nome acima de 200 caracteres ou com caractere de controle que não seja tabulação | 400 | Confira os campos informados. | `displayName`: O texto excede o limite permitido. |
| Nome que não é texto | 400 | Confira os campos informados. | `displayName`: Confira o preenchimento deste campo. |
| Idioma vazio | 400 | Confira os campos informados. | `language`: Informe o idioma. |
| Idioma fora da etiqueta | 400 | Confira os campos informados. | `language`: Informe um idioma válido. |
| Fuso vazio | 400 | Confira os campos informados. | `timezone`: Informe o fuso horário. |
| Fuso irreconhecível | 400 | Confira os campos informados. | `timezone`: Informe um fuso horário válido. |
| Campo ausente ou de tipo errado | 400 | Confira os campos informados. | o campo, com "Confira o preenchimento deste campo." quando não houver mensagem mais específica |
| `version` de tipo errado | 400 | Confira os campos informados. | `version`: Confira o preenchimento deste campo. |
| Corpo ilegível | 400 | Os dados enviados são inválidos. | objeto vazio |
| Sem JWT | 401 | o comportamento atual do filtro, sem perfil | |
| JWT sem empresa | 403 | Selecione uma empresa para continuar. | objeto vazio |
| Versão divergente, ou duas primeiras gravações da mesma pessoa | 409 | Os dados foram alterados. Recarregue antes de salvar novamente. | objeto vazio |
| Falha de acesso aos dados | 503 | Não foi possível acessar os dados. Tente novamente. | objeto vazio |

Em todo 400 e no 409, o perfil gravado permanece como estava. O 403 de empresa não selecionada também não grava.

## Isolamento

- A resposta contém só o perfil da pessoa da sessão.
- Duas pessoas da mesma empresa têm perfis diferentes.
- A mesma pessoa, com a empresa A ou a empresa B selecionada, recebe o mesmo perfil.
- Um corpo que cita outra pessoa ou outra empresa ainda grava só o perfil da sessão.
- `GET` e `PUT` do perfil não alteram `GET /api/company`. Fuso e idioma da empresa ficam nos campos que já existem (`preferences.timezone`, `data.timezone`, `shared.timezone`, `settings.data.timeZone`, e os campos de idioma correspondentes).

## Fora deste contrato

- Não há chamada à Clerk. O write-back do nome é item em aberto: a integração atual só valida o JWT (`CLERK_ISSUER`, `CLERK_JWKS_URI`, `CLERK_AUTHORIZED_PARTIES`) e não tem segredo nem cliente para `PATCH /v1/users/{user_id}`. Este contrato não cria essa chamada. O `displayName` devolvido é o gravado no Core.
- Não há painel, agendamento de varredura nem e-mail neste Core. O `timezone` fica disponível nesta resposta para esses consumidores nas entregas deles.
- O Core não traduz mensagens por causa de `language`. O Web aplica o idioma na interface.
- E-mail da pessoa e notificações não têm campo aqui (LLT-172 e LLT-173).
- CEP, PDF e sincronização da empresa continuam 501 e não fazem parte deste recurso.

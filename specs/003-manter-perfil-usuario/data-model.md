# Modelo de dados: Manter perfil do usuário

O perfil é novo. O cadastro da empresa permanece o de [Manter dados da empresa](../002-manter-dados-empresa/data-model.md) e não é atualizado por esta feature.

## Perfil (`UserProfile`)

- **Identidade**: `id` é a pessoa da sessão, o `sub` do JWT (`CompanySession.userId()`). O cliente não escolhe esse id.
- **Campos gravados**:
  - `displayName`: texto obrigatório, até 200 caracteres, sem caractere de controle exceto tabulação, já com trim.
  - `language`: etiqueta obrigatória. Aliases "Português (Brasil)" e "Portugues (Brasil)" são gravados como `pt-BR`. O valor gravado casa `^[a-z]{2}(-[A-Z]{2})?$`.
  - `timezone`: identificador obrigatório aceito por `ZoneId`. Aliases "(UTC-03:00) Brasília" e "(UTC-03:00) Brasilia" são gravados como `America/Sao_Paulo`. O valor gravado é `ZoneId.of(valor).getId()`.
  - `updatedAt`: instante da última gravação aceita.
  - `version`: versão otimista. A primeira gravação parte de ausência. A alteração envia a versão vigente.
- **O que não é gravado**: empresa, e-mail, notificação, papel, segredo e cópia do token. Identificadores enviados pelo cliente (`userId`, `id`, `clerkUserId`, `organizationId`, `tenantId`) são ignorados.
- **Unicidade**: no máximo um documento por pessoa. O id é a chave.
- **Ausência**: sem documento, a leitura devolve `displayName` vazio, `language` `pt-BR`, `timezone` `America/Sao_Paulo` e `version` nula. A leitura não insere.
- **Validação**: campo inválido responde 400 com `fieldErrors` e não grava. Corpo ilegível responde 400 e não grava.
- **Transições**:
  1. Ausente → gravado: a pessoa envia `version` nula e os três campos válidos. Se duas primeiras gravações da mesma pessoa partem juntas, uma insere e a outra responde 409, pelo conflito de versão ou pela chave duplicada já tratada no handler atual.
  2. Gravado → gravado: a pessoa envia a `version` vigente. Versão divergente responde 409 e o documento fica como estava.
  3. Não há exclusão nesta feature. Não há transição de volta para ausente.

## Sessão

- **Pessoa**: `sub` não vazio, já exigido por `ClerkTokenValidator`.
- **Empresa**: `o.id` ou, na forma antiga, `org_id`. Ausente continua 403 com "Selecione uma empresa para continuar." A empresa não entra no documento.
- **Papel**: não seleciona o perfil. `administrator()` não é consultado neste fluxo.
- **Sessão ausente**: 401, antes do controlador, como nos demais caminhos `/api/**`.

## O que este perfil não alcança

- **Empresa** (`Tenant`), **logotipo** (`TenantLogo`) e **histórico** (`TenantContextRevision`) não são lidos nem gravados pela manutenção do perfil. O fuso e o idioma da empresa continuam os campos já existentes.
- **Produto** (`Produto`) e **vínculo** (`AcessoProduto`) permanecem.
- A pessoa na Clerk não é lida nem atualizada. Não há coleção de auditoria nova.

## Isolamento

Toda leitura e gravação usa o `userId` resolvido na sessão. Dois documentos de pessoas diferentes não se misturam. A mesma pessoa, com duas empresas selecionadas em sessões diferentes, lê o mesmo documento. Um corpo que cita outra pessoa ou outra empresa não muda a chave.

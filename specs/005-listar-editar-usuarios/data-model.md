# Modelo de dados: Listar, editar e desativar usuários

O registro é novo e por empresa. O perfil da LLT-174 continua um documento por pessoa, sem e-mail, sem cargo e sem status. O cadastro da empresa não é atualizado por esta feature.

## Membro observado (`CompanyMember`)

- **Identidade**: par (`tenantId`, `userId`). `tenantId` é a empresa da sessão (`o.id` ou, sem o mapa `o`, `org_id`). `userId` é o `sub` do JWT. O cliente não escolhe a empresa. Na desativação e na reativação, o `userId` alvo vem do caminho e pertence a essa empresa.
- **Campos**:
  - `administrator`: verdadeiro quando o cargo do último token aceito, sem um prefixo `org:`, está em `CLERK_ADMIN_ROLES` (padrão `admin`).
  - `status`: `active` ou `deactivated`.
  - `lastAccessAt`: instante da última requisição aceita que foi gravada. Nulo se a pessoa foi desativada sem nunca ter tido requisição aceita.
  - `version`: versão otimista do documento.
- **O que não é gravado**: nome, e-mail, cargo cru, token, segredo e identificador de outra empresa.
- **Unicidade**: no máximo um documento por empresa e `userId`.
- **Ausência**: a pessoa está na organização e o Core ainda não a viu nem a desativou. O Web mostra status Ativo e último acesso sem uso. A leitura da lista não cria esse documento. A requisição aceita da própria pessoa cria.
- **Validação**: `userId` do caminho vazio ou acima de 200 caracteres é recusado com 400 e não grava. Outro identificador de empresa no corpo é ignorado.

## Contagem de administradores ativos (`CompanyAdminCount`)

- **Identidade**: `tenantId` da empresa. Um documento por empresa.
- **Campo**: `activeAdminCount`, inteiro do número de `CompanyMember` da empresa com `administrator` verdadeiro e `status` `active`.
- **Nascimento**: se o documento não existe, ele nasce com a contagem dos registros atuais e não sobrescreve um valor já gravado.
- **Subida**: só depois que a escrita do membro realmente passa a pessoa a administrador ativo. Reativação de quem está observado como administrador sobe 1 só se o status saiu de `deactivated`. Inserção que perde a chave única não sobe de novo.
- **Descida**: requisição aceita cujo token deixa de ser de administrador, só se o membro deixou de ser administrador ativo nessa escrita; desativação de administrador ativo que passou na condição atômica.
- **Desativar administrador ativo**: a contagem só desce se `activeAdminCount > 1` no mesmo passo atômico. Sem esse casamento, o membro não muda. Se a contagem desceu e o membro não chegou a mudar, a contagem sobe 1 de volta.
- **Conflito de observação**: versão ou chave duplicada ao gravar `lastAccessAt` não falha a requisição. Fica o instante gravado por uma das chamadas.
- **Não participa**: gravação de `lastAccessAt` com o mesmo cargo observado. Marca de alguém que o Core não conhece como administrador ativo.

## Transições do membro

1. Ausente → `active`: a primeira requisição aceita grava `administrator` segundo o token, `status = active` e `lastAccessAt` agora.
2. `active` → `active`: requisição aceita. Se `lastAccessAt` tem 5 minutos ou mais, ou se `administrator` difere do token, grava de novo e move `lastAccessAt` para agora. Dentro de 5 minutos, com o mesmo cargo, não grava.
3. `active` → `deactivated`: o administrador desativa. `lastAccessAt` e `administrator` ficam como estavam. Se a pessoa é o último administrador ativo conhecido, a transição não ocorre.
4. Ausente → `deactivated`: o administrador desativa um `userId` sem registro. Nasce `administrator = false`, `status = deactivated` e `lastAccessAt` nulo. Não mexe na contagem.
5. `deactivated` → `active`: o administrador reativa. O próximo request aceito dessa pessoa observa de novo. `lastAccessAt` só muda nessa requisição seguinte, não na reativação.
6. `deactivated` → `deactivated`: desativar de novo não muda o documento e não reativa. A requisição da própria pessoa é recusada e não observa.
7. Não há exclusão do documento nesta feature.

## Sessão

- **Pessoa**: `sub` não vazio, já exigido por `ClerkTokenValidator`.
- **Empresa**: `o.id` ou, na forma antiga, `org_id`. Ausente continua 403 com "Selecione uma empresa para continuar." O interceptor não aplica a marca quando não há empresa.
- **Cargo do token**: define quem pode gerenciar usuários nesta requisição e, na observação, o valor de `administrator`. Não é lido de documento para autorizar o administrador que está chamando.
- **Marca**: se o documento da empresa e do `sub` está `deactivated`, a requisição em `/api/**` responde 403 "Seu acesso a esta empresa foi desativado." e não observa.
- **Sessão ausente**: 401, antes do controlador, como nos demais caminhos `/api/**`.

## Isolamento

A lista, a desativação e a reativação usam a empresa da sessão. Um corpo que cita outra empresa não muda a chave. A mesma pessoa em duas empresas tem dois documentos. O perfil `UserProfile` não é lido nem gravado por estes fluxos.

## O que este registro não alcança

- **Empresa**, logotipo e contexto permanecem.
- **Produto** e `AcessoProduto` permanecem. A LLT-171 continua fora.
- O cargo exibido e a troca `admin`/`member` não são campos escritos por endpoint. O Web altera a organização. Este documento só reflete o token da próxima requisição aceita.
- Não há coleção de convite. Os convites seguem na Clerk e na tela `/equipe`.

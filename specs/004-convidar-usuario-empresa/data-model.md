# Modelo de dados: Convidar usuário para a empresa

Não há coleção nova. O cadastro da empresa permanece o de [Manter dados da empresa](../002-manter-dados-empresa/data-model.md). O perfil permanece o de [Manter perfil do usuário](../003-manter-perfil-usuario/data-model.md). Nenhum dos dois é atualizado por esta feature.

## O que não é gravado

- Convite, e-mail do convidado, cargo escolhido no convite, prazo, estado do convite e lista de pendentes.
- Vínculo da pessoa com a empresa. Não há documento de membro.
- Segredo, cópia de token e identificador de convite da Clerk.
- `Tenant`, `TenantLogo`, `TenantContextRevision`, `Produto`, `AcessoProduto` e `UserProfile` não ganham campo.

## Sessão, que é o vínculo depois do aceite

A sessão já existe e não muda de forma. Cada requisição resolve:

- **Pessoa**: `sub` não vazio, já exigido por `ClerkTokenValidator`. É `CompanySession.userId()`.
- **Empresa**: `o.id`. Na forma antiga, sem o mapa `o`, `org_id`. Ausente continua 403 com "Selecione uma empresa para continuar." O cliente não escolhe a empresa.
- **Cargo**: `o.rol`. Na forma antiga, `org_role`. Um prefixo `org:` no início é removido antes da comparação.
- **Administrador**: o cargo resultante está em `CLERK_ADMIN_ROLES`. O padrão é `admin`. `admin` e `org:admin` são administrador. `member` e `org:member` não são. O nome exibido não entra na comparação.
- **Sessão ausente**: 401, antes do controlador, como nos demais caminhos `/api/**`.

Não há versão, prazo nem estado gravados junto dessa sessão. O Core não lembra que a sessão nasceu de um convite.

## Convite na Clerk

O convite não é entidade deste repositório. O ciclo abaixo acontece na Clerk, conduzido pelo Web:

1. Pendente: o administrador cria o convite com e-mail e cargo `admin` ou `member`. A Clerk envia o e-mail. O prazo é de 7 dias a partir do envio.
2. Pendente → pendente: o administrador reenvia. O prazo volta a contar 7 dias a partir do reenvio.
3. Pendente → revogado: o administrador revoga. O aceite deixa de criar sessão.
4. Pendente → expirado: passam 7 dias sem aceite e sem reenvio. O aceite deixa de criar sessão.
5. Pendente → aceito: a pessoa aceita nas telas de entrada já existentes. A próxima sessão traz a organização e o cargo. Não há segundo aceite desse mesmo convite.

Nenhuma dessas transições escreve no Mongo do Core.

## Isolamento

Duas empresas continuam isoladas pela organização da sessão. Uma sessão com a empresa A não lê nem grava a empresa B. O convite criado no Web usa a organização ativa do administrador, não um identificador enviado ao Core.

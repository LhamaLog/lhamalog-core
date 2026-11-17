# Quickstart: Listar, editar e desativar usuários

Guia de verificação. Não implementa a feature. Os exemplos de pedido estão no [contrato](contracts/company-members.md). Os campos estão no [modelo](data-model.md).

## Pré-requisitos

- JDK 25 e Docker, para `./mvnw verify` (ou `.\mvnw.cmd verify`).
- A imagem de teste é `mongo:8.0.34`, em `TestcontainersConfiguration`. Não usar tag `latest`.
- Nenhum `CLERK_SECRET_KEY` e nenhum valor que comece com `sk_`.
- Não configurar o dashboard da Clerk para esta história.

## O que o teste HTTP precisa provar

Classe prevista: `src/test/java/br/com/lhamalog/core/member/CompanyMemberHttpTests.java`.

O mesmo arranque dos testes HTTP já existentes: `@Import(TestcontainersConfiguration.class)`, `@SpringBootTest`, `@AutoConfigureMockMvc`, `management.otlp.metrics.export.enabled=false` e `management.tracing.export.enabled=false`. JWT sintético com `sub` e `o.id` / `o.rol`. Sem `TEST_MONGODB_URI`.

1. Administrador `GET /api/company/members` recebe 200. Membro recebe 403 "Somente o administrador da empresa pode gerenciar os usuários." Sem JWT, 401. Sem empresa, 403 "Selecione uma empresa para continuar."
2. A lista da empresa A não traz `userId` observado só na empresa B.
3. `POST /api/company/members/{userId}/deactivation` de quem não é o último administrador ativo conhecido responde 200 com `status` `deactivated`. A requisição seguinte dessa pessoa em `GET /api/company`, `GET /api/products` e `GET /api/user/profile` responde 403 "Seu acesso a esta empresa foi desativado." O `GET` de outra pessoa da mesma empresa continua 200.
4. Repetir o `POST` mantém `deactivated`. `DELETE` volta `active`, e a requisição seguinte da pessoa volta a ser aceita. Repetir o `DELETE` mantém `active`.
5. Com um único administrador ativo conhecido, o `POST` contra ele responde 403 "Não é possível desativar o último administrador." e o `GET` seguinte o mostra `active`.
6. Com dois administradores ativos conhecidos, desativar um deixa o outro `active`.
7. `POST /api/company/members/{userId}/role` responde 404.
8. Duas requisições aceitas da mesma pessoa, com o mesmo cargo, dentro do intervalo, deixam `lastAccessAt` igual. Depois do intervalo, ou com o cargo do token diferente, `lastAccessAt` muda. O teste controla o relógio. A marca é conferida também dentro do intervalo.
9. Desativar na empresa A não muda o acesso da mesma pessoa na empresa B.
10. O teste não define `CLERK_SECRET_KEY` e não adiciona dependência no `pom.xml`.

## Web, fora deste repositório

Na tela `/equipe`, ao lado dos convites:

- uma linha por membro da organização;
- nome, e-mail e cargo da organização;
- status e último acesso do `GET`, ou Ativo e sem uso quando o `userId` não veio;
- antes de desativar ou rebaixar, a contagem do contrato; se ficaria zero, não chamar o Core nem `organization.updateMember`;
- troca de cargo com `organization.updateMember` e identificador `admin` ou `member`.

## Fora desta verificação

Não provar convite, matriz de permissão, auditoria, acesso por produto, e-mail, notificação nem o perfil além do 403 da marca. Não chamar a Clerk real.

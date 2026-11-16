# Validação: Convidar usuário para a empresa

Prova local da [spec](spec.md) pelo [contrato](contracts/organization-invite.md). Sem pull request e sem troca para outra branch. A implementação ainda não foi feita; os testes descritos aqui são o que a fase de tasks acrescenta.

## Pré-requisitos

- JDK 25 e o Maven Wrapper do repositório.
- Docker em execução para o teste HTTP. Ele reutiliza `TestcontainersConfiguration` com Mongo `mongo:8.0.34`.
- Nenhum token Clerk real e nenhuma chave secreta. Os cenários usam JWT sintético.

## Comando

Na raiz do repositório:

```powershell
.\mvnw.cmd verify
```

No Linux:

```bash
./mvnw verify
```

O comando precisa terminar com sucesso. `CompanySessionTests` e `OrganizationInviteBoundaryTests` fazem parte desse `verify`.

## Cenários do Core

1. **Membro recém-aceito.** JWT com `o.id` da empresa e `o.rol` `member`. `administrator()` é falso. `GET /api/company` devolve o cadastro. `PUT /api/company` responde 403 e o cadastro não muda. O mesmo vale para `o.rol` `org:member` e para a forma antiga `org_role` `member` e `org:member`.
2. **Administrador recém-aceito.** JWT com `o.rol` `admin`. `administrator()` é verdadeiro e o `PUT /api/company` da própria empresa é aceito. O mesmo vale para `o.rol` `org:admin` e para `org_role` `admin` e `org:admin`.
3. **Nome exibido.** O identificador `Administrador` continua sem poder de administrador, coberto pelo teste de cadastro já existente.
4. **Sessão.** Pedido sem autenticação responde 401. JWT sem `o.id` e sem `org_id` responde 403 com "Selecione uma empresa para continuar."
5. **Sem caminho de convite.** Com sessão autenticada, `POST /api/invitations`, `POST /api/company/invitations` e `POST /api/organization-invitations` respondem 404. Não surge controlador de convite.
6. **Sem segredo e sem e-mail.** `pom.xml` não ganha SDK da Clerk nem biblioteca de e-mail. `application.properties`, `.env.example`, `.env.prod.example`, `compose.yaml` e `compose.prod.yaml` não ganham `CLERK_SECRET_KEY`.

## O que este comando não prova

O recebimento do e-mail, a espera de 7 dias, a lista de pendentes, a revogação, o reenvio e o aceite nas telas de entrada são do Web e da Clerk. O `verify` deste repositório não chama a Clerk. A configuração do dashboard fica registrada no contrato para outra pessoa aplicar.

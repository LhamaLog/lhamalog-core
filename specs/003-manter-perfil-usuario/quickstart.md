# Validação: Manter perfil do usuário

Prova local da [spec](spec.md) pelo [contrato](contracts/user-profile.md). Sem troca de branch e sem pull request.

## Pré-requisitos

- JDK 25 e o Maven Wrapper do repositório.
- Docker em execução. Os testes HTTP novos sobem Mongo `mongo:8.0.34` pela `TestcontainersConfiguration` já existente, junto dos containers fixos que essa configuração publica. A tag não pode ser `latest`.
- Nenhum token Clerk real e nenhum segredo. Os cenários usam JWT sintético de teste, com `sub` e `o.id` / `o.rol`.

## Comando

Na raiz do repositório:

```bash
./mvnw verify
```

```powershell
.\mvnw.cmd verify
```

O comando precisa terminar com sucesso. A classe `UserProfileHttpTests` faz parte desse `verify`.

## Cenários

Os detalhes de status e corpo estão no contrato. Cada item abaixo precisa aparecer na suíte HTTP.

1. **Consulta inicial.** Sem perfil gravado, `GET /api/user/profile` devolve nome vazio, `language` `pt-BR`, `timezone` `America/Sao_Paulo` e `version` nula. Um segundo `GET` continua com `version` nula.
2. **Gravação e alteração.** `PUT` com `version` nula grava os três campos. O `GET` seguinte devolve esses valores. Um segundo `PUT` com a versão vigente muda um campo e mantém os outros dois. Um `PUT` com a versão antiga responde 409 e a consulta fica no segundo salvamento. Duas primeiras gravações da mesma pessoa também terminam com uma aceita e a outra em 409.
3. **Validação.** Nome vazio, nome acima de 200 caracteres, idioma inválido e fuso inválido respondem 400 com o `fieldErrors` do contrato. A consulta seguinte permanece no último salvamento aceito. Rótulo "Português (Brasil)" volta como `pt-BR`. Rótulo "(UTC-03:00) Brasília" volta como `America/Sao_Paulo`.
4. **Isolamento.** Duas pessoas da mesma empresa não veem o perfil uma da outra. Um corpo com `userId` ou `organizationId` de outra pessoa ainda grava só quem está na sessão. A mesma pessoa, em duas empresas, recebe o mesmo perfil.
5. **Papel.** Membro, com slug fora de `CLERK_ADMIN_ROLES`, consulta e grava o próprio perfil com sucesso.
6. **Sessão.** Pedido sem autenticação responde 401. JWT sem empresa responde 403 com "Selecione uma empresa para continuar." e não grava.
7. **Empresa intacta.** Depois de gravar fuso e idioma no perfil, `GET /api/company` mantém o fuso e o idioma que a empresa já tinha.
8. **Identidade.** A gravação do nome sucede sem `CLERK_SECRET_KEY` e sem cliente da Clerk. A consulta e a resposta do `PUT` devolvem o nome gravado. A suíte afirma que o Core não chama a Clerk. Atualizar a identidade na sessão do navegador (`user.update`) é do Web, depois do sucesso, e não entra nesta suíte.
9. **Fora desta entrega.** A suíte não cria painel, agendamento de varredura, envio de e-mail nem campo de notificação. CEP, PDF e sincronização da empresa não são alterados por estes testes.

## Resultado em 2026-11-12

`bash ./mvnw verify` com JDK Temurin 25.0.5. O `./mvnw` do repositório não está executável neste ambiente; o comando foi o mesmo wrapper. A máquina não tem Docker.

- `CompanyContextTests`: 3 passaram.
- `CompanySettingsTests`: 7 passaram.
- `CompanySessionTests`: 2 passaram.
- `ClerkTokenValidatorTests`: 5 passaram.
- `ApiSecurityTests`: 2 passaram.
- `CompanyProductIntegrationTests`: 5 ignorados, porque `TEST_MONGODB_URI` não está definida.
- `UserProfileHttpTests`: 6 erros ao subir o contexto. O Testcontainers não encontrou Docker (`Could not find a valid Docker environment`). A classe não foi executada e não está aprovada.
- `CompanyRegistrationHttpTests`: 7 erros pelo mesmo motivo.
- `SecurityConfigTests`: 2 erros pelo mesmo motivo.
- `LhamalogCoreApplicationTests`: 1 erro pelo mesmo motivo.

Totais do Surefire: 40 testes, 0 falhas de asserção, 16 erros de contexto, 5 ignorados. `./mvnw verify` não foi dado como aprovado. O portão local continua exigindo Docker com as imagens fixas de `TestcontainersConfiguration`, inclusive `mongo:8.0.34`. Os testes HTTP do perfil permanecem na suíte e não foram enfraquecidos.

## Fora desta validação

Não há cenário de tela. Não há `user.update`, `PATCH /v1/users`, SDK da Clerk nem `CLERK_SECRET_KEY`. Não há verificação de e-mail da pessoa nem de notificação.

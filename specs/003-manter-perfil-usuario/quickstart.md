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
8. **Identidade.** A gravação do nome sucede sem variável de segredo da Clerk. A consulta devolve o nome gravado. A suíte não chama a Clerk.
9. **Fora desta entrega.** A suíte não cria painel, agendamento de varredura, envio de e-mail nem campo de notificação. CEP, PDF e sincronização da empresa não são alterados por estes testes.

## Resultado

Ainda não executado. Esta execução para na especificação. O `verify` fica para a implementação das tasks, que deve registrar aqui o resultado.

## Fora desta validação

Não há cenário de tela. Não há `PATCH` na Clerk. Não há segredo em variável de ambiente neste guia. Não há verificação de e-mail da pessoa nem de notificação.

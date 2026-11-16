# Validação: Manter dados da empresa

Prova local da [spec](spec.md) pelo [contrato](contracts/company-registration.md). Sem commit e sem troca de branch.

## Pré-requisitos

- JDK 25 e o Maven Wrapper do repositório.
- Docker em execução. Os testes HTTP novos sobem Mongo `mongo:8.0.34` pela `TestcontainersConfiguration` já existente, junto dos containers fixos que essa configuração publica.
- Nenhum token Clerk real. Os cenários usam JWT sintético de teste.

## Comando

Na raiz do repositório:

```powershell
.\mvnw.cmd verify
```

O comando precisa terminar com sucesso. A classe nova de HTTP do cadastro da empresa faz parte desse `verify`. Se `TEST_MONGODB_URI` também estiver definida, `CompanyProductIntegrationTests` roda junto e as gravações de empresa desse arquivo usam papel `admin`.

## Cenários

Os detalhes de status e corpo estão no contrato. Cada item abaixo precisa aparecer na suíte HTTP.

1. **Administrador cadastra e altera.** `PUT` com `version: null`, consulta devolve os valores, segundo `PUT` com a versão vigente altera um campo e preserva outro. Um `PUT` com a versão antiga responde 409 e a consulta fica com a primeira alteração.
2. **Membro só lê.** Na mesma organização, `GET` da empresa, do logotipo existente e das revisões devolve o cadastro. `PUT`, `POST` do logotipo, `DELETE` do logotipo e `DELETE` do cadastro respondem 403. A consulta seguinte permanece igual. Corpo inválido de membro também responde 403.
3. **Papel fora da lista.** Com o padrão `admin`, um slug que não está em `CLERK_ADMIN_ROLES` consulta e recebe 403 ao gravar ou excluir.
4. **Sessão.** Pedido sem autenticação responde 401. Sessão sem organização responde 403 com a mensagem já usada para empresa não selecionada, em consulta, gravação e exclusão.
5. **Exclusão.** Administrador grava cadastro, logotipo e uma mudança de contexto, e existe um produto nessa organização. `DELETE /api/company` responde 200 com cadastro vazio e `version` nula. Logotipo responde 404. Revisões ficam vazias. O produto continua. Outro `DELETE` responde 200 vazio. Novo `PUT` com `version: null` grava só os dados novos.
6. **Isolamento.** Organizações A e B têm cadastros diferentes. Consulta, alteração e exclusão com a sessão de A deixam B igual e oculto. Um corpo com o id de B ainda afeta só A. Membro de A que cita B recebe 403 e as duas empresas ficam como estavam.
7. **Caminhos fora desta entrega.** Consulta de CEP, exportação de PDF e sincronização continuam 501 para administrador e membro.

## Resultado em 2026-11-11

`./mvnw test` com JDK 25.0.5, sem Docker:

- `CompanyContextTests`: 3 passaram.
- `CompanySettingsTests`: 7 passaram.
- `CompanyProductIntegrationTests`: 5 ignorados, porque `TEST_MONGODB_URI` não está definida.
- `CompanyRegistrationHttpTests`: 7 erros ao subir o contexto. O Testcontainers não encontrou Docker (`Could not find a valid Docker environment`). A classe não foi executada e não está aprovada.
- `SecurityConfigTests` e `LhamalogCoreApplicationTests`, em execução separada, falharam pelo mesmo motivo.

`./mvnw verify` não foi dado como aprovado. O portão local continua exigindo Docker com as imagens fixas de `TestcontainersConfiguration`.

## Fora desta validação

Não há cenário de tela. Não há chamada à Clerk para apagar organização. Não há verificação de faturamento, token, alerta ou métrica.

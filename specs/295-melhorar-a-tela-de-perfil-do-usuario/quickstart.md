# Validação: Melhorar a tela de perfil do usuário

Prova local da [spec](spec.md) pelo contrato canônico [user-profile.md](../003-manter-perfil-usuario/contracts/user-profile.md) e pelo delta [user-profile.md](contracts/user-profile.md). Sem troca de branch e sem pull request.

Esta rodada de plano não executa o `verify`. O comando abaixo é o portão de quem for implementar.

## Pré-requisitos

- JDK 25 e o Maven Wrapper do repositório.
- Docker em execução. `UserProfileHttpTests` sobe com `@CoreIntegrationTest`, que importa `TestcontainersConfiguration` e Mongo `mongo:8.0.34`. A tag não pode ser `latest`. Não definir `TEST_MONGODB_URI` para desviar do container.
- Nenhum token Clerk real e nenhum segredo. Os cenários usam o JWT sintético que a classe de teste já monta.

## Comando

Na raiz do repositório:

```bash
./mvnw verify
```

```powershell
.\mvnw.cmd verify
```

O comando precisa terminar com sucesso. A classe `UserProfileHttpTests` faz parte desse `verify`, com os testes que já existiam e os desta entrega.

## Cenários

Os status e os corpos estão no contrato. Cada item abaixo precisa aparecer na suíte HTTP.

1. **Bio e cargo.** Com versão vigente, `PUT` grava `bio` de até 160 caracteres e `jobTitle` de até 80. O `GET` seguinte devolve os dois textos e a versão nova. `jobTitle` "Administrador" não cria campo de papel, função ou organização.
2. **Limite e texto puro.** Bio de 161, cargo de 81 ou texto com quebra de linha responde 400 em `fieldErrors.bio` ou `fieldErrors.jobTitle`, com "O texto excede o limite permitido." O `GET` seguinte permanece no último salvamento. Texto com `<b>` é aceito e devolvido com esses caracteres. Tipo que não é texto responde "Confira o preenchimento deste campo."
3. **Versão.** `PUT` com a versão antiga, mudando bio e cargo, responde 409. A consulta fica no salvamento anterior.
4. **Perfil antigo.** Um documento sem as chaves `bio` e `jobTitle` responde 200, com as duas como `""`, e nome, idioma, fuso e versão intactos. `PUT` sem as duas chaves é aceito e não inventa texto.
5. **Omissão e apagamento.** Omitir um campo mantém o valor guardado. `""`, `null` ou só espaços apaga esse campo. O `GET` devolve `""`. Consulta sem perfil não cria documento e também devolve `""` nos dois.
6. **Fora do perfil.** A resposta não traz foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas, sessões, tema nem acessibilidade. Mandar tema no corpo não grava e não devolve. A suíte afirma que o Core não chama a conta. Empresa e preferências de notificação não mudam.
7. **Suíte anterior.** Os testes já existentes de `UserProfileHttpTests` continuam passando. O corpo deles omite `bio` e `jobTitle`.

## Fora desta validação

Não há cenário de tela. Não há `user.update`, SDK da Clerk nem `CLERK_SECRET_KEY`. Não há verificação de tema no navegador. Esta rodada não registra resultado de `verify` porque o código ainda não foi escrito.

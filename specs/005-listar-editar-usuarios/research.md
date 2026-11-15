# Research: Listar, editar e desativar usuários

## Marca de desativado, sem mexer na Clerk

- **Decision**: A pessoa desativada continua na organização. O Core grava `status = deactivated` para o par empresa + `userId` e, em toda requisição autenticada que tenha empresa, recusa com 403 se essa marca existir. O token pode continuar válido. O Core não remove membro, não troca cargo e não chama a Clerk.
- **Rationale**: A decisão pede o mínimo na Clerk, que é de outra pessoa, e o corte na próxima requisição. A sessão já emitida seguiria aceita se o Core só lesse o token. A marca é a única trava que vale antes de o token expirar. Manter a pessoa na organização também é o que permite a lista mostrar status Desativado e a reativação devolver o acesso sem convite novo.
- **Alternatives considered**: Retirar a pessoa da organização pelo SDK. Tira o acesso só depois que o token expira e some com a linha de status. Fazer os dois. Mexeria na Clerk, o que esta decisão evita. Chave secreta para revogar a sessão. Está fora da regra.

## O que o Core conhece

- **Decision**: Um documento por empresa e `userId`. Os campos são o `userId`, se o cargo visto no último token aceito é administrador, o status e o instante do último acesso aceito. O cargo administrador segue `CLERK_ADMIN_ROLES` (padrão `admin`), já sem o prefixo `org:`. Não há nome, e-mail nem cópia do token.
- **Rationale**: Sem chave secreta, o Core não lista a organização. O token da própria pessoa é a única fonte de cargo que ele pode ver. O Web continua dono de nome, e-mail e cargo exibido, lidos na sessão do administrador.
- **Alternatives considered**: Guardar o cargo que o Web envia na desativação. O cliente escolheria o cargo e a trava do último administrador ficaria forjável. Ler a organização pela Backend API. Exige `CLERK_SECRET_KEY`.

## Último acesso a cada 5 minutos

- **Decision**: Toda requisição autenticada com empresa lê a marca. A gravação do instante só ocorre quando não há registro, quando o instante gravado tem 5 minutos ou mais, ou quando o cargo do token difere do cargo observado. A requisição recusada por desativação não grava. O intervalo é fixo em 5 minutos no código de produção e pode ser substituído no teste. Não há variável de ambiente nova. Duas gravações simultâneas do mesmo par empresa + `userId` não falham a requisição: conflito de versão ou chave duplicada deixa valer uma das gravações e a chamada segue.
- **Rationale**: "Poucos minutos" pede folga sem deixar a lista velha por uma sessão inteira de trabalho. Cinco minutos cobre uma sequência de chamadas da tela sem uma escrita por request, e ainda mostra uso recente. Um minuto grava demais. Quinze deixa o administrador vendo acesso frio no meio do uso. Gravar o cargo na hora em que ele muda evita que a trava do último administrador espere a folga.
- **Alternatives considered**: Gravar em toda requisição. Cumpre o aceite e custa uma escrita por chamada, o que a decisão pediu para evitar. Gravar só a primeira request da sessão. O último acesso ficaria no login, não no uso. Coluna vazia. Não cumpre o aceite.

## Quem é o último administrador para o Core

- **Decision**: Administrador ativo conhecido é o registro da empresa com cargo observado de administrador e status ativo. A contagem sobe ou desce só depois que a escrita condicional do `CompanyMember` realmente muda esse par. Inserção duplicada não conta de novo. Desativar administrador ativo só prossegue se `activeAdminCount > 1` no mesmo `findAndModify` que desce 1. Se a condição não casa, a desativação é recusada e o membro não muda. Se o membro não mudar depois que a contagem já desceu, a contagem volta 1. A contagem nasce, se ainda não existe, com o número de registros que já são administrador ativo, sem sobrescrever uma contagem já gravada.
- **Rationale**: A decisão pede que o Core recuse o último administrador ativo que ele conhece, sem chave secreta. A condição num único documento fecha duas desativações simultâneas sem transação de vários documentos. O Mongo local do compose não está em replica set, então transação entre documentos não é o caminho.
- **Alternatives considered**: Contar os registros e só depois atualizar. Duas chamadas simultâneas leem dois e as duas desativam. Transação Mongo. O compose local não oferece replica set. Confiar só na contagem do Web. Uma chamada direta ao Core furaria o aceite.

## Contagem no Web

- **Decision**: Antes de desativar e antes de rebaixar, o Web conta as pessoas da organização cujo cargo, sem `org:`, é `admin` e cujo status no Core não é `deactivated`. Registro ausente no Core conta como ativo. Se a ação deixasse essa conta em zero, o Web não chama o Core e não chama a Clerk. A mensagem de desativar, quando a recusa nasce no Web, é a mesma do Core: "Não é possível desativar o último administrador." A de rebaixar é "Não é possível rebaixar o último administrador."
- **Rationale**: Quem foi desativado continua na organização com o cargo antigo. Contar só o cargo da Clerk trataria um administrador já desativado como se ainda pudesse segurar a empresa, e deixaria rebaixar o último que ainda acessa o Core. A lista que o Web já monta tem os dois dados.
- **Alternatives considered**: Contar só o cargo na Clerk. Falha no cenário acima. Contar só o que o Core conhece. Um administrador que nunca chamou o Core sumiria da conta do Web, e a tela poderia desativar o único administrador real.

## Troca de cargo

- **Decision**: O Web chama `organization.updateMember` na sessão do administrador, com o mesmo identificador do convite: `admin` ou `member`. O Core não tem caminho de troca. O cargo observado muda na próxima requisição aceita dessa pessoa, porque o token novo traz o cargo. Não há item novo de dashboard.
- **Rationale**: A decisão tira a troca de cargo do Core e deixa o SDK que a LLT-167 já usa para o convite. Os identificadores já são os da sessão. Pedir configuração nova passaria trabalho para quem cuida da Clerk.
- **Alternatives considered**: Endpoint no Core que grava o cargo. A sessão seguinte continuaria com o cargo do token, e os dois divergiriam. Backend API para alterar membership. Exige segredo.

## Reativar

- **Decision**: `DELETE` da marca. O status volta a `active`. A próxima requisição com empresa é aceita e passa de novo pela observação. Reativar quem já está ativo devolve o mesmo estado. Pessoa desativada não chama esse caminho: a marca já recusou a requisição dela.
- **Rationale**: A decisão é tirar a marca. Como a pessoa não saiu da organização, não há convite nem SDK nessa volta.
- **Alternatives considered**: Reativar por convite novo. É a LLT-167 e exige a pessoa sair da organização, o que esta decisão não faz.

## Onde a conferência entra

- **Decision**: Um interceptor em `/api/**` lê a marca depois da autenticação e antes do controlador. Sem empresa no token, ele não decide: vale a recusa já existente. Com empresa e marca desativada, responde 403 "Seu acesso a esta empresa foi desativado." e não grava. Sem marca, observa a requisição aceita e segue. Assim company, produtos, perfil e a própria lista passam pela mesma trava.
- **Rationale**: Colocar a conferência só nos controladores novos deixaria o cadastro, os produtos e o perfil abertos para o token ainda válido. O interceptor único evita esse furo.
- **Alternatives considered**: Filtro dentro do `JwtDecoder`. Mistura validação do token, que continua igual, com regra de empresa. Conferir só em `CompanySessionResolver`. Um caminho novo que esquecesse o resolver ficaria aberto.

## Risco residual

- **Decision**: O contrato registra estes limites, aceitos porque o Core não lê a Clerk:
  - O cargo observado atrasa até a próxima requisição aceita daquela pessoa. Nesse intervalo o Core pode recusar desativar quem já não é o último administrador na Clerk, ou permitir desativar quem virou o último administrador na Clerk mas ainda está observado como membro.
  - Administrador que nunca teve requisição aceita não entra na contagem do Core. A tela o conta como ativo. Uma chamada direta ao Core, furando a tela, pode desativar esse `userId`.
  - Uma chamada direta ao SDK, furando a contagem do Web, pode rebaixar o último administrador. O Core não vê a chamada e não a impede.
  - A marca corta o Core. A pessoa continua na organização e o token da Clerk segue válido até expirar para o que não passa por este Core.
  - O último acesso pode ficar até 5 minutos atrás enquanto o cargo observado não muda.
  - Um `userId` que não está na organização pode receber marca nesta empresa. A tela não cria linha para quem a organização não lista. Outra empresa não herda a marca.

## O que não muda

- **Decision**: `ClerkTokenValidator`, `CLERK_ADMIN_ROLES`, `CompanySession` e a ausência de SDK e de `CLERK_SECRET_KEY` permanecem. Não há configuração nova de dashboard. LLT-169 a LLT-174 permanecem fora, exceto a fronteira já dita: a troca `admin`/`member` é desta história e fica no Web.
- **Rationale**: A decisão pede o mínimo na Clerk e o contrato do Core para o Web. O resto do produto não precisa mudar de stack.
- **Alternatives considered**: Aproveitar a LLT-163, que troca `CompanySession` e remove `CLERK_ADMIN_ROLES`. Essa branch não está na base. Esta entrega não faz rebase.

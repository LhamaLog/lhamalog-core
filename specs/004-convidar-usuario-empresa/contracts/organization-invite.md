# Contrato: convite para a empresa

Fonte da verdade desta entrega no Core. O Web, em outro repositório, cria o convite, lista os pendentes, revoga e reenvia. O Core não expõe recurso de convite.

## Fronteira

| Ação | Quem faz | O Core |
|---|---|---|
| Enviar convite | Web, SDK do navegador, sessão do administrador | Não envia e não oferece caminho |
| E-mail | Clerk | Não envia |
| Prazo de 7 dias, expiração | Clerk, valor configurado no dashboard por outra pessoa | Não guarda data |
| Lista de pendentes, revogar, reenviar | Web, via Clerk | Não lista, não revoga e não reenvia |
| Aceitar | Telas de entrada já existentes | Não cria rota |
| Vínculo depois do aceite | Sessão com a organização e o cargo | Não grava documento |
| Tratar `admin` e `member` | Core, nas regras já existentes | Única mudança de comportamento esperada: nenhuma. Os testes travam a regra |

## Caminhos que este repositório não ganha

Não criar controlador, serviço nem documento para convite. Com sessão autenticada e empresa selecionada, um pedido a caminho inexistente de convite continua 404. Exemplos que não devem passar a existir: `/api/invitations`, `/api/company/invitations` e `/api/organization-invitations`.

Sem sessão, a resposta continua 401, inclusive nesses caminhos, porque a segurança já exige autenticação antes do roteamento.

Não criar `POST`, `GET`, `PUT` nem `DELETE` de aceite. A pessoa entra pelas telas já existentes do Web.

## Sessão depois do aceite

O JWT de teste e o JWT real usam a mesma leitura de `CompanySession`.

| Sessão | Empresa | Cargo na sessão | `administrator()` com o padrão `admin` |
|---|---|---|---|
| Forma atual | `o.id` | `o.rol` = `admin` | sim |
| Forma atual | `o.id` | `o.rol` = `org:admin` | sim |
| Forma atual | `o.id` | `o.rol` = `member` | não |
| Forma atual | `o.id` | `o.rol` = `org:member` | não |
| Forma antiga | `org_id` | `org_role` = `admin` ou `org:admin` | sim |
| Forma antiga | `org_id` | `org_role` = `member` ou `org:member` | não |
| Sem organização | ausente | qualquer | a requisição é recusada com "Selecione uma empresa para continuar." |

O nome exibido "Administrador" não entra nessa tabela. Só o identificador.

## O que a sessão pode fazer

Estas regras já existem. O convite aceito não as troca.

- Membro (`member` ou `org:member`) consulta o cadastro da empresa e recebe 403 ao alterá-lo, com "Somente o administrador da empresa pode alterar este cadastro." A sessão não é encerrada.
- Administrador (`admin` ou `org:admin`) altera o cadastro da própria empresa.
- Membro e administrador consultam e gravam o próprio perfil, como na LLT-174. O perfil não ganha e-mail nem cargo.
- Administrador vê os produtos da empresa. Membro vê só os produtos com vínculo `AcessoProduto`. Esta entrega não cria nem remove esse vínculo. A LLT-171 continua fora.
- Identificador de outra empresa no corpo é ignorado.

## E-mail no Web

- E-mail vazio: "Informe o e-mail." O Web não chama a Clerk.
- E-mail irreconhecível: "Informe um e-mail válido." O Web não chama a Clerk.
- O Core não recebe esse campo e não devolve essas frases num recurso de convite.

## Configuração do dashboard da Clerk

Outra pessoa configura. Este repositório não grava segredo e não aplica a configuração.

- Convites de organização habilitados.
- Cargos que o convite pode atribuir: `admin` e `member`, os mesmos identificadores comparados na sessão. Não criar Gestor nem Visualizador nesta entrega.
- Prazo de validade de 7 dias. O reenvio, feito pelo Web, recomeça os 7 dias.
- Depois do aceite, a pessoa cai nas telas de entrada já existentes. Não há rota nova.
- Texto do e-mail de convite, no editor da Clerk, sem copiar chave para este repositório.
- Não colocar valor de `CLERK_SECRET_KEY` nem chave que comece com `sk_` em código, teste, exemplo ou documentação.

## Fora deste contrato

LLT-168 (listar, editar e desativar usuários), LLT-169 (catálogo e matriz), LLT-170 (trocar o cargo de quem já entrou), LLT-171 (acesso por produto), LLT-172, LLT-173 e LLT-174.

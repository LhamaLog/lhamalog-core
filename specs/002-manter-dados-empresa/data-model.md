# Modelo de dados: Manter dados da empresa

O formato dos campos continua o de [Empresa e Produtos](../001-empresa-produtos/data-model.md) e o contrato de gravação já aceito. Esta feature muda quem grava e acrescenta o fim do cadastro.

## Empresa (`Tenant`)

- **Identidade**: `id` e `clerkOrgId` são a organização da sessão. O cliente não escolhe esse id.
- **Campos**: dados cadastrais, contexto, endereço, preferências, `settings`, metadados de logotipo, `updatedAt`, `updatedByUserId` e `version`.
- **Unicidade**: no máximo um documento por organização.
- **Ausência**: sem documento, a leitura monta a empresa vazia dessa organização, com `version` nula.
- **Validação**: a gravação do administrador conserva as regras atuais. Cadastro incompleto continua aceito. Campo preenchido continua validado. Campos omitidos numa alteração permanecem.
- **Transições**:
  1. Ausente → gravado: administrador envia `version: null`.
  2. Gravado → gravado: administrador envia a `version` vigente. Versão divergente responde 409 e o documento fica como estava.
  3. Gravado → ausente: administrador exclui o cadastro. O documento é removido.
  4. Ausente → ausente: exclusão sem documento termina sem efeito.

## Logotipo (`TenantLogo`)

- **Identidade**: o id é a organização da sessão.
- **Relação**: no máximo um arquivo para a empresa.
- **Transições**: o administrador substitui ou remove o arquivo na gravação já existente. A exclusão do cadastro remove o documento do arquivo junto com a empresa.

## Histórico de contexto (`TenantContextRevision`)

- **Identidade**: `tenantId` mais número de revisão. Índice único já existente em `tenantId` e `revision`.
- **Relação**: várias revisões de uma empresa. Cada revisão guarda o recorte de contexto, autor e instante.
- **Transição**: a exclusão do cadastro remove todas as revisões daquela `tenantId`. Revisão de outra organização permanece.

## O que esta exclusão não alcança

- **Produto** (`Produto`) e **vínculo** (`AcessoProduto`) continuam com o `tenantId` da organização.
- A organização no serviço de identidade não é criada nem removida.
- Tokens, alertas, faturamento e métricas não têm documento alterado aqui.

## Papel

- **Administrador**: o slug único do claim de papel da sessão está em `CLERK_ADMIN_ROLES`. Cadastra, altera, troca ou remove logotipo e exclui o cadastro da própria organização.
- **Membro**: sessão válida com organização e slug fora dessa lista. Lê empresa, logotipo e histórico. Gravação e exclusão são recusadas antes de mudar documento.
- **Sessão sem organização**: continua recusada como hoje, antes da regra de papel.

## Isolamento

Toda leitura, gravação e exclusão usa o id resolvido na sessão. Id de organização enviado no corpo continua ignorado na gravação. A exclusão não recebe id no caminho nem no corpo.

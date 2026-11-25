# Modelo de dados: Melhorar a tela de perfil do usuário

Estende o perfil de [Manter perfil do usuário](../003-manter-perfil-usuario/data-model.md). A coleção continua `UserProfile`. Não há entidade nova.

## Perfil (`UserProfile`)

- **Identidade**: inalterada. `id` é o `sub` da sessão (`CompanySession.userId()`).
- **Campos que permanecem**: `displayName`, `language`, `timezone`, `updatedAt`, `version`. Regras, aliases e versão otimista não mudam.
- **Campos acrescentados**:
  - `bio`: texto opcional, já com trim, no máximo 160 caracteres por `String.length()`. Vazio permitido. Caractere de controle (código menor que 32, exceto tabulação) recusado. Quebra de linha entra nessa recusa. `<` e `>` permanecem. Ausência de texto é `null`.
  - `jobTitle`: o mesmo tratamento, no máximo 80 caracteres. Não é papel de acesso nem cargo de notificação.
- **O que não é gravado**: empresa, e-mail, foto, função, organização, membro desde, senha, verificação em duas etapas, sessões, tema, acessibilidade, papel e segredo. Identificadores enviados pelo cliente continuam ignorados. `theme` e chaves de acessibilidade também.
- **Unicidade**: inalterada. No máximo um documento por pessoa.
- **Ausência do documento**: a leitura não insere. Devolve nome vazio, `language` `pt-BR`, `timezone` `America/Sao_Paulo`, `bio` `""`, `jobTitle` `""` e `version` nula.
- **Ausência do texto**: documento antigo sem as chaves, ou campo apagado, guarda `null`. A view devolve `""`. A consulta e a gravação aceita sempre incluem as duas chaves.
- **Validação**: acima do limite ou com caractere de controle recusado responde 400, no campo, com "O texto excede o limite permitido." Tipo que não é texto responde 400, no campo, com "Confira o preenchimento deste campo." A mensagem geral continua "Confira os campos informados." Nada disso grava.
- **Gravação dos dois campos**:
  1. Chave ausente: mantém o valor corrente. Na primeira gravação, o valor corrente é `null`.
  2. `null`, `""` ou só espaços: grava `null`.
  3. Texto restante depois do trim: grava esse texto.
- **Transições que não mudam**:
  1. Ausente → gravado: `version` nula e nome, idioma e fuso válidos. `bio` e `jobTitle` seguem a gravação acima.
  2. Gravado → gravado: `version` vigente. Divergência responde 409 e o documento fica como estava, inclusive bio e cargo.
  3. Não há exclusão do perfil.

## Sessão

Igual à do perfil já existente. Sem empresa, a recusa continua a do handler atual. Esta feature não grava `tenantId` e não consulta `administrator()`.

## O que este acréscimo não alcança

Empresa, logotipo, produto, vínculo, cargos de notificação e preferências de notificação não são lidos nem gravados por causa de `bio` ou `jobTitle`. A conta externa não é lida nem atualizada.

# Contrato: bio e cargo no perfil do usuário

Esta entrega não cria recurso. O contrato vigente de `GET /api/user/profile` e `PUT /api/user/profile` é [user-profile.md](../../003-manter-perfil-usuario/contracts/user-profile.md), atualizado com os campos abaixo. Nome, idioma, fuso, versão, sessão, erros já aceitos e a fronteira com a conta externa permanecem naquele arquivo.

## Campos novos

| Campo | Obrigatório no `PUT` | Gravado | Consulta sem texto |
|-------|----------------------|---------|--------------------|
| `bio` | não | trim, 0 a 160 caracteres por `String.length()`, sem caractere de controle exceto tabulação; vazio não é gravado (`null`) | `""` |
| `jobTitle` | não | trim, 0 a 80 caracteres, a mesma regra da bio | `""` |

`displayName`, `language` e `timezone` continuam obrigatórios. Omitir um desses três segue inválido.

Para `bio` e `jobTitle`:

- chave ausente mantém o valor já guardado; na primeira gravação, não há valor;
- `""`, `null` ou texto que o trim deixa vazio apaga;
- texto acima do limite, ou com caractere de controle que não seja tabulação (incluindo quebra de linha), responde 400 no campo com "O texto excede o limite permitido." e não grava;
- tipo que não é texto responde 400 no campo com "Confira o preenchimento deste campo.";
- `<` e `>` são texto. O Core não interpreta HTML.

O `GET`, e o `PUT` aceito, sempre devolvem as duas chaves. Sem texto, o valor é `""`. Isso vale para perfil antigo sem as chaves e para consulta sem documento.

`jobTitle` com o texto "Administrador" não altera papel de acesso. Tema, acessibilidade, foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões não têm campo. Chave extra no JSON é ignorada.

## Exemplo

Consulta de um perfil que tem os dois textos:

```json
{
  "displayName": "Ana Souza",
  "language": "pt-BR",
  "timezone": "America/Sao_Paulo",
  "bio": "Cuida da operação.",
  "jobTitle": "Coordenadora",
  "version": 0
}
```

Perfil antigo, ou texto apagado:

```json
{
  "displayName": "Ana Souza",
  "language": "pt-BR",
  "timezone": "America/Sao_Paulo",
  "bio": "",
  "jobTitle": "",
  "version": 0
}
```

Gravação que muda a bio e apaga o cargo, mantendo nome, idioma e fuso:

```json
{
  "displayName": "Ana Souza",
  "language": "pt-BR",
  "timezone": "America/Sao_Paulo",
  "bio": "Cuida da operação.",
  "jobTitle": "",
  "version": 0
}
```

Um `PUT` sem `bio` e sem `jobTitle` continua válido. Os valores guardados permanecem.

# Contrato REST Tokens de ingestão

Autenticação e empresa via JWT da Clerk (`o.id`), como na 001. Formato de erro `{message, fieldErrors}`. Todas as rotas exigem papel administrador da organização.

Base: `/api/products/{productId}/tokens`

## Visão do token (`TokenView`)

```json
{
  "id": "3f1c…",
  "name": "cluster-producao",
  "state": "EXPIRING",
  "createdAt": "2026-11-12T22:10:00.000Z",
  "createdBy": "user_abc",
  "expiresAt": "2026-11-13T22:10:00.000Z",
  "revokedAt": null,
  "revokedBy": null,
  "replacedBy": "9a2e…"
}
```

Nunca contém o valor nem o hash.

## GET /api/products/{productId}/tokens

200 `[TokenView…]`, mais recente primeiro.

## POST /api/products/{productId}/tokens ← `{"name": "cluster-producao"}`

201:

```json
{
  "token": { "…TokenView…": "…" },
  "secret": "llt_…",
  "notice": "Copie o token agora: ele não será exibido de novo."
}
```

## POST /api/products/{productId}/tokens/{tokenId}/revoke

200 `TokenView` com `state = REVOKED`. Repetir responde 200 sem mudança.

## POST /api/products/{productId}/tokens/{tokenId}/rotate ← `{"name": "opcional", "transitionHours": 24}`

Corpo opcional; sem `name` o novo token herda o nome; sem `transitionHours` vale 24.

201:

```json
{
  "created": { "token": { "…": "…" }, "secret": "llt_…", "notice": "…" },
  "previous": { "…TokenView com state EXPIRING e replacedBy…": "…" }
}
```

## GET /api/products/{productId}/tokens/audit

200, mais recente primeiro:

```json
[
  {"id": "…", "tokenId": "…", "action": "ROTATED", "actorUserId": "user_abc", "at": "…", "relatedTokenId": "…", "transitionEndsAt": "…"}
]
```

## Recusas

| Situação | Status | Mensagem |
| --- | --- | --- |
| JWT ausente ou inválido | 401 | |
| Sem organização ativa | 403 | Selecione uma empresa para continuar. |
| Não é administrador | 403 | Somente donos e administradores podem gerenciar tokens. |
| Produto inexistente ou de outra empresa | 404 | Produto não encontrado ou sem acesso. |
| Token inexistente ou de outro produto | 404 | Token não encontrado. |
| Nome vazio ou acima de 100; transição fora de 1–168 | 400 | |
| Gerar ou rotacionar em produto arquivado | 409 | Produto arquivado não pode receber tokens novos. |
| Rotacionar token revogado ou expirado | 409 | Só um token ativo pode ser rotacionado. |
| Rotacionar token em transição | 409 | Este token já está em período de transição. |
| Gravação concorrente | 409 | Os dados foram alterados. Recarregue antes de salvar novamente. |

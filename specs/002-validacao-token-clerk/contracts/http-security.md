# Contrato HTTP de segurança (LLT-163)

## Requisição

- Header: `Authorization: Bearer <session token da Clerk>`.
- Cookies não são usados (front em outra origem).

## Rotas públicas (sem token)

| Método | Rota |
|---|---|
| GET | `/actuator/health`, `/actuator/health/**`, `/actuator/info` |
| POST | `/webhooks/clerk` (assinatura Svix, LLT-164) |
| OPTIONS | qualquer rota (preflight CORS) |

## Respostas de erro

### 401 Unauthorized

Token ausente, malformado, assinatura inválida, algoritmo diferente de RS256, `exp`/`nbf` fora da janela, `iss` diferente, `azp` ausente ou fora da lista, `sts` diferente de `active`.

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer
Content-Type: application/json

{"error":"unauthorized"}
```

### 403 Forbidden

Token válido sem a claim `o`, `o.rol` desconhecido, Tenant inexistente para `o.id` ou Tenant não ativo (e futuras negações de permissão). Efeito colateral: o Core tenta revogar todas as sessões ativas do usuário na Clerk.

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{"error":"forbidden"}
```

## Chamadas de saída à Clerk Backend API (só no 403)

Base: `CLERK_API_URL` (padrão `https://api.clerk.com`). Header: `Authorization: Bearer <CLERK_SECRET_KEY>`.

| Passo | Chamada |
|---|---|
| 1 | `GET /v1/sessions?user_id=<sub>&status=active&limit=500` → array de sessões ou `{ "data": [...] }` |
| 2 | `POST /v1/sessions/<session_id>/revoke` para cada sessão |

## Porta para o restante do Core

```java
public interface CurrentRequestContext {
    RequestContext current(); // IllegalStateException fora de requisição autenticada
}
```

# Quickstart: testar a validação do token da Clerk (LLT-163)

## Automático

Com o Docker aberto, na raiz do repositório:

```powershell
.\mvnw.cmd clean verify
```

Os testes geram o próprio par de chaves RSA e não chamam a Clerk.

## Manual (instância de desenvolvimento da Clerk)

1. Suba a infra local (`make infra-up`) e exporte as variáveis do README (Mongo, Redis, OpenObserve).
2. Exporte as variáveis da Clerk (valores do Dashboard da Clerk; nunca versione):
   ```powershell
   $env:CLERK_ISSUER="<Frontend API URL da instância, sem barra final>"
   $env:CLERK_SECRET_KEY="<secret key da instância de dev>"
   # opcional: $env:CLERK_AUTHORIZED_PARTIES="http://localhost:5173"
   ```
3. Rode o Core: `make run-local`.
4. Insira um Tenant de teste no Mongo local para a organização que você usa no lhamalog-web (troque `<org_id>`):
   ```javascript
   db.tenant.insertOne({ _id: "<um UUID>", clerkOrgId: "<org_id>", name: "Teste local", status: "ACTIVE" })
   ```
5. No lhamalog-web (`http://localhost:5173`), logado e com a organização ativa, obtenha um token pelo console do navegador: `await window.Clerk.session.getToken()`. O token vale 60 s.
6. Confira o formato das claims colando o token num decodificador **local** (não cole token em site externo) e registre na spec o valor real de `o.rol` e `sts` (pendência da spec).
7. Chame uma rota protegida qualquer:
   ```powershell
   curl.exe -i -H "Authorization: Bearer <token>" http://localhost:8080/api/qualquer
   ```
   - Sem rota de negócio ainda, o esperado com token válido e Tenant ativo é **404** (passou da segurança).
   - Sem token: **401** `{"error":"unauthorized"}`.
   - Com uma organização sem Tenant: **403** `{"error":"forbidden"}`, e o usuário é deslogado do front em até 60 s (sessões revogadas na Clerk).
